package com.rmi.server2.repository;

import com.rmi.common.model.Student;
import com.rmi.common.model.SyncEvent;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public final class OutboxRepository {
    public int pendingCount(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM sync_outbox WHERE delivered = FALSE");
             ResultSet result = statement.executeQuery()) {
            result.next();
            return result.getInt(1);
        }
    }

    public void enqueue(Connection connection, SyncEvent event) throws SQLException {
        String sql = """
                INSERT INTO sync_outbox
                    (event_id, student_id, full_name, class_name, updated_at, deleted, delivered)
                VALUES (?::uuid, ?, ?, ?, ?, ?, FALSE)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, event.eventId());
            statement.setString(2, event.student().studentId());
            statement.setString(3, event.student().fullName());
            statement.setString(4, event.student().className());
            statement.setLong(5, event.student().updatedAt());
            statement.setBoolean(6, event.student().deleted());
            statement.executeUpdate();
        }
    }

    public List<SyncEvent> pending(Connection connection, int limit) throws SQLException {
        List<SyncEvent> events = new ArrayList<>();
        String sql = """
                SELECT event_id::text, student_id, full_name, class_name, updated_at, deleted
                FROM sync_outbox
                WHERE delivered = FALSE
                ORDER BY created_at, event_id
                LIMIT ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, limit);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    Student student = new Student(result.getString("student_id"),
                            result.getString("full_name"), result.getString("class_name"),
                            result.getLong("updated_at"), result.getBoolean("deleted"));
                    events.add(new SyncEvent(result.getString("event_id"), student));
                }
            }
        }
        return events;
    }

    public void markDelivered(Connection connection, String eventId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE sync_outbox SET delivered = TRUE, delivered_at = CURRENT_TIMESTAMP, "
                        + "last_error = NULL WHERE event_id = ?::uuid")) {
            statement.setString(1, eventId);
            statement.executeUpdate();
        }
    }

    public void markFailure(Connection connection, String eventId, String error) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE sync_outbox SET retry_count = retry_count + 1, last_error = ? "
                        + "WHERE event_id = ?::uuid")) {
            statement.setString(1, abbreviate(error, 1000));
            statement.setString(2, eventId);
            statement.executeUpdate();
        }
    }

    private String abbreviate(String value, int maxLength) {
        if (value == null) return null;
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
