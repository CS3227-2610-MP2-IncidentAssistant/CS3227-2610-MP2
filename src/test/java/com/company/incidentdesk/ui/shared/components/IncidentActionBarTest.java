package com.company.incidentdesk.ui.shared.components;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.company.incidentdesk.application.presentation.IncidentActionModel;
import com.company.incidentdesk.domain.incident.IncidentId;

import javafx.application.Platform;
import javafx.scene.control.Button;

class IncidentActionBarTest {
    private static final IncidentId ID = new IncidentId(java.util.UUID.randomUUID());

    @BeforeAll
    static void startJavaFx() throws InterruptedException {
        CountDownLatch started = new CountDownLatch(1);
        try {
            Platform.startup(started::countDown);
        } catch (IllegalStateException alreadyStarted) {
            Platform.runLater(started::countDown);
        }
        assertTrue(started.await(10, TimeUnit.SECONDS));
    }

    @Test
    void rendersOnlyModelPermittedActionsWithConfiguredTypedHandlers() throws Exception {
        runOnJavaFx(() -> {
            AtomicReference<IncidentId> claimed = new AtomicReference<>();
            IncidentDetailActions handlers = new IncidentDetailActions(
                    Optional.empty(), Optional.empty(), Optional.of(claimed::set), Optional.empty(),
                    Optional.empty(), Optional.empty(), Optional.empty());
            IncidentActionModel allowedClaim = new IncidentActionModel(
                    false, false, true, false, false, false, false, false, false);
            IncidentActionBar bar = new IncidentActionBar(ID, allowedClaim, handlers);

            assertEquals(1, bar.getChildren().size());
            Button claim = (Button) bar.getChildren().getFirst();
            assertEquals("Claim", claim.getText());
            claim.fire();
            assertEquals(ID, claimed.get());
        });
    }

    @Test
    void reassignActionReadsAsAssignForAnUnassignedIncident() throws Exception {
        runOnJavaFx(() -> {
            IncidentActionModel allowedReassign = new IncidentActionModel(
                    false, false, false, false, false, true, false, false, false);

            IncidentActionBar assigned = new IncidentActionBar(
                    ID, allowedReassign, IncidentDetailActions.none(), false);
            assertEquals("Reassign", ((Button) assigned.getChildren().getFirst()).getText());

            IncidentActionBar unassigned = new IncidentActionBar(
                    ID, allowedReassign, IncidentDetailActions.none(), true);
            assertEquals("Assign", ((Button) unassigned.getChildren().getFirst()).getText());
        });
    }

    @Test
    void showsAuthorizedActionsAsDisabledUntilAWorkflowHandlerIsConnected() throws Exception {
        runOnJavaFx(() -> {
            IncidentActionModel allAllowed = new IncidentActionModel(
                    true, true, true, true, true, true, true, true, true);
            IncidentActionBar bar = new IncidentActionBar(ID, allAllowed, IncidentDetailActions.none());

            assertEquals(7, bar.getChildren().size());
            assertTrue(bar.getChildren().stream().map(Button.class::cast).allMatch(Button::isDisabled));
        });
    }

    private static void runOnJavaFx(Runnable assertions) throws Exception {
        FutureTask<Void> task = new FutureTask<>(assertions, null);
        Platform.runLater(task);
        task.get(10, TimeUnit.SECONDS);
    }
}
