package com.rmi.client;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

public final class MainApplication extends Application {
    @Override
    public void start(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader(MainApplication.class.getResource("/view/main-view.fxml"));
        Scene scene = new Scene(loader.load(), 1180, 790);
        scene.getStylesheets().add(
                MainApplication.class.getResource("/css/application.css").toExternalForm());
        stage.setTitle("RMI Student Synchronization");
        stage.setMinWidth(1020);
        stage.setMinHeight(700);
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
