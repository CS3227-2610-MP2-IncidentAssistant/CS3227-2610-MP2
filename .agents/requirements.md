# Product Requirements

## Product goal

Provide a local company incident-reporting application in which reporters can
raise and track issues, authorized responders can handle them, and
administrators can govern access and review operational performance.

The application is a Java desktop application distributed as a single Shadow
JAR. It stores application data and attachments in local files. Multiple
accounts may exist, but only one user can operate the application at a time.

## Shared capabilities

- Login, logout, and a single active application session.
- Login names are case-sensitive. Names that differ only by letter case identify
  distinct accounts.
- User registration.
- Role- and category-based access control.
- Reusable table/list views with searching, filtering, and sorting where
  required.
- Incident list search trims input and matches title, description, or incident
  identifier case-insensitively. Category/status selections use OR within each
  group and all filter groups combine with AND. Empty selections mean "all".
- Incident filters support assignment state, inclusive creation-date bounds,
  evaluated SLO state, and authorized reporter/responder identities. Reporter
  identity filtering excludes anonymous incidents. The default ordering is
  newest creation time first; every ordering uses incident identifier ascending
  as its stable tie-breaker.
- A shared incident-detail view.
- Incident comment threads.
- Image and video attachment upload and rendering.
- Attachments support PNG/JPEG up to 10 MiB and MP4 with H.264 video and
  optional AAC audio up to 50 MiB. An incident permits at most 5 attachments
  and 100 MiB total. Limits are centrally configurable; image decoding is
  limited to 40 million pixels. Unsupported media fails safely in the viewer.
- Only the owning reporter can add attachments to a persisted `DRAFT` or
  unassigned `SUBMITTED` incident. Viewing follows incident-detail permissions.
- In-process notifications/events; notifications do not need to survive an
  application restart unless a later requirement says otherwise.
- SLO countdown or status badges.

## Reporter requirements

A reporter can:

- Submit an incident report, optionally marked anonymous.
- Submitted incident reports require a non-blank title, description, and
  category; incomplete drafts may omit title or description. Titles are
  stripped of leading and trailing whitespace before storage. Descriptions and
  resolution remarks retain entered whitespace after non-blank validation. No
  arbitrary text-length limits are imposed.
- Save an incomplete report as a draft and submit it later.
- Upload supported images and videos and view them in the application.
- View, search, and filter their own incidents and their statuses.
- View incident details and resolution remarks for their own incidents.
- Edit or withdraw an incident while it is submitted and unassigned.
- Reopen their own resolved incident by adding a non-blank follow-up comment
  explaining why the incident remains unresolved or why the resolution was
  insufficient.
- Request promotion to responder by selecting requested incident categories and
  providing optional additional comments.

## Responder requirements

A responder can:

- View and search/filter unassigned incidents in categories assigned to them.
- View and search/filter incidents assigned to them.
- Claim an eligible unassigned incident.
- Resolve an assigned incident with resolution remarks.
- Hand an assigned incident back to its category queue at its original queue
  position.
- View weekly resolved-case counts and the responder SLO measures defined in
  `.agents/slo.md`.

## Administrator requirements

An administrator can:

- View, search, filter, and sort incidents across the company.
- Resolve an incident with resolution remarks.
- Reassign an incident to an eligible responder.
- Review responder-promotion requests and approve or reject them.
- Update a responder's permitted categories.
- Delete an account subject to the retention rules below.
- initiate a secure password reset without learning the existing password.
- Configure SLO targets for each incident category.
- View incident and application audit records.
- View operational statistics filtered by responder, reporter, category, and
  time period, subject to anonymous-report privacy rules.

Statistics include incidents resolved, resolution duration, reopened-incident
counts/rates, and configured SLO performance.

## Data-retention expectations

- Deleting an account must not delete incident or audit history required for
  integrity. Historical references become a non-login tombstoned identity.
- Withdrawing an incident is a lifecycle state, not physical deletion.
- Audit entries are append-only and retained with the application data.
- Attachments belonging only to drafts may be removed when the draft is deleted
  under a future explicit requirement; no such deletion feature is currently
  specified.

## Decisions requiring product confirmation

The following are deliberately not invented by this document:

- Additional incident form fields beyond title, description, and category.
- Whether registration is open or requires admin activation.
- Business-hours, weekend, holiday, and pause rules for SLOs.
- Whether an admin can recover the identity of an anonymous reporter through a
  separate exceptional process.
- Notification presentation and retention.
- Password policy and the exact delivery mechanism for reset credentials.
