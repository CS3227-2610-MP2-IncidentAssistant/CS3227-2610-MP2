# Incident Desk Developer Guide

## Prerequisites

- JDK 25. The application deliberately rejects every other Java feature
  version at startup so local and packaged behavior remains reproducible.
- Git. A separate Gradle installation is not required.

Confirm that `java -version` and `javac -version` both report version 25.

## Common commands

On Windows, run:

```powershell
.\gradlew.bat test
.\gradlew.bat check
.\gradlew.bat run
.\gradlew.bat previewAuthenticatedShell
.\gradlew.bat shadowJar
```

On macOS or Linux, replace `.\gradlew.bat` with `./gradlew`.

The executable Shadow JAR is written to `build/libs/incident-desk.jar`. It
contains JavaFX native libraries for Windows, macOS, and Linux, all x86_64.
JavaFX publishes separate, differently-architected natives for Apple Silicon
(`mac-aarch64`) and ARM Linux (`linux-aarch64`), but those natives share the
same file names as their x86_64 counterparts (e.g. `libglass.dylib`), so a
single jar cannot bundle both architectures for the same OS. Running the
packaged jar on Apple Silicon therefore requires an x86_64 (Rosetta) JVM.
Development compilation, tests, and `run` instead select JavaFX for the Gradle
JVM's OS and CPU architecture, including native Apple Silicon (`mac-aarch64`)
and ARM Linux (`linux-aarch64`). Use the same architecture for the Gradle JVM
and its JDK 25 toolchain. Windows development currently supports x86_64 only.
`verifyJavaFxPlatforms` (included in `check`) verifies dependency isolation;
building the Shadow JAR on ARM does not add ARM natives to that Intel-only JAR.

For native Apple Silicon packaging, run:

```sh
./gradlew shadowJarMacArm64
java -jar build/libs/incident-desk-mac-aarch64.jar
```

Use an ARM64 JDK 25 for that launch. This second JAR contains only macOS ARM64
JavaFX libraries and does not replace `incident-desk.jar`. `assemble` builds
both artifacts; `shadowJar` alone still builds only the original Intel artifact.
Each user needs just the one JAR matching their OS/JVM architecture.

`PackagedApplicationTest` checks both JARs' macOS binary headers and launches
the matching artifact with `java -jar`. A test-only observer agent waits for
the application window and requests normal shutdown. Neither the observer nor
test code ships in either application JAR. The process uses fresh temporary
application storage and a separate JavaFX cache. CI tests the packaged launch
on Linux/Windows x86_64, macOS x86_64, and macOS ARM64. Release jobs publish
each of the two filenames only once, avoiding merged-artifact overwrites.

`previewAuthenticatedShell` exercises the real login, session, role-routing,
shell, and logout path with in-memory administrator, reporter, and responder accounts:

```powershell
.\gradlew.bat previewAuthenticatedShell
```

The login names are `admin`, `reporter`, and `responder`; the preview accepts
any password for each account. These accounts and the test-only verifier exist
only in the preview process and are not written to application data or included
in production.

## Architecture

- `com.company.incidentdesk.domain`: domain types, lifecycle rules, and
  authorization.
- `com.company.incidentdesk.application`: use cases and authenticated-session
  orchestration.
- `com.company.incidentdesk.persistence`: local-file storage behind
  domain-facing interfaces.
- `com.company.incidentdesk.ui`: JavaFX presentation code.
- `com.company.incidentdesk.startup`: runtime checks, configuration, locking,
  and assembly.

`ApplicationContext` assembles process-wide repositories and application
services once at startup. `ApplicationNavigator` owns the authentication
boundary and places every role dashboard inside the same `AuthenticatedShell`;
views receive their dependencies through `DefaultViewFactory` rather than
constructing services themselves.

Dependencies point inward: UI, persistence, and startup may depend on
application and domain code; domain code must not depend on those outer layers.

## Runtime data

The packaged JAR and classpath resources are read-only. Runtime data,
attachments, backups, and diagnostics will be resolved through one configurable
application-data directory. Tests that exercise persistence must use a fresh
temporary directory and must never use a real application-data directory.

The serialization format and default application-data location have not yet
been selected. Do not create ad hoc file paths before those decisions are made.

## Testing and quality checks

