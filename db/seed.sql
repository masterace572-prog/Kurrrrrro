-- ============================================================================
--  KURO PANEL — seed data (mirrors the rows from the original main.sql)
--  Run after schema.sql:  npm run db:seed
-- ============================================================================

-- Single settings rows -------------------------------------------------------
INSERT INTO modname (id, modname) VALUES (1, 'VIP MOD')
  ON CONFLICT (id) DO NOTHING;

INSERT INTO onoff (id, status, myinput) VALUES (1, 'off', '')
  ON CONFLICT (id) DO NOTHING;

INSERT INTO _ftext (id, _status, _ftext) VALUES (1, 'Safe', 'MOD STATUS :- 100% SAFE')
  ON CONFLICT (id) DO NOTHING;

INSERT INTO feature (id, esp, item, silentaim, aim, bullettrack, memory, floating, setting)
  VALUES (1, 'on', 'on', 'on', 'on', 'on', 'on', 'on', 'on')
  ON CONFLICT (id) DO NOTHING;

INSERT INTO credit (id, name) VALUES (1, '') ON CONFLICT (id) DO NOTHING;

INSERT INTO lib (id, file, file_type, file_size, time)
  VALUES (1, 'lib.so', 'Onlinelib/lib.so', '555 KB', now())
  ON CONFLICT (id) DO NOTHING;

-- Owner account --------------------------------------------------------------
-- username: admin      password: admin123
-- This is the *original* hash from the MySQL dump. It is
-- bcrypt(md5('XquxmymXDtWRA66D' + 'admin123')), so the same credential that
-- worked on the PHP panel works here. CHANGE IT AFTER YOUR FIRST LOGIN.
INSERT INTO users (
  id_users, fullname, username, email, reset_link_token, exp_date, level, saldo,
  status, uplink, password, user_ip, created_at, updated_at, expiration_date
) VALUES (
  1, 'admin', 'admin', 'support@aloneboy.com', '', '2023-07-23 01:22:54', 1, 2147384644,
  1, 'Owner', '$2y$08$/CsSVgrGgCqVcievCuR2COPnlMIpRz6kA.hzItBD/xd1Cx0hj0kMK', '',
  now(), now(), '2050-01-01 00:00:00+00'
)
ON CONFLICT (id_users) DO NOTHING;

-- Keep the serial sequence in sync after explicit ids
SELECT setval(pg_get_serial_sequence('users', 'id_users'),
              COALESCE((SELECT MAX(id_users) FROM users), 1));
