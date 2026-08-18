USE sunrise_dental;

CREATE TABLE IF NOT EXISTS roles (
                                     role_id TINYINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
                                     role_name VARCHAR(30) NOT NULL UNIQUE,
    description VARCHAR(255) NOT NULL
    );

CREATE TABLE IF NOT EXISTS users (
                                     user_id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
                                     username VARCHAR(50) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    full_name VARCHAR(100) NOT NULL,
    email VARCHAR(150) NULL UNIQUE,
    contact_number VARCHAR(20) NULL,
    role_id TINYINT UNSIGNED NOT NULL,

    active BOOLEAN NOT NULL DEFAULT TRUE,
    failed_login_attempts TINYINT UNSIGNED NOT NULL DEFAULT 0,
    locked_until DATETIME NULL,
    last_login_at DATETIME NULL,

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL
    DEFAULT CURRENT_TIMESTAMP
    ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_users_role
    FOREIGN KEY (role_id)
    REFERENCES roles(role_id),

    CONSTRAINT chk_users_username_length
    CHECK (CHAR_LENGTH(username) BETWEEN 4 AND 50)
    );

CREATE TABLE IF NOT EXISTS dentists (
                                        dentist_id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
                                        user_id BIGINT UNSIGNED NOT NULL UNIQUE,
                                        registration_number VARCHAR(50) NOT NULL UNIQUE,
    specialization VARCHAR(100) NULL,
    consultation_fee DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    active BOOLEAN NOT NULL DEFAULT TRUE,

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL
    DEFAULT CURRENT_TIMESTAMP
    ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_dentists_user
    FOREIGN KEY (user_id)
    REFERENCES users(user_id),

    CONSTRAINT chk_dentist_consultation_fee
    CHECK (consultation_fee >= 0)
    );

CREATE TABLE IF NOT EXISTS patients (
                                        patient_id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
                                        patient_code VARCHAR(20) NOT NULL UNIQUE,
    full_name VARCHAR(100) NOT NULL,
    address VARCHAR(255) NOT NULL,
    contact_number VARCHAR(20) NOT NULL,
    email VARCHAR(150) NULL,
    date_of_birth DATE NULL,
    gender VARCHAR(20) NULL,
    allergies VARCHAR(500) NULL,
    medical_notes TEXT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,

    created_by BIGINT UNSIGNED NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL
    DEFAULT CURRENT_TIMESTAMP
    ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_patients_created_by
    FOREIGN KEY (created_by)
    REFERENCES users(user_id),

    INDEX idx_patients_name (full_name),
    INDEX idx_patients_contact (contact_number)
    );

CREATE TABLE IF NOT EXISTS treatment_types (
                                               treatment_type_id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
                                               treatment_code VARCHAR(30) NOT NULL UNIQUE,
    treatment_name VARCHAR(100) NOT NULL UNIQUE,
    description VARCHAR(500) NULL,
    default_fee DECIMAL(10,2) NOT NULL,
    default_duration_minutes SMALLINT UNSIGNED NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL
    DEFAULT CURRENT_TIMESTAMP
    ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT chk_treatment_fee
    CHECK (default_fee >= 0),

    CONSTRAINT chk_treatment_duration
    CHECK (default_duration_minutes BETWEEN 10 AND 480)
    );

CREATE TABLE IF NOT EXISTS appointments (
                                            appointment_id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
                                            appointment_number VARCHAR(30) NOT NULL UNIQUE,

    patient_id BIGINT UNSIGNED NOT NULL,
    dentist_id BIGINT UNSIGNED NOT NULL,
    treatment_type_id BIGINT UNSIGNED NOT NULL,

    start_at DATETIME NOT NULL,
    end_at DATETIME NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED',

    patient_reason VARCHAR(500) NULL,
    internal_notes VARCHAR(500) NULL,
    cancellation_reason VARCHAR(255) NULL,

    created_by BIGINT UNSIGNED NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL
    DEFAULT CURRENT_TIMESTAMP
    ON UPDATE CURRENT_TIMESTAMP,

    version_number INT UNSIGNED NOT NULL DEFAULT 1,

    CONSTRAINT fk_appointments_patient
    FOREIGN KEY (patient_id)
    REFERENCES patients(patient_id),

    CONSTRAINT fk_appointments_dentist
    FOREIGN KEY (dentist_id)
    REFERENCES dentists(dentist_id),

    CONSTRAINT fk_appointments_treatment
    FOREIGN KEY (treatment_type_id)
    REFERENCES treatment_types(treatment_type_id),

    CONSTRAINT fk_appointments_created_by
    FOREIGN KEY (created_by)
    REFERENCES users(user_id),

    CONSTRAINT chk_appointment_time
    CHECK (end_at > start_at),

    CONSTRAINT chk_appointment_status
    CHECK (
              status IN (
              'SCHEDULED',
              'CONFIRMED',
              'CHECKED_IN',
              'IN_TREATMENT',
              'COMPLETED',
              'CANCELLED',
              'NO_SHOW'
                        )
    ),

    INDEX idx_appointments_dentist_time
(dentist_id, start_at, end_at),

    INDEX idx_appointments_patient_time
(patient_id, start_at),

    INDEX idx_appointments_status_time
(status, start_at)
    );

