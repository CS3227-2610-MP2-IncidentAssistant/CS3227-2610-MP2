# MVP Scope

## MVP objective

Deliver a coherent end-to-end incident flow with enforced authorization:
registration/login, reporter submission, responder claim and resolution, and
administrator oversight and account permissions.

## Included in MVP

### Shared

- Local user registration, login, logout, and one active session.
- Local-file persistence and restart recovery.
- Role- and category-based authorization.
- Basic incident list and detail views.

### Reporter

- Submit a non-draft incident.
- View the reporter's own incident list, details, status, and resolution notes.
- Upload and view supported attachments if attachment support is selected for
  the implementation milestone.

### Responder

- View incidents in assigned categories.
- Claim an unassigned incident.
- View claimed incidents.
- Resolve a claimed incident with remarks.

### Administrator

- View and filter all incident reports.
- Review accounts and approve responder promotion.
- Update responder category access.

## Post-MVP unless scheduled earlier

- Draft reports.
- Reporter editing and withdrawal.
- Anonymous reports.
- Reporter reopening with a required explanatory follow-up comment.
- Responder handoff.
- Admin reassignment and direct resolution.
- Account deletion and password-reset workflow.
- SLO configuration, countdown badges, and statistics.
- Personal responder statistics.
- Full audit-log user interface.
- Advanced searching, filtering, and sorting.
- Persistent notification history.

## MVP exit criteria

- A registered reporter can submit and later view an incident.
- An authorized responder can see, claim, and resolve it.
- An unauthorized responder cannot see or modify incidents outside assigned
  categories.
- An admin can grant responder status/category access and monitor incidents.
- State survives a normal application restart.
- Required authorization, lifecycle, and persistence tests pass.

This file sets delivery priority, not permission to violate future-facing domain
invariants. Post-MVP data structures should not be prematurely implemented, but
MVP choices should avoid making the documented lifecycle impossible.
