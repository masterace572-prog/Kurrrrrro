-- ============================================================================
--  KURO PANEL — PostgreSQL schema (Supabase / Neon / any Postgres)
--  Direct port of `Free Source /main.sql` (MySQL/MariaDB, CodeIgniter 4)
--  Safe to run repeatedly: every object uses IF NOT EXISTS.
--  Run with:  npm run db:push        (or paste into Supabase SQL editor)
-- ============================================================================

-- ---------------------------------------------------------------- users -----
CREATE TABLE IF NOT EXISTS users (
  id_users        serial       PRIMARY KEY,
  fullname        varchar(155),
  username        varchar(66)  NOT NULL UNIQUE,
  email           varchar(120) NOT NULL DEFAULT '',
  reset_link_token varchar(255) NOT NULL DEFAULT '',
  exp_date        varchar(250) NOT NULL DEFAULT '',
  level           integer      NOT NULL DEFAULT 3,      -- 1 owner, 2 admin, 3 reseller
  saldo           bigint       NOT NULL DEFAULT 0,
  status          smallint     NOT NULL DEFAULT 1,
  uplink          varchar(66)  DEFAULT 'Owner',
  password        varchar(255) NOT NULL,               -- bcrypt(md5(PHP_SALT + pw))
  user_ip         varchar(155) NOT NULL DEFAULT '',
  created_at      timestamptz  NOT NULL DEFAULT now(),
  updated_at      timestamptz  NOT NULL DEFAULT now(),
  expiration_date timestamptz  DEFAULT '2050-01-01 00:00:00+00'
);

-- ----------------------------------------------------------- keys_code -----
CREATE TABLE IF NOT EXISTS keys_code (
  id_keys      serial      PRIMARY KEY,
  game         varchar(32) NOT NULL,
  user_key     varchar(66) NOT NULL UNIQUE,
  duration     integer     NOT NULL DEFAULT 2,          -- hours
  expired_date timestamptz,                             -- NULL = starts on first login
  max_devices  integer     NOT NULL DEFAULT 1,
  devices      text,                                    -- comma separated serials
  status       smallint    NOT NULL DEFAULT 1,
  registrator  varchar(32),
  created_at   timestamptz NOT NULL DEFAULT now(),
  updated_at   timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS keys_code_game_key_idx  ON keys_code (game, user_key);
CREATE INDEX IF NOT EXISTS keys_code_registrator_idx ON keys_code (registrator);
CREATE INDEX IF NOT EXISTS keys_code_expired_idx   ON keys_code (expired_date);

-- ------------------------------------------------------ referral_code ------
CREATE TABLE IF NOT EXISTS referral_code (
  id_reff       serial      PRIMARY KEY,
  code          varchar(128) NOT NULL,                  -- md5(6-char code)
  "Referral"    varchar(16)  NOT NULL,                  -- the human readable code
  level         integer      NOT NULL DEFAULT 3,
  set_saldo     integer      NOT NULL DEFAULT 0,
  used_by       varchar(66)  NOT NULL DEFAULT '',
  created_by    varchar(66)  NOT NULL DEFAULT 'Owner',
  created_at    timestamptz  NOT NULL DEFAULT now(),
  updated_at    timestamptz  NOT NULL DEFAULT now(),
  acc_expiration timestamptz
);
CREATE UNIQUE INDEX IF NOT EXISTS referral_code_code_idx ON referral_code (code);

-- --------------------------------------------------------------- history ---
CREATE TABLE IF NOT EXISTS history (
  id_history serial      PRIMARY KEY,
  keys_id    varchar(33),
  user_do    varchar(33),
  info       text        NOT NULL DEFAULT '',
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

-- --------------------------------------------------------------- modname ---
CREATE TABLE IF NOT EXISTS modname (
  id      integer      PRIMARY KEY,
  modname varchar(100) NOT NULL
);

-- ----------------------------------------------------------------- onoff ----
CREATE TABLE IF NOT EXISTS onoff (
  id      integer     PRIMARY KEY,
  status  varchar(5)  NOT NULL DEFAULT 'off',            -- 'on' = maintenance
  myinput varchar(500) NOT NULL DEFAULT ''
);

-- ---------------------------------------------------------------- _ftext ----
CREATE TABLE IF NOT EXISTS _ftext (
  id      integer      PRIMARY KEY,
  _status varchar(100) NOT NULL,
  _ftext  varchar(100) NOT NULL
);

-- --------------------------------------------------------------- feature ----
CREATE TABLE IF NOT EXISTS feature (
  id          integer    PRIMARY KEY,
  esp         varchar(8) NOT NULL DEFAULT 'on',
  item        varchar(8) NOT NULL DEFAULT 'on',
  silentaim   varchar(8) NOT NULL DEFAULT 'on',
  aim         varchar(8) NOT NULL DEFAULT 'on',
  bullettrack varchar(8) NOT NULL DEFAULT 'on',
  memory      varchar(8) NOT NULL DEFAULT 'on',
  floating    varchar(8) NOT NULL DEFAULT 'on',
  setting     varchar(8) NOT NULL DEFAULT 'on'
);

-- ---------------------------------------------------------------- credit ----
CREATE TABLE IF NOT EXISTS credit (
  id   integer     PRIMARY KEY,
  name varchar(32) NOT NULL DEFAULT ''
);

-- ------------------------------------------------------------------- lib ----
CREATE TABLE IF NOT EXISTS lib (
  id        integer      PRIMARY KEY,
  file      varchar(255) NOT NULL,
  file_type varchar(255) NOT NULL,
  file_size varchar(32)  NOT NULL,
  time      timestamptz  NOT NULL DEFAULT now()
);
