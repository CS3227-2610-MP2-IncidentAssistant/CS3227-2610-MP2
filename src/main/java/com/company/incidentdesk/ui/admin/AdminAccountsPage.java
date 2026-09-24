package com.company.incidentdesk.ui.admin;

import com.company.incidentdesk.application.account.AccountDirectoryService;
import com.company.incidentdesk.domain.account.Account;
import com.company.incidentdesk.ui.shared.components.FeedbackType;
import com.company.incidentdesk.ui.shared.components.UiComponents;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;

/** Administrator account directory. */
public final class AdminAccountsPage extends BorderPane {
    public AdminAccountsPage(AccountDirectoryService service) {
        Label title = new Label("Accounts");
        title.getStyleClass().add("page-title");
        VBox content = new VBox(16, title);
        content.setPadding(new Insets(24));
        var result = service.listAccounts();
        if (result.isSuccess()) {
            content.getChildren().add(UiComponents.panel("Users", accountTable(result.value().orElseThrow())));
        } else {
            content.getChildren().add(UiComponents.feedback(
                    "Accounts unavailable", "Sign in as an administrator and try again.", FeedbackType.ERROR));
        }
        setCenter(content);
    }

    private TableView<Account> accountTable(java.util.List<Account> accounts) {
        TableView<Account> table = new TableView<>();
        table.setAccessibleText("User accounts");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.getColumns().add(column("Login name", Account::loginName));
        table.getColumns().add(column("Role", account -> account.role().name()));
        table.getColumns().add(column("Status", account -> account.status().name()));
        table.getColumns().add(column("Responder categories", account -> account.responderAccess().categories().toString()));
        table.getItems().setAll(accounts);
        table.setPlaceholder(new Label("No user accounts found"));
        return table;
    }

    private TableColumn<Account, String> column(String title, java.util.function.Function<Account, String> value) {
        TableColumn<Account, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(value.apply(cell.getValue())));
        return column;
    }
}
