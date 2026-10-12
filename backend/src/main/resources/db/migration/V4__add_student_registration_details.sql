ALTER TABLE users
    ADD COLUMN roll_number VARCHAR(40) NULL,
    ADD COLUMN class_name VARCHAR(100) NULL,
    ADD COLUMN section VARCHAR(40) NULL,
    ADD CONSTRAINT uk_users_roll_number UNIQUE (roll_number);
