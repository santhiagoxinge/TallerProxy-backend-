-- PostgreSQL schema used by the backend (Supabase). Apply it once on an empty database.
-- Everything lives in its own schema so it never mixes with other applications.

create schema if not exists proxy_workshop;

create table proxy_workshop.products (
  sku text primary key,
  name text not null,
  stock integer not null check (stock >= 0),
  updated_at timestamptz not null default now()
);

create sequence proxy_workshop.order_seq start 1001;

create table proxy_workshop.orders (
  order_id text primary key,
  customer_id text not null,
  sku text not null references proxy_workshop.products (sku),
  quantity integer not null check (quantity > 0),
  total numeric(12, 2) not null,
  status text not null check (status in ('CREATED', 'CANCELLED')),
  created_at timestamptz not null default now()
);

create table proxy_workshop.payments (
  payment_id text primary key,
  order_id text not null references proxy_workshop.orders (order_id),
  amount numeric(12, 2) not null check (amount > 0),
  status text not null check (status in ('APPROVED', 'REFUNDED')),
  processed_at timestamptz not null default now()
);

-- Audit trail written by LoggingProxy
create table proxy_workshop.call_logs (
  request_id uuid primary key,
  service_id text not null,
  operation text not null,
  duration_ms bigint not null,
  status text not null check (status in ('SUCCESS', 'ERROR')),
  created_at timestamptz not null,
  params jsonb,
  response jsonb,
  error text
);

create index call_logs_created_idx on proxy_workshop.call_logs (created_at desc);
create index call_logs_service_status_idx on proxy_workshop.call_logs (service_id, status, created_at desc);
create index orders_sku_idx on proxy_workshop.orders (sku);
create index payments_order_idx on proxy_workshop.payments (order_id);

insert into proxy_workshop.products (sku, name, stock) values
  ('SKU-LAPTOP', 'Laptop', 25),
  ('SKU-MOUSE', 'Wireless mouse', 140),
  ('SKU-KEYBOARD', 'Mechanical keyboard', 80),
  ('SKU-MONITOR', 'Monitor 27 inch', 40),
  ('SKU-HEADSET', 'Headset', 60);

-- Row level security on, no access for the public API roles.
alter table proxy_workshop.products enable row level security;
alter table proxy_workshop.orders enable row level security;
alter table proxy_workshop.payments enable row level security;
alter table proxy_workshop.call_logs enable row level security;

revoke all on schema proxy_workshop from public, anon, authenticated;
revoke all on all tables in schema proxy_workshop from public, anon, authenticated;
revoke all on all sequences in schema proxy_workshop from public, anon, authenticated;

-- Dedicated login for the backend, limited to this schema. Choose your own password.
-- create role proxy_app login password '<choose-a-strong-password>' nosuperuser nocreatedb nocreaterole noinherit;
-- grant usage on schema proxy_workshop to proxy_app;
-- grant select, insert, update, delete on all tables in schema proxy_workshop to proxy_app;
-- grant usage, select on all sequences in schema proxy_workshop to proxy_app;
-- create policy proxy_app_all on proxy_workshop.products for all to proxy_app using (true) with check (true);
-- (repeat the policy for orders, payments and call_logs)
