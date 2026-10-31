package com.worknest.servlet;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.json.JSONArray;
import org.json.JSONObject;

/** Public, client-only view of freelancer profile information. */
public class FreelancerDirectoryServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final String DB_URL = "jdbc:mysql://localhost:3306/worknest_db";
    private static final int RESULT_LIMIT = 100;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");

        HttpSession session = request.getSession(false);
        if (session == null || !"CLIENT".equals(session.getAttribute("role"))) {
            response.setStatus(session == null
                    ? HttpServletResponse.SC_UNAUTHORIZED
                    : HttpServletResponse.SC_FORBIDDEN);
            response.getWriter().write(new JSONObject()
                    .put("error", "Only clients can browse freelancer profiles.")
                    .toString());
            return;
        }

        String search = request.getParameter("q");
        if (search == null) search = "";
        search = search.trim();
        if (search.length() > 120) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write(new JSONObject()
                    .put("error", "Search must be 120 characters or fewer.")
                    .toString());
            return;
        }

        String username = System.getenv("DB_USERNAME");
        String password = System.getenv("DB_PASSWORD");
        if (username == null || username.isBlank()
                || password == null || password.isEmpty()) {
            getServletContext().log("Database credentials are not configured.");
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write(new JSONObject()
                    .put("error", "Freelancers are unavailable right now.")
                    .toString());
            return;
        }

        String sql = "SELECT u.user_id, u.full_name, fp.headline, fp.overview, "
                + "fp.skills, fp.experience_years, fp.location, fp.availability, "
                + "fp.hourly_rate, fp.portfolio_url, fp.linkedin_url, fp.github_url, "
                + "fp.updated_at FROM users u "
                + "INNER JOIN freelancer_profiles fp ON fp.user_id = u.user_id "
                + "WHERE u.role = 'FREELANCER' "
                + "AND (TRIM(fp.headline) <> '' OR TRIM(fp.overview) <> '' "
                + "OR TRIM(fp.skills) <> '') "
                + "AND (? = '' OR CONCAT_WS(' ', u.full_name, fp.headline, "
                + "fp.overview, fp.skills, fp.location) LIKE ?) "
                + "ORDER BY fp.updated_at DESC, u.full_name ASC LIMIT ?";

        JSONArray freelancers = new JSONArray();
        try (Connection connection = DriverManager.getConnection(DB_URL, username, password);
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, search);
            statement.setString(2, "%" + search + "%");
            statement.setInt(3, RESULT_LIMIT);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    JSONObject profile = new JSONObject();
                    profile.put("userId", rs.getLong("user_id"));
                    profile.put("fullName", safe(rs.getString("full_name")));
                    profile.put("headline", safe(rs.getString("headline")));
                    profile.put("overview", safe(rs.getString("overview")));
                    profile.put("skills", safe(rs.getString("skills")));
                    int years = rs.getInt("experience_years");
                    profile.put("experienceYears", rs.wasNull() ? JSONObject.NULL : years);
                    profile.put("location", safe(rs.getString("location")));
                    profile.put("availability", safe(rs.getString("availability")));
                    java.math.BigDecimal rate = rs.getBigDecimal("hourly_rate");
                    profile.put("hourlyRate", rate == null ? JSONObject.NULL : rate);
                    profile.put("portfolioUrl", safe(rs.getString("portfolio_url")));
                    profile.put("linkedinUrl", safe(rs.getString("linkedin_url")));
                    profile.put("githubUrl", safe(rs.getString("github_url")));
                    freelancers.put(profile);
                }
            }
        } catch (SQLException exception) {
            getServletContext().log("Unable to load freelancer directory.", exception);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write(new JSONObject()
                    .put("error", "Freelancers are unavailable right now.")
                    .toString());
            return;
        }

        response.setStatus(HttpServletResponse.SC_OK);
        response.getWriter().write(new JSONObject()
                .put("freelancers", freelancers)
                .put("limit", RESULT_LIMIT)
                .toString());
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
