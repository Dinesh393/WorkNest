package com.worknest.servlet;

import com.worknest.util.DatabaseUtil;

import java.io.IOException;
import java.net.URI;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.List;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.json.JSONObject;

/** Reads and updates the signed-in client's basic account details. */
public class ClientAccountServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final SecureRandom RANDOM = new SecureRandom();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res)
            throws IOException {
        prepareJson(res);
        HttpSession session = req.getSession(false);
        Long userId = getClientId(session);
        if (userId == null) {
            writeError(res, HttpServletResponse.SC_UNAUTHORIZED,
                    "Please sign in with a client account to view settings.");
            return;
        }

        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "SELECT u.full_name, u.email, u.role, u.created_at, "
               + "(SELECT COUNT(*) FROM projects p WHERE p.client_id = u.user_id) AS project_count, "
               + "(SELECT COUNT(*) FROM projects p WHERE p.client_id = u.user_id "
               + "AND p.status IN ('OPEN', 'IN_PROGRESS')) AS active_project_count "
               + "FROM users u WHERE u.user_id = ? AND u.role = 'CLIENT'")) {
            statement.setLong(1, userId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    writeError(res, HttpServletResponse.SC_UNAUTHORIZED,
                            "Your account could not be verified. Please sign in again.");
                    return;
                }

                String fullName = result.getString("full_name");
                session.setAttribute("fullName", fullName);
                Timestamp createdAt = result.getTimestamp("created_at");

                JSONObject account = new JSONObject();
                account.put("success", true);
                account.put("fullName", fullName);
                account.put("email", result.getString("email"));
                account.put("role", result.getString("role"));
                account.put("createdAt", createdAt == null ? "" : createdAt.toLocalDateTime().toString());
                account.put("projectCount", result.getInt("project_count"));
                account.put("activeProjectCount", result.getInt("active_project_count"));
                account.put("profileStorageReady", true);
                account.put("profileSetupError", "");
                JSONObject emptyOrganization = emptyOrganization();
                try (PreparedStatement profileStatement = connection.prepareStatement(
                        "SELECT organization_name, organization_type, industry, website, country, city, "
                      + "business_description "
                      + "FROM client_profiles WHERE client_id = ?")) {
                    profileStatement.setLong(1, userId);
                    try (ResultSet profile = profileStatement.executeQuery()) {
                        if (profile.next()) emptyOrganization = organizationFrom(profile);
                    }
                } catch (SQLException profileTableException) {
                    account.put("profileStorageReady", false);
                    account.put("profileSetupError", "Run database/client_profiles.sql in MySQL to enable organization profile storage.");
                    getServletContext().log("Client profile table is not available; returning basic account details.", profileTableException);
                }
                account.put("organization", emptyOrganization);
                account.put("csrfToken", csrfToken(session));
                res.getWriter().write(account.toString());
            }
        } catch (ClassNotFoundException | SQLException exception) {
            getServletContext().log("Unable to load client account settings.", exception);
            writeError(res, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Account profile could not be loaded. Check that database/client_profiles.sql has been run, then try again.");
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res)
            throws IOException {
        req.setCharacterEncoding("UTF-8");
        prepareJson(res);
        HttpSession session = req.getSession(false);
        Long userId = getClientId(session);
        if (userId == null) {
            writeError(res, HttpServletResponse.SC_UNAUTHORIZED,
                    "Please sign in again to save account settings.");
            return;
        }
        if (!validCsrf(session, req.getParameter("csrfToken"))) {
            writeError(res, HttpServletResponse.SC_FORBIDDEN,
                    "This form expired. Refresh the page and try again.");
            return;
        }

        String action = req.getParameter("action");
        if ("saveOrganization".equals(action)) {
            updateOrganization(req, res, userId);
            return;
        }

        String fullName = req.getParameter("fullName");
        if (fullName == null) fullName = "";
        fullName = fullName.trim();
        if (fullName.isEmpty() || fullName.length() > 100
                || containsControlCharacter(fullName)) {
            writeError(res, HttpServletResponse.SC_BAD_REQUEST,
                    "Enter a name using 1 to 100 characters.");
            return;
        }

        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "UPDATE users SET full_name = ? WHERE user_id = ? AND role = 'CLIENT'")) {
            statement.setString(1, fullName);
            statement.setLong(2, userId);
            statement.executeUpdate();
            session.setAttribute("fullName", fullName);

            JSONObject result = new JSONObject();
            result.put("success", true);
            result.put("message", "Profile details saved.");
            result.put("fullName", fullName);
            res.getWriter().write(result.toString());
        } catch (ClassNotFoundException | SQLException exception) {
            getServletContext().log("Unable to update client account settings.", exception);
            writeError(res, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Your profile could not be saved. Please try again.");
        }
    }

    private void updateOrganization(HttpServletRequest req, HttpServletResponse res, long userId)
            throws IOException {
        String organizationName = clean(req.getParameter("organizationName"));
        String organizationType = clean(req.getParameter("organizationType"));
        String industry = clean(req.getParameter("industry"));
        String website = clean(req.getParameter("website"));
        String country = clean(req.getParameter("country"));
        String city = clean(req.getParameter("city"));
        String description = clean(req.getParameter("businessDescription"));

        List<String> organizationTypes = Arrays.asList("Individual", "Startup", "Small business",
                "Medium business", "Enterprise", "Nonprofit", "Other");
        if (organizationName.isEmpty() || organizationName.length() > 160
                || organizationType.isEmpty() || !organizationTypes.contains(organizationType)
                || industry.isEmpty() || industry.length() > 80
                || country.isEmpty() || country.length() > 100
                || city.isEmpty() || city.length() > 100
                || website.length() > 255 || description.length() > 2000
                || hasControl(organizationName) || hasControl(industry) || hasControl(country)
                || hasControl(city) || hasControl(description) || !validWebsite(website)) {
            writeError(res, HttpServletResponse.SC_BAD_REQUEST,
                    "Complete the required organization fields and use a valid https:// or http:// website address.");
            return;
        }

        String sql = "INSERT INTO client_profiles (client_id, organization_name, organization_type, industry, "
                + "website, country, city, business_description) VALUES (?, ?, ?, ?, ?, ?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE organization_name = VALUES(organization_name), "
                + "organization_type = VALUES(organization_type), industry = VALUES(industry), "
                + "website = VALUES(website), country = VALUES(country), city = VALUES(city), "
                + "business_description = VALUES(business_description)";
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, userId);
            statement.setString(2, organizationName);
            statement.setString(3, organizationType);
            statement.setString(4, industry);
            statement.setString(5, website.isEmpty() ? null : website);
            statement.setString(6, country);
            statement.setString(7, city);
            statement.setString(8, description.isEmpty() ? null : description);
            statement.executeUpdate();

            JSONObject organization = new JSONObject();
            organization.put("organizationName", organizationName);
            organization.put("organizationType", organizationType);
            organization.put("industry", industry);
            organization.put("website", website);
            organization.put("country", country);
            organization.put("city", city);
            organization.put("businessDescription", description);

            JSONObject result = new JSONObject();
            result.put("success", true);
            result.put("message", "Organization details saved.");
            result.put("organization", organization);
            res.getWriter().write(result.toString());
        } catch (ClassNotFoundException | SQLException exception) {
            getServletContext().log("Unable to save client organization details.", exception);
            writeError(res, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Organization details could not be saved. Run database/client_profiles.sql, then try again.");
        }
    }

    private static JSONObject organizationFrom(ResultSet result) throws SQLException {
        JSONObject organization = new JSONObject();
        organization.put("organizationName", valueOr(result.getString("organization_name"), ""));
        organization.put("organizationType", valueOr(result.getString("organization_type"), ""));
        organization.put("industry", valueOr(result.getString("industry"), ""));
        organization.put("website", valueOr(result.getString("website"), ""));
        organization.put("country", valueOr(result.getString("country"), ""));
        organization.put("city", valueOr(result.getString("city"), ""));
        organization.put("businessDescription", valueOr(result.getString("business_description"), ""));
        return organization;
    }

    private static JSONObject emptyOrganization() {
        JSONObject organization = new JSONObject();
        organization.put("organizationName", "");
        organization.put("organizationType", "");
        organization.put("industry", "");
        organization.put("website", "");
        organization.put("country", "");
        organization.put("city", "");
        organization.put("businessDescription", "");
        return organization;
    }

    private static String clean(String value) { return value == null ? "" : value.trim(); }
    private static String valueOr(String value, String fallback) { return value == null ? fallback : value; }
    private static boolean same(String left, String right) { return clean(left).equals(right); }
    private static boolean hasControl(String value) {
        for (int i = 0; i < value.length(); i++) if (Character.isISOControl(value.charAt(i))) return true;
        return false;
    }
    private static boolean validWebsite(String website) {
        if (website.isEmpty()) return true;
        try {
            URI uri = new URI(website);
            String scheme = uri.getScheme();
            return uri.getHost() != null && ("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme))
                    && uri.getUserInfo() == null;
        } catch (Exception exception) { return false; }
    }

    static Long getClientId(HttpSession session) {
        if (session == null || !"CLIENT".equals(session.getAttribute("role"))) {
            return null;
        }
        Object value = session.getAttribute("userId");
        if (value instanceof Number) return ((Number) value).longValue();
        return null;
    }

    static String csrfToken(HttpSession session) {
        synchronized (session) {
            Object existing = session.getAttribute("clientAccountCsrfToken");
            if (existing instanceof String) return (String) existing;
            byte[] bytes = new byte[32];
            RANDOM.nextBytes(bytes);
            StringBuilder encoded = new StringBuilder(bytes.length * 2);
            for (byte value : bytes) {
                encoded.append(String.format("%02x", value & 0xff));
            }
            String token = encoded.toString();
            session.setAttribute("clientAccountCsrfToken", token);
            return token;
        }
    }

    static boolean validCsrf(HttpSession session, String submitted) {
        if (session == null || submitted == null) return false;
        Object expected = session.getAttribute("clientAccountCsrfToken");
        return expected instanceof String && ((String) expected).equals(submitted);
    }

    static void prepareJson(HttpServletResponse res) {
        res.setContentType("application/json");
        res.setCharacterEncoding("UTF-8");
        res.setHeader("Cache-Control", "no-store");
        res.setHeader("X-Content-Type-Options", "nosniff");
    }

    static void writeError(HttpServletResponse res, int status, String message)
            throws IOException {
        res.setStatus(status);
        JSONObject result = new JSONObject();
        result.put("success", false);
        result.put("error", message);
        res.getWriter().write(result.toString());
    }

    private static Connection openConnection()
            throws SQLException, ClassNotFoundException {
        String username = System.getenv("DB_USERNAME");
        String password = System.getenv("DB_PASSWORD");
        if (username == null || username.isBlank() || password == null || password.isEmpty()) {
            throw new SQLException("Database credentials are not configured.");
        }
        Class.forName("com.mysql.cj.jdbc.Driver");
        return DatabaseUtil.getConnection();
    }

    private static boolean containsControlCharacter(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.isISOControl(value.charAt(i))) return true;
        }
        return false;
    }
}
