package com.company.incidentdesk.ui.admin;

import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.company.incidentdesk.application.result.ApplicationError;
import com.company.incidentdesk.application.result.ApplicationResult;
import com.company.incidentdesk.application.slo.SloConfigurationGateway;
import com.company.incidentdesk.domain.incident.IncidentCategory;
import com.company.incidentdesk.domain.slo.SloTargetVersion;

/** UI-thread state holder for the administrator SLO configuration workflow. */
public final class AdminSloPresenter {
    public enum State { LOADING, READY, SAVED, VALIDATION_ERROR, UNAVAILABLE, STORAGE_ERROR }

    private final SloConfigurationGateway configurations;
    private final Map<IncidentCategory, SloTargetVersion> current = new EnumMap<>(IncidentCategory.class);
    private IncidentCategory selectedCategory = IncidentCategory.IT;
    private List<SloTargetVersion> history = List.of();
    private State state = State.LOADING;

    public AdminSloPresenter(SloConfigurationGateway configurations) {
        this.configurations = Objects.requireNonNull(configurations, "configurations");
    }

    public void load() {
        state = State.LOADING;
        current.clear();
        history = List.of();
        ApplicationResult<List<SloTargetVersion>> targets = configurations.currentTargets();
        if (!targets.isSuccess()) {
            fail(targets.error().orElseThrow());
            return;
        }
        current.clear();
        targets.value().orElseThrow().forEach(version -> current.put(version.category(), version));
        loadHistory(State.READY);
    }

    public void selectCategory(IncidentCategory category) {
        selectedCategory = Objects.requireNonNull(category, "category");
        loadHistory(State.READY);
    }

    public void save(String claimMinutes, String progressMinutes, String reopenPercent) {
        if (!hasChanges(claimMinutes, progressMinutes, reopenPercent)) {
            state = State.READY;
            return;
        }
        Optional<ParsedTargets> parsed = parse(claimMinutes, progressMinutes, reopenPercent);
        if (parsed.isEmpty()) {
            state = State.VALIDATION_ERROR;
            return;
        }
        ParsedTargets values = parsed.orElseThrow();
        ApplicationResult<SloTargetVersion> saved = configurations.configure(
                selectedCategory, values.claim(), values.progress(), values.reopenRate());
        if (!saved.isSuccess()) {
            fail(saved.error().orElseThrow());
            return;
        }
        current.put(selectedCategory, saved.value().orElseThrow());
        loadHistory(State.SAVED);
    }

    public boolean hasChanges(String claimMinutes, String progressMinutes, String reopenPercent) {
        Optional<ParsedTargets> parsed = parse(claimMinutes, progressMinutes, reopenPercent);
        Optional<SloTargetVersion> effective = current(selectedCategory);
        if (parsed.isEmpty()) {
            return effective.isPresent() || hasEnteredValue(claimMinutes, progressMinutes, reopenPercent);
        }
        if (effective.isEmpty()) {
            return true;
        }
        ParsedTargets values = parsed.orElseThrow();
        var target = effective.orElseThrow().target();
        return !values.claim().equals(target.timeToClaimTarget())
                || !values.progress().equals(target.timeInProgressTarget())
                || Double.compare(values.reopenRate(), target.reopenRateTarget()) != 0;
    }

    public State state() {
        return state;
    }

    public IncidentCategory selectedCategory() {
        return selectedCategory;
    }

    public Optional<SloTargetVersion> current(IncidentCategory category) {
        return Optional.ofNullable(current.get(Objects.requireNonNull(category, "category")));
    }

    public List<SloTargetVersion> history() {
        return history;
    }

    private void loadHistory(State successState) {
        ApplicationResult<List<SloTargetVersion>> result = configurations.history(selectedCategory);
        if (!result.isSuccess()) {
            fail(result.error().orElseThrow());
            return;
        }
        history = result.value().orElseThrow();
        state = successState;
    }

    private void fail(ApplicationError error) {
        state = switch (error.code()) {
        case VALIDATION -> State.VALIDATION_ERROR;
        case PERSISTENCE_FAILURE, CORRUPT_DATA -> State.STORAGE_ERROR;
        case ACCESS_DENIED, RESOURCE_UNAVAILABLE, INVALID_STATE -> State.UNAVAILABLE;
        };
        if (state == State.UNAVAILABLE) {
            current.clear();
            history = List.of();
        }
    }

    private Optional<ParsedTargets> parse(String claimMinutes, String progressMinutes, String reopenPercent) {
        try {
            long claim = Long.parseLong(Objects.requireNonNull(claimMinutes, "claimMinutes").trim());
            long progress = Long.parseLong(Objects.requireNonNull(progressMinutes, "progressMinutes").trim());
            double percent = Double.parseDouble(Objects.requireNonNull(reopenPercent, "reopenPercent").trim());
            if (claim < 0 || progress < 0 || !Double.isFinite(percent) || percent < 0 || percent > 100) {
                return Optional.empty();
            }
            return Optional.of(new ParsedTargets(
                    Duration.ofMinutes(claim), Duration.ofMinutes(progress), percent / 100));
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }

    private boolean hasEnteredValue(String claimMinutes, String progressMinutes, String reopenPercent) {
        return !Objects.requireNonNull(claimMinutes, "claimMinutes").isBlank()
                || !Objects.requireNonNull(progressMinutes, "progressMinutes").isBlank()
                || !Objects.requireNonNull(reopenPercent, "reopenPercent").isBlank();
    }

    private record ParsedTargets(Duration claim, Duration progress, double reopenRate) { }
}
