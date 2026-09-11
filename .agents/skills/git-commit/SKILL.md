---
name: git-commit
description: Write Git commit messages and perform requested Git commits. Use whenever the user asks to commit changes, create or suggest a commit message, revise a commit message, or assess commit-message wording.
---

# Git Commit

Write a commit message that accurately describes the changes in scope. When the
user asks only for wording, return the message without creating a commit. When
the user asks to commit, inspect the relevant diff, compose the message, and run
the commit as authorized; do not broaden the request by staging unrelated files
or making additional code changes.

## Base the message on evidence

- Inspect the staged diff for a requested commit. If nothing is staged, inspect
  the intended working-tree changes and follow the user's staging instructions.
- Describe the commit as a whole, not merely the most prominent changed file.
- Do not claim effects, motivations, tests, or fixes that the diff and available
  context do not support.
- If the changes contain unrelated concerns, flag that a split may produce
  clearer commits instead of inventing one vague message.

## Subject

Every message must have a well-written subject line.

- Use imperative mood: `Add README.md`, not `Added README.md` or
  `Adding README.md`.
- Capitalize the first letter of the subject text.
- Do not end the subject with a period.
- Aim for 50 characters or fewer. Never exceed 72 characters.
- Optionally prefix the subject with a meaningful `<scope>:` or `<category>:`
  when it improves clarity, for example `Main.java: Remove blank lines` or
  `chore: Update release date`.
- Prefer a specific statement of the change over vague labels such as `Update code`.

## Body

Add a body for every non-trivial commit. A tiny, self-explanatory change may use
only a subject.

- Separate the subject and body with one blank line.
- Wrap body text at 72 characters.
- Separate paragraphs with blank lines and use bullet points when they improve
  clarity.
- Explain WHAT changed and WHY it was necessary or chosen. Leave implementation
  mechanics to the diff and avoid repeating code comments.
- Give enough context for a reviewer to judge the purpose without reading the
  diff. If that requires an unwieldy explanation, recommend splitting the
  commit.

Use this body progression when the available context supports it; omit sections
that would be redundant or speculative:

1. Describe the existing situation in present tense. Avoid words such as
   `currently` and `originally` because that timing is implied.
2. Explain why the situation needs to change.
3. State what the commit does in imperative language. `Let's` may introduce this
   paragraph.
4. Explain why this approach was selected.
5. Add other relevant context, limitations, references, or follow-up details.

Keep prose natural rather than adding headings for these sections. Use present
tense for the situation and imperative mood for the change being made.

## Before committing

- Check the final subject length and body wrapping.
- Confirm the message matches exactly what will be committed.
- Preserve the user's existing staging choices and unrelated working-tree
  changes.
- Report the resulting commit identifier and subject after a successful commit.
