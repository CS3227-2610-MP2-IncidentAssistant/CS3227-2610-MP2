---
layout: default
title: Developer Guide
nav_order: 3
---

# Incident Desk Developer Guide

## Table of Contents

- [Acknowledgements](#acknowledgements)
- [Design and implementation](#design-and-implementation)
  - [Repository layout](#repository-layout)
  - [Architecture](#architecture)
  - [Component responsibilities](#component-responsibilities)
  - [Sessions and password handling](#sessions-and-password-handling)
  - [Incident changes and audit](#incident-changes-and-audit)
  - [Access control and anonymity](#access-control-and-anonymity)
  - [Search and filtering](#search-and-filtering)
  - [Comments and attachments](#comments-and-attachments)
  - [Audit and notifications](#audit-and-notifications)
  - [Local storage and recovery](#local-storage-and-recovery)
  - [SLOs and statistics](#slos-and-statistics)
  - [Other design decisions](#other-design-decisions)
- [Product scope](#product-scope)
  - [Principal user stories](#principal-user-stories)
- [Development process and testing](#development-process-and-testing)
- [Non-functional requirements](#non-functional-requirements)
- [Glossary](#glossary)
- [Appendix: Instructions for Manual Testing](#appendix-instructions-for-manual-testing)
- [Future enhancements and current limitations](#future-enhancements-and-current-limitations)

## Acknowledgements

The project uses [JavaFX](https://openjfx.io/) for its UI,
[Gradle](https://gradle.org/) and the [Shadow plugin](https://gradleup.com/shadow/)
for builds, [JUnit 5](https://junit.org/junit5/) for tests,
[PlantUML](https://plantuml.com/) for diagram sources, and
[GitHub Actions](https://docs.github.com/actions) for CI. Add any further reused
ideas, code, assets, or documentation here before release.

## Design and implementation

This section covers the repository structure and the implementation boundaries
for authentication, incident changes, access control, queries, comments,
attachments, audit, notifications, storage, and SLOs.

### Repository layout

```text
CS3227-2610-MP2/
├── src/main/java/com/company/incidentdesk/
│   ├── startup/          runtime setup and dependency assembly
│   ├── ui/               JavaFX pages and shared components
│   ├── application/      use cases, authorization, and notifications
│   ├── domain/           incident rules, audit values, and SLO calculations
│   └── persistence/      repository interfaces and local-file adapters
├── src/main/resources/   JavaFX styles and other application resources
├── src/test/java/        JUnit tests mirroring the application packages
├── src/test/resources/   test fixtures
├── docs/                 guides, diagrams, and screenshots
├── .agents/              product and engineering contracts
├── .github/workflows/    CI and release workflows
├── logs/                 AI usage and development logs
├── release/              packaged application JARs
├── build.gradle          build configuration
└── gradlew(.bat)         Gradle wrapper scripts
```

### Architecture

Incident Desk is one JavaFX process with local-file persistence. Startup builds
shared services once; role pages call application services, which enforce rules
before using persistence interfaces. The domain has no JavaFX or file-system
dependency.

![Incident Desk component architecture](diagrams/architecture.png)

The [editable architecture source](diagrams/architecture.puml) shows the major
dependencies. `LocalApplicationStore` implements the persistence interfaces;
the services depend on those interfaces, not on the local adapter. The explicit
application layer is important because session-derived actors, authorization,
and atomic audit are use-case concerns rather than UI or entity concerns.

### Component responsibilities

| Component | Responsibility | Main examples |
| --- | --- | --- |
| Startup | Check runtime, acquire the data-directory lock, and assemble shared dependencies | `IncidentDeskLauncher`, `IncidentDeskApplication`, `ApplicationContext` |
| UI | Navigate between role pages, collect input, and display safe models | `ApplicationNavigator`, `DefaultViewFactory`, `ReporterIncidentPage`, `IncidentTable` |
| Application | Authorize and coordinate use cases, audit, events, and presentation mapping | `IncidentService`, `AttachmentService`, `SessionService` |
| Domain | Enforce lifecycle rules and calculate SLOs from persisted times | `IncidentLifecycle`, `SloCalculator` |
| Persistence | Commit aggregate state and store authorized attachment bytes | `LocalApplicationStore`, `RecoverySafeFile` |
| Notifications | Convert committed events into recipient-specific in-memory inbox entries | `NotificationService`, `NotificationInbox`, `NotificationCenter` |

`ApplicationContext` owns the process-wide services and store.
`ApplicationNavigator` switches between authentication and the role-specific
shell. Shared UI components consume presentation models rather than unrestricted
domain entities. Hiding a control improves usability but never replaces a
service-side authorization check.

### Sessions and password handling

`InMemorySessionService` holds one process-local session. A successful login
replaces the active session; a rejected login leaves it unchanged, and logout
or account invalidation clears it. Use cases obtain the enabled actor from the
session service when each operation runs rather than trusting an actor ID from
the UI.

`AccountRegistrationService` stores salted PBKDF2-HMAC-SHA256 credentials, not
plaintext passwords. Password changes recheck the current password and save the
new credential with its audit event. Administrator resets create a temporary
credential that expires after 24 hours, display it once, and require a password
change after login. Resetting also invalidates the target's active session.

### Incident changes and audit

`IncidentLifecycle` owns the legal transitions among `DRAFT`, `SUBMITTED`,
`ASSIGNED`, `RESOLVED`, and `WITHDRAWN`. An incident has at most one assignee.

| From | Action | To | Actor and condition |
| --- | --- | --- | --- |
| none | Save draft | `DRAFT` | Reporter; incomplete content is allowed |
| none or `DRAFT` | Submit | `SUBMITTED` | Owning Reporter; required content is valid |
| `DRAFT` or unassigned `SUBMITTED` | Edit | unchanged | Owning Reporter |
| unassigned `SUBMITTED` | Withdraw | `WITHDRAWN` | Owning Reporter |
| unassigned `SUBMITTED` | Claim | `ASSIGNED` | Responder with access to the incident category |
| `SUBMITTED` or `ASSIGNED` | Assign or reassign | `ASSIGNED` | Administrator; target Responder is eligible |
| `ASSIGNED` | Resolve | `RESOLVED` | Assigned Responder or Administrator; remarks are non-blank |
| `ASSIGNED` | Hand off | `SUBMITTED` | Assigned Responder or Administrator |
| `RESOLVED` | Reopen with follow-up | `SUBMITTED` | Owning Reporter; explanation is non-blank |

The domain also supports saving a `DRAFT`; the table describes domain and
service behavior, while the UG identifies which workflows are exposed in the
current UI. Handoff preserves the queue position in the current cycle.
Reopening starts a new cycle and retains earlier resolution history.

The submission path illustrates the mutation boundary:

![Reporter incident submission sequence](diagrams/incident-submission.png)

In the [editable sequence source](diagrams/incident-submission.puml),
`ReporterPage` passes the selected anonymous flag to `IncidentService.submit`.
The service obtains the actor from the session, validates the request, creates
the incident and `INCIDENT_CREATED` evidence, and commits both through
`IncidentStore.commit(new AuditedMutation<>(...))`. The store replaces its
in-memory state only after the durable save succeeds. Only then does the
service publish `IncidentChangedEvent`.

The same principle applies to other sensitive changes:

- A Reporter may edit or withdraw only their own unassigned `SUBMITTED`
  incident. Assignment and state are checked again when the action executes.
- Reopening requires a non-blank follow-up. The new queue cycle, explanatory
  comment, and audit event commit together; an ordinary comment does not reopen.
- Claim, resolution, handoff, reassignment, account access changes, and SLO
  changes also enforce their own actor and state rules before committing.
- A failed validation or save produces no successful mutation, audit event,
  notification, or change event.

### Access control and anonymity

Application policies are deny-by-default. Reporters read only incidents they
submitted. Responders see unassigned work only in permitted categories and
their own assigned work. Administrators have broader operational access, but
normal views still redact an anonymous Reporter's identity.

Category access is checked again when a Responder lists or claims unassigned
work. Removing a category immediately removes that queue access, but does not
remove the Responder's visibility or ability to resolve or hand off incidents
already assigned to them. Those assignments remain until resolution, handoff,
or reassignment.

List filters are not access checks. Detail reads, attachment reads, and
mutations re-evaluate the current session and incident state. Missing and
inaccessible incidents receive the same presentation-safe result.
`IncidentPresentationMapper` supplies redacted incident rows and details;
audit actor labels, notifications, and statistics apply their own privacy-safe
presentation rules. Anonymous submissions retain an internal owner reference
so the author can track them; they do not conceal identity from the local data
owner.

### Search and filtering

`IncidentSearchCriteria` carries user-selected filters separately from the
role-specific `IncidentQuery` scope. Text is trimmed and matched
case-insensitively against incident ID, title, or description. Multiple values
within a category, status, or SLO filter are alternatives; different filter
groups combine together. Empty selections mean no restriction. Assignment can
be any, assigned, or unassigned; creation-date bounds are inclusive.

Reporter and Responder identity filters apply only where that identity is
visible; Reporter identity filtering excludes anonymous reports. Sorting uses
the selected field and direction, then incident ID as a stable tie-breaker.
The default sort is creation time descending. Query scope and current
authorization determine which records can be returned; filters never grant
access.

### Comments and attachments

`IncidentCommentService` checks current incident visibility before listing or
adding comments. An ordinary comment records the authenticated author and does
not change lifecycle state. Reopening is a separate operation: the owning
Reporter must provide a non-blank explanation, which is committed with the new
queue cycle and audit event. For anonymous incidents, the Reporter's comment
author label is shown as `Anonymous reporter` to other roles.

`AttachmentService` applies the same current incident-visibility check before
listing or opening an attachment. Only the owning Reporter can add an image
while the draft or submitted report is editable and unassigned. Access is
checked again before bytes are returned, so a stale view or changed session
does not preserve access. Attachments are PNG/JPEG images, limited to 10 MiB
each, five files and 100 MiB per incident, and 40 million decoded pixels.
Generated storage names prevent local paths from reaching presentation models.
Pending-upload markers support cleanup after interrupted attachment operations.
An upload does not change an incident's lifecycle or SLO timestamps, and failed
upload does not leave committed metadata or success audit evidence.

### Audit and notifications

An `AuditEvent` records a generated ID, application UTC timestamp, actor ID and
role, actor-visibility setting, action, typed target, outcome, structured
changes, and an optional evidence reference. Audit data omits passwords,
session identifiers, and unnecessary incident text. Normal application
operations append audit events as part of the same durable mutation as the
change they record.

After a successful commit, the event bus publishes application events.
`NotificationService` uses those events to create notifications only for
currently authorized recipients; `NotificationInbox` is in-memory and is not
the audit trail. A failed save publishes no success event or notification.

### Local storage and recovery

Runtime data is never written into the JAR. `ApplicationDataDirectory` resolves
the data directory from the `incidentdesk.dataDir` system property, then
`INCIDENT_DESK_DATA_DIR`, then `.incident-desk` in the user's home directory.
`LocalApplicationStore` holds a process lock to reject concurrent writers.

`incident-desk.dat` contains version-1 aggregate state: accounts, credentials,
incidents, comments, audit events, promotion requests, SLO history, and
attachment metadata. Attachment bytes use generated names in separate files.
`RecoverySafeFile` validates a temporary file, keeps a bounded backup, and
replaces the canonical file atomically where supported. Corrupt data or an
unsupported newer schema stops startup rather than silently resetting records.

Backup restoration exists in the storage layer, but it is not an authenticated,
audited user workflow. Do not expose it through the UI without authorization,
confirmation, and audit design. Tests use disposable directories, never a
user's actual data directory.

### Other design decisions

| Area | Decision and reason | Trade-off |
| --- | --- | --- |
| Queries | Services return authorized, privacy-safe rows to shared tables | Mapping stays outside the views |
| Attachments | Verify PNG/JPEG content and size; authorize every list, add, and open | Original image content and metadata can reveal identity |
| Audit | Commit required evidence with the change | Aggregate saves rewrite the local state file |
| SLOs | Use persisted UTC lifecycle times and versioned targets | More history is retained, but later targets do not rewrite past results |
| Notifications | Publish events after commit to an in-memory inbox | Inbox history does not survive restart; audit remains durable |

### SLOs and statistics

`SloCalculator` uses persisted lifecycle timestamps. For each cycle, time to
claim is first assignment minus queue entry; time in progress is resolution
minus first assignment. Reopen rate is the number of distinct resolved
incidents reopened in the selected scope and period divided by the number of
incidents resolved there. Period bounds are inclusive; unresolved incidents
do not contribute to resolution-based measures.

Handoff retains the current cycle's queue-entry and first-assignment times;
reopening creates a new cycle. Per-category target versions apply from their
effective time, so later target changes do not rewrite earlier evaluations.
Calculations use application UTC instants and return no average or rate when
there is no applicable population.

## Product scope

Incident Desk supports local incident reporting and handling by three roles:

- **Reporter:** submit and track owned incidents, edit or withdraw eligible
  reports, and explain why a resolved report must be reopened.
- **Responder:** view eligible and assigned work within granted categories,
  claim, resolve with remarks, or hand off an assigned incident.
- **Administrator:** review incidents and audit evidence, manage accounts and
  category access, configure SLOs, and inspect statistics.

### Principal user stories

| Role | Need |
| --- | --- |
| Reporter | Register, sign in, submit, and track only incidents they submitted |
| Reporter | Add permitted images before assignment and understand privacy risks |
| Reporter | Withdraw an unassigned incident or reopen an inadequate resolution |
| Responder | See and claim unassigned incidents only in permitted categories |
| Responder | Resolve or hand off incidents assigned to them |
| Administrator | Review incidents, accounts, audit evidence, SLOs, and statistics |
| Administrator | Approve promotion and control Responder category access |

The [User Guide](UserGuide.md) describes the currently available controls. The
[product requirements](https://github.com/CS3227-2610-MP2-IncidentAssistant/CS3227-2610-MP2/blob/main/.agents/requirements.md)
and [MVP scope](https://github.com/CS3227-2610-MP2-IncidentAssistant/CS3227-2610-MP2/blob/main/.agents/mvp-scope.md)
contain the full user stories and boundaries. A service method or Sample UI
preview is not proof that a feature is available in the authenticated product.

## Development process and testing

### Set up the project

Use JDK 25 and Git. The repository includes the Gradle wrapper; no separate
Gradle installation is needed. Check that both `java -version` and
`javac -version` report 25, then open the repository root as a Gradle project.

From the repository root on Windows:

```powershell
.\gradlew.bat test
.\gradlew.bat check
.\gradlew.bat run
.\gradlew.bat shadowJar
```

On macOS/Linux, use `./gradlew` instead. The main
`build/libs/incident-desk.jar` contains x86_64 JavaFX natives for Windows,
macOS, and Linux. On Apple Silicon, build `shadowJarMacArm64` and run
`build/libs/incident-desk-mac-aarch64.jar` with an ARM64 JDK 25. `assemble`
builds both artifacts. `previewAuthenticatedShell` uses temporary in-memory
accounts and accepts any password; it is not the production authentication
workflow.

### Change and verification workflow

1. Read the applicable `.agents/` contract and inspect the existing code.
2. Make a small change in the layer that owns the rule. Keep UI controls and
   persistence adapters from becoming alternate authorization paths.
3. Test successful, denied, stale-state, and failure paths as applicable.
   Persistence tests use isolated directories and verify durable state and
   audit evidence, not only return messages.
4. Run focused tests, then `check`. Review the diff for unrelated changes,
   generated output, runtime data, and sensitive information.
5. Update the UG for observable changes and this guide for design changes.
   Report commands actually run, skipped checks, and remaining risks.

Tests mirror production packages. Domain and application tests use deterministic
clocks and IDs where possible. `check` runs JUnit, `verifyShadowJar`, and
`verifyJavaFxPlatforms`; there is currently no Checkstyle plugin. CI exercises
the build and packaged startup on supported targets, but does not replace an
interactive JavaFX check. `PackagedApplicationTest` launches a matching JAR
with temporary storage and requests a normal shutdown through a test-only
observer.

`docs/diagrams/` contains editable `.puml` sources and rendered images.
Production dependencies require approval, a narrow purpose, a pinned version,
and a license check.

## Non-functional requirements

- Run as a Java 25 desktop application with a packaged executable JAR.
- Preserve canonical state and required audit evidence across restarts, while
  refusing concurrent writers to one data directory.
- Enforce authorization in application logic and avoid exposing anonymous
  identity, credentials, sessions, or storage paths through presentation data.
- Keep JavaFX responsive during storage and attachment work.
- Calculate lifecycle and SLO measures from persisted UTC timestamps.
- Recover from interrupted writes without silently overwriting corrupt data.
- Isolate tests from real user data and keep secrets out of logs and audits.

## Glossary

| Term | Meaning |
| --- | --- |
| Audit event | Durable evidence of an incident-changing or sensitive operation |
| Canonical file | `incident-desk.dat`, the authoritative aggregate-state file |
| Presentation model | Authorized and privacy-safe data supplied to a UI component |
| Queue cycle | Submission or reopening through first assignment; handoff stays in the same cycle |
| Resolution cycle | Assignment-through-resolution history for one handling cycle |
| SLO | Service-level objective calculated from persisted times and versioned targets |
| Tombstoned account | Disabled account whose ID remains for historical references |

## Appendix: Instructions for Manual Testing

Use a new disposable directory through `INCIDENT_DESK_DATA_DIR`; never run
destructive tests against real user data. Record the OS, CPU architecture,
JDK, JAR, launch command, and observed result. The [User Guide](UserGuide.md)
explains routine controls; these checks add test data and expected outcomes.

### Launch, accounts, and restart

1. Launch the matching JAR with JDK 25. A sign-in window should open; data
   files should appear only in the disposable directory.
2. Start a second process against the same directory. It should be refused
   without changing canonical data.
3. Register two Reporters, two Responders, and an Administrator. Try a
   duplicate login name and an incorrect password; both should fail without
   opening a session.
4. Change a password using first an incorrect, then the correct current
   password. Only the latter should succeed; the old password should fail.
5. Close and relaunch after creating an incident. Accounts, incident state,
   audit evidence, and supported attachments should remain available.
6. Reset a disposable account's password as Administrator. The temporary
   password should be shown only once; the account should have to replace it
   after sign-in. Delete a different disposable account and confirm it can no
   longer sign in while its historical references remain.

### Incident and access flow

1. As Reporter, submit `Water leak near pantry` in **Facilities**, with
   description `Water is dripping beside the third-floor pantry.` It should
   appear in **My incidents** as `SUBMITTED`.
2. As Administrator, grant **Facilities** to the Responder in **Accounts**.
   The Responder should now see that incident in the eligible queue. Without
   this category, the queue should not expose it.
3. As Responder, claim it. It should leave the eligible queue, appear in the
   assigned list. Try blank resolution remarks, then `The leak was repaired
   and the floor was dried.` Only the non-blank remarks should resolve it.
4. As Reporter, inspect the resolution and reopen with `Water is still
   dripping from the same pipe.` A blank explanation should fail; the valid
   explanation should add a follow-up and return the incident to the queue.
5. Before assignment, edit and withdraw a separate report. After assignment,
   those actions should be unavailable or rejected even from a stale screen.
6. Grant Facilities to the second Responder. As Administrator, assign an
   eligible incident, reassign it to that Responder, then unassign it. The
   assignee and queue should reflect each change. A Responder without
   Facilities access must not be selectable.
7. On the Administrator dashboard, search `pantry`, filter to **Facilities**
   and **Submitted**, then reset the filters. The matching row should appear
   under the filter and the complete authorized list after reset. Also try
   assignment, reporter, responder, date, and SLO filters, then change the
   sort field and direction; results should follow the selected criteria.
8. Sign in as the second Reporter. The first Reporter's incident must not
   appear in **My incidents**. For a separate Facilities incident, have a
   Responder claim it, then remove that Responder's Facilities access. New
   unassigned work should disappear, but the existing assignment should
   remain actionable.
9. As Administrator, resolve a separate assigned incident with non-blank
   remarks. Its status and resolution history should update; blank remarks
   should be rejected without changing the incident.

### Privacy, attachments, and oversight

1. Submit another incident with **Submit anonymously**. The author should
   still see it in **My incidents**; other roles should see **Anonymous
   reporter**, including in details and audit-facing labels. Follow-up text
   should use a neutral author label for other roles.
2. Add a small PNG or JPEG to an eligible saved report, then list and view it
   as an authorized account. Try an unsupported or oversized file; it should
   leave no visible attachment or successful audit entry. Log out while the
   viewer is open; the previous image must not remain accessible.
3. Inspect audit entries for account access, submission, claim, resolution,
   and reopening. Configure a Facilities SLO and inspect statistics after a
   completed cycle. No view should disclose credentials or anonymous identity.
4. Submit two incidents, claim the older one, then hand it off. Its assignee
   should clear and it should regain its original relative queue position.
5. Add `Please check the ceiling above the sink.` as an ordinary comment. It
   should appear in the thread without reopening or otherwise changing status.
   Open the notification bell after a relevant change; its inbox should update
   during the session but not survive a restart.
6. In **SLO configuration**, save Facilities targets `60` minutes to claim,
   `240` minutes in progress, and `10` percent reopen rate. Invalid values
   should be rejected. Filter **Statistics** by category and date; an empty
   population should not produce invented averages. Open an **Audit log** row
   and inspect its recorded action and outcome.

### Storage integrity checks

Save failure, corrupt-file handling, and backup restoration need controlled
storage conditions rather than ordinary UI input. Run `RecoverySafeFileTest`
and `LocalApplicationStoreTest` against their temporary directories to check
file integrity and corrupt-data handling. `IncidentServiceTest` covers the
application result and lack of a new success event after persistence failure.
Backup restoration is a storage-layer developer test, not a production UI
workflow.

## Future enhancements and current limitations

The [User Guide](UserGuide.md#data-storage-and-current-limitations) lists
current user-facing omissions. Supporting services or a Sample UI preview do
not make a workflow available in the authenticated application. A Reporter
promotion-request form is proposed, not implemented.

Development boundaries still to resolve are:

- Backup restoration has no authenticated, audited UI. The persistence contract
  still mentions a schema-2 attachment migration, while the current codec uses
  schema version 1; resolve that mismatch before changing the format.
- Windows development currently supports x86_64; Apple Silicon requires its
  separate JAR. The build has no Java static-style plugin.
- Server deployment, concurrent writers, and cross-device synchronization are
  outside the approved local-desktop scope.

Future work must update the relevant contract, implementation, tests, UG, and
DG together. This section does not authorize a change by itself.
