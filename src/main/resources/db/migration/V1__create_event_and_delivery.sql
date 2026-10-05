CREATE TABLE events
(
    event_id       VARCHAR(100) PRIMARY KEY,
    event_category VARCHAR(100) NOT NULL,
    payload        JSON         NOT NULL
) ENGINE = InnoDB;

CREATE TABLE deliveries (
    delivery_id VARCHAR(100) PRIMARY KEY,
    event_id VARCHAR(100) NOT NULL,
    receiver_url VARCHAR(2048) NOT NULL,
    delivery_status VARCHAR(20) NOT NULL,
    FOREIGN KEY (event_id) REFERENCES events(event_id)
) ENGINE = InnoDB;