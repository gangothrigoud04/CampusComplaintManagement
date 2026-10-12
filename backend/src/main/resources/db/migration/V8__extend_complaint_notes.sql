ALTER TABLE complaint_work_notes
    ADD COLUMN record_type VARCHAR(30) NOT NULL DEFAULT 'WORK_NOTE'
        AFTER note,
    ADD CONSTRAINT chk_complaint_work_notes_record_type
        CHECK (record_type IN ('WORK_NOTE', 'COMMENT', 'RESOLUTION_NOTE'));
