package com.worknest.util;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

import javax.servlet.http.HttpSession;

/** Creates and validates per-session synchronizer CSRF tokens. */
public final class CsrfTokenUtil {
    public static final String SESSION_ATTRIBUTE = "worknest.csrfToken";
    private static final SecureRandom RANDOM = new SecureRandom();

    private CsrfTokenUtil() {
    }

    public static String getOrCreateToken(HttpSession session) {
        synchronized (session) {
            Object existing = session.getAttribute(SESSION_ATTRIBUTE);
            if (existing instanceof String && !((String) existing).isEmpty()) {
                return (String) existing;
            }
            String token = newToken();
            session.setAttribute(SESSION_ATTRIBUTE, token);
            return token;
        }
    }

    public static String rotateToken(HttpSession session) {
        synchronized (session) {
            String token = newToken();
            session.setAttribute(SESSION_ATTRIBUTE, token);
            return token;
        }
    }

    public static boolean matches(String expected, String supplied) {
        if (expected == null || supplied == null || expected.isEmpty() || supplied.isEmpty()) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                supplied.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
