package com.company.incidentdesk.ui.navigation;

import java.util.Objects;
import java.util.function.Consumer;

import com.company.incidentdesk.application.notification.NotificationInbox;
import com.company.incidentdesk.domain.account.Account;
import com.company.incidentdesk.ui.shared.components.ActionStyle;
import com.company.incidentdesk.ui.shared.components.NotificationCenter;
import com.company.incidentdesk.ui.shared.components.UiComponents;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/** Shared navigation and current-user frame for every authenticated role area. */
public final class AuthenticatedShell extends BorderPane implements AutoCloseable {
    private final NotificationCenter notificationCenter;
    private final VBox content = new VBox();

    public AuthenticatedShell(
            Account account,
            NotificationInbox notifications,
            Consumer<ApplicationRoute> onNavigate,
            Runnable onLogout) {
        Account currentAccount = Objects.requireNonNull(account, "account");
        Objects.requireNonNull(onNavigate, "onNavigate");
        Objects.requireNonNull(onLogout, "onLogout");

        notificationCenter = new NotificationCenter(
                Objects.requireNonNull(notifications, "notifications"), currentAccount.id());
        setLeft(createNavigation(currentAccount, onNavigate, onLogout));
        content.getStyleClass().add("shell-content");
        content.setId("shell-content");
        setCenter(content);
        getStyleClass().add("authenticated-shell");
    }

    public void show(Node view) {
        content.getChildren().setAll(Objects.requireNonNull(view, "view"));
        VBox.setVgrow(view, Priority.ALWAYS);
    }

    private VBox createNavigation(
            Account account,
            Consumer<ApplicationRoute> onNavigate,
            Runnable onLogout) {
        Label product = new Label("Incident Desk");
        product.getStyleClass().add("shell-product");
        Region brandSpacer = new Region();
        HBox.setHgrow(brandSpacer, Priority.ALWAYS);
        HBox brand = new HBox(8, product, brandSpacer, notificationCenter);
        brand.setAlignment(Pos.CENTER_LEFT);
        brand.getStyleClass().add("shell-brand");

        Button dashboard = UiComponents.action(ApplicationRoute.DASHBOARD.label(), ActionStyle.GHOST);
        dashboard.setMaxWidth(Double.MAX_VALUE);
        dashboard.setId("dashboard-navigation");
        dashboard.getStyleClass().add("active-navigation");
        dashboard.setOnAction(event -> onNavigate.accept(ApplicationRoute.DASHBOARD));
        HBox identity = createIdentity(account);
        Button logout = UiComponents.action("Log out", ActionStyle.GHOST);
        logout.setId("logout-navigation");
        logout.setMaxWidth(Double.MAX_VALUE);
        logout.setAccessibleText("Log out " + account.loginName());
        logout.setOnAction(event -> onLogout.run());

        Region navigationSpacer = new Region();
        VBox.setVgrow(navigationSpacer, Priority.ALWAYS);
        VBox navigation = new VBox(16, brand, dashboard, navigationSpacer, identity, logout);
        navigation.setPadding(new Insets(20, 12, 20, 12));
        navigation.getStyleClass().add("shell-navigation");
        return navigation;
    }

    private static HBox createIdentity(Account account) {
        Label avatar = UiComponents.avatar(initial(account.loginName()), account.loginName());
        avatar.setId("current-user-avatar");
        Label user = new Label(account.loginName());
        user.getStyleClass().add("shell-user-name");
        Label role = new Label(roleLabel(account));
        role.getStyleClass().add("shell-user-role");
        VBox labels = new VBox(2, user, role);
        HBox identity = new HBox(10, avatar, labels);
        identity.setAlignment(Pos.CENTER_LEFT);
        identity.setAccessibleText("Signed in as " + account.loginName() + ", " + roleLabel(account));
        identity.getStyleClass().add("shell-identity");
        return identity;
    }

    private static String initial(String loginName) {
        return loginName.substring(0, 1).toUpperCase();
    }

    private static String roleLabel(Account account) {
        return switch (account.role()) {
        case REPORTER -> "Reporter";
        case RESPONDER -> "Responder";
        case ADMINISTRATOR -> "Administrator";
        };
    }

    @Override
    public void close() {
        notificationCenter.close();
    }
}
