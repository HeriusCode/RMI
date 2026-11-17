package com.rmi.common.model;

import java.io.Serial;
import java.io.Serializable;

public record SyncLogEntry(
        long logId,
        String eventId,
        String studentId,
        String direction,
        String status,
        String message,
        long createdAt
) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
