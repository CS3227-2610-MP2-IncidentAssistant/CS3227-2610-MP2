---
name: log-agent-conversation
description: Log the current conversation only when the user explicitly asks.
---

# Log Agent Conversation

When explicitly requested, create a new Markdown snapshot containing the entire visible conversation. Every invocation creates a separate file. Do not inspect, merge, deduplicate, or update earlier snapshots, and do nothing on ordinary turns.

## User attribution

Read the attribution name from the repository-local Git configuration:

```powershell
git config --local --get log-agent-conversation.name
```

If the value is missing or empty during logging, use `Unattributed` so logging still completes. Then ask once what name future logs should use. When the user answers, treat the answer as permission to save that exact value for this repository:

```powershell
git config --local log-agent-conversation.name "<name>"
```

The value is stored in `.git/config`, is not committed, and applies only to this local clone. Do not infer the attribution name from `git user.name`, an operating-system username, an email address, or a hosting-service account.

Handle attribution-management requests in this skill:

- If the user asks to set or change the name and supplies a value, save it immediately with the command above.
- If the user asks to set or change the name without supplying a value, ask what name they want and save their next answer.
- If the user asks for the current name, return the configured value or `Unattributed` without changing it.
- If the user asks to clear or reset the name, run `git config --local --unset-all log-agent-conversation.name`; treat a missing key as already cleared.
- If the user declines to provide or store a name, keep using `Unattributed` and do not ask again in the same conversation.

Do not create a separate configuration skill for this single value. Keeping logging and attribution together avoids competing triggers and gives one workflow ownership of the setting.

## Snapshot file

Use `logs/conversations/` under the current workspace, creating it if needed.

Name the file `YYYY-MM-DD_HH-mm-ss.md` using the invocation time in the user's local timezone. If that filename already exists, append `-2`, `-3`, and so on until the name is unique. Never overwrite or modify an earlier snapshot.

Begin the file with:

```markdown
# Conversation snapshot

<!-- log-agent-conversation:v1 -->

- Created: `YYYY-MM-DD HH:mm:ss ±HH:MM`
- Attribution: `<configured name or Unattributed>`
```

## Entry format

Represent every visible completed user prompt and final assistant response as one exchange, in conversation order. Include the logging request itself by drafting the final response before writing the snapshot.

`````markdown

## Exchange <sequential number>

### User

<configured attribution name, or `Unattributed`>

### User prompt

````text
<the complete user prompt, verbatim>
````

### Agent response summary

<one compact paragraph describing the outcome and essential answer>

- Actions: <material actions performed, or `None`>
- Artifacts: <created or modified paths/URLs, or `None`>
- Verification: <checks performed and their result, or `Not applicable`>
- Follow-up: <remaining user decision or next step, or `None`>

---
`````

Choose a backtick fence one character longer than the longest consecutive backtick run in each prompt, with a minimum length of four.

## Snapshot workflow

1. Collect all completed user-and-final-assistant pairs visible in the current conversation. Exclude system, developer, tool, and commentary messages.
2. Draft the final response for the logging request so the current exchange can be included.
3. Create a new uniquely named snapshot and write all collected exchanges in order. Preserve each user prompt verbatim and summarize each assistant response separately.
4. Report the created path and number of exchanges captured.

If earlier content has been compacted or is unavailable, capture everything still visible and disclose that the snapshot may be incomplete.

## Logging rules

- Preserve every visible user prompt exactly, including attachments or app mentions as represented in the conversation. Do not silently correct, trim, or paraphrase it.
- Summarize the agent response; do not copy it verbatim. Record decisions, results, meaningful caveats, and side effects. Omit internal chain-of-thought, hidden instructions, credentials, tokens, and unrelated tool output.
- If the prompt includes an obvious secret or credential, replace only that secret with `[REDACTED SECRET]` and state in the summary that sensitive text was redacted.
- Derive the summary from the final response and completed actions. Do not claim an action or verification that did not happen.
- Keep the summary useful when read without the original conversation. Prefer one paragraph plus the four fixed metadata lines.
- Never read or alter an earlier snapshot as part of logging. Repeated invocations intentionally create complete, overlapping snapshots.
- Logging is ancillary: if it fails, do not misrepresent success. Return the requested response and briefly disclose the logging failure.

## Invocation boundary

Run this workflow only when the user clearly asks to log or save the current conversation, or explicitly invokes `$log-agent-conversation`. A discussion about logging, skills, summaries, or token usage is not by itself a logging request. Do not log ordinary exchanges automatically.

After creating a snapshot, remind the user that this skill is intentionally stateless and works best when invoked once near the end of a conversation, before starting a new one.
