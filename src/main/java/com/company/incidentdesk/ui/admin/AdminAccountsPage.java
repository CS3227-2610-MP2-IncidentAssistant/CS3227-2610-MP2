package com.company.incidentdesk.ui.admin;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.company.incidentdesk.application.account.AccountDirectoryService;
import com.company.incidentdesk.application.account.AccountDeletionService;
import com.company.incidentdesk.application.audit.AuditActorLabelResolver;
import com.company.incidentdesk.domain.account.Account;
import com.company.incidentdesk.domain.account.AccountStatus;
import com.company.incidentdesk.domain.account.Role;
import com.company.incidentdesk.domain.incident.IncidentCategory;
import com.company.incidentdesk.ui.shared.components.ActionStyle;
import com.company.incidentdesk.ui.shared.components.FeedbackType;
import com.company.incidentdesk.ui.shared.components.SemanticTone;
import com.company.incidentdesk.ui.shared.components.UiComponents;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;

/** Administrator account directory. */
public final class AdminAccountsPage extends BorderPane {
    private final AccountDirectoryService directory;
    private final AccountDeletionService deletion;
    private final VBox content = new VBox(16);

    public AdminAccountsPage(AccountDirectoryService directory, AccountDeletionService deletion) {
        this.directory = Objects.requireNonNull(directory, "directory");
        this.deletion = Objects.requireNonNull(deletion, "deletion");
        Label title = new Label("Accounts");
        title.getStyleClass().add("page-title");
        content.getChildren().add(title);
        content.setPadding(new Insets(24));
        refresh();
        setCenter(content);
    }

    private void refresh() {
        content.getChildren().remove(1, content.getChildren().size());
        var result = directory.listAccounts();
        if (result.isSuccess()) {
            content.getChildren().add(UiComponents.panel("Users", accountTable(result.value().orElseThrow())));
        } else {
            content.getChildren().add(UiComponents.feedback(
                    "Accounts unavailable", "Sign in as an administrator and try again.", FeedbackType.ERROR));
        }
    }

    private TableView<Account> accountTable(List<Account> accounts) {
        TableView<Account> table = new TableView<>();
        table.setAccessibleText("User accounts");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.getColumns().add(column("Login name", account -> account.isDeleted()
                ? AuditActorLabelResolver.DELETED_ACCOUNT_LABEL : account.loginName()));
        table.getColumns().add(nodeColumn("Role", account -> roleBadge(account.role())));
        table.getColumns().add(nodeColumn("Status", account -> statusBadge(account.status())));
        table.getColumns().add(nodeColumn("Responder categories", this::categoryBadges));
        table.getColumns().add(actionColumn());
        table.getItems().setAll(accounts);
        table.setPlaceholder(new Label("No user accounts found"));
        return table;
    }

    private Label roleBadge(Role role) {
        return switch (role) {
        case REPORTER -> UiComponents.badge("Reporter", SemanticTone.NEUTRAL);
        case RESPONDER -> UiComponents.badge("Responder", SemanticTone.INFO);
        case ADMINISTRATOR -> UiComponents.badge("Administrator", SemanticTone.WARNING);
        };
    }

    private Label statusBadge(AccountStatus status) {
        return switch (status) {
        case ENABLED -> UiComponents.badge("Enabled", SemanticTone.SUCCESS);
        case DISABLED -> UiComponents.badge("Disabled", SemanticTone.WARNING);
        case DELETED -> UiComponents.badge("Deleted", SemanticTone.NEUTRAL);
        };
    }

    private Node categoryBadges(Account account) {
        if (account.responderAccess().isEmpty()) {
            return new Label("—");
        }
        FlowPane badges = new FlowPane(6, 6);
        for (IncidentCategory category : IncidentCategory.values()) {
            if (account.responderAccess().permits(category)) {
                badges.getChildren().add(UiComponents.badge(category.displayName(), SemanticTone.INFO));
            }
        }
        badges.setAccessibleText("Responder categories: " + account.responderAccess().categories().stream()
                .map(IncidentCategory::displayName)
                .sorted()
                .collect(Collectors.joining(", ")));
        return badges;
    }

    private TableColumn<Account, Void> actionColumn() {
        TableColumn<Account, Void> column = new TableColumn<>("Actions");
        column.setCellFactory(ignored -> new TableCell<>() {
            private final Button delete = UiComponents.action("Delete", ActionStyle.DANGER);
            {
                delete.setAccessibleText("Delete account");
                delete.setOnAction(event -> confirmDeletion(getTableView().getItems().get(getIndex())));
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                    return;
                }
                Account account = getTableView().getItems().get(getIndex());
                delete.setDisable(!deletion.canDelete(account.id()));
                delete.setAccessibleText("Delete account " + account.loginName());
                setGraphic(delete);
            }
        });
        return column;
    }

    private void confirmDeletion(Account account) {
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete " + account.loginName() + "? Their login will be disabled while incident and audit history is retained.",
                ButtonType.CANCEL, ButtonType.OK);
        confirmation.setTitle("Delete account");
        confirmation.setHeaderText("This action cannot be undone");
        if (confirmation.showAndWait().filter(ButtonType.OK::equals).isEmpty()) {
            return;
        }
        var result = deletion.delete(account.id());
        if (result.isSuccess()) {
            refresh();
        } else {
            content.getChildren().add(UiComponents.feedback(
                    "Account was not deleted", "The account may already be deleted or no longer available.",
                    FeedbackType.ERROR));
        }
    }

    private TableColumn<Account, String> column(String title, Function<Account, String> value) {
        TableColumn<Account, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(value.apply(cell.getValue())));
        return column;
    }

    private TableColumn<Account, Account> nodeColumn(String title, Function<Account, Node> value) {
        TableColumn<Account, Account> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue()));
        column.setCellFactory(ignored -> new TableCell<>() {
            @Override
            protected void updateItem(Account account, boolean empty) {
                super.updateItem(account, empty);
                setGraphic(empty || account == null ? null : value.apply(account));
                setText(null);
            }
        });
        return column;
    }
}
