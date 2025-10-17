package com.ivanka.audioeditor.client;

import com.ivanka.audioeditor.client.ui.EditorView;
import com.ivanka.audioeditor.client.ui.LoginView;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class MainApp extends Application {
    @Override public void start(Stage stage) {
        stage.setTitle("Audio Editor — Desktop");
        LoginView login = new LoginView(stage);
        stage.setScene(new Scene(login.getRoot(), 960, 640));
        stage.show();
    }
    public static void main(String[] args) { launch(args); }
}
