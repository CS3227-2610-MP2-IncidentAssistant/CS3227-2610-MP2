---
layout: default
title: Developer Guide
nav_order: 3
---

# Incident Desk Developer Guide

## Table of Contents

- [Introduction](#introduction)
- [Setting up](#setting-up)
- [Architecture](#architecture)
- [Component design](#component-design)
- [Implementation](#implementation)
- [Design considerations](#design-considerations)
- [Development process and testing](#development-process-and-testing)
- [Instructions for manual testing](#instructions-for-manual-testing)
- [Known limitations and planned work](#known-limitations-and-planned-work)
- [Product requirements summary](#product-requirements-summary)
- [Glossary](#glossary)
- [Acknowledgements](#acknowledgements)

## Introduction

This guide is for developers building, testing, or extending Incident Desk. The
[User Guide](UserGuide.md) describes current user-facing workflows. Incident
Desk is a single-process JavaFX desktop application with Reporter, Responder,
and Administrator roles. It uses local files, not a server or database. Some
application services support operations that the authenticated UI does not yet
expose; this guide marks those cases rather than presenting them as complete
user workflows.

The repository's [`.agents/` documents](https://github.com/CS3227-2610-MP2-IncidentAssistant/CS3227-2610-MP2/tree/main/.agents)
are the authoritative contracts. Read the
[product requirements](https://github.com/CS3227-2610-MP2-IncidentAssistant/CS3227-2610-MP2/blob/main/.agents/requirements.md),
[MVP scope](https://github.com/CS3227-2610-MP2-IncidentAssistant/CS3227-2610-MP2/blob/main/.agents/mvp-scope.md),
[incident lifecycle](https://github.com/CS3227-2610-MP2-IncidentAssistant/CS3227-2610-MP2/blob/main/.agents/incident-lifecycle.md),
[authorization matrix](https://github.com/CS3227-2610-MP2-IncidentAssistant/CS3227-2610-MP2/blob/main/.agents/authorization-matrix.md),
[anonymity policy](https://github.com/CS3227-2610-MP2-IncidentAssistant/CS3227-2610-MP2/blob/main/.agents/anonymity-policy.md),
[audit policy](https://github.com/CS3227-2610-MP2-IncidentAssistant/CS3227-2610-MP2/blob/main/.agents/audit-policy.md),
[SLO definitions](https://github.com/CS3227-2610-MP2-IncidentAssistant/CS3227-2610-MP2/blob/main/.agents/slo.md),
[persistence contract](https://github.com/CS3227-2610-MP2-IncidentAssistant/CS3227-2610-MP2/blob/main/.agents/persistence.md), and
[testing requirements](https://github.com/CS3227-2610-MP2-IncidentAssistant/CS3227-2610-MP2/blob/main/.agents/testing.md)
before changing related behavior.
A concise, publishable summary appears in
[Product requirements summary](#product-requirements-summary), so readers of
the generated site can understand the product without repository-only files.

## Setting up

### Prerequisites

- JDK 25. The application rejects every other Java feature version at startup
  so local and packaged behavior remains reproducible.
- Git. A separate Gradle installation is unnecessary because the repository
  contains the Gradle wrapper.

Confirm that `java -version` and `javac -version` both report version 25. Open
the repository root as a Gradle project in the IDE of your choice.

### Common commands

On Windows:

```powershell
.\gradlew.bat test
.\gradlew.bat check
.\gradlew.bat run
.\gradlew.bat previewAuthenticatedShell
.\gradlew.bat shadowJar
```

On macOS or Linux, replace `.\gradlew.bat` with `./gradlew`.

The executable `build/libs/incident-desk.jar` contains JavaFX natives for
Windows, macOS, and Linux x86_64. JavaFX natives for different CPU architectures
on one OS share filenames, so one JAR cannot contain both Intel and ARM variants.
The main JAR therefore requires an x86_64 JVM on Apple Silicon.

For native Apple Silicon packaging, run:

```sh
./gradlew shadowJarMacArm64
java -jar build/libs/incident-desk-mac-aarch64.jar
```

Use an ARM64 JDK 25 for that launch. `assemble` builds both artifacts;
`shadowJar` builds the main Intel artifact. Development builds select JavaFX
for the Gradle JVM's operating system and architecture. Windows development
currently supports x86_64 only. `verifyJavaFxPlatforms`, included in `check`,
verifies classifier isolation.

`PackagedApplicationTest` launches the matching JAR with `java -jar` using
temporary application storage and a separate JavaFX cache. A test-only observer
requests normal shutdown and is not included in production. CI exercises
packaged startup on Linux/Windows x86_64, macOS x86_64, and macOS ARM64.

`previewAuthenticatedShell` exercises login, session, role routing, shell, and
logout using in-memory accounts named `admin`, `reporter`, and `responder`. The
preview accepts any password. These accounts exist only in the preview process
and are neither persisted nor included in production.

### Repository layout

| Path | Purpose |
| --- | --- |
| `src/main/java/com/company/incidentdesk/domain/` | Entities, lifecycle rules, audit values, and deterministic SLO/statistics calculations |
| `src/main/java/com/company/incidentdesk/application/` | Authenticated use cases, authorization, validation, mapping, events, and notifications |
| `src/main/java/com/company/incidentdesk/persistence/` | Persistence ports plus in-memory and local-file adapters |
| `src/main/java/com/company/incidentdesk/ui/` | JavaFX navigation, role pages, shared components, and theme |
| `src/main/java/com/company/incidentdesk/startup/` | Runtime checks, configuration, and dependency assembly |
| `src/test/java/` | JUnit tests mirroring production packages |
| `docs/` | User/developer guides, diagrams, and screenshots |
| `.agents/` | Authoritative product and engineering contracts |
| `.github/workflows/` | CI and release jobs |
| `build/` | Generated output; never edit or commit it as source |

## Architecture

![Incident Desk component architecture](diagrams/architecture.png)

`ApplicationContext` assembles shared services and storage once. JavaFX views
call application services; services enforce authorization and domain rules
before calling persistence interfaces. `LocalApplicationStore` implements the
incident, account, audit, attachment, and SLO ports. Aggregate state is stored
in one file and attachment content in separate generated files. Diagram arrows
show collaboration or interface implementation; they do not grant permission.

Dependencies point inward:

- Domain code is independent of UI, startup, and file storage.
- Application code depends on domain types and persistence interfaces, not on
  JavaFX or the local-file implementation.
- UI code invokes use cases and consumes presentation-safe models.
- Persistence adapters translate durable data to and from domain types.
- Startup is the composition root and may depend on every layer to wire it.

This deliberately differs from the UI–Logic–Model–Storage structure common in
command-driven AddressBook-derived applications. Incident Desk has an explicit
application layer because authorization, atomic auditing, and session-derived
actors are use-case concerns rather than UI or entity concerns.

## Component design

### Startup and composition

`IncidentDeskLauncher` is the executable entry point.
`IncidentDeskApplication` owns JavaFX startup and shutdown, while
`ApplicationContext` constructs process-wide repositories, services, event bus,
and session state. The file store acquires the process lock during assembly.

Keep dependency construction here. Views and services should receive their
collaborators rather than opening files or constructing alternate repositories.

### UI and navigation

`ApplicationNavigator` owns the authentication boundary and switches between
`AuthenticationPage` and `AuthenticatedShell`. `DefaultViewFactory` creates
role dashboards and incident-detail views from `ApplicationContext` services.
The Reporter dashboard opens owned incidents through `ReporterIncidentPage`,
which wraps the shared `IncidentDetailView` and supplies Reporter-specific edit,
withdrawal, and reopen callbacks.

Shared components under `ui.shared.components` consume presentation models
rather than unrestricted domain objects. They include `IncidentTable`,
`IncidentDetailView`, `IncidentFilterBar`, `AttachmentPane`,
`IncidentCommentThread`, `NotificationCenter`, and `SloProgressBar`. Storage
and image-decoding work runs away from the JavaFX thread. Views discard stale
results when detached, logged out, or superseded by newer requests.

Hiding a control is usability behavior, not authorization. Application services
recheck every operation when it executes.

### Application services

The application layer coordinates use cases and returns stable success/error
results to the UI. It:

- obtains the actor from `SessionProvider`, never from form input;
- applies ownership, role, assignee, and category policies;
- validates input and invokes legal domain transitions;
- creates redacted audit evidence;
- commits related state and audit changes atomically;
- publishes in-process events only after durable commit; and
- maps domain data to privacy-safe presentation models.

Principal services include `IncidentService`, `IncidentDetailService`,
`IncidentCommentService`, `AttachmentService`, account and responder-access
services, `AuditLogService`, `SloConfigurationService`, and
`IncidentStatisticsService`.

### Domain model

`IncidentLifecycle` owns transitions among `DRAFT`, `SUBMITTED`, `ASSIGNED`,
`RESOLVED`, and `WITHDRAWN`. `Incident` retains lifecycle timestamps,
assignment history, resolution cycles, and the queue key required for stable
ordering. Value types validate identifiers, remarks, reopen explanations,
comments, accounts, and audit evidence.

`SloCalculator` and statistics calculators are deterministic domain services.
They consume persisted UTC timestamps and versioned targets; they do not read a
UI clock or JavaFX state.

### Persistence

Application services depend on interfaces such as `IncidentStore`,
`AccountRepository`, `AuditRepository`, `AttachmentStore`, and
`SloConfigurationStore`. In-memory implementations support focused tests.
`LocalApplicationStore` and its facets provide production local-file storage.

`LocalApplicationStateCodec` encodes accounts, credentials, incidents,
comments, audit records, promotion requests, SLO history, and attachment
metadata in `incident-desk.dat`. Attachment bytes live separately under the
same data directory. The persisted schema version is 1.

### Events and notifications

Application events are published only after a successful durable mutation.
`NotificationService` updates the in-memory `NotificationInbox`, which
`NotificationCenter` presents. Notifications are convenience state, not the
audit trail or source of truth. Persistent notification history is out of scope.

## Implementation

### Authentication, accounts, and sessions

Login names are case-sensitive. Password code stores salted hashes and never
exposes plaintext passwords, hashes, reset credentials, or session identifiers
through presentation models, audit changes, or logs.

`SessionService` holds the one active account. Login replaces the session,
logout clears it, and every privileged use case reads the actor when it executes.
Self-service password replacement requires the current password and commits the
new credential with its audit event. Administrator account, promotion, reset,
and category-access operations enforce separate policies and preserve audit
references when an account is disabled.

The UI exposes only a subset of account services. Service availability is not
proof of a complete end-to-end workflow.

### Authorization and privacy-safe reads

Authorization is deny-by-default. Reporters access only incidents they
submitted. Responders access unassigned incidents in permitted categories and
incidents assigned to them. Administrators may inspect all incidents, subject
to anonymous-identity redaction. Mutations add state and eligibility conditions.

List filtering is never reused as authorization. Detail reads and mutations
re-evaluate current permissions. Missing and inaccessible incidents map to the
same presentation-safe result. `IncidentPresentationMapper` removes anonymous
identity before data reaches tables, details, filters, statistics, audit labels,
notifications, or attachment display names.

### Incident lifecycle

| From | Action | To | Authorized actor and conditions |
| --- | --- | --- | --- |
| none | Save draft | `DRAFT` | Reporter |
| none or `DRAFT` | Submit | `SUBMITTED` | Owning reporter; required content is valid |
| `DRAFT` or unassigned `SUBMITTED` | Edit | unchanged | Owning reporter |
| unassigned `SUBMITTED` | Withdraw | `WITHDRAWN` | Owning reporter |
| `SUBMITTED` | Claim | `ASSIGNED` | Responder permitted for the category |
| `SUBMITTED` | Assign | `ASSIGNED` | Administrator; responder is eligible |
| `ASSIGNED` | Resolve | `RESOLVED` | Assigned responder or administrator; non-blank remarks |
| `ASSIGNED` | Hand off | `SUBMITTED` | Assigned responder or administrator; queue key retained |
| `ASSIGNED` | Reassign | `ASSIGNED` | Administrator; replacement is eligible |
| `RESOLVED` | Reopen with follow-up | `SUBMITTED` | Owning reporter; non-blank explanation |

Every successful transition produces one incident audit event. Reopening
commits its explanatory comment, transition, and audit event together and starts
a new queue cycle. Handoff retains the current cycle's original queue position.
Resolution history remains append-only across reopen cycles.

### Incident submission and atomic audit

![Reporter incident submission sequence](diagrams/incident-submission.png)

`ReporterPage` submits asynchronously through `IncidentService.submit(...)`,
passing `anonymous=false` in the current UI. The service derives the actor,
authorizes and validates the request, and asks `IncidentLifecycle` to create a
submitted incident. It creates `INCIDENT_CREATED` evidence and calls
`IncidentStore.commit(new AuditedMutation<>(...))`.

The store builds the next state with the incident and audit event, saves it,
and only then replaces in-memory state. `IncidentChangedEvent` is published
after commit. Validation, authorization, or storage failure retains the form
input and produces no success event.

### Reporter incident actions (#90)

`ReporterPage` reads the authenticated user's incident rows; opening a row routes
through `DefaultViewFactory` to `ReporterIncidentPage`. That page composes the
shared detail and attachment components, and supplies callbacks for editing,
withdrawing, and reopening. The forms perform required-field feedback, then
delegate mutations to `IncidentService`; the service remains responsible for
authorization, lifecycle validation, audit evidence, and persistence. A stale
action rejected after an incident changes triggers a detail refresh. When the
Reporter can still view the incident but can no longer perform that action, the
form retains its text as read-only until dismissed. Attachments use the shared
`AttachmentPane` and existing PNG/JPEG rules; video and audio are unsupported.

### Queue reads, claim, resolution, and handoff

`ResponderPage` reads eligible and assigned queues in deterministic order.
Refresh and navigation recheck the session and category access; detaching the
page clears content and invalidates outstanding reads. `ResponderIncidentPage`
invokes claim, resolution, and handoff and refreshes from committed state.

A responder who loses category access immediately loses unassigned incidents
in that category but retains incidents already assigned to them until resolution,
handoff, or reassignment. Claim and reassignment check eligibility at mutation
time, so a stale list cannot grant access. An incident has at most one assignee.

### Search, filtering, and presentation

`IncidentSearchCriteria`, `IncidentQuery`, and `IncidentSort` describe queries.
Text search trims input and matches identifier, title, or description
case-insensitively. Values within one category/status group use OR; groups
combine with AND. Empty selections mean all values. Date bounds are inclusive
and default ordering is deterministic.

Identity filters operate only on identities the actor may see. Anonymous
reporters are excluded rather than recoverable through counts or labels. Shared
tables receive already authorized `IncidentRowModel` values.

### Comments, reopening, and notifications

`IncidentCommentService` authorizes incident access before returning or adding
comments. Reopening is not a separate comment followed by a transition: its
explanation, new queue cycle, and audit event commit together. Anonymous
Reporter comments use a neutral author label for other roles.

After successful changes, the event bus lets UI and notification components
refresh without treating events as durable state. A failed save produces no
success notification or event.

### Attachments

Use `ApplicationContext.attachments()` and compose
`AttachmentPane(service, savedIncidentId)` into a page. File reads run off the
JavaFX thread. The viewer clears content when session or permission changes.
The pane is composed into Administrator, Reporter, and Responder incident-detail
pages and must receive an already persisted incident.

`AttachmentService.list/add/open` enforce current authorization. Only the
owning Reporter may add files to a draft or unassigned submitted incident.
Upload does not change lifecycle or SLO timestamps. Metadata and
`ATTACHMENT_ADDED` evidence commit together. `AttachmentRead` rechecks access
before exposing bytes and never exposes a storage URI.

Attachments are PNG/JPEG only: at most 10 MiB each, five files and 100 MiB per
incident, and 40 million decoded pixels. Content is verified rather than names
or extensions. Generated UUID filenames are stored under the data directory.
Anonymous display models use generic names and no local paths. Original bytes
and metadata are preserved, so uploaders are warned about possible identity
leakage through image content or metadata.

Pending-upload markers enable targeted restart cleanup. Failed validation or
persistence leaves no committed metadata, success audit, or valid orphan.

### Audit log

Audit events contain a generated ID, application UTC timestamp, actor ID and
role, action, target, outcome where applicable, and structured change summary.
They exclude credentials, sessions, unnecessary incident text, and anonymous
Reporter identity.

`AuditLogService` is Administrator-only and supports deterministic filtering
and ordering. Account deletion preserves a tombstoned actor reference. Normal
operations cannot update or delete audit records. Recovery-safe local storage
is not claimed to be tamper-proof against the machine owner.

### SLOs and statistics

Administrators configure per-category average time-to-claim,
time-in-progress, and reopen-rate targets. `SloConfigurationHistory` versions
targets so later changes do not rewrite historical evaluation.

- Time to claim: first assignment minus queue entry for a queue cycle.
- Time in progress: resolution minus first assignment for a completed cycle.
- Reopen rate: incidents reopened at least once divided by incidents resolved
  for the selected population and period.

Handoff retains queue-entry and first-assignment timing; reopening starts a new
cycle. Calculators use persisted UTC instants and handle empty populations and
zero durations deterministically. Statistics services authorize scope and apply
anonymity rules before returning results.

### Local-file persistence and recovery

The packaged JAR and classpath are read-only. Runtime data is resolved through
one configurable directory. `ApplicationDataDirectory` checks the
`incidentdesk.dataDir` system property, then `INCIDENT_DESK_DATA_DIR`, then
`.incident-desk` under the user's home directory.

`LocalApplicationStore` holds `incident-desk.lock` while open.
`RecoverySafeFile` writes and validates a temporary file, retains one bounded
`.bak`, and atomically replaces the canonical file where supported. In-memory
state changes only after durable save. Corruption or an unsupported newer
schema stops startup; the app does not silently reset or overwrite user data.
The store exposes explicit backup restoration for controlled recovery, but it
is not yet an authenticated, audited application workflow. Do not expose it to
users until the required authorization, confirmation, and audit operation are
implemented. Persistence tests always use fresh temporary directories.

## Design considerations

| Decision | Selected approach | Benefit | Trade-off or rejected alternative |
| --- | --- | --- | --- |
| Deployment | One desktop process and local files | Matches offline, single-user scope | No cross-device synchronization; server/database complexity is outside scope |
| Durable state | Versioned aggregate plus attachment blobs | Cross-entity state and audit commit together | Each logical mutation rewrites the aggregate |
| Write safety | Validated temporary file, backup, atomic replace, lock | Avoids partial canonical state and concurrent writers | More recovery logic than direct overwrite |
| Authorization | Application policies using active session | Protects direct calls and stale screens | Repeated checks are intentional; UI-only checks were rejected |
| Audit | Same commit as domain mutation | No successful required change without evidence | Separate append-after-save could create unaudited changes |
| SLO targets | Versioned, prospective configuration | Historical measures retain meaning | Latest-target retroactivity is simpler but rewrites history |
| Attachments | Verified original bytes under generated names | No lossy conversion or path disclosure | Metadata remains, requiring a privacy warning |
| Notifications | In-memory event-driven inbox | Keeps convenience state separate from truth | History does not survive restart |
| UI reuse | Shared typed components and presentation models | Consistency without unrestricted entities | Role pages still compose workflow-specific behavior |

## Development process and testing

### Automated testing strategy

Tests mirror production packages. Domain, authorization, validation, lifecycle,
audit, and SLO behavior should run without JavaFX. Use deterministic clocks and
IDs. Persistence tests use isolated storage and verify durable state and audit
evidence rather than returned messages.

Every authorization change needs allowed and denied tests. Every lifecycle
change needs successful and invalid states, persistence failure, audit effect,
anonymity impact, and SLO timestamp effect where applicable.

`./gradlew check` runs JUnit, `verifyShadowJar`, and
`verifyJavaFxPlatforms`; no Checkstyle plugin is currently applied. Linux CI
runs the full build under Xvfb. Windows and macOS jobs exercise attachments,
startup, and packaged behavior. Automated checks do not replace interactive
verification on a supported target platform.

### Change workflow

1. Read applicable `.agents/` contracts and inspect existing code.
2. Make the smallest coherent change without moving domain rules into UI or
   persistence code.
3. Add success, denial, failure, and persistence tests.
4. Run focused tests, then `check` where appropriate.
5. Review the diff for unrelated changes, generated output, runtime data, and
   sensitive information.
6. Update the User Guide for observable behavior and this guide for design.
7. Report commands run, results, skipped checks, format effects, assumptions,
   and remaining risks.

### Adding dependencies

Production dependencies require approval. Record the need, prefer a maintained
narrow-purpose library, pin its version, and verify its license.

## Instructions for manual testing

Use an empty disposable directory through `INCIDENT_DESK_DATA_DIR`, never real
user data. Record OS, architecture, JDK, JAR, launch command, and result. These
cases are a baseline, not a substitute for exploratory testing.

### Launch, locking, and restart

1. Launch the matching JAR. Expected: sign-in opens and runtime files appear
   only beneath the disposable directory.
2. Start a second process against that directory. Expected: it is refused
   without altering canonical data.
3. Create accounts and an incident, close, and relaunch. Expected: authorized
   state, audit evidence, and attachments remain available.

### Authentication and password change

1. Register a Reporter and sign in. Expected: the Reporter shell opens.
2. Try duplicate registration and invalid credentials. Expected: a non-secret
   error and no session.
3. Change the password first with an incorrect, then correct current password.
   Expected: the first changes nothing; the second succeeds, the old password
   fails, and no secret appears in diagnostics or audit details.

### End-to-end incident flow

1. As Reporter, submit a Facilities incident. Expected: it appears in the
   Reporter's list as submitted.
2. As Administrator, promote an account to Responder and grant Facilities.
   Expected: the access change is effective and audited.
3. As Responder, claim the incident. Expected: it leaves the eligible queue,
   enters the assigned queue, and cannot be claimed by another Responder.
4. Resolve first with blank, then non-blank remarks. Expected: blank input is
   rejected without change; valid input resolves and is audited.
5. As Reporter, inspect it. Expected: status and resolution are visible.

### Authorization denial

1. As another Reporter, locate or open the first Reporter's incident. Expected:
   it is absent and direct access matches a missing identifier.
2. As a Responder without Facilities, list/open/claim it. Expected: all fail.
3. Remove a category from a Responder with an assigned incident. Expected: no
   new claims, but the assigned incident remains resolvable or handoff-capable.

### Handoff, reopen, and queue order

1. Submit two incidents, claim the older, then hand it off. Expected: assignee
   clears and the incident returns to its original relative queue position.
2. Try reopening a resolved incident with blank, then non-blank explanation.
   Expected: blank input changes nothing; valid input appends the comment and
   starts a new queue cycle at reopen time.

### Anonymous privacy

The production Reporter UI does not expose anonymous submission. Run this only
through an approved fixture or after that UI is implemented.

1. Inspect an anonymous incident as owner, eligible Responder, and Administrator.
   Expected: the owner has access; other roles see `Anonymous reporter` and
   cannot recover identity through details, comments, filters, statistics,
   audit labels, notifications, or attachment names.
2. Reopen with a follow-up. Expected: other roles see its text with a neutral
   anonymous author label.

### Attachments

1. Add a valid PNG/JPEG through an exposed workflow. Expected: only currently
   authorized users can list and open it.
2. Try an oversized image, renamed video, unsupported type, excessive pixels,
   sixth attachment, and aggregate overflow. Expected: rejection without
   metadata, success audit, or visible orphan.
3. Log out while viewing an image. Expected: the viewer clears and prior access
   cannot expose bytes in the new session.

### Audit, SLO, and statistics

1. Inspect audit entries for registration, access change, submission, claim,
   handoff, and resolution. Expected: deterministic order, required changes,
   and no secrets or anonymous identity.
2. Configure SLOs, complete a cycle, change targets, and complete another.
   Expected: each cycle uses its applicable target version; display time zones
   do not alter elapsed durations.
3. Filter statistics containing an anonymous incident. Expected: no identity
   or identifying breakdown is exposed.

### Storage failure and corruption

Use disposable copies only.

1. Simulate save failure during a mutation. Expected: failure, unchanged
   in-memory/canonical state, and no success audit or event.
2. Corrupt the canonical aggregate and restart. Expected: startup stops with a
   recovery-oriented error and does not reset or overwrite the file.
3. Exercise `restoreLastKnownGoodBackup()` only in a disposable developer test.
   Expected: the validated backup replaces canonical state. Do not present this
   as a production user workflow until authorization and audit are added.

## Known limitations and planned work

- Reporter submission currently passes `anonymous=false`; anonymous safeguards
  exist, but submission is not an end-to-end anonymous UI workflow.
- Responder dashboard lacks the full filters, interactive sorting, and SLO
  column.
- Notification history is in-memory and does not survive restart.
- Backup restoration exists at the storage layer but is not yet an
  authenticated, audited application workflow.
- The attachment-compatibility subsection of the
  [persistence contract](https://github.com/CS3227-2610-MP2-IncidentAssistant/CS3227-2610-MP2/blob/main/.agents/persistence.md)
  still
  describes a schema-2 migration, while the current codec and attachment
  implementation keep the aggregate at schema version 1. Resolve that
  authoritative-contract mismatch before changing the persisted format.
- Windows development supports x86_64 only. Apple Silicon uses a separate JAR.
- The build does not enforce Checkstyle or another Java static-style plugin.
- Server/database deployment, concurrent writers, cross-device sync,
  deanonymization, and video/audio attachments are outside approved scope.

Before implementing a planned item, update its authoritative contract,
implementation, tests, User Guide, and this guide together. This list does not
grant new permissions.

## Product requirements summary

### Product scope

Incident Desk provides a local workflow for reporting and resolving company
incidents. Reporters track their cases, Responders work within permitted
categories, and Administrators govern access and operational oversight. It is a
privacy-aware, auditable desktop system that requires no server deployment.

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

### Principal use cases

**Submit:** an authenticated Reporter supplies required content; the application
derives the actor, validates it, and commits the incident and audit together.
Failure leaves no partial incident or success audit.

**Claim and resolve:** an eligible Responder selects an incident; eligibility is
rechecked before assignment. The assigned Responder supplies non-blank remarks,
and resolution plus audit commit together. Stale screens grant no authority.

**Administer access:** an Administrator approves/rejects promotion or changes
categories. State and audit commit together, and subsequent operations use the
new permissions immediately.

### Non-functional requirements

- Run as a Java 25 desktop application distributed as executable Shadow JARs.
- Preserve canonical state and required audit evidence across restart.
- Refuse concurrent writers to one data directory.
- Keep JavaFX responsive during storage and attachment work.
- Enforce authorization outside UI controls.
- Preserve anonymous confidentiality across all presentation paths.
- Use application UTC timestamps and deterministic calculations.
- Recover from interrupted writes without silently destroying user data.
- Isolate tests from real data and keep secrets out of logs and audit details.

## Glossary

| Term | Meaning |
| --- | --- |
| Application-data directory | Configurable directory containing canonical state, lock, backup, attachments, and recovery files |
| Assignment | The single Responder currently responsible for an incident |
| Audit event | Append-only evidence of a sensitive or incident-changing operation |
| Canonical file | `incident-desk.dat`, the authoritative aggregate-state file |
| Handoff | Clear an assignee and return an incident to its original queue position |
| Presentation model | Role-appropriate, privacy-safe data supplied to UI components |
| Queue cycle | Submission/reopen to first assignment; handoff does not start a new cycle |
| Resolution cycle | Assignment-through-resolution history for one handling cycle |
| SLO | Service-level objective calculated from persisted timestamps and versioned targets |
| Tombstoned account | Disabled account whose ID remains for historical references |
| UTC instant | Time-zone-independent persisted time, converted only for display |

## Acknowledgements

The guide's organization and diagrams were informed by the
[Possession Manager Developer Guide](https://github.com/haowern98/CS3227-2610-MP1/blob/master/docs/DeveloperGuide.md).
The diagram style follows its
[architecture source](https://github.com/haowern98/CS3227-2610-MP1/blob/master/docs/diagrams/architecture.puml)
and [sequence source](https://github.com/haowern98/CS3227-2610-MP1/blob/master/docs/diagrams/persistent-change-sequence.puml).
Incident Desk's participants and relationships match this project's code.

The supplied
[KeyContacts Developer Guide](https://ay2425s1-cs2103t-t08-2.github.io/tp/DeveloperGuide.html)
was a reference for component documentation, design alternatives, requirements
summaries, glossary, and manual-test structure; no KeyContacts implementation or
product text was reused.

Diagrams use [PlantUML](https://plantuml.com/). The project uses
[JavaFX](https://openjfx.io/), [Gradle](https://gradle.org/), the
[Shadow plugin](https://gradleup.com/shadow/),
[JUnit 5](https://junit.org/junit5/), and
[GitHub Actions](https://docs.github.com/actions). Add any further reused ideas,
code, assets, or documentation before submission.
