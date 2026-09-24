# Anonymous Report Policy

## Meaning of anonymous

An anonymous report hides the reporter's identity from responders and from
normal administrator incident, statistics, export, and audit views. The
application retains an internal reporter link so the reporter can view the
incident, receive its status, and post a follow-up.

This is confidentiality within the application, not cryptographic anonymity:
the local data owner may be capable of inspecting storage files. User-facing
copy must not promise stronger anonymity than the implementation provides.

## Required protections

- Return a neutral label such as `Anonymous reporter` in every incident-facing
  view and transfer object.
- Do not reveal identity through comments, audit actor labels, filters,
  statistics, notifications, attachment paths/names, exports, error messages,
  or logs.
- Store the internal reporter reference only in the protected canonical data,
  not duplicated across derived indexes or display models without need.
- Generate stored attachment names; never embed usernames or original local
  paths.
- For anonymous incidents, use generic image display names for every role
  (and generic video names for any retained, unsupported legacy metadata).
  Sanitized original names remain protected storage metadata only.
- Warn uploaders that media contents and embedded metadata may reveal their
  identity. Uploads preserve original bytes; generic naming is not metadata
  stripping or content anonymization.
- A reporter still sees an anonymous incident in their own list.
- A reopening comment on an anonymous incident is displayed to other users as
  authored by `Anonymous reporter`; its text remains visible as part of the
  incident thread.
- Anonymous status cannot be removed after submission without an explicit future
  requirement and audit policy.
- Administrators can see that a report is anonymous but cannot reveal its
  identity through the normal application interface.

## Exceptional identity recovery

No exceptional deanonymization feature is currently specified. Do not build
one. If the product later requires it, define authorization, justification,
confirmation, audit, and display rules before implementation.

## Testing expectations

Test every representation of an anonymous incident, including detail/list
views, audit displays, statistics, filters, exports, comments, attachments,
notifications, serialized display models, and failure messages.
