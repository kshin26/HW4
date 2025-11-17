package test;

import application.Staff;
import application.Question;
import application.Answer;
import databasePart1.DiscussionBoardDAO;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/**
 * JUnit4 tests for Staff / DiscussionBoardDAO features.
 */
public class StaffTest {

	private DiscussionBoardDAO dao;
	private Staff staff;
	private Question question;
	private Answer answer;

	@Before
	public void setUp() throws SQLException {
		dao = new DiscussionBoardDAO();
		staff = new Staff("staffUser", "pw", "S-1");
		dao.addStaff(staff);

		// create a question and an answer to operate on
		question = new Question("Sample Title", "Sample Content", "student1");
		int qId = dao.createQuestion(question);
		assertTrue("Question should be persisted", qId > 0);
		question.setQuestionId(qId);

		answer = new Answer(question.getQuestionId(), "Answer Content", "student2");
		int aId = dao.createAnswer(answer);
		assertTrue("Answer should be persisted", aId > 0);
		answer.setAnswerId(aId);
	}

	@After
	public void tearDown() {
		try {
			if (answer != null) dao.deleteAnswer(answer.getAnswerId());
			if (question != null) dao.deleteQuestion(question.getQuestionId());
		} catch (SQLException ignored) {
		}
		if (dao != null) dao.closeConnection();
	}

	/**
	 * Tests that creating a question and answer persists correctly
	 * and that they can be retrieved from the DAO.
	 */
	@Test
	public void testCreateQuestionAndAnswer() throws SQLException {
		Question q = dao.getQuestionById(question.getQuestionId());
		assertNotNull("Question should be retrievable", q);
		assertNotNull("Answers list should not be null", dao.getAnswersForQuestion(question.getQuestionId()));
	}

	/**
	 * Tests that staff can flag items and add notes to flagged items.
	 */
	@Test
	public void testFlaggingAndNotes() {
		int before = dao.getFlaggedItems().getOrDefault("Q:" + question.getQuestionId(), java.util.Collections.emptyList()).size();
		dao.flagItem("Q:" + question.getQuestionId(), "Potential issue with wording");
		Map<String, List<String>> flagged = dao.getFlaggedItems();
		assertTrue("Flagged map should contain the question key", flagged.containsKey("Q:" + question.getQuestionId()));
		dao.addFlagNote("Q:" + question.getQuestionId(), "Second note");
		flagged = dao.getFlaggedItems();
		assertEquals("Flag note count should increase by 2", before + 2, flagged.get("Q:" + question.getQuestionId()).size());
	}

	/**
	 * Tests the life cycle of tasks: adding a task, resolving it,
	 * and ensuring the task list updates correctly.
	 */
	@Test
	public void testTasksLifecycle() {
		int before = dao.getTasks().size();
		int taskIndex = dao.addTask("Investigate unanswered questions");
		assertTrue("Task index should be >= 0", taskIndex >= 0);
		assertEquals("There should be one additional task", before + 1, dao.getTasks().size());
		assertTrue("Resolve should succeed", dao.resolveTask(taskIndex));
		assertEquals("Tasks should return to previous size after resolve", before, dao.getTasks().size());
	}

	/**
	 * Tests adding private feedback for a question and checks
	 * that the feedback count increases properly.
	 */
	@Test
	public void testPrivateFeedback() {
		int before = dao.getPrivateFeedbackForQuestion(question.getQuestionId()).size();
		dao.addPrivateFeedback(question.getQuestionId(), "Student expressed confusion", staff.getStaffId());
		assertEquals("Private feedback list should increase by 1", before + 1, dao.getPrivateFeedbackForQuestion(question.getQuestionId()).size());
	}

	/**
	 * Tests the staff messaging feature and ensures that
	 * log entries are recorded when messages are sent.
	 */
	@Test
	public void testMessagingAndLogs() {
		staff.sendMessage(dao, "instructor1", "Please review flagged items.");
		assertTrue("Logs should contain a message entry", dao.getLogs().stream().anyMatch(log -> log.contains("MSG")));
		assertTrue("There should be at least 1 log entry", dao.getLogs().size() >= 1);
	}
}
