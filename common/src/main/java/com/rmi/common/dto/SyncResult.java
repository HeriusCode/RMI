package com.rmi.common.dto;

import com.rmi.common.model.Student;
import java.io.Serial;
import java.io.Serializable;

public record SyncResult(
        boolean success,
        String message,
        String eventId,
        Student student
) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    public static SyncResult success(String message, String eventId, Student student) {
        return new SyncResult(true, message, eventId, student);
    }

    public static SyncResult failure(String message) {
        return new SyncResult(false, message, null, null);
    }
}
