---
name: resolve-issue-workflow
description: Complete an end-to-end issue resolution when a conversation's purpose is to fix and close a tracked repository issue. Use for explicit requests to resolve, fix, or close an issue; do not invoke for exploratory discussion, diagnosis-only work, or changes not tied to an issue.
---

# Resolve Issue Workflow

Deliver the verified fix and close its issue through the repository's review
workflow.

Before changing Git or remote state, identify the exact issue and confirm that
the requested scope authorizes an end-to-end resolution. If the issue is
ambiguous, or the user requested only diagnosis or implementation, stop before
external mutations and ask for the missing direction.

1. Read the issue and applicable repository instructions. Check the working
   tree and preserve unrelated or user-owned changes.
2. Create a dedicated feature branch from the intended base branch. Follow the
   repository's branch naming convention and include the issue number when
   available.
3. Implement and verify the smallest coherent fix. Stage only the issue's
   files, review the staged diff, and commit with an evidence-based message.
4. Push the feature branch to its configured remote.
5. Create a pull request or merge request targeting the intended base branch.
   Include the platform's closing syntax, such as `Closes #123`, in its
   description. Summarize the fix and checks actually run.
6. Wait for required checks and review conditions. Merge the request only when
   they pass and the task authorizes merging; never bypass protections or
   weaken checks.
7. Add a concise resolving comment to the issue with the merged request link,
   the outcome, and relevant verification. Avoid duplicating a comment already
   created automatically or by an earlier attempt.

After each remote mutation, verify the returned state rather than assuming it
succeeded. Stop after three materially similar failures and report the blocker.
At completion, report the branch, commit, request URL, merge result, issue
comment, checks, and any remaining risk.
