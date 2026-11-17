package com.rmi.server2.repository;

import com.rmi.common.model.SyncLogEntry;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public final class SyncLogRepository {
    public List<SyncLogEntry> recent(Connection connection, int limit) throws SQLException {
        List<SyncLogEntry> entries = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT log_id, event_id::text, student_id, direction, status, message, created_at "
                        + "FROM sync_log ORDER BY created_at DESC, log_id DESC LIMIT ?")) {
            statement.setInt(1, limit);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    entries.add(new SyncLogEntry(result.getLong("log_id"),
                            result.getString("event_id"), result.getString("student_id"),
                            result.getString("direction"), result.getString("status"),
                            result.getString("message"),
                            result.getTimestamp("created_at").getTime()));
                }
            }
        }
        return entries;
    }

    public void add(Connection connection, String eventId, String studentId,
                    String status, String message) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO sync_log (event_id, student_id, direction, status, message) "
                        + "VALUES (?::uuid, ?, 'OUTBOUND', ?, ?)")) {
            statement.setString(1, eventId);
            statement.setString(2, studentId);
            statement.setString(3, status);
            statement.setString(4, message == null ? null
                    : message.substring(0, Math.min(message.length(), 1000)));
            statement.executeUpdate();
        }
    }
}
