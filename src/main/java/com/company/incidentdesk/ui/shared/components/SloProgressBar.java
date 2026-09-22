package com.company.incidentdesk.ui.shared.components;

import javafx.beans.binding.Bindings;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.geometry.Pos;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;

/** Themeable SLO progress indicator with deterministic proportional sizing. */
public final class SloProgressBar extends StackPane {
    private final DoubleProperty progress = new SimpleDoubleProperty(this, "progress");

    public SloProgressBar(double initialProgress, String accessibleDescription) {
        Region fill = new Region();
        fill.getStyleClass().add("slo-progress-fill");
        fill.minWidthProperty().bind(Bindings.multiply(widthProperty(), progress));
        fill.prefWidthProperty().bind(Bindings.multiply(widthProperty(), progress));
        fill.maxWidthProperty().bind(Bindings.multiply(widthProperty(), progress));

        getChildren().add(fill);
        getStyleClass().add("slo-progress-track");
        setAlignment(Pos.CENTER_LEFT);
        setAccessibleText(accessibleDescription);
        setProgress(initialProgress);
    }

    public double getProgress() {
        return progress.get();
    }

    public void setProgress(double value) {
        if (!Double.isFinite(value) || value < 0 || value > 1) {
            throw new IllegalArgumentException("Progress must be between 0 and 1");
        }
        progress.set(value);
    }

    public DoubleProperty progressProperty() {
        return progress;
    }
}
