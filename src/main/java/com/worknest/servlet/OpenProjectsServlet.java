
package com.worknest.servlet;

import com.worknest.util.DatabaseUtil;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Connection;
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

        return DatabaseUtil.getConnection();
    }


    private JSONObject clientOrganization(ResultSet result) throws SQLException {
        JSONObject organization = new JSONObject();
        organization.put("organizationName", safe(result.getString("client_organization_name")));
        organization.put("organizationType", safe(result.getString("client_organization_type")));
        organization.put("industry", safe(result.getString("client_industry")));
        organization.put("website", safe(result.getString("client_website")));
        organization.put("country", safe(result.getString("client_country")));
        organization.put("city", safe(result.getString("client_city")));
        organization.put("businessDescription", safe(result.getString("client_business_description")));
        return organization;
    }

    private String safe(String value) {
        return value == null ? "" : value;
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
            "SELECT p.project_id, p.title, p.description, p.required_skills, " +
            "p.project_type, p.budget_min, p.budget_max, p.deadline, p.status, " +
            "u.full_name AS client_name, " +
            "cp.organization_name AS client_organization_name, " +
            "cp.organization_type AS client_organization_type, " +
            "cp.industry AS client_industry, cp.website AS client_website, " +
            "cp.country AS client_country, cp.city AS client_city, " +
            "cp.business_description AS client_business_description " +
            "FROM projects p " +
            "INNER JOIN users u ON u.user_id = p.client_id " +
            "LEFT JOIN client_profiles cp ON cp.client_id = p.client_id " +
            "WHERE p.status = 'OPEN' " +
            "ORDER BY p.created_at DESC";

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
                project.put("clientName", safe(result.getString("client_name")));
                project.put("clientOrganization", clientOrganization(result));

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
