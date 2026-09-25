# Responder MVP integration verification (#40)

## What is connected

`IncidentDeskApplication` builds the navigator and `DefaultViewFactory` from
`ApplicationContext`. The context supplies a real credential verifier, one
active session, the recovery-safe local store, incident/promotion/category
services, audit creation, and the in-process event bus. The factory selects
the role page from the authenticated account; selecting a view is not authority.

The existing production connections satisfy this issue. This change adds
integration coverage, not a second startup path, new responder functionality,
or a persistence migration.

## Automated verification

Run from the repository root with Java 25 and a graphical session:

```sh
./gradlew test --tests '*ResponderMvpIntegrationTest'
./gradlew build
```

On headless Linux, use the existing CI convention:

```sh
xvfb-run --auto-servernum ./gradlew test --tests '*ResponderMvpIntegrationTest'
```

`ResponderMvpIntegrationTest` is in the normal test source set, so the existing
Linux CI build automatically includes it. The separate Windows/macOS attachment
jobs do not run this suite. Test results are available under
`build/reports/tests/test/index.html` and `build/test-results/test/`.

Every test uses JUnit temporary storage. No test calls `openDefault`, reads the
developer's store, or uses actual user credentials. Registration and login use
the production password hashing/verifier. The test-only administrator bootstrap
uses the registration service; candidates become responders through a real
reporter promotion request and administrator approval. No accounts are promoted
by directly editing repository records.

| Test | Evidence |
| --- | --- |
| `realSubmissionClaimResolutionAndAuditSurviveRestart` | Real reporter form submission, responder Claim/Resolve buttons, queue refresh, preserved remarks, resolver attribution, durable resolution/audit evidence, and reporter detail access after reopening the store |
| `currentAdminProvisioningControlsQueuesButRetainsExistingAssignment` | Actual admin category changes remove new-claim access while retaining existing assigned work; restoring access restores eligible incidents |
| `unauthorizedSessionsCannotReachOrMutateResponderWork` | Wrong-category responder, reporter, and signed-out denials through navigation/services; no denied incident/audit writes |
| `logoutAndReplacementSessionDiscardRetainedResponderContent` | Logout and replacement-session navigation detach and clear old responder rows/details |
| `failedDiskWritePreservesIncidentAuditAndCanonicalBytesAcrossRestart` | A nonempty directory obstructs backup replacement in the temporary store; claim fails without changing canonical bytes, incident, or audit history; restart and retry succeed |

The integration tests invoke real login, promotion, and category-change services
directly; they do not automate password entry or the admin decision dialogs.
Reporter submission and responder claim/resolution are exercised through their
actual UI controls. Existing focused tests cover validation, repeated clicks,
resolution write failure, and shared navigation behavior.

## Manual smoke test

Use a dedicated development store, not production data. Do not delete or modify
an existing store to run this checklist. Use existing test accounts or approved
account setup; this document does not introduce another admin-bootstrap policy.

1. Sign in as a reporter and submit an IT incident with a title and description.
2. As an administrator, approve a pending responder request if necessary, then
   use **Accounts → Configure categories** to grant the responder IT access.
3. Sign in as that responder. In **Eligible queue**, open the incident and
   choose **Claim**. Return to the dashboard and check **My assigned incidents**.
4. Open it again and choose **Resolve**. Submit blank remarks and confirm that
   field feedback appears without resolving the incident. Enter remarks and
   confirm resolution. Check that it leaves the assigned queue.
5. Restart the application using the same test store. Confirm the resolution
   and audit evidence remain available to authorized users.
6. Repeat with another incident: revoke IT access after claiming it. Existing
   assigned work must remain visible/resolvable; new IT incidents must not be
   visible or claimable until access is restored. This follows the policy
   adopted in #55, not the earlier category-revocation behavior.
7. Log out from a responder detail page and sign in as another reporter. The old
   responder data must not remain displayed or be reachable through navigation.

Do not simulate storage failures against a real development or user store. The
automated temporary-store test provides that evidence safely.

## Scope and limitations

- No stored-data format changes, migrations, or new dependencies.
- No admin assignment/reassignment UI (#53), responder handoff, or statistics.
- This verifies responder MVP integration, not completion of every role's UI.
- A passing automated suite is not a claim that the manual checklist or a
  packaged application has been exercised on every supported operating system.
