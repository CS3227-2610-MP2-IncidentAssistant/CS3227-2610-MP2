# Issue #15 implementation review

Branch: `codex/issue-15-attachments`, based on `c40a9b7`.
Changes are unstaged; no commit, push, pull request, or issue closure has been
performed. No real application data was migrated or modified.

## Behavior and data impact

The shared attachment service validates and copies PNG/JPEG and H.264 MP4
attachments, returns privacy-safe display models, and authorizes each open
against the active session and incident. Only the owning reporter can add to
an editable draft or unassigned submitted incident. Responders and admins
can view only under existing incident permissions; they cannot upload.

Defaults: images 10 MiB, videos 50 MiB, 5 files and 100 MiB total per incident,
40 million decoded pixels. Anonymous names are generic; original bytes and
embedded metadata are not stripped. The upload panel warns about that risk.
Attachment writes do not alter lifecycle or SLO timestamps.

Blobs use generated UUID names and validated extensions. Metadata and audits
share the aggregate safe-write transaction. Durable upload markers support
rollback and restart cleanup of only marked files, retaining blobs referenced
by canonical state or backup. Original selected files remain untouched.

Existing version-1 data remains unchanged until the first successful upload.
That upload upgrades to version 2 with a migration audit and the prior state
as the normal bounded backup. Later saves rotate that backup. New stores use
version 2; older application builds cannot read version 2. Migrations were
tested only in temporary storage, not run against a user's data.

`AttachmentPane` is a reusable saved-incident uploader/list/viewer, not a new
role screen. Integration into #21 (detail) and #64 (reporter submission) remains
separate. It composes existing panels, tiles, actions and feedback; the new
viewer supplies image decoding and native media lifecycle absent from those
existing primitives.

## Changed production classes and review points

Paths below are relative to `src/main/java/com/company/incidentdesk/`.

| Class | Methods/contracts to review |
| --- | --- |
| `domain/attachment/AttachmentId` | Constructor: typed, non-null UUID; no UI-supplied storage path. |
| `domain/attachment/AttachmentType` | Enum and `mediaType/extension`: supported format allowlist. |
| `domain/attachment/IncidentAttachment` | Constructor and `storageName`: valid metadata and generated filename; original name stays protected. |
| `application/attachment/AttachmentLimits` | Constructor, `DEFAULT`, `maximumFileBytes`: central byte/count/pixel limits and bounded allocations. |
| `application/attachment/AttachmentValidationException` | Constructor: neutral error without selected file paths. |
| `application/attachment/AttachmentValidator` | `readSource`, `validate`, `sanitizeName`, `validateImage`: bounded reads, signature/extension agreement, image decoding and sanitized names. |
| `application/attachment/Mp4Validator` | `validate`, `boxes`, `sampleDescriptions`, `requireChild`, `hasAacDecoder`, `descriptor`: bounds-checked container and codec configuration; not a full frame decoder. |
| `application/attachment/AttachmentContent` | Constructor/`bytes`: defensive copies and path-free content. |
| `application/attachment/AttachmentRead` | `isAvailable`, `content`, `mediaSource`: session-bound access; URI only for native player, never display/log. |
| `application/attachment/AttachmentService` | `list/add/open`: current authorization, validation, privacy mapping and audit creation; `viewGuard/canAdd`: safe UI affordances; `requireContext/requireCurrent`: reject stale reads. |
| `application/authorization/IncidentAuthorizationPolicy` | `authorizeAttachmentAccess/authorizeAttachmentAddition`: distinguish incident viewing from owning-reporter editing. |
| `persistence/AttachmentStore` | `list/find/read/mediaSource/add`: immutable blob plus audited aggregate transaction contract. |
| `persistence/file/AttachmentFiles` | `prepare/read/mediaSource/finish/recover`: generated paths, symlink rejection, flushed marker protocol and scoped cleanup. |
| `persistence/file/LocalApplicationState` | Constructors and `empty`: immutable attachment map and retained schema version. |
| `persistence/file/LocalApplicationStateCodec` | `encode/decode`, `writeAttachments/readAttachments`, `validateReferences`: version-1 compatibility, deterministic version-2 metadata and valid incident references. |
| `persistence/file/LocalApplicationStore` | Constructor recovery, `attachmentStore`, `nextState`, facet `add/validateAddition/commitFiles`: existing writes preserve attachments; expected-state checks, quotas, atomic metadata/audit and blob rollback. |
| `startup/ApplicationContext` | Factory assembly and `attachments`: use existing sessions, store, authorization and audit infrastructure. |
| `ui/shared/components/AttachmentPane` | Constructor, `refresh/addFile/load/showRows/close`: shared components, asynchronous work, duplicate-operation guard, stale-result rejection, safe feedback and cleanup. |
| `ui/shared/components/AttachmentViewer` | `show/render/renderImage/renderVideo/close`: asynchronous authorized load, in-app viewing, error handling, access expiry and native-player disposal. |

## Other changed artifacts

- `build.gradle`: adds the approved JavaFX 25 media module, using the existing
  OpenJFX dependency resolver.
- `.github/workflows/ci.yml`: retains the Linux full build; adds Windows/macOS
  attachment and real-playback tests. These new remote jobs have not run yet.
- `.agents/requirements.md`: approved format, size and upload-permission rules.
- `.agents/authorization-matrix.md`: separate upload authorization row.
- `.agents/anonymity-policy.md`: generic names and media-content warning.
- `.agents/persistence.md`: version-2 migration, markers, backup and recovery.
- `.agents/testing.md`: attachment validation/recovery and native-playback checks.
- `docs/DeveloperGuide.md`: service/panel integration, data impact and platform
  verification instructions.
