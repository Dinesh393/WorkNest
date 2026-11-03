package com.worknest.filter;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import com.worknest.util.CsrfTokenUtil;

/** Rejects unsafe requests without a valid synchronizer token. */
public final class CsrfFilter implements Filter {
    private static boolean isSafeMethod(String method) {
        return "GET".equals(method) || "HEAD".equals(method)
                || "OPTIONS".equals(method) || "TRACE".equals(method);
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;

        if (isSafeMethod(req.getMethod())) {
            chain.doFilter(request, response);
            return;
        }

        HttpSession session = req.getSession(false);
        Object tokenValue = session == null ? null : session.getAttribute(CsrfTokenUtil.SESSION_ATTRIBUTE);
        String expected = tokenValue instanceof String ? (String) tokenValue : null;
        String supplied = req.getHeader("X-CSRF-Token");
        if (supplied == null || supplied.isEmpty()) {
            supplied = req.getParameter("_csrf");
        }

        if (CsrfTokenUtil.matches(expected, supplied)) {
            chain.doFilter(request, response);
            return;
        }

        res.setStatus(HttpServletResponse.SC_FORBIDDEN);
        res.setHeader("Cache-Control", "no-store");
        res.setHeader("X-Content-Type-Options", "nosniff");
        String accept = req.getHeader("Accept");
        boolean json = req.getHeader("X-CSRF-Token") != null
                || (accept != null && accept.contains("application/json"))
                || (req.getContentType() != null && req.getContentType().toLowerCase().contains("application/json"));
        if (json) {
            res.setContentType("application/json");
            res.setCharacterEncoding("UTF-8");
            res.getWriter().write("{\"error\":\"Security check failed. Refresh the page and try again.\"}");
        } else {
            res.setContentType("text/html");
            res.setCharacterEncoding("UTF-8");
            res.getWriter().write("<!doctype html><html lang=\"en\"><meta charset=\"utf-8\"><title>Request expired | WorkNest</title>"
                    + "<body><h1>Request expired</h1><p>Your security check could not be verified. Refresh the page and try again.</p></body></html>");
        }
    }
}
