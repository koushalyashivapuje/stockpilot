-- StockPilot initial schema

CREATE TABLE users (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    email           VARCHAR(255) NOT NULL,
    password_hash   VARCHAR(255) NOT NULL,
    full_name       VARCHAR(255) NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_users_email UNIQUE (email)
);

CREATE TABLE workspaces (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    name            VARCHAR(255) NOT NULL,
    slug            VARCHAR(255) NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_workspaces_slug UNIQUE (slug)
);

CREATE TABLE workspace_memberships (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    workspace_id    BIGINT NOT NULL,
    user_id         BIGINT NOT NULL,
    role            VARCHAR(20) NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_membership_workspace FOREIGN KEY (workspace_id) REFERENCES workspaces(id) ON DELETE CASCADE,
    CONSTRAINT fk_membership_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT uq_membership_workspace_user UNIQUE (workspace_id, user_id),
    CONSTRAINT chk_membership_role CHECK (role IN ('OWNER', 'STAFF'))
);

CREATE TABLE products (
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    workspace_id         BIGINT NOT NULL,
    name                 VARCHAR(255) NOT NULL,
    sku                  VARCHAR(100) NOT NULL,
    description          VARCHAR(2000),
    unit_price           DECIMAL(12,2) NOT NULL,
    stock_quantity       INT NOT NULL DEFAULT 0,
    low_stock_threshold  INT NOT NULL DEFAULT 0,
    active               BOOLEAN NOT NULL DEFAULT TRUE,
    version              BIGINT NOT NULL DEFAULT 0,
    created_at           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_product_workspace FOREIGN KEY (workspace_id) REFERENCES workspaces(id) ON DELETE CASCADE,
    CONSTRAINT uq_product_workspace_sku UNIQUE (workspace_id, sku),
    CONSTRAINT chk_product_price CHECK (unit_price >= 0),
    CONSTRAINT chk_product_stock CHECK (stock_quantity >= 0),
    CONSTRAINT chk_product_threshold CHECK (low_stock_threshold >= 0)
);

CREATE INDEX idx_products_workspace ON products(workspace_id);
CREATE INDEX idx_products_workspace_active ON products(workspace_id, active);

CREATE TABLE inventory_adjustments (
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    workspace_id         BIGINT NOT NULL,
    product_id           BIGINT NOT NULL,
    user_id              BIGINT NOT NULL,
    quantity_change      INT NOT NULL,
    adjustment_type      VARCHAR(30) NOT NULL,
    reason               VARCHAR(500),
    resulting_quantity   INT NOT NULL,
    created_at           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_adjustment_workspace FOREIGN KEY (workspace_id) REFERENCES workspaces(id) ON DELETE CASCADE,
    CONSTRAINT fk_adjustment_product FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE,
    CONSTRAINT fk_adjustment_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT chk_adjustment_resulting CHECK (resulting_quantity >= 0)
);

CREATE INDEX idx_adjustments_product ON inventory_adjustments(product_id);
CREATE INDEX idx_adjustments_workspace_date ON inventory_adjustments(workspace_id, created_at);

CREATE TABLE orders (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    workspace_id    BIGINT NOT NULL,
    created_by      BIGINT NOT NULL,
    status          VARCHAR(20) NOT NULL,
    total_amount    DECIMAL(14,2) NOT NULL DEFAULT 0,
    stock_restored  BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_order_workspace FOREIGN KEY (workspace_id) REFERENCES workspaces(id) ON DELETE CASCADE,
    CONSTRAINT fk_order_user FOREIGN KEY (created_by) REFERENCES users(id),
    CONSTRAINT chk_order_status CHECK (status IN ('PENDING', 'CONFIRMED', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT chk_order_total CHECK (total_amount >= 0)
);

CREATE INDEX idx_orders_workspace_status ON orders(workspace_id, status);
CREATE INDEX idx_orders_workspace_date ON orders(workspace_id, created_at);

CREATE TABLE order_items (
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id             BIGINT NOT NULL,
    product_id           BIGINT NOT NULL,
    product_name_snapshot VARCHAR(255) NOT NULL,
    sku_snapshot         VARCHAR(100) NOT NULL,
    unit_price_snapshot  DECIMAL(12,2) NOT NULL,
    quantity             INT NOT NULL,
    line_total           DECIMAL(14,2) NOT NULL,
    CONSTRAINT fk_orderitem_order FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE,
    CONSTRAINT fk_orderitem_product FOREIGN KEY (product_id) REFERENCES products(id),
    CONSTRAINT uq_orderitem_order_product UNIQUE (order_id, product_id),
    CONSTRAINT chk_orderitem_qty CHECK (quantity > 0),
    CONSTRAINT chk_orderitem_price CHECK (unit_price_snapshot >= 0)
);

CREATE INDEX idx_orderitems_order ON order_items(order_id);
CREATE INDEX idx_orderitems_product ON order_items(product_id);
