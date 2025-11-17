package application;

import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.geometry.Insets;
import javafx.geometry.Pos;

/**
 * StaffHomePage mirrors AdminHomePage but preserves the 'staff' role when
 * opening the discussion board so the staff dashboard features appear.
 */
public class StaffHomePage {
    private Stage stage;
    private String userName;

    public StaffHomePage(Stage stage, String userName) {
        this.stage = stage;
        this.userName = userName;
    }

    public Scene createScene() {
        VBox layout = new VBox(20);
        layout.setAlignment(Pos.CENTER);
        layout.setPadding(new Insets(20));

        Label staffLabel = new Label("Hello, Staff " + userName + "!");
        staffLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        Button discussionBoardBtn = new Button("Discussion Board");
        discussionBoardBtn.setPrefWidth(200);
        discussionBoardBtn.setOnAction(e -> {
            DisussionBoardPage dbPage = new DisussionBoardPage(stage, userName, "staff");
            stage.setScene(dbPage.createScene());
        });

        // Logout button
        Button logoutBtn = new Button("Logout");
        logoutBtn.setPrefWidth(200);
        logoutBtn.setOnAction(e -> {
            try {
                databasePart1.DatabaseHelper dbh = new databasePart1.DatabaseHelper();
                dbh.connectToDatabase();
                new UserLoginPage(dbh).show(stage);
            } catch (Exception ex) {
                System.err.println("Failed to return to login: " + ex.getMessage());
            }
        });

        layout.getChildren().addAll(staffLabel, discussionBoardBtn, logoutBtn);
        return new Scene(layout, 800, 400);
    }

    public void show(Stage primaryStage) {
        primaryStage.setScene(createScene());
        primaryStage.setTitle("Staff Page");
    }
}
