package incidentApp;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.stage.Stage;

public final class Main extends Application {

    @Override
    public void start(Stage stage) {
        stage.setTitle("Incident Reporting App");
        stage.setScene(new Scene(new Label("Incident Reporting App"), 600, 400));
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
