package application;

import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import databasePart1.DiscussionBoardDAO;
import java.sql.SQLException;
import java.util.List;

//UI for the discussion board
public class DisussionBoardPage {
    private Stage stage;
    private String currentUserName;
    private String currentUserRole;
    private DiscussionBoardDAO dao;

    //UI components
    private ListView<Question> questionListView;
    private TextArea questionDetailArea;
    private ListView<Answer> answerListView;
    private TextField searchField;
    private ComboBox<String> filterComboBox;

    //currently selected question
    private Question selectedQuestion;

    public DisussionBoardPage(Stage stage, String currentUserName, String currentUserRole) {
        this.stage = stage;
        this.currentUserName = currentUserName;
        this.currentUserRole = currentUserRole;

        try {
            this.dao = new DiscussionBoardDAO();
        } catch (SQLException e) {
            showError("Failed to connect to the database");
        }
    }

    //create the scene for UI
    public Scene createScene() {
        BorderPane mainLayout = new BorderPane();
        mainLayout.setPadding(new Insets(10));

    // Questions column (left)
    VBox questionsBox = new VBox(10);
    questionsBox.setPadding(new Insets(10));
    Label questionLabel = new Label("Questions");
    questionLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

    questionListView = new ListView<>();
    questionListView.setPrefHeight(600);

        //cell factory for question list
        questionListView.setCellFactory(lv -> new ListCell<Question>() {
            @Override
            protected void updateItem(Question question, boolean empty) {
                super.updateItem(question, empty);
                if (empty || question == null) {
                    setText(null);
                } else{
                    String status = question.getIsAnswered() ? "[✓]" : "[?]";
                    boolean qFlagged = dao.isItemFlagged("Q:" + question.getQuestionId());
                    String flagged = qFlagged ? "[FLAGGED] " : "";
                    // hide for regular users only if the question itself is flagged
                    if (!isElevatedRole() && qFlagged) {
                        setText(null);
                        setGraphic(null);
                        return;
                    }
                    setText(status + " " + flagged + question.getTitle()+ " (" + question.getAuthorUserName() + ")");

                }
            }
        });

        questionListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> displayQuestionDetail(newVal));

        // context menu for question-level flagging/unflagging (staff/admin only)
        ContextMenu qMenu = new ContextMenu();
        MenuItem flagQ = new MenuItem("Flag");
        MenuItem unflagQ = new MenuItem("Unflag");
        flagQ.setOnAction(e -> {
            Question q = questionListView.getSelectionModel().getSelectedItem();
            if (q == null) { showError("Select a question to flag"); return; }
            TextInputDialog td = new TextInputDialog();
            td.setTitle("Flag Question");
            td.setHeaderText("Add note for flag Q:" + q.getQuestionId());
            td.showAndWait().ifPresent(note -> { dao.flagQuestion(q.getQuestionId(), note, currentUserName); showInfo("Question flagged"); refreshData(); });
        });
        unflagQ.setOnAction(e -> {
            Question q = questionListView.getSelectionModel().getSelectedItem();
            if (q == null) { showError("Select a question to unflag"); return; }
            dao.unflagItem("Q:" + q.getQuestionId(), currentUserName); showInfo("Question unflagged"); refreshData(); questionListView.refresh(); answerListView.refresh();
        });
        qMenu.getItems().addAll(flagQ, unflagQ);
        questionListView.setOnContextMenuRequested(e -> {
            if (isElevatedRole()) qMenu.show(questionListView, e.getScreenX(), e.getScreenY());
        });

        loadQuestions();
        questionsBox.getChildren().addAll(questionLabel, questionListView);

        // assemble layout
        mainLayout.setLeft(questionsBox);
        mainLayout.setCenter(createDetailSection());
        mainLayout.setRight(createActionSection());

