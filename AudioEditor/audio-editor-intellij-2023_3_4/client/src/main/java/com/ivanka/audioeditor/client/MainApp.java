package com.ivanka.audioeditor.client;

import com.ivanka.audioeditor.client.ui.LoginView;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.scene.image.Image;

import java.util.Objects;

public class MainApp extends Application {
    @Override public void start(Stage stage) {
        stage.setTitle("Audio Editor — Desktop");
        LoginView login = new LoginView(stage);
        stage.getIcons().add(new Image(
                Objects.requireNonNull(
                        getClass().getResourceAsStream("/icons/app-icon.png")
                )
        ));
        stage.setScene(new Scene(login.getRoot(), 960, 640));
        stage.show();
    }
    public static void main(String[] args) { launch(args); }
}