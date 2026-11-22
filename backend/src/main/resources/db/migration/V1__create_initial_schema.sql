CREATE TABLE users (
    id UUID PRIMARY KEY,
    email VARCHAR(254) NOT NULL,
    password_hash VARCHAR(60) NOT NULL,
    role VARCHAR(20) NOT NULL,
    must_change_password BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_users_email_normalized
        CHECK (email = lower(btrim(email)) AND char_length(email) BETWEEN 3 AND 254),
    CONSTRAINT ck_users_password_hash_length
        CHECK (char_length(password_hash) = 60),
    CONSTRAINT ck_users_role
        CHECK (role IN ('GERENTE', 'FUNCIONARIO')),
    CONSTRAINT uk_users_email UNIQUE (email)
);

CREATE UNIQUE INDEX ux_users_single_manager
    ON users (role)
    WHERE role = 'GERENTE';

CREATE TABLE employees (
    user_id UUID PRIMARY KEY,
    registration_number VARCHAR(30) NOT NULL,
    name VARCHAR(120) NOT NULL,
    job_title VARCHAR(80) NOT NULL,
    profile_photo_key VARCHAR(255) NOT NULL,
    CONSTRAINT fk_employees_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT uk_employees_registration_number UNIQUE (registration_number),
    CONSTRAINT ck_employees_registration_number
        CHECK (registration_number = upper(btrim(registration_number))
            AND registration_number ~ '^[A-Z0-9._-]{1,30}$'),
    CONSTRAINT ck_employees_name
        CHECK (name = btrim(name) AND char_length(name) BETWEEN 2 AND 120),
    CONSTRAINT ck_employees_job_title
        CHECK (job_title = btrim(job_title) AND char_length(job_title) BETWEEN 2 AND 80),
    CONSTRAINT ck_employees_profile_photo_key
        CHECK (profile_photo_key = btrim(profile_photo_key)
            AND char_length(profile_photo_key) BETWEEN 1 AND 255)
);

CREATE TABLE attendance_records (
    id UUID PRIMARY KEY,
    employee_id UUID NOT NULL,
    type VARCHAR(10) NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL,
    work_date DATE NOT NULL,
    gps_captured_at TIMESTAMPTZ NOT NULL,
    latitude NUMERIC(9, 6) NOT NULL,
    longitude NUMERIC(9, 6) NOT NULL,
    accuracy_meters NUMERIC(8, 3) NOT NULL,
    distance_meters NUMERIC(10, 3) NOT NULL,
    photo_key VARCHAR(255) NOT NULL,
    photo_sha256 VARCHAR(64) NOT NULL,
    idempotency_key UUID NOT NULL,
    request_fingerprint VARCHAR(64) NOT NULL,
    CONSTRAINT fk_attendance_employee
        FOREIGN KEY (employee_id) REFERENCES employees (user_id) ON DELETE RESTRICT,
    CONSTRAINT uk_attendance_employee_work_date_type
        UNIQUE (employee_id, work_date, type),
    CONSTRAINT uk_attendance_employee_idempotency
        UNIQUE (employee_id, idempotency_key),
    CONSTRAINT ck_attendance_type
        CHECK (type IN ('ENTRADA', 'SAIDA')),
    CONSTRAINT ck_attendance_latitude
        CHECK (latitude BETWEEN -90 AND 90),
    CONSTRAINT ck_attendance_longitude
        CHECK (longitude BETWEEN -180 AND 180),
    CONSTRAINT ck_attendance_accuracy
        CHECK (accuracy_meters > 0 AND accuracy_meters <= 50),
    CONSTRAINT ck_attendance_distance
        CHECK (distance_meters >= 0),
    CONSTRAINT ck_attendance_photo_key
        CHECK (photo_key = btrim(photo_key) AND char_length(photo_key) BETWEEN 1 AND 255),
    CONSTRAINT ck_attendance_photo_sha256
        CHECK (photo_sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_attendance_request_fingerprint
        CHECK (request_fingerprint ~ '^[0-9a-f]{64}$')
);

CREATE INDEX ix_attendance_employee_recorded_at
    ON attendance_records (employee_id, recorded_at DESC, id DESC);

CREATE INDEX ix_attendance_work_date_recorded_at
    ON attendance_records (work_date DESC, recorded_at DESC, id DESC);
