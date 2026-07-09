CREATE TABLE member (
    member_id BIGINT NOT NULL AUTO_INCREMENT,
    login_id VARCHAR(255) NOT NULL,
    password VARCHAR(255) NOT NULL,
    name VARCHAR(255),
    email VARCHAR(255),
    role VARCHAR(255),
    PRIMARY KEY (member_id),
    UNIQUE KEY uk_member_login_id (login_id),
    UNIQUE KEY uk_member_email (email)
) ENGINE=InnoDB;

CREATE TABLE hall (
    hall_id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(255),
    address VARCHAR(255),
    total_seats INT NOT NULL,
    PRIMARY KEY (hall_id)
) ENGINE=InnoDB;

CREATE TABLE performance (
    performance_id BIGINT NOT NULL AUTO_INCREMENT,
    api_id VARCHAR(255),
    title VARCHAR(255),
    artist VARCHAR(255),
    description TEXT,
    category VARCHAR(255),
    status VARCHAR(255),
    last_updated_at DATETIME(6),
    PRIMARY KEY (performance_id)
) ENGINE=InnoDB;

CREATE TABLE schedule (
    schedule_id BIGINT NOT NULL AUTO_INCREMENT,
    performance_id BIGINT,
    hall_id BIGINT,
    start_time DATETIME(6),
    total_seat_count INT NOT NULL,
    available_seat_count INT NOT NULL,
    PRIMARY KEY (schedule_id),
    CONSTRAINT fk_schedule_performance FOREIGN KEY (performance_id) REFERENCES performance (performance_id),
    CONSTRAINT fk_schedule_hall FOREIGN KEY (hall_id) REFERENCES hall (hall_id)
) ENGINE=InnoDB;

CREATE TABLE seat (
    seat_id BIGINT NOT NULL AUTO_INCREMENT,
    schedule_id BIGINT,
    grade VARCHAR(255),
    seat_number INT NOT NULL,
    price INT NOT NULL,
    status VARCHAR(255),
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (seat_id),
    CONSTRAINT fk_seat_schedule FOREIGN KEY (schedule_id) REFERENCES schedule (schedule_id)
) ENGINE=InnoDB;

CREATE TABLE reservation (
    reservation_id BIGINT NOT NULL AUTO_INCREMENT,
    member_id BIGINT,
    total_price INT NOT NULL,
    status VARCHAR(255),
    reserved_at DATETIME(6),
    PRIMARY KEY (reservation_id),
    CONSTRAINT fk_reservation_member FOREIGN KEY (member_id) REFERENCES member (member_id)
) ENGINE=InnoDB;

CREATE TABLE ticket (
    ticket_id BIGINT NOT NULL AUTO_INCREMENT,
    reservation_id BIGINT,
    seat_id BIGINT,
    PRIMARY KEY (ticket_id),
    CONSTRAINT fk_ticket_reservation FOREIGN KEY (reservation_id) REFERENCES reservation (reservation_id),
    CONSTRAINT fk_ticket_seat FOREIGN KEY (seat_id) REFERENCES seat (seat_id)
) ENGINE=InnoDB;

CREATE TABLE payment (
    payment_id BIGINT NOT NULL AUTO_INCREMENT,
    reservation_id BIGINT,
    amount INT NOT NULL,
    status VARCHAR(255),
    payment_key VARCHAR(255),
    paid_at DATETIME(6),
    PRIMARY KEY (payment_id),
    UNIQUE KEY uk_payment_reservation_id (reservation_id),
    CONSTRAINT fk_payment_reservation FOREIGN KEY (reservation_id) REFERENCES reservation (reservation_id)
) ENGINE=InnoDB;
