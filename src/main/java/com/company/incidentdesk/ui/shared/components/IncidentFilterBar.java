package com.company.incidentdesk.ui.shared.components;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.company.incidentdesk.domain.account.AccountId;
import com.company.incidentdesk.application.presentation.IncidentDisplayLabels;
import com.company.incidentdesk.domain.incident.IncidentCategory;
import com.company.incidentdesk.domain.incident.IncidentStatus;
import com.company.incidentdesk.persistence.AssignmentState;
import com.company.incidentdesk.persistence.IncidentSearchCriteria;
import com.company.incidentdesk.persistence.IncidentSloState;
import com.company.incidentdesk.persistence.IncidentSort;
import com.company.incidentdesk.persistence.IncidentSortField;
import com.company.incidentdesk.persistence.SortDirection;

import javafx.collections.FXCollections;
import javafx.animation.PauseTransition;
import javafx.css.PseudoClass;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Control;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/** Reusable, stateful controls for the shared incident search language. */
public final class IncidentFilterBar extends VBox {
    private static final Duration SEARCH_DEBOUNCE = Duration.millis(200);
    static final double FILTER_WIDTH = 165;
    static final double MENU_ROW_WIDTH = FILTER_WIDTH - 16;
    private final TextField search = new TextField();
    private final MultiSelectDropdown<IncidentCategory> categories = new MultiSelectDropdown<>(
            "All categories", IncidentCategory.values(), IncidentDisplayLabels::category);
    private final MultiSelectDropdown<IncidentStatus> statuses = new MultiSelectDropdown<>(
            "All statuses", IncidentStatus.values(), IncidentDisplayLabels::status);
    private final ComboBox<AssignmentState> assignment = combo(AssignmentState.values());
    private final ComboBox<AccountOption> reporters = new ComboBox<>();
    private final ComboBox<AccountOption> responders = new ComboBox<>();
    private final DatePicker createdFrom = new DatePicker();
    private final DatePicker createdThrough = new DatePicker();
    private final MultiSelectDropdown<IncidentSloState> sloStates = new MultiSelectDropdown<>(
            "All SLO states", IncidentSloState.values(), IncidentDisplayLabels::sloState);
    private final ComboBox<IncidentSortField> sortField = combo(IncidentSortField.values());
    private final ComboBox<SortDirection> sortDirection = combo(SortDirection.values());
    private final PauseTransition searchDebounce = new PauseTransition(SEARCH_DEBOUNCE);
    private Consumer<IncidentSearchCriteria> onSearch = ignored -> { };
    private boolean suppressSearch;

    public IncidentFilterBar() {
        super(12);
        getStyleClass().add("incident-filter-bar");
        search.setPromptText("Search title, description, or incident ID");
        search.setAccessibleText("Search incidents");
        HBox.setHgrow(search, Priority.ALWAYS);

        assignment.setValue(AssignmentState.ANY);
        sortField.setValue(IncidentSortField.CREATED_AT);
        sortDirection.setValue(SortDirection.DESCENDING);
        reporters.setPromptText("Any reporter");
        responders.setPromptText("Any responder");

        Button reset = UiComponents.action("Reset", ActionStyle.SECONDARY);
        reset.setOnAction(event -> {
            setCriteria(IncidentSearchCriteria.defaults());
            searchNow();
        });
        searchDebounce.setOnFinished(event -> searchNow());
        search.textProperty().addListener((observable, previous, current) -> restartSearchDebounce());
        search.setOnAction(event -> {
            searchDebounce.stop();
            searchNow();
        });
        categories.setOnChange(this::searchNow);
        statuses.setOnChange(this::searchNow);
        sloStates.setOnChange(this::searchNow);
        assignment.valueProperty().addListener((observable, previous, current) -> searchNow());
        reporters.valueProperty().addListener((observable, previous, current) -> searchNow());
        responders.valueProperty().addListener((observable, previous, current) -> searchNow());
        createdFrom.valueProperty().addListener((observable, previous, current) -> searchNow());
        createdThrough.valueProperty().addListener((observable, previous, current) -> searchNow());
        sortField.valueProperty().addListener((observable, previous, current) -> searchNow());
        sortDirection.valueProperty().addListener((observable, previous, current) -> searchNow());

        HBox query = new HBox(10, search, reset);
        query.setAlignment(Pos.CENTER_LEFT);
        FlowPane filters = new FlowPane(10, 10,
                labelled("Categories", categories), labelled("Statuses", statuses),
                labelled("Assignment", assignment), labelled("Reporter", reporters),
                labelled("Responder", responders), labelled("Created from", createdFrom),
                labelled("Created through", createdThrough), labelled("SLO", sloStates),
                labelled("Sort by", sortField), labelled("Direction", sortDirection));
        getChildren().addAll(query, filters);
        setCriteria(IncidentSearchCriteria.defaults());
    }

