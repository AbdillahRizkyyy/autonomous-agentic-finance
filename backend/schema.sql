-- SQL schema untuk backend Personal Finance AI Agent (PostgreSQL / SQLite)

-- Tabel Pengguna (Opsional)
CREATE TABLE IF NOT EXISTS users (
    id SERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    whatsapp_number VARCHAR(20) UNIQUE NOT NULL,
    home_latitude DOUBLE PRECISION,
    home_longitude DOUBLE PRECISION,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Tabel Transaksi Utama
CREATE TABLE IF NOT EXISTS transactions (
    id SERIAL PRIMARY KEY,
    user_id INT REFERENCES users(id) ON DELETE SET NULL,
    nominal DECIMAL(15, 2) NOT NULL,
    category VARCHAR(50) NOT NULL,
    type VARCHAR(10) NOT NULL, -- 'INCOME' atau 'EXPENSE'
    description TEXT NOT NULL,
    source VARCHAR(20) NOT NULL, -- 'NOTIFIKASI', 'LAYAR', 'TUNAI'
    timestamp BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Tabel Log Logika Agent
CREATE TABLE IF NOT EXISTS agent_logs (
    id SERIAL PRIMARY KEY,
    log_text TEXT NOT NULL,
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Tabel Konfirmasi Tertunda (Pending Confirmation)
CREATE TABLE IF NOT EXISTS pending_receipts (
    reference_id VARCHAR(50) PRIMARY KEY,
    raw_text TEXT,
    source VARCHAR(20) NOT NULL,
    parsed_preview JSONB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
