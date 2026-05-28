-- Picsou — Flyway V1 initial schema
-- M1 MIAGE Dauphine-PSL — Personal Finance Tracker
-- Source: docs/superpowers/specs/2026-05-22-picsou-design.md §5

-- ====================
-- AUTH
-- ====================
CREATE TABLE users (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  email TEXT UNIQUE NOT NULL,
  password_hash TEXT NOT NULL,
  first_name TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE refresh_tokens (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  token_hash TEXT UNIQUE NOT NULL,
  expires_at TIMESTAMPTZ NOT NULL,
  revoked_at TIMESTAMPTZ NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ====================
-- COMPTES
-- ====================
CREATE TYPE account_type AS ENUM ('cash', 'coloc', 'bank');

CREATE TABLE accounts (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID REFERENCES users(id) ON DELETE CASCADE,
  name TEXT NOT NULL,
  type account_type NOT NULL,
  balance NUMERIC(10,2) DEFAULT 0,
  currency CHAR(3) DEFAULT 'EUR',
  external_id TEXT NULL,
  provider TEXT NULL,
  created_at TIMESTAMPTZ DEFAULT NOW()
);

-- ====================
-- CATÉGORIES
-- ====================
CREATE TABLE categories (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID REFERENCES users(id) ON DELETE CASCADE NULL,
  name TEXT NOT NULL,
  icon_key TEXT,
  color_key TEXT,
  is_default BOOLEAN DEFAULT FALSE,
  parent_id UUID REFERENCES categories(id) NULL,
  created_at TIMESTAMPTZ DEFAULT NOW()
);

-- ====================
-- TRANSACTIONS
-- ====================
CREATE TYPE tx_type AS ENUM ('income', 'expense');
CREATE TYPE tx_source AS ENUM ('manual', 'ocr', 'openbanking');

CREATE TABLE transactions (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID REFERENCES users(id) ON DELETE CASCADE,
  account_id UUID REFERENCES accounts(id),
  category_id UUID REFERENCES categories(id) NULL,
  amount NUMERIC(10,2) NOT NULL,
  date DATE NOT NULL,
  description TEXT NOT NULL,
  type tx_type NOT NULL,
  note TEXT NULL,
  source tx_source DEFAULT 'manual',
  external_id TEXT NULL,
  created_at TIMESTAMPTZ DEFAULT NOW(),
  updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_tx_user_date ON transactions(user_id, date DESC);
CREATE INDEX idx_tx_user_category ON transactions(user_id, category_id);
CREATE INDEX idx_tx_user_account ON transactions(user_id, account_id);
CREATE UNIQUE INDEX idx_tx_external ON transactions(external_id) WHERE external_id IS NOT NULL;

-- ====================
-- SPLIT COLOC
-- ====================
CREATE TABLE coloc_groups (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  name TEXT NOT NULL,
  created_by_user_id UUID REFERENCES users(id),
  created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TYPE coloc_role AS ENUM ('admin', 'member');

CREATE TABLE coloc_members (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  coloc_group_id UUID REFERENCES coloc_groups(id) ON DELETE CASCADE,
  user_id UUID REFERENCES users(id) ON DELETE CASCADE,
  role coloc_role DEFAULT 'member',
  joined_at TIMESTAMPTZ DEFAULT NOW(),
  UNIQUE(coloc_group_id, user_id)
);

CREATE TYPE split_method AS ENUM ('equal', 'custom');

CREATE TABLE shared_expenses (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  transaction_id UUID REFERENCES transactions(id) ON DELETE CASCADE,
  coloc_group_id UUID REFERENCES coloc_groups(id),
  payer_user_id UUID REFERENCES users(id),
  total_amount NUMERIC(10,2) NOT NULL,
  split_method split_method DEFAULT 'equal'
);

CREATE TABLE shared_expense_parts (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  shared_expense_id UUID REFERENCES shared_expenses(id) ON DELETE CASCADE,
  user_id UUID REFERENCES users(id),
  amount NUMERIC(10,2) NOT NULL,
  settled BOOLEAN DEFAULT FALSE,
  settled_at TIMESTAMPTZ NULL
);

-- ====================
-- GOALS
-- ====================
CREATE TYPE goal_template AS ENUM ('savings', 'travel', 'purchase', 'custom');

CREATE TABLE goals (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID REFERENCES users(id) ON DELETE CASCADE,
  name TEXT NOT NULL,
  target_amount NUMERIC(10,2) NOT NULL,
  current_amount NUMERIC(10,2) DEFAULT 0,
  deadline DATE NULL,
  template goal_template DEFAULT 'custom',
  created_at TIMESTAMPTZ DEFAULT NOW(),
  completed_at TIMESTAMPTZ NULL
);

CREATE TABLE goal_contributions (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  goal_id UUID REFERENCES goals(id) ON DELETE CASCADE,
  amount NUMERIC(10,2) NOT NULL,
  date DATE NOT NULL,
  created_at TIMESTAMPTZ DEFAULT NOW()
);

-- ====================
-- OPEN BANKING
-- ====================
CREATE TYPE ob_provider AS ENUM ('bridge', 'gocardless', 'tink');
CREATE TYPE ob_status AS ENUM ('active', 'expired', 'revoked');

CREATE TABLE bank_connections (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID REFERENCES users(id) ON DELETE CASCADE,
  provider ob_provider NOT NULL,
  provider_user_id TEXT,
  access_token_encrypted TEXT NOT NULL,
  refresh_token_encrypted TEXT,
  expires_at TIMESTAMPTZ,
  status ob_status DEFAULT 'active',
  created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE bank_sync_log (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  bank_connection_id UUID REFERENCES bank_connections(id),
  started_at TIMESTAMPTZ DEFAULT NOW(),
  completed_at TIMESTAMPTZ NULL,
  transactions_imported INT DEFAULT 0,
  error TEXT NULL
);

-- ====================
-- INSIGHTS AI (cache)
-- ====================
CREATE TABLE ai_insights (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID REFERENCES users(id) ON DELETE CASCADE,
  period_start DATE NOT NULL,
  period_end DATE NOT NULL,
  prompt_variant TEXT,
  response TEXT NOT NULL,
  tokens_used INT,
  model_used TEXT,
  created_at TIMESTAMPTZ DEFAULT NOW()
);

-- ====================
-- PRÉDICTIONS (cache calcul)
-- ====================
CREATE TABLE predictions (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID REFERENCES users(id) ON DELETE CASCADE,
  forecast_date DATE NOT NULL,
  predicted_balance NUMERIC(10,2),
  anomalies JSONB,
  computed_at TIMESTAMPTZ DEFAULT NOW()
);
