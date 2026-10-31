
package com.worknest.servlet;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.json.JSONArray;
import org.json.JSONObject;

public class OpenProjectsServlet extends HttpServlet {

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

    @Override
    protected void doGet(
            HttpServletRequest request,
            HttpServletResponse response)
            throws ServletException, IOException {

        response.setHeader("Cache-Control", "no-store");

        HttpSession session = request.getSession(false);

        if (session == null || !(session.getAttribute("userId") instanceof Long)) {
            sendJson(response, HttpServletResponse.SC_UNAUTHORIZED,
                new JSONObject().put("error", "Please log in."));
            return;
        }

        String role = (String) session.getAttribute("role");

        if (!"FREELANCER".equals(role)) {
            sendJson(response, HttpServletResponse.SC_FORBIDDEN,
                new JSONObject().put("error",
                    "Only freelancers can browse available projects."));
            return;
        }

        String sql =
            "SELECT project_id, title, description, required_skills, " +
            "project_type, budget_min, budget_max, deadline, status " +
            "FROM projects " +
            "WHERE status = 'OPEN' " +
            "ORDER BY created_at DESC";

        JSONArray projects = new JSONArray();

        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {

            while (result.next()) {
                JSONObject project = new JSONObject();

                project.put("projectId", result.getLong("project_id"));
                project.put("title", result.getString("title"));
                project.put("description", result.getString("description"));
                project.put("requiredSkills",
                    result.getString("required_skills"));
                project.put("projectType", result.getString("project_type"));

                BigDecimal budgetMin = result.getBigDecimal("budget_min");
                BigDecimal budgetMax = result.getBigDecimal("budget_max");

                project.put("budgetMin",
                    budgetMin == null ? JSONObject.NULL : budgetMin);
                project.put("budgetMax",
                    budgetMax == null ? JSONObject.NULL : budgetMax);

                java.sql.Date deadline = result.getDate("deadline");

                project.put("deadline",
                    deadline == null ? JSONObject.NULL : deadline.toString());
                project.put("status", result.getString("status"));

                projects.put(project);
            }

            sendJson(response, HttpServletResponse.SC_OK,
                new JSONObject().put("projects", projects));

        } catch (SQLException exception) {
            getServletContext().log(
                "Failed to retrieve open WorkNest projects.", exception);

            sendJson(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                new JSONObject().put("error",
                    "Unable to load projects right now."));
        }
    }
}
