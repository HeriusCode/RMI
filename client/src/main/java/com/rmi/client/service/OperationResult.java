package com.rmi.client.service;

import com.rmi.common.dto.SyncResult;

public record OperationResult(SyncResult result, ComparisonResult comparison) {}
