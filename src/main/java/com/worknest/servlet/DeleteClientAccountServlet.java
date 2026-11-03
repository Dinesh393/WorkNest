package com.worknest.servlet;

import com.worknest.util.DatabaseUtil;

import java.io.IOException;
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

/** Deactivates a client account while preserving project history and references. */
public class DeleteClientAccountServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res)
            throws IOException {
        req.setCharacterEncoding("UTF-8");
        ClientAccountServlet.prepareJson(res);

        HttpSession session = req.getSession(false);
        Long userId = ClientAccountServlet.getClientId(session);
        if (userId == null) {
            ClientAccountServlet.writeError(res, HttpServletResponse.SC_UNAUTHORIZED,
                    "Please sign in again before deleting your account.");
            return;
        }
        if (!ClientAccountServlet.validCsrf(session, req.getParameter("csrfToken"))) {
            ClientAccountServlet.writeError(res, HttpServletResponse.SC_FORBIDDEN,
                    "This form expired. Refresh the page and try again.");
            return;
        }

        String password = req.getParameter("currentPassword");
        String confirmation = req.getParameter("confirmation");
        if (password == null || password.length() < 1 || password.length() > 100
                || !"DELETE".equals(confirmation)) {
            ClientAccountServlet.writeError(res, HttpServletResponse.SC_BAD_REQUEST,
                    "Enter your current password and type DELETE to confirm.");
            return;
        }

        String dbUsername = System.getenv("DB_USERNAME");
        String dbPassword = System.getenv("DB_PASSWORD");
        if (dbUsername == null || dbUsername.isBlank()
                || dbPassword == null || dbPassword.isEmpty()) {
            ClientAccountServlet.writeError(res, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Account deletion is temporarily unavailable.");
            return;
        }

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException exception) {
            getServletContext().log("MySQL JDBC driver not found for account deletion.", exception);
            ClientAccountServlet.writeError(res, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Account deletion is temporarily unavailable.");
            return;
        }

        try (Connection connection = DatabaseUtil.getConnection()) {
            connection.setAutoCommit(false);
            try {
                String passwordHash;
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT password_hash FROM users WHERE user_id = ? AND role = 'CLIENT' FOR UPDATE")) {
                    statement.setLong(1, userId);
                    try (ResultSet result = statement.executeQuery()) {
                        if (!result.next()) {
                            connection.rollback();
                            ClientAccountServlet.writeError(res, HttpServletResponse.SC_UNAUTHORIZED,
                                    "Your client account could not be verified. Please sign in again.");
                            return;
                        }
                        passwordHash = result.getString("password_hash");
                    }
                }

                if (!PasswordUtil.verifyPassword(password, passwordHash)) {
                    connection.rollback();
                    ClientAccountServlet.writeError(res, HttpServletResponse.SC_BAD_REQUEST,
                            "The password did not match. Your account has not been changed.");
                    return;
                }

                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT COUNT(*) FROM projects WHERE client_id = ? "
                      + "AND status IN ('OPEN', 'IN_PROGRESS')")) {
                    statement.setLong(1, userId);
                    try (ResultSet result = statement.executeQuery()) {
                        result.next();
                        if (result.getInt(1) > 0) {
                            connection.rollback();
                            ClientAccountServlet.writeError(res, HttpServletResponse.SC_CONFLICT,
                                    "Close or complete your open projects before deleting this account.");
                            return;
                        }
                    }
                }

                String replacementEmail = "deleted-" + userId + "-"
                        + UUID.randomUUID().toString().replace("-", "")
                        + "@worknest.invalid";
                String replacementPassword = UUID.randomUUID().toString()
                        + UUID.randomUUID().toString();
                String replacementHash = PasswordUtil.hashPassword(replacementPassword);

                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE users SET full_name = ?, email = ?, password_hash = ? "
                      + "WHERE user_id = ? AND role = 'CLIENT'")) {
                    statement.setString(1, "Deleted WorkNest account");
                    statement.setString(2, replacementEmail);
                    statement.setString(3, replacementHash);
                    statement.setLong(4, userId);
                    if (statement.executeUpdate() != 1) {
                        connection.rollback();
                        ClientAccountServlet.writeError(res, HttpServletResponse.SC_CONFLICT,
                                "The account could not be updated. Refresh and try again.");
                        return;
                    }
                }

                try (PreparedStatement statement = connection.prepareStatement(
                        "DELETE FROM client_profiles WHERE client_id = ?")) {
                    statement.setLong(1, userId);
                    statement.executeUpdate();
                }
                try (PreparedStatement statement = connection.prepareStatement(
                        "DELETE FROM notifications WHERE user_id = ?")) {
                    statement.setLong(1, userId);
                    statement.executeUpdate();
                }
                try (PreparedStatement statement = connection.prepareStatement(
                        "DELETE FROM project_invitations WHERE client_id = ?")) {
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
            getServletContext().log("Unable to delete client account.", exception);
            ClientAccountServlet.writeError(res, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Your account could not be deleted. No account changes were saved.");
        }
    }
}
