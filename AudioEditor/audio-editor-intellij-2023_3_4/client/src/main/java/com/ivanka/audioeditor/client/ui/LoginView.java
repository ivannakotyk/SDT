package com.ivanka.audioeditor.client.ui;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ivanka.audioeditor.client.net.ApiClient;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class LoginView {
    private final VBox root = new VBox(20);
    private Long userId;
    private final ApiClient api = ApiClient.getInstance();

    public LoginView(Stage stage) {
        root.setPadding(new Insets(48));
        root.setAlignment(Pos.CENTER);
        root.setStyle("""
            -fx-background-color: linear-gradient(to bottom right, #0f2027, #203a43, #2c5364);
            -fx-font-family: 'Segoe UI', sans-serif;
        """);

        // Заголовок
        Label title = new Label("🎧 Welcome to Audio Editor");
        title.setStyle("""
            -fx-text-fill: white;
            -fx-font-size: 26px;
            -fx-font-weight: bold;
        """);

        // Поля введення
        TextField name = new TextField();
        name.setPromptText("Enter your name");
        TextField email = new TextField();
        email.setPromptText("Enter your email");

        name.setMaxWidth(280);
        email.setMaxWidth(280);

        name.setStyle("-fx-background-radius: 10; -fx-padding: 8;");
        email.setStyle("-fx-background-radius: 10; -fx-padding: 8;");

        // Повідомлення про статус
        Label nameStatus = new Label();
        nameStatus.setTextFill(Color.LIGHTGRAY);
        Label emailStatus = new Label();
        emailStatus.setTextFill(Color.LIGHTGRAY);

        // Кнопка
        Button go = new Button("Continue ▶");
        go.setStyle("""
            -fx-background-color: #38bdf8;
            -fx-text-fill: white;
            -fx-font-weight: bold;
            -fx-background-radius: 20;
            -fx-padding: 10 22;
            -fx-cursor: hand;
        """);

        Label errorLabel = new Label();
        errorLabel.setTextFill(Color.web("#ff6b6b"));
        errorLabel.setStyle("-fx-font-size: 13px;");

        VBox form = new VBox(10, name, nameStatus, email, emailStatus, go, errorLabel);
        form.setAlignment(Pos.CENTER);

        root.getChildren().addAll(title, form);

        // Валідація під час введення
        name.textProperty().addListener((obs, oldV, newV) -> {
            if (isValidName(newV)) {
                nameStatus.setText("Valid name");
                nameStatus.setTextFill(Color.web("#22c55e"));
            } else {
                nameStatus.setText("Only letters and spaces allowed (min 2 chars)");
                nameStatus.setTextFill(Color.web("#f87171"));
            }
        });

        email.textProperty().addListener((obs, oldV, newV) -> {
            if (isValidEmail(newV)) {
                emailStatus.setText("Valid email");
                emailStatus.setTextFill(Color.web("#22c55e"));
            } else {
                emailStatus.setText("Invalid email format");
                emailStatus.setTextFill(Color.web("#f87171"));
            }
        });

        // --- Обробка кнопки ---
        go.setOnAction(e -> {
            String username = name.getText().trim();
            String userEmail = email.getText().trim();

            if (!isValidName(username)) {
                errorLabel.setText("Please enter a valid name.");
                return;
            }
            if (!isValidEmail(userEmail)) {
                errorLabel.setText("Please enter a valid email.");
                return;
            }

            try {
                String encodedName = URLEncoder.encode(username, StandardCharsets.UTF_8);
                String encodedEmail = URLEncoder.encode(userEmail, StandardCharsets.UTF_8);

                String res = api.postForm("/users/find-or-create",
                        "name=" + encodedName + "&email=" + encodedEmail);

                ObjectMapper mapper = new ObjectMapper();
                JsonNode node = mapper.readTree(res);
                userId = node.get("id").asLong();

                alertInfo("Profile loaded!\nName: " + username + "\nEmail: " + userEmail);
                EditorView editor = new EditorView(stage, userId);
                stage.setScene(new Scene(editor.getRoot(), 1200, 800));

            } catch (Exception ex) {
                ex.printStackTrace();
                errorLabel.setText("Failed to connect to server. Please try again.");
            }
        });
    }

    // ======= Перевірки =======

    private boolean isValidName(String name) {
        return name != null && name.matches("[A-Za-zА-Яа-яІіЇїЄє'\\s]{2,}");
    }

    private boolean isValidEmail(String email) {
        return email != null && email.matches("^[\\w._%+-]+@[\\w.-]+\\.[A-Za-z]{2,}$");
    }

    // ======= Спливаючі повідомлення =======
    private void alertInfo(String msg) {
        new Alert(Alert.AlertType.INFORMATION, msg).showAndWait();
    }

    // ======= Головний вузол =======
    public Parent getRoot() {
        return root;
    }
}
