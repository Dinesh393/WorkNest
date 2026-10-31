
package com.worknest.servlet;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.json.JSONObject;
import com.worknest.util.NotificationUtil;

public class SubmitProposalServlet extends HttpServlet {

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

    private void sendError(
            HttpServletResponse response,
            int status,
            String message) throws IOException {

        JSONObject body = new JSONObject();
        body.put("error", message);
        sendJson(response, status, body);
    }

    @Override
    protected void doPost(
            HttpServletRequest request,
            HttpServletResponse response)
            throws ServletException, IOException {

        request.setCharacterEncoding("UTF-8");

        HttpSession session = request.getSession(false);

        if (session == null
                || !(session.getAttribute("userId") instanceof Long)) {
            sendError(response, HttpServletResponse.SC_UNAUTHORIZED,
                    "Please log in to submit a proposal.");
            return;
        }

        if (!"FREELANCER".equals(session.getAttribute("role"))) {
            sendError(response, HttpServletResponse.SC_FORBIDDEN,
                    "Only freelancers can submit proposals.");
            return;
        }

        long freelancerId = (Long) session.getAttribute("userId");

        String projectIdText = request.getParameter("projectId");
        String amountText = request.getParameter("proposedAmount");
        String coverLetter = request.getParameter("coverLetter");

        long projectId;
        BigDecimal proposedAmount;

        if (projectIdText == null
                || !projectIdText.matches("[1-9][0-9]{0,18}")) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST,
                    "Invalid project ID.");
            return;
        }

        try {
            projectId = Long.parseLong(projectIdText);
        } catch (NumberFormatException e) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST,
                    "Invalid project ID.");
            return;
        }

        if (amountText == null || amountText.trim().isEmpty()
                || amountText.trim().length() > 30) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST,
                    "Enter a valid proposed amount.");
            return;
        }

        try {
            proposedAmount = new BigDecimal(amountText.trim());

            if (proposedAmount.scale() > 2
                    || proposedAmount.compareTo(BigDecimal.ZERO) <= 0
                    || proposedAmount.precision() > 12) {
                sendError(response, HttpServletResponse.SC_BAD_REQUEST,
                        "Amount must be positive and have at most two decimal places.");
                return;
            }
        } catch (NumberFormatException e) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST,
                    "Enter a valid numeric amount.");
            return;
        }

        if (coverLetter == null) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST,
                    "Please enter a cover letter.");
            return;
        }

        coverLetter = coverLetter.trim();

        if (coverLetter.isEmpty() || coverLetter.length() > 5000) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST,
                    "Cover letter must contain 1 to 5000 characters.");
            return;
        }

        try (Connection connection = getConnection()) {
            connection.setAutoCommit(false);

            try {
                String projectSql =
                    "SELECT project_type, status, client_id, title FROM projects "
                    + "WHERE project_id = ? FOR UPDATE";

                String projectType;
                long clientId;
                String projectTitle;

                try (PreparedStatement statement =
                        connection.prepareStatement(projectSql)) {

                    statement.setLong(1, projectId);

                    try (ResultSet result = statement.executeQuery()) {
                        if (!result.next()) {
                            connection.rollback();
                            sendError(response,
                                    HttpServletResponse.SC_NOT_FOUND,
                                    "Project not found.");
                            return;
                        }

                        projectType = result.getString("project_type");
                        clientId = result.getLong("client_id");
                        projectTitle = result.getString("title");
                        String status = result.getString("status");

                        if (!"OPEN".equals(status)) {
                            connection.rollback();
                            sendError(response,
                                    HttpServletResponse.SC_CONFLICT,
                                    "This project is no longer open for proposals.");
                            return;
                        }
                    }
                }

                String insertSql =
                    "INSERT INTO proposals "
                    + "(project_id, freelancer_id, cover_letter, "
                    + "proposed_amount, status) "
                    + "VALUES (?, ?, ?, ?, 'PENDING')";

                try (PreparedStatement statement =
                        connection.prepareStatement(insertSql)) {

                    statement.setLong(1, projectId);
                    statement.setLong(2, freelancerId);
                    statement.setString(3, coverLetter);
                    statement.setBigDecimal(4, proposedAmount);

                    statement.executeUpdate();
                }

                NotificationUtil.create(connection, clientId, projectId,
                        "PROPOSAL_RECEIVED", "New proposal received",
                        "A freelancer submitted a proposal for “" + projectTitle + "”.");

                connection.commit();

                JSONObject body = new JSONObject();
                body.put("message", "Proposal submitted successfully.");
                body.put("projectType", projectType);
                body.put("proposedAmount", proposedAmount);

                sendJson(response, HttpServletResponse.SC_CREATED, body);

            } catch (SQLException e) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackError) {
                    getServletContext().log(
                        "Proposal transaction rollback failed.",
                        rollbackError);
                }

                // MySQL duplicate-key violation, including the unique
                // freelancer/project constraint.
                if ("23000".equals(e.getSQLState())
                        && e.getErrorCode() == 1062) {
                    sendError(response,
                            HttpServletResponse.SC_CONFLICT,
                            "You have already submitted a proposal for this project.");
                    return;
                }

                throw e;
            } finally {
                try {
                    connection.setAutoCommit(true);
                } catch (SQLException e) {
                    getServletContext().log(
                        "Could not restore database auto-commit.", e);
                }
            }

        } catch (SQLException e) {
            getServletContext().log("Proposal submission failed.", e);
            sendError(response,
                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Unable to submit your proposal right now. Please try again.");
        }
    }

    @Override
    protected void doGet(
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {
        response.setHeader("Allow", "POST");
        sendError(response, HttpServletResponse.SC_METHOD_NOT_ALLOWED,
                "Use POST to submit a proposal.");
    }
}
