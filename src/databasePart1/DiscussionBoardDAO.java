package databasePart1;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import application.Staff;
import application.Question;
import application.Answer;
import application.Questions;
import application.Answers;

//data access object for the discussion board
public class DiscussionBoardDAO {
    private Connection connection;
    private Statement statement;

    // In-memory structures for staff monitoring features (HW4 additions)
    // Make these static so all DAO instances share the same flagged state and logs.
    private static final List<Staff> staffList = new ArrayList<>(); // tracks staff instances (not persisted)
    private static final Map<String, List<String>> flaggedItems = new HashMap<>(); // itemId -> list of notes
    private static final List<String> logs = new ArrayList<>(); // historical logs of reviews/actions & communications
    private static final List<String> taskBoard = new ArrayList<>(); // unresolved issues/tasks (index-based id)
    private static final List<java.util.function.Consumer<List<String>>> taskListeners = new ArrayList<>();
    private static final Map<Integer, List<String>> privateFeedback = new HashMap<>(); // questionId -> feedback entries
    private static final Map<Integer, List<String>> privateAnswerFeedback = new HashMap<>(); // answerId -> feedback entries

    //db credentials (from DatabaseHelper)

    // JDBC driver name and database URL
	static final String JDBC_DRIVER = "org.h2.Driver";
	static final String DB_URL = "jdbc:h2:~/FoundationDatabase";

	//  Database credentials
	static final String USER = "sa";
	static final String PASS = "";

    //constructor
    public DiscussionBoardDAO() throws SQLException {
        connectToDatabase();
    }
    //connect to db
    private void connectToDatabase() throws SQLException {
        try {
            Class.forName(JDBC_DRIVER);
            connection = DriverManager.getConnection(DB_URL, USER, PASS);
            statement = connection.createStatement();
            createTables();
            // load persisted staff-related data into in-memory structures
            loadPersistentState();
        } catch (ClassNotFoundException e) {
            throw new SQLException("Failed to connect to the database", e);
        }
    }
    //create the tables
    private void createTables() throws SQLException {
        //questions table.
        String questionsTable = "CREATE TABLE IF NOT EXISTS questions(" +
        "questionId INT AUTO_INCREMENT PRIMARY KEY," +
        "title VARCHAR(255) NOT NULL," +
        "content TEXT NOT NULL," +
        "authorUserName VARCHAR(255) NOT NULL," +
        "createdAt TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
        "updatedAt TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
        "isAnswered BOOLEAN DEFAULT FALSE," +
        "category VARCHAR(100))";
        statement.execute(questionsTable);

    //answers table.
    String answersTable = "CREATE TABLE IF NOT EXISTS answers(" +
    "answerId INT AUTO_INCREMENT PRIMARY KEY," +
    "questionId INT NOT NULL," +
    "content TEXT NOT NULL," +
    "authorUserName VARCHAR(255) NOT NULL," +
    "createdAt TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
    "updatedAt TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
    "isAccepted BOOLEAN DEFAULT FALSE," +
    "FOREIGN KEY (questionId) REFERENCES questions(questionId))";

    statement.execute(answersTable);
    // flagged items + notes (persisted)
    String flaggedTable = "CREATE TABLE IF NOT EXISTS flagged_items(itemId VARCHAR(100) PRIMARY KEY)";
    String flaggedNotes = "CREATE TABLE IF NOT EXISTS flagged_notes(noteId INT AUTO_INCREMENT PRIMARY KEY, itemId VARCHAR(100), note TEXT, author VARCHAR(255))";
    statement.execute(flaggedTable);
    statement.execute(flaggedNotes);
    try { statement.execute("ALTER TABLE flagged_notes ADD COLUMN IF NOT EXISTS author VARCHAR(255)"); } catch (SQLException ex) { /* ignore */ }

    // admin logs (store optional author for each entry)
    String logsTable = "CREATE TABLE IF NOT EXISTS admin_logs(logId INT AUTO_INCREMENT PRIMARY KEY, entry TEXT, author VARCHAR(255), createdAt TIMESTAMP DEFAULT CURRENT_TIMESTAMP)";
    statement.execute(logsTable);
    // ensure older DBs get the author column
    try {
        statement.execute("ALTER TABLE admin_logs ADD COLUMN IF NOT EXISTS author VARCHAR(255)");
    } catch (SQLException ex) { /* ignore */ }

    // private feedback persistent store
    String fbTable = "CREATE TABLE IF NOT EXISTS private_feedback(feedbackId INT AUTO_INCREMENT PRIMARY KEY, questionId INT, author VARCHAR(255), feedback TEXT, createdAt TIMESTAMP DEFAULT CURRENT_TIMESTAMP)";
    statement.execute(fbTable);

    // answer-level private feedback (per-answer feedback entries)
    String afbTable = "CREATE TABLE IF NOT EXISTS answer_feedback(feedbackId INT AUTO_INCREMENT PRIMARY KEY, answerId INT, author VARCHAR(255), feedback TEXT, createdAt TIMESTAMP DEFAULT CURRENT_TIMESTAMP)";
    statement.execute(afbTable);

    // tasks persistent store (unresolved tasks)
    String tasksTable = "CREATE TABLE IF NOT EXISTS tasks(taskId INT AUTO_INCREMENT PRIMARY KEY, description TEXT, createdAt TIMESTAMP DEFAULT CURRENT_TIMESTAMP)";
    statement.execute(tasksTable);
    }

