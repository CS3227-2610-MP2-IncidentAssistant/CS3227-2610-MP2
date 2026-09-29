---
name: shared-ui-reuse
description: Check existing shared JavaFX components before creating or extending UI components in Incident Desk, and reuse or compose matching implementations.
---

# Shared UI reuse

Before implementing a new UI component or renderer in this repository:

1. Read `.agents/ui-component-checklist.md` and the shared API section of
   `.agents/STYLE_GUIDE.md`.
2. Search `src/main/java/com/company/incidentdesk/ui/shared/components/` for
   the needed behavior. Inspect matching implementations and callers,
   including `ComponentShowcasePage`, rather than relying on class names alone.
3. Reuse an existing factory or component when it already provides the behavior.
   Compose primitives for larger workflows: a comment thread should use the
   shared single-comment renderer instead of recreating its layout.
4. If an existing component nearly fits, consider a small compatible extension.
   Create a new primitive only when the existing API cannot reasonably cover
   the requirement; explain that gap in the implementation summary.
5. Preserve presentation inputs and behavior, including role labels, avatars,
   timestamps, accessibility, and validation feedback. Obtain missing display
   fields through the presentation model. Never pass protected identity data
   to the UI to reconstruct a missing label or avatar.

Keep authorization and persistence outside shared UI components. Verify the
composed behavior and changed caller contracts with appropriate checks.
