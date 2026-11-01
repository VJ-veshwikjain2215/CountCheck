CREATE TABLE businesses (
 id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 name VARCHAR(80) NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE members (
 id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 business_id BIGINT NOT NULL REFERENCES businesses(id),
 email VARCHAR(254) NOT NULL UNIQUE, password_hash VARCHAR(100) NOT NULL,
 name VARCHAR(80) NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE products (
 id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 business_id BIGINT NOT NULL REFERENCES businesses(id),
 name VARCHAR(80) NOT NULL, sku VARCHAR(40) NOT NULL,
 unit VARCHAR(24) NOT NULL, location VARCHAR(80) NOT NULL,
 recorded_count INTEGER NOT NULL CHECK(recorded_count BETWEEN 0 AND 100000),
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 UNIQUE(business_id,sku), UNIQUE(business_id,id)
);
CREATE TABLE count_entries (
 id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 business_id BIGINT NOT NULL REFERENCES businesses(id),
 product_id BIGINT NOT NULL,
 member_id BIGINT NOT NULL REFERENCES members(id),
 request_id UUID NOT NULL,
 item_name VARCHAR(80) NOT NULL, sku VARCHAR(40) NOT NULL,
 location VARCHAR(80) NOT NULL,
 recorded_count INTEGER NOT NULL CHECK(recorded_count BETWEEN 0 AND 100000),
 physical_count INTEGER NOT NULL CHECK(physical_count BETWEEN 0 AND 100000),
 note VARCHAR(500) NOT NULL DEFAULT '',
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 UNIQUE(business_id,request_id),
 FOREIGN KEY(business_id,product_id) REFERENCES products(business_id,id)
);
CREATE INDEX counts_history ON count_entries(business_id,created_at DESC,id DESC);
