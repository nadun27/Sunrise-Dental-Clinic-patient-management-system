USE sunrise_dental;

DELIMITER $$

DROP TRIGGER IF EXISTS trg_appointment_insert_no_overlap$$

CREATE TRIGGER trg_appointment_insert_no_overlap
    BEFORE INSERT ON appointments
    FOR EACH ROW
BEGIN
    IF NEW.status NOT IN ('CANCELLED', 'NO_SHOW') AND EXISTS (
        SELECT 1
        FROM appointments
        WHERE dentist_id = NEW.dentist_id
          AND status NOT IN ('CANCELLED', 'NO_SHOW')
          AND start_at < NEW.end_at
          AND end_at > NEW.start_at
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Dentist already has an overlapping appointment';
END IF;

IF NEW.status NOT IN ('CANCELLED', 'NO_SHOW') AND EXISTS (
        SELECT 1
        FROM appointments
        WHERE patient_id = NEW.patient_id
          AND status NOT IN ('CANCELLED', 'NO_SHOW')
          AND start_at < NEW.end_at
          AND end_at > NEW.start_at
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Patient already has an overlapping appointment';
END IF;
END$$

DROP TRIGGER IF EXISTS trg_appointment_update_no_overlap$$

CREATE TRIGGER trg_appointment_update_no_overlap
    BEFORE UPDATE ON appointments
    FOR EACH ROW
BEGIN
    IF NEW.status NOT IN ('CANCELLED', 'NO_SHOW') AND EXISTS (
        SELECT 1
        FROM appointments
        WHERE dentist_id = NEW.dentist_id
          AND appointment_id <> NEW.appointment_id
          AND status NOT IN ('CANCELLED', 'NO_SHOW')
          AND start_at < NEW.end_at
          AND end_at > NEW.start_at
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Dentist already has an overlapping appointment';
END IF;

IF NEW.status NOT IN ('CANCELLED', 'NO_SHOW') AND EXISTS (
        SELECT 1
        FROM appointments
        WHERE patient_id = NEW.patient_id
          AND appointment_id <> NEW.appointment_id
          AND status NOT IN ('CANCELLED', 'NO_SHOW')
          AND start_at < NEW.end_at
          AND end_at > NEW.start_at
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Patient already has an overlapping appointment';
END IF;
END$$

DELIMITER ;