package com.company.incidentdesk.ui.shared.components;

import java.util.List;
import java.util.function.Function;

import com.company.incidentdesk.application.presentation.IncidentRowModel;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

/** Reusable, presentation-only MVP table. Ordering belongs to the application query. */
public final class IncidentTable extends TableView<IncidentRowModel> {
    public IncidentTable(String accessibleName, String emptyMessage) {
        setAccessibleText(accessibleName);
        setColumnResizePolicy(CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        setPrefHeight(240);
        setMinHeight(180);
        setPlaceholder(UiComponents.feedback("No incidents", emptyMessage, FeedbackType.EMPTY));
        getColumns().addAll(List.of(
                column("Incident", IncidentRowModel::title),
                column("Category", IncidentRowModel::categoryLabel),
                column("Status", IncidentRowModel::statusLabel),
                column("Reporter", IncidentRowModel::reporterLabel),
                column("Created", IncidentRowModel::createdAt)));
    }

    private TableColumn<IncidentRowModel, String> column(
            String title, Function<IncidentRowModel, String> value) {
        TableColumn<IncidentRowModel, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(value.apply(cell.getValue())));
        column.setSortable(false);
        column.setMinWidth(90);
        return column;
    }
}
