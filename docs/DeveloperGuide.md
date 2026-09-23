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
.\gradlew.bat shadowJar
```

On macOS or Linux, replace `.\gradlew.bat` with `./gradlew`.

The executable Shadow JAR is written to `build/libs/incident-desk.jar`. It
contains JavaFX native libraries for Windows, macOS, and Linux.

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

## Adding dependencies

Production dependencies require team approval. Record why a dependency is
needed, prefer a maintained library with a narrow purpose, pin its version, and
verify its license before adding it.

## Product references

The canonical requirements are in `.agents/`. Read the applicable lifecycle,
authorization, anonymity, audit, persistence, SLO, and testing documents before
changing related behavior.
