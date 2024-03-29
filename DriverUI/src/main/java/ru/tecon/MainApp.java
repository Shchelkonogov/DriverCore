package ru.tecon;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import ru.tecon.controller.LogStage;
import ru.tecon.controller.RootLayoutController;
import ru.tecon.mfk1500Server.MFK1500Server;

import java.io.IOException;
import java.util.logging.LogManager;

public class MainApp extends Application {

    public static void main(String[] args) throws IOException {
        LogManager.getLogManager().readConfiguration(MainApp.class.getResourceAsStream("/logging.properties"));

        launch(args);
    }

    @Override
    public void start(Stage primaryStage) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fx/RootLayout.fxml"));
        Parent root = loader.load();

        RootLayoutController controller = loader.getController();
        controller.setPrimaryStage(primaryStage);

        primaryStage.setTitle("Сервер MFK1500");
        primaryStage.setScene(new Scene(root, 1300, 600));

        primaryStage.setWidth(1300);
        primaryStage.setHeight(600);

        primaryStage.setMinWidth(1300);
        primaryStage.setMinHeight(600);

        primaryStage.show();

        LogStage.setOwner(primaryStage);
    }

    @Override
    public void stop() throws Exception {
        MFK1500Server.stopServer();
        super.stop();
    }
}