        return new Scene(mainLayout, 1200, 800);
    }
    //create center section with question detail and answer list
    private VBox createDetailSection() {
        VBox detailBox = new VBox(10);
        detailBox.setPadding(new Insets(10));

        //question detail
        Label detailLabel = new Label("Question Details");
        detailLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        questionDetailArea = new TextArea();
        questionDetailArea.setEditable(false);
        questionDetailArea.setPrefHeight(200);
        questionDetailArea.setWrapText(true);

        //answer list
        Label answerLabel = new Label("Answers");
        answerLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        answerListView = new ListView<>();
        answerListView.setPrefHeight(350);

        //cell factory for answer list
        answerListView.setCellFactory(lv -> new ListCell<Answer>() {
            @Override
            protected void updateItem(Answer answer, boolean empty) {
                super.updateItem(answer, empty);
                if (empty || answer == null) {
                    setText(null);
                } else {
                    String status = answer.getIsAccepted() ? "[ACCEPTED] " : "";
                    String flagged = dao.isItemFlagged("A:" + answer.getAnswerId()) ? "[FLAGGED] " : "";
                    if (!isElevatedRole() && dao.isItemFlagged("A:" + answer.getAnswerId())) {
                        setText(null);
                        setGraphic(null);
                        return;
                    }
                    setText(status + flagged + answer.getContent() + "\n - " + answer.getAuthorUserName() + " (" + answer.getCreatedAt().toLocalDate() + ")");
                }
            }
        });

        // context menu for answer-level flagging/unflagging (staff/admin only)
        ContextMenu aMenu = new ContextMenu();
        MenuItem flagA = new MenuItem("Flag");
        MenuItem unflagA = new MenuItem("Unflag");
        flagA.setOnAction(e -> {
            Answer a = answerListView.getSelectionModel().getSelectedItem();
            if (a == null) { showError("Select an answer to flag"); return; }
            TextInputDialog td = new TextInputDialog();
            td.setTitle("Flag Answer");
            td.setHeaderText("Add note for flag A:" + a.getAnswerId());
            td.showAndWait().ifPresent(note -> { dao.flagAnswer(a.getAnswerId(), note, currentUserName); showInfo("Answer flagged"); displayQuestionDetail(selectedQuestion); });
        });
        unflagA.setOnAction(e -> {
            Answer a = answerListView.getSelectionModel().getSelectedItem();
            if (a == null) { showError("Select an answer to unflag"); return; }
            dao.unflagItem("A:" + a.getAnswerId(), currentUserName); showInfo("Answer unflagged"); displayQuestionDetail(selectedQuestion); questionListView.refresh(); answerListView.refresh();
        });
        aMenu.getItems().addAll(flagA, unflagA);
        answerListView.setOnContextMenuRequested(e -> {
            if (isElevatedRole()) aMenu.show(answerListView, e.getScreenX(), e.getScreenY());
        });

        // Reviews box (below answers) - shows admin-added reviews per answer. Similar style to answers list.
        Label reviewsLabel = new Label("Reviews");
        reviewsLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");
        ListView<String> reviewsList = new ListView<>();
        reviewsList.setPrefHeight(200);
        reviewsList.setCellFactory(lv -> new ListCell<String>() {
            @Override
            protected void updateItem(String review, boolean empty) {
                super.updateItem(review, empty);
                if (empty || review == null) { setText(null); setGraphic(null); return; }
                Answer selA = answerListView.getSelectionModel().getSelectedItem();
                int idx = getIndex();
                if (selA != null) {
                    String itemId = "FB:A:" + selA.getAnswerId() + ":" + idx;
                    boolean flagged = dao.isItemFlagged(itemId);
                    if (!isElevatedRole() && flagged) { setText(null); setGraphic(null); return; }
                    setText((flagged ? "[FLAGGED] " : "") + review);
                } else {
                    setText(review);
                }
            }
        });

        // context menu for reviews (staff can flag/unflag)
        ContextMenu rMenu = new ContextMenu();
        MenuItem flagR = new MenuItem("Flag");
        MenuItem unflagR = new MenuItem("Unflag");
        flagR.setOnAction(e -> {
            Answer selA = answerListView.getSelectionModel().getSelectedItem();
            int idx = reviewsList.getSelectionModel().getSelectedIndex();
            if (selA == null || idx < 0) { showError("Select an answer and a review to flag"); return; }
            TextInputDialog td = new TextInputDialog(); td.setTitle("Flag Review"); td.setHeaderText("Add note for flag FB:A:" + selA.getAnswerId() + ":" + idx);
            td.showAndWait().ifPresent(note -> { dao.flagAnswerFeedback(selA.getAnswerId(), idx, note, currentUserName); reviewsList.setItems(FXCollections.observableArrayList(dao.getPrivateFeedbackForAnswer(selA.getAnswerId()))); showInfo("Review flagged"); });
        });
        unflagR.setOnAction(e -> {
            Answer selA = answerListView.getSelectionModel().getSelectedItem();
            int idx = reviewsList.getSelectionModel().getSelectedIndex();
            if (selA == null || idx < 0) { showError("Select an answer and a review to unflag"); return; }
            dao.unflagItem("FB:A:" + selA.getAnswerId() + ":" + idx, currentUserName);
            reviewsList.setItems(FXCollections.observableArrayList(dao.getPrivateFeedbackForAnswer(selA.getAnswerId())));
            showInfo("Review unflagged");
        });
        rMenu.getItems().addAll(flagR, unflagR);
        reviewsList.setOnContextMenuRequested(e -> { if (isElevatedRole()) rMenu.show(reviewsList, e.getScreenX(), e.getScreenY()); });

        reviewsList.setOnMouseClicked(e -> {
            if (e.getClickCount() != 2) return;
            Answer selA = answerListView.getSelectionModel().getSelectedItem();
            int idx = reviewsList.getSelectionModel().getSelectedIndex();
            if (selA == null || idx < 0) { showError("Select an answer and a review first"); return; }
            String itemId = "FB:A:" + selA.getAnswerId() + ":" + idx;
            try {
                boolean flagged = dao.isItemFlagged(itemId);
                if (flagged && "admin".equalsIgnoreCase(currentUserRole)) {
                    dao.unflagItem(itemId, currentUserName);
                    reviewsList.setItems(FXCollections.observableArrayList(dao.getPrivateFeedbackForAnswer(selA.getAnswerId())));
                    showInfo("Review unflagged");
                    questionListView.refresh();
                    answerListView.refresh();
                    return;
                }
                if (flagged && !"admin".equalsIgnoreCase(currentUserRole)) {
                    showInfo("This review is already flagged");
                    return;
                }
                if (!isElevatedRole()) { showError("Only staff or admin can flag reviews"); return; }
                TextInputDialog td = new TextInputDialog();
                td.setTitle("Flag Review");
                td.setHeaderText("Add note for flag " + itemId);
                td.showAndWait().ifPresent(note -> {
                    dao.flagAnswerFeedback(selA.getAnswerId(), idx, note, currentUserName);
                    reviewsList.setItems(FXCollections.observableArrayList(dao.getPrivateFeedbackForAnswer(selA.getAnswerId())));
                    showInfo("Review flagged");
                    questionListView.refresh();
                    answerListView.refresh();
                });
            } catch (Exception ex) { showError("Failed to flag/unflag review: " + ex.getMessage()); }
        });

        // update reviews when answer selection changes
        answerListView.getSelectionModel().selectedItemProperty().addListener((obs, oldA, newA) -> {
            if (newA == null) { reviewsList.setItems(FXCollections.observableArrayList()); return; }
            try { reviewsList.setItems(FXCollections.observableArrayList(dao.getPrivateFeedbackForAnswer(newA.getAnswerId()))); } catch (Exception ex) { reviewsList.setItems(FXCollections.observableArrayList()); }
        });

        // Add review controls (admins only)
        TextField reviewField = new TextField(); reviewField.setPromptText("Add review for selected answer");
        Button addReviewBtn = new Button("Add Review");
        addReviewBtn.setOnAction(e -> {
            Answer selA = answerListView.getSelectionModel().getSelectedItem();
            if (selA == null) { showError("Select an answer to review"); return; }
            try { dao.addPrivateFeedbackForAnswer(selA.getAnswerId(), reviewField.getText(), currentUserName); reviewsList.setItems(FXCollections.observableArrayList(dao.getPrivateFeedbackForAnswer(selA.getAnswerId()))); reviewField.clear(); }
            catch (Exception ex) { showError("Failed to add review: " + ex.getMessage()); }
        });

        HBox reviewControls = new HBox(6);
        reviewControls.getChildren().addAll(reviewField, addReviewBtn);
        if (!"admin".equalsIgnoreCase(currentUserRole)) reviewControls.setVisible(false);

        detailBox.getChildren().addAll(detailLabel, questionDetailArea, answerLabel, answerListView, reviewsLabel, reviewsList, reviewControls);
        return detailBox;
}
//create right section with action buttons
    private VBox createActionSection() {
        VBox actionBox = new VBox(10);
        actionBox.setPadding(new Insets(10));
        actionBox.setAlignment(Pos.CENTER);
        actionBox.setPrefWidth(200);

        //add question button
        Button createQuestionBtn = new Button("Create Question");
        createQuestionBtn.setPrefWidth(180);
        createQuestionBtn.setOnAction(e -> createQuestion());
        //edit question
        Button editQuestionBtn = new Button("Edit Question");
        editQuestionBtn.setPrefWidth(180);
        editQuestionBtn.setOnAction(e -> editQuestion());
        //delete question
        Button deleteQuestionBtn = new Button("Delete Question");
        deleteQuestionBtn.setPrefWidth(180);
        deleteQuestionBtn.setOnAction(e -> deleteQuestion());
        //add answer
        Button addAnswerBtn = new Button("Add Answer");
        addAnswerBtn.setPrefWidth(180);
        addAnswerBtn.setOnAction(e -> addAnswer());
        //edit answer
        Button editAnswerBtn = new Button("Edit Answer");
        editAnswerBtn.setPrefWidth(180);
        editAnswerBtn.setOnAction(e -> editAnswer());
        //delete answer
        Button deleteAnswerBtn = new Button("Delete Answer");
        deleteAnswerBtn.setPrefWidth(180);
        deleteAnswerBtn.setOnAction(e -> deleteAnswer());
        //refresh button
        Button refreshBtn = new Button("Refresh");
        refreshBtn.setPrefWidth(180);
        refreshBtn.setOnAction(e -> refreshData());
        //back button
        Button backBtn = new Button("Back");
        backBtn.setPrefWidth(180);
        backBtn.setOnAction(e -> goBack());
        // staff dashboard button (visible only for staff role)
        if ("staff".equalsIgnoreCase(currentUserRole)) {
            Button staffDashboardBtn = new Button("Staff Dashboard");
            staffDashboardBtn.setPrefWidth(180);
            staffDashboardBtn.setOnAction(e -> openStaffDashboard());
            actionBox.getChildren().add(staffDashboardBtn);
            actionBox.getChildren().add(new Separator());

            // Flag selected button (staff only)
            Button flagSelectedBtn = new Button("Flag Selected");
            flagSelectedBtn.setPrefWidth(180);
            flagSelectedBtn.setOnAction(e -> {
                // Prefer answer selection over question
                Answer selA = answerListView.getSelectionModel().getSelectedItem();
                try {
                    if (selA != null) {
                        dao.flagAnswer(selA.getAnswerId(), "Flagged by staff", currentUserName);
                        showInfo("Answer flagged for review");
                        refreshData();
                        questionListView.refresh();
                        answerListView.refresh();
                        return;
                    }
                    if (selectedQuestion != null) {
                        dao.flagQuestion(selectedQuestion.getQuestionId(), "Flagged by staff", currentUserName);
                        showInfo("Question flagged for review");
                        refreshData();
                        questionListView.refresh();
                        answerListView.refresh();
                        return;
                    }
                    showError("Select a question or an answer to flag");
                } catch (Exception ex) { showError("Failed to flag: " + ex.getMessage()); }
            });
            actionBox.getChildren().add(flagSelectedBtn);

            // Unflag selected (staff/admin) - visible to elevated roles; we add but staff can also unflag
            Button unflagSelectedBtn = new Button("Unflag Selected");
            unflagSelectedBtn.setPrefWidth(180);
            unflagSelectedBtn.setOnAction(e -> {
                try {
                    if (answerListView.getSelectionModel().getSelectedItem() != null) {
                        Answer a = answerListView.getSelectionModel().getSelectedItem();
                        dao.unflagItem("A:" + a.getAnswerId(), currentUserName);
                        showInfo("Answer unflagged");
                        refreshData();
                        questionListView.refresh();
                        answerListView.refresh();
                        return;
                    }
                    if (selectedQuestion != null) {
                        dao.unflagItem("Q:" + selectedQuestion.getQuestionId(), currentUserName);
                        showInfo("Question unflagged");
                        refreshData();
                        questionListView.refresh();
                        answerListView.refresh();
                        return;
                    }
                    showError("Select a flagged question or answer to unflag");
                } catch (Exception ex) { showError("Failed to unflag: " + ex.getMessage()); }
            });
            actionBox.getChildren().add(unflagSelectedBtn);
            actionBox.getChildren().add(new Separator());
        }
        // Admin dashboard button (placed on the main discussion board, only for Admin)
        if ("admin".equalsIgnoreCase(currentUserRole)) {
            Button adminDashboardBtn = new Button("Admin Dashboard");
            adminDashboardBtn.setPrefWidth(180);
            adminDashboardBtn.setOnAction(e -> openAdminDashboard());
            actionBox.getChildren().add(adminDashboardBtn);
            actionBox.getChildren().add(new Separator());
        }

        actionBox.getChildren().addAll(
            createQuestionBtn, editQuestionBtn, deleteQuestionBtn,
            new Separator(),
            addAnswerBtn, editAnswerBtn, deleteAnswerBtn,
            new Separator(),
            refreshBtn, backBtn
        );
        return actionBox;
    }

    //crud operations.

      //create a question
      private void createQuestion() {
        //dialog for creating a question
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Create Question");
        dialog.setHeaderText("Enter the details of the question");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(10));

        TextField titleField = new TextField();
        titleField.setPromptText("Enter the title of the question");
        TextArea contentField = new TextArea();
        contentField.setPromptText("Enter the content of the question");
        contentField.setPrefRowCount(5);
        contentField.setWrapText(true);

        TextField categoryField = new TextField();
        categoryField.setPromptText("Enter the category of the question (optional)");

        grid.add(new Label("Title:"), 0, 0);
        grid.add(titleField, 1, 0);
        grid.add(new Label("Content:"), 0, 1);
        grid.add(contentField, 1, 1);
        grid.add(new Label("Category:"), 0, 2);
        grid.add(categoryField, 1, 2);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                String title = titleField.getText();
                String content = contentField.getText();
                String category = categoryField.getText();
                //validate the question
                String error = DiscussionBoardValidator.validateQuestion(title, content, category);
                if (error != null) {
                    showError(error);
                    return;
                }
                //create the question
                Question newQuestion = new Question(title.trim(), content.trim(), currentUserName);
                if (category != null && !category.trim().isEmpty()) {
                    newQuestion.setCategory(category.trim());
                }

                try {
                    dao.createQuestion(newQuestion);
                    showInfo("Question created successfully!");
                    refreshData();
                } catch (SQLException e) {
                    showError("Failed to create question: " + e.getMessage());
                }
            }
        });
    }

    //update a question
    private void editQuestion() {
        //for selecting and validating
        if (selectedQuestion == null) {
            showError("Please select a question to edit");
            return;
        }
        //check permissions (only admin or author can edit)
        if(!selectedQuestion.getAuthorUserName().equals(currentUserName) && !isElevatedRole()) {
            showError("You are not authorized to edit this question");
            return;
        }
        //dialog for editing a question
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Edit Question");
        dialog.setHeaderText("Modify the question details");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(10));
        TextField titleField = new TextField(selectedQuestion.getTitle());
        TextArea contentField = new TextArea(selectedQuestion.getContent());
        contentField.setPrefRowCount(5);
        contentField.setWrapText(true);
        TextField categoryField = new TextField(selectedQuestion.getCategory() != null ? selectedQuestion.getCategory() : "");

        grid.add(new Label("Title:"), 0, 0);
        grid.add(titleField, 1, 0);
        grid.add(new Label("Content:"), 0, 1);
        grid.add(contentField, 1, 1);
        grid.add(new Label("Category:"), 0, 2);
        grid.add(categoryField, 1, 2);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                String title = titleField.getText();
                String content = contentField.getText();
                String category = categoryField.getText();
                //validate the question
                String error = DiscussionBoardValidator.validateQuestion(title, content, category);
                if (error != null) {
                    showError(error);
                    return;
                }
                //update the question
                selectedQuestion.setTitle(title.trim());
                selectedQuestion.setContent(content.trim());
                if (category != null && !category.trim().isEmpty()) {
                    selectedQuestion.setCategory(category.trim());
                }
                try {
                    dao.updateQuestion(selectedQuestion);
                    showInfo("Question updated successfully!");
                    refreshData();
                } catch (SQLException e) {
                    showError("Failed to update question: " + e.getMessage());
                }
            }
        });
}

    //delete a question
    private void deleteQuestion() {
        if (selectedQuestion == null) {
            showError("Please select a question to delete");
            return;
        }
        //check permissions (only admin or author can delete)
        if(!selectedQuestion.getAuthorUserName().equals(currentUserName) && !isElevatedRole()) {
            showError("You are not authorized to delete this question");
            return;
        }
        //dialog for deleting a question
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Delete Question");
        confirm.setHeaderText("Are you sure you want to delete this question?");
        confirm.setContentText("This action cannot be undone, this will delete all answers associated with this question.");

        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                try {
                    dao.deleteQuestion(selectedQuestion.getQuestionId());
                    showInfo("Question deleted successfully");
                    selectedQuestion = null;
                    refreshData();
                } catch (SQLException e) {
                    showError("Failed to delete question: " + e.getMessage());
                }
            }
        });
    }

    //add an answer
    private void addAnswer() {
        if (selectedQuestion == null) {
            showError("Please select a question to add an answer");
            return;
        }
        //dialog for adding an answer
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Add Answer");
        dialog.setHeaderText("Add answer to: " + selectedQuestion.getTitle());
        dialog.setContentText("Enter the content of the answer");

        dialog.showAndWait().ifPresent(response -> {
            String error = DiscussionBoardValidator.validateAnswer(response);
            if (error != null) {
                showError(error);
                return;
            }
            Answer newAnswer = new Answer(selectedQuestion.getQuestionId(), response.trim(), currentUserName);
            try {
                dao.createAnswer(newAnswer);
                showInfo("Answer added successfully!");
                displayQuestionDetail(selectedQuestion);
            } catch (SQLException e) {
                showError("Failed to add answer: " + e.getMessage());
            }
        });
    }
    //edit an answer
    private void editAnswer() {
        Answer selectedAnswer = answerListView.getSelectionModel().getSelectedItem();
        if (selectedAnswer == null) {
            showError("Please select an answer to edit");
            return;
        }
        //check permissions (only author or admin can edit)
        if(!selectedAnswer.getAuthorUserName().equals(currentUserName) && !isElevatedRole()) {
            showError("You are not authorized to edit this answer");
            return;
        }
        TextInputDialog dialog = new TextInputDialog(selectedAnswer.getContent());
        dialog.setTitle("Edit Answer");
        dialog.setContentText("Answer:");

        dialog.showAndWait().ifPresent(content -> {
            String error = DiscussionBoardValidator.validateAnswer(content);
            if (error != null) {
                showError(error);
                return;
            }
            selectedAnswer.setContent(content.trim());
            try {
                dao.updateAnswer(selectedAnswer);
                showInfo("Answer updated successfully!");
                displayQuestionDetail(selectedQuestion);
            } catch (SQLException e) {
                showError("Failed to update answer: " + e.getMessage());
            }
        });
    }
    //delete an answer
    private void deleteAnswer() {
        Answer selectedAnswer = answerListView.getSelectionModel().getSelectedItem();
        if (selectedAnswer == null) {
        showError("Please select an answer to delete");
            return;
        }
        //check permissions (only author or admin can delete)
        if(!selectedAnswer.getAuthorUserName().equals(currentUserName) && !isElevatedRole()) {
            showError("You are not authorized to delete this answer");
            return;
        }
        //dialog for deleting an answer
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Delete Answer");
        confirm.setHeaderText("Are you sure you want to delete this answer?");

        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                try {
                    dao.deleteAnswer(selectedAnswer.getAnswerId());
                    showInfo("Answer deleted successfully");
                    displayQuestionDetail(selectedQuestion);
                } catch (SQLException e) {
                    showError("Failed to delete answer: " + e.getMessage());
                }
            }
        });
    }

    //helper methods

    //load questions
    private void loadQuestions() {
        try {
            Questions questions = dao.getAllQuestions();
            // hide flagged items from regular users
            List<Question> qList = questions.getAllQuestions();
            if (!isElevatedRole()) {
                qList.removeIf(q -> dao.isItemFlagged("Q:" + q.getQuestionId()));
            }
            ObservableList<Question> questionList = FXCollections.observableArrayList(qList);
            questionListView.setItems(questionList);
        } catch (SQLException e) { showError("Failed to load questions: " + e.getMessage());}
    }
        //display question detail
        private void displayQuestionDetail(Question question) {
            selectedQuestion = question;
            if(question == null){
                questionDetailArea.clear();
                answerListView.setItems(FXCollections.observableArrayList());
                return;
            }
            String details = "Title: " + question.getTitle() + "\n\n" +
            "Author: " + question.getAuthorUserName() + "\n" +
            "Category: " + (question.getCategory() != null ? question.getCategory() : "N/A") + "\n" +
            "Created At: " + question.getCreatedAt().toLocalDate() + "\n" +
            "Status: " + (question.getIsAnswered() ? "Answered" : "Unanswered") + "\n\n" +
            "Content:\n" + question.getContent();
            // check if question is flagged; hide content from regular users only when the question itself is flagged
            boolean qFlagged = dao.isItemFlagged("Q:" + question.getQuestionId());
            if (qFlagged && !isElevatedRole()) {
                questionDetailArea.setText("This question has been flagged and is temporarily hidden.");
                answerListView.setItems(FXCollections.observableArrayList());
                return;
            }
            questionDetailArea.setText(details);

            //load answers
            try {
                Answers answers = dao.getAnswersForQuestion(question.getQuestionId());
                List<Answer> aList = answers.getAllAnswers();
                if (!isElevatedRole()) {
                    aList.removeIf(a -> dao.isItemFlagged("A:" + a.getAnswerId()));
                }
                ObservableList<Answer> answerList = FXCollections.observableArrayList(aList);
                answerListView.setItems(answerList);
            } catch (SQLException e) { showError("Failed to load answers: " + e.getMessage());}
        }
        //perofm search
        private void performSearch() {
            String keyword = searchField.getText();
            String error = DiscussionBoardValidator.validateSearchQuery(keyword);
            if (error != null) {
                showError(error);
                return;
            }
            try {
                Questions allQuestions = dao.getAllQuestions();
                Questions searchResults = allQuestions.search(keyword);
                    List<Question> results = searchResults.getAllQuestions();
                    if (!isElevatedRole()) results.removeIf(q -> dao.isItemFlagged("Q:" + q.getQuestionId()));
                    ObservableList<Question> resultList = FXCollections.observableArrayList(results);
                    questionListView.setItems(resultList);
            } catch (SQLException e) { showError("Failed to search questions: " + e.getMessage());}
        }
        //clear search
        private void clearSearch() {
            searchField.clear();
            filterComboBox.setValue("All");
            loadQuestions();
        }

        //filter questions
        private void applyFilter() {
            try {
                Questions allQuestions = dao.getAllQuestions();
                Questions filtered;
                String filter = filterComboBox.getValue();

                switch (filter) {
                    case "Answered":
                        filtered = allQuestions.filterByAnsweredStatus(true);
                        break;
                    case "Unanswered":
                        filtered = allQuestions.filterByAnsweredStatus(false);
                        break;
                    case "My Questions":
                        filtered = allQuestions.filterByAuthor(currentUserName);
                        break;
                    default:
                        filtered = allQuestions;
                        break;
                }
                ObservableList<Question> resultList = FXCollections.observableArrayList(filtered.getAllQuestions());
                List<Question> results = filtered.getAllQuestions();
                if (!isElevatedRole()) results.removeIf(q -> dao.isItemFlagged("Q:" + q.getQuestionId()));
                questionListView.setItems(FXCollections.observableArrayList(results));
            } catch (SQLException e) { showError("Failed to filter questions: " + e.getMessage());}
        }
        //refresh data
        private void refreshData() {
            loadQuestions();
            if(selectedQuestion != null) {
                try {
                    Question refreshed = dao.getQuestionById(selectedQuestion.getQuestionId());
                    displayQuestionDetail(refreshed);
                }catch (SQLException e) {displayQuestionDetail(null);}
            }
        }

    //navigate to home page for role
    private void goBack() {
        if("staff".equalsIgnoreCase(currentUserRole)) {
            StaffHomePage staffHomePage = new StaffHomePage(stage,currentUserName);
            stage.setScene(staffHomePage.createScene());
        } else if(isElevatedRole()) {
            AdminHomePage adminHomePage = new AdminHomePage(stage,currentUserName);
            stage.setScene(adminHomePage.createScene());
        } else {
            UserHomePage userHomePage = new UserHomePage(stage,currentUserName);
            stage.setScene(userHomePage.createScene());
        }
    }
    // treat staff as elevated/admin equivalent
    private boolean isElevatedRole() { return "Admin".equalsIgnoreCase(currentUserRole) || "staff".equalsIgnoreCase(currentUserRole); }
    //Show error and info messages
    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setContentText(message);
        alert.showAndWait();
    }
    private void showInfo(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Information");
        alert.setContentText(message);
        alert.showAndWait();
    }

    // Helper to format a flagged item id into a human-readable display string
    private String formatFlaggedItem(String itemId) {
        if (itemId == null) return "";
        try {
            if (itemId.startsWith("Q:")) {
                int qid = Integer.parseInt(itemId.substring(2));
                Question q = dao.getQuestionById(qid);
                if (q != null) return "Question " + qid + ": " + q.getTitle();
                return "Question " + qid;
            } else if (itemId.startsWith("A:")) {
                int aid = Integer.parseInt(itemId.substring(2));
                Answer a = dao.getAnswerById(aid);
                if (a != null) return "Answer " + aid + ": " + (a.getContent().length() > 80 ? a.getContent().substring(0, 77) + "..." : a.getContent());
                return "Answer " + aid;
            } else if (itemId.startsWith("FB:")) {
                String[] parts = itemId.split(":");
                // support FB:<qid>:<idx> and FB:A:<aid>:<idx>
                if (parts.length >= 3) {
                    if ("A".equals(parts[1]) && parts.length >= 4) {
                        int aid = Integer.parseInt(parts[2]);
                        int idx = Integer.parseInt(parts[3]);
                        List<String> fb = dao.getPrivateFeedbackForAnswer(aid);
                        if (idx >= 0 && idx < fb.size()) {
                            String entry = fb.get(idx);
                            return "Feedback A:" + aid + " -> " + (entry.length() > 80 ? entry.substring(0,77) + "..." : entry);
                        }
                        return "Feedback A:" + aid + " [" + idx + "]";
                    } else {
                        int qid = Integer.parseInt(parts[1]);
                        int idx = Integer.parseInt(parts[2]);
                        List<String> fb = dao.getPrivateFeedbackForQuestion(qid);
                        if (idx >= 0 && idx < fb.size()) {
                            String entry = fb.get(idx);
                            return "Feedback Q:" + qid + " -> " + (entry.length() > 80 ? entry.substring(0,77) + "..." : entry);
                        }
                        return "Feedback Q:" + qid + " [" + idx + "]";
                    }
                }
            }
        } catch (Exception ex) { /* ignore and fall through */ }
        return itemId;
    }

    // Staff Dashboard
    // Minimal in-place dashboard dialog to satisfy staff user stories without new scene file.
    private void openStaffDashboard() {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Staff Dashboard");
    dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
    // make the staff dashboard wider for better readability
    dialog.getDialogPane().setPrefSize(1000, 600);

    TabPane tabs = new TabPane();

        // Summary tab
        Tab summaryTab = new Tab("Summary");
        summaryTab.setClosable(false);
        VBox summaryBox = new VBox(8);
        summaryBox.setPadding(new Insets(10));
        // prepare a tasks label that can be updated live via task listener
        final Label tasksLabel = new Label();
        try {
            Questions allQ = dao.getAllQuestions();
            Answers allA = dao.getAllAnswers();
            long unanswered = allQ.getAllQuestions().stream().filter(q -> !q.getIsAnswered()).count();
            Label qLabel = new Label("Total Questions: " + allQ.size());
            Label unLabel = new Label("Unanswered: " + unanswered);
            Label aLabel = new Label("Total Answers: " + allA.size());
            Label flaggedLabel = new Label("Flagged Items: " + dao.getFlaggedItems().size());
            tasksLabel.setText("Unresolved Tasks: " + dao.getTasks().size());
            summaryBox.getChildren().addAll(qLabel, unLabel, aLabel, flaggedLabel, tasksLabel);
        } catch (Exception ex) {
            summaryBox.getChildren().add(new Label("Error loading summary."));
        }
        summaryTab.setContent(summaryBox);

        // Flagged tab
        Tab flaggedTab = new Tab("Flagged");
        flaggedTab.setClosable(false);
        VBox flaggedBox = new VBox(5);
        flaggedBox.setPadding(new Insets(10));
    // present flagged items in a ListView with display->id mapping; show notes/chat in a pane like admin view
    ListView<String> staffFlaggedList = new ListView<>();
    // wider list for staff clarity
    staffFlaggedList.setPrefWidth(900);
        java.util.Map<String, String> staffDisplayToId = new java.util.HashMap<>();
        java.util.List<String> staffDisplays = new java.util.ArrayList<>();
        // build display list (display-only, notes shown in chat area)
        dao.getFlaggedItems().forEach((id, notes) -> {
            String disp = formatFlaggedItem(id);
            if (staffDisplayToId.containsKey(disp)) disp = disp + " (" + id + ")";
            staffDisplayToId.put(disp, id);
            staffDisplays.add(disp);
        });
        staffFlaggedList.setItems(FXCollections.observableArrayList(staffDisplays));
        Button refreshFlagged = new Button("Refresh Flagged");
        refreshFlagged.setOnAction(e -> {
            staffDisplays.clear(); staffDisplayToId.clear();
            dao.getFlaggedItems().forEach((id, notes) -> {
                String disp = formatFlaggedItem(id);
                if (staffDisplayToId.containsKey(disp)) disp = disp + " (" + id + ")";
                staffDisplayToId.put(disp, id);
                staffDisplays.add(disp);
            });
            staffFlaggedList.setItems(FXCollections.observableArrayList(staffDisplays));
            // also refresh main lists
            loadQuestions();
            if (selectedQuestion != null) displayQuestionDetail(selectedQuestion);
        });

        // chat area to show notes for selected flagged item (staff view mirrors admin)
        TextArea staffChat = new TextArea();
        staffChat.setEditable(false);
        staffChat.setPrefHeight(200);

        staffFlaggedList.getSelectionModel().selectedItemProperty().addListener((obs, oldSel, newSel) -> {
            staffChat.clear();
            if (newSel == null) return;
            String itemId = staffDisplayToId.getOrDefault(newSel, newSel);
            List<String> notes = dao.getFlaggedItems().getOrDefault(itemId, java.util.Collections.emptyList());
            if (!notes.isEmpty()) {
                staffChat.appendText("Notes:\n");
                notes.forEach(n -> staffChat.appendText(" - " + n + "\n"));
            }
        });

        // Flag selected question convenience (staff can still flag a question)
        Button flagSelectedBtn = new Button("Flag Selected Question");
        flagSelectedBtn.setOnAction(e -> {
            if (selectedQuestion == null) { showError("Select a question first"); return; }
            TextInputDialog td = new TextInputDialog();
            td.setTitle("Flag Question");
            td.setHeaderText("Add note for flag Q:" + selectedQuestion.getQuestionId());
            td.showAndWait().ifPresent(note -> {
                dao.flagQuestion(selectedQuestion.getQuestionId(), note, currentUserName);
                refreshFlagged.fire();
            });
        });

        // reply/unflag controls (staff can add notes for admin and unflag items)
        TextField staffReplyField = new TextField();
        staffReplyField.setPromptText("Reply/add note to selected flagged item (visible to admins)");
        Button staffReplyBtn = new Button("Reply");
        staffReplyBtn.setOnAction(a -> {
            String sel = staffFlaggedList.getSelectionModel().getSelectedItem();
            if (sel == null) { showError("Select a flagged item first"); return; }
            String itemId = staffDisplayToId.getOrDefault(sel, sel);
            dao.addFlagNote(itemId, staffReplyField.getText(), currentUserName);
            staffReplyField.clear();
            // refresh chat and list
            refreshFlagged.fire();
        });

        Button staffUnflagBtn = new Button("Unflag Selected");
        staffUnflagBtn.setOnAction(a -> {
            String sel = staffFlaggedList.getSelectionModel().getSelectedItem();
            if (sel == null) { showError("Select a flagged item first"); return; }
            String itemId = staffDisplayToId.getOrDefault(sel, sel);
            dao.unflagItem(itemId, currentUserName);
            refreshFlagged.fire();
            questionListView.refresh();
            answerListView.refresh();
            showInfo("Item unflagged");
        });

        HBox staffReplyControls = new HBox(6, staffReplyField, staffReplyBtn, staffUnflagBtn);

        flaggedBox.getChildren().addAll(staffFlaggedList, refreshFlagged, new Separator(), staffChat, staffReplyControls, new Separator(), flagSelectedBtn);
        flaggedTab.setContent(new ScrollPane(flaggedBox));

        // Tasks tab
        Tab tasksTab = new Tab("Tasks");
        tasksTab.setClosable(false);
        VBox tasksBox = new VBox(5);
        tasksBox.setPadding(new Insets(10));
    ListView<String> taskList = new ListView<>();
    taskList.setPrefWidth(900);
    taskList.setItems(FXCollections.observableArrayList(dao.getTasks()));
        HBox taskControls = new HBox(5);
        TextField newTaskField = new TextField();
        newTaskField.setPromptText("New issue/task");
        Button addTaskBtn = new Button("Add");
        addTaskBtn.setOnAction(e -> {
            int id = dao.addTask(newTaskField.getText());
            if (id >= 0) {
                taskList.setItems(FXCollections.observableArrayList(dao.getTasks()));
                newTaskField.clear();
            }
        });
        Button resolveBtn = new Button("Resolve Selected");
        resolveBtn.setOnAction(e -> {
            int idx = taskList.getSelectionModel().getSelectedIndex();
            if (dao.resolveTask(idx)) {
                taskList.setItems(FXCollections.observableArrayList(dao.getTasks()));
            }
        });
        taskControls.getChildren().addAll(newTaskField, addTaskBtn, resolveBtn);
        tasksBox.getChildren().addAll(taskList, taskControls);
        tasksTab.setContent(tasksBox);
    // register a task listener so the tasks ListView updates automatically when tasks change
    java.util.function.Consumer<List<String>> staffTaskListener = tasks -> javafx.application.Platform.runLater(() -> {
        taskList.setItems(FXCollections.observableArrayList(tasks));
        tasksLabel.setText("Unresolved Tasks: " + (tasks == null ? 0 : tasks.size()));
    });
    dao.registerTaskListener(staffTaskListener);

        // Feedback tab
        Tab feedbackTab = new Tab("Feedback");
        feedbackTab.setClosable(false);
        VBox fbBox = new VBox(5);
        fbBox.setPadding(new Insets(10));
    ListView<String> fbList = new ListView<>();
    fbList.setPrefWidth(900);
        // initialize feedback list based on current selection (answer preferred)
        if (selectedQuestion != null) {
            if (answerListView.getSelectionModel().getSelectedItem() != null) {
                Answer selA = answerListView.getSelectionModel().getSelectedItem();
                fbList.setItems(FXCollections.observableArrayList(dao.getPrivateFeedbackForAnswer(selA.getAnswerId())));
            } else {
                fbList.setItems(FXCollections.observableArrayList(dao.getPrivateFeedbackForQuestion(selectedQuestion.getQuestionId())));
            }
        }
        TextField fbField = new TextField();
        fbField.setPromptText("Private feedback for selected question or answer");
        Button addFbBtn = new Button("Add Feedback");
        addFbBtn.setOnAction(e -> {
            if (selectedQuestion == null) { showError("Select a question first"); return; }
            Answer selA = answerListView.getSelectionModel().getSelectedItem();
            if (selA != null) {
                dao.addPrivateFeedbackForAnswer(selA.getAnswerId(), fbField.getText(), currentUserName);
                fbList.setItems(FXCollections.observableArrayList(dao.getPrivateFeedbackForAnswer(selA.getAnswerId())));
            } else {
                dao.addPrivateFeedback(selectedQuestion.getQuestionId(), fbField.getText(), currentUserName);
                fbList.setItems(FXCollections.observableArrayList(dao.getPrivateFeedbackForQuestion(selectedQuestion.getQuestionId())));
            }
            fbField.clear();
        });
        // Flag selected feedback (staff only)
        Button flagFbBtn = new Button("Flag Selected Feedback");
        flagFbBtn.setOnAction(e -> {
            if (selectedQuestion == null) { showError("Select a question first"); return; }
            int idx = fbList.getSelectionModel().getSelectedIndex();
            if (idx < 0) { showError("Select a feedback entry to flag"); return; }
            Answer selA = answerListView.getSelectionModel().getSelectedItem();
            if (selA != null) {
                dao.flagAnswerFeedback(selA.getAnswerId(), idx, "Flagged feedback by staff", currentUserName);
                fbList.setItems(FXCollections.observableArrayList(dao.getPrivateFeedbackForAnswer(selA.getAnswerId())));
            } else {
                dao.flagFeedback(selectedQuestion.getQuestionId(), idx, "Flagged feedback by staff", currentUserName);
                fbList.setItems(FXCollections.observableArrayList(dao.getPrivateFeedbackForQuestion(selectedQuestion.getQuestionId())));
            }
            showInfo("Feedback flagged for review");
            // ensure lists reflect flag state
            questionListView.refresh();
            answerListView.refresh();
        });
        fbBox.getChildren().addAll(fbList, fbField, addFbBtn);
        // only show flag feedback button for staff
        if ("staff".equalsIgnoreCase(currentUserRole)) fbBox.getChildren().add(flagFbBtn);
        feedbackTab.setContent(fbBox);

        // Logs tab
        Tab logsTab = new Tab("Logs");
        logsTab.setClosable(false);
    ListView<String> logView = new ListView<>();
    logView.setPrefWidth(900);
    logView.setItems(FXCollections.observableArrayList(dao.getLogs()));
    Button refreshLogsBtn = new Button("Refresh Logs");
    refreshLogsBtn.setOnAction(e -> logView.setItems(FXCollections.observableArrayList(dao.getLogs())));
        logsTab.setContent(logView);
    VBox logsBox = new VBox(5);
    logsBox.setPadding(new Insets(10));
    logsBox.getChildren().addAll(logView, refreshLogsBtn);
    logsTab.setContent(logsBox);

    tabs.getTabs().addAll(summaryTab, flaggedTab, tasksTab, logsTab);
    // give the tab pane more space inside the dialog
    tabs.setPrefSize(960, 540);
        dialog.getDialogPane().setContent(tabs);
        dialog.showAndWait();
        // unregister the listener when the dialog closes
        dao.unregisterTaskListener(staffTaskListener);
    }

    // Admin dashboard on main discussion board (uses same dao instance so flags are shared)
    private void openAdminDashboard() {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Admin Dashboard");
    dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
    // make the admin dashboard wider for better readability
    dialog.getDialogPane().setPrefSize(1000, 700);

    VBox box = new VBox(8);
    box.setPadding(new Insets(10));
    box.setPrefWidth(980);

    ListView<String> flaggedList = new ListView<>();
    flaggedList.setPrefWidth(960);
        // build display -> id map so list shows content instead of raw ids
        java.util.Map<String, String> displayToId = new java.util.HashMap<>();
        java.util.List<String> displays = new java.util.ArrayList<>();
        dao.getFlaggedItems().keySet().forEach(id -> {
            String disp = formatFlaggedItem(id);
            // if duplicate displays, append id to disambiguate
            if (displayToId.containsKey(disp)) disp = disp + " (" + id + ")";
            displayToId.put(disp, id);
            displays.add(disp);
        });
        flaggedList.setItems(FXCollections.observableArrayList(displays));

    TextArea chatArea = new TextArea();
    chatArea.setEditable(false);
    chatArea.setPrefHeight(300);
    chatArea.setPrefWidth(960);

        // when admin selects a flagged item, show notes and related private feedback
        flaggedList.getSelectionModel().selectedItemProperty().addListener((obs, oldSel, newSel) -> {
            chatArea.clear();
            if (newSel == null) return;
            String itemId = displayToId.getOrDefault(newSel, newSel);
            List<String> notes = dao.getFlaggedItems().getOrDefault(itemId, java.util.Collections.emptyList());
            if (!notes.isEmpty()) {
                chatArea.appendText("Notes:\n");
                notes.forEach(n -> chatArea.appendText(" - " + n + "\n"));
            }
            // do not display question-level private feedback here to avoid mixing replies across flagged items
            // flagged-item notes are shown above and admin replies are stored per-flagged-item via flagged_notes
        });

        // admin logs view (declared early so handlers can refresh it)
    // admin logs removed from admin dashboard (kept in DAO for auditing, but not shown in UI)

        TextField replyField = new TextField();
        replyField.setPromptText("Reply to selected flagged item");
        Button replyBtn = new Button("Reply");
        replyBtn.setOnAction(a -> {
            String selDisplay = flaggedList.getSelectionModel().getSelectedItem();
            if (selDisplay == null) return;
            String sel = displayToId.getOrDefault(selDisplay, selDisplay);
            try {
                // store admin replies as notes on the specific flagged item so each flagged item has its own chat/history
                dao.addFlagNote(sel, replyField.getText(), currentUserName);
                if (sel.startsWith("Q:")) {
                    int qid = Integer.parseInt(sel.substring(2));
                    chatArea.appendText(currentUserName + "->Q:" + qid + ": " + replyField.getText() + "\n");
                } else if (sel.startsWith("A:")) {
                    int aid = Integer.parseInt(sel.substring(2));
                    chatArea.appendText(currentUserName + "->A:" + aid + ": " + replyField.getText() + "\n");
                } else if (sel.startsWith("FB:")) {
                    chatArea.appendText(currentUserName + "->FB:" + sel + ": " + replyField.getText() + "\n");
                }
                replyField.clear();
                // refresh admin views (rebuild display map)
                displayToId.clear();
                java.util.List<String> displays2 = new java.util.ArrayList<>();
                dao.getFlaggedItems().keySet().forEach(id -> {
                    String d = formatFlaggedItem(id);
                    if (displayToId.containsKey(d)) d = d + " (" + id + ")";
                    displayToId.put(d, id);
                    displays2.add(d);
                });
                flaggedList.setItems(FXCollections.observableArrayList(displays2));
                questionListView.refresh();
                answerListView.refresh();
            } catch (Exception ex) { /* ignore */ }
        });

        Button unflagBtn = new Button("Unflag Selected");
        unflagBtn.setOnAction(a -> {
            String selDisplay = flaggedList.getSelectionModel().getSelectedItem();
            if (selDisplay == null) return;
            String sel = displayToId.getOrDefault(selDisplay, selDisplay);
            dao.unflagItem(sel, currentUserName);
            // rebuild displays
            displayToId.clear();
            java.util.List<String> displays3 = new java.util.ArrayList<>();
            dao.getFlaggedItems().keySet().forEach(id -> {
                String d = formatFlaggedItem(id);
                if (displayToId.containsKey(d)) d = d + " (" + id + ")";
                displayToId.put(d, id);
                displays3.add(d);
            });
            flaggedList.setItems(FXCollections.observableArrayList(displays3));
            questionListView.refresh();
            answerListView.refresh();
        });

        // Admin logs view and refresh
        Button refreshAdminBtn = new Button("Refresh");
        refreshAdminBtn.setOnAction(e -> {
            displayToId.clear();
            java.util.List<String> displays4 = new java.util.ArrayList<>();
            dao.getFlaggedItems().keySet().forEach(id -> {
                String d = formatFlaggedItem(id);
                if (displayToId.containsKey(d)) d = d + " (" + id + ")";
                displayToId.put(d, id);
                displays4.add(d);
            });
            flaggedList.setItems(FXCollections.observableArrayList(displays4));
        });

        // add a logout button that returns to the login page
        Button logoutBtn = new Button("Logout");
        logoutBtn.setOnAction(e -> {
            dialog.close();
            try {
                databasePart1.DatabaseHelper dbh = new databasePart1.DatabaseHelper();
                dbh.connectToDatabase();
                new UserLoginPage(dbh).show(stage);
            } catch (SQLException ex) {
                showError("Failed to open login page: " + ex.getMessage());
            }
        });

    HBox controls = new HBox(6, replyField, replyBtn, unflagBtn, refreshAdminBtn, logoutBtn);
    box.getChildren().addAll(new Label("Flagged Items:"), flaggedList, new Label("Chat:"), chatArea, controls);

        // ensure admin view is populated with the latest flagged items and logs before showing
        displayToId.clear();
        java.util.List<String> displaysFinal = new java.util.ArrayList<>();
        dao.getFlaggedItems().keySet().forEach(id -> {
            String d = formatFlaggedItem(id);
            if (displayToId.containsKey(d)) d = d + " (" + id + ")";
            displayToId.put(d, id);
            displaysFinal.add(d);
        });
        flaggedList.setItems(FXCollections.observableArrayList(displaysFinal));
    // admin logs not shown in dashboard UI
    // admin does not receive the staff task board in this dashboard (tasks are managed via Staff Dashboard)
        questionListView.refresh();
        answerListView.refresh();

        dialog.getDialogPane().setContent(box);
        dialog.showAndWait();
        // nothing to unregister here (no admin task listener)
    }
}