package com.worknest.servlet;

import com.worknest.util.DatabaseUtil;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.json.JSONArray;
import org.json.JSONObject;

import com.worknest.util.NotificationUtil;

/** Client-to-freelancer project offers that require freelancer acceptance. */
public class ProjectInvitationServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final Pattern ID_PATTERN = Pattern.compile("^[1-9]\\d{0,18}$");

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        setJson(response);
        HttpSession session = request.getSession(false);
        if (!hasUser(session)) {
            send(response, HttpServletResponse.SC_UNAUTHORIZED, error("Please log in again."));
            return;
        }

        String role = String.valueOf(session.getAttribute("role"));
        long userId = ((Number) session.getAttribute("userId")).longValue();
        String sql;
        if ("FREELANCER".equals(role)) {
            sql = "SELECT i.invitation_id, i.project_id, i.offered_amount, "
                    + "i.client_message, i.created_at, p.title, p.project_type, "
                    + "p.budget_min, p.budget_max, u.full_name AS client_name, "
                    + "cp.organization_name AS client_organization_name, "
                    + "cp.organization_type AS client_organization_type, "
                    + "cp.industry AS client_industry, cp.website AS client_website, "
                    + "cp.country AS client_country, cp.city AS client_city, "
                    + "cp.business_description AS client_business_description "
                    + "FROM project_invitations i "
                    + "INNER JOIN projects p ON p.project_id = i.project_id "
                    + "INNER JOIN users u ON u.user_id = i.client_id "
                    + "LEFT JOIN client_profiles cp ON cp.client_id = i.client_id "
                    + "WHERE i.freelancer_id = ? AND i.status = 'PENDING' "
                    + "AND p.status = 'OPEN' ORDER BY i.created_at DESC";
        } else if ("CLIENT".equals(role)) {
            sql = "SELECT i.invitation_id, i.project_id, i.freelancer_id, "
                    + "i.created_at, p.title, u.full_name AS freelancer_name "
                    + "FROM project_invitations i "
                    + "INNER JOIN projects p ON p.project_id = i.project_id "
                    + "INNER JOIN users u ON u.user_id = i.freelancer_id "
                    + "WHERE i.client_id = ? AND i.status = 'PENDING' "
                    + "AND p.status = 'OPEN' ORDER BY i.created_at DESC";
        } else {
            send(response, HttpServletResponse.SC_FORBIDDEN, error("This account cannot view project invitations."));
            return;
        }

        JSONArray invitations = new JSONArray();
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, userId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    JSONObject invitation = new JSONObject()
                            .put("invitationId", rs.getLong("invitation_id"))
                            .put("projectId", rs.getLong("project_id"))
                            .put("projectTitle", safe(rs.getString("title")))
                            .put("createdAt", rs.getTimestamp("created_at").toString());
                    if ("FREELANCER".equals(role)) {
                        invitation.put("offeredAmount", rs.getBigDecimal("offered_amount"))
                                .put("clientMessage", safe(rs.getString("client_message")))
                                .put("projectType", safe(rs.getString("project_type")))
                                .put("budgetMin", rs.getBigDecimal("budget_min"))
                                .put("budgetMax", rs.getBigDecimal("budget_max"))
                                .put("clientName", safe(rs.getString("client_name")))
                                .put("clientOrganization", organizationFrom(rs));
                    } else {
                        invitation.put("freelancerId", rs.getLong("freelancer_id"))
                                .put("freelancerName", safe(rs.getString("freelancer_name")));
                    }
                    invitations.put(invitation);
                }
            }
            send(response, HttpServletResponse.SC_OK,
                    new JSONObject().put("invitations", invitations));
        } catch (SQLException exception) {
            log("Unable to load project invitations.", exception);
            send(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    error("Unable to load project invitations right now."));
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        request.setCharacterEncoding("UTF-8");
        setJson(response);
        if (!isSameOriginRequest(request)) {
            send(response, HttpServletResponse.SC_FORBIDDEN, error("This request could not be verified."));
            return;
        }
        HttpSession session = request.getSession(false);
        if (!hasUser(session)) {
            send(response, HttpServletResponse.SC_UNAUTHORIZED, error("Please log in again."));
            return;
        }

        String action = request.getParameter("action");
        String role = String.valueOf(session.getAttribute("role"));
        long userId = ((Number) session.getAttribute("userId")).longValue();
        if ("invite".equals(action) && "CLIENT".equals(role)) {
            sendInvitation(request, response, userId);
        } else if (("accept".equals(action) || "decline".equals(action))
                && "FREELANCER".equals(role)) {
            respondToInvitation(request, response, userId, "accept".equals(action));
        } else {
            send(response, HttpServletResponse.SC_FORBIDDEN,
                    error("This action is not available for your account."));
        }
    }

    private void sendInvitation(HttpServletRequest request, HttpServletResponse response,
            long clientId) throws IOException {
        Long projectId = parseId(request.getParameter("projectId"));
        Long freelancerId = parseId(request.getParameter("freelancerId"));
        BigDecimal amount;
        try {
            String value = request.getParameter("offeredAmount");
            if (value == null || value.length() > 30) throw new NumberFormatException();
            amount = new BigDecimal(value.trim());
            if (amount.scale() > 2 || amount.precision() > 12 || amount.signum() <= 0) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException exception) {
            send(response, HttpServletResponse.SC_BAD_REQUEST, error("Enter a valid positive offer amount."));
            return;
        }
        String message = request.getParameter("clientMessage");
        message = message == null ? "" : message.trim();
        if (message.length() > 1000) {
            send(response, HttpServletResponse.SC_BAD_REQUEST, error("The message must be 1,000 characters or fewer."));
            return;
        }
        if (projectId == null || freelancerId == null) {
            send(response, HttpServletResponse.SC_BAD_REQUEST, error("Choose a valid project and freelancer."));
            return;
        }

        try (Connection connection = getConnection()) {
            connection.setAutoCommit(false);
            try {
                String projectSql = "SELECT title, status, budget_min, budget_max FROM projects "
                        + "WHERE project_id = ? AND client_id = ? FOR UPDATE";
                String title;
                BigDecimal minimum;
                BigDecimal maximum;
                try (PreparedStatement ps = connection.prepareStatement(projectSql)) {
                    ps.setLong(1, projectId);
                    ps.setLong(2, clientId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            connection.rollback();
                            send(response, HttpServletResponse.SC_NOT_FOUND, error("Project not found."));
                            return;
                        }
                        if (!"OPEN".equals(rs.getString("status"))) {
                            connection.rollback();
                            send(response, HttpServletResponse.SC_CONFLICT, error("Only open projects can receive invitations."));
                            return;
                        }
                        title = safe(rs.getString("title"));
                        minimum = rs.getBigDecimal("budget_min");
                        maximum = rs.getBigDecimal("budget_max");
                    }
                }
                if ((minimum != null && amount.compareTo(minimum) < 0)
                        || (maximum != null && amount.compareTo(maximum) > 0)) {
                    connection.rollback();
                    send(response, HttpServletResponse.SC_BAD_REQUEST,
                            error("The offer must be within the project's budget range."));
                    return;
                }

                try (PreparedStatement ps = connection.prepareStatement(
                        "SELECT user_id FROM users WHERE user_id = ? AND role = 'FREELANCER'")) {
                    ps.setLong(1, freelancerId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            connection.rollback();
                            send(response, HttpServletResponse.SC_NOT_FOUND, error("Freelancer not found."));
                            return;
                        }
                    }
                }
                try (PreparedStatement ps = connection.prepareStatement(
                        "SELECT invitation_id FROM project_invitations WHERE project_id = ? "
                        + "AND freelancer_id = ? AND status = 'PENDING' FOR UPDATE")) {
                    ps.setLong(1, projectId);
                    ps.setLong(2, freelancerId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            connection.rollback();
                            send(response, HttpServletResponse.SC_CONFLICT, error("An invitation is already pending for this freelancer and project."));
                            return;
                        }
                    }
                }
                try (PreparedStatement ps = connection.prepareStatement(
                        "INSERT INTO project_invitations (project_id, client_id, freelancer_id, "
                        + "offered_amount, client_message, status) VALUES (?, ?, ?, ?, ?, 'PENDING')")) {
                    ps.setLong(1, projectId);
                    ps.setLong(2, clientId);
                    ps.setLong(3, freelancerId);
                    ps.setBigDecimal(4, amount);
                    ps.setString(5, message);
                    ps.executeUpdate();
                }
                NotificationUtil.create(connection, freelancerId, projectId,
                        "PROJECT_INVITATION", "Project invitation received",
                        "A client invited you to work on “" + title + "”. Review the offer in My proposals.");
                connection.commit();
                send(response, HttpServletResponse.SC_CREATED,
                        new JSONObject().put("message", "Project invitation sent."));
            } catch (SQLException exception) {
                rollback(connection, exception);
                throw exception;
            }
        } catch (SQLException exception) {
            log("Unable to send project invitation.", exception);
            send(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    error("Unable to send the invitation right now."));
        }
    }

    private void respondToInvitation(HttpServletRequest request, HttpServletResponse response,
            long freelancerId, boolean accept) throws IOException {
        Long invitationId = parseId(request.getParameter("invitationId"));
        if (invitationId == null) {
            send(response, HttpServletResponse.SC_BAD_REQUEST, error("Choose a valid invitation."));
            return;
        }
        try (Connection connection = getConnection()) {
            connection.setAutoCommit(false);
            try {
                long projectId;
                try (PreparedStatement ps = connection.prepareStatement(
                        "SELECT project_id FROM project_invitations WHERE invitation_id = ? AND freelancer_id = ?")) {
                    ps.setLong(1, invitationId);
                    ps.setLong(2, freelancerId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            connection.rollback();
                            send(response, HttpServletResponse.SC_NOT_FOUND, error("Invitation not found."));
                            return;
                        }
                        projectId = rs.getLong("project_id");
                    }
                }

                String projectTitle;
                long clientId;
                String projectStatus;
                try (PreparedStatement ps = connection.prepareStatement(
                        "SELECT title, client_id, status FROM projects WHERE project_id = ? FOR UPDATE")) {
                    ps.setLong(1, projectId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            connection.rollback();
                            send(response, HttpServletResponse.SC_NOT_FOUND, error("Project not found."));
                            return;
                        }
                        projectTitle = safe(rs.getString("title"));
                        clientId = rs.getLong("client_id");
                        projectStatus = rs.getString("status");
                    }
                }

                BigDecimal amount;
                String clientMessage;
                String invitationStatus;
                try (PreparedStatement ps = connection.prepareStatement(
                        "SELECT offered_amount, client_message, status FROM project_invitations "
                        + "WHERE invitation_id = ? AND project_id = ? AND freelancer_id = ? FOR UPDATE")) {
                    ps.setLong(1, invitationId);
                    ps.setLong(2, projectId);
                    ps.setLong(3, freelancerId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            connection.rollback();
                            send(response, HttpServletResponse.SC_NOT_FOUND, error("Invitation not found."));
                            return;
                        }
                        amount = rs.getBigDecimal("offered_amount");
                        clientMessage = safe(rs.getString("client_message"));
                        invitationStatus = rs.getString("status");
                    }
                }
                if (!"PENDING".equals(invitationStatus)) {
                    connection.rollback();
                    send(response, HttpServletResponse.SC_CONFLICT, error("This invitation has already been answered."));
                    return;
                }
                if (!accept) {
                    try (PreparedStatement ps = connection.prepareStatement(
                            "UPDATE project_invitations SET status = 'DECLINED' WHERE invitation_id = ? AND status = 'PENDING'")) {
                        ps.setLong(1, invitationId);
                        ps.executeUpdate();
                    }
                    NotificationUtil.create(connection, clientId, projectId,
                            "PROJECT_INVITATION_DECLINED", "Project invitation declined",
                            "The freelancer declined your invitation for “" + projectTitle + "”.");
                    connection.commit();
                    send(response, HttpServletResponse.SC_OK,
                            new JSONObject().put("message", "Invitation declined."));
                    return;
                }
                if (!"OPEN".equals(projectStatus)) {
                    connection.rollback();
                    send(response, HttpServletResponse.SC_CONFLICT, error("This project is no longer open."));
                    return;
                }

                List<Long> otherFreelancers = new ArrayList<>();
                try (PreparedStatement ps = connection.prepareStatement(
                        "SELECT freelancer_id FROM proposals WHERE project_id = ? AND status = 'PENDING' FOR UPDATE")) {
                    ps.setLong(1, projectId);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) otherFreelancers.add(rs.getLong("freelancer_id"));
                    }
                }
                try (PreparedStatement ps = connection.prepareStatement(
                        "UPDATE proposals SET status = 'REJECTED' WHERE project_id = ? AND status = 'PENDING'")) {
                    ps.setLong(1, projectId);
                    ps.executeUpdate();
                }

                String acceptedCover = clientMessage.isEmpty()
                        ? "Accepted direct project invitation."
                        : "Accepted direct project invitation: " + clientMessage;
                boolean existingProposal = false;
                try (PreparedStatement ps = connection.prepareStatement(
                        "SELECT status FROM proposals WHERE project_id = ? AND freelancer_id = ? FOR UPDATE")) {
                    ps.setLong(1, projectId);
                    ps.setLong(2, freelancerId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            if ("ACCEPTED".equals(rs.getString("status"))) {
                                connection.rollback();
                                send(response, HttpServletResponse.SC_CONFLICT, error("You are already assigned to this project."));
                                return;
                            }
                            existingProposal = true;
                        }
                    }
                }
                if (existingProposal) {
                    try (PreparedStatement ps = connection.prepareStatement(
                            "UPDATE proposals SET status = 'ACCEPTED', proposed_amount = ?, cover_letter = ? "
                            + "WHERE project_id = ? AND freelancer_id = ?")) {
                        ps.setBigDecimal(1, amount);
                        ps.setString(2, acceptedCover);
                        ps.setLong(3, projectId);
                        ps.setLong(4, freelancerId);
                        ps.executeUpdate();
                    }
                } else {
                    try (PreparedStatement ps = connection.prepareStatement(
                            "INSERT INTO proposals (project_id, freelancer_id, cover_letter, proposed_amount, status) "
                            + "VALUES (?, ?, ?, ?, 'ACCEPTED')")) {
                        ps.setLong(1, projectId);
                        ps.setLong(2, freelancerId);
                        ps.setString(3, acceptedCover);
                        ps.setBigDecimal(4, amount);
                        ps.executeUpdate();
                    }
                }

                List<Long> invitedFreelancers = new ArrayList<>();
                try (PreparedStatement ps = connection.prepareStatement(
                        "SELECT freelancer_id FROM project_invitations WHERE project_id = ? "
                        + "AND invitation_id <> ? AND status = 'PENDING' FOR UPDATE")) {
                    ps.setLong(1, projectId);
                    ps.setLong(2, invitationId);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) invitedFreelancers.add(rs.getLong("freelancer_id"));
                    }
                }
                try (PreparedStatement ps = connection.prepareStatement(
                        "UPDATE project_invitations SET status = 'CANCELLED' WHERE project_id = ? "
                        + "AND status = 'PENDING' AND invitation_id <> ?")) {
                    ps.setLong(1, projectId);
                    ps.setLong(2, invitationId);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = connection.prepareStatement(
                        "UPDATE project_invitations SET status = 'ACCEPTED' WHERE invitation_id = ? AND status = 'PENDING'")) {
                    ps.setLong(1, invitationId);
                    if (ps.executeUpdate() != 1) throw new SQLException("Invitation could not be accepted.");
                }
                try (PreparedStatement ps = connection.prepareStatement(
                        "UPDATE projects SET status = 'IN_PROGRESS' WHERE project_id = ? AND status = 'OPEN'")) {
                    ps.setLong(1, projectId);
                    if (ps.executeUpdate() != 1) throw new SQLException("Project could not be started.");
                }
                NotificationUtil.create(connection, clientId, projectId,
                        "PROJECT_INVITATION_ACCEPTED", "Freelancer accepted your offer",
                        "Your invitation for “" + projectTitle + "” was accepted. The project is now in progress.");
                for (Long otherId : otherFreelancers) {
                    if (otherId.longValue() == freelancerId) continue;
                    NotificationUtil.create(connection, otherId, projectId,
                            "PROPOSAL_NOT_SELECTED", "Project assigned",
                            "The client selected another freelancer for “" + projectTitle + "”.");
                }
                for (Long invitedId : invitedFreelancers) {
                    NotificationUtil.create(connection, invitedId, projectId,
                            "PROJECT_INVITATION_CANCELLED", "Project invitation closed",
                            "Another freelancer was selected for “" + projectTitle + "”.");
                }
                connection.commit();
                send(response, HttpServletResponse.SC_OK,
                        new JSONObject().put("message", "You accepted the invitation. The project is now in progress."));
            } catch (SQLException exception) {
                rollback(connection, exception);
                throw exception;
            }
        } catch (SQLException exception) {
            log("Unable to process project invitation.", exception);
            send(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    error("Unable to process the invitation right now."));
        }
    }

    private Connection getConnection() throws SQLException {
        String username = System.getenv("DB_USERNAME");
        String password = System.getenv("DB_PASSWORD");
        if (username == null || username.isBlank() || password == null || password.isEmpty()) {
            throw new SQLException("Database configuration is missing.");
        }
        return DatabaseUtil.getConnection();
    }

    private static boolean hasUser(HttpSession session) {
        return session != null && session.getAttribute("userId") instanceof Number;
    }

    private static Long parseId(String value) {
        if (value == null || !ID_PATTERN.matcher(value).matches()) return null;
        try { return Long.valueOf(value); } catch (NumberFormatException exception) { return null; }
    }

    private static boolean isSameOriginRequest(HttpServletRequest request) {
        String origin = request.getHeader("Origin");
        if (origin == null || origin.isBlank()) origin = request.getHeader("Referer");
        if (origin == null || origin.isBlank()) return false;
        try {
            URI uri = URI.create(origin);
            int port = uri.getPort() < 0 ? ("https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80) : uri.getPort();
            int requestPort = request.getServerPort();
            return request.getScheme().equalsIgnoreCase(uri.getScheme())
                    && request.getServerName().equalsIgnoreCase(uri.getHost())
                    && port == requestPort;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static void setJson(HttpServletResponse response) {
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");
    }

    private static void send(HttpServletResponse response, int status, JSONObject body) throws IOException {
        response.setStatus(status);
        response.getWriter().write(body.toString());
    }

    private static JSONObject error(String message) { return new JSONObject().put("error", message); }
    private static String safe(String value) { return value == null ? "" : value; }

    private static JSONObject organizationFrom(ResultSet rs) throws SQLException {
        return new JSONObject()
                .put("organizationName", safe(rs.getString("client_organization_name")))
                .put("organizationType", safe(rs.getString("client_organization_type")))
                .put("industry", safe(rs.getString("client_industry")))
                .put("website", safe(rs.getString("client_website")))
                .put("country", safe(rs.getString("client_country")))
                .put("city", safe(rs.getString("client_city")))
                .put("businessDescription", safe(rs.getString("client_business_description")));
    }

    private static void rollback(Connection connection, SQLException exception) {
        try { connection.rollback(); } catch (SQLException rollbackError) { exception.addSuppressed(rollbackError); }
    }
}
