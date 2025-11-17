package application;

import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.collections.FXCollections;

import java.util.List;

import databasePart1.DiscussionBoardDAO;
import javafx.geometry.Insets;
import javafx.geometry.Pos;

/**
 * AdminPage class represents the user interface for the admin user.
 * This page displays a simple welcome message for the admin.
 */

public class AdminHomePage {
    private Stage stage;
    private String userName;

    //constructor
    public AdminHomePage(Stage stage, String userName) {
        this.stage = stage;
        this.userName = userName;
    }

    //create the scene
    public Scene createScene() {
        VBox layout = new VBox(20);
        layout.setAlignment(Pos.CENTER);
        layout.setPadding(new Insets(20));

        //label to display the welcome message for the admin
        Label adminLabel = new Label("Hello, Admin " + userName + "!");
        adminLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        //discussion board button
        Button discussionBoardBtn = new Button("Discussion Board");
        discussionBoardBtn.setPrefWidth(200);
        discussionBoardBtn.setOnAction(e -> {
            DisussionBoardPage dbPage = new DisussionBoardPage(stage, userName, "Admin");
            stage.setScene(dbPage.createScene());
        });

        // Logout button to return to login screen
        Button logoutBtn = new Button("Logout");
        logoutBtn.setPrefWidth(200);
        logoutBtn.setOnAction(e -> {
            try {
                databasePart1.DatabaseHelper dbh = new databasePart1.DatabaseHelper();
                dbh.connectToDatabase();
                new UserLoginPage(dbh).show(stage);
            } catch (Exception ex) {
                // show simple alert via console fallback
                System.err.println("Failed to return to login: " + ex.getMessage());
            }
        });

        layout.getChildren().addAll(adminLabel, discussionBoardBtn, logoutBtn);
        return new Scene(layout, 800, 400);
    }

	/**
     * Displays the admin page in the provided primary stage.
     * @param primaryStage The primary stage where the scene will be displayed.
     */
    public void show(Stage primaryStage) {
	    // Set the scene to primary stage
	    primaryStage.setScene(createScene());
	    primaryStage.setTitle("Admin Page");
    }

    // Admin dashboard dialog for flagged items with reply/chat capability
    private void openAdminDashboard() {
        DiscussionBoardDAO dao;
        try {
            dao = new DiscussionBoardDAO();
        } catch (Exception e) { return; }

        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Admin Dashboard");
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        VBox box = new VBox(8);
        box.setPadding(new Insets(10));

        // list flagged items
        ListView<String> flaggedList = new ListView<>();
        flaggedList.setItems(FXCollections.observableArrayList(dao.getFlaggedItems().keySet()));

        TextArea chatArea = new TextArea();
        chatArea.setEditable(false);
        chatArea.setPrefHeight(200);

        TextField replyField = new TextField();
        replyField.setPromptText("Reply to selected flagged item");
        Button replyBtn = new Button("Reply");
        replyBtn.setOnAction(a -> {
            String sel = flaggedList.getSelectionModel().getSelectedItem();
            if (sel == null) return;
                try {
                    try {
                        // store admin reply as a flagged-item note so each flagged item has its own chat history
                        dao.addFlagNote(sel, replyField.getText(), userName);
                        if (sel.startsWith("Q:")) {
                            int qid = Integer.parseInt(sel.substring(2));
                            chatArea.appendText(userName + "->Q:" + qid + ": " + replyField.getText() + "\n");
                        } else if (sel.startsWith("A:")) {
                            int aid = Integer.parseInt(sel.substring(2));
                            chatArea.appendText(userName + "->A:" + aid + ": " + replyField.getText() + "\n");
                        } else if (sel.startsWith("FB:")) {
                            chatArea.appendText(userName + "->FB:" + sel + ": " + replyField.getText() + "\n");
                        }
                    } catch (Exception ex) { /* ignore */ }
                replyField.clear();
                } catch (Exception ex) { /* ignore */ }
        });

            // show notes for selected flagged item
            flaggedList.getSelectionModel().selectedItemProperty().addListener((obs, oldSel, newSel) -> {
                chatArea.clear();
                if (newSel == null) return;
                List<String> notes = dao.getFlaggedItems().getOrDefault(newSel, java.util.Collections.emptyList());
                if (!notes.isEmpty()) {
                    chatArea.appendText("Notes:\n");
                    notes.forEach(n -> chatArea.appendText(" - " + n + "\n"));
                }
            });

        Button unflagBtn = new Button("Unflag Selected");
        unflagBtn.setOnAction(a -> {
            String sel = flaggedList.getSelectionModel().getSelectedItem();
            if (sel == null) return;
            dao.unflagItem(sel, userName);
            flaggedList.setItems(FXCollections.observableArrayList(dao.getFlaggedItems().keySet()));
        });

        HBox controls = new HBox(6, replyField, replyBtn, unflagBtn);
        box.getChildren().addAll(new Label("Flagged Items:"), flaggedList, new Label("Chat:"), chatArea, controls);
        dialog.getDialogPane().setContent(box);
        dialog.showAndWait();
        dao.closeConnection();
    }
}