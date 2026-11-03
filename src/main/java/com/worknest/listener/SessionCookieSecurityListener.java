package com.worknest.listener;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.SessionCookieConfig;

/** Applies secure session-cookie flags only in production deployments. */
public final class SessionCookieSecurityListener implements ServletContextListener {
    @Override
    public void contextInitialized(ServletContextEvent event) {
        SessionCookieConfig cookie = event.getServletContext().getSessionCookieConfig();
        cookie.setHttpOnly(true);
        String environment = System.getenv("APP_ENV");
        cookie.setSecure("production".equalsIgnoreCase(environment));
    }
}
