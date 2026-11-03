
package com.worknest.servlet;

import com.worknest.util.DatabaseUtil;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

public class MyProjectsServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;


    private static final String SELECT_PROJECTS_SQL =
            "SELECT project_id, title, description, required_skills, "
            + "project_type, budget_min, budget_max, deadline, "
            + "status, created_at, "
            + "CASE WHEN projects.status = 'OPEN' THEN "
            + "(SELECT COUNT(*) FROM proposals total_proposals "
            + " WHERE total_proposals.project_id = projects.project_id) ELSE 0 END AS proposal_count, "
            + "CASE WHEN projects.status = 'OPEN' THEN "
            + "(SELECT COUNT(*) FROM proposals pending_proposals "
            + " WHERE pending_proposals.project_id = projects.project_id "
            + " AND pending_proposals.status = 'PENDING') ELSE 0 END AS pending_proposal_count "
            + "FROM projects "
            + "WHERE client_id = ? "
            + "ORDER BY created_at DESC";

    @Override
    protected void doGet(
            HttpServletRequest req,
            HttpServletResponse res) throws IOException {

        res.setContentType("application/json");
        res.setCharacterEncoding("UTF-8");
        res.setHeader("Cache-Control", "no-store");

        // Check whether a session exists.
        HttpSession session = req.getSession(false);

        if (session == null
                || !(session.getAttribute("userId") instanceof Long)) {

            sendJsonError(
                    res,
                    HttpServletResponse.SC_UNAUTHORIZED,
                    "Your session has expired. Please log in again."
            );
            return;
        }

        // Only clients can view their posted projects.
        if (!"CLIENT".equals(session.getAttribute("role"))) {

            sendJsonError(
                    res,
                    HttpServletResponse.SC_FORBIDDEN,
                    "You don't have permission to view these projects."
            );
            return;
        }

        long clientId = (Long) session.getAttribute("userId");
        long requestStarted = System.nanoTime();
        long queryElapsedMs = -1L;
        int rowCount = 0;

        // Read database credentials from environment variables.
        String dbUsername = System.getenv("DB_USERNAME");
        String dbPassword = System.getenv("DB_PASSWORD");

        if (dbUsername == null || dbUsername.isBlank()
                || dbPassword == null || dbPassword.isEmpty()) {

            log("Database credentials are not configured.");

            sendJsonError(
                    res,
                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Unable to load projects right now. Please try again later."
            );
            return;
        }

        StringBuilder json = new StringBuilder();
        json.append("{\"projects\":[");

        try {
            try (Connection connection = DatabaseUtil.getConnection();
                 PreparedStatement statement =
                        connection.prepareStatement(SELECT_PROJECTS_SQL)) {

                statement.setLong(1, clientId);
                long queryStarted = System.nanoTime();
                try (ResultSet rs = statement.executeQuery()) {
                    queryElapsedMs = (System.nanoTime() - queryStarted) / 1_000_000L;
                    boolean first = true;

                    while (rs.next()) {
                        rowCount++;

                        if (!first) {
                            json.append(",");
                        }

                        json.append("{")
                            .append("\"projectId\":")
                            .append(rs.getLong("project_id"))

                            .append(",\"title\":")
                            .append(toJsonString(rs.getString("title")))

                            .append(",\"description\":")
                            .append(toJsonString(
                                    rs.getString("description")))

                            .append(",\"requiredSkills\":")
                            .append(toJsonString(
                                    rs.getString("required_skills")))

                            .append(",\"projectType\":")
                            .append(toJsonString(
                                    rs.getString("project_type")))

                            .append(",\"budgetMin\":")
                            .append(rs.getBigDecimal(
                                    "budget_min").toPlainString())

                            .append(",\"budgetMax\":")
                            .append(rs.getBigDecimal(
                                    "budget_max").toPlainString())

                            .append(",\"deadline\":")
                            .append(toJsonString(
                                    rs.getString("deadline")))

                            .append(",\"status\":")
                            .append(toJsonString(
                                    rs.getString("status")))

                            .append(",\"createdAt\":")
                            .append(toJsonString(
                                    rs.getString("created_at")))

                            .append(",\"proposalCount\":")
                            .append(rs.getLong("proposal_count"))

                            .append(",\"pendingProposalCount\":")
                            .append(rs.getLong("pending_proposal_count"))

                            .append("}");

                        first = false;
                    }
                }
            }

            json.append("]}");

            res.setStatus(HttpServletResponse.SC_OK);
            res.getWriter().write(json.toString());
            log("PERF path=/my-projects dbExecuteMs=" + queryElapsedMs
                    + " rows=" + rowCount
                    + " totalMs=" + ((System.nanoTime() - requestStarted) / 1_000_000L));

        } catch (SQLException e) {

            // Keep technical details in the server logs only.
            log("Error retrieving client projects.", e);

            sendJsonError(
                    res,
                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Something went wrong while loading your projects. Please try again."
            );
        }
    }

    // Sends a JSON error response instead of Tomcat's default error page.
    private void sendJsonError(
            HttpServletResponse res,
            int status,
            String message) throws IOException {

        res.setStatus(status);
        res.setContentType("application/json");
        res.setCharacterEncoding("UTF-8");
        res.setHeader("Cache-Control", "no-store");

        res.getWriter().write(
                "{\"error\":" + toJsonString(message) + "}"
        );
    }

    // Escapes strings correctly before adding them to JSON.
    private String toJsonString(String value) {

        if (value == null) {
            return "null";
        }

        StringBuilder escaped = new StringBuilder("\"");

        for (int i = 0; i < value.length(); i++) {

            char ch = value.charAt(i);

            switch (ch) {
                case '"':
                    escaped.append("\\\"");
                    break;

                case '\\':
                    escaped.append("\\\\");
                    break;

                case '\b':
                    escaped.append("\\b");
                    break;

                case '\f':
                    escaped.append("\\f");
                    break;

                case '\n':
                    escaped.append("\\n");
                    break;

                case '\r':
                    escaped.append("\\r");
                    break;

                case '\t':
                    escaped.append("\\t");
                    break;

                default:
                    if (ch < 0x20) {
                        escaped.append(
                                String.format("\\u%04x", (int) ch));
                    } else {
                        escaped.append(ch);
                    }
            }
        }

        escaped.append("\"");
        return escaped.toString();
    }
}
