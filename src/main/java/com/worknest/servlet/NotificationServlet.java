package com.worknest.servlet;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class NotificationServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final String DB_URL = "jdbc:mysql://localhost:3306/worknest_db";
    private static final int MAX_BODY_CHARS = 1024;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        prepareJson(response);
        HttpSession session = request.getSession(false);
        Long userId = getUserId(session);
        String role = session == null ? null : (String) session.getAttribute("role");
        if (userId == null) {
            sendError(response, HttpServletResponse.SC_UNAUTHORIZED, "Please log in to view notifications.");
            return;
        }
        if (!isSupportedRole(role)) {
            sendError(response, HttpServletResponse.SC_FORBIDDEN, "This account cannot view notifications.");
            return;
        }

        try (Connection connection = getConnection()) {
            int unreadCount;
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT COUNT(*) FROM notifications WHERE user_id = ? AND read_at IS NULL")) {
                statement.setLong(1, userId);
                try (ResultSet rs = statement.executeQuery()) {
                    rs.next();
                    unreadCount = rs.getInt(1);
                }
            }

            JSONArray items = new JSONArray();
            String sql = "SELECT notification_id, project_id, type, title, message, "
                    + "created_at, read_at FROM notifications WHERE user_id = ? "
                    + "ORDER BY created_at DESC, notification_id DESC LIMIT 25";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setLong(1, userId);
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) {
                        JSONObject item = new JSONObject();
                        item.put("id", rs.getLong("notification_id"));
                        long projectId = rs.getLong("project_id");
                        item.put("projectId", rs.wasNull() ? JSONObject.NULL : projectId);
                        String type = rs.getString("type");
                        item.put("type", type);
                        item.put("title", rs.getString("title"));
                        item.put("message", rs.getString("message"));
                        item.put("createdAt", rs.getTimestamp("created_at").toInstant().toString());
                        item.put("read", rs.getTimestamp("read_at") != null);
                        item.put("href", buildHref(request.getContextPath(), role, type, projectId));
                        items.put(item);
                    }
                }
            }

            JSONObject result = new JSONObject();
            result.put("unreadCount", unreadCount);
            result.put("notifications", items);
            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().write(result.toString());
        } catch (SQLException exception) {
            getServletContext().log("Unable to load notifications.", exception);
            sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Notifications are unavailable right now.");
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        prepareJson(response);
        if (!isSameOriginRequest(request)) {
            sendError(response, HttpServletResponse.SC_FORBIDDEN, "This request could not be verified.");
            return;
        }
        HttpSession session = request.getSession(false);
        Long userId = getUserId(session);
        String role = session == null ? null : (String) session.getAttribute("role");
        if (userId == null) {
            sendError(response, HttpServletResponse.SC_UNAUTHORIZED, "Please log in to update notifications.");
            return;
        }
        if (!isSupportedRole(role)) {
            sendError(response, HttpServletResponse.SC_FORBIDDEN, "This account cannot update notifications.");
            return;
        }

        JSONObject body;
        try {
            body = readBody(request);
        } catch (IOException | JSONException | IllegalArgumentException exception) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST, "Invalid notification request.");
            return;
        }

        String action = body.optString("action", "");
        String sql;
        boolean markOne = "mark-read".equals(action);
        if (markOne) {
            long notificationId = body.optLong("notificationId", 0L);
            if (notificationId <= 0) {
                sendError(response, HttpServletResponse.SC_BAD_REQUEST, "Invalid notification ID.");
                return;
            }
            sql = "UPDATE notifications SET read_at = COALESCE(read_at, CURRENT_TIMESTAMP) "
                    + "WHERE notification_id = ? AND user_id = ?";
        } else if ("mark-all-read".equals(action)) {
            sql = "UPDATE notifications SET read_at = CURRENT_TIMESTAMP "
                    + "WHERE user_id = ? AND read_at IS NULL";
        } else {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST, "Unknown notification action.");
            return;
        }

        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            if (markOne) {
                statement.setLong(1, body.optLong("notificationId"));
                statement.setLong(2, userId);
            } else {
                statement.setLong(1, userId);
            }
            statement.executeUpdate();
            JSONObject result = new JSONObject();
            result.put("success", true);
            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().write(result.toString());
        } catch (SQLException exception) {
            getServletContext().log("Unable to update notification state.", exception);
            sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Unable to update notifications right now.");
        }
    }

    private JSONObject readBody(HttpServletRequest request)
            throws IOException, JSONException {
        StringBuilder body = new StringBuilder();
        char[] buffer = new char[512];
        int count;
        try (BufferedReader reader = request.getReader()) {
            while ((count = reader.read(buffer)) != -1) {
                if (body.length() + count > MAX_BODY_CHARS) {
                    throw new IllegalArgumentException("Request too large.");
                }
                body.append(buffer, 0, count);
            }
        }
        return new JSONObject(body.toString());
    }

    private Connection getConnection() throws SQLException {
        String username = System.getenv("DB_USERNAME");
        String password = System.getenv("DB_PASSWORD");
        if (username == null || username.isBlank() || password == null || password.isEmpty()) {
            throw new SQLException("Database credentials are not configured.");
        }
        return DriverManager.getConnection(DB_URL, username, password);
    }

    private Long getUserId(HttpSession session) {
        if (session == null) return null;
        Object value = session.getAttribute("userId");
        return value instanceof Long ? (Long) value : null;
    }

    private boolean isSupportedRole(String role) {
        return "CLIENT".equals(role) || "FREELANCER".equals(role);
    }

    private String buildHref(String contextPath, String role, String type, long projectId) {
        String id = projectId > 0 ? Long.toString(projectId) : "";
        if ("CLIENT".equals(role)) {
            if ("PROPOSAL_RECEIVED".equals(type)) {
                return contextPath + "/dashboard/client/project-proposals.html?projectId=" + id;
            }
            if ("DELIVERY_SUBMITTED".equals(type)
                    || "PROJECT_INVITATION_ACCEPTED".equals(type)) {
                return contextPath + "/dashboard/client/project-workspace.html?projectId=" + id;
            }
            return contextPath + "/dashboard/client/index.html";
        }
        if ("PROJECT_INVITATION".equals(type)) {
            return contextPath + "/dashboard/freelancer/my-proposals.html";
        }
        if ("PROPOSAL_ACCEPTED".equals(type) || "PROPOSAL_NOT_SELECTED".equals(type)) {
            return contextPath + "/dashboard/freelancer/my-proposals.html";
        }
        if ("REVISION_REQUESTED".equals(type) || "DELIVERY_APPROVED".equals(type)) {
            return contextPath + "/dashboard/freelancer/project-workspace.html?projectId=" + id;
        }
        return contextPath + "/dashboard/freelancer/my-projects.html";
    }

    private boolean isSameOriginRequest(HttpServletRequest request) {
        String supplied = request.getHeader("Origin");
        if (supplied == null || supplied.isBlank()) supplied = request.getHeader("Referer");
        if (supplied == null || supplied.isBlank() || "null".equalsIgnoreCase(supplied)) return false;
        try {
            URI uri = new URI(supplied);
            if (uri.getScheme() == null || uri.getHost() == null || uri.getUserInfo() != null) return false;
            int sourcePort = uri.getPort() < 0 ? defaultPort(uri.getScheme()) : uri.getPort();
            int targetPort = request.getServerPort() < 0
                    ? defaultPort(request.getScheme()) : request.getServerPort();
            return uri.getScheme().equalsIgnoreCase(request.getScheme())
                    && uri.getHost().equalsIgnoreCase(request.getServerName())
                    && sourcePort == targetPort;
        } catch (URISyntaxException exception) {
            return false;
        }
    }

    private int defaultPort(String scheme) {
        if ("https".equalsIgnoreCase(scheme)) return 443;
        if ("http".equalsIgnoreCase(scheme)) return 80;
        return -1;
    }

    private void prepareJson(HttpServletResponse response) {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-store");
    }

    private void sendError(HttpServletResponse response, int status, String message)
            throws IOException {
        JSONObject error = new JSONObject();
        error.put("error", message);
        response.setStatus(status);
        response.getWriter().write(error.toString());
    }
}
