package com.worknest.listener;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import com.worknest.util.DatabaseUtil;

/** Cleans up database connections when WorkNest stops or reloads. */
public final class DatabasePoolContextListener implements ServletContextListener {
    @Override
    public void contextDestroyed(ServletContextEvent event) {
        DatabaseUtil.closePool();
    }
}
