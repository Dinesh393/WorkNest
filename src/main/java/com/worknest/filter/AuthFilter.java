
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

public class AuthFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response,
                         FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;

        HttpSession session = req.getSession(false);

        // Step 1: Check authentication
        if (session == null || session.getAttribute("userId") == null) {
            res.sendRedirect(req.getContextPath() + "/login.html");
            return;
        }

        // Step 2: Get the user's role
        String role = (String) session.getAttribute("role");

        if (role == null) {
            res.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        // Step 3: Determine whether this route requires a specific role
        String path = req.getServletPath();

        if (path.startsWith("/dashboard/client/")
                && !"CLIENT".equals(role)) {
            res.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        if (path.startsWith("/dashboard/freelancer/")
                && !"FREELANCER".equals(role)) {
            res.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        // Step 4: Allow the request to continue
        chain.doFilter(request, response);
    }
}
