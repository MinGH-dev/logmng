-- =============================================================================
-- LOCAL / DEVELOPMENT ONLY — PB FEP (pb_send / pb_recv). ProObject AES without E002;
-- decrypt with LogPayloadCryptoVariant.PB_FEP and dev key 12345678901234567890123456789012.
-- Rows marked reserve = 'LDPT' (4-char tag) for idempotent delete.
-- =============================================================================

DO $$
BEGIN
  IF to_regclass('pb_send') IS NULL THEN
    RAISE NOTICE 'init-data-local-decrypt-test-pbfep: pb_send not found — skip.';
  ELSE
    DELETE FROM pb_send WHERE reserve = 'LDPT';
    INSERT INTO pb_send (
      log_timestamp, media_gb, tr_code, brodid, pub_ip, prt_ip, log_ch_cd,
      msg_code, data, bmsg, reserve, con_key, wire_ts
    )
    VALUES (
      TIMESTAMP '2026-04-13 10:20:00', '99', 'SLDECT01', 'local_decrypt',
      '127.0.0.1   ', '127.0.0.1   ', 'PC    ',
      '0200',
      'Jo1mpzWoGoFvutn6NhYlAYqEYwp4IhULCXShlxAHK5GutM8YJHLKCPjhJwl5nMp7dbDFF0909WMC8TuchyX1HA==',
      'Jo1mpzWoGoFvutn6NhYlARmEZroo9TXu45zg1P1ukA3IHYk1ipdOZ6SOCnscVoiMSZ38xquZEUNosNQlyysgxw==',
      'LDPT',
      rpad('LOCAL-DEC-SND1', 18),
      '2026-04-13 10:20:00'
    );
    INSERT INTO pb_send (
      log_timestamp, media_gb, tr_code, brodid, pub_ip, prt_ip, log_ch_cd,
      msg_code, data, bmsg, reserve, con_key, wire_ts
    )
    VALUES (
      TIMESTAMP '2026-04-13 10:25:00', '99', 'SLDECT02', 'local_decrypt',
      '127.0.0.1   ', '127.0.0.1   ', 'MOB   ',
      '0201',
      'Jo1mpzWoGoFvutn6NhYlARw4WzTxYboWpdYxZa09MCuNZwai+nhNHRf09HlmCUW38a+WhYIuZQOlsvdglLY2Iw==',
      'Jo1mpzWoGoFvutn6NhYlAUGt3Y7Xp6cBH6bJ/PWRI4nXhoN3l3NoBMVy3wcmB3xBu9wm4mJbY2/FesC0wVf8nw==',
      'LDPT',
      rpad('LOCAL-DEC-SND2', 18),
      '2026-04-13 10:25:00'
    );
  END IF;

  IF to_regclass('pb_recv') IS NULL THEN
    RAISE NOTICE 'init-data-local-decrypt-test-pbfep: pb_recv not found — skip.';
  ELSE
    DELETE FROM pb_recv WHERE reserve = 'LDPT';
    INSERT INTO pb_recv (
      log_timestamp, media_gb, tr_code, brodid, pub_ip, prt_ip, log_ch_cd,
      msg_code, data, bmsg, reserve, con_key, wire_ts
    )
    VALUES (
      TIMESTAMP '2026-04-13 10:21:00', '99', 'RLDECT01', 'local_decrypt',
      '127.0.0.1   ', '127.0.0.1   ', 'PC    ',
      '0200',
      'Jo1mpzWoGoFvutn6NhYlAZXmVaAmsqHTPlmXMrJHo1BZOyDm0/fEeUWYTdjzLfvq+K+sBn9JcTI1lcW0chBN7A==',
      'Jo1mpzWoGoFvutn6NhYlASYtFfpR1ODWdpHqP3Zi2CapO15FkzFYTXkOes3sPy5IPsha+A9RrPMzEpVQxgprig==',
      'LDPT',
      rpad('LOCAL-DEC-RCV1', 18),
      '2026-04-13 10:21:00'
    );
    INSERT INTO pb_recv (
      log_timestamp, media_gb, tr_code, brodid, pub_ip, prt_ip, log_ch_cd,
      msg_code, data, bmsg, reserve, con_key, wire_ts
    )
    VALUES (
      TIMESTAMP '2026-04-13 10:26:00', '99', 'RLDECT02', 'local_decrypt',
      '127.0.0.1   ', '127.0.0.1   ', 'MOB   ',
      '0200',
      'Jo1mpzWoGoFvutn6NhYlAXwvoIC8E5TEQ16S5DlLisI4O7rwEA9jwavFZ5PXDCdPMJQtpQ51uMbU48tGUqKRfw==',
      'Jo1mpzWoGoFvutn6NhYlAYf/Va0iU7VomgHMDV8lpWwhcswtB3vNccQF8xFUTr21+vtbMDAcpcuRU7TddazUmQ==',
      'LDPT',
      rpad('LOCAL-DEC-RCV2', 18),
      '2026-04-13 10:26:00'
    );
  END IF;
END $$;
