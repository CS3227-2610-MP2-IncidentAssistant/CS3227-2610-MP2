# Documentation and Delivery

These entries summarize my AI-assisted documentation work and the review decisions I made. The User Guide and Developer Guide describe the JavaFX product, not the earlier web mockup.

## User Guide and screenshots

**Prompt strategy.** I asked for a task-based User Guide that peer testers could follow and insisted that claims match the current app. I wanted the guide built in sections, with screenshots taken from the running JavaFX application.

**AI contribution and corrections.** The agent drafted setup and role workflows, while I supplied and checked screenshots. The first ordering had an operational mistake: it described the Responder queue before explaining how an Administrator grants category access. During manual review the queue appeared empty because the account lacked the required category; commit `d9b50ee` put that setup step first. The Reporter screenshot became stale when the anonymous checkbox and **My incidents** section were added, so `1314205` replaced it with the current screen. I requested a table of contents and a flat `docs/images/` directory; `1d5dfe2` moved the PNGs and updated their links together.

**Verification and judgement.** I compared the pictured Reporter form, administrator category controls, incident lists, and instructions with the app I could run. The empty-queue observation led to a setup-order correction, not a change to the Responder queue code. This is evidence of visual review, not a claim that every possible peer-testing path was exercised.

**Reflection takeaway.** Documentation became more useful when I followed the order a tester needs: set up an account, grant access, create an incident, then observe the next role. Screenshots needed updating whenever the form changed.

## Developer Guide and diagrams

**Prompt strategy.** I asked the agent to build around the Developer Guide's existing material, not remove teammates' work. I used my MP1 guide as a style reference for an architecture diagram and a sequence diagram, while requiring this project's actual classes and behaviour.

**AI contribution and corrections.** The agent drafted the architecture and incident-submission diagrams, but I rejected an early composition that resembled the MP1 diagram more than this application's code. The architecture source then had a UML direction error: `Ports -> Storage` implied that the interfaces implemented the local adapter. `LocalApplicationStore` actually implements the persistence interfaces, so `239a50b` reversed that realization. The submission sequence originally hid important arguments behind `submit(incident details)` and drew a propagated `RepositoryException` as ordinary returns; the same commit clarified both. After anonymous submission was wired, the sequence still showed `submit(..., false)` until `d11a5d3` changed it to the selected `anonymous` value.

**Verification and judgement.** Editable PlantUML sources and rendered SVGs are stored under `docs/diagrams/`; the guide embeds the rendered diagrams. I checked the persistence arrow against the interface implementation, and checked that a failed save produces a storage failure rather than a success event. The MP1 diagram is acknowledged as a style influence, not presented as Incident Desk's design.

**Reflection takeaway.** A diagram can look polished while its arrows imply the wrong dependency. Reviewing labels and direction against source code mattered more than matching a reference image exactly.

## Branches, reviews, and checks

**Prompt strategy.** I used issues and small branches for Reporter work, requested focused feature/test/docs commits, and repeatedly asked for read-only checks before changes that might overlap with teammates' code. I also asked for code quality consistent with the course conventions.

**AI contribution and corrections.** The agent helped prepare branches, tests, guide updates, and PR descriptions. One integration error was small but decisive: the authenticated shell still constructed the no-argument Reporter page, which returned `RESOURCE_UNAVAILABLE` on submission. Commit `b31993c` injected `IncidentService` in `DefaultViewFactory`. Later the dashboard gained an incident list without a scroll container, so lower content could fall below the window until `f2b4108` fixed the layout. A mistaken merge involving `main` also led me to inspect the branch graph and target branch before pushing.

**Verification and judgement.** The repository history records separate Reporter PRs for submission integration (#72), tracking (#94), actions (#96), and anonymity (#97). I reviewed CI and the running UI rather than treating a green build as complete visual verification: the missing service injection and missing scrollbar required checking the actual route and full window. This log does not claim that tests were rerun while writing these summaries.

**Reflection takeaway.** Agent speed helped with repetitive work, but Git history, human UI checks, and narrow branch scope were essential to keep the team's work understandable and avoid unsupported completion claims.
