
package com.worknest.servlet;

import com.worknest.util.DatabaseUtil;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.json.JSONObject;
import com.worknest.util.NotificationUtil;

public class ReviewSubmissionServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    private Connection getConnection() throws SQLException {
        String username = System.getenv("DB_USERNAME");
        String password = System.getenv("DB_PASSWORD");

        if (username == null || password == null) {
            throw new SQLException("Database configuration is missing.");
        }

        return DatabaseUtil.getConnection();
    }

    private void sendJson(
            HttpServletResponse response,
            int status,
            JSONObject body) throws IOException {

        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-store");
        response.getWriter().write(body.toString());
    }

    private Long getUserId(HttpSession session) {
        Object value = session.getAttribute("userId");

        if (value instanceof Number) {
            return ((Number) value).longValue();
        }

        return null;
    }

    @Override
    protected void doPost(
            HttpServletRequest request,
            HttpServletResponse response)
            throws ServletException, IOException {

        request.setCharacterEncoding("UTF-8");

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-store");

        HttpSession session = request.getSession(false);

        if (session == null || getUserId(session) == null) {
            sendJson(response, 401,
                new JSONObject()
                    .put("success", false)
                    .put("message", "Please log in."));
            return;
        }

        Long clientId = getUserId(session);
        String role = (String) session.getAttribute("role");

        if (!"CLIENT".equals(role)) {
            sendJson(response, 403,
                new JSONObject()
                    .put("success", false)
                    .put("message",
                         "Only clients can review final deliveries."));
            return;
        }

        long projectId;
        long submissionId;

        try {
            projectId = Long.parseLong(
                request.getParameter("projectId"));
            submissionId = Long.parseLong(
                request.getParameter("submissionId"));
        } catch (Exception e) {
            sendJson(response, 400,
                new JSONObject()
                    .put("success", false)
                    .put("message",
                         "Valid projectId and submissionId are required."));
            return;
        }

        if (projectId <= 0 || submissionId <= 0) {
            sendJson(response, 400,
                new JSONObject()
                    .put("success", false)
                    .put("message", "Invalid project or submission ID."));
            return;
        }

        String action = request.getParameter("action");
        String feedback = request.getParameter("feedback");

        if (action == null
                || (!"APPROVE".equals(action)
                    && !"REQUEST_REVISION".equals(action))) {

            sendJson(response, 400,
                new JSONObject()
                    .put("success", false)
                    .put("message",
                         "Action must be APPROVE or REQUEST_REVISION."));
            return;
        }

        if ("REQUEST_REVISION".equals(action)
                && (feedback == null
                    || feedback.trim().isEmpty()
                    || feedback.trim().length() > 10000)) {

            sendJson(response, 400,
                new JSONObject()
                    .put("success", false)
                    .put("message",
                         "Provide revision feedback of 1 to 10000 characters."));
            return;
        }

        if (feedback != null && feedback.length() > 10000) {
            sendJson(response, 400,
                new JSONObject()
                    .put("success", false)
                    .put("message", "Feedback cannot exceed 10000 characters."));
            return;
        }

        try (Connection con = getConnection()) {
            con.setAutoCommit(false);

            try {
                String projectSql = """
                    SELECT status, title
                    FROM projects
                    WHERE project_id = ?
                      AND client_id = ?
                    FOR UPDATE
                    """;

                String projectStatus;
                String projectTitle;

                try (PreparedStatement ps =
                         con.prepareStatement(projectSql)) {

                    ps.setLong(1, projectId);
                    ps.setLong(2, clientId);

                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            con.rollback();

                            sendJson(response, 403,
                                new JSONObject()
                                    .put("success", false)
                                    .put("message",
                                         "You do not own this project."));
                            return;
                        }

                        projectStatus = rs.getString("status");
                        projectTitle = rs.getString("title");
                    }
                }

                if (!"IN_PROGRESS".equals(projectStatus)) {
                    con.rollback();

                    sendJson(response, 409,
                        new JSONObject()
                            .put("success", false)
                            .put("message",
                                 "Only projects in progress can be reviewed."));
                    return;
                }

                String latestSql = """
                    SELECT submission_id, status, freelancer_id
                    FROM project_submissions
                    WHERE project_id = ?
                    ORDER BY submission_id DESC
                    LIMIT 1
                    FOR UPDATE
                    """;

                long latestSubmissionId;
                long freelancerId;
                String submissionStatus;

                try (PreparedStatement ps =
                         con.prepareStatement(latestSql)) {

                    ps.setLong(1, projectId);

                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            con.rollback();

                            sendJson(response, 404,
                                new JSONObject()
                                    .put("success", false)
                                    .put("message",
                                         "No delivery has been submitted."));
                            return;
                        }

                        latestSubmissionId =
                            rs.getLong("submission_id");
                        submissionStatus = rs.getString("status");
                        freelancerId = rs.getLong("freelancer_id");
                    }
                }

                if (latestSubmissionId != submissionId) {
                    con.rollback();

                    sendJson(response, 409,
                        new JSONObject()
                            .put("success", false)
                            .put("message",
                                 "This is not the latest project delivery."));
                    return;
                }

                if (!"SUBMITTED".equals(submissionStatus)) {
                    con.rollback();

                    sendJson(response, 409,
                        new JSONObject()
                            .put("success", false)
                            .put("message",
                                 "This delivery is no longer awaiting review."));
                    return;
                }

                String updateSql = """
                    UPDATE project_submissions
                    SET status = ?,
                        client_feedback = ?,
                        reviewed_at = CURRENT_TIMESTAMP
                    WHERE submission_id = ?
                      AND project_id = ?
                      AND status = 'SUBMITTED'
                    """;

                String newSubmissionStatus =
                    "APPROVE".equals(action)
                        ? "APPROVED"
                        : "REVISION_REQUESTED";

                try (PreparedStatement ps =
                         con.prepareStatement(updateSql)) {

                    ps.setString(1, newSubmissionStatus);

                    if ("REQUEST_REVISION".equals(action)) {
                        ps.setString(2, feedback.trim());
                    } else {
                        ps.setNull(2, java.sql.Types.VARCHAR);
                    }

                    ps.setLong(3, submissionId);
                    ps.setLong(4, projectId);

                    if (ps.executeUpdate() != 1) {
                        throw new SQLException(
                            "The submission could not be updated.");
                    }
                }

                if ("APPROVE".equals(action)) {
                    String completeSql = """
                        UPDATE projects
                        SET status = 'COMPLETED'
                        WHERE project_id = ?
                          AND client_id = ?
                          AND status = 'IN_PROGRESS'
                        """;

                    try (PreparedStatement ps =
                             con.prepareStatement(completeSql)) {

                        ps.setLong(1, projectId);
                        ps.setLong(2, clientId);

                        if (ps.executeUpdate() != 1) {
                            throw new SQLException(
                                "The project status could not be updated.");
                        }
                    }
                }

                if ("APPROVE".equals(action)) {
                    NotificationUtil.create(con, freelancerId, projectId,
                            "DELIVERY_APPROVED", "Delivery approved",
                            "The client approved your delivery for “" + projectTitle + "”. The project is complete.");
                } else {
                    NotificationUtil.create(con, freelancerId, projectId,
                            "REVISION_REQUESTED", "Revision requested",
                            "The client requested a revision for “" + projectTitle + "”. Open the workspace to review the feedback.");
                }

                con.commit();

                JSONObject result = new JSONObject();
                result.put("success", true);
                result.put("submissionStatus", newSubmissionStatus);
                result.put("projectStatus",
                    "APPROVE".equals(action)
                        ? "COMPLETED"
                        : "IN_PROGRESS");
                result.put("message",
                    "APPROVE".equals(action)
                        ? "Delivery approved. Project completed."
                        : "Revision requested. Project remains in progress.");

                sendJson(response, 200, result);

            } catch (SQLException | RuntimeException e) {
                try {
                    con.rollback();
                } catch (SQLException rollbackError) {
                    log("Failed to roll back submission review.",
                        rollbackError);
                }

                throw e;
            } finally {
                try {
                    con.setAutoCommit(true);
                } catch (SQLException e) {
                    log("Failed to reset database connection.", e);
                }
            }

        } catch (SQLException e) {
            log("Unable to review project submission.", e);

            sendJson(response, 500,
                new JSONObject()
                    .put("success", false)
                    .put("message",
                         "Unable to process the project review."));
        }
    }
}
