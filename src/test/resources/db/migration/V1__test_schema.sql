CREATE TABLE IF NOT EXISTS test_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    value VARCHAR(255),
    score NUMERIC(10,2),
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMPTZ,
    metadata JSONB
);
