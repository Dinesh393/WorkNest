
package com.worknest.servlet;

import com.worknest.util.DatabaseUtil;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.json.JSONArray;
import org.json.JSONObject;

public class ProjectUpdatesServlet extends HttpServlet {

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

    private JSONObject readUpdates(
            Connection con,
            long projectId) throws SQLException {

        String sql = """
            SELECT pu.update_id,
                   pu.project_id,
                   pu.freelancer_id,
                   pu.update_note,
                   pu.created_at,
                   u.full_name AS freelancer_name
            FROM project_updates pu
            INNER JOIN users u
                ON u.user_id = pu.freelancer_id
            WHERE pu.project_id = ?
            ORDER BY pu.created_at DESC, pu.update_id DESC
            """;

        JSONArray updates = new JSONArray();

        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, projectId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    JSONObject item = new JSONObject();

                    item.put("updateId", rs.getLong("update_id"));
                    item.put("projectId", rs.getLong("project_id"));
                    item.put("freelancerId",
                             rs.getLong("freelancer_id"));
                    item.put("updateNote",
                             rs.getString("update_note"));
                    item.put("freelancerName",
                             rs.getString("freelancer_name"));

                    Timestamp createdAt =
                        rs.getTimestamp("created_at");

                    item.put(
                        "createdAt",
                        createdAt == null
                            ? JSONObject.NULL
                            : createdAt.toString()
                    );

                    updates.put(item);
                }
            }
        }

        JSONObject result = new JSONObject();
        result.put("success", true);
        result.put("updates", updates);

        return result;
    }

    @Override
    protected void doGet(
            HttpServletRequest request,
            HttpServletResponse response)
            throws ServletException, IOException {

        HttpSession session = request.getSession(false);

        if (session == null || getUserId(session) == null) {
            sendJson(response, HttpServletResponse.SC_UNAUTHORIZED,
                new JSONObject().put("success", false)
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
            sendJson(response, HttpServletResponse.SC_BAD_REQUEST,
                new JSONObject().put("success", false)
                    .put("message", "A valid projectId is required."));
            return;
        }

        if (projectId <= 0) {
            sendJson(response, HttpServletResponse.SC_BAD_REQUEST,
                new JSONObject().put("success", false)
                    .put("message", "Invalid projectId."));
            return;
        }

        try (Connection con = getConnection()) {
            if (!hasAccess(con, projectId, userId, role)) {
                sendJson(response, HttpServletResponse.SC_FORBIDDEN,
                    new JSONObject().put("success", false)
                        .put("message",
                              "You cannot access these project updates."));
                return;
            }

            sendJson(response, HttpServletResponse.SC_OK,
                     readUpdates(con, projectId));

        } catch (SQLException e) {
            log("Unable to retrieve project updates.", e);

            sendJson(response,
                HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                new JSONObject().put("success", false)
                    .put("message",
                         "Unable to retrieve project updates."));
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
            sendJson(response, HttpServletResponse.SC_UNAUTHORIZED,
                new JSONObject().put("success", false)
                    .put("message", "Please log in."));
            return;
        }

        Long userId = getUserId(session);
        String role = (String) session.getAttribute("role");

        if (!"FREELANCER".equals(role)) {
            sendJson(response, HttpServletResponse.SC_FORBIDDEN,
                new JSONObject().put("success", false)
                    .put("message",
                         "Only freelancers can post updates."));
            return;
        }

        long projectId;

        try {
            projectId = Long.parseLong(
                request.getParameter("projectId"));
        } catch (Exception e) {
            sendJson(response, HttpServletResponse.SC_BAD_REQUEST,
                new JSONObject().put("success", false)
                    .put("message", "A valid projectId is required."));
            return;
        }

        String note = request.getParameter("updateNote");

        if (projectId <= 0 || note == null
                || note.trim().isEmpty()
                || note.trim().length() > 10000) {

            sendJson(response, HttpServletResponse.SC_BAD_REQUEST,
                new JSONObject().put("success", false)
                    .put("message",
                         "Enter an update between 1 and 10000 characters."));
            return;
        }

        try (Connection con = getConnection()) {
            con.setAutoCommit(false);

            try {
                String projectSql = """
                    SELECT pr.status
                    FROM projects pr
                    INNER JOIN proposals p
                        ON p.project_id = pr.project_id
                    WHERE pr.project_id = ?
                      AND p.freelancer_id = ?
                      AND p.status = 'ACCEPTED'
                    FOR UPDATE
                    """;

                String projectStatus;

                try (PreparedStatement ps =
                         con.prepareStatement(projectSql)) {

                    ps.setLong(1, projectId);
                    ps.setLong(2, userId);

                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            con.rollback();

                            sendJson(response,
                                HttpServletResponse.SC_FORBIDDEN,
                                new JSONObject()
                                    .put("success", false)
                                    .put("message",
                                         "You are not assigned to this project."));
                            return;
                        }

                        projectStatus = rs.getString("status");
                    }
                }

                if (!"IN_PROGRESS".equals(projectStatus)) {
                    con.rollback();

                    sendJson(response,
                        HttpServletResponse.SC_CONFLICT,
                        new JSONObject().put("success", false)
                            .put("message",
                                 "Updates are allowed only for projects in progress."));
                    return;
                }

                String insertSql = """
                    INSERT INTO project_updates
                        (project_id, freelancer_id, update_note)
                    VALUES (?, ?, ?)
                    """;

                long updateId;

                try (PreparedStatement ps = con.prepareStatement(
                        insertSql, Statement.RETURN_GENERATED_KEYS)) {

                    ps.setLong(1, projectId);
                    ps.setLong(2, userId);
                    ps.setString(3, note.trim());

                    ps.executeUpdate();

                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        if (!keys.next()) {
                            throw new SQLException(
                                "Could not retrieve the update ID.");
                        }

                        updateId = keys.getLong(1);
                    }
                }

                con.commit();

                JSONObject result = new JSONObject();
                result.put("success", true);
                result.put("message", "Progress update posted.");
                result.put("updateId", updateId);

                sendJson(response, HttpServletResponse.SC_CREATED,
                         result);

            } catch (Exception e) {
                try {
                    con.rollback();
                } catch (SQLException rollbackError) {
                    log("Failed to roll back progress update.", rollbackError);
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
            log("Unable to save project update.", e);

            sendJson(response,
                HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                new JSONObject().put("success", false)
                    .put("message", "Unable to save the update."));
        }
    }
}
