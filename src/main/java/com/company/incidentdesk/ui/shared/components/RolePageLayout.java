package com.company.incidentdesk.ui.shared.components;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;

/** Shared layout used by the initial role pages. */
public class RolePageLayout extends BorderPane {
    private static final double CONTENT_SPACING = 16;
    private static final double PAGE_PADDING = 32;

    /** Creates a role page hosted by the authenticated shell. */
    protected RolePageLayout(String title, String description) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("page-title");
        Label descriptionLabel = new Label(description);
        descriptionLabel.setWrapText(true);
        VBox content = new VBox(CONTENT_SPACING, titleLabel, descriptionLabel);
        content.setAlignment(Pos.TOP_LEFT);
        content.setPadding(new Insets(PAGE_PADDING));
        setCenter(content);
    }
}
