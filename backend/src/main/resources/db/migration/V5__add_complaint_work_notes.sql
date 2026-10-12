CREATE TABLE complaint_work_notes (
    id BIGINT NOT NULL AUTO_INCREMENT,
    complaint_id BIGINT NOT NULL,
    author_id BIGINT NOT NULL,
    note VARCHAR(1000) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY ix_complaint_work_notes_complaint_created (complaint_id, created_at),
    KEY ix_complaint_work_notes_author (author_id),
    CONSTRAINT fk_complaint_work_notes_complaint FOREIGN KEY (complaint_id)
        REFERENCES complaints (id) ON DELETE RESTRICT,
    CONSTRAINT fk_complaint_work_notes_author FOREIGN KEY (author_id)
        REFERENCES users (id) ON DELETE RESTRICT
) ENGINE=InnoDB;
