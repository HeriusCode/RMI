package com.rmi.server1.service;

import com.rmi.common.dto.ServerStatus;
import com.rmi.common.dto.SyncResult;
import com.rmi.common.model.Student;
import com.rmi.common.model.SyncEvent;
import com.rmi.common.remote.ReplicaServiceRemote;
import com.rmi.common.validation.StudentValidator;
import com.rmi.server1.database.DatabaseConnection;
import com.rmi.server1.repository.InboxRepository;
import com.rmi.server1.repository.StudentRepository;
import com.rmi.server1.repository.SyncLogRepository;
import java.rmi.RemoteException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

public final class ReplicaServiceImpl implements ReplicaServiceRemote {
    private final DatabaseConnection database;
    private final StudentRepository students = new StudentRepository();
    private final InboxRepository inbox = new InboxRepository();
    private final SyncLogRepository logs = new SyncLogRepository();

    public ReplicaServiceImpl(DatabaseConnection database) {
        this.database = database;
    }

    @Override
    public synchronized SyncResult saveStudent(Student input) throws RemoteException {
        try {
            StudentValidator.validate(input);
            return persistLocal(new Student(input.studentId().trim(), input.fullName().trim(),
                    input.className().trim(), nextTimestamp(), false),
                    "Client đã lưu trực tiếp trên Server 1");
        } catch (IllegalArgumentException error) {
            throw new RemoteException(error.getMessage(), error);
        } catch (SQLException error) {
            throw new RemoteException("Server 1 không thể lưu PostgreSQL: " + error.getMessage(), error);
        }
    }

    @Override
    public synchronized SyncResult deleteStudent(String studentId) throws RemoteException {
        try {
            StudentValidator.validateId(studentId);
            Student current;
            try (Connection connection = database.open()) {
                current = students.findById(connection, studentId.trim());
            }
            long timestamp = nextTimestamp();
            Student tombstone = current == null
                    ? new Student(studentId.trim(), null, null, timestamp, true)
                    : current.asDeleted(timestamp);
            return persistLocal(tombstone, "Client đã xóa trực tiếp trên Server 1");
        } catch (IllegalArgumentException error) {
            throw new RemoteException(error.getMessage(), error);
        } catch (SQLException error) {
            throw new RemoteException("Server 1 không thể xóa PostgreSQL: " + error.getMessage(), error);
        }
    }

    @Override
    public synchronized SyncResult apply(SyncEvent event) throws RemoteException {
        if (event == null || event.eventId() == null) {
            throw new RemoteException("Sự kiện đồng bộ không hợp lệ");
        }
        try {
            StudentValidator.validate(event.student());
        } catch (IllegalArgumentException error) {
            throw new RemoteException(error.getMessage(), error);
        }

        try (Connection connection = database.open()) {
            connection.setAutoCommit(false);
            try {
                if (inbox.contains(connection, event.eventId())) {
                    connection.rollback();
                    return SyncResult.success("Sự kiện đã được xử lý trước đó",
                            event.eventId(), event.student());
                }
                students.upsert(connection, event.student());
                inbox.save(connection, event.eventId());
                logs.success(connection, event.eventId(), event.student().studentId());
                connection.commit();
                return SyncResult.success("Server 1 đã lưu dữ liệu",
                        event.eventId(), event.student());
            } catch (SQLException error) {
                connection.rollback();
                throw error;
            }
        } catch (SQLException error) {
            throw new RemoteException("Server 1 không thể lưu PostgreSQL: " + error.getMessage(), error);
        }
    }

    @Override
    public Student findById(String studentId) throws RemoteException {
        try (Connection connection = database.open()) {
            StudentValidator.validateId(studentId);
            return students.findById(connection, studentId);
        } catch (IllegalArgumentException error) {
            throw new RemoteException(error.getMessage(), error);
        } catch (SQLException error) {
            throw new RemoteException("Không thể đọc Database 1: " + error.getMessage(), error);
        }
    }

    @Override
    public List<Student> search(String keyword) throws RemoteException {
        try (Connection connection = database.open()) {
            return students.search(connection, keyword);
        } catch (SQLException error) {
            throw new RemoteException("Không thể tìm kiếm Database 1: " + error.getMessage(), error);
        }
    }

    @Override
    public ServerStatus health() {
        try {
            database.verify();
            return new ServerStatus("Server 1", true, "RMI và PostgreSQL hoạt động",
                    System.currentTimeMillis());
        } catch (SQLException error) {
            return new ServerStatus("Server 1", false, error.getMessage(), System.currentTimeMillis());
        }
    }

    private long nextTimestamp() throws SQLException {
        try (Connection connection = database.open()) {
            return Math.max(System.currentTimeMillis(), students.latestTimestamp(connection) + 1);
        }
    }

    private SyncResult persistLocal(Student student, String message) throws SQLException {
        String eventId = UUID.randomUUID().toString();
        try (Connection connection = database.open()) {
            connection.setAutoCommit(false);
            try {
                students.upsert(connection, student);
                logs.localSuccess(connection, eventId, student.studentId(), message);
                connection.commit();
            } catch (SQLException error) {
                connection.rollback();
                throw error;
            }
        }
        return SyncResult.success(message, eventId, student);
    }
}
