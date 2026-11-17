package com.rmi.client.service;

import com.rmi.common.dto.ServerStatus;

public record HealthSnapshot(ServerStatus server1, ServerStatus server2) {}
