
package com.worknest.servlet;

import com.worknest.util.DatabaseUtil;

import java.io.IOException;
import java.net.URI;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.json.JSONObject;
import com.worknest.util.NotificationUtil;

public class ProjectSubmissionServlet extends HttpServlet {

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

    private boolean hasAccess(
            Connection con,
            long projectId,
            long userId,
            String role) throws SQLException {

        String sql;

        if ("CLIENT".equals(role)) {
            sql = """
                SELECT project_id
                FROM projects
                WHERE project_id = ?
                  AND client_id = ?
                """;
        } else if ("FREELANCER".equals(role)) {
            sql = """
                SELECT pr.project_id
                FROM projects pr
                INNER JOIN proposals p
                    ON p.project_id = pr.project_id
                WHERE pr.project_id = ?
                  AND p.freelancer_id = ?
                  AND p.status = 'ACCEPTED'
                """;
        } else {
            return false;
        }

        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, projectId);
            ps.setLong(2, userId);

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private JSONObject submissionToJson(ResultSet rs)
            throws SQLException {

        JSONObject submission = new JSONObject();

        submission.put("submissionId",
            rs.getLong("submission_id"));
        submission.put("projectId",
            rs.getLong("project_id"));
        submission.put("freelancerId",
            rs.getLong("freelancer_id"));
        submission.put("submissionNote",
            rs.getString("submission_note"));
        submission.put("deliveryUrl",
            rs.getString("delivery_url") == null
                ? JSONObject.NULL
                : rs.getString("delivery_url"));
        submission.put("status",
            rs.getString("status"));
        submission.put("clientFeedback",
            rs.getString("client_feedback") == null
                ? JSONObject.NULL
                : rs.getString("client_feedback"));

        if (rs.getTimestamp("submitted_at") == null) {
            submission.put("submittedAt", JSONObject.NULL);
        } else {
            submission.put("submittedAt",
                rs.getTimestamp("submitted_at").toString());
        }

        if (rs.getTimestamp("reviewed_at") == null) {
            submission.put("reviewedAt", JSONObject.NULL);
        } else {
            submission.put("reviewedAt",
                rs.getTimestamp("reviewed_at").toString());
        }

        return submission;
    }

