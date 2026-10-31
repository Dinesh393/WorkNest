package com.worknest.util;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/** Writes notification records using the caller's existing transaction. */
public final class NotificationUtil {

    private NotificationUtil() { }

    public static void create(
            Connection connection,
            long recipientUserId,
            long projectId,
            String type,
            String title,
            String message) throws SQLException {

        String sql = "INSERT INTO notifications "
                + "(user_id, project_id, type, title, message) "
                + "VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, recipientUserId);
            if (projectId > 0) statement.setLong(2, projectId);
            else statement.setNull(2, java.sql.Types.BIGINT);
            statement.setString(3, type);
            statement.setString(4, title);
            statement.setString(5, message);
            statement.executeUpdate();
        }
    }
}
