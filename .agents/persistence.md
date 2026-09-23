# Local-File Persistence

## Architecture

The application runs as one Java process from a Shadow JAR and uses local files
as its canonical data store. Only one application instance and one active user
are supported at a time.

Do not add a database or network service to emulate one. Keep persistence behind
interfaces so domain and UI code do not depend on serialization details.

## Data location

- Treat the JAR and classpath resources as read-only.
- Resolve a configurable application-data directory at startup.
- Keep runtime data, attachments, backups, and diagnostic logs outside the JAR.
- Never commit runtime data to the repository.
- Use stable generated identifiers for entities and stored attachment names.

## Safe-write protocol

For a logical state change:

1. Validate and authorize the complete operation in memory.
2. Build the new domain state and associated audit event without mutating the
   currently persisted representation.
3. Serialize to a temporary file in the same filesystem as the target.
4. Flush and close the temporary file.
5. Validate that the temporary representation can be read and satisfies schema
   invariants.
6. Replace the canonical file atomically when the platform supports it; use a
   documented backup-and-replace fallback otherwise.
7. Retain or rotate a last-known-good backup according to a bounded policy.

Never write incrementally into the only canonical copy.

## Startup and recovery

- Detect an already running application using a process lock in the data
  directory. Refuse a second writer with a clear message.
- Include an explicit data-format/schema version.
- Reject unknown newer formats without overwriting them.
- Validate identifiers, references, roles, categories, lifecycle invariants,
  and required fields when loading.
- On corruption, do not silently reset to empty data. Preserve the damaged file,
  report the problem, and offer only documented recovery paths.
- Migrations require a backup, deterministic migration logic, and migration
  tests using old-format fixtures.

## Attachments

- Copy approved files into an attachment directory; do not depend on the user's
  original path.
- Validate configured byte-size and supported-type limits before copying.
- Detect type from content where feasible rather than trusting the extension.
- Store generated filenames and retain the sanitized original name only as
  display metadata.
- Normalize and verify resolved paths remain inside the attachment directory.
- Do not execute attachments or serve them without incident authorization.

## Scope limitation

Single-user operation removes simultaneous user races, including concurrent
incident claims. It does not remove the need to handle crashes, disk-full
errors, interrupted writes, duplicate application instances, corrupt files, or
authorization between sequentially logged-in accounts.

## Implemented local format and recovery

- The application resolves its data directory from the
  `incidentdesk.dataDir` system property, then `INCIDENT_DESK_DATA_DIR`, and
  otherwise uses `.incident-desk` under the current user's home directory.
- `incident-desk.dat` is one versioned aggregate state file. Schema version 1
  persists accounts, incidents, comments, audits, and SLO target configuration
  history, and reserves empty placeholder sections for attachment metadata and
  promotion requests. The whole file shares one schema version; there is no
  independent per-section versioning.
- Each successful replacement retains one bounded last-known-good copy at
  `incident-desk.dat.bak`. Unique unfinished temporary files are never treated
  as canonical state.
- Corrupt canonical data and unknown newer schemas stop startup without
  modifying any data. Recovery from the backup is an explicit operation, not
  an automatic reset.
- `incident-desk.lock` is held for the JavaFX application lifetime. Failure to
  acquire it means another writer is active and startup is refused.
- Schema migrations are introduced only when an older supported schema exists.
  Each migration must decode the old version deterministically, retain the
  pre-migration file as the backup, validate the new representation, and record
  the required migration audit event before that version is declared supported.
