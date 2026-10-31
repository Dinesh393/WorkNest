
package com.worknest.servlet;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.worknest.util.PasswordUtil;

public class RegisterServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    private static final String DB_URL =
            "jdbc:mysql://localhost:3306/worknest_db";

    @Override
    protected void doPost(
            HttpServletRequest req,
            HttpServletResponse res) throws IOException {

        req.setCharacterEncoding("UTF-8");

        String fullName = req.getParameter("fullName");
        String email = req.getParameter("email");
        String password = req.getParameter("password");
        String confirmPassword = req.getParameter("confirmPassword");
        String role = req.getParameter("role");
        String termsAccepted = req.getParameter("termsAccepted");

        String registerUrl = req.getContextPath() + "/register.html";

        // 1. Validate full name
        if (fullName == null || fullName.isBlank()) {
            res.sendRedirect(registerUrl + "?error=name");
            return;
        }

        fullName = fullName.trim();

        if (fullName.length() > 100) {
            res.sendRedirect(registerUrl + "?error=name");
            return;
        }

        // 2. Validate email
        if (email == null || email.isBlank()) {
            res.sendRedirect(registerUrl + "?error=email");
            return;
        }

        email = email.trim();

        if (email.length() > 255
                || !email.matches(
                    "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {

            res.sendRedirect(registerUrl + "?error=email");
            return;
        }

        // 3. Validate password
        if (password == null || password.isBlank()) {
            res.sendRedirect(registerUrl + "?error=password");
            return;
        }

        if (password.length() < 6 || password.length() > 100) {
            res.sendRedirect(registerUrl + "?error=password");
            return;
        }

        // 4. Validate password confirmation
        if (confirmPassword == null
                || !password.equals(confirmPassword)) {

            res.sendRedirect(registerUrl + "?error=confirmPassword");
            return;
        }

        // 5. Validate role
        if (!"FREELANCER".equals(role)
                && !"CLIENT".equals(role)) {

            res.sendRedirect(registerUrl + "?error=role");
            return;
        }

        // 6. Validate terms acceptance
        if (!"true".equals(termsAccepted)) {
            res.sendRedirect(registerUrl + "?error=terms");
            return;
        }

        // 7. Read database credentials
        String dbUsername = System.getenv("DB_USERNAME");
        String dbPassword = System.getenv("DB_PASSWORD");

        if (dbUsername == null || dbUsername.isBlank()
                || dbPassword == null || dbPassword.isEmpty()) {

            log("Database credentials are not configured.");

            res.sendRedirect(registerUrl + "?error=server");
            return;
        }

        // 8. Hash password
        final String passwordHash;

        try {
            passwordHash = PasswordUtil.hashPassword(password);
        } catch (RuntimeException e) {
            log("Password hashing failed.", e);

            res.sendRedirect(registerUrl + "?error=server");
            return;
        }

        // 9. Insert user into database
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");

            String sql = "INSERT INTO users "
                    + "(full_name, email, password_hash, role) "
                    + "VALUES (?, ?, ?, ?)";

            try (Connection connection = DriverManager.getConnection(
                        DB_URL, dbUsername, dbPassword);
                 PreparedStatement statement =
                        connection.prepareStatement(sql)) {

                statement.setString(1, fullName);
                statement.setString(2, email);
                statement.setString(3, passwordHash);
                statement.setString(4, role);

                int rowsInserted = statement.executeUpdate();

                if (rowsInserted == 1) {
                    res.sendRedirect(
                    req.getContextPath() + "/login.html?registered=true");
                    return;
                }

                log("Registration did not insert exactly one row.");

                res.sendRedirect(registerUrl + "?error=server");
            }

        } catch (ClassNotFoundException e) {
            log("MySQL JDBC driver not found.", e);

            res.sendRedirect(registerUrl + "?error=server");

        } catch (SQLException e) {
            if (e.getErrorCode() == 1062
                    && "23000".equals(e.getSQLState())) {

                res.sendRedirect(registerUrl + "?error=exists");

            } else {
                log("Database error during registration.", e);

                res.sendRedirect(registerUrl + "?error=server");
            }
        }
    }
}
