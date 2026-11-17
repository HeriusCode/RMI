package com.rmi.common.model;

import java.io.Serial;
import java.io.Serializable;

public record SyncEvent(String eventId, Student student) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
