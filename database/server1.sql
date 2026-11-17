-- Chạy script này trong database rmi_student trên Máy thật 1.

CREATE TABLE IF NOT EXISTS student (
    student_id VARCHAR(20) PRIMARY KEY,
    full_name VARCHAR(100),
    class_name VARCHAR(50),
    updated_at BIGINT NOT NULL,
    deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE IF NOT EXISTS sync_inbox (
    event_id UUID PRIMARY KEY,
    received_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS sync_log (
    log_id BIGSERIAL PRIMARY KEY,
    event_id UUID NOT NULL,
    student_id VARCHAR(20) NOT NULL,
    direction VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    message VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_student_server1_active
    ON student (deleted, student_id);

CREATE INDEX IF NOT EXISTS idx_sync_log_server1_created
    ON sync_log (created_at DESC);