    /** Supplies already-authorized identity options; anonymous reporters must never be included. */
    public void setIdentityOptions(List<AccountOption> reporterOptions, List<AccountOption> responderOptions) {
        Optional<AccountId> selectedReporter = selectedAccount(reporters);
        Optional<AccountId> selectedResponder = selectedAccount(responders);
        suppressSearch = true;
        try {
            reporters.setItems(FXCollections.observableArrayList(withEmptyOption("Any reporter", reporterOptions)));
            responders.setItems(FXCollections.observableArrayList(withEmptyOption("Any responder", responderOptions)));
            selectAccount(reporters, selectedReporter);
            selectAccount(responders, selectedResponder);
        } finally {
            suppressSearch = false;
        }
    }

    public void setIdentityFiltersVisible(boolean visible) {
        setVisibleAndManaged(reporters.getParent(), visible);
        setVisibleAndManaged(responders.getParent(), visible);
    }

    public void setSloFilterVisible(boolean visible) {
        setVisibleAndManaged(sloStates.getParent(), visible);
    }

    public void setOnSearch(Consumer<IncidentSearchCriteria> handler) {
        onSearch = Objects.requireNonNull(handler, "handler");
    }

    public IncidentSearchCriteria criteria() {
        ZoneId zone = ZoneId.systemDefault();
        return new IncidentSearchCriteria(
                search.getText(), categories.selected(), statuses.selected(), assignment.getValue(),
                selectedAccount(reporters), selectedAccount(responders),
                Optional.ofNullable(createdFrom.getValue()).map(date -> date.atStartOfDay(zone).toInstant()),
                Optional.ofNullable(createdThrough.getValue())
                        .map(date -> date.plusDays(1).atStartOfDay(zone).toInstant().minusNanos(1)),
                sloStates.selected(), new IncidentSort(sortField.getValue(), sortDirection.getValue()));
    }

    /** Restores a previous snapshot so list/detail navigation can retain filter state. */
    public void setCriteria(IncidentSearchCriteria criteria) {
        IncidentSearchCriteria value = Objects.requireNonNull(criteria, "criteria");
        suppressSearch = true;
        try {
            searchDebounce.stop();
            search.setText(value.text());
            categories.select(value.categories());
            statuses.select(value.statuses());
            assignment.setValue(value.assignmentState());
            selectAccount(reporters, value.reporterId());
            selectAccount(responders, value.responderId());
            ZoneId zone = ZoneId.systemDefault();
            createdFrom.setValue(value.createdFrom().map(at -> LocalDate.ofInstant(at, zone)).orElse(null));
            createdThrough.setValue(value.createdThrough().map(at -> LocalDate.ofInstant(at, zone)).orElse(null));
            sloStates.select(value.sloStates());
            sortField.setValue(value.sort().field());
            sortDirection.setValue(value.sort().direction());
        } finally {
            suppressSearch = false;
        }
    }

    private void restartSearchDebounce() {
        if (!suppressSearch) {
            searchDebounce.playFromStart();
        }
    }

    private void searchNow() {
        if (!suppressSearch) {
            onSearch.accept(criteria());
        }
    }

    private static VBox labelled(String text, Node control) {
        Label label = new Label(text);
        label.setLabelFor(control);
        VBox wrapper = new VBox(5, label, control);
        wrapper.getStyleClass().add("filter-field");
        wrapper.setMinWidth(FILTER_WIDTH);
        wrapper.setPrefWidth(FILTER_WIDTH);
        wrapper.setMaxWidth(FILTER_WIDTH);
        if (control instanceof Control sizedControl) {
            sizedControl.setMinWidth(FILTER_WIDTH);
            sizedControl.setPrefWidth(FILTER_WIDTH);
            sizedControl.setMaxWidth(FILTER_WIDTH);
        }
        return wrapper;
    }

    private static <T> ComboBox<T> combo(T[] values) {
        return new ComboBox<>(FXCollections.observableArrayList(values));
    }

    private static Optional<AccountId> selectedAccount(ComboBox<AccountOption> comboBox) {
        return Optional.ofNullable(comboBox.getValue()).flatMap(AccountOption::filterId);
    }

