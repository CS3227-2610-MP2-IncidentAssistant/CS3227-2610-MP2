package com.company.incidentdesk.ui.admin;

import com.company.incidentdesk.ui.shared.components.RolePageLayout;

/** Initial workspace for administrators. */
public final class AdminPage extends RolePageLayout {
    /** Creates the dashboard hosted by the authenticated shell. */
    public AdminPage() {
        super("Administrator", "Manage incidents, users, responder access, and operational oversight.");
    }
}
