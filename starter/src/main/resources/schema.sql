DROP TABLE IF EXISTS pick_events;
DROP TABLE IF EXISTS reservations;
DROP TABLE IF EXISTS order_lines;
DROP TABLE IF EXISTS orders;
DROP TABLE IF EXISTS bin_stock;
DROP TABLE IF EXISTS skus;

CREATE TABLE skus (
    sku_id      VARCHAR(64) PRIMARY KEY,
    description VARCHAR(256) NOT NULL
);

CREATE TABLE bin_stock (
    bin_id            VARCHAR(32) NOT NULL,
    sku_id            VARCHAR(64) NOT NULL REFERENCES skus(sku_id),
    quantity_on_hand  INTEGER     NOT NULL CHECK (quantity_on_hand >= 0),
    quantity_reserved INTEGER     NOT NULL DEFAULT 0 CHECK (quantity_reserved >= 0),
    received_at       DATE        NOT NULL,
    PRIMARY KEY (bin_id, sku_id),
    CHECK (quantity_reserved <= quantity_on_hand)
);

CREATE TABLE orders (
    order_id   VARCHAR(64) PRIMARY KEY,
    status     VARCHAR(16) NOT NULL
        CHECK (status IN ('NEW','ALLOCATED','PICKING','PICKED','SHORT','CANCELLED')),
    created_at TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE order_lines (
    order_id          VARCHAR(64) NOT NULL REFERENCES orders(order_id),
    line_id           VARCHAR(32) NOT NULL,
    sku_id            VARCHAR(64) NOT NULL REFERENCES skus(sku_id),
    quantity_required INTEGER     NOT NULL CHECK (quantity_required > 0),
    quantity_picked   INTEGER     NOT NULL DEFAULT 0 CHECK (quantity_picked >= 0),
    PRIMARY KEY (order_id, line_id),
    CHECK (quantity_picked <= quantity_required)
);

CREATE TABLE reservations (
    reservation_id    VARCHAR(64) PRIMARY KEY,
    order_id          VARCHAR(64) NOT NULL,
    line_id           VARCHAR(32) NOT NULL,
    bin_id            VARCHAR(32) NOT NULL,
    sku_id            VARCHAR(64) NOT NULL,
    quantity_reserved INTEGER     NOT NULL CHECK (quantity_reserved > 0),
    quantity_picked   INTEGER     NOT NULL DEFAULT 0 CHECK (quantity_picked >= 0),
    status            VARCHAR(16) NOT NULL
        CHECK (status IN ('OPEN','PICKED','SHORT','CANCELLED')),
    FOREIGN KEY (order_id, line_id) REFERENCES order_lines(order_id, line_id),
    CHECK (quantity_picked <= quantity_reserved)
);

CREATE TABLE pick_events (
    event_id        VARCHAR(64) PRIMARY KEY,
    client_event_id VARCHAR(64) NOT NULL,
    reservation_id  VARCHAR(64) NOT NULL REFERENCES reservations(reservation_id),
    event_type      VARCHAR(16) NOT NULL CHECK (event_type IN ('PICK','SHORT_PICK')),
    quantity        INTEGER     NOT NULL CHECK (quantity > 0),
    scanned_barcode VARCHAR(64),
    reason          VARCHAR(32),
    picker_id       VARCHAR(32) NOT NULL,
    at              TIMESTAMP   NOT NULL
    -- TODO: add a constraint that prevents the same client_event_id from being
    --       inserted twice. Remember that retries may arrive concurrently.
);

-- TODO: consider adding an explicit audit_log table to capture state transitions
--       for orders, reservations, and bin_stock so we can answer
--       "what happened to unit X?" after the fact (see brief §5.5 and §9).
