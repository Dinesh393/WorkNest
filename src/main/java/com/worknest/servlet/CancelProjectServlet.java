
package com.worknest.servlet;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.json.JSONException;
import org.json.JSONObject;
import com.worknest.util.NotificationUtil;

public class CancelProjectServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    private static final String DB_URL =
            "jdbc:mysql://localhost:3306/worknest_db";

    private static final String DELETE_PROJECT_SQL =
            "DELETE FROM projects "
          + "WHERE project_id = ? "
          + "AND client_id = ? "
          + "AND status = 'OPEN'";

    private static final Pattern POSITIVE_ID =
            Pattern.compile("[1-9][0-9]*");

    @Override
    protected void doPost(
            HttpServletRequest req,
            HttpServletResponse res) throws IOException {

        res.setContentType("application/json");
        res.setCharacterEncoding("UTF-8");
        res.setHeader("Cache-Control", "no-store");

        if (!isSameOriginRequest(req)) {
            sendJson(res, HttpServletResponse.SC_FORBIDDEN,
                    "This request could not be verified.");
            return;
        }

        // 1. Verify the existing session.
        HttpSession session = req.getSession(false);

        if (session == null || !(session.getAttribute("userId") instanceof Long)) {
            sendJson(res, HttpServletResponse.SC_UNAUTHORIZED, "Your session has expired. Please log in again.");
            return;
        }

        // 2. Only clients can delete their projects.
        if (!"CLIENT".equals(session.getAttribute("role"))) {

            sendJson(res, HttpServletResponse.SC_FORBIDDEN,
                    "Only clients can delete projects.");
            return;
        }

        long clientId = (Long) session.getAttribute("userId");

        // 3. Read the JSON request body with a size limit.
        StringBuilder body = new StringBuilder();

        try (BufferedReader reader = req.getReader()) {
            char[] buffer = new char[1024];
            int count;

            while ((count = reader.read(buffer)) != -1) {
                body.append(buffer, 0, count);

                if (body.length() > 5000) {
                    sendJson(res, HttpServletResponse.SC_BAD_REQUEST,
                            "Request body is too large.");
                    return;
                }
            }
        }

        // 4. Parse the JSON.
        final JSONObject data;

        try {
            data = new JSONObject(body.toString());
        } catch (JSONException e) {
            sendJson(res, HttpServletResponse.SC_BAD_REQUEST,
                    "Invalid JSON request.");
            return;
        }

        // 5. Validate the project ID.
        String projectIdText = data.optString("projectId", "");

        if (!POSITIVE_ID.matcher(projectIdText).matches()) {
            sendJson(res, HttpServletResponse.SC_BAD_REQUEST,
                    "Invalid project ID.");
            return;
        }

        final long projectId;

        try {
            projectId = Long.parseLong(projectIdText);
        } catch (NumberFormatException e) {
            sendJson(res, HttpServletResponse.SC_BAD_REQUEST,
                    "Invalid project ID.");
            return;
        }

        // 6. Read database credentials securely.
        String dbUsername = System.getenv("DB_USERNAME");
        String dbPassword = System.getenv("DB_PASSWORD");

        if (dbUsername == null || dbUsername.isBlank()
                || dbPassword == null || dbPassword.isEmpty()) {

            log("Database credentials are not configured.");

            sendJson(res, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Unable to delete the project right now.");
            return;
        }

        // 7. Delete only an OPEN project owned by this client.
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");

            try (Connection connection = DriverManager.getConnection(
                        DB_URL, dbUsername, dbPassword)) {
                connection.setAutoCommit(false);
                try {
                    try (PreparedStatement statement = connection.prepareStatement(
                            "SELECT project_id FROM projects WHERE project_id = ? "
                            + "AND client_id = ? AND status = 'OPEN' FOR UPDATE")) {
                        statement.setLong(1, projectId);
                        statement.setLong(2, clientId);
                        try (java.sql.ResultSet result = statement.executeQuery()) {
                            if (!result.next()) {
                                connection.rollback();
                                sendJson(res, HttpServletResponse.SC_NOT_FOUND,
                                        "Project not found, already deleted, "
                                      + "not open, or you don't have access to it.");
                                return;
                            }
                        }
                    }
                    List<Long> invitedFreelancers = new ArrayList<>();
                    try (PreparedStatement statement = connection.prepareStatement(
                            "SELECT freelancer_id FROM project_invitations WHERE project_id = ? "
                            + "AND status = 'PENDING' FOR UPDATE")) {
                        statement.setLong(1, projectId);
                        try (java.sql.ResultSet result = statement.executeQuery()) {
                            while (result.next()) invitedFreelancers.add(result.getLong("freelancer_id"));
                        }
                    }
                    try (PreparedStatement invitations = connection.prepareStatement(
                            "DELETE FROM project_invitations WHERE project_id = ?")) {
                        invitations.setLong(1, projectId);
                        invitations.executeUpdate();
                    }
                    int rowsDeleted;
                    try (PreparedStatement statement = connection.prepareStatement(DELETE_PROJECT_SQL)) {
                        statement.setLong(1, projectId);
                        statement.setLong(2, clientId);
                        rowsDeleted = statement.executeUpdate();
                    }
                    if (rowsDeleted != 1) throw new SQLException("Project could not be deleted.");
                    for (Long freelancerId : invitedFreelancers) {
                        NotificationUtil.create(connection, freelancerId, 0,
                                "PROJECT_INVITATION_CANCELLED", "Project invitation withdrawn",
                                "The client removed a project before you responded to the invitation.");
                    }
                    connection.commit();

                    res.setStatus(HttpServletResponse.SC_OK);
                    JSONObject success = new JSONObject();
                    success.put("message", "Project deleted successfully.");
                    res.getWriter().write(success.toString());
                } catch (SQLException exception) {
                    try { connection.rollback(); }
                    catch (SQLException rollbackError) { exception.addSuppressed(rollbackError); }
                    throw exception;
                } finally {
                    try { connection.setAutoCommit(true); }
                    catch (SQLException exception) { log("Unable to restore auto-commit.", exception); }
                }
            }

        } catch (ClassNotFoundException | SQLException e) {

            log("Error deleting project.", e);

            if (!res.isCommitted()) {
                sendJson(res,
                        HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                        "Something went wrong while deleting the project.");
            }
        }
    }

    private void sendJson(
            HttpServletResponse res,
            int status,
            String message) throws IOException {

        res.setStatus(status);
        res.setContentType("application/json");
        res.setCharacterEncoding("UTF-8");
        res.setHeader("Cache-Control", "no-store");

        JSONObject error = new JSONObject();
        error.put("error", message);

        res.getWriter().write(error.toString());
    }

    private boolean isSameOriginRequest(HttpServletRequest request) {
        String suppliedOrigin = request.getHeader("Origin");
        if (suppliedOrigin == null || suppliedOrigin.isBlank()) {
            suppliedOrigin = request.getHeader("Referer");
        }
        if (suppliedOrigin == null || suppliedOrigin.isBlank()
                || "null".equalsIgnoreCase(suppliedOrigin)) {
            return false;
        }

        try {
            URI supplied = new URI(suppliedOrigin);
            String scheme = supplied.getScheme();
            if (scheme == null || supplied.getHost() == null
                    || supplied.getUserInfo() != null) {
                return false;
            }

            int suppliedPort = supplied.getPort() < 0
                    ? defaultPort(scheme) : supplied.getPort();
            int requestPort = request.getServerPort() < 0
                    ? defaultPort(request.getScheme()) : request.getServerPort();

            return scheme.equalsIgnoreCase(request.getScheme())
                    && supplied.getHost().equalsIgnoreCase(request.getServerName())
                    && suppliedPort == requestPort;
        } catch (URISyntaxException exception) {
            return false;
        }
    }

    private int defaultPort(String scheme) {
        if ("https".equalsIgnoreCase(scheme)) return 443;
        if ("http".equalsIgnoreCase(scheme)) return 80;
        return -1;
    }
}
