CREATE TABLE IF NOT EXISTS restaurants (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    category VARCHAR(50),
    address VARCHAR(255),
    average_price NUMERIC(10, 2),
    rating NUMERIC(3, 2),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO restaurants (
    name,
    category,
    address,
    average_price,
    rating
)
SELECT
    'FoodAdvisor Test Restaurant',
    'Chinese',
    'Test Address',
    68.00,
    4.50
WHERE NOT EXISTS (
    SELECT 1
    FROM restaurants
    WHERE name = 'FoodAdvisor Test Restaurant'
);