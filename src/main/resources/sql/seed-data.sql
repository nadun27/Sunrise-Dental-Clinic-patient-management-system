USE sunrise_dental;

INSERT IGNORE INTO roles (
    role_name,
    description
)
VALUES
    (
        'ADMIN',
        'Manages users, configuration, reports and all clinic information'
    ),
    (
        'RECEPTIONIST',
        'Registers patients and manages appointments and check-in'
    ),
    (
        'DENTIST',
        'Views assigned appointments and records clinical treatment details'
    ),
    (
        'CASHIER',
        'Creates bills, records payments and prints receipts'
    );

INSERT IGNORE INTO treatment_types (
    treatment_code,
    treatment_name,
    description,
    default_fee,
    default_duration_minutes
)
VALUES
    (
        'CONSULT',
        'Dental Consultation',
        'Initial examination and treatment planning',
        2500.00,
        30
    ),
    (
        'CLEAN',
        'Scaling and Cleaning',
        'Routine dental scaling and polishing',
        6500.00,
        45
    ),
    (
        'FILL',
        'Dental Filling',
        'Tooth-coloured restorative filling',
        8000.00,
        60
    ),
    (
        'EXTRACT',
        'Tooth Extraction',
        'Standard non-surgical tooth extraction',
        9000.00,
        60
    ),
    (
        'ROOT_CANAL',
        'Root Canal Treatment',
        'Root canal therapy and treatment',
        25000.00,
        90
    ),
    (
        'WHITEN',
        'Teeth Whitening',
        'Clinic-based cosmetic teeth whitening',
        30000.00,
        90
    );