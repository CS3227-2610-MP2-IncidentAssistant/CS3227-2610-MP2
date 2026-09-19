# Testing Requirements

## General approach

- Keep domain and authorization logic testable without launching the UI.
- Return stable application error codes and field validation results so UI code
  never parses exception messages.
- Use deterministic clocks and identifier generators in tests.
- Use a fresh temporary application-data directory for every persistence test.
- Never access a developer's or user's real application-data directory.
- Test both successful and denied paths.
- Verify persisted state and audit evidence rather than trusting status text.

## Required suites

### Authentication and accounts

- Registration validation and duplicate identities.
- Case-sensitive login lookup, including distinct accounts whose names differ
  only by letter case.
- Login success/failure, logout, and session replacement.
- Password hashes are salted and plaintext is absent from stored files/logs.
- Promotion approval/rejection and category-access changes.
- Password reset invalidates reset credentials after one use.
- Account deletion disables login while preserving tombstoned history.

### Authorization

- Map access denial and missing resources to identical presentation-safe
  resource-unavailable errors.
- Each row of `.agents/authorization-matrix.md` has allowed and denied tests.
- Reporters cannot enumerate or access another reporter's incidents.
- Responders cannot access categories not assigned to them.
- Responders cannot modify incidents assigned to someone else.
- Authorization is rechecked after role/category changes.
- UI-hidden actions also fail when invoked directly at the domain boundary.

### Incident lifecycle

- Required submitted-incident title, description, and category validation,
  including incomplete draft text, title stripping, and preservation of
  description and resolution whitespace.
- Every transition and invalid transition in
  `.agents/incident-lifecycle.md`.
- Editing/withdrawal before and after assignment.
- Resolution requires remarks.
- Reopening succeeds only for the owning reporter and only with a non-blank
  explanatory follow-up comment.
- Reopening appends the comment, preserves prior resolution history, and commits
  the comment, state transition, and audit event as one logical operation.
- Empty or whitespace-only comments and follow-ups on non-resolved incidents do
  not reopen an incident.
- Handoff preserves the original queue position.
- Admin reassignment rejects ineligible responders.

### Privacy and attachments

- Anonymous identity is absent from every display/export model and log.
- The owning reporter can still access an anonymous incident.
- Attachment size/type/path validation and traversal attempts.
- Attachment reads require current incident authorization.
- Stored filenames do not disclose usernames or original paths.

### Persistence and recovery

- Round-trip all entity and lifecycle variants through storage.
- Restart recovery preserves data and relationships.
- Failed validation leaves canonical data unchanged.
- Simulated interrupted writes and corrupt temporary/canonical files.
- Unknown schema version and tested migrations from supported old versions.
- A second application writer is refused.
- Audit event and domain mutation commit or fail together.

### Audit and SLO

- Every required operation produces the correct redacted audit event.
- Audit ordering and tombstoned actors.
- All formulas and edge cases in `.agents/slo.md`.
- Filters do not leak anonymous identity.

## Completion evidence

Before declaring a change complete, report:

- Test/build commands run.
- Passed and failed results.
- Checks not run and why.
- Relevant manual verification, if any.
- Persisted-format or migration impact.
- Remaining assumptions or risks.
