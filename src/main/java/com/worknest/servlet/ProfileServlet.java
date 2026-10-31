
package com.worknest.servlet;

import java.io.IOException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

public class ProfileServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(
            HttpServletRequest req,
            HttpServletResponse res) throws IOException {

        res.setContentType("text/plain");
        res.setCharacterEncoding("UTF-8");
        res.setHeader("Cache-Control", "no-store");

        HttpSession session = req.getSession(false);

        if (session == null || session.getAttribute("userId") == null) {
            res.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        Object fullName = session.getAttribute("fullName");

        if (!(fullName instanceof String)
                || ((String) fullName).isBlank()) {
            res.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            return;
        }

        res.getWriter().write((String) fullName);
    }
}
