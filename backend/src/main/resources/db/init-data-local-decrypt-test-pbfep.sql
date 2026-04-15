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
      log_time, media_gb, tr_code, brodid, pub_ip, prt_ip, log_ch_cd,
      msg_code, data, bmsg, reserve, con_key, wire_ts
    )
    VALUES (
      '20260413102000000000', '99', 'SLDECT01', 'local_decrypt',
      '127.0.0.1   ', '127.0.0.1   ', 'PC    ',
      '0200',
      'Jo1mpzWoGoFvutn6NhYlAQH7UGba/3BzBB+08fQcc6eblGiskh+a8X2ezhLPImkh2q8taaqNTr8ag8g5YqNY5A==',
      'Jo1mpzWoGoFvutn6NhYlAWF0TdQ6qM7HOrgg2APz2JD11Yx0D7oZHW72bgek5T40AjwbZNUTDamDntmnp5dhXg==',
      'LDPT',
      rpad('LOCAL-DEC-SND1', 18),
      '2026-04-13 10:20:00'
    );
    INSERT INTO pb_send (
      log_time, media_gb, tr_code, brodid, pub_ip, prt_ip, log_ch_cd,
      msg_code, data, bmsg, reserve, con_key, wire_ts
    )
    VALUES (
      '20260413102500000000', '99', 'SLDECT02', 'local_decrypt',
      '127.0.0.1   ', '127.0.0.1   ', 'MOB   ',
      '0201',
      'Jo1mpzWoGoFvutn6NhYlAeJDM1z39G71BSMqdD6RQfvQcRQ19DnNwC5cI/Tn2oWw2J9gxHyxHNjnU86Bb/zwhA==',
      'Jo1mpzWoGoFvutn6NhYlAcqNz7Kbb6ZfV8qf94Ty8+gnZzMGFybxSBJOChcxmgpI0FFZNrda9jvvtgdJ6FaeTQ==',
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
      log_time, media_gb, tr_code, brodid, pub_ip, prt_ip, log_ch_cd,
      msg_code, data, bmsg, reserve, con_key, wire_ts
    )
    VALUES (
      '20260413102100000000', '99', 'RLDECT01', 'local_decrypt',
      '127.0.0.1   ', '127.0.0.1   ', 'PC    ',
      '0200',
      'Jo1mpzWoGoFvutn6NhYlAYNldecMIdFXJZH2U3XHKwHbrqznyVBWh+Hdpr+c4hMX6sFoyXUd6DRNeIdmRSka6Q==',
      'Jo1mpzWoGoFvutn6NhYlAStAuD8FdsybPmFh4c+01Tls44GOk/wFw3HlN6938GyNOZxakGPJfQWHCfYRbw1rPA==',
      'LDPT',
      rpad('LOCAL-DEC-RCV1', 18),
      '2026-04-13 10:21:00'
    );
    INSERT INTO pb_recv (
      log_time, media_gb, tr_code, brodid, pub_ip, prt_ip, log_ch_cd,
      msg_code, data, bmsg, reserve, con_key, wire_ts
    )
    VALUES (
      '20260413102600000000', '99', 'RLDECT02', 'local_decrypt',
      '127.0.0.1   ', '127.0.0.1   ', 'MOB   ',
      '0200',
      'Jo1mpzWoGoFvutn6NhYlAc/Kk/Og0NXIPQ0wUCmzpaG3MNnEGrXjkaiVDcwAVULJIsVu3zfeOZsartVDM9x0aw==',
      'Jo1mpzWoGoFvutn6NhYlAafrbINJ+vtDJghcpx8Ziroav4yh3v1fVB9L24pM0KFXn0ccg3P46LI0saZAF5+6oA==',
      'LDPT',
      rpad('LOCAL-DEC-RCV2', 18),
      '2026-04-13 10:26:00'
    );
  END IF;
END $$;
