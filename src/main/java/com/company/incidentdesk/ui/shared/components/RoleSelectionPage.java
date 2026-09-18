package com.company.incidentdesk.ui.shared.components;

import java.util.function.Consumer;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

import com.company.incidentdesk.domain.account.Role;

/** Landing page used to navigate to each role-specific workspace. */
public final class RoleSelectionPage extends VBox {
    private static final double CONTENT_SPACING = 16;
    private static final double PAGE_PADDING = 32;
    private static final double BUTTON_WIDTH = 220;

    /**
     * Creates the role-selection page.
     *
     * @param onRoleSelected handler called with the selected role
     */
    public RoleSelectionPage(Consumer<Role> onRoleSelected) {
        super(CONTENT_SPACING);

        Label title = new Label("Incident Desk");
        title.setStyle("-fx-font-size: 28px; -fx-font-weight: bold;");
        Label prompt = new Label("Choose a role workspace");

        getChildren().addAll(
                title,
                prompt,
                createRoleButton("Reporter", Role.REPORTER, onRoleSelected),
                createRoleButton("Responder", Role.RESPONDER, onRoleSelected),
                createRoleButton("Administrator", Role.ADMINISTRATOR, onRoleSelected));
        setAlignment(Pos.CENTER);
        setPadding(new Insets(PAGE_PADDING));
    }

    private Button createRoleButton(String label, Role role, Consumer<Role> onRoleSelected) {
        Button button = new Button(label);
        button.setPrefWidth(BUTTON_WIDTH);
        button.setOnAction(event -> onRoleSelected.accept(role));
        return button;
    }
}
