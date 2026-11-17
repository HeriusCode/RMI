package com.rmi.server2.service;

import com.rmi.common.dto.SyncResult;
import com.rmi.common.model.SyncEvent;
import com.rmi.common.remote.ReplicaServiceRemote;
import com.rmi.common.remote.ServiceNames;
import com.rmi.server2.config.ServerConfig;
import com.rmi.server2.database.DatabaseConnection;
import com.rmi.server2.repository.OutboxRepository;
import com.rmi.server2.repository.SyncLogRepository;
import java.rmi.registry.LocateRegistry;
import java.sql.Connection;
import java.util.List;

public final class SyncDispatcher implements Runnable {
    private final DatabaseConnection database;
    private final String server1Host;
    private final int server1Port;
    private final OutboxRepository outbox = new OutboxRepository();
    private final SyncLogRepository logs = new SyncLogRepository();

    public SyncDispatcher(DatabaseConnection database, ServerConfig config) {
        this.database = database;
        server1Host = config.required("server1.host");
        server1Port = config.port("server1.port");
    }

    @Override
    public synchronized void run() {
        List<SyncEvent> events;
        try (Connection connection = database.open()) {
            events = outbox.pending(connection, 100);
        } catch (Exception error) {
            System.err.println("Đồng bộ đang chờ: " + rootMessage(error));
            return;
        }
        for (SyncEvent event : events) deliverNow(event);
    }

    public synchronized boolean deliverNow(SyncEvent event) {
        try {
            ReplicaServiceRemote replica = (ReplicaServiceRemote) LocateRegistry
                    .getRegistry(server1Host, server1Port).lookup(ServiceNames.REPLICA);
            return deliver(replica, event);
        } catch (Exception error) {
            recordFailure(event, error);
            return false;
        }
    }

    private boolean deliver(ReplicaServiceRemote replica, SyncEvent event) {
        try {
            SyncResult result = replica.apply(event);
            try (Connection connection = database.open()) {
                connection.setAutoCommit(false);
                outbox.markDelivered(connection, event.eventId());
                logs.add(connection, event.eventId(), event.student().studentId(),
                        "SUCCESS", result.message());
                connection.commit();
            }
            System.out.println("Đã đồng bộ " + event.student().studentId()
                    + " (" + event.eventId() + ")");
            return true;
        } catch (Exception error) {
            recordFailure(event, error);
            return false;
        }
    }

    private void recordFailure(SyncEvent event, Throwable error) {
        try (Connection connection = database.open()) {
            connection.setAutoCommit(false);
            String message = rootMessage(error);
            outbox.markFailure(connection, event.eventId(), message);
            logs.add(connection, event.eventId(), event.student().studentId(),
                    "FAILED", message);
            connection.commit();
        } catch (Exception logError) {
            System.err.println("Không thể ghi lỗi đồng bộ: " + rootMessage(logError));
        }
    }

    private String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
