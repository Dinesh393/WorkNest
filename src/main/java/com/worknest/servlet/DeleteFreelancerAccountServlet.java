package com.worknest.servlet;

import com.worknest.util.DatabaseUtil;

import java.io.IOException;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.json.JSONObject;

import com.worknest.util.PasswordUtil;

/**
 * Deletes a freelancer account the same way client accounts are handled:
 * personal details are removed and the row is anonymized, so project history,
 * proposals, updates and submissions that other users rely on stay intact.
 *
 * GET  /delete-freelancer-account  returns a CSRF token for the delete form.
 * POST /delete-freelancer-account  verifies the password and performs the deletion.
 */
public class DeleteFreelancerAccountServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final String CSRF_ATTRIBUTE = "freelancerAccountCsrfToken";
    private static final SecureRandom RANDOM = new SecureRandom();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res)
            throws IOException {
        prepareJson(res);
        HttpSession session = req.getSession(false);
        if (getFreelancerId(session) == null) {
            writeError(res, HttpServletResponse.SC_UNAUTHORIZED,
                    "Please sign in with a freelancer account.");
            return;
        }
        JSONObject result = new JSONObject();
        result.put("success", true);
        result.put("csrfToken", csrfToken(session));
        res.getWriter().write(result.toString());
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res)
            throws IOException {
        req.setCharacterEncoding("UTF-8");
        prepareJson(res);

        HttpSession session = req.getSession(false);
        Long userId = getFreelancerId(session);
        if (userId == null) {
            writeError(res, HttpServletResponse.SC_UNAUTHORIZED,
                    "Please sign in again before deleting your account.");
            return;
        }
        if (!validCsrf(session, req.getParameter("csrfToken"))) {
            writeError(res, HttpServletResponse.SC_FORBIDDEN,
                    "This form expired. Refresh the page and try again.");
            return;
        }

        String password = req.getParameter("currentPassword");
        String confirmation = req.getParameter("confirmation");
        if (password == null || password.length() < 1 || password.length() > 100
                || !"DELETE".equals(confirmation)) {
            writeError(res, HttpServletResponse.SC_BAD_REQUEST,
                    "Enter your current password and type DELETE to confirm.");
            return;
        }

        String dbUsername = System.getenv("DB_USERNAME");
        String dbPassword = System.getenv("DB_PASSWORD");
        if (dbUsername == null || dbUsername.isBlank()
                || dbPassword == null || dbPassword.isEmpty()) {
            writeError(res, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Account deletion is temporarily unavailable.");
            return;
        }

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException exception) {
            getServletContext().log("MySQL JDBC driver not found for account deletion.", exception);
            writeError(res, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Account deletion is temporarily unavailable.");
            return;
        }

        try (Connection connection = DatabaseUtil.getConnection()) {
            connection.setAutoCommit(false);
            try {
                // 1. Lock the freelancer row and verify the password.
                String passwordHash;
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT password_hash FROM users "
                      + "WHERE user_id = ? AND role = 'FREELANCER' FOR UPDATE")) {
                    statement.setLong(1, userId);
                    try (ResultSet result = statement.executeQuery()) {
                        if (!result.next()) {
                            connection.rollback();
                            writeError(res, HttpServletResponse.SC_UNAUTHORIZED,
                                    "Your freelancer account could not be verified. Please sign in again.");
                            return;
                        }
                        passwordHash = result.getString("password_hash");
                    }
                }

                if (!PasswordUtil.verifyPassword(password, passwordHash)) {
                    connection.rollback();
                    writeError(res, HttpServletResponse.SC_BAD_REQUEST,
                            "The password did not match. Your account has not been changed.");
                    return;
                }

                // 2. Do not allow deletion while accepted work is still in progress.
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT COUNT(*) FROM proposals pr "
                      + "JOIN projects p ON p.project_id = pr.project_id "
                      + "WHERE pr.freelancer_id = ? AND pr.status = 'ACCEPTED' "
                      + "AND p.status = 'IN_PROGRESS'")) {
                    statement.setLong(1, userId);
                    try (ResultSet result = statement.executeQuery()) {
                        result.next();
                        if (result.getInt(1) > 0) {
                            connection.rollback();
                            writeError(res, HttpServletResponse.SC_CONFLICT,
                                    "Finish or hand over your in-progress projects before deleting this account.");
                            return;
                        }
                    }
                }

                // 3. Anonymize the user row (keeps foreign keys valid for project history).
                String replacementEmail = "deleted-" + userId + "-"
                        + UUID.randomUUID().toString().replace("-", "")
                        + "@worknest.invalid";
                String replacementPassword = UUID.randomUUID().toString()
                        + UUID.randomUUID().toString();
                String replacementHash = PasswordUtil.hashPassword(replacementPassword);

                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE users SET full_name = ?, email = ?, password_hash = ? "
                      + "WHERE user_id = ? AND role = 'FREELANCER'")) {
                    statement.setString(1, "Deleted WorkNest account");
                    statement.setString(2, replacementEmail);
                    statement.setString(3, replacementHash);
                    statement.setLong(4, userId);
                    if (statement.executeUpdate() != 1) {
                        connection.rollback();
                        writeError(res, HttpServletResponse.SC_CONFLICT,
                                "The account could not be updated. Refresh and try again.");
                        return;
                    }
                }

                // 4. Remove personal and still-open data.
                try (PreparedStatement statement = connection.prepareStatement(
                        "DELETE FROM freelancer_profiles WHERE user_id = ?")) {
                    statement.setLong(1, userId);
                    statement.executeUpdate();
                }
                try (PreparedStatement statement = connection.prepareStatement(
                        "DELETE FROM notifications WHERE user_id = ?")) {
                    statement.setLong(1, userId);
                    statement.executeUpdate();
                }
                // Withdraw pending proposals so clients cannot accept a deleted account.
                try (PreparedStatement statement = connection.prepareStatement(
                        "DELETE FROM proposals WHERE freelancer_id = ? AND status = 'PENDING'")) {
                    statement.setLong(1, userId);
                    statement.executeUpdate();
                }
                // Remove direct offers that were still waiting for an answer.
                try (PreparedStatement statement = connection.prepareStatement(
                        "DELETE FROM project_invitations WHERE freelancer_id = ? AND status = 'PENDING'")) {
                    statement.setLong(1, userId);
                    statement.executeUpdate();
                }

                connection.commit();
                session.invalidate();
                JSONObject result = new JSONObject();
                result.put("success", true);
                result.put("message", "Your account details were removed. Project history is retained without your personal details.");
                result.put("redirect", req.getContextPath() + "/index.html?accountDeleted=true");
                res.getWriter().write(result.toString());
            } catch (Exception exception) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackException) {
                    exception.addSuppressed(rollbackException);
                }
                throw exception;
            }
        } catch (Exception exception) {
            getServletContext().log("Unable to delete freelancer account.", exception);
            writeError(res, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Your account could not be deleted. No account changes were saved.");
        }
    }

    private static Long getFreelancerId(HttpSession session) {
        if (session == null || !"FREELANCER".equals(session.getAttribute("role"))) {
            return null;
        }
        Object value = session.getAttribute("userId");
        return value instanceof Number ? ((Number) value).longValue() : null;
    }

    private static String csrfToken(HttpSession session) {
        synchronized (session) {
            Object existing = session.getAttribute(CSRF_ATTRIBUTE);
            if (existing instanceof String) return (String) existing;
            byte[] bytes = new byte[32];
            RANDOM.nextBytes(bytes);
            StringBuilder encoded = new StringBuilder(bytes.length * 2);
            for (byte value : bytes) {
                encoded.append(String.format("%02x", value & 0xff));
            }
            String token = encoded.toString();
            session.setAttribute(CSRF_ATTRIBUTE, token);
            return token;
        }
    }

    private static boolean validCsrf(HttpSession session, String submitted) {
        if (session == null || submitted == null) return false;
        Object expected = session.getAttribute(CSRF_ATTRIBUTE);
        return expected instanceof String
                && java.security.MessageDigest.isEqual(
                        ((String) expected).getBytes(java.nio.charset.StandardCharsets.UTF_8),
                        submitted.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static void prepareJson(HttpServletResponse res) {
        res.setCharacterEncoding("UTF-8");
        res.setContentType("application/json");
        res.setHeader("Cache-Control", "no-store");
    }

    private static void writeError(HttpServletResponse res, int status, String message)
            throws IOException {
        res.setStatus(status);
        JSONObject error = new JSONObject();
        error.put("success", false);
        error.put("error", message);
        error.put("message", message);
        res.getWriter().write(error.toString());
    }
}
