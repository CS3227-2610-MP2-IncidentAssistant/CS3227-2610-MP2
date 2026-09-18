package com.company.incidentdesk.ui.responder;

import com.company.incidentdesk.ui.shared.components.RolePageLayout;

/** Initial workspace for incident responders. */
public final class ResponderPage extends RolePageLayout {
    /**
     * Creates the responder page.
     *
     * @param onBack action that returns to role selection
     */
    public ResponderPage(Runnable onBack) {
        super(
                "Responder",
                "Review eligible incidents and manage incidents assigned to you.",
                onBack);
    }
}
