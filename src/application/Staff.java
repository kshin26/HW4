package application;

import databasePart1.DiscussionBoardDAO;

/**
 * Represents a staff member in the system. Staff can review questions, answers, private
 * feedback, flag items, maintain a lightweight task board and communicate with instructors
 * and other staff members.
 */
public class Staff extends User {
    private String staffId;

    /**
     * Constructs a Staff member with the given username, password, and staffId.
     * @param username the username of the staff member
     * @param password the password of the staff member
     * @param staffId the unique staff ID
     */
    public Staff(String username, String password, String staffId) {
        super(username, password, "staff");
        this.staffId = staffId;
    }

    /**
     * Gets the staff ID.
     * @return the staff ID
     */
    public String getStaffId() {
        return staffId;
    }

    /**
     * Sets the staff ID.
     * @param staffId the new staff ID
     */
    public void setStaffId(String staffId) {
        this.staffId = staffId;
    }

    /**
     * Sends a communication message that is persisted in the in-memory log of the DAO.
     * This satisfies the staff communication user story by recording the intent.
     * @param dao the discussion board DAO
     * @param toRecipient the target recipient
     * @param message the message body
     */
    public void sendMessage(DiscussionBoardDAO dao, String toRecipient, String message) {
        if (dao == null || message == null || message.trim().isEmpty()) {
            return; //silently ignore invalid input
        }
        dao.addLog("MSG from " + staffId + " to " + toRecipient + ": " + message.trim());
    }
}
