package com.company.incidentdesk.ui.admin;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;

import com.company.incidentdesk.application.audit.AuditLogEntry;
import com.company.incidentdesk.application.audit.AuditLogService;
import com.company.incidentdesk.domain.audit.AuditOutcome;
import com.company.incidentdesk.ui.navigation.NavigableView;
import com.company.incidentdesk.ui.shared.components.FeedbackType;
import com.company.incidentdesk.ui.shared.components.SemanticTone;
import com.company.incidentdesk.ui.shared.components.UiComponents;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** Administrator view of privacy-safe application audit history. */
public final class AdminAuditLogPage extends BorderPane implements NavigableView {
    private static final double ROW_HEIGHT = 40;
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter
            .ofPattern("d MMM uuuu, HH:mm:ss", Locale.ENGLISH)
            .withZone(ZoneId.systemDefault());
    private final AuditLogService service;
    private final VBox results = new VBox();

    public AdminAuditLogPage(AuditLogService service) {
        this.service = Objects.requireNonNull(service, "service");
        Label title = new Label("Audit log");
        title.getStyleClass().add("page-title");
        Label supportingText = new Label("Review security-sensitive and incident-changing activity.");
        supportingText.getStyleClass().add("page-supporting-text");
        VBox content = new VBox(8, title, supportingText, results);
        content.setPadding(new Insets(24));
        setCenter(content);
    }

    @Override
    public void onShown() {
        var result = service.listAuditLog();
        if (result.isSuccess()) {
            TableView<AuditLogEntry> table = auditTable(result.value().orElseThrow());
            results.getChildren().setAll(UiComponents.panel("Application activity", table));
        } else {
            results.getChildren().setAll(UiComponents.feedback(
                    "Audit log unavailable",
                    "Sign in as an administrator and try again.",
                    FeedbackType.ERROR));
        }
    }

    private static TableView<AuditLogEntry> auditTable(List<AuditLogEntry> entries) {
        TableView<AuditLogEntry> table = new TableView<>();
        table.setId("audit-log-table");
        table.getStyleClass().add("audit-log-table");
        table.setFixedCellSize(ROW_HEIGHT);
        table.setAccessibleText("Application audit log");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.getColumns().add(column("Time", entry -> TIMESTAMP.format(entry.occurredAt())));
        table.getColumns().add(column("Event ID", AuditLogEntry::eventIdentifier));
        table.getColumns().add(actorColumn());
        table.getColumns().add(eventColumn());
        table.getItems().setAll(entries);
        table.setPlaceholder(new Label("No audit activity recorded"));
        table.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2 && table.getSelectionModel().getSelectedItem() != null) {
                showDetails(table.getSelectionModel().getSelectedItem());
            }
        });
        return table;
    }

    private static TableColumn<AuditLogEntry, AuditLogEntry> actorColumn() {
        TableColumn<AuditLogEntry, AuditLogEntry> column = new TableColumn<>("Actor");
        column.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue()));
        column.setCellFactory(ignored -> entryCell(entry -> {
            Label role = UiComponents.badge(display(entry.actorRole().name()), SemanticTone.NEUTRAL);
            return new HBox(8, new Label(entry.actorLabel()), role);
        }));
        return column;
    }

    private static TableColumn<AuditLogEntry, AuditLogEntry> eventColumn() {
        TableColumn<AuditLogEntry, AuditLogEntry> column = new TableColumn<>("Event");
        column.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue()));
        column.setCellFactory(ignored -> entryCell(entry -> {
            SemanticTone tone = entry.outcome() == AuditOutcome.SUCCESS
                    ? SemanticTone.SUCCESS : SemanticTone.DANGER;
            Label outcome = UiComponents.badge(display(entry.outcome().name()), tone);
            Label description = new Label(entry.eventDescription());
            description.setTextOverrun(OverrunStyle.ELLIPSIS);
            description.setMaxWidth(Double.MAX_VALUE);
            HBox row = new HBox(8, description, outcome);
            row.setAlignment(Pos.CENTER_LEFT);
            HBox.setHgrow(description, Priority.ALWAYS);
            return row;
        }));
        return column;
    }

    private static TableCell<AuditLogEntry, AuditLogEntry> entryCell(
            Function<AuditLogEntry, Node> content) {
        return new TableCell<>() {
            @Override
            protected void updateItem(AuditLogEntry entry, boolean empty) {
                super.updateItem(entry, empty);
                setText(null);
                setGraphic(empty || entry == null ? null : content.apply(entry));
            }
        };
    }

    private static void showDetails(AuditLogEntry entry) {
        TextArea details = new TextArea(detailText(entry));
        details.setId("audit-event-details");
        details.setEditable(false);
        details.setWrapText(true);
        details.setPrefRowCount(12);

        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Audit event details");
        dialog.setHeaderText(entry.eventDescription());
        dialog.getDialogPane().setContent(details);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.getDialogPane().setAccessibleText("Audit event details for " + entry.eventIdentifier());
        dialog.show();
    }

    private static String detailText(AuditLogEntry entry) {
        String changes = entry.details().isBlank() ? "None" : entry.details();
        return "Event ID: " + entry.eventIdentifier()
                + "\nTime: " + TIMESTAMP.format(entry.occurredAt())
                + "\nActor: " + entry.actorLabel() + " (" + display(entry.actorRole().name()) + ")"
                + "\nAction: " + display(entry.action().name())
                + "\nTarget: " + display(entry.targetType().name()) + " " + entry.targetIdentifier()
                + "\nOutcome: " + display(entry.outcome().name())
                + "\n\nDetails:\n" + changes;
    }

    private static TableColumn<AuditLogEntry, String> column(
            String title, Function<AuditLogEntry, String> value) {
        TableColumn<AuditLogEntry, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(value.apply(cell.getValue())));
        return column;
    }

    private static String display(String enumName) {
        String text = enumName.toLowerCase(Locale.ENGLISH).replace('_', ' ');
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}
