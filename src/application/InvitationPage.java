package application;


import databasePart1.*;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
// Intentionally avoid importing Stage in the method signature to reduce
// coupling with the caller's Stage type. The page will create and show
// its own Stage internally using the fully-qualified type where needed.

/**
 * InvitePage class represents the page where an admin can generate an invitation code.
 * The invitation code is displayed upon clicking a button.
 */

public class InvitationPage {

	/**
     * Displays the Invite Page in the provided primary stage.
     *
     * @param databaseHelper An instance of DatabaseHelper to handle database operations.
     * @param primaryStage   The primary stage where the scene will be displayed.
     */
	public void show(DatabaseHelper databaseHelper) {
		VBox layout = new VBox();
		layout.setStyle("-fx-alignment: center; -fx-padding: 20;");

	    // Label to display the title of the page
	    Label userLabel = new Label("Invite ");
	    userLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

	    // Button to generate the invitation code
	    Button showCodeButton = new Button("Generate Invitation Code");

	    // Label to display the generated invitation code
	    Label inviteCodeLabel = new Label(""); ;
        inviteCodeLabel.setStyle("-fx-font-size: 14px; -fx-font-style: italic;");

        showCodeButton.setOnAction(a -> {
        	// Generate the invitation code using the databaseHelper and set it to the label
            String invitationCode = databaseHelper.generateInvitationCode();
            inviteCodeLabel.setText(invitationCode);
        });


		layout.getChildren().addAll(userLabel, showCodeButton, inviteCodeLabel);
		Scene inviteScene = new Scene(layout, 800, 400);

		// Create and show our own stage rather than depending on an external Stage
		javafx.stage.Stage inviteStage = new javafx.stage.Stage();
		inviteStage.setScene(inviteScene);
		inviteStage.setTitle("Invite Page");
		inviteStage.show();

    }
}