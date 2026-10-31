
package com.worknest.servlet;

import java.io.BufferedReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;

public class UpdateProjectServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    private static final String DB_URL =
            "jdbc:mysql://localhost:3306/worknest_db";

    private static final String UPDATE_SQL =
            "UPDATE projects SET title = ?, description = ?, "
          + "required_skills = ?, project_type = ?, budget_min = ?, "
          + "budget_max = ?, deadline = ? "
          + "WHERE project_id = ? AND client_id = ? "
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

        // 1. Verify session.
        HttpSession session = req.getSession(false);

        if (session == null
                || !(session.getAttribute("userId") instanceof Long)) {
            sendError(res, HttpServletResponse.SC_UNAUTHORIZED,
                    "Your session has expired. Please log in again.");
            return;
        }

        // 2. Only clients may edit projects.
        if (!"CLIENT".equals(session.getAttribute("role"))) {
            sendError(res, HttpServletResponse.SC_FORBIDDEN,
                    "Only clients can edit projects.");
            return;
        }

        long clientId = (Long) session.getAttribute("userId");

        // 3. Read JSON request body with a size limit.
        StringBuilder body = new StringBuilder();

        try (BufferedReader reader = req.getReader()) {
            char[] buffer = new char[2048];
            int count;

            while ((count = reader.read(buffer)) != -1) {
                body.append(buffer, 0, count);

                if (body.length() > 20000) {
                    sendError(res, HttpServletResponse.SC_BAD_REQUEST,
                            "Request body is too large.");
                    return;
                }
            }
        }

        final JSONObject data;

        try {
            data = new JSONObject(body.toString());
        } catch (JSONException e) {
            sendError(res, HttpServletResponse.SC_BAD_REQUEST,
                    "Invalid JSON request.");
            return;
        }

        // 4. Validate project ID.
        String projectIdText = data.optString("projectId", "");

        if (!POSITIVE_ID.matcher(projectIdText).matches()) {
            sendError(res, HttpServletResponse.SC_BAD_REQUEST,
                    "Invalid project ID.");
            return;
        }

        final long projectId;

        try {
            projectId = Long.parseLong(projectIdText);
        } catch (NumberFormatException e) {
            sendError(res, HttpServletResponse.SC_BAD_REQUEST,
                    "Invalid project ID.");
            return;
        }

        // 5. Read and validate fields.
        String title = data.optString("title", "").trim();
        String description = data.optString("description", "").trim();
        String skills = data.optString("requiredSkills", "").trim();
        String projectType = data.optString("projectType", "").trim();
        String minText = data.optString("budgetMin", "");
        String maxText = data.optString("budgetMax", "");
        String deadlineText = data.optString("deadline", "");

        if (title.isEmpty() || title.length() > 150) {
            sendError(res, HttpServletResponse.SC_BAD_REQUEST,
                    "Title is required and must not exceed 150 characters.");
            return;
        }

        if (description.isEmpty()) {
            sendError(res, HttpServletResponse.SC_BAD_REQUEST,
                    "Project description is required.");
            return;
        }

        if (skills.isEmpty() || skills.length() > 500) {
            sendError(res, HttpServletResponse.SC_BAD_REQUEST,
                    "Required skills are required and must not exceed 500 characters.");
            return;
        }

        if (!"FIXED".equals(projectType)
                && !"HOURLY".equals(projectType)) {
            sendError(res, HttpServletResponse.SC_BAD_REQUEST,
                    "Invalid project type.");
            return;
        }

        final BigDecimal budgetMin;
        final BigDecimal budgetMax;

        try {
            budgetMin = new BigDecimal(minText);
            budgetMax = new BigDecimal(maxText);

            if (budgetMin.signum() <= 0
                    || budgetMax.compareTo(budgetMin) < 0
                    || budgetMin.precision() > 12
                    || budgetMax.precision() > 12
                    || Math.max(budgetMin.scale(), 0) > 2
                    || Math.max(budgetMax.scale(), 0) > 2) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException e) {
            sendError(res, HttpServletResponse.SC_BAD_REQUEST,
                    "Enter valid budgets. Maximum must be at least minimum.");
            return;
        }

        final LocalDate deadline;

        try {
            deadline = LocalDate.parse(deadlineText);

            if (deadline.isBefore(LocalDate.now())) {
                sendError(res, HttpServletResponse.SC_BAD_REQUEST,
                        "The deadline cannot be in the past.");
                return;
            }
        } catch (DateTimeParseException e) {
            sendError(res, HttpServletResponse.SC_BAD_REQUEST,
                    "Enter a valid project deadline.");
            return;
        }

        // 6. Read database credentials from environment.
        String dbUsername = System.getenv("DB_USERNAME");
        String dbPassword = System.getenv("DB_PASSWORD");

        if (dbUsername == null || dbUsername.isBlank()
                || dbPassword == null || dbPassword.isEmpty()) {
            log("Database credentials are not configured.");
            sendError(res, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Unable to update project right now.");
            return;
        }

        // 7. Update only a project owned by this client.
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");

            try (Connection connection = DriverManager.getConnection(
                        DB_URL, dbUsername, dbPassword);
                 PreparedStatement statement =
                        connection.prepareStatement(UPDATE_SQL)) {

                statement.setString(1, title);
                statement.setString(2, description);
                statement.setString(3, skills);
                statement.setString(4, projectType);
                statement.setBigDecimal(5, budgetMin);
                statement.setBigDecimal(6, budgetMax);
                statement.setDate(7, java.sql.Date.valueOf(deadline));
                statement.setLong(8, projectId);
                statement.setLong(9, clientId);

                int rowsUpdated = statement.executeUpdate();

                if (rowsUpdated == 0) {
                    sendError(res, HttpServletResponse.SC_NOT_FOUND,
                            "Project not found, no longer open, or you don't have access to it.");
                    return;
                }

                res.setStatus(HttpServletResponse.SC_OK);
                res.getWriter().write(
                        "{\"message\":\"Project updated successfully.\"}");
            }

        } catch (ClassNotFoundException | SQLException e) {
            log("Error updating project.", e);
            if (!res.isCommitted()) {
                sendError(res,
                        HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                        "Something went wrong while updating the project.");
            }
        }
    }

    private void sendError(
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
}
