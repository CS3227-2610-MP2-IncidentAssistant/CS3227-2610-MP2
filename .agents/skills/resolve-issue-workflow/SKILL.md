---
name: resolve-issue-workflow
description: Implement and verify a tracked repository issue, pause for user review, then complete the Git and review workflow only with explicit permission. Use for explicit requests to resolve, fix, or close an issue; do not invoke for exploratory discussion, diagnosis-only work, or changes not tied to an issue.
---

# Resolve Issue Workflow

Deliver a verified fix for user review before committing it, then close the
issue through the repository's review workflow only after explicit approval.

Before changing Git or remote state, identify the exact issue. Authorization to
tackle, resolve, fix, or close an issue authorizes implementation and local
verification, but does not authorize staging or committing the result. Do not
infer commit permission from a broad issue-resolution request.

1. Read the issue and applicable repository instructions. Check the working
   tree and preserve unrelated or user-owned changes.
2. Create a dedicated feature branch from the intended base branch. Follow the
   repository's branch naming convention and include the issue number when
   available.
3. Implement and verify the smallest coherent fix. Keep the implementation
   unstaged unless the user explicitly asked to stage it.
4. Stop for user review. Provide a review guide that:
   - summarizes the behavior and persisted-data impact;
   - lists every changed production class or artifact;
   - names the public, protected, or otherwise behavior-critical methods that
     warrant review and explains what to verify in each;
   - identifies the tests that demonstrate the acceptance criteria;
   - reports checks run, failures, skipped checks, assumptions, and remaining
     risks; and
   - states clearly that no commit has been created.
5. Ask for explicit permission to proceed with the commit. Do not stage,
   commit, push, create a pull request, merge, or comment on the issue until the
   user approves after reviewing the implementation.
6. After approval, stage only the issue's files, review the staged diff, and
   commit with an evidence-based message.
7. Push the feature branch to its configured remote.
8. Create a pull request or merge request targeting the intended base branch.
   Include the platform's closing syntax, such as `Closes #123`, in its
   description. Summarize the fix and checks actually run.
9. Wait for required checks and review conditions. Merge the request only when
   they pass and the task authorizes merging; never bypass protections or
   weaken checks.
10. Add a concise resolving comment to the issue with the merged request link,
   the outcome, and relevant verification. Avoid duplicating a comment already
   created automatically or by an earlier attempt.

After each remote mutation, verify the returned state rather than assuming it
succeeded. Stop after three materially similar failures and report the blocker.
At completion, report the branch, commit, request URL, merge result, issue
comment, checks, and any remaining risk.
