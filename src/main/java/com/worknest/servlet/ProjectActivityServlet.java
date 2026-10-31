
package com.worknest.servlet;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.json.JSONArray;
import org.json.JSONObject;

public class ProjectActivityServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    private Connection getConnection() throws SQLException {
        String username = System.getenv("DB_USERNAME");
        String password = System.getenv("DB_PASSWORD");
        if (username == null || password == null) {
            throw new SQLException("Database configuration is missing.");
        }
        return DriverManager.getConnection(
            "jdbc:mysql://localhost:3306/worknest_db",
            username,
            password
        );
    }

    private void sendJson(HttpServletResponse response, int status, JSONObject body)
            throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-store");
        response.getWriter().write(body.toString());
    }

    private Long getUserId(HttpSession session) {
        Object value = session.getAttribute("userId");
        return value instanceof Number
            ? ((Number) value).longValue()
            : null;
    }

    private boolean hasAccess(Connection con, long projectId, long userId, String role)
            throws SQLException {
        String sql;

        if ("CLIENT".equals(role)) {
            sql = """
                SELECT project_id
                FROM projects
                WHERE project_id = ? AND client_id = ?
                """;
        } else if ("FREELANCER".equals(role)) {
            sql = """
                SELECT pr.project_id
                FROM projects pr
                INNER JOIN proposals p ON p.project_id = pr.project_id
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

    private void addActivity(
            List<Activity> activities,
            String type,
            String title,
            String description,
            String actor,
            Timestamp timestamp) {

        if (timestamp == null) {
            return;
        }

        JSONObject item = new JSONObject();
        item.put("type", type);
        item.put("title", title);
        item.put("description", description == null ? "" : description);
        item.put("actor", actor == null ? "WorkNest" : actor);
        item.put("createdAt", timestamp.toString());

        activities.add(new Activity(timestamp.getTime(), item));
    }

    private static class Activity {
        private final long time;
        private final JSONObject data;

        private Activity(long time, JSONObject data) {
            this.time = time;
            this.data = data;
        }
    }

    private void loadProposalActivity(
            Connection con, long projectId, List<Activity> activities)
            throws SQLException {

        String sql = """
            SELECT p.updated_at, u.full_name
            FROM proposals p
            INNER JOIN users u ON u.user_id = p.freelancer_id
            WHERE p.project_id = ?
              AND p.status = 'ACCEPTED'
            ORDER BY p.updated_at ASC
            LIMIT 1
            """;

        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, projectId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    addActivity(
                        activities,
                        "PROJECT_STARTED",
                        "Proposal accepted — project started",
                        "The client accepted the freelancer's proposal.",
                        rs.getString("full_name"),
                        rs.getTimestamp("updated_at")
                    );
                }
            }
        }
    }

    private void loadProgressActivity(
            Connection con, long projectId, List<Activity> activities)
            throws SQLException {

        String sql = """
            SELECT pu.update_note, pu.created_at, u.full_name
            FROM project_updates pu
            INNER JOIN users u ON u.user_id = pu.freelancer_id
            WHERE pu.project_id = ?
            ORDER BY pu.created_at ASC, pu.update_id ASC
            """;

        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, projectId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    addActivity(
                        activities,
                        "PROGRESS_UPDATE",
                        "Progress update",
                        rs.getString("update_note"),
                        rs.getString("full_name"),
                        rs.getTimestamp("created_at")
                    );
                }
            }
        }
    }

    private void loadSubmissionActivity(
            Connection con, long projectId, List<Activity> activities)
            throws SQLException {

        String sql = """
            SELECT ps.submission_id,
                   ps.submission_note,
                   ps.status,
                   ps.client_feedback,
                   ps.submitted_at,
                   ps.reviewed_at,
                   u.full_name
            FROM project_submissions ps
            INNER JOIN users u ON u.user_id = ps.freelancer_id
            WHERE ps.project_id = ?
            ORDER BY ps.submission_id ASC
            """;

        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, projectId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String status = rs.getString("status");
                    String freelancerName = rs.getString("full_name");

                    addActivity(
                        activities,
                        "DELIVERY_SUBMITTED",
                        "Final delivery submitted",
                        rs.getString("submission_note"),
                        freelancerName,
                        rs.getTimestamp("submitted_at")
                    );

                    Timestamp reviewedAt = rs.getTimestamp("reviewed_at");

                    if ("REVISION_REQUESTED".equals(status)) {
                        addActivity(
                            activities,
                            "REVISION_REQUESTED",
                            "Revision requested",
                            rs.getString("client_feedback"),
                            "Client",
                            reviewedAt
                        );
                    } else if ("APPROVED".equals(status)) {
                        addActivity(
                            activities,
                            "DELIVERY_APPROVED",
                            "Project approved",
                            "The client approved the final delivery.",
                            "Client",
                            reviewedAt
                        );
                    }
                }
            }
        }
    }

    private void loadProjectCompletion(
            Connection con, long projectId, List<Activity> activities)
            throws SQLException {

        String sql = """
            SELECT status, updated_at
            FROM projects
            WHERE project_id = ?
            """;

        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, projectId);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next() && "COMPLETED".equals(rs.getString("status"))) {
                    addActivity(
                        activities,
                        "PROJECT_COMPLETED",
                        "Project completed",
                        "The project has been marked as completed.",
                        "WorkNest",
                        rs.getTimestamp("updated_at")
                    );
                }
            }
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

        long userId = getUserId(session);
        String role = (String) session.getAttribute("role");

        long projectId;

        try {
            projectId = Long.parseLong(request.getParameter("projectId"));
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
                        .put("message", "You cannot access this project."));
                return;
            }

            List<Activity> activities = new ArrayList<>();

            loadProposalActivity(con, projectId, activities);
            loadProgressActivity(con, projectId, activities);
            loadSubmissionActivity(con, projectId, activities);
            loadProjectCompletion(con, projectId, activities);

            activities.sort(Comparator.comparingLong(item -> item.time));

            JSONArray result = new JSONArray();

            for (Activity activity : activities) {
                result.put(activity.data);
            }

            sendJson(response, 200,
                new JSONObject()
                    .put("success", true)
                    .put("activities", result));

        } catch (SQLException e) {
            log("Unable to retrieve project activity.", e);

            sendJson(response, 500,
                new JSONObject()
                    .put("success", false)
                    .put("message", "Unable to retrieve project activity."));
        }
    }
}
