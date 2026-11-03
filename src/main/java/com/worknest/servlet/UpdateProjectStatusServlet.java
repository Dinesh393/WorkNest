package com.worknest.servlet;

import com.worknest.util.DatabaseUtil;

import java.io.BufferedReader;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.json.JSONException;
import org.json.JSONObject;
import com.worknest.util.NotificationUtil;

public class UpdateProjectStatusServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;


    private static final Pattern PROJECT_ID_PATTERN =
            Pattern.compile("[1-9][0-9]*");

    private static final Set<String> ALLOWED_MANUAL_STATUSES =
            Set.of("CANCELLED");

    @Override
    protected void doPost(
            HttpServletRequest request,
            HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-store");


        HttpSession session = request.getSession(false);

        if (session == null || !(session.getAttribute("userId") instanceof Long)) {
            sendError(response, HttpServletResponse.SC_UNAUTHORIZED,
                    "Please log in to continue.");
            return;
        }

        if (!"CLIENT".equals(session.getAttribute("role"))) {
            sendError(response, HttpServletResponse.SC_FORBIDDEN,
                    "Only clients can update project status.");
            return;
        }

        long clientId = (Long) session.getAttribute("userId");

        StringBuilder requestBody = new StringBuilder();

        try (BufferedReader reader = request.getReader()) {
            String line;
            int totalCharacters = 0;

            while ((line = reader.readLine()) != null) {
                totalCharacters += line.length();

                if (totalCharacters > 5000) {
                    sendError(response, HttpServletResponse.SC_BAD_REQUEST,
                            "Request body is too large.");
                    return;
                }

                requestBody.append(line);
            }
        }

        final JSONObject json;

        try {
            json = new JSONObject(requestBody.toString());
        } catch (JSONException exception) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST,
                    "Invalid JSON request.");
            return;
        }

        String projectIdText = json.optString("projectId", "");
        String newStatus = json.optString("status", "");

        if (!PROJECT_ID_PATTERN.matcher(projectIdText).matches()) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST,
                    "Invalid project ID.");
            return;
        }

        if (!ALLOWED_MANUAL_STATUSES.contains(newStatus)) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST,
                    "Invalid project status.");
            return;
        }

        final long projectId;

        try {
            projectId = Long.parseLong(projectIdText);
        } catch (NumberFormatException exception) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST,
                    "Invalid project ID.");
            return;
        }

        String dbUsername = System.getenv("DB_USERNAME");
        String dbPassword = System.getenv("DB_PASSWORD");

        if (dbUsername == null || dbUsername.isBlank()
                || dbPassword == null) {
            sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Database configuration is unavailable.");
            return;
        }

        try (Connection connection = DatabaseUtil.getConnection()) {
            connection.setAutoCommit(false);
            try {
                String projectTitle;
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT title FROM projects WHERE project_id = ? "
                        + "AND client_id = ? AND status = 'OPEN' FOR UPDATE")) {
                    statement.setLong(1, projectId);
                    statement.setLong(2, clientId);
                    try (ResultSet rs = statement.executeQuery()) {
                        if (!rs.next()) {
                            connection.rollback();
                            sendError(response, HttpServletResponse.SC_CONFLICT,
                                    "The project was not updated. It may not belong to you, "
                                    + "may already be closed, or the requested transition "
                                    + "may not be allowed.");
                            return;
                        }
                        projectTitle = rs.getString("title");
                    }
                }

                List<Long> waitingFreelancers = new ArrayList<>();
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT freelancer_id FROM proposals WHERE project_id = ? "
                        + "AND status = 'PENDING' FOR UPDATE")) {
                    statement.setLong(1, projectId);
                    try (ResultSet rs = statement.executeQuery()) {
                        while (rs.next()) waitingFreelancers.add(rs.getLong("freelancer_id"));
                    }
                }

                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE projects SET status = 'CANCELLED' WHERE project_id = ? "
                        + "AND client_id = ? AND status = 'OPEN'")) {
                    statement.setLong(1, projectId);
                    statement.setLong(2, clientId);
                    if (statement.executeUpdate() != 1) {
                        throw new SQLException("Project status changed during cancellation.");
                    }
                }

                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE proposals SET status = 'REJECTED' WHERE project_id = ? "
                        + "AND status = 'PENDING'")) {
                    statement.setLong(1, projectId);
                    statement.executeUpdate();
                }

                List<Long> invitedFreelancers = new ArrayList<>();
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT freelancer_id FROM project_invitations WHERE project_id = ? "
                        + "AND status = 'PENDING' FOR UPDATE")) {
                    statement.setLong(1, projectId);
                    try (ResultSet rs = statement.executeQuery()) {
                        while (rs.next()) invitedFreelancers.add(rs.getLong("freelancer_id"));
                    }
                }
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE project_invitations SET status = 'CANCELLED' WHERE project_id = ? "
                        + "AND status = 'PENDING'")) {
                    statement.setLong(1, projectId);
                    statement.executeUpdate();
                }

                for (Long freelancerId : waitingFreelancers) {
                    NotificationUtil.create(connection, freelancerId, projectId,
                            "PROJECT_CANCELLED", "Project cancelled",
                            "The client cancelled “" + projectTitle + "”. You can continue exploring open projects.");
                }
                for (Long freelancerId : invitedFreelancers) {
                    if (waitingFreelancers.contains(freelancerId)) continue;
                    NotificationUtil.create(connection, freelancerId, projectId,
                            "PROJECT_INVITATION_CANCELLED", "Project invitation closed",
                            "The client cancelled “" + projectTitle + "”.");
                }

                connection.commit();

                JSONObject result = new JSONObject();
                result.put("message", "Project status updated successfully.");
                result.put("projectId", projectId);
                result.put("status", newStatus);
                response.setStatus(HttpServletResponse.SC_OK);
                response.getWriter().write(result.toString());
            } catch (SQLException | RuntimeException exception) {
                try { connection.rollback(); }
                catch (SQLException rollbackException) { exception.addSuppressed(rollbackException); }
                throw exception;
            } finally {
                try { connection.setAutoCommit(true); }
                catch (SQLException exception) { getServletContext().log("Unable to restore auto-commit.", exception); }
            }

        } catch (SQLException exception) {
            getServletContext().log(
                    "Database error while updating project status.", exception);

            sendError(response,
                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "A database error occurred. Please try again.");
        }
    }

    private void sendError(
            HttpServletResponse response,
            int statusCode,
            String message) throws IOException {

        response.setStatus(statusCode);

        JSONObject error = new JSONObject();
        error.put("error", message);

        response.getWriter().write(error.toString());
    }

    
    private int defaultPort(String scheme) {
        if ("https".equalsIgnoreCase(scheme)) return 443;
        if ("http".equalsIgnoreCase(scheme)) return 80;
        return -1;
    }
}
