CREATE TABLE complaint_resolution_proofs (
    id BIGINT NOT NULL AUTO_INCREMENT,
    complaint_id BIGINT NOT NULL,
    uploaded_by_id BIGINT NOT NULL,
    image_reference VARCHAR(80) NOT NULL,
    uploaded_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_complaint_resolution_proofs_complaint UNIQUE (complaint_id),
    CONSTRAINT uk_complaint_resolution_proofs_image UNIQUE (image_reference),
    CONSTRAINT fk_complaint_resolution_proofs_complaint FOREIGN KEY (complaint_id)
        REFERENCES complaints (id) ON DELETE RESTRICT,
    CONSTRAINT fk_complaint_resolution_proofs_uploader FOREIGN KEY (uploaded_by_id)
        REFERENCES users (id) ON DELETE RESTRICT,
    KEY ix_complaint_resolution_proofs_uploader (uploaded_by_id)
) ENGINE=InnoDB;