    // load persisted flagged items, notes, logs, and private feedback into in-memory maps/lists
    private void loadPersistentState() {
        try {
            // load flagged items and notes
            String sql = "SELECT fi.itemId, fn.note, fn.author FROM flagged_items fi LEFT JOIN flagged_notes fn ON fi.itemId = fn.itemId ORDER BY fn.noteId ASC";
            try (PreparedStatement pstmt = connection.prepareStatement(sql); ResultSet rs = pstmt.executeQuery()) {
                flaggedItems.clear();
                while (rs.next()) {
                    String itemId = rs.getString("itemId");
                    String note = rs.getString("note");
                    String author = rs.getString("author");
                    if (itemId == null) continue;
                    if (note == null) note = "";
                    String display = (author != null && !author.trim().isEmpty()) ? (author + ": " + note) : note;
                    flaggedItems.computeIfAbsent(itemId, k -> new ArrayList<>()).add(display);
                }
            }

            // load logs (include author if present)
            String lsql = "SELECT entry, author FROM admin_logs ORDER BY createdAt ASC";
            try (PreparedStatement lp = connection.prepareStatement(lsql); ResultSet lr = lp.executeQuery()) {
                logs.clear();
                while (lr.next()) {
                    String entry = lr.getString("entry");
                    String author = lr.getString("author");
                    if (author != null && !author.trim().isEmpty()) logs.add(author + ": " + entry);
                    else logs.add(entry);
                }
            }

            // load private feedback
            String fsql = "SELECT questionId, author, feedback FROM private_feedback ORDER BY createdAt ASC";
            try (PreparedStatement fp = connection.prepareStatement(fsql); ResultSet fr = fp.executeQuery()) {
                privateFeedback.clear();
                while (fr.next()) {
                    int qid = fr.getInt("questionId");
                    String author = fr.getString("author");
                    String feedback = fr.getString("feedback");
                    privateFeedback.computeIfAbsent(qid, k -> new ArrayList<>()).add((author == null ? "anon" : author) + ": " + feedback);
                }
            }
                    // load answer-level private feedback
                    try {
                        String afsql = "SELECT answerId, author, feedback FROM answer_feedback ORDER BY createdAt ASC";
                        try (PreparedStatement afp = connection.prepareStatement(afsql); ResultSet afr = afp.executeQuery()) {
                            privateAnswerFeedback.clear();
                            while (afr.next()) {
                                int aid = afr.getInt("answerId");
                                String author = afr.getString("author");
                                String feedback = afr.getString("feedback");
                                privateAnswerFeedback.computeIfAbsent(aid, k -> new ArrayList<>()).add((author == null ? "anon" : author) + ": " + feedback);
                            }
                        }
                    } catch (SQLException ex) { /* ignore answer feedback load errors */ }
            // load tasks
            try {
                List<String> dbTasks = new ArrayList<>();
                String tsql = "SELECT description FROM tasks ORDER BY createdAt ASC";
                try (PreparedStatement tp = connection.prepareStatement(tsql); ResultSet tr = tp.executeQuery()) {
                    while (tr.next()) dbTasks.add(tr.getString("description"));
                }
                taskBoard.clear();
                taskBoard.addAll(dbTasks);
            } catch (SQLException tex) { /* ignore task load errors */ }
        } catch (SQLException e) {
            // best-effort load; keep in-memory state if DB read fails
            e.printStackTrace();
        }
    }
    //insert a question
    public int createQuestion(Question question) throws SQLException {
        String sql = "INSERT INTO questions (title, content, authorUserName, category) VALUES (?, ?, ?, ?)";
        try (PreparedStatement pstmt = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, question.getTitle());
            pstmt.setString(2, question.getContent());
            pstmt.setString(3, question.getAuthorUserName());
            pstmt.setString(4, question.getCategory());
            pstmt.executeUpdate();
            //return the question id
            ResultSet rs = pstmt.getGeneratedKeys();
            if (rs.next()) {
                int generatedId = rs.getInt(1);
                question.setQuestionId(generatedId);
                return generatedId;
            }
        }
            return -1;
        }
        //get all questions
        public Questions getAllQuestions() throws SQLException {
            Questions questions = new Questions();
            String sql = "SELECT * FROM questions ORDER BY createdAt DESC";
            try (PreparedStatement pstmt = connection.prepareStatement(sql);
                 ResultSet rs = pstmt.executeQuery()) {

                while (rs.next()) {
                    Question q = extractQuestionFromResultSet(rs);
                    questions.addQuestion(q);
                }
            }
            return questions;
        }
        //get question by id
        public Question getQuestionById(int questionId) throws SQLException {
            String sql = "SELECT * FROM questions WHERE questionId = ?";
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setInt(1, questionId);
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    return extractQuestionFromResultSet(rs);
                }
            }
            return null;
        }
        //update a question
        public boolean updateQuestion(Question question) throws SQLException {
            String sql = "UPDATE questions SET title = ?, content = ?, updatedAt = ?, "
                    + "isAnswered = ?, category = ? WHERE questionId = ?";
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, question.getTitle());
                pstmt.setString(2, question.getContent());
                pstmt.setTimestamp(3, Timestamp.valueOf(LocalDateTime.now()));
                pstmt.setBoolean(4, question.getIsAnswered());
                pstmt.setString(5, question.getCategory());
                pstmt.setInt(6, question.getQuestionId());
                return pstmt.executeUpdate() > 0;
            }
        }
        //delete a question
        public boolean deleteQuestion(int questionId) throws SQLException {
            // perform cleanup: remove answers, related flagged notes/items, and then the question
            boolean autoCommit = connection.getAutoCommit();
            try {
                connection.setAutoCommit(false);
                // find answers for the question
                String findAnswers = "SELECT answerId FROM answers WHERE questionId = ?";
                try (PreparedStatement fa = connection.prepareStatement(findAnswers)) {
                    fa.setInt(1, questionId);
                    try (ResultSet rs = fa.executeQuery()) {
                        while (rs.next()) {
                            int aid = rs.getInt("answerId");
                            // remove any flagged notes/items for this answer and its feedback
                            try (PreparedStatement dp1 = connection.prepareStatement("DELETE FROM flagged_notes WHERE itemId = ?")) {
                                dp1.setString(1, "A:" + aid); dp1.executeUpdate();
                            }
                            try (PreparedStatement dp2 = connection.prepareStatement("DELETE FROM flagged_items WHERE itemId = ?")) {
                                dp2.setString(1, "A:" + aid); dp2.executeUpdate();
                            }
                            try (PreparedStatement dp3 = connection.prepareStatement("DELETE FROM flagged_notes WHERE itemId LIKE ?")) {
                                dp3.setString(1, "FB:A:" + aid + ":%"); dp3.executeUpdate();
                            }
                            try (PreparedStatement dp4 = connection.prepareStatement("DELETE FROM flagged_items WHERE itemId LIKE ?")) {
                                dp4.setString(1, "FB:A:" + aid + ":%"); dp4.executeUpdate();
                            }
                            // remove answer-level private feedback
                            try (PreparedStatement ap = connection.prepareStatement("DELETE FROM answer_feedback WHERE answerId = ?")) { ap.setInt(1, aid); ap.executeUpdate(); }
                        }
                    }
                }
                // remove question-level flagged notes/items and private feedback
                try (PreparedStatement dpq1 = connection.prepareStatement("DELETE FROM flagged_notes WHERE itemId = ?")) { dpq1.setString(1, "Q:" + questionId); dpq1.executeUpdate(); }
                try (PreparedStatement dpq2 = connection.prepareStatement("DELETE FROM flagged_items WHERE itemId = ?")) { dpq2.setString(1, "Q:" + questionId); dpq2.executeUpdate(); }
                try (PreparedStatement dpq3 = connection.prepareStatement("DELETE FROM flagged_notes WHERE itemId LIKE ?")) { dpq3.setString(1, "FB:" + questionId + ":%"); dpq3.executeUpdate(); }
                try (PreparedStatement dpq4 = connection.prepareStatement("DELETE FROM flagged_items WHERE itemId LIKE ?")) { dpq4.setString(1, "FB:" + questionId + ":%"); dpq4.executeUpdate(); }
                try (PreparedStatement pfp = connection.prepareStatement("DELETE FROM private_feedback WHERE questionId = ?")) { pfp.setInt(1, questionId); pfp.executeUpdate(); }

                // remove answers
                try (PreparedStatement da = connection.prepareStatement("DELETE FROM answers WHERE questionId = ?")) { da.setInt(1, questionId); da.executeUpdate(); }

                // finally remove the question
                try (PreparedStatement dq = connection.prepareStatement("DELETE FROM questions WHERE questionId = ?")) { dq.setInt(1, questionId); int res = dq.executeUpdate(); connection.commit(); return res > 0; }
            } catch (SQLException ex) {
                try { connection.rollback(); } catch (SQLException e) { /* ignore rollback errors */ }
                throw ex;
            } finally {
                try { connection.setAutoCommit(autoCommit); } catch (SQLException e) { /* ignore */ }
            }
        }

        //ANSWER CRUD OPERATIONS

        //insert an answer
        public int createAnswer(Answer answer) throws SQLException {
            String sql = "INSERT INTO answers (questionId, content, authorUserName, createdAt, updatedAt, isAccepted) "
                    + "VALUES (?, ?, ?, ?, ?, ?)";

            try (PreparedStatement pstmt = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                pstmt.setInt(1, answer.getQuestionId());
                pstmt.setString(2, answer.getContent());
                pstmt.setString(3, answer.getAuthorUserName());
                pstmt.setTimestamp(4, Timestamp.valueOf(answer.getCreatedAt()));
                pstmt.setTimestamp(5, Timestamp.valueOf(answer.getUpdatedAt()));
                pstmt.setBoolean(6, answer.getIsAccepted());

                pstmt.executeUpdate();

                // generate answerId
                ResultSet rs = pstmt.getGeneratedKeys();
                if (rs.next()) {
                    int generatedId = rs.getInt(1);
                    answer.setAnswerId(generatedId);
                    return generatedId;
                }
            }
            return -1;
        }
        //get all answers for a question
        public Answers getAnswersForQuestion(int questionId) throws SQLException {
            Answers answers = new Answers();
            String sql = "SELECT * FROM answers WHERE questionId = ? ORDER BY isAccepted DESC, createdAt ASC";

            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setInt(1, questionId);
                ResultSet rs = pstmt.executeQuery();

                while (rs.next()) {
                    Answer a = extractAnswerFromResultSet(rs);
                    answers.addAnswer(a);
                }
            }
            return answers;
        }
        //get all answers
        public Answers getAllAnswers() throws SQLException {
            Answers answers = new Answers();
            String sql = "SELECT * FROM answers ORDER BY createdAt DESC";
            try (PreparedStatement pstmt = connection.prepareStatement(sql);
                 ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    Answer a = extractAnswerFromResultSet(rs);
                    answers.addAnswer(a);
                }
            }
            return answers;
        }
        //update an answer
        public boolean updateAnswer(Answer answer) throws SQLException {
            String sql = "UPDATE answers SET content = ?, updatedAt = ?, isAccepted = ? WHERE answerId = ?";

            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, answer.getContent());
                pstmt.setTimestamp(2, Timestamp.valueOf(LocalDateTime.now()));
                pstmt.setBoolean(3, answer.getIsAccepted());
                pstmt.setInt(4, answer.getAnswerId());

                return pstmt.executeUpdate() > 0;
            }
        }
        //delete an answer
        public boolean deleteAnswer(int answerId) throws SQLException {
            // remove flagged notes/items and answer-feedback associated with this answer, then delete the answer
            boolean autoCommit = connection.getAutoCommit();
            try {
                connection.setAutoCommit(false);
                try (PreparedStatement dp1 = connection.prepareStatement("DELETE FROM flagged_notes WHERE itemId = ?")) { dp1.setString(1, "A:" + answerId); dp1.executeUpdate(); }
                try (PreparedStatement dp2 = connection.prepareStatement("DELETE FROM flagged_items WHERE itemId = ?")) { dp2.setString(1, "A:" + answerId); dp2.executeUpdate(); }
                try (PreparedStatement dp3 = connection.prepareStatement("DELETE FROM flagged_notes WHERE itemId LIKE ?")) { dp3.setString(1, "FB:A:" + answerId + ":%"); dp3.executeUpdate(); }
                try (PreparedStatement dp4 = connection.prepareStatement("DELETE FROM flagged_items WHERE itemId LIKE ?")) { dp4.setString(1, "FB:A:" + answerId + ":%"); dp4.executeUpdate(); }
                try (PreparedStatement af = connection.prepareStatement("DELETE FROM answer_feedback WHERE answerId = ?")) { af.setInt(1, answerId); af.executeUpdate(); }
                try (PreparedStatement da = connection.prepareStatement("DELETE FROM answers WHERE answerId = ?")) { da.setInt(1, answerId); int res = da.executeUpdate(); connection.commit(); return res > 0; }
            } catch (SQLException ex) {
                try { connection.rollback(); } catch (SQLException e) { /* ignore rollback errors */ }
                throw ex;
            } finally {
                try { connection.setAutoCommit(autoCommit); } catch (SQLException e) { /* ignore */ }
            }
        }
        //helper methods for all operations
        private Question extractQuestionFromResultSet(ResultSet rs) throws SQLException {
            Question q = new Question(
                rs.getString("title"),
                rs.getString("content"),
                rs.getString("authorUserName")
            );
            q.setQuestionId(rs.getInt("questionId"));
            q.setCreatedAt(rs.getTimestamp("createdAt").toLocalDateTime());
            q.setUpdatedAt(rs.getTimestamp("updatedAt").toLocalDateTime());
            q.setIsAnswered(rs.getBoolean("isAnswered"));
            q.setCategory(rs.getString("category"));
            return q;
        }
        //extract an answer from the result set
        private Answer extractAnswerFromResultSet(ResultSet rs) throws SQLException {
            Answer a = new Answer(
                rs.getInt("answerId"),
                rs.getInt("questionId"),
                rs.getString("content"),
                rs.getString("authorUserName"),
                rs.getTimestamp("createdAt").toLocalDateTime(),
                rs.getTimestamp("updatedAt").toLocalDateTime(),
                rs.getBoolean("isAccepted")
            );
            return a;
        }

        // get answer by id
        public Answer getAnswerById(int answerId) throws SQLException {
            String sql = "SELECT * FROM answers WHERE answerId = ?";
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setInt(1, answerId);
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) return extractAnswerFromResultSet(rs);
            }
            return null;
        }

        /**
         * Check if an itemId (Q:<id> | A:<id> | FB:<qId>:<idx>) is flagged.
         * @param itemId identifier
         * @return true if flagged
         */
        public boolean isItemFlagged(String itemId) { return flaggedItems.containsKey(itemId); }

        /**
         * Remove flag for an item.
         * @param itemId identifier
         * @return true if removed
         */
        public boolean unflagItem(String itemId) { return unflagItem(itemId, null); }

        /**
         * Unflag an item and record who performed the unflag operation (optional).
         * @param itemId identifier
         * @param author username performing the unflag (may be null)
         * @return true if removed
         */
        public boolean unflagItem(String itemId, String author) {
            try {
                String delNotes = "DELETE FROM flagged_notes WHERE itemId = ?";
                try (PreparedStatement dp = connection.prepareStatement(delNotes)) { dp.setString(1, itemId); dp.executeUpdate(); }
                String delItem = "DELETE FROM flagged_items WHERE itemId = ?";
                try (PreparedStatement dip = connection.prepareStatement(delItem)) { dip.setString(1, itemId); dip.executeUpdate(); }
            } catch (SQLException e) {
                // ignore DB errors, continue to remove in-memory
            }
            if (flaggedItems.remove(itemId) != null) {
                addLog("UNFLAG " + itemId, author);
                return true;
            }
            return false;
        }

        /**
         * Convenience to flag an answer by id.
         */
    public void flagAnswer(int answerId, String note) { flagItem("A:" + answerId, note); }
    public void flagAnswer(int answerId, String note, String author) { flagItem("A:" + answerId, note, author); }

        /**
         * Convenience to flag a question by id.
         */
    public void flagQuestion(int questionId, String note) { flagItem("Q:" + questionId, note); }
    public void flagQuestion(int questionId, String note, String author) { flagItem("Q:" + questionId, note, author); }

        /**
         * Convenience to flag a feedback entry by questionId and feedback index.
         */
    public void flagFeedback(int questionId, int feedbackIndex, String note) { flagItem("FB:" + questionId + ":" + feedbackIndex, note); }
    public void flagFeedback(int questionId, int feedbackIndex, String note, String author) { flagItem("FB:" + questionId + ":" + feedbackIndex, note, author); }

    /** Flag a feedback entry attached to an answer. */
    public void flagAnswerFeedback(int answerId, int feedbackIndex, String note) { flagItem("FB:A:" + answerId + ":" + feedbackIndex, note); }
    public void flagAnswerFeedback(int answerId, int feedbackIndex, String note, String author) { flagItem("FB:A:" + answerId + ":" + feedbackIndex, note, author); }
        //finally, close the connection
        public void closeConnection() {
            try {
                if (statement != null) statement.close();
                if (connection != null) connection.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }


        /**
         * Add a staff member to the in-memory list (not persisted to DB).
         * @param staff the staff member instance
         */
        public void addStaff(Staff staff) { staffList.add(staff); }
        /**
         * Get all staff members currently tracked (defensive copy).
         * @return list of staff
         */
        public List<Staff> getAllStaff() { return new ArrayList<>(staffList); }
        /**
         * Flag an item (question/answer/feedback) with a note for review.
         * @param itemId identifier (e.g., Q:<id>, A:<id>)
         * @param note explanatory note
         */
        /**
         * Flag an item (question/answer/feedback) with a note for review.
         * Backwards-compatible single-author-omitting method (will store null author).
         * @param itemId identifier (e.g., Q:<id>, A:<id>)
         * @param note explanatory note
         */
        public void flagItem(String itemId, String note) { flagItem(itemId, note, null); }

        /**
         * Flag an item (question/answer/feedback) with a note for review, recording the author who flagged it.
         * @param itemId identifier (e.g., Q:<id>, A:<id>)
         * @param note explanatory note
         * @param author username who performed the flag (may be null)
         */
        public void flagItem(String itemId, String note, String author) {
            if (itemId == null || itemId.trim().isEmpty() || note == null || note.trim().isEmpty()) return;
            String n = note.trim();
            try {
                String mergeSql = "MERGE INTO flagged_items(itemId) KEY(itemId) VALUES (?)";
                try (PreparedStatement mp = connection.prepareStatement(mergeSql)) { mp.setString(1, itemId); mp.executeUpdate(); }
                String ins = "INSERT INTO flagged_notes(itemId, note, author) VALUES (?, ?, ?)";
                try (PreparedStatement ip = connection.prepareStatement(ins)) { ip.setString(1, itemId); ip.setString(2, n); ip.setString(3, author); ip.executeUpdate(); }
                // store display of note (author included if present)
                String display = (author == null || author.trim().isEmpty()) ? n : (author + ": " + n);
                flaggedItems.computeIfAbsent(itemId, k -> new ArrayList<>()).add(display);
                addLog("FLAG " + itemId + " -> " + n, author);
                // also add a task entry so staff/admin have it on their task board
                try {
                    String contentSnippet = itemId;
                    if (itemId.startsWith("Q:")) {
                        int qid = Integer.parseInt(itemId.substring(2));
                        Question q = getQuestionById(qid);
                        if (q != null) contentSnippet = "Q:" + qid + " - " + q.getTitle();
                    } else if (itemId.startsWith("A:")) {
                        int aid = Integer.parseInt(itemId.substring(2));
                        Answer a = getAnswerById(aid);
                        if (a != null) {
                            String c = a.getContent();
                            if (c.length() > 120) c = c.substring(0, 117) + "...";
                            contentSnippet = "A:" + aid + " - " + c;
                        }
                    } else if (itemId.startsWith("FB:")) {
                        contentSnippet = "FB:" + itemId.substring(3);
                    }
                    addTask("Review flagged item " + contentSnippet + " -> " + n);
                } catch (Exception ex) { /* ignore task add errors */ }
            } catch (SQLException e) {
                // fallback to in-memory only
                String display = n;
                flaggedItems.computeIfAbsent(itemId, k -> new ArrayList<>()).add(display);
                addLog("FLAG " + itemId + " -> " + n, author);
                try { addTask("Review flagged item " + itemId + " -> " + n); } catch (Exception ex) { /* ignore */ }
            }
        }
        /**
         * Add an additional note to an already flagged item. This does NOT create a new task
         * and will not re-create the flagged_items row. Stores note into flagged_notes and records a NOTE log.
         * @param itemId the item identifier
         * @param note the note to append
         * @param author optional author username
         */
        public void addFlagNote(String itemId, String note, String author) {
            if (itemId == null || itemId.trim().isEmpty() || note == null || note.trim().isEmpty()) return;
            String n = note.trim();
            try {
                // ensure parent flagged_items exists
                String mergeSql = "MERGE INTO flagged_items(itemId) KEY(itemId) VALUES (?)";
                try (PreparedStatement mp = connection.prepareStatement(mergeSql)) { mp.setString(1, itemId); mp.executeUpdate(); }
                String ins = "INSERT INTO flagged_notes(itemId, note, author) VALUES (?, ?, ?)";
                try (PreparedStatement ip = connection.prepareStatement(ins)) { ip.setString(1, itemId); ip.setString(2, n); ip.setString(3, author); ip.executeUpdate(); }
                String display = (author == null || author.trim().isEmpty()) ? n : (author + ": " + n);
                flaggedItems.computeIfAbsent(itemId, k -> new ArrayList<>()).add(display);
                addLog("NOTE " + itemId + " -> " + n, author);
            } catch (SQLException e) {
                // fallback to in-memory only
                flaggedItems.computeIfAbsent(itemId, k -> new ArrayList<>()).add(n);
                addLog("NOTE " + itemId + " -> " + n, author);
            }
        }

        /** Backwards-compatible wrapper */
        public void addFlagNote(String itemId, String note) { addFlagNote(itemId, note, null); }
        /**
         * Retrieve all flagged items and their notes.
         * @return map of itemId to notes
         */
        public Map<String, List<String>> getFlaggedItems() {
            // return defensive copy
            return new HashMap<>(flaggedItems);
        }
        /**
         * Add a historical log entry (actions, messages, etc.).
         * @param logEntry the log text
         */
        /**
         * Add a historical log entry (actions, messages, etc.) with optional author.
         * @param logEntry the log text
         * @param author optional author username (may be null)
         */
        public void addLog(String logEntry, String author) {
            if (logEntry == null || logEntry.trim().isEmpty()) return;
            String e = logEntry.trim();
            try {
                String ins = "INSERT INTO admin_logs(entry, author) VALUES (?, ?)";
                try (PreparedStatement p = connection.prepareStatement(ins)) { p.setString(1, e); p.setString(2, author); p.executeUpdate(); }
                if (author == null || author.trim().isEmpty()) logs.add(e);
                else logs.add(author + ": " + e);
            } catch (SQLException ex) {
                // fallback to in-memory
                if (author == null || author.trim().isEmpty()) logs.add(e);
                else logs.add(author + ": " + e);
            }
        }

        /**
         * Backwards-compatible single-argument addLog.
         */
        public void addLog(String logEntry) { addLog(logEntry, null); }
        /**
         * Get all historical log entries.
         * @return list of log strings
         */
        public List<String> getLogs() {
            // try to read from DB to ensure latest
            try {
                List<String> dbLogs = new ArrayList<>();
                String sql = "SELECT entry, author FROM admin_logs ORDER BY createdAt ASC";
                try (PreparedStatement p = connection.prepareStatement(sql); ResultSet rs = p.executeQuery()) {
                    while (rs.next()) {
                        String entry = rs.getString("entry");
                        String author = rs.getString("author");
                        if (author != null && !author.trim().isEmpty()) dbLogs.add(author + ": " + entry);
                        else dbLogs.add(entry);
                    }
                }
                // update in-memory copy
                logs.clear(); logs.addAll(dbLogs);
                return new ArrayList<>(logs);
            } catch (SQLException ex) {
                return new ArrayList<>(logs);
            }
        }
        /**
         * Add a task / issue to the task board (unresolved by default).
         * @param description task description
         * @return index id of task
         */
        public int addTask(String description) {
            if (description == null || description.trim().isEmpty()) return -1;
            String desc = description.trim();
            try {
                String ins = "INSERT INTO tasks(description) VALUES (?)";
                try (PreparedStatement p = connection.prepareStatement(ins)) { p.setString(1, desc); p.executeUpdate(); }
            } catch (SQLException ex) {
                // ignore DB error and continue with in-memory only
            }
            taskBoard.add(desc);
            addLog("TASK ADD -> " + desc);
            // notify listeners about updated tasks
            try {
                List<String> snapshot = new ArrayList<>(taskBoard);
                for (java.util.function.Consumer<List<String>> l : taskListeners) {
                    try { l.accept(snapshot); } catch (Exception ex) { /* ignore listener errors */ }
                }
            } catch (Exception ex) { /* ignore notify errors */ }
            return taskBoard.size() - 1;
        }
        /**
         * Resolve a task by its index (removes it from unresolved list).
         * @param taskIndex index returned from addTask
         * @return true if removed
         */
        public boolean resolveTask(int taskIndex) {
            if (taskIndex < 0 || taskIndex >= taskBoard.size()) return false;
            String removed = taskBoard.remove(taskIndex);
            addLog("TASK RESOLVED -> " + removed);
            // attempt to remove a matching row from DB (remove the oldest matching description)
            try {
                String del = "DELETE FROM tasks WHERE taskId = (SELECT taskId FROM tasks WHERE description = ? ORDER BY createdAt ASC LIMIT 1)";
                try (PreparedStatement dp = connection.prepareStatement(del)) { dp.setString(1, removed); dp.executeUpdate(); }
            } catch (SQLException ex) { /* ignore DB delete errors */ }
            // notify listeners about updated tasks
            try {
                List<String> snapshot = new ArrayList<>(taskBoard);
                for (java.util.function.Consumer<List<String>> l : taskListeners) {
                    try { l.accept(snapshot); } catch (Exception ex) { /* ignore listener errors */ }
                }
            } catch (Exception ex) { /* ignore notify errors */ }
            return true;
        }

        /**
         * Register a listener to be notified when the task board changes.
         * Listener receives a snapshot list of current tasks.
         */
        public void registerTaskListener(java.util.function.Consumer<List<String>> listener) {
            if (listener == null) return;
            taskListeners.add(listener);
        }

        /** Unregister a previously registered task listener. */
        public void unregisterTaskListener(java.util.function.Consumer<List<String>> listener) {
            if (listener == null) return;
            taskListeners.remove(listener);
        }
        /**
         * Get current unresolved tasks.
         * @return list of task descriptions
         */
        public List<String> getTasks() { return new ArrayList<>(taskBoard); }
        /**
         * Add private feedback entry for a question.
         * @param questionId the question id
         * @param feedback feedback content
         * @param author author username
         */
        public void addPrivateFeedback(int questionId, String feedback, String author) {
            if (feedback == null || feedback.trim().isEmpty()) return;
            String fb = feedback.trim();
            String auth = author == null ? "anon" : author;
            try {
                String ins = "INSERT INTO private_feedback(questionId, author, feedback) VALUES (?, ?, ?)";
                try (PreparedStatement p = connection.prepareStatement(ins)) { p.setInt(1, questionId); p.setString(2, auth); p.setString(3, fb); p.executeUpdate(); }
                privateFeedback.computeIfAbsent(questionId, k -> new ArrayList<>()).add(auth + ": " + fb);
                addLog("FEEDBACK Q:" + questionId + " -> " + fb, auth);
            } catch (SQLException e) {
                // fallback to in-memory
                privateFeedback.computeIfAbsent(questionId, k -> new ArrayList<>()).add(auth + ": " + fb);
                addLog("FEEDBACK Q:" + questionId + " -> " + fb, auth);
            }
        }

        /** Add private feedback attached to an answer. */
        public void addPrivateFeedbackForAnswer(int answerId, String feedback, String author) {
            if (feedback == null || feedback.trim().isEmpty()) return;
            String fb = feedback.trim();
            String auth = author == null ? "anon" : author;
            try {
                String ins = "INSERT INTO answer_feedback(answerId, author, feedback) VALUES (?, ?, ?)";
                try (PreparedStatement p = connection.prepareStatement(ins)) { p.setInt(1, answerId); p.setString(2, auth); p.setString(3, fb); p.executeUpdate(); }
                privateAnswerFeedback.computeIfAbsent(answerId, k -> new ArrayList<>()).add(auth + ": " + fb);
                addLog("FEEDBACK A:" + answerId + " -> " + fb, auth);
            } catch (SQLException e) {
                privateAnswerFeedback.computeIfAbsent(answerId, k -> new ArrayList<>()).add(auth + ": " + fb);
                addLog("FEEDBACK A:" + answerId + " -> " + fb, auth);
            }
        }

        /** Get private feedback for an answer. */
        public List<String> getPrivateFeedbackForAnswer(int answerId) {
            try {
                List<String> out = new ArrayList<>();
                String sql = "SELECT author, feedback FROM answer_feedback WHERE answerId = ? ORDER BY createdAt ASC";
                try (PreparedStatement p = connection.prepareStatement(sql)) {
                    p.setInt(1, answerId);
                    try (ResultSet rs = p.executeQuery()) {
                        while (rs.next()) out.add(rs.getString("author") + ": " + rs.getString("feedback"));
                    }
                }
                privateAnswerFeedback.put(answerId, new ArrayList<>(out));
                return out;
            } catch (SQLException e) {
                return privateAnswerFeedback.getOrDefault(answerId, new ArrayList<>());
            }
        }
        /**
         * Get private feedback entries for a question.
         * @param questionId the question id
         * @return list of feedback strings (may be empty)
         */
        public List<String> getPrivateFeedbackForQuestion(int questionId) {
            // try to read from DB for freshness
            try {
                List<String> out = new ArrayList<>();
                String sql = "SELECT author, feedback FROM private_feedback WHERE questionId = ? ORDER BY createdAt ASC";
                try (PreparedStatement p = connection.prepareStatement(sql)) {
                    p.setInt(1, questionId);
                    try (ResultSet rs = p.executeQuery()) {
                        while (rs.next()) out.add(rs.getString("author") + ": " + rs.getString("feedback"));
                    }
                }
                // update in-memory map
                privateFeedback.put(questionId, new ArrayList<>(out));
                return out;
            } catch (SQLException e) {
                return privateFeedback.getOrDefault(questionId, new ArrayList<>());
            }
        }
        /**
         * Get all private feedback keyed by question id.
         * @return map of questionId to feedback list
         */
        public Map<Integer, List<String>> getAllPrivateFeedback() { return privateFeedback; }
    }
