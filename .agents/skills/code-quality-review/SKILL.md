---
name: code-quality-review
description: Review completed code changes for organizational readability, naming, unsafe-shortcut, and commenting standards. Invoke after meaningful logic or behavior changes, using roughly 20 executable lines as a flexible size signal.
---

# Code Quality Review

Review the relevant completed change, normally the current diff rather than the entire repository. This is a report-first gate: do not make review-driven improvements until the user approves them.

## Review Workflow

1. Inspect the relevant diff and enough surrounding code to understand its intent, conventions, and constraints.
2. Apply the rules below as strong organizational standards with context-sensitive exceptions. Thresholds are prompts for judgment, not automatic violations.
3. Report only actual findings. Do not emit a ceremonial checklist of rules that passed.
4. Assign stable finding IDs (`CQ-1`, `CQ-2`, and so on) and classify each finding as:
   - **Required**: a clear rule violation without a credible contextual exception.
   - **Recommended**: a strong improvement for which reasonable judgment is involved.
   - **Consider**: a heuristic concern whose benefit depends on context.
5. For every finding, name the rule and ID, identify the location, explain the problem and its impact, recommend a concrete improvement, and mention any credible reason to retain the current code.
6. Summarize counts by severity and list the rules applied.
7. Ask one bundled question: whether the user wants all findings implemented or only selected finding IDs. Do not ask separately for each finding.
8. If approved, implement only the approved findings, run validation proportionate to the change, and give a short completion report mapping each improvement to its finding and rule.

If there are no findings, give a brief pass report and do not ask for implementation approval.

Ask for a specific decision, rather than proposing an automatic edit, when resolving a finding would change observable behavior, public API compatibility, architecture, cross-module coupling, a demonstrated performance property, or uncertain code outside the current scope.

## Rules

### Readability

- **R1 — Method length:** Avoid long methods. Review functions over roughly 30 logical lines for extraction, but keep a longer function when it is cohesive and splitting it would obscure the story.
- **R2 — Nesting:** Avoid more than three meaningful levels of indentation. Prefer guard clauses and focused helpers where they clarify control flow.
- **R3 — Expression complexity:** Break apart hard-to-read negations, nested parentheses, and compound expressions. Give intermediate values explanatory names.
- **R4 — Magic literals:** Name literals that encode domain rules, protocols, units, limits, configuration, sentinels, or repeated non-obvious values. Do not extract obvious idiomatic values when a name would add noise.
- **R5 — Implicit behavior:** Avoid implicit conversions and precedence-dependent expressions when they obscure intent, risk information loss, or are easy to misread. Use explicit conversions, intermediate values, or clarifying parentheses as appropriate.
- **R6 — State representation:** Prefer enums or similarly constrained types over arbitrary integer states.
- **R7 — Logical layout:** Arrange code as a coherent story. Group related declarations and operations, order sections logically, and use whitespace or section breaks where they clarify meaningful structure.
- **R8 — Reader confusion:** Avoid unused parameters, deceptively similar names or constructs, inconsistent forms for equivalent concepts, multiple statements on one line, and overwriting an assigned value before it is used.
- **R9 — Simplicity:** Avoid clever code. Prefer the simplest clear implementation.
- **R10 — Premature optimization:** Do not sacrifice clarity for unmeasured or unnecessary optimization. Respect demonstrated performance requirements.
- **R11 — Single level of abstraction:** Keep each code fragment at a consistent conceptual level; extract lower-level mechanics when they interrupt the higher-level narrative.
- **R12 — Happy path:** Make the main successful flow prominent, commonly with guard clauses for exceptional or invalid cases.

### Naming

- **N1 — Parts of speech:** Use nouns for things and verbs for actions.
- **N2 — Cardinality:** Distinguish single values from collections with singular and plural names.
- **N3 — Shared vocabulary:** Avoid slang, unexplained foreign words, and terms meaningful only in an unstated local context. Preserve established domain terminology when it is the clearest shared vocabulary.
- **N4 — Explanatory names:** Use names to communicate purpose and meaning.
- **N5 — Numeric distinctions:** Do not distinguish otherwise identical names with arbitrary numbers.
- **N6 — Appropriate length:** Avoid names too short to explain themselves or so long that they obscure use. Judge length in context, including conventional local idioms.
- **N7 — Honest names:** Avoid names that mislead about value, units, state, side effects, ownership, or behavior.

### Unsafe Shortcuts

- **U1 — Exhaustive branches:** Handle every known case explicitly. Prefer compiler-enforced exhaustive matching when available. Use a defensive default or unreachable branch when the language or untrusted input boundary requires runtime protection. Never use `default` or `else` merely as shorthand for the final known case; reserve it for a genuine remainder.
- **U2 — Variable recycling:** Do not recycle variables or repurpose parameters as local working storage. Introduce a distinct name for a distinct meaning.
- **U3 — Empty catches:** Do not silently swallow exceptions. Handle, propagate, log, or deliberately document why ignoring one is safe.
- **U4 — Dead code:** Remove code made redundant by the current change. Do not delete uncertain or unrelated code without evidence and appropriate scope.
- **U5 — Narrow scope:** Declare values in the narrowest useful scope and avoid unnecessary global state.
- **U6 — Duplication:** Remove duplication when repeated code represents the same concept and a shared abstraction improves clarity. Coincidental similarities may remain duplicated when abstraction would couple unrelated behavior; explain non-obvious intentional duplication in the report.

### Comments

- **C1 — No repetition:** Do not restate information obvious from the code.
- **C2 — Audience:** Write comments for future maintainers, not as notes to the current author.
- **C3 — Intent and rationale:** Prefer comments about intent, rationale, constraints, invariants, and non-obvious behavior. Explain mechanics only when complex algorithms, concurrency, numerical work, interoperability, or workarounds cannot be made sufficiently clear through code alone.

Read [references/examples.md](references/examples.md) when a finding involves exhaustive branching, magic literals, guard clauses, abstraction levels, comments, duplication, implicit conversions, or expression precedence and an example would help apply the rule consistently.

## Report Format

Use this compact structure for each finding:

```text
CQ-1 — R2: Deep nesting [Recommended]
Location: path/to/file.ext:line
Finding: The success path is nested four levels deep.
Impact: Error handling obscures the main operation.
Recommendation: Convert the first two checks into guard clauses.
Context: Keep the current structure only if early returns would violate a local cleanup convention.
```

Omit `Context` when there is no credible exception. End with the severity counts, the applied rule IDs and names, and the single approval question.
