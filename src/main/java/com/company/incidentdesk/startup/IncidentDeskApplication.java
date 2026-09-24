package com.company.incidentdesk.startup;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import com.company.incidentdesk.ui.navigation.ApplicationNavigator;
import com.company.incidentdesk.ui.navigation.DefaultViewFactory;
import com.company.incidentdesk.ui.shared.theme.ApplicationTheme;

/** JavaFX application for Incident Desk. */
public final class IncidentDeskApplication extends Application {
    private static final double INITIAL_WIDTH = 720;
    private static final double INITIAL_HEIGHT = 480;

    private Scene scene;
    private ApplicationContext applicationContext;
    private ApplicationNavigator navigator;

    @Override
    public void start(Stage primaryStage) {
        applicationContext = ApplicationContext.openDefault();
        scene = new Scene(new StackPane(), INITIAL_WIDTH, INITIAL_HEIGHT);
        ApplicationTheme.applyTo(scene);
        navigator = new ApplicationNavigator(
                scene,
                applicationContext.sessions(),
                applicationContext.notifications(),
                new DefaultViewFactory(
                        applicationContext.incidents(),
                        applicationContext.presentationMapper(),
                        applicationContext.sessions()),
                applicationContext.registrations());
        navigator.start();
        primaryStage.setTitle("Incident Desk");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    @Override
    public void stop() {
        if (navigator != null) {
            navigator.close();
        }
        if (applicationContext != null) {
            applicationContext.close();
        }
    }

}
