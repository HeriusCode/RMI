package com.rmi.server2.service;

import com.rmi.common.dto.ServerStatus;
import com.rmi.common.dto.SyncResult;
import com.rmi.common.model.Student;
import com.rmi.common.model.SyncEvent;
import com.rmi.common.remote.PrimaryServiceRemote;
import com.rmi.common.validation.StudentValidator;
import com.rmi.server2.database.DatabaseConnection;
import com.rmi.server2.repository.OutboxRepository;
import com.rmi.server2.repository.StudentRepository;
import java.rmi.RemoteException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

public final class PrimaryServiceImpl implements PrimaryServiceRemote {
    private final DatabaseConnection database;
    private final StudentRepository students = new StudentRepository();
    private final OutboxRepository outbox = new OutboxRepository();
    private final AtomicLong clock;
    private final SyncDispatcher dispatcher;

    public PrimaryServiceImpl(DatabaseConnection database, SyncDispatcher dispatcher) throws SQLException {
        this.database = database;
        this.dispatcher = dispatcher;
        try (Connection connection = database.open()) {
            clock = new AtomicLong(Math.max(System.currentTimeMillis(),
                    students.latestTimestamp(connection)));
        }
    }

    @Override
    public synchronized SyncResult saveStudent(Student input) throws RemoteException {
        try {
            StudentValidator.validate(input);
            Student student = new Student(input.studentId().trim(), input.fullName().trim(),
                    input.className().trim(), nextTimestamp(), false);
            return persist(student);
        } catch (IllegalArgumentException error) {
            throw new RemoteException(error.getMessage(), error);
        }
    }

    @Override
    public synchronized SyncResult deleteStudent(String studentId) throws RemoteException {
        try {
            StudentValidator.validateId(studentId);
            Student current;
            try (Connection connection = database.open()) {
                current = students.findById(connection, studentId);
            }
            Student tombstone = current == null
                    ? new Student(studentId.trim(), null, null, nextTimestamp(), true)
                    : current.asDeleted(nextTimestamp());
            return persist(tombstone);
        } catch (SQLException error) {
            throw new RemoteException("Không thể đọc sinh viên trước khi xóa", error);
        } catch (IllegalArgumentException error) {
            throw new RemoteException(error.getMessage(), error);
        }
    }

    @Override
    public Student findById(String studentId) throws RemoteException {
        try {
            StudentValidator.validateId(studentId);
            try (Connection connection = database.open()) {
                return students.findById(connection, studentId);
            }
        } catch (SQLException error) {
            throw new RemoteException("Không thể đọc Database 2: " + error.getMessage(), error);
        } catch (IllegalArgumentException error) {
            throw new RemoteException(error.getMessage(), error);
        }
    }

    @Override
    public List<Student> search(String keyword) throws RemoteException {
        try (Connection connection = database.open()) {
            return students.search(connection, keyword);
        } catch (SQLException error) {
            throw new RemoteException("Không thể tìm kiếm Database 2: " + error.getMessage(), error);
        }
    }

    @Override
    public ServerStatus health() {
        try {
            database.verify();
            return new ServerStatus("Server 2", true, "RMI và PostgreSQL hoạt động",
                    System.currentTimeMillis());
        } catch (SQLException error) {
            return new ServerStatus("Server 2", false, error.getMessage(), System.currentTimeMillis());
        }
    }

    private SyncResult persist(Student student) throws RemoteException {
        String eventId = UUID.randomUUID().toString();
        SyncEvent event = new SyncEvent(eventId, student);
        try (Connection connection = database.open()) {
            connection.setAutoCommit(false);
            try {
                students.upsert(connection, student);
                outbox.enqueue(connection, event);
                connection.commit();
            } catch (SQLException error) {
                connection.rollback();
                throw error;
            }
        } catch (SQLException error) {
            throw new RemoteException("Server 2 không thể lưu PostgreSQL: " + error.getMessage(), error);
        }
        boolean synchronizedNow = dispatcher.deliverNow(event);
        String message = synchronizedNow
                ? "Đã lưu Server 2 và đồng bộ Server 1"
                : "Đã lưu Server 2, Server 1 đang chờ đồng bộ lại";
        return SyncResult.success(message, eventId, student);
    }

    private long nextTimestamp() {
        return clock.updateAndGet(previous -> Math.max(System.currentTimeMillis(), previous + 1));
    }
}
