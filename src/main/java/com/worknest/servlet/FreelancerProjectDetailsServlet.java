
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

public class FreelancerProjectDetailsServlet extends HttpServlet {

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
                    "Only freelancers can view marketplace projects."));
            return;
        }

        String idParameter = request.getParameter("id");

        if (idParameter == null ||
                !idParameter.matches("[1-9][0-9]*")) {
            sendJson(response, HttpServletResponse.SC_BAD_REQUEST,
                new JSONObject().put("error", "Invalid project ID."));
            return;
        }

        final long projectId;

        try {
            projectId = Long.parseLong(idParameter);
        } catch (NumberFormatException exception) {
            sendJson(response, HttpServletResponse.SC_BAD_REQUEST,
                new JSONObject().put("error", "Invalid project ID."));
            return;
        }

        String sql =
            "SELECT p.project_id, p.title, p.description, p.required_skills, " +
            "p.project_type, p.budget_min, p.budget_max, p.deadline, p.status, " +
            "p.created_at, p.updated_at, u.full_name AS client_name, " +
            "cp.organization_name AS client_organization_name, " +
            "cp.organization_type AS client_organization_type, " +
            "cp.industry AS client_industry, cp.website AS client_website, " +
            "cp.country AS client_country, cp.city AS client_city, " +
            "cp.business_description AS client_business_description " +
            "FROM projects p " +
            "INNER JOIN users u ON u.user_id = p.client_id " +
            "LEFT JOIN client_profiles cp ON cp.client_id = p.client_id " +
            "WHERE p.project_id = ? AND p.status = 'OPEN'";

        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setLong(1, projectId);

            try (ResultSet result = statement.executeQuery()) {

                if (!result.next()) {
                    sendJson(response, HttpServletResponse.SC_NOT_FOUND,
                        new JSONObject().put("error",
                            "This project is no longer available."));
                    return;
                }

                JSONObject project = new JSONObject();

                project.put("projectId", result.getLong("project_id"));
                project.put("title", result.getString("title"));
                project.put("description", result.getString("description"));
                project.put("requiredSkills",
                    result.getString("required_skills"));
                project.put("projectType", result.getString("project_type"));
                project.put("budgetMin", result.getBigDecimal("budget_min"));
                project.put("budgetMax", result.getBigDecimal("budget_max"));

                java.sql.Date deadline = result.getDate("deadline");

                project.put("deadline",
                    deadline == null ? JSONObject.NULL : deadline.toString());
                project.put("status", result.getString("status"));
                project.put("clientName", safe(result.getString("client_name")));
                project.put("clientOrganization", clientOrganization(result));

                java.sql.Timestamp createdAt = result.getTimestamp("created_at");
                java.sql.Timestamp updatedAt = result.getTimestamp("updated_at");

                project.put("createdAt",
                    createdAt == null ? JSONObject.NULL : createdAt.toString());
                project.put("updatedAt",
                    updatedAt == null ? JSONObject.NULL : updatedAt.toString());

                sendJson(response, HttpServletResponse.SC_OK, project);
            }

        } catch (SQLException exception) {
            getServletContext().log(
                "Failed to retrieve freelancer project details.", exception);

            sendJson(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                new JSONObject().put("error",
                    "Unable to load project details right now."));
        }
    }
}
