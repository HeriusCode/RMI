package com.rmi.server2.repository;

import com.rmi.common.model.Student;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public final class StudentRepository {
    public void upsert(Connection connection, Student student) throws SQLException {
        String sql = """
                INSERT INTO student (student_id, full_name, class_name, updated_at, deleted)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (student_id) DO UPDATE SET
                    full_name = EXCLUDED.full_name,
                    class_name = EXCLUDED.class_name,
                    updated_at = EXCLUDED.updated_at,
                    deleted = EXCLUDED.deleted
                WHERE student.updated_at <= EXCLUDED.updated_at
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, student.studentId());
            statement.setString(2, student.fullName());
            statement.setString(3, student.className());
            statement.setLong(4, student.updatedAt());
            statement.setBoolean(5, student.deleted());
            statement.executeUpdate();
        }
    }

    public Student findById(Connection connection, String studentId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT student_id, full_name, class_name, updated_at, deleted "
                        + "FROM student WHERE student_id = ?")) {
            statement.setString(1, studentId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? map(result) : null;
            }
        }
    }

    public List<Student> search(Connection connection, String keyword) throws SQLException {
        String value = "%" + (keyword == null ? "" : keyword.trim()) + "%";
        List<Student> students = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT student_id, full_name, class_name, updated_at, deleted FROM student "
                        + "WHERE deleted = FALSE AND (student_id ILIKE ? OR full_name ILIKE ? OR class_name ILIKE ?) "
                        + "ORDER BY student_id")) {
            statement.setString(1, value);
            statement.setString(2, value);
            statement.setString(3, value);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) students.add(map(result));
            }
        }
        return students;
    }

    public long latestTimestamp(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COALESCE(MAX(updated_at), 0) FROM student");
             ResultSet result = statement.executeQuery()) {
            return result.next() ? result.getLong(1) : 0;
        }
    }

    private Student map(ResultSet result) throws SQLException {
        return new Student(result.getString("student_id"), result.getString("full_name"),
                result.getString("class_name"), result.getLong("updated_at"),
                result.getBoolean("deleted"));
    }
}
