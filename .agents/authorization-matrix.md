# Authorization Matrix

Authorization is deny-by-default. All conditions listed for an allowed action
must hold. UI visibility must mirror, but never replace, domain authorization.

| Action | Reporter | Responder | Admin |
| --- | --- | --- | --- |
| Register account | Allowed | Not applicable; responder is promoted | Admin account bootstrap is implementation-specific |
| View incident list | Own incidents only | Assigned incidents and unassigned incidents in permitted categories | All incidents |
| View incident details | Own incidents only | Incident is assigned to them, or is unassigned in a permitted category | All incidents |
| View attachments/comments | Same permission as incident detail | Same permission as incident detail | Same permission as incident detail |
| Add attachments | Own `DRAFT`, or own unassigned `SUBMITTED` incident | No | No |
| Create/save draft | Allowed for self | Allowed only when acting as a reporter, if dual-role behaviour is supported | Not required |
| Submit incident | Allowed for self | Same caveat as create | Not required |
| Edit/withdraw | Own `DRAFT`, or own unassigned `SUBMITTED` incident | No | No current requirement |
| Claim | No | Unassigned `SUBMITTED` incident in a permitted category | No current requirement |
| Resolve | No | Incident assigned to self | Any `ASSIGNED` incident |
| Hand off | No | Incident assigned to self | No current requirement |
| Reassign | No | No | To a responder permitted for the incident category |
| Follow up/reopen | Own `RESOLVED` incident, with a non-blank explanatory comment | No | No current requirement |
| View reporter identity | Own identity | Only for non-anonymous incidents they may access | Non-anonymous identity; anonymous identity remains hidden by default |
| Request responder promotion | Allowed for self | Not applicable while already responder | No |
| Decide promotion request | No | No | Allowed |
| Change responder categories | No | No | Allowed |
| Configure SLO | No | No | Allowed |
| View own performance | No | Own statistics | All responder/statistical views |
| View audit log | No | No | Allowed |
| Delete/reset account | No | No | Allowed, subject to retention/security rules |

## Enforcement rules

- Obtain the actor from the active authenticated session, not a method argument
  originating from the UI.
- Re-evaluate authorization when an operation executes; do not rely on a list
  view checked earlier.
- A responder losing category access immediately loses access to unassigned
  incidents in that category. Existing assignments require an admin to reassign
  or restore access; the responder must not continue viewing them.
- Account deletion disables authentication but preserves tombstoned references
  required by incidents and audit records.
- Statistics and exports must apply the same anonymity rules as detail views.
- When permission is denied, avoid revealing whether an inaccessible incident
  exists or who reported it.
