package com.company.incidentdesk.ui.navigation;

import java.util.EnumMap;
import java.util.Objects;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;

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
    private final Map<ApplicationRoute, Button> navigationButtons = new EnumMap<>(ApplicationRoute.class);

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

    public void show(ApplicationRoute route, Node view) {
        selectRoute(Objects.requireNonNull(route, "route"));
        showContent(view);
    }

    public void showDetail(Node view) {
        showContent(view);
    }

    public void showUnavailable(Node view) {
        selectRoute(null);
        showContent(view);
    }

    private void showContent(Node view) {
        content.getChildren().setAll(Objects.requireNonNull(view, "view"));
        VBox.setVgrow(view, Priority.ALWAYS);
    }

    private void selectRoute(ApplicationRoute selectedRoute) {
        navigationButtons.forEach((route, button) -> {
            boolean selected = route == selectedRoute;
            if (selected && !button.getStyleClass().contains("active-navigation")) {
                button.getStyleClass().add("active-navigation");
            } else if (!selected) {
                button.getStyleClass().remove("active-navigation");
            }
        });
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

        VBox destinations = new VBox(8);
        Stream.of(ApplicationRoute.values())
                .filter(route -> route.isAvailableTo(account.role()))
                .map(route -> navigationButton(route, onNavigate))
                .forEach(destinations.getChildren()::add);
        HBox identity = createIdentity(account);
        Button logout = UiComponents.action("Log out", ActionStyle.GHOST);
        logout.setId("logout-navigation");
        logout.setMaxWidth(Double.MAX_VALUE);
        logout.setAccessibleText("Log out " + account.loginName());
        logout.setOnAction(event -> onLogout.run());

        Region navigationSpacer = new Region();
        VBox.setVgrow(navigationSpacer, Priority.ALWAYS);
        VBox navigation = new VBox(16, brand, destinations, navigationSpacer, identity, logout);
        navigation.setPadding(new Insets(20, 12, 20, 12));
        navigation.getStyleClass().add("shell-navigation");
        return navigation;
    }

    private Button navigationButton(
            ApplicationRoute route,
            Consumer<ApplicationRoute> onNavigate) {
        Button button = UiComponents.action(route.label(), ActionStyle.GHOST);
        button.setMaxWidth(Double.MAX_VALUE);
        button.setId(route.name().toLowerCase(Locale.ROOT).replace('_', '-') + "-navigation");
        button.setOnAction(event -> onNavigate.accept(route));
        navigationButtons.put(route, button);
        return button;
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
