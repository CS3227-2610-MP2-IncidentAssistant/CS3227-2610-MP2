package com.company.incidentdesk.ui.shared.components;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;

/** Shared layout used by the initial role pages. */
public class RolePageLayout extends BorderPane {
    private static final double CONTENT_SPACING = 16;
    private static final double PAGE_PADDING = 32;

    /**
     * Creates a role page with shared navigation and presentation.
     *
     * @param title page title
     * @param description short description of the role workspace
     * @param onBack action that returns to role selection
     */
    protected RolePageLayout(String title, String description, Runnable onBack) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("page-title");

        Label descriptionLabel = new Label(description);
        descriptionLabel.setWrapText(true);

        Button backButton = new Button("Back to role selection");
        backButton.setOnAction(event -> onBack.run());

        VBox content = new VBox(CONTENT_SPACING, titleLabel, descriptionLabel, backButton);
        content.setAlignment(Pos.CENTER);
        content.setPadding(new Insets(PAGE_PADDING));
        setCenter(content);
    }
}
