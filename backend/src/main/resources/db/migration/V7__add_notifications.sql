CREATE TABLE notifications (
    id BIGINT NOT NULL AUTO_INCREMENT,
    recipient_id BIGINT NOT NULL,
    complaint_id BIGINT NULL,
    title VARCHAR(160) NOT NULL,
    message VARCHAR(1000) NOT NULL,
    notification_type VARCHAR(40) NOT NULL,
    event_key VARCHAR(160) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    read_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_notifications_recipient_event UNIQUE (recipient_id, event_key),
    CONSTRAINT fk_notifications_recipient FOREIGN KEY (recipient_id)
        REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_notifications_complaint FOREIGN KEY (complaint_id)
        REFERENCES complaints (id) ON DELETE SET NULL,
    KEY ix_notifications_recipient_created (recipient_id, created_at, id),
    KEY ix_notifications_recipient_read (recipient_id, read_at)
) ENGINE=InnoDB;
