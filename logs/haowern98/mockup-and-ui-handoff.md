# Web Mockup and JavaFX UI Handoff

This entry concerns the earlier static web prototype. It was a design reference for the JavaFX application, not the production application itself.

## Defining a feasible prototype

**Prompt strategy.** I asked for a polished mockup that my group could inspect online, but required every visible pattern to be realistically reproducible with JavaFX controls, layout, and CSS. I chose to represent all three roles and then narrowed the preview to navigable screens with limited interactions and a simple role selector. After comparing visual directions, I selected the calm-blue operations style.

**AI contribution and corrections.** The agent built a static HTML/CSS/JavaScript preview, but the first role layer had incomplete interaction handlers: sorting and statistics filters, account mutations, report editing, and responder requests were initially only visual. Commit `6a43e8d` added the in-memory behaviour. Review then found a stale-state bug: `state.editingId` survived navigation or a role switch, so a later **New report** screen could open in the wrong editing context. Commit `f3a9f0a` cleared it on ordinary navigation while preserving it for the deliberate **Edit report** route. The same commit removed linked responder requests when a mock user was deleted. I checked that the visual patterns could be implemented with JavaFX rather than relying on browser-only effects; the prototype itself did not provide real authentication, persistence, or authorization.

**Verification and judgement.** The first smoke-test regexes had a false-positive bug: they accepted `data-href`, `data-src`, or `data-id` as real asset links or DOM IDs, so the test could pass even if the page did not load its stylesheet or script. Commit `f9808c1` tightened the predicates and added a negative fixture for those lookalikes. The pasted development record reports five passing static checks, visual inspection of role screens, and an HTTP 200 check after deployment to `https://incident-desk-preview.vercel.app`. Those checks applied to the web prototype at the time, not to the later JavaFX app or GitHub Pages product site.

**Reflection takeaway.** A mockup was useful for aligning the group on a visual language, but it could easily be mistaken for a working product. I had to keep the preview's simulated behaviour separate from implemented desktop features.

## Handing the style to the JavaFX project

**Prompt strategy.** I asked for a small handoff that teammates could use without copying web-only CSS into JavaFX. The requested artifacts were a readable style guide and a JavaFX-compatible stylesheet.

**AI contribution and corrections.** The agent translated the approved colours, typography, spacing, and component states into `STYLE_GUIDE.md` and `incident-desk-theme.css`. Layout values that JavaFX CSS cannot express were documented for Java layout code rather than represented as unsupported stylesheet properties.

**Verification and judgement.** The mockup repository history records the preview, smoke tests, deployment housekeeping, theme, and style-guide commits. The later JavaFX project still required its own implementation and runtime checks; the handoff alone did not establish that all screens used the theme or that the web interactions were complete product features.

**Reflection takeaway.** The strongest design handoff explained both what to reuse and what not to copy. A consistent colour system helped, but the desktop app needed separate code, tests, and visual review.