JUnit tests belong under `src/test/java` and should mirror the production
package layout. Use deterministic clocks and identifier generators for domain
tests. Test both allowed and denied operations at the domain boundary.

`check` runs the tests and Checkstyle. CI also assembles the Shadow JAR. A
change is ready for review only after the relevant checks pass and its data,
authorization, audit, privacy, and SLO effects have been considered.

## Responder dashboard integration (#37)

`ResponderPage` accepts the shared `IncidentService`, an
`IncidentPresentationMapper` using the same authorization/session source, the
`SessionProvider`, an incident-ID detail callback, and a back callback. It reads
authorized eligible and assigned queues asynchronously, in deterministic queue
order. Refresh and navigation recheck the session and current category access;
detaching the page clears its contents and invalidates outstanding reads.

The reusable `IncidentTable` from #20 is shared with the component showcase and
consumes only privacy-safe `IncidentRowModel` values. The dashboard displays
queue-entry timestamps and keeps application queue ordering. Its filters and
interactive sorting remain hidden until their integration under #42; SLO
columns are not enabled by this dashboard change.

The role-selection launcher still uses the signed-out preview constructor. It
does not impersonate a responder or load production data. Authenticated service
wiring belongs to #40/#22; the detail callback is implemented by #38. Dashboard
reads do not modify persisted data, audit records, or SLO timestamps, and require
no migration or additional production dependency.

## Shared attachments (#15)

Use `ApplicationContext.attachments()` for application operations and compose
`AttachmentPane(service, savedIncidentId)` into a page. The pane reuses the
shared panel, attachment tile, action, and feedback components. It performs
file reads off the JavaFX thread and owns an `AttachmentViewer`; close it on
navigation if it is not detached from its scene. The viewer clears content
when the authenticated session or incident permission snapshot changes.
The pane must receive a persisted incident; saving a new draft/submission and
attaching selected files is a separate, explicit sequence for #64. Detail-page
integration belongs to #21. No existing role screen is replaced by this change.

`AttachmentService.list/add/open` enforce current incident authorization.
Only the owning reporter can add files while a draft or an unassigned submitted
incident is editable. An upload does not change lifecycle or SLO timestamps.
Successful additions commit metadata and `ATTACHMENT_ADDED` evidence together.
Anonymous display models use generic names and do not contain local paths.
The internal `AttachmentRead` capability rechecks authorization before exposing
image bytes; it does not expose a storage URI.

Attachments are image-only: PNG/JPEG ≤10 MiB,
5 files/100 MiB per incident, and 40 million decoded image pixels. Inject an
`AttachmentLimits` value when constructing the service to change the limits.
The UI help text comes from `AttachmentService.uploadLimitSummary()` and
reflects those configured limits without rounding byte bounds. Videos and audio
are rejected even if renamed as an image. Images are decoded from memory;
the app does not use JavaFX media or launch an external viewer.
Original content and embedded metadata are preserved, so the uploader warns
that media can disclose identity despite generic anonymous filenames.

Storage uses generated UUID filenames with validated extensions beneath the
configured application-data directory. Version-1 stores remain unchanged until
the first successful attachment addition, which writes version 2, a migration
audit, and the previous canonical file as the bounded backup. Later saves
rotate that backup normally. Older app versions cannot read version 2.
Pending-upload markers support restart cleanup without sweeping unrelated
files. See `.agents/persistence.md` for the write/recovery protocol. Development
tests use temporary directories and do not migrate real application data.

Run `./gradlew test --tests '*Attachment*Test'` on macOS/Linux, or the equivalent
`gradlew.bat` command on Windows. The tiny original H.264 fixture is retained
only for rejection tests, never played. CI keeps the Linux full build and
Windows/macOS attachment tests, with no native media codec requirement.
Any legacy schema-2 MP4 metadata/blobs are preserved but cannot be opened.
No data migration is needed for this policy change. Passing source tests is
not a clean-machine packaging certificate; packaging remains #63.

## Adding dependencies

Production dependencies require team approval. Record why a dependency is
needed, prefer a maintained library with a narrow purpose, pin its version, and
verify its license before adding it.

## Product references

The canonical requirements are in `.agents/`. Read the applicable lifecycle,
authorization, anonymity, audit, persistence, SLO, and testing documents before
changing related behavior.
