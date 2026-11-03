package com.worknest.servlet;

import com.worknest.util.DatabaseUtil;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.json.JSONException;
import org.json.JSONObject;

public class FreelancerProfileServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final int MAX_BODY_CHARS = 16000;
    private static final BigDecimal MAX_RATE = new BigDecimal("99999999.99");

    @Override
    protected void doGet(
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {

        prepareJson(response);
        HttpSession session = request.getSession(false);
        Long sessionUserId = getUserId(session);

        if (sessionUserId == null) {
            sendError(response, HttpServletResponse.SC_UNAUTHORIZED,
                    "Please log in to view this profile.");
            return;
        }

        String role = String.valueOf(session.getAttribute("role"));
        long targetUserId = sessionUserId;

        if ("CLIENT".equals(role)) {
            String requestedId = request.getParameter("userId");
            if (!isPositiveId(requestedId)) {
                sendError(response, HttpServletResponse.SC_BAD_REQUEST,
                        "A valid freelancer ID is required.");
                return;
            }
            try {
                targetUserId = Long.parseLong(requestedId);
            } catch (NumberFormatException exception) {
                sendError(response, HttpServletResponse.SC_BAD_REQUEST,
                        "A valid freelancer ID is required.");
                return;
            }
        } else if (!"FREELANCER".equals(role)) {
            sendError(response, HttpServletResponse.SC_FORBIDDEN,
                    "This account cannot view freelancer profiles.");
            return;
        } else {
            String requestedId = request.getParameter("userId");
            if (requestedId != null && (!isPositiveId(requestedId)
                    || !requestedId.equals(String.valueOf(sessionUserId)))) {
                sendError(response, HttpServletResponse.SC_FORBIDDEN,
                        "You can only view your own profile.");
                return;
            }
        }

        String username = System.getenv("DB_USERNAME");
        String password = System.getenv("DB_PASSWORD");
        if (username == null || username.isBlank()
                || password == null || password.isEmpty()) {
            getServletContext().log("Database credentials are not configured.");
            sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Unable to load the profile right now.");
            return;
        }

        String sql = "SELECT u.full_name, u.email, fp.headline, fp.overview, fp.skills, "
                + "fp.experience_years, fp.experience, fp.education, fp.certifications, "
                + "fp.languages, fp.location, fp.availability, fp.phone, fp.hourly_rate, "
                + "fp.portfolio_url, fp.linkedin_url, fp.github_url, fp.updated_at "
                + "FROM users u LEFT JOIN freelancer_profiles fp "
                + "ON fp.user_id = u.user_id "
                + "WHERE u.user_id = ? AND u.role = 'FREELANCER'";

        try (Connection connection = DatabaseUtil.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setLong(1, targetUserId);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    sendError(response, HttpServletResponse.SC_NOT_FOUND,
                            "Freelancer profile not found.");
                    return;
                }

                JSONObject profile = new JSONObject();
                profile.put("userId", targetUserId);
                profile.put("fullName", rs.getString("full_name"));
                if ("FREELANCER".equals(role) && targetUserId == sessionUserId.longValue()) {
                    profile.put("email", rs.getString("email"));
                }
                profile.put("headline", valueOrEmpty(rs.getString("headline")));
                profile.put("overview", valueOrEmpty(rs.getString("overview")));
                profile.put("skills", valueOrEmpty(rs.getString("skills")));
                int years = rs.getInt("experience_years");
                profile.put("experienceYears", rs.wasNull() ? JSONObject.NULL : years);
                profile.put("experience", valueOrEmpty(rs.getString("experience")));
                profile.put("education", valueOrEmpty(rs.getString("education")));
                profile.put("certifications", valueOrEmpty(rs.getString("certifications")));
                profile.put("languages", valueOrEmpty(rs.getString("languages")));
                profile.put("availability", valueOrEmpty(rs.getString("availability")));
                profile.put("phone", "FREELANCER".equals(role)
                        && targetUserId == sessionUserId.longValue()
                        ? valueOrEmpty(rs.getString("phone")) : "");
                BigDecimal rate = rs.getBigDecimal("hourly_rate");
                profile.put("hourlyRate", rate == null ? JSONObject.NULL : rate);
                profile.put("location", valueOrEmpty(rs.getString("location")));
                profile.put("portfolioUrl", valueOrEmpty(rs.getString("portfolio_url")));
                profile.put("linkedinUrl", valueOrEmpty(rs.getString("linkedin_url")));
                profile.put("githubUrl", valueOrEmpty(rs.getString("github_url")));
                profile.put("updatedAt", rs.getTimestamp("updated_at") == null
                        ? JSONObject.NULL : rs.getTimestamp("updated_at").toString());
                response.setStatus(HttpServletResponse.SC_OK);
                response.getWriter().write(profile.toString());
            }
        } catch (SQLException exception) {
            getServletContext().log("Unable to load freelancer profile.", exception);
            sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Unable to load the profile right now.");
        }
    }

    @Override
    protected void doPost(
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {

        prepareJson(response);

        HttpSession session = request.getSession(false);
        Long userId = getUserId(session);
        if (userId == null) {
            sendError(response, HttpServletResponse.SC_UNAUTHORIZED,
                    "Please log in to save your profile.");
            return;
        }
        if (!"FREELANCER".equals(session.getAttribute("role"))) {
            sendError(response, HttpServletResponse.SC_FORBIDDEN,
                    "Only freelancers can update freelancer profiles.");
            return;
        }

        final JSONObject body;
        try {
            body = readJsonBody(request);
        } catch (IllegalArgumentException | JSONException exception) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST,
                    exception.getMessage() == null
                            ? "Invalid JSON request." : exception.getMessage());
            return;
        }

        String headline = readText(body, "headline").trim();
        String overview = readText(body, "overview").trim();
        String skills = readText(body, "skills").trim();
        String experienceText = readText(body, "experienceYears").trim();
        String experience = readText(body, "experience").trim();
        String education = readText(body, "education").trim();
        String certifications = readText(body, "certifications").trim();
        String languages = readText(body, "languages").trim();
        String location = readText(body, "location").trim();
        String availability = readText(body, "availability").trim();
        String phone = readText(body, "phone").trim();
        String portfolioUrl = readText(body, "portfolioUrl").trim();
        String linkedinUrl = readText(body, "linkedinUrl").trim();
        String githubUrl = readText(body, "githubUrl").trim();
        String rateText = readText(body, "hourlyRate").trim();

        if (headline.length() > 120 || overview.length() > 3000
                || skills.length() > 1000 || experience.length() > 2500
                || education.length() > 1500 || certifications.length() > 1500
                || languages.length() > 500 || location.length() > 120
                || availability.length() > 20 || phone.length() > 30
                || portfolioUrl.length() > 2048 || linkedinUrl.length() > 2048
                || githubUrl.length() > 2048) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST,
                    "One or more profile fields exceed their character limit.");
            return;
        }
        if (containsControlCharacters(headline) || containsControlCharacters(overview)
                || containsControlCharacters(skills) || containsControlCharacters(experience)
                || containsControlCharacters(education) || containsControlCharacters(certifications)
                || containsControlCharacters(languages) || containsControlCharacters(location)
                || containsControlCharacters(availability) || containsControlCharacters(phone)) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST,
                    "Profile fields contain unsupported characters.");
            return;
        }

        Integer experienceYears = null;
        if (!experienceText.isEmpty()) {
            try {
                experienceYears = Integer.valueOf(experienceText);
                if (experienceYears < 0 || experienceYears > 60) {
                    throw new NumberFormatException("Years out of range.");
                }
            } catch (NumberFormatException exception) {
                sendError(response, HttpServletResponse.SC_BAD_REQUEST,
                        "Years of experience must be a whole number from 0 to 60.");
                return;
            }
        }
        if (!availability.isEmpty() && !isAllowedAvailability(availability)) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST,
                    "Choose a valid availability option.");
            return;
        }

        BigDecimal hourlyRate = null;
        if (!rateText.isEmpty()) {
            try {
                hourlyRate = new BigDecimal(rateText);
                if (hourlyRate.signum() < 0 || hourlyRate.compareTo(MAX_RATE) > 0
                        || hourlyRate.stripTrailingZeros().scale() > 2) {
                    throw new NumberFormatException("Rate out of range.");
                }
                hourlyRate = hourlyRate.setScale(2);
            } catch (NumberFormatException | ArithmeticException exception) {
                sendError(response, HttpServletResponse.SC_BAD_REQUEST,
                        "Enter an hourly rate from 0 to 99,999,999.99 with up to two decimals.");
                return;
            }
        }

        if (!isValidPortfolioUrl(portfolioUrl)
                || !isValidPortfolioUrl(linkedinUrl)
                || !isValidPortfolioUrl(githubUrl)) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST,
                    "Portfolio and social links must be valid HTTP or HTTPS addresses.");
            return;
        }

        String username = System.getenv("DB_USERNAME");
        String password = System.getenv("DB_PASSWORD");
        if (username == null || username.isBlank()
                || password == null || password.isEmpty()) {
            getServletContext().log("Database credentials are not configured.");
            sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Unable to save the profile right now.");
            return;
        }

        String sql = "INSERT INTO freelancer_profiles "
                + "(user_id, headline, overview, skills, experience_years, experience, education, "
                + "certifications, languages, location, availability, phone, hourly_rate, "
                + "portfolio_url, linkedin_url, github_url) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE headline = VALUES(headline), "
                + "overview = VALUES(overview), skills = VALUES(skills), "
                + "experience_years = VALUES(experience_years), experience = VALUES(experience), "
                + "education = VALUES(education), certifications = VALUES(certifications), "
                + "languages = VALUES(languages), location = VALUES(location), "
                + "availability = VALUES(availability), phone = VALUES(phone), "
                + "hourly_rate = VALUES(hourly_rate), portfolio_url = VALUES(portfolio_url), "
                + "linkedin_url = VALUES(linkedin_url), github_url = VALUES(github_url), "
                + "updated_at = CURRENT_TIMESTAMP";

        try (Connection connection = DatabaseUtil.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setLong(1, userId);
            statement.setString(2, headline);
            statement.setString(3, overview);
            statement.setString(4, skills);
            if (experienceYears == null) statement.setNull(5, java.sql.Types.INTEGER);
            else statement.setInt(5, experienceYears);
            statement.setString(6, experience);
            statement.setString(7, education);
            statement.setString(8, certifications);
            statement.setString(9, languages);
            statement.setString(10, location);
            statement.setString(11, availability);
            statement.setString(12, phone);
            if (hourlyRate == null) statement.setNull(13, java.sql.Types.DECIMAL);
            else statement.setBigDecimal(13, hourlyRate);
            setOptional(statement, 14, portfolioUrl);
            setOptional(statement, 15, linkedinUrl);
            setOptional(statement, 16, githubUrl);

            statement.executeUpdate();
            JSONObject result = new JSONObject();
            result.put("message", "Your freelancer profile was saved.");
            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().write(result.toString());
        } catch (SQLException exception) {
            getServletContext().log("Unable to save freelancer profile.", exception);
            sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Unable to save the profile right now.");
        }
    }

    private JSONObject readJsonBody(HttpServletRequest request)
            throws IOException, JSONException {
        StringBuilder body = new StringBuilder();
        char[] buffer = new char[1024];
        int count;
        try (BufferedReader reader = request.getReader()) {
            while ((count = reader.read(buffer)) != -1) {
                if (body.length() + count > MAX_BODY_CHARS) {
                    throw new IllegalArgumentException("Request body is too large.");
                }
                body.append(buffer, 0, count);
            }
        }
        return new JSONObject(body.toString());
    }

    private Long getUserId(HttpSession session) {
        if (session == null) return null;
        Object value = session.getAttribute("userId");
        return value instanceof Number ? ((Number) value).longValue() : null;
    }

    private String readText(JSONObject body, String key) {
        if (body.isNull(key)) return "";
        Object value = body.opt(key);
        return value instanceof String ? (String) value : "";
    }

    private boolean isPositiveId(String value) {
        return value != null && value.matches("[1-9][0-9]{0,18}");
    }

    private boolean containsControlCharacters(String value) {
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if (Character.isISOControl(character) && character != '\n' && character != '\r' && character != '\t') {
                return true;
            }
        }
        return false;
    }

    private boolean isValidPortfolioUrl(String value) {
        if (value.isEmpty()) return true;
        try {
            URI uri = new URI(value);
            String scheme = uri.getScheme();
            return uri.getHost() != null && uri.getUserInfo() == null
                    && ("http".equalsIgnoreCase(scheme)
                        || "https".equalsIgnoreCase(scheme));
        } catch (URISyntaxException exception) {
            return false;
        }
    }

    private boolean isAllowedAvailability(String value) {
        return "AVAILABLE".equals(value) || "PART_TIME".equals(value)
                || "FULL_TIME".equals(value) || "UNAVAILABLE".equals(value);
    }

    private void setOptional(PreparedStatement statement, int index, String value)
            throws SQLException {
        if (value.isEmpty()) statement.setNull(index, java.sql.Types.VARCHAR);
        else statement.setString(index, value);
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    
    private int defaultPort(String scheme) {
        if ("https".equalsIgnoreCase(scheme)) return 443;
        if ("http".equalsIgnoreCase(scheme)) return 80;
        return -1;
    }

    private void prepareJson(HttpServletResponse response) {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-store");
    }

    private void sendError(HttpServletResponse response, int status, String message)
            throws IOException {
        response.setStatus(status);
        JSONObject error = new JSONObject();
        error.put("error", message);
        response.getWriter().write(error.toString());
    }
}
