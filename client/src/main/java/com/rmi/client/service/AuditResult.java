package com.rmi.client.service;

import java.util.List;

public record AuditResult(
        int server2Count,
        int server1Count,
        List<String> inconsistentStudentIds
) {
    public boolean consistent() {
        return inconsistentStudentIds.isEmpty();
    }
}
