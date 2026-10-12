ALTER TABLE complaints
    ADD COLUMN complaint_code VARCHAR(40) NULL AFTER id,
    ADD COLUMN priority VARCHAR(10) NOT NULL DEFAULT 'MEDIUM' AFTER status,
    ADD COLUMN image_reference VARCHAR(80) NULL AFTER priority;

UPDATE complaints
SET complaint_code = CONCAT('CMP-', LPAD(id, 12, '0'))
WHERE complaint_code IS NULL;

ALTER TABLE complaints
    MODIFY COLUMN complaint_code VARCHAR(40) NOT NULL,
    ADD CONSTRAINT uk_complaints_complaint_code UNIQUE (complaint_code),
    ADD CONSTRAINT ck_complaints_priority CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'URGENT'));

INSERT INTO complaint_categories (name, description, active)
VALUES
    ('Electricity', 'Electrical infrastructure and power supply', TRUE),
    ('Wi-Fi and Internet', 'Campus network and internet connectivity', TRUE),
    ('Water Supply', 'Water availability and supply issues', TRUE),
    ('Classroom Maintenance', 'Classroom condition and maintenance', TRUE),
    ('Laboratory Equipment', 'Laboratory equipment and facilities', TRUE),
    ('Furniture', 'Campus furniture condition and maintenance', TRUE),
    ('Washroom and Sanitation', 'Washroom and sanitation facilities', TRUE),
    ('Cleaning', 'Cleaning and campus hygiene', TRUE),
    ('Plumbing', 'Pipes, drains, and plumbing fixtures', TRUE),
    ('Security and Safety', 'Campus security and safety concerns', TRUE),
    ('Other', 'Other campus facilities concerns', TRUE)
ON DUPLICATE KEY UPDATE description = VALUES(description), active = TRUE;
