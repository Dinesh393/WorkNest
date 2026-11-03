
package com.worknest.servlet;

import com.worknest.util.DatabaseUtil;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.json.JSONObject;
import com.worknest.util.NotificationUtil;

public class AcceptProposalServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doPost(
            HttpServletRequest request,
            HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-store");

        JSONObject result = new JSONObject();

        // 1. Verify the logged-in session.
        HttpSession session = request.getSession(false);

        if (session == null
                || !(session.getAttribute("userId") instanceof Long)) {

            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            result.put("error", "Please log in again.");
            response.getWriter().write(result.toString());
            return;
        }

        // 2. Only clients can accept proposals.
        if (!"CLIENT".equals(session.getAttribute("role"))) {

            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            result.put("error", "Only clients can accept proposals.");
            response.getWriter().write(result.toString());
            return;
        }

        // 3. Validate the submitted IDs.
        String projectIdText = request.getParameter("projectId");
        String proposalIdText = request.getParameter("proposalId");

        if (!isPositiveInteger(projectIdText)
                || !isPositiveInteger(proposalIdText)) {

            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            result.put("error", "Invalid project or proposal ID.");
            response.getWriter().write(result.toString());
            return;
        }

        long projectId;
        long proposalId;

        try {
            projectId = Long.parseLong(projectIdText);
            proposalId = Long.parseLong(proposalIdText);

        } catch (NumberFormatException ex) {

            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            result.put("error", "Invalid project or proposal ID.");
            response.getWriter().write(result.toString());
            return;
        }

        long clientId = (Long) session.getAttribute("userId");

        try (Connection connection = getConnection()) {

            connection.setAutoCommit(false);

            try {
                // 4. Verify project ownership and lock the project row.
                // This serializes acceptance attempts for the same project.
                String projectSql =
                    "SELECT status, title FROM projects "
                    + "WHERE project_id = ? AND client_id = ? "
                    + "FOR UPDATE";

                String projectStatus = null;
                String projectTitle = "Project";

                try (PreparedStatement ps =
                        connection.prepareStatement(projectSql)) {

                    ps.setLong(1, projectId);
                    ps.setLong(2, clientId);

                    try (ResultSet rs = ps.executeQuery()) {

                        if (rs.next()) {
                            projectStatus = rs.getString("status");
                            projectTitle = rs.getString("title");
                        }
                    }
                }

                if (projectStatus == null) {

                    connection.rollback();

                    response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                    result.put("error", "Project not found.");
                    response.getWriter().write(result.toString());
                    return;
                }

                if (!"OPEN".equals(projectStatus)) {

                    connection.rollback();

                    response.setStatus(HttpServletResponse.SC_CONFLICT);
                    result.put("error",
                        "Only open projects can accept proposals.");

                    response.getWriter().write(result.toString());
                    return;
                }

                // 5. Verify the proposal belongs to this project
                // and is still pending.
                String proposalSql =
                    "SELECT status, freelancer_id FROM proposals "
                    + "WHERE proposal_id = ? AND project_id = ? "
                    + "FOR UPDATE";

                long acceptedFreelancerId;

                try (PreparedStatement ps =
                        connection.prepareStatement(proposalSql)) {

                    ps.setLong(1, proposalId);
                    ps.setLong(2, projectId);

                    try (ResultSet rs = ps.executeQuery()) {

                        if (!rs.next()) {

                            connection.rollback();

                            response.setStatus(
                                HttpServletResponse.SC_NOT_FOUND);

                            result.put("error",
                                "Proposal not found for this project.");

                            response.getWriter().write(result.toString());
                            return;
                        }

                        if (!"PENDING".equals(rs.getString("status"))) {

                            connection.rollback();

                            response.setStatus(
                                HttpServletResponse.SC_CONFLICT);

                            result.put("error",
                                "This proposal has already been processed.");

                            response.getWriter().write(result.toString());
                            return;
                        }
                        acceptedFreelancerId = rs.getLong("freelancer_id");
                    }
                }

                List<Long> rejectedFreelancerIds = new ArrayList<>();
                String pendingSql = "SELECT freelancer_id FROM proposals "
                        + "WHERE project_id = ? AND proposal_id <> ? "
                        + "AND status = 'PENDING' FOR UPDATE";
                try (PreparedStatement ps = connection.prepareStatement(pendingSql)) {
                    ps.setLong(1, projectId);
                    ps.setLong(2, proposalId);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) rejectedFreelancerIds.add(rs.getLong("freelancer_id"));
                    }
                }

                // 6. Accept the selected proposal.
                String acceptSql =
                    "UPDATE proposals SET status = 'ACCEPTED' "
                    + "WHERE proposal_id = ? AND project_id = ? "
                    + "AND status = 'PENDING'";

                int acceptedRows;

                try (PreparedStatement ps =
                        connection.prepareStatement(acceptSql)) {

                    ps.setLong(1, proposalId);
                    ps.setLong(2, projectId);

                    acceptedRows = ps.executeUpdate();
                }

                if (acceptedRows != 1) {
                    throw new SQLException(
                        "Proposal could not be accepted.");
                }

                // 7. Reject all other pending proposals for this project.
                String rejectSql =
                    "UPDATE proposals SET status = 'REJECTED' "
                    + "WHERE project_id = ? "
                    + "AND proposal_id <> ? "
                    + "AND status = 'PENDING'";

                try (PreparedStatement ps =
                        connection.prepareStatement(rejectSql)) {

                    ps.setLong(1, projectId);
                    ps.setLong(2, proposalId);

                    ps.executeUpdate();
                }

                List<Long> invitedFreelancerIds = new ArrayList<>();
                String pendingInvitationsSql =
                    "SELECT freelancer_id FROM project_invitations "
                    + "WHERE project_id = ? AND status = 'PENDING' FOR UPDATE";
                try (PreparedStatement ps = connection.prepareStatement(pendingInvitationsSql)) {
                    ps.setLong(1, projectId);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) invitedFreelancerIds.add(rs.getLong("freelancer_id"));
                    }
                }
                try (PreparedStatement ps = connection.prepareStatement(
                        "UPDATE project_invitations SET status = 'CANCELLED' "
                        + "WHERE project_id = ? AND status = 'PENDING'")) {
                    ps.setLong(1, projectId);
                    ps.executeUpdate();
                }

                // 8. Change the project status to IN_PROGRESS.
                String updateProjectSql =
                    "UPDATE projects SET status = 'IN_PROGRESS' "
                    + "WHERE project_id = ? "
                    + "AND client_id = ? "
                    + "AND status = 'OPEN'";

                int updatedProjects;

                try (PreparedStatement ps =
                        connection.prepareStatement(updateProjectSql)) {

                    ps.setLong(1, projectId);
                    ps.setLong(2, clientId);

                    updatedProjects = ps.executeUpdate();
                }

                if (updatedProjects != 1) {
                    throw new SQLException(
                        "Project status could not be updated.");
                }

                NotificationUtil.create(connection, acceptedFreelancerId, projectId,
                        "PROPOSAL_ACCEPTED", "Your proposal was accepted",
                        "Your proposal for “" + projectTitle + "” was accepted. You can now open the project workspace.");
                for (Long rejectedFreelancerId : rejectedFreelancerIds) {
                    NotificationUtil.create(connection, rejectedFreelancerId, projectId,
                            "PROPOSAL_NOT_SELECTED", "Proposal update",
                            "The client selected another freelancer for “" + projectTitle + "”. You can continue exploring open projects.");
                }
                for (Long invitedFreelancerId : invitedFreelancerIds) {
                    if (invitedFreelancerId.longValue() == acceptedFreelancerId
                            || rejectedFreelancerIds.contains(invitedFreelancerId)) continue;
                    NotificationUtil.create(connection, invitedFreelancerId, projectId,
                            "PROJECT_INVITATION_CANCELLED", "Project invitation closed",
                            "The client selected another freelancer for “" + projectTitle + "”.");
                }

                // Commit the proposal, project, and notification changes together.
                connection.commit();

                response.setStatus(HttpServletResponse.SC_OK);

                result.put("message",
                    "Proposal accepted successfully. "
                    + "The project is now in progress.");

                result.put("projectStatus", "IN_PROGRESS");
                result.put("proposalStatus", "ACCEPTED");

                response.getWriter().write(result.toString());

            } catch (SQLException ex) {

                // Roll back all database changes if any step fails.
                try {
                    connection.rollback();
                } catch (SQLException rollbackEx) {
                    ex.addSuppressed(rollbackEx);
                }

                throw ex;
            }

        } catch (SQLException ex) {

            getServletContext().log(
                "Failed to accept proposal.", ex);

            response.setStatus(
                HttpServletResponse.SC_INTERNAL_SERVER_ERROR);

            result = new JSONObject();
            result.put("error",
                "Unable to accept this proposal. Please try again.");

            response.getWriter().write(result.toString());
        }
    }

    // Uses the same database connection pattern as your other servlets.
    private Connection getConnection() throws SQLException {

        String username = System.getenv("DB_USERNAME");
        String password = System.getenv("DB_PASSWORD");

        if (username == null || password == null) {
            throw new SQLException(
                "Database configuration is missing.");
        }

        return DatabaseUtil.getConnection();
    }

    private boolean isPositiveInteger(String value) {
        return value != null
                && value.matches("[1-9][0-9]{0,18}");
    }

    @Override
    protected void doGet(
            HttpServletRequest request,
            HttpServletResponse response)
            throws ServletException, IOException {

        response.setHeader("Allow", "POST");

        response.sendError(
            HttpServletResponse.SC_METHOD_NOT_ALLOWED);
    }
}