    private boolean isValidHttpUrl(String value) {
        if (value == null || value.isBlank()) {
            return true;
        }

        if (value.length() > 2048) {
            return false;
        }

        try {
            URI uri = URI.create(value.trim());

            String scheme = uri.getScheme();

            return uri.isAbsolute()
                && uri.getHost() != null
                && ("https".equalsIgnoreCase(scheme)
                    || "http".equalsIgnoreCase(scheme))
                && uri.getUserInfo() == null;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    @Override
    protected void doGet(
            HttpServletRequest request,
            HttpServletResponse response)
            throws ServletException, IOException {

        HttpSession session = request.getSession(false);

        if (session == null || getUserId(session) == null) {
            sendJson(response, 401,
                new JSONObject()
                    .put("success", false)
                    .put("message", "Please log in."));
            return;
        }

        Long userId = getUserId(session);
        String role = (String) session.getAttribute("role");

        long projectId;

        try {
            projectId = Long.parseLong(
                request.getParameter("projectId"));
        } catch (Exception e) {
            sendJson(response, 400,
                new JSONObject()
                    .put("success", false)
                    .put("message", "A valid projectId is required."));
            return;
        }

        if (projectId <= 0) {
            sendJson(response, 400,
                new JSONObject()
                    .put("success", false)
                    .put("message", "Invalid projectId."));
            return;
        }

        try (Connection con = getConnection()) {
            if (!hasAccess(con, projectId, userId, role)) {
                sendJson(response, 403,
                    new JSONObject()
                        .put("success", false)
                        .put("message",
                             "You cannot access this project."));
                return;
            }

            String sql = """
                SELECT submission_id, project_id, freelancer_id,
                       submission_note, delivery_url, status,
                       client_feedback, submitted_at, reviewed_at
                FROM project_submissions
                WHERE project_id = ?
                ORDER BY submission_id DESC
                LIMIT 1
                """;

            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setLong(1, projectId);

                try (ResultSet rs = ps.executeQuery()) {
                    JSONObject result = new JSONObject();
                    result.put("success", true);

                    if (rs.next()) {
                        result.put("submission",
                            submissionToJson(rs));
                    } else {
                        result.put("submission", JSONObject.NULL);
                    }

                    sendJson(response, 200, result);
                }
            }

        } catch (SQLException e) {
            log("Unable to retrieve project submission.", e);

            sendJson(response, 500,
                new JSONObject()
                    .put("success", false)
                    .put("message",
                         "Unable to retrieve the project submission."));
        }
    }

    @Override
    protected void doPost(
            HttpServletRequest request,
            HttpServletResponse response)
            throws ServletException, IOException {

        request.setCharacterEncoding("UTF-8");

        HttpSession session = request.getSession(false);

        if (session == null || getUserId(session) == null) {
            sendJson(response, 401,
                new JSONObject()
                    .put("success", false)
                    .put("message", "Please log in."));
            return;
        }

        Long userId = getUserId(session);
        String role = (String) session.getAttribute("role");

        if (!"FREELANCER".equals(role)) {
            sendJson(response, 403,
                new JSONObject()
                    .put("success", false)
                    .put("message",
                         "Only freelancers can submit final delivery."));
            return;
        }

        long projectId;

        try {
            projectId = Long.parseLong(
                request.getParameter("projectId"));
        } catch (Exception e) {
            sendJson(response, 400,
                new JSONObject()
                    .put("success", false)
                    .put("message", "A valid projectId is required."));
            return;
        }

        String note = request.getParameter("submissionNote");
        String url = request.getParameter("deliveryUrl");

        if (note == null || note.trim().isEmpty()
                || note.trim().length() > 10000) {
            sendJson(response, 400,
                new JSONObject()
                    .put("success", false)
                    .put("message",
                         "Enter a delivery summary of 1 to 10000 characters."));
            return;
        }

        if (projectId <= 0 || !isValidHttpUrl(url)) {
            sendJson(response, 400,
                new JSONObject()
                    .put("success", false)
                    .put("message",
                         "Enter a valid project ID and HTTP/HTTPS URL."));
            return;
        }

        if (url != null && url.isBlank()) {
            url = null;
        } else if (url != null) {
            url = url.trim();
        }

        try (Connection con = getConnection()) {
            con.setAutoCommit(false);

            try {
                String projectSql = """
                    SELECT pr.status, pr.client_id, pr.title
                    FROM projects pr
                    INNER JOIN proposals p
                        ON p.project_id = pr.project_id
                    WHERE pr.project_id = ?
                      AND p.freelancer_id = ?
                      AND p.status = 'ACCEPTED'
                    FOR UPDATE
                    """;

                String projectStatus;
                long clientId;
                String projectTitle;

                try (PreparedStatement ps =
                         con.prepareStatement(projectSql)) {

                    ps.setLong(1, projectId);
                    ps.setLong(2, userId);

                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            con.rollback();

                            sendJson(response, 403,
                                new JSONObject()
                                    .put("success", false)
                                    .put("message",
                                         "You are not assigned to this project."));
                            return;
                        }

                        projectStatus = rs.getString("status");
                        clientId = rs.getLong("client_id");
                        projectTitle = rs.getString("title");
                    }
                }

                if (!"IN_PROGRESS".equals(projectStatus)) {
                    con.rollback();

                    sendJson(response, 409,
                        new JSONObject()
                            .put("success", false)
                            .put("message",
                                 "Delivery is allowed only while the project is in progress."));
                    return;
                }

                String latestSql = """
                    SELECT status
                    FROM project_submissions
                    WHERE project_id = ?
                    ORDER BY submission_id DESC
                    LIMIT 1
                    FOR UPDATE
                    """;

                try (PreparedStatement ps =
                         con.prepareStatement(latestSql)) {

                    ps.setLong(1, projectId);

                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()
                                && "SUBMITTED".equals(
                                    rs.getString("status"))) {

                            con.rollback();

                            sendJson(response, 409,
                                new JSONObject()
                                    .put("success", false)
                                    .put("message",
                                         "Your latest delivery is awaiting client review."));
                            return;
                        }
                    }
                }

                String insertSql = """
                    INSERT INTO project_submissions
                        (project_id, freelancer_id, submission_note,
                         delivery_url, status)
                    VALUES (?, ?, ?, ?, 'SUBMITTED')
                    """;

                long submissionId;

                try (PreparedStatement ps = con.prepareStatement(
                        insertSql, Statement.RETURN_GENERATED_KEYS)) {

                    ps.setLong(1, projectId);
                    ps.setLong(2, userId);
                    ps.setString(3, note.trim());

                    if (url == null) {
                        ps.setNull(4, java.sql.Types.VARCHAR);
                    } else {
                        ps.setString(4, url);
                    }

                    ps.executeUpdate();

                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        if (!keys.next()) {
                            throw new SQLException(
                                "Could not retrieve the submission ID.");
                        }

                        submissionId = keys.getLong(1);
                    }
                }

                NotificationUtil.create(con, clientId, projectId,
                        "DELIVERY_SUBMITTED", "Final delivery submitted",
                        "The freelancer submitted final work for “" + projectTitle + "”. Review the delivery in the project workspace.");

                con.commit();

                sendJson(response, 201,
                    new JSONObject()
                        .put("success", true)
                        .put("message",
                             "Final delivery submitted for client review.")
                        .put("submissionId", submissionId));

            } catch (SQLException | RuntimeException e) {
                try {
                    con.rollback();
                } catch (SQLException rollbackError) {
                    log("Failed to roll back delivery submission.",
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
            log("Unable to submit final delivery.", e);

            sendJson(response, 500,
                new JSONObject()
                    .put("success", false)
                    .put("message",
                         "Unable to submit final delivery."));
        }
    }
}
