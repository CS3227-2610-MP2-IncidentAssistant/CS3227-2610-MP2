package com.company.incidentdesk.ui.admin;

import com.company.incidentdesk.ui.shared.components.RolePageLayout;

/** Initial workspace for administrators. */
public final class AdminPage extends RolePageLayout {
    /**
     * Creates the administrator page.
     *
     * @param onBack action that returns to role selection
     */
    public AdminPage(Runnable onBack) {
        super(
                "Administrator",
                "Manage incidents, users, responder access, and operational oversight.",
                onBack);
    }
}
