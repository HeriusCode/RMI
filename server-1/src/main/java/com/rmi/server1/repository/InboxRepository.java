package com.rmi.server1.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public final class InboxRepository {
    public boolean contains(Connection connection, String eventId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT event_id FROM sync_inbox WHERE event_id = ?::uuid")) {
            statement.setString(1, eventId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    public void save(Connection connection, String eventId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO sync_inbox (event_id) VALUES (?::uuid)")) {
            statement.setString(1, eventId);
            statement.executeUpdate();
        }
    }
}