CREATE TABLE IF NOT EXISTS treatment_records (
                                                 treatment_record_id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
                                                 appointment_id BIGINT UNSIGNED NOT NULL UNIQUE,
                                                 dentist_id BIGINT UNSIGNED NOT NULL,

                                                 diagnosis VARCHAR(1000) NOT NULL,
    treatment_performed VARCHAR(1000) NOT NULL,
    clinical_notes TEXT NULL,
    prescription TEXT NULL,
    follow_up_date DATE NULL,

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL
    DEFAULT CURRENT_TIMESTAMP
    ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_treatment_records_appointment
    FOREIGN KEY (appointment_id)
    REFERENCES appointments(appointment_id),

    CONSTRAINT fk_treatment_records_dentist
    FOREIGN KEY (dentist_id)
    REFERENCES dentists(dentist_id)
    );

CREATE TABLE IF NOT EXISTS bills (
                                     bill_id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
                                     invoice_number VARCHAR(30) NOT NULL UNIQUE,
    appointment_id BIGINT UNSIGNED NOT NULL UNIQUE,

    consultation_fee DECIMAL(10,2) NOT NULL,
    treatment_fee DECIMAL(10,2) NOT NULL,
    discount_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    tax_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    total_amount DECIMAL(10,2) NOT NULL,

    payment_status VARCHAR(20) NOT NULL DEFAULT 'UNPAID',
    discount_reason VARCHAR(255) NULL,

    created_by BIGINT UNSIGNED NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL
    DEFAULT CURRENT_TIMESTAMP
    ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_bills_appointment
    FOREIGN KEY (appointment_id)
    REFERENCES appointments(appointment_id),

    CONSTRAINT fk_bills_created_by
    FOREIGN KEY (created_by)
    REFERENCES users(user_id),

    CONSTRAINT chk_bill_amounts
    CHECK (
              consultation_fee >= 0
              AND treatment_fee >= 0
              AND discount_amount >= 0
              AND tax_amount >= 0
              AND total_amount >= 0
          ),

    CONSTRAINT chk_bill_discount
    CHECK (
              discount_amount <= consultation_fee + treatment_fee
          ),

    CONSTRAINT chk_bill_status
    CHECK (
              payment_status IN (
              'UNPAID',
              'PARTIALLY_PAID',
              'PAID',
              'VOID'
                                )
    )
    );

CREATE TABLE IF NOT EXISTS payments (
                                        payment_id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
                                        receipt_number VARCHAR(30) NOT NULL UNIQUE,
    bill_id BIGINT UNSIGNED NOT NULL,

    amount DECIMAL(10,2) NOT NULL,
    payment_method VARCHAR(20) NOT NULL,
    reference_number VARCHAR(100) NULL,

    paid_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    received_by BIGINT UNSIGNED NOT NULL,

    voided_at DATETIME NULL,
    voided_by BIGINT UNSIGNED NULL,
    void_reason VARCHAR(255) NULL,

    CONSTRAINT fk_payments_bill
    FOREIGN KEY (bill_id)
    REFERENCES bills(bill_id),

    CONSTRAINT fk_payments_received_by
    FOREIGN KEY (received_by)
    REFERENCES users(user_id),

    CONSTRAINT fk_payments_voided_by
    FOREIGN KEY (voided_by)
    REFERENCES users(user_id),

    CONSTRAINT chk_payment_amount
    CHECK (amount > 0),

    CONSTRAINT chk_payment_method
    CHECK (
              payment_method IN (
              'CASH',
              'CARD',
              'BANK_TRANSFER',
              'ONLINE'
                                )
    ),

    INDEX idx_payments_bill_time (bill_id, paid_at)
    );

CREATE TABLE IF NOT EXISTS notification_outbox (
                                                   notification_id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
                                                   appointment_id BIGINT UNSIGNED NOT NULL,

                                                   channel VARCHAR(10) NOT NULL,
    recipient VARCHAR(150) NOT NULL,
    template_code VARCHAR(50) NOT NULL,
    payload_json JSON NOT NULL,

    status VARCHAR(15) NOT NULL DEFAULT 'PENDING',
    attempt_count TINYINT UNSIGNED NOT NULL DEFAULT 0,
    next_attempt_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    sent_at DATETIME NULL,
    last_error VARCHAR(500) NULL,

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_notifications_appointment
    FOREIGN KEY (appointment_id)
    REFERENCES appointments(appointment_id),

    CONSTRAINT chk_notification_channel
    CHECK (channel IN ('EMAIL', 'SMS')),

    CONSTRAINT chk_notification_status
    CHECK (
              status IN (
              'PENDING',
              'PROCESSING',
              'SENT',
              'FAILED'
                        )
    ),

    INDEX idx_notification_delivery
(status, next_attempt_at)
    );

CREATE TABLE IF NOT EXISTS audit_logs (
                                          audit_id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
                                          actor_user_id BIGINT UNSIGNED NULL,

                                          action_name VARCHAR(80) NOT NULL,
    entity_type VARCHAR(50) NOT NULL,
    entity_id VARCHAR(50) NULL,

    before_json JSON NULL,
    after_json JSON NULL,

    ip_address VARCHAR(45) NULL,
    correlation_id VARCHAR(50) NULL,

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_audit_actor
    FOREIGN KEY (actor_user_id)
    REFERENCES users(user_id),

    INDEX idx_audit_entity
(entity_type, entity_id),

    INDEX idx_audit_created_at
(created_at)
    );