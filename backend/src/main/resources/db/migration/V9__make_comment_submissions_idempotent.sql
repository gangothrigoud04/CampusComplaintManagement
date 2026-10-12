ALTER TABLE complaint_work_notes
    ADD COLUMN request_id VARCHAR(36) NULL,
    ADD CONSTRAINT uk_complaint_work_notes_request
        UNIQUE (complaint_id, author_id, request_id);
