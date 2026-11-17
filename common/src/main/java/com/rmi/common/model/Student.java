package com.rmi.common.model;

import java.io.Serial;
import java.io.Serializable;

public record Student(
        String studentId,
        String fullName,
        String className,
        long updatedAt,
        boolean deleted
) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    public Student withTimestamp(long timestamp) {
        return new Student(studentId, fullName, className, timestamp, deleted);
    }

    public Student asDeleted(long timestamp) {
        return new Student(studentId, fullName, className, timestamp, true);
    }
}
