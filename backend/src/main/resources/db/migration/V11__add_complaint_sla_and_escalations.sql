SET time_zone = '+00:00';

ALTER TABLE complaints
    ADD COLUMN sla_target_hours SMALLINT NOT NULL DEFAULT 72,
    ADD COLUMN sla_due_soon_at DATETIME(6) NULL,
    ADD COLUMN resolution_deadline_at DATETIME(6) NULL,
    ADD COLUMN resolved_at DATETIME(6) NULL,
    ADD COLUMN escalation_level TINYINT NOT NULL DEFAULT 0,
    ADD COLUMN last_escalation_at DATETIME(6) NULL;

UPDATE complaints
SET sla_target_hours = CASE priority
        WHEN 'URGENT' THEN 4
        WHEN 'HIGH' THEN 24
        WHEN 'MEDIUM' THEN 72
        WHEN 'LOW' THEN 120
        ELSE 72
    END;

UPDATE complaints
SET resolution_deadline_at = DATE_ADD(created_at, INTERVAL sla_target_hours HOUR);

UPDATE complaints
SET sla_due_soon_at = DATE_SUB(resolution_deadline_at, INTERVAL FLOOR(sla_target_hours * 12) MINUTE);

UPDATE complaints complaint
JOIN (
    SELECT complaint_id, MIN(changed_at) AS resolved_at
    FROM complaint_status_history
    WHERE new_status IN ('RESOLVED', 'CLOSED')
    GROUP BY complaint_id
) resolved ON resolved.complaint_id = complaint.id
SET complaint.resolved_at = resolved.resolved_at;

ALTER TABLE complaints
    MODIFY COLUMN sla_due_soon_at DATETIME(6) NOT NULL,
    MODIFY COLUMN resolution_deadline_at DATETIME(6) NOT NULL,
    ADD INDEX idx_complaints_sla_deadline (resolution_deadline_at, status),
    ADD INDEX idx_complaints_escalation_level (escalation_level, status);

ALTER TABLE audit_trail
    MODIFY COLUMN actor_user_id BIGINT NULL;

CREATE TABLE complaint_sla_escalations (
    id BIGINT NOT NULL AUTO_INCREMENT,
    complaint_id BIGINT NOT NULL,
    escalation_level TINYINT NOT NULL,
    escalated_at DATETIME(6) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_complaint_sla_escalation_level UNIQUE (complaint_id, escalation_level),
    CONSTRAINT fk_complaint_sla_escalation_complaint FOREIGN KEY (complaint_id)
        REFERENCES complaints (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    INDEX idx_complaint_sla_escalation_time (escalated_at, complaint_id)
) ENGINE=InnoDB;
