package com.company.incidentdesk.ui.reporter;

import com.company.incidentdesk.ui.shared.components.RolePageLayout;

/** Initial workspace for incident reporters. */
public final class ReporterPage extends RolePageLayout {
    /**
     * Creates the reporter page.
     *
     * @param onBack action that returns to role selection
     */
    public ReporterPage(Runnable onBack) {
        super(
                "Reporter",
                "Create incident reports and track the incidents you submitted.",
                onBack);
    }
}
