# Reporter Submission and Tracking

These are summaries of my AI-assisted work on Incident Desk, not conversation transcripts. They record my decisions alongside the agent's contributions and the checks I can substantiate.

## Submission form and service integration

**Prompt strategy.** I first scoped my work to the Reporter page and new Reporter-specific tests so that I would not change my teammates' services or role screens. I asked the agent to inspect the existing submission API and shared validation before making the form functional.

**AI contribution and corrections.** The agent drafted the title, description, and category form, but the first `ReporterPage` had a dependency-wiring error: its submit callback always showed **Submission unavailable** because it had no `IncidentService`. Even after the page gained a service-aware constructor, `DefaultViewFactory` still used the no-argument constructor. Commit `b31993c` changed that factory call, allowing the authenticated page to persist reports through the existing service. Commit `a707d4d` also separated validation errors from storage or session failures: success clears the form, while failure retains its entries. I kept authorization, validation, persistence, and audit in the service rather than duplicating them in JavaFX.

**Verification and judgement.** Focused tests were added for required fields, successful submission, and failure feedback. A form-level validation test alone would not have detected the missing factory wiring, so I also used the running JavaFX app to check that submitting showed the saved-report confirmation. I supplied an actual screenshot for the User Guide. The form and integration were delivered through separate feature and test commits, including the work merged in PR #72.

**Reflection takeaway.** A UI that accepts input is not enough by itself. I had to check that it used the application's existing service boundary and that the guide showed the screen users would actually see.

## My incidents and detail navigation

**Prompt strategy.** Issue #89 added a way for Reporters to find their own submissions, see status, and open details. I required a read-only check first and kept the work isolated from teammates' role implementations. I also limited commits to small, reviewable groups.

**AI contribution and corrections.** The agent connected the Reporter dashboard to the existing incident-table and detail components. The first implementation had a presentation-mapping error: `ReporterPage` converted `IncidentView` to `IncidentRowModel` itself, filling several columns with empty strings, marking SLO unavailable, and setting every action flag to false. Commits `11f2d9c` and `97ec68a` moved authorized row creation into `IncidentService.reporterIncidents(...)` and made the page consume the shared mapper's rows. I kept submission and tracking on one dashboard. That exposed a layout defect: the form and **My incidents** list exceeded the window height while the page still used a plain `VBox`. Commit `f2b4108` placed it inside a width-fitting `ScrollPane`.

**Verification and judgement.** The branch added tracking, mapped-row, and refresh tests rather than just counting incidents. Commit `e64e86f` added a UI-flow test that checks the scroll container and access to both the submit control and incident table. I inspected the running app, including the sidebar and lower list, before PR #94 was merged. These checks support the dashboard workflow; they do not imply that every planned Reporter feature existed at that point.

**Reflection takeaway.** Reusing a shared table reduced duplicate UI work, but the real screen exposed a layout problem that tests of service data alone would not have found. Manual navigation and scrolling checks were necessary.
