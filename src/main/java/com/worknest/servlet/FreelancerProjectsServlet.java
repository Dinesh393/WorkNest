
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

import org.json.JSONArray;
import org.json.JSONObject;

public class FreelancerProjectsServlet extends HttpServlet {

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

    @Override
    protected void doGet(
            HttpServletRequest request,
            HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-store");

        JSONObject result = new JSONObject();

        HttpSession session = request.getSession(false);

        if (session == null
                || !(session.getAttribute("userId") instanceof Number)) {

            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);

            result.put("success", false);
            result.put("error", "Please log in.");

            response.getWriter().write(result.toString());
            return;
        }

        if (!"FREELANCER".equals(session.getAttribute("role"))) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);

            result.put("success", false);
            result.put("error", "Freelancer access required.");

            response.getWriter().write(result.toString());
            return;
        }

        long freelancerId =
            ((Number) session.getAttribute("userId")).longValue();

        String sql = """
            SELECT
                pr.project_id,
                pr.title,
                pr.description,
                pr.required_skills,
                pr.project_type,
                pr.budget_min,
                pr.budget_max,
                pr.deadline,
                pr.status AS project_status,
                pr.created_at,
                pr.updated_at,
                u.full_name AS client_name,
                p.proposed_amount AS agreed_amount,
                cp.organization_name AS client_organization_name,
                cp.organization_type AS client_organization_type,
                cp.industry AS client_industry,
                cp.website AS client_website,
                cp.country AS client_country,
                cp.city AS client_city,
                cp.business_description AS client_business_description
            FROM proposals p
            INNER JOIN projects pr
                ON pr.project_id = p.project_id
            INNER JOIN users u
                ON u.user_id = pr.client_id
            LEFT JOIN client_profiles cp
                ON cp.client_id = pr.client_id
            WHERE p.freelancer_id = ?
              AND p.status = 'ACCEPTED'
            ORDER BY p.updated_at DESC
            """;

        JSONArray projects = new JSONArray();

        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setLong(1, freelancerId);

            try (ResultSet rs = ps.executeQuery()) {

                while (rs.next()) {
                    JSONObject project = new JSONObject();

                    project.put("projectId",
                        rs.getLong("project_id"));

                    project.put("title",
                        rs.getString("title"));

                    project.put("description",
                        rs.getString("description"));

                    project.put("requiredSkills",
                        rs.getString("required_skills"));

                    project.put("projectType",
                        rs.getString("project_type"));

                    project.put("budgetMin",
                        rs.getBigDecimal("budget_min"));

                    project.put("budgetMax",
                        rs.getBigDecimal("budget_max"));

                    project.put("deadline",
                        rs.getDate("deadline") == null
                            ? JSONObject.NULL
                            : rs.getDate("deadline").toString());

                    project.put("projectStatus",
                        rs.getString("project_status"));

                    project.put("clientName",
                        rs.getString("client_name"));
                    project.put("clientOrganization", clientOrganization(rs));

                    project.put("agreedAmount",
                        rs.getBigDecimal("agreed_amount"));

                    project.put("createdAt",
                        rs.getTimestamp("created_at") == null
                            ? JSONObject.NULL
                            : rs.getTimestamp("created_at").toString());

                    project.put("updatedAt",
                        rs.getTimestamp("updated_at") == null
                            ? JSONObject.NULL
                            : rs.getTimestamp("updated_at").toString());

                    projects.put(project);
                }
            }

            result.put("success", true);
            result.put("projects", projects);

            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().write(result.toString());

        } catch (SQLException e) {
            log("Unable to load freelancer projects.", e);

            response.setStatus(
                HttpServletResponse.SC_INTERNAL_SERVER_ERROR);

            result.put("success", false);
            result.put("error", "Unable to load your projects.");

            response.getWriter().write(result.toString());
        }
    }
}
