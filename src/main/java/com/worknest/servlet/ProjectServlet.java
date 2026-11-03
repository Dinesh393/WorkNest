
package com.worknest.servlet;

import com.worknest.util.DatabaseUtil;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

public class ProjectServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;


    private static final String INSERT_PROJECT_SQL =
            "INSERT INTO projects "
            + "(client_id, title, description, required_skills, "
            + "project_type, budget_min, budget_max, deadline) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

    @Override
    protected void doPost(
            HttpServletRequest req,
            HttpServletResponse res) throws IOException {

        req.setCharacterEncoding("UTF-8");

        String contextPath = req.getContextPath();

        String formUrl = contextPath
                + "/dashboard/client/create-project.html";

        // 1. Check whether the user is logged in.
        HttpSession session = req.getSession(false);

        if (session == null
                || !(session.getAttribute("userId") instanceof Long)) {

            res.sendRedirect(contextPath + "/login.html?error=session");
            return;
        }

        // 2. Only clients can create projects.
        if (!"CLIENT".equals(session.getAttribute("role"))) {
            res.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        long clientId = (Long) session.getAttribute("userId");
        
        

        // 3. Read form values.
        String title = req.getParameter("title");
        String description = req.getParameter("description");
        String requiredSkills = req.getParameter("requiredSkills");
        String projectType = req.getParameter("projectType");
        String budgetMinInput = req.getParameter("budgetMin");
        String budgetMaxInput = req.getParameter("budgetMax");
        String deadlineInput = req.getParameter("deadline");

        // 4. Validate project title.
        if (title == null || title.isBlank()
                || title.trim().length() < 5
                || title.trim().length() > 150) {

            res.sendRedirect(formUrl + "?error=title");
            return;
        }

        title = title.trim();

        // 5. Validate description.
        if (description == null || description.isBlank()
                || description.trim().length() < 30
                || description.trim().length() > 10000) {

            res.sendRedirect(formUrl + "?error=description");
            return;
        }

        description = description.trim();

        // 6. Validate required skills.
        if (requiredSkills == null || requiredSkills.isBlank()
                || requiredSkills.trim().length() > 500) {

            res.sendRedirect(formUrl + "?error=skills");
            return;
        }

        requiredSkills = requiredSkills.trim();

        // 7. Validate project type.
        if (!"FIXED".equals(projectType)
                && !"HOURLY".equals(projectType)) {

            res.sendRedirect(formUrl + "?error=type");
            return;
        }

        // 8. Validate budgets.
        BigDecimal budgetMin;
        BigDecimal budgetMax;

        try {
            if (budgetMinInput == null || budgetMaxInput == null
                    || budgetMinInput.isBlank()
                    || budgetMaxInput.isBlank()) {

                throw new NumberFormatException();
            }

            budgetMin = new BigDecimal(budgetMinInput);
            budgetMax = new BigDecimal(budgetMaxInput);

            BigDecimal maximumAllowed =
                    new BigDecimal("9999999999.99");

            if (budgetMin.compareTo(BigDecimal.ZERO) <= 0
                    || budgetMax.compareTo(budgetMin) < 0
                    || budgetMin.compareTo(maximumAllowed) > 0
                    || budgetMax.compareTo(maximumAllowed) > 0
                    || budgetMin.scale() > 2
                    || budgetMax.scale() > 2) {

                res.sendRedirect(formUrl + "?error=budget");
                return;
            }

        } catch (NumberFormatException e) {
            res.sendRedirect(formUrl + "?error=budget");
            return;
        }

        // 9. Validate deadline.
        LocalDate deadline;

        try {
            if (deadlineInput == null || deadlineInput.isBlank()) {
                throw new DateTimeParseException(
                        "Missing deadline", "", 0);
            }

            deadline = LocalDate.parse(deadlineInput);

            if (deadline.isBefore(LocalDate.now())) {
                res.sendRedirect(formUrl + "?error=deadline");
                return;
            }

        } catch (DateTimeParseException e) {
            res.sendRedirect(formUrl + "?error=deadline");
            return;
        }

        // 10. Read database credentials from environment variables.
        String dbUsername = System.getenv("DB_USERNAME");
        String dbPassword = System.getenv("DB_PASSWORD");

        if (dbUsername == null || dbUsername.isBlank()
                || dbPassword == null || dbPassword.isEmpty()) {

            log("Database credentials are not configured.");

            res.sendRedirect(formUrl + "?error=server");
            return;
        }

        // 11. Insert the project using a PreparedStatement.
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");

            try (Connection connection = DatabaseUtil.getConnection();
                 PreparedStatement statement =
                        connection.prepareStatement(INSERT_PROJECT_SQL)) {

                statement.setLong(1, clientId);
                statement.setString(2, title);
                statement.setString(3, description);
                statement.setString(4, requiredSkills);
                statement.setString(5, projectType);
                statement.setBigDecimal(6, budgetMin);
                statement.setBigDecimal(7, budgetMax);
                statement.setDate(8, Date.valueOf(deadline));

                int rowsInserted = statement.executeUpdate();

                if (rowsInserted == 1) {
                    res.sendRedirect(contextPath
                            + "/dashboard/client/index.html?projectCreated=true");
                    return;
                }

                log("Project insert did not insert exactly one row.");
                res.sendRedirect(formUrl + "?error=server");
            }

        } catch (ClassNotFoundException | SQLException e) {
            log("Error while creating project.", e);
            res.sendRedirect(formUrl + "?error=server");
        }
    }
}
