package com.rmi.server1.repository;

import com.rmi.common.model.SyncLogEntry;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public final class SyncLogRepository {
    public void localSuccess(Connection connection, String eventId, String studentId,
                             String message) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO sync_log (event_id, student_id, direction, status, message) "
                        + "VALUES (?::uuid, ?, 'LOCAL', 'SUCCESS', ?)")) {
            statement.setString(1, eventId);
            statement.setString(2, studentId);
            statement.setString(3, message);
            statement.executeUpdate();
        }
    }

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

    public void success(Connection connection, String eventId, String studentId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO sync_log (event_id, student_id, direction, status, message) "
                        + "VALUES (?::uuid, ?, 'INBOUND', 'SUCCESS', 'Đã áp dụng trên Server 1')")) {
            statement.setString(1, eventId);
            statement.setString(2, studentId);
            statement.executeUpdate();
        }
    }
}