    private static void selectAccount(ComboBox<AccountOption> comboBox, Optional<AccountId> accountId) {
        comboBox.setValue(accountId.flatMap(id -> comboBox.getItems().stream()
                .filter(option -> option.filterId().filter(id::equals).isPresent()).findFirst()).orElse(null));
    }

    private static List<AccountOption> withEmptyOption(String label, List<AccountOption> options) {
        Objects.requireNonNull(options, "options");
        return Stream.concat(
                Stream.of(AccountOption.empty(label)),
                options.stream()).toList();
    }

    private static void setVisibleAndManaged(Node node, boolean visible) {
        node.setVisible(visible);
        node.setManaged(visible);
    }

    /** Compact multi-select menu used where an ordinary combo box cannot represent multiple values. */
    private static final class MultiSelectDropdown<T> extends MenuButton {
        private final String emptyLabel;
        private final Function<T, String> label;
        private final List<Option<T>> options;
        private Runnable onChange = () -> { };

        private MultiSelectDropdown(String emptyLabel, T[] values, Function<T, String> label) {
            this.emptyLabel = Objects.requireNonNull(emptyLabel, "emptyLabel");
            this.label = Objects.requireNonNull(label, "label");
            options = Arrays.stream(values)
                    .map(value -> option(value, label.apply(value)))
                    .toList();
            options.forEach(option -> {
                option.item().setOnAction(event -> {
                    option.toggle();
                    updateLabel();
                    onChange.run();
                });
                getItems().add(option.item());
            });
            setAccessibleText(emptyLabel);
            updateLabel();
        }

        private Set<T> selected() {
            return options.stream()
                    .filter(Option::isSelected)
                    .map(Option::value)
                    .collect(Collectors.toUnmodifiableSet());
        }

        private void setOnChange(Runnable handler) {
            onChange = Objects.requireNonNull(handler, "handler");
        }

        private void select(Set<T> values) {
            options.forEach(option -> option.setSelected(values.contains(option.value())));
            updateLabel();
        }

        private void updateLabel() {
            List<String> selectedLabels = options.stream()
                    .filter(Option::isSelected)
                    .map(option -> label.apply(option.value()))
                    .toList();
            String text = selectedLabels.isEmpty() ? emptyLabel : String.join(", ", selectedLabels);
            setText(text);
            setAccessibleText(text);
        }

        private static <T> Option<T> option(T value, String text) {
            return new Option<>(value, text);
        }

        private static final class Option<T> {
            private static final PseudoClass SELECTED = PseudoClass.getPseudoClass("selected");
            private final T value;
            private final String text;
            private final Label checkmark = new Label("\u2713");
            private final HBox row;
            private final CustomMenuItem item;
            private boolean selected;

            private Option(T value, String text) {
                this.value = value;
                this.text = text;
                Label label = new Label(text);
                label.setMaxWidth(Double.MAX_VALUE);
                HBox.setHgrow(label, Priority.ALWAYS);
                row = new HBox(label, checkmark);
                row.getStyleClass().add("multi-select-menu-row");
                row.setMinWidth(MENU_ROW_WIDTH);
                row.setPrefWidth(MENU_ROW_WIDTH);
                row.setMaxWidth(MENU_ROW_WIDTH);
                item = new CustomMenuItem(row, false);
                item.setHideOnClick(false);
                setSelected(false);
            }

            private T value() {
                return value;
            }

            private CustomMenuItem item() {
                return item;
            }

            private boolean isSelected() {
                return selected;
            }

            private void toggle() {
                setSelected(!selected);
            }

            private void setSelected(boolean selected) {
                this.selected = selected;
                checkmark.setVisible(selected);
                row.pseudoClassStateChanged(SELECTED, selected);
                row.setAccessibleText(text + (selected ? ", selected" : ", not selected"));
            }
        }
    }

    /** Safe label/id pair for identity filter options. */
    public record AccountOption(AccountId id, String displayName, boolean empty) {
        public AccountOption(AccountId id, String displayName) {
            this(id, displayName, false);
        }

        public AccountOption {
            if (!empty) {
                Objects.requireNonNull(id, "id");
            } else if (id != null) {
                throw new IllegalArgumentException("Empty option must not have an account id");
            }
            Objects.requireNonNull(displayName, "displayName");
            if (displayName.isBlank()) {
                throw new IllegalArgumentException("displayName must not be blank");
            }
        }

        private static AccountOption empty(String displayName) {
            return new AccountOption(null, displayName, true);
        }

        private Optional<AccountId> filterId() {
            return empty ? Optional.empty() : Optional.of(id);
        }

        @Override
        public String toString() {
            return displayName;
        }
    }
}
