package com.worknest.servlet;

import java.io.IOException;
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

public class MyProposalsServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    private Connection getConnection() throws SQLException {
        String username = System.getenv("DB_USERNAME");
        String password = System.getenv("DB_PASSWORD");

        if (username == null || username.isBlank()
                || password == null || password.isEmpty()) {
            throw new SQLException(
                "Database configuration is missing."
            );
        }

        return DriverManager.getConnection(
            "jdbc:mysql://localhost:3306/worknest_db",
            username,
            password
        );
    }


    private String safe(String value) { return value == null ? "" : value; }

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

    private void sendError(
            HttpServletResponse response,
            int status,
            String message) throws IOException {

        JSONObject body = new JSONObject();
        body.put("error", message);

        sendJson(response, status, body);
    }

    @Override
    protected void doGet(
            HttpServletRequest request,
            HttpServletResponse response)
            throws ServletException, IOException {

        HttpSession session = request.getSession(false);

        if (session == null
                || !(session.getAttribute("userId") instanceof Long)) {
            sendError(
                response,
                HttpServletResponse.SC_UNAUTHORIZED,
                "Please log in to view your proposals."
            );
            return;
        }

        if (!"FREELANCER".equals(session.getAttribute("role"))) {
            sendError(
                response,
                HttpServletResponse.SC_FORBIDDEN,
                "Only freelancers can view this page."
            );
            return;
        }

        long freelancerId = (Long) session.getAttribute("userId");

        String sql =
            "SELECT p.proposal_id, p.project_id, p.cover_letter, " +
            "p.proposed_amount, p.status AS proposal_status, " +
            "p.created_at, p.updated_at, " +
            "pr.title AS project_title, pr.project_type, " +
            "pr.status AS project_status, u.full_name AS client_name, " +
            "cp.organization_name AS client_organization_name, " +
            "cp.organization_type AS client_organization_type, " +
            "cp.industry AS client_industry, cp.website AS client_website, " +
            "cp.country AS client_country, cp.city AS client_city, " +
            "cp.business_description AS client_business_description " +
            "FROM proposals p " +
            "INNER JOIN projects pr ON p.project_id = pr.project_id " +
            "INNER JOIN users u ON u.user_id = pr.client_id " +
            "LEFT JOIN client_profiles cp ON cp.client_id = pr.client_id " +
            "WHERE p.freelancer_id = ? " +
            "ORDER BY p.created_at DESC";

        JSONArray proposals = new JSONArray();

        try (Connection connection = getConnection();
             PreparedStatement statement =
                 connection.prepareStatement(sql)) {

            statement.setLong(1, freelancerId);

            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    JSONObject proposal = new JSONObject();

                    proposal.put(
                        "proposalId",
                        result.getLong("proposal_id")
                    );

                    proposal.put(
                        "projectId",
                        result.getLong("project_id")
                    );

                    proposal.put(
                        "projectTitle",
                        result.getString("project_title")
                    );

                    proposal.put(
                        "projectType",
                        result.getString("project_type")
                    );

                    proposal.put(
                        "projectStatus",
                        result.getString("project_status")
                    );
                    proposal.put("clientName", safe(result.getString("client_name")));
                    proposal.put("clientOrganization", clientOrganization(result));

                    proposal.put(
                        "coverLetter",
                        result.getString("cover_letter")
                    );

                    proposal.put(
                        "proposedAmount",
                        result.getBigDecimal("proposed_amount")
                    );

                    proposal.put(
                        "status",
                        result.getString("proposal_status")
                    );

                    proposal.put(
                        "createdAt",
                        result.getTimestamp("created_at").toString()
                    );

                    proposal.put(
                        "updatedAt",
                        result.getTimestamp("updated_at").toString()
                    );

                    proposals.put(proposal);
                }
            }

            JSONObject body = new JSONObject();
            body.put("proposals", proposals);

            sendJson(
                response,
                HttpServletResponse.SC_OK,
                body
            );

        } catch (SQLException exception) {
            getServletContext().log(
                "Unable to retrieve freelancer proposals.",
                exception
            );

            if (!response.isCommitted()) {
                sendError(
                    response,
                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Unable to load your proposals right now."
                );
            }
        }
    }

    @Override
    protected void doPost(
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {

        response.setHeader("Allow", "GET");

        sendError(
            response,
            HttpServletResponse.SC_METHOD_NOT_ALLOWED,
            "Use GET to view your proposals."
        );
    }
}