- This review guide records handoff evidence and the review finding.

## Verification

- `./gradlew clean build`: **passed** after CQ-1, 310 tests, zero failures/errors/skips.
  This includes normal JAR/distribution assembly; it is not a Shadow JAR or
  clean-machine installer verification. No Checkstyle/formatter/static-analysis
  task is configured in the current build, despite older guide text mentioning
  Checkstyle and Shadow JAR tasks.
- `./gradlew test --tests '*Attachment*Test' --tests '*Mp4ValidatorTest'`:
  **passed** before CQ-1, 18 attachment tests. The final full build also includes
  the two new default/custom-limit guidance tests.
- `git diff --check`: **passed**.
- Toolchain: Homebrew OpenJDK **25.0.4.1**, JavaFX **25**, Gradle **9.1.0**,
  macOS **15.2 arm64**. Gradle's launcher/daemon uses Java 17; compilation and
  test execution use the configured Java 25 toolchain.
- Windows/Linux execution and clean-machine packaged launch: **not run locally**.
  CI coverage is configured, not yet verified. Packaging remains #63.
- Actual local native H.264 video playback advanced and the player was disposed
  on close. Image rendering and clearing on logout passed. No claim is made
  about AAC audio, every H.264 profile, all OS/CPU combinations, or a manual
  full-screen layout review.
- Initial video runs failed with a generated `.blob` filename. Retaining the
  validated media extension fixed playback, and the real-playback test passed.
- The first full run caught an old unknown-schema fixture hardcoded to version
  2. It now tests `SCHEMA_VERSION + 1`, retaining unknown-version rejection
  while recognizing the approved version-2 migration. The final clean run passed.
- JavaFX emitted native-access/unnamed-module warnings; there were no build or
  test failures in the final run. Launch configuration/package hardening remains
  separate from claiming all-platform compatibility.

### Acceptance tests

- `AttachmentServiceTest`: anonymous naming/audits, authorized/denied reads
  and additions, assigned/resolved/withdrawn denial, count/aggregate quotas,
  failed metadata/audit commit rollback, restart persistence, session replacement
  and revoked category access, indistinguishable missing/unauthorized errors.
  `uploadGuidanceUsesDefaultLimits` and
  `uploadGuidanceUsesCustomLimitsWithoutRounding` verify CQ-1, including byte
  bounds that cannot be expressed as whole MiB and singular file/byte labels.
- `AttachmentValidatorTest`: spoofed extensions, malformed/empty content,
  source byte bounds, decoded pixel bounds, path/control-character sanitization.
- `Mp4ValidatorTest`: supported AVC sample entry, unsupported codec and
  malformed/truncated box rejection. Structural fixtures are not playback proof.
- `AttachmentPersistenceTest`: old-format backup/migration/audits, marked
  orphan cleanup, committed-blob retention, unrelated-file preservation,
  source/stored symlink rejection.
- `AttachmentViewerTest`: image rendering/logout clearing and real H.264
  playback/player disposal, using an original 1,262-byte black/white fixture.
- `RecoverySafeFileTest`: updated unknown-schema fixture, existing safe-write
  and process-lock protection remain covered by the full suite.
- `support/AttachmentFixture` and `src/test/resources/attachments/` are test-only;
  all file mutation tests operate in isolated temporary directories.

## Code-quality review

CQ-1 — U6: Duplicated limit policy [Resolved with user approval]

Location: `ui/shared/components/AttachmentPane.java:87`.
The error-help string previously repeated default limits despite configurable
`AttachmentLimits`. It now consumes `AttachmentService.uploadLimitSummary()`,
which derives byte, count and pixel guidance from the same injected limits used
for validation. Exact whole-MiB values use MiB; other values retain exact bytes
without rounding. No authorization, validation, or persistence policy changed.

Outstanding counts: 0 Required, 0 Recommended, 0 Consider. Reviewed readability R1–R13,
naming N1–N7, unsafe shortcuts U1–U6, and comments C1–C3. The longer transaction
and parser methods were retained where splitting would obscure cleanup or
protocol flow. The approved CQ-1 follow-up was reviewed with no further findings.

The implementation remains available for review. Nothing is staged or committed;
committing still requires explicit permission.

## Cross-platform CI investigation after PR #68

Run `35980438733` passed macOS attachment tests but failed the native video
test on Linux and Windows. Linux reported a playback assertion failure;
Windows reported a JUnit exception caused by an I/O exception, consistent
with temporary-directory cleanup but not yet proven. The run retained no
JUnit XML or HTML reports, and the abbreviated console output omitted the
underlying messages. Do not interpret this as a confirmed codec or disposal bug.

The diagnostic follow-up changes only:

- `build.gradle`, `test` task: show full exception output; opt-in
  `-PmediaDiagnostics` enables native-media debug messages in tests only.
- `.github/workflows/ci.yml`: enable that test-only option, retain XML/HTML
  test reports on success or failure for seven days, and print Linux media
  library availability after failure. No tests are skipped or relaxed.
- This review guide: record evidence and the remaining verification step.

`./gradlew clean build -PmediaDiagnostics` passed locally with 310 tests and
no failures/errors/skips on the same macOS/Java 25 environment. Native media
debug output was verified. `git diff --check` passed. Review of the follow-up
found no further code-quality findings. No production behavior, application
data format, permissions, or production logging changed.

Root causes and fixes remain unconfirmed pending a new Windows/Linux CI run.
The local Docker daemon is unavailable, and no local Windows host is available.
This diagnostic follow-up is unstaged and uncommitted; approval to commit and
push it is needed to obtain the missing platform evidence. PR #68 is unmerged
and issue #15 remains open.
