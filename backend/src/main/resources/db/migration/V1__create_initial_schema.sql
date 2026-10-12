CREATE TABLE departments (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(120) NOT NULL,
    description VARCHAR(500),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_departments_name UNIQUE (name)
) ENGINE=InnoDB;

CREATE TABLE users (
    id BIGINT NOT NULL AUTO_INCREMENT,
    full_name VARCHAR(160) NOT NULL,
    email VARCHAR(254) NOT NULL,
    user_role VARCHAR(30) NOT NULL,
    department_id BIGINT,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT ck_users_role CHECK (user_role IN ('STUDENT', 'FACULTY', 'STAFF', 'ADMIN')),
    CONSTRAINT fk_users_department FOREIGN KEY (department_id)
        REFERENCES departments (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    INDEX idx_users_department_id (department_id),
    INDEX idx_users_user_role (user_role)
) ENGINE=InnoDB;

CREATE TABLE complaint_categories (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(120) NOT NULL,
    description VARCHAR(500),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_complaint_categories_name UNIQUE (name)
) ENGINE=InnoDB;

CREATE TABLE campus_locations (
    id BIGINT NOT NULL AUTO_INCREMENT,
    building VARCHAR(160) NOT NULL,
    room VARCHAR(80),
    description VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_campus_locations_building_room UNIQUE (building, room),
    INDEX idx_campus_locations_building (building)
) ENGINE=InnoDB;

CREATE TABLE complaints (
    id BIGINT NOT NULL AUTO_INCREMENT,
    reporter_id BIGINT NOT NULL,
    category_id BIGINT NOT NULL,
    location_id BIGINT NOT NULL,
    department_id BIGINT,
    title VARCHAR(200) NOT NULL,
    description TEXT NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'SUBMITTED',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT ck_complaints_status CHECK (status IN ('SUBMITTED', 'UNDER_REVIEW', 'ASSIGNED', 'IN_PROGRESS', 'RESOLVED', 'CLOSED', 'REJECTED')),
    CONSTRAINT fk_complaints_reporter FOREIGN KEY (reporter_id)
        REFERENCES users (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_complaints_category FOREIGN KEY (category_id)
        REFERENCES complaint_categories (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_complaints_location FOREIGN KEY (location_id)
        REFERENCES campus_locations (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_complaints_department FOREIGN KEY (department_id)
        REFERENCES departments (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    INDEX idx_complaints_reporter_id (reporter_id),
    INDEX idx_complaints_category_id (category_id),
    INDEX idx_complaints_location_id (location_id),
    INDEX idx_complaints_department_id (department_id),
    INDEX idx_complaints_status_created (status, created_at)
) ENGINE=InnoDB;

CREATE TABLE complaint_assignments (
    id BIGINT NOT NULL AUTO_INCREMENT,
    complaint_id BIGINT NOT NULL,
    assignee_id BIGINT NOT NULL,
    assigned_by_id BIGINT,
    assigned_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    unassigned_at TIMESTAMP NULL,
    note VARCHAR(1000),
    PRIMARY KEY (id),
    CONSTRAINT fk_complaint_assignments_complaint FOREIGN KEY (complaint_id)
        REFERENCES complaints (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_complaint_assignments_assignee FOREIGN KEY (assignee_id)
        REFERENCES users (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_complaint_assignments_assigned_by FOREIGN KEY (assigned_by_id)
        REFERENCES users (id) ON DELETE SET NULL ON UPDATE RESTRICT,
    INDEX idx_complaint_assignments_complaint_time (complaint_id, assigned_at),
    INDEX idx_complaint_assignments_assignee (assignee_id)
) ENGINE=InnoDB;

CREATE TABLE complaint_status_history (
    id BIGINT NOT NULL AUTO_INCREMENT,
    complaint_id BIGINT NOT NULL,
    changed_by_id BIGINT,
    previous_status VARCHAR(30),
    new_status VARCHAR(30) NOT NULL,
    note VARCHAR(1000),
    changed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT ck_complaint_history_previous_status CHECK (previous_status IS NULL OR previous_status IN ('SUBMITTED', 'UNDER_REVIEW', 'ASSIGNED', 'IN_PROGRESS', 'RESOLVED', 'CLOSED', 'REJECTED')),
    CONSTRAINT ck_complaint_history_new_status CHECK (new_status IN ('SUBMITTED', 'UNDER_REVIEW', 'ASSIGNED', 'IN_PROGRESS', 'RESOLVED', 'CLOSED', 'REJECTED')),
    CONSTRAINT fk_complaint_status_history_complaint FOREIGN KEY (complaint_id)
        REFERENCES complaints (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_complaint_status_history_changed_by FOREIGN KEY (changed_by_id)
        REFERENCES users (id) ON DELETE SET NULL ON UPDATE RESTRICT,
    INDEX idx_complaint_status_history_complaint_time (complaint_id, changed_at),
    INDEX idx_complaint_status_history_changed_by (changed_by_id)
) ENGINE=InnoDB;
