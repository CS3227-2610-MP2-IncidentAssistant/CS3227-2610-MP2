package com.company.incidentdesk.ui.shared.components;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

/** Factories for small, presentation-only components shared by role pages. */
public final class UiComponents {
    private static final DateTimeFormatter LOCAL_DATE_TIME =
            DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm").withZone(ZoneId.systemDefault());

    private UiComponents() {
    }

    public static Label badge(String text, SemanticTone tone) {
        Label badge = new Label(text);
        badge.getStyleClass().addAll("badge", tone.styleClass());
        badge.setAccessibleText(text);
        return badge;
    }

    public static Button button(String text, String... styleClasses) {
        Button button = new Button(text);
        button.getStyleClass().addAll(styleClasses);
        button.setAccessibleText(text);
        return button;
    }

    public static VBox panel(String title, Node... content) {
        Label heading = new Label(title);
        heading.getStyleClass().add("section-title");

        VBox body = new VBox(12, content);
        body.getStyleClass().add("panel-body");
        VBox panel = new VBox(heading, body);
        panel.getStyleClass().add("panel");
        VBox.setMargin(heading, new Insets(18, 20, 0, 20));
        return panel;
    }

    public static DateTimeFormatter localDateTimeFormatter() {
        return LOCAL_DATE_TIME;
    }
}
