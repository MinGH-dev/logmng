-- =============================================================================
-- LOCAL / DEVELOPMENT ONLY — PB FEP keyword decrypt-for-match fixture.
-- Requirement: docs/requirements/20260415-pb-fep-keyword-decrypt-and-plaintext-search.md §2 DB
--
-- Dev key (same as init-data-local-decrypt-test-pbfep.sql):
--   CryptoUtil + LogPayloadCryptoVariant.PB_FEP, encryptionKey 12345678901234567890123456789012
--
-- Known plaintext token (must match backend tests): PB-FEP-KW-TEST-20260415
-- The token appears only inside decrypted JSON for the positive rows; ciphertext
-- strings were checked not to contain the literal token substring.
--
-- Idempotency: reserve = 'PFKW' (4-char). DELETE ... WHERE reserve = 'PFKW' before INSERT.
-- log_time: VARCHAR(20) yyyyMMddHHmmssSSSSSS (microseconds, zero-padded).
-- =============================================================================

DO $$
BEGIN
  IF to_regclass('pb_send') IS NULL THEN
    RAISE NOTICE 'seed-pb-fep-keyword-decrypt-20260415: pb_send not found — skip.';
  ELSE
    DELETE FROM pb_send WHERE reserve = 'PFKW';
    -- Positive: keyword only after decrypt on vlen (request_data); data/bmsg have no token.
    INSERT INTO pb_send (
      log_time, media_gb, tr_code, brodid, pub_ip, prt_ip, log_ch_cd,
      msg_code, vlen, data, bmsg, reserve, con_key, wire_ts
    )
    VALUES (
      '20260415103000111111', '99', 'PKWTSV01', 'kwseed_20260415',
      '127.0.0.1   ', '127.0.0.1   ', 'PC    ',
      '0200',
      'Jo1mpzWoGoFvutn6NhYlAYfG/cRQT5/cfX/UJ27HDoPD6Mp0Oow7xiKXD/GgGH9QsQ86wCLRoIsDu/DYhV0AAtjlv+0AbpgJS4GAWIEqHQ4=',
      'Jo1mpzWoGoFvutn6NhYlAWPgEgM9Kuk0KrmwX0AYxswLjSxEtyy/kKS9K3HzzW7ywoV7qPqtDrgvlZqNbWMuYGnY7bWvgRw+7l7ZkwKR6X4=',
      'ERR-NO-KW',
      'PFKW',
      rpad('PFKW-SEND-V', 18),
      '2026-04-15 10:30:00'
    );
    -- Positive: keyword only after decrypt on data (response_data); vlen decrypts without token.
    INSERT INTO pb_send (
      log_time, media_gb, tr_code, brodid, pub_ip, prt_ip, log_ch_cd,
      msg_code, vlen, data, bmsg, reserve, con_key, wire_ts
    )
    VALUES (
      '20260415103000222222', '99', 'PKWTSV02', 'kwseed_20260415',
      '127.0.0.1   ', '127.0.0.1   ', 'PC    ',
      '0200',
      'Jo1mpzWoGoFvutn6NhYlAWPgEgM9Kuk0KrmwX0AYxswLjSxEtyy/kKS9K3HzzW7ywoV7qPqtDrgvlZqNbWMuYGnY7bWvgRw+7l7ZkwKR6X4=',
      'Jo1mpzWoGoFvutn6NhYlATRxH3yQ9zzjcUSSL2y2+a2/S5rPxqScyKsVucoZy4KuLBU6CLWT5LamTRpWutsy9MZyPfL9beX2GSe//PLt7pI=',
      'ERR-NO-KW',
      'PFKW',
      rpad('PFKW-SEND-D', 18),
      '2026-04-15 10:30:01'
    );
    -- Positive: token inside ciphertext for both vlen and data (same wire ciphertext payload).
    INSERT INTO pb_send (
      log_time, media_gb, tr_code, brodid, pub_ip, prt_ip, log_ch_cd,
      msg_code, vlen, data, bmsg, reserve, con_key, wire_ts
    )
    VALUES (
      '20260415103000333333', '99', 'PKWTSV03', 'kwseed_20260415',
      '127.0.0.1   ', '127.0.0.1   ', 'PC    ',
      '0200',
      'Jo1mpzWoGoFvutn6NhYlASk3+5vJaLqYIzRUTazLvxT+nRrb4n9m0yr/fGN4zFCsYYScSEiVdKzLdhg7K+9eDWvYTvGQG7bEkzu7D+O/3bo=',
      'Jo1mpzWoGoFvutn6NhYlASk3+5vJaLqYIzRUTazLvxT+nRrb4n9m0yr/fGN4zFCsYYScSEiVdKzLdhg7K+9eDWvYTvGQG7bEkzu7D+O/3bo=',
      'ERR-NO-KW',
      'PFKW',
      rpad('PFKW-SEND-B', 18),
      '2026-04-15 10:30:02'
    );
    -- Control: ciphertext only; plaintext columns do not contain the keyword token.
    INSERT INTO pb_send (
      log_time, media_gb, tr_code, brodid, pub_ip, prt_ip, log_ch_cd,
      msg_code, vlen, data, bmsg, reserve, con_key, wire_ts
    )
    VALUES (
      '20260415103000444444', '99', 'PKWTSCTL', 'kwseed_20260415',
      '127.0.0.1   ', '127.0.0.1   ', 'PC    ',
      '0200',
      'Jo1mpzWoGoFvutn6NhYlAWPgEgM9Kuk0KrmwX0AYxswLjSxEtyy/kKS9K3HzzW7ywoV7qPqtDrgvlZqNbWMuYGnY7bWvgRw+7l7ZkwKR6X4=',
      'Jo1mpzWoGoFvutn6NhYlAWPgEgM9Kuk0KrmwX0AYxswLjSxEtyy/kKS9K3HzzW7ywoV7qPqtDrgvlZqNbWMuYGnY7bWvgRw+7l7ZkwKR6X4=',
      'PLAIN-CTRL-NO-TOKEN-20260415',
      'PFKW',
      rpad('PFKW-SEND-C', 18),
      '2026-04-15 10:30:03'
    );
  END IF;

  IF to_regclass('pb_recv') IS NULL THEN
    RAISE NOTICE 'seed-pb-fep-keyword-decrypt-20260415: pb_recv not found — skip.';
  ELSE
    DELETE FROM pb_recv WHERE reserve = 'PFKW';
    -- Positive recv: token only in vlen ciphertext.
    INSERT INTO pb_recv (
      log_time, media_gb, tr_code, brodid, pub_ip, prt_ip, log_ch_cd,
      msg_code, vlen, data, bmsg, reserve, con_key, wire_ts
    )
    VALUES (
      '20260415103100111111', '99', 'PKWTRV01', 'kwseed_20260415',
      '127.0.0.1   ', '127.0.0.1   ', 'PC    ',
      '0200',
      'Jo1mpzWoGoFvutn6NhYlAYfG/cRQT5/cfX/UJ27HDoPD6Mp0Oow7xiKXD/GgGH9QsQ86wCLRoIsDu/DYhV0AAtjlv+0AbpgJS4GAWIEqHQ4=',
      'Jo1mpzWoGoFvutn6NhYlAWPgEgM9Kuk0KrmwX0AYxswLjSxEtyy/kKS9K3HzzW7ywoV7qPqtDrgvlZqNbWMuYGnY7bWvgRw+7l7ZkwKR6X4=',
      'ERR-NO-KW',
      'PFKW',
      rpad('PFKW-RECV-V', 18),
      '2026-04-15 10:31:00'
    );
    -- Control recv
    INSERT INTO pb_recv (
      log_time, media_gb, tr_code, brodid, pub_ip, prt_ip, log_ch_cd,
      msg_code, vlen, data, bmsg, reserve, con_key, wire_ts
    )
    VALUES (
      '20260415103100222222', '99', 'PKWTRCTL', 'kwseed_20260415',
      '127.0.0.1   ', '127.0.0.1   ', 'PC    ',
      '0200',
      'Jo1mpzWoGoFvutn6NhYlAWPgEgM9Kuk0KrmwX0AYxswLjSxEtyy/kKS9K3HzzW7ywoV7qPqtDrgvlZqNbWMuYGnY7bWvgRw+7l7ZkwKR6X4=',
      'Jo1mpzWoGoFvutn6NhYlAWPgEgM9Kuk0KrmwX0AYxswLjSxEtyy/kKS9K3HzzW7ywoV7qPqtDrgvlZqNbWMuYGnY7bWvgRw+7l7ZkwKR6X4=',
      'PLAIN-CTRL-NO-TOKEN-20260415',
      'PFKW',
      rpad('PFKW-RECV-C', 18),
      '2026-04-15 10:31:01'
    );
  END IF;
END $$;
