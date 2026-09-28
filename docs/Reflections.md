---
layout: default
title: Reflections
nav_order: 4
---

# Reflections

Throughout the development of Incident Desk, our team used AI coding agents to support activities such as planning, implementation, testing, documentation, and code review. The following reflections describe each member's experience working with these tools, including where they were helpful, where human judgement remained essential, and what we learned about using AI responsibly in a collaborative software-engineering project.

---

## Isaac Ng Jun Jie

_Reflection to be added._

---

## Hao Wern

I used Codex to explore the UI, implement Reporter workflows, add tests, and help write the guides. I set the scope of each task, reviewed the generated work against the running application and our shared services, and corrected mistakes before merging. My [interaction summaries](../logs/haowern98/reporter-submission-and-tracking.md) record the decisions and checks behind these examples.

### 1. Turning a mockup into a working Reporter page

#### Prompt: a working Reporter interface

> Create a mockup that the group can review, but use visual patterns that can also be built in JavaFX. For the actual application, focus on the Reporter role. Inspect the existing incident-submission service and shared table before connecting the form and incident list. Keep changes to teammates' code small, and add focused tests for the user workflow.

#### Why this prompt required a clear boundary

The web mockup helped the group agree on a visual direction, but it was not the JavaFX product. I asked the agent to inspect the existing services and table so it would connect the Reporter screen to shared application rules instead of recreating them in the UI. A convincing preview was not proof that a report could be saved or tracked.

#### Reporter assumptions, corrections, and verification

The agent produced the mockup and later drafted the Reporter form. The first working page still showed **Submission unavailable** because it had no `IncidentService`. Even after a service-aware constructor was added, `DefaultViewFactory` continued to call the no-argument constructor. I asked for the actual authenticated route to be checked; injecting the service there made submission use the existing validation, authorization, persistence, and audit path. I checked the saved-report confirmation in the running app, not only a form-level test.

When **My incidents** was added, the first table conversion filled some fields with placeholders instead of using the shared row mapping. The agent moved authorized row creation into `IncidentService.reporterIncidents(...)`. The combined form and list also extended below the window without a page scrollbar. I noticed that in the live UI; the page gained a `ScrollPane`, and a UI-flow test checked access to both sections. These changes are described in the [Reporter submission and tracking log](../logs/haowern98/reporter-submission-and-tracking.md). The earlier [mockup log](../logs/haowern98/mockup-and-ui-handoff.md) records separate preview defects, including an editing ID that survived navigation and smoke tests that mistook `data-href` for a real link.

#### Reporter judgement and next time

I learned to test the route a user actually takes through the application. A passing form test would not have found the missing service injection, and service tests would not have shown that the lower half of the page was unreachable. Next time I would check construction, submission, navigation, and scrolling as one short end-to-end path before calling a screen complete.

### 2. Connecting Reporter actions to the correct state changes

#### Prompt: correct Reporter actions and anonymous submission

> Connect the Reporter detail screen to the existing edit, withdraw, and reopen operations without moving authorization rules into JavaFX. Allow editing and withdrawal only before assignment. Require an explanation when a Reporter follows up on a resolved incident, and make that action reopen the incident rather than add an ordinary comment. Add an anonymous option to the submission form using the service's existing flag, and explain its privacy limit accurately. Test both allowed and rejected actions.

#### Why this prompt specified the state changes

The backend already contained these operations, but the Reporter UI did not expose them correctly. A comment on a resolved incident was not equivalent to reopening it, and hiding an edit button was not enough to enforce the assignment rule. I specified the required state changes and denied paths so the agent would use the existing service boundary rather than implement a UI-only imitation. The anonymous option also needed wording that did not promise privacy from someone inspecting local data files.

#### Reporter action assumptions, corrections, and verification

The most important error was a missing mutation path. `DefaultViewFactory` gave Reporters a generic detail view with `IncidentDetailActions.none()`. Its comment box called `IncidentCommentService.add(...)`, which added text but did not change the incident's status. The agent added a Reporter-specific detail page whose follow-up action calls `IncidentService.reopen(...)`. That operation records the transition back to `SUBMITTED`, the `REOPEN_EXPLANATION` comment, and the audit event together. The focused test checked the stored status and comment type while confirming that earlier resolution remarks remained in the history. Other tests checked that assignment blocks a stale edit and that withdrawal requires confirmation.

Anonymous submission exposed a simpler wiring error: the form always passed `false` to `IncidentService.submit(...)`. The agent connected a checkbox to that argument and tested that its value reaches storage, clears after success, and remains selected after a failed submission. I also checked the running app from two roles: the incident remained in the author's **My incidents**, while the Responder queue showed **Anonymous reporter**. These checks are recorded in the [Reporter actions and anonymity log](../logs/haowern98/reporter-actions-and-anonymity.md). They do not mean the local data owner cannot inspect stored files, and the separate promotion-request feature was not part of this work.

#### Reporter action judgement and next time

The existence of a service method did not mean a user could reach it. I had to trace each UI action through the service to the stored state and test both permitted and denied cases. Next time I would sketch that path before implementation, especially for actions that change an incident's lifecycle or privacy presentation.

### 3. Keeping group documentation tied to the product

#### Prompt: documentation that matches the group project

> Write the User Guide around the steps a peer tester needs to follow, using screenshots from the current JavaFX application. Preserve the Developer Guide material already written by teammates and explain the architecture using this project's actual classes and relationships. Use my MP1 diagrams only as a visual style reference. Check instructions, screenshots, and diagrams against the implemented product before presenting them as complete.

#### Why this prompt required product evidence

Peer testers need an accurate setup order, not just a list of features. I also wanted the agent to respect my teammates' existing guide content and to avoid carrying MP1 design details into a different application. Source code and current screenshots were necessary evidence because a polished diagram or guide could still describe the wrong behaviour.

#### Documentation assumptions, corrections, and verification

The first User Guide order described the Responder queue before explaining how an Administrator grants category access. When I tried the flow, the queue was empty; the guide was reordered to put account setup first. Later, the Reporter screenshot became outdated when the anonymous checkbox and **My incidents** appeared, so I replaced it. In the Developer Guide, I challenged a persistence arrow that pointed from interfaces toward their adapter. `LocalApplicationStore` implements those interfaces, so the UML realization needed to point toward them. I also had the submission sequence updated after the form stopped hard-coding `false` for anonymity. The [documentation log](../logs/haowern98/documentation-and-delivery.md) records these corrections and the source and UI checks behind them.

#### Documentation judgement and next time

Good-looking documentation can still give a tester the wrong order or show a dependency backwards. AI helped draft and organise the guides, but I needed to compare them with source code and the current screen, and to respect teammates' existing work. Next time I would keep a small evidence checklist for every guide change: the implemented feature, its actual route, a current screenshot if needed, and the check that supports the claim.

### Overall lessons

The agent was most useful when I gave it a narrow boundary and a result I could verify. The costly mistakes were mostly at boundaries: a page constructed without its service, a comment that did not reopen an incident, a checkbox value not passed to the backend, and diagrams or screenshots that lagged behind the code. Tests, source inspection, the running JavaFX app, and review of small PRs helped me separate a plausible draft from work I could defend as part of a team.

---

## Qing Rui

_Reflection to be added._
