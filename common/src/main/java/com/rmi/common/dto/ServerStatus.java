package com.rmi.common.dto;

import java.io.Serial;
import java.io.Serializable;

public record ServerStatus(
        String serverName,
        boolean available,
        String message,
        long checkedAt
) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
