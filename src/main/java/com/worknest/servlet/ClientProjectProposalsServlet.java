
package com.worknest.servlet;

import com.worknest.util.DatabaseUtil;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.json.JSONArray;
import org.json.JSONObject;

public class ClientProjectProposalsServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;


    private static final Pattern ID_PATTERN =
    		Pattern.compile("^[1-9]\\d*$");

    @Override
    protected void doGet(
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-store");

        HttpSession session = request.getSession(false);

        if (session == null
                || !(session.getAttribute("userId") instanceof Long)) {
            sendError(response, HttpServletResponse.SC_UNAUTHORIZED,
                    "Please log in to continue.");
            return;
        }

        if (!"CLIENT".equals(session.getAttribute("role"))) {
            sendError(response, HttpServletResponse.SC_FORBIDDEN,
                    "Only clients can view project proposals.");
            return;
        }

        String projectIdValue = request.getParameter("projectId");

        if (projectIdValue == null
                || !ID_PATTERN.matcher(projectIdValue).matches()) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST,
                    "A valid project ID is required.");
            return;
        }

        final long projectId;

        try {
            projectId = Long.parseLong(projectIdValue);
        } catch (NumberFormatException exception) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST,
                    "Invalid project ID.");
            return;
        }

        long clientId = (Long) session.getAttribute("userId");

        String dbUsername = System.getenv("DB_USERNAME");
        String dbPassword = System.getenv("DB_PASSWORD");

        if (dbUsername == null || dbUsername.isBlank()
                || dbPassword == null || dbPassword.isEmpty()) {
            log("Database credentials are not configured.");
            sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Unable to load proposals right now.");
            return;
        }

        String ownershipSql =
                "SELECT project_id, status FROM projects "
              + "WHERE project_id = ? AND client_id = ?";

        String proposalsSql =
                "SELECT p.proposal_id, p.freelancer_id, p.cover_letter, "
              + "p.proposed_amount, p.status, p.created_at, "
              + "u.full_name AS freelancer_name "
              + "FROM proposals p "
              + "INNER JOIN users u ON u.user_id = p.freelancer_id "
              + "INNER JOIN projects pr ON pr.project_id = p.project_id "
              + "WHERE pr.project_id = ? AND pr.client_id = ? "
              + "ORDER BY p.created_at DESC";
        String projectStatus;
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");

            try (Connection connection = DatabaseUtil.getConnection()) {

                // First, verify that this project belongs to this client.
                try (PreparedStatement statement =
                        connection.prepareStatement(ownershipSql)) {

                    statement.setLong(1, projectId);
                    statement.setLong(2, clientId);

                    try (ResultSet result = statement.executeQuery()) {
                        if (!result.next()) {
                            sendError(response,
                                    HttpServletResponse.SC_NOT_FOUND,
                                    "Project not found.");
                            return;
                        }

                        projectStatus = result.getString("status");
                    }
                }

                JSONArray proposals = new JSONArray();

                try (PreparedStatement statement =
                        connection.prepareStatement(proposalsSql)) {

                    statement.setLong(1, projectId);
                    statement.setLong(2, clientId);

                    try (ResultSet result = statement.executeQuery()) {
                        while (result.next()) {
                            JSONObject proposal = new JSONObject();

                            proposal.put("proposalId",
                                    result.getLong("proposal_id"));
                            proposal.put("freelancerId",
                                    result.getLong("freelancer_id"));
                            proposal.put("freelancerName",
                                    result.getString("freelancer_name"));
                            proposal.put("coverLetter",
                                    result.getString("cover_letter"));
                            proposal.put("proposedAmount",
                                    result.getBigDecimal("proposed_amount"));
                            proposal.put("status",
                                    result.getString("status"));
                            proposal.put("createdAt",
                                    result.getTimestamp("created_at")
                                            .toString());

                            proposals.put(proposal);
                        }
                    }
                }

                JSONObject result = new JSONObject();
                result.put("projectId", projectId);
                result.put("projectStatus", projectStatus);
                result.put("proposals", proposals);

                response.setStatus(HttpServletResponse.SC_OK);
                response.getWriter().write(result.toString());
            }

        } catch (ClassNotFoundException | SQLException exception) {
            log("Unable to load client project proposals.", exception);

            if (!response.isCommitted()) {
                sendError(response,
                        HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                        "Unable to load proposals right now.");
            }
        }
    }

    @Override
    protected void doPost(
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {

        response.setHeader("Allow", "GET");
        sendError(response, HttpServletResponse.SC_METHOD_NOT_ALLOWED,
                "This endpoint only supports GET requests.");
    }

    private void sendError(
            HttpServletResponse response,
            int status,
            String message) throws IOException {

        response.setStatus(status);

        JSONObject error = new JSONObject();
        error.put("message", message);

        response.getWriter().write(error.toString());
    }
}
