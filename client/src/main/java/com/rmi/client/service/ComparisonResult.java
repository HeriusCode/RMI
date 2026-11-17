package com.rmi.client.service;

import com.rmi.common.model.Student;

public record ComparisonResult(
        Student server2,
        String server2Error,
        Student server1,
        String server1Error,
        boolean synchronizedData
) {}
