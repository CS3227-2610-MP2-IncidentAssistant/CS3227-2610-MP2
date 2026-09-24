# Issue #15 implementation review

## Current scope: image-only attachments

The user explicitly removed video support after the platform investigation.
This supersedes all video-support and pending-codec-fix discussion in the
historical sections below. New uploads accept only PNG/JPEG; videos renamed
as images are rejected without persisted changes. The shared viewer uses
in-memory images only, with no JavaFX media dependency, native decoder, or
external viewer. Image limits, authorization, privacy, audit and recovery
safeguards remain unchanged.

Existing schema-2 MP4 metadata and files remain intact; opening them is denied.
The MP4 enum value is retained solely for decoding old metadata. No migration
or deletion of user data is performed. Anonymous legacy names remain generic.

### Files and methods to review in this follow-up

Under `src/main/java/com/company/incidentdesk/`:

- `application/attachment/AttachmentLimits`: remove the obsolete video limit;
  the constructor retains image/count/total/pixel bounds.
- `AttachmentValidator.readSource/validate`: image-only byte limits and
  signature checks; non-image content is rejected before any storage write.
- `AttachmentService.uploadLimitSummary/open`: image-only help and deny
  unsupported legacy types before reading content.
- `AttachmentRead`: remove the URI field/accessor; retain session-bound bytes.
- `domain/attachment/AttachmentType.isSupported`: explicit PNG/JPEG allowlist
  with MP4 retained only for persisted-format compatibility.
- `persistence/AttachmentStore`, `file/AttachmentFiles`, and
  `file/LocalApplicationStore`: remove URI APIs; `validateAddition` also denies
  unsupported types. Existing aggregate and recovery formats are unchanged.
- `ui/shared/components/AttachmentPane`: image-only picker/privacy wording;
  existing shared controls are reused.
- `AttachmentViewer.render/close`: image rendering and safe error feedback;
  remove all native player construction, playback and disposal code.
- Delete `application/attachment/Mp4Validator` and its structural acceptance
  tests because accepting video is no longer a product requirement.

Other changed artifacts: `build.gradle` removes JavaFX media and its diagnostic
flag; `.github/workflows/ci.yml` retains all three OS jobs and saved reports,
removes media diagnostics, and drops the uncommitted Linux codec installation.
`.agents/requirements.md`, `persistence.md`, `anonymity-policy.md`, and
`testing.md`, plus `docs/DeveloperGuide.md`, reflect the approved policy.
The test-resource README now describes the retained MP4 as rejection-only.

New/updated tests: `AttachmentServiceTest` rejects a real MP4 both normally and
renamed as PNG/JPEG, verifying unchanged canonical state, no blobs/audits, and
preserved source files; `AttachmentPersistenceTest` preserves legacy videos
but denies reads; `AttachmentValidatorTest` covers PNG/JPEG and revised limits;
`AttachmentViewerTest` retains logout-clearing and replaces playback with
unavailable-content feedback. This is an approved requirement change, not a
skip or relaxation of the remaining image/privacy/persistence guarantees.

Verification and review: `./gradlew clean build` passes locally with 311 tests,
zero failures/errors/skips; workflow YAML parsing and `git diff --check` pass.
Java 25.0.4.1 / JavaFX 25 / Gradle 9.1.0 on macOS 15.2 arm64, with only
graphics native-access warnings. No further code-quality findings. No new commit
has been made for this follow-up. Windows/Linux CI must rerun after approval
to commit/push; PR #68 and issue #15 descriptions also need the approved
image-only scope before closing. Previous native-media failures are no longer
applicable to the feature, but this does not certify all-platform image support.

## Historical implementation and investigation record

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

### Diagnostic results and proposed platform corrections

Diagnostic commit `a55de0b` was pushed with approval. Run `35981406898`
passed macOS and reproduced both failures with detailed reports:

- Linux: `ERROR_MEDIA_AUDIO_FORMAT_UNSUPPORTED` during pipeline creation,
  followed by `No player created`. The library inventory contains ALSA and
  GStreamer but neither libavcodec nor libavformat. The pending CI correction
  pins the existing Ubuntu 24.04 runner family and installs its `libavcodec60`
  and `libavformat60` packages before testing. This installs runtime codecs on
  the ephemeral CI host, not a new Java dependency or libraries on user machines.
- Windows: playback and disposal assertions pass; JUnit then fails deleting
  the stored temporary MP4. JavaFX 25's `Locator.getInputStream` opens one
  connection for content length and another stream for reading, closing only
  the latter. JDK 25's file-connection content-length implementation opens an
  input stream. This provides a source-level explanation for a residual handle
  beyond player disposal; a timing-only workaround is not proven sufficient.

Primary source references:

- [JavaFX 25 Locator](https://github.com/openjdk/jfx/blob/jfx25/modules/javafx.media/src/main/java/com/sun/media/jfxmedia/locator/Locator.java)
- [JDK 25 file URL connection](https://github.com/openjdk/jdk/blob/jdk-25-ga/src/java.base/share/classes/sun/net/www/protocol/file/FileURLConnection.java)

The proposed Windows test-harness change is a separate JVM for native playback,
preserving playback-advancement and disposal assertions and requiring temporary
file deletion after worker exit. It would isolate the native library lifetime,
not fix an upstream file-handle lifetime in production. Immediate attachment
deletion is not a product feature, but this limitation must remain documented.
This change is awaiting approval because it changes the test cleanup boundary;
no cleanup assertion has been removed and no production workaround added.

Only `.github/workflows/ci.yml` and this guide have changed since the diagnostic
commit. The CI dependency correction is uncommitted and not yet exercised on
Linux. Local tests last passed all 310 cases with diagnostics before this CI-only
edit. Workflow YAML parsing and `git diff --check` pass. No new review findings.
Issue #15 and PR #68 remain open pending fixes and passing platform checks.
