-- =============================================================================
-- LOCAL / DEVELOPMENT ONLY — ProObject AES (E002+Base64) via dev key
-- 12345678901234567890123456789012 (application.yml / ENCRYPTION_KEY).
-- Regenerate: cd backend && mvn -q test-compile && java -cp "target/test-classes:target/classes:$(mvn -q dependency:build-classpath -Dmdep.outputFile=/tmp/cp.txt && cat /tmp/cp.txt)" \
--   com.logmng.util.LocalDecryptSampleSeedGenerator src/main/resources/db/init-data-local-decrypt-test-imagelog.sql
--
-- Plaintext for each field: see generateFullSeedSql() in this generator class (source).
-- =============================================================================

-- Replace prior local decrypt test rows (re-seed safe)
DELETE FROM imagelog WHERE guid LIKE 'LOCAL-DECRYPT-TST-IM-%';

INSERT INTO imagelog (application, servicegroup, service, status, data, datastring, guid, header, headerstring, insert_time)
VALUES (
  'LDP',
  'EduSG',
  'SE10002_select',
  'input',
  'E002Jo1mpzWoGoFvutn6NhYlAZ/DVSL1YiN8pdLElQz/2GoC7r+zxR2iVg+HAzJBLojR5n+ZGQj+DrUWKkW00LBLQg==',
  '{"id":"LD1","name":"시드평문이름","p":"[E002Jo1mpzWoGoFvutn6NhYlARgM9TM78jxM6400eaQ1Uw8B+icv452big9dbE+HLK5mxjRmy5C1/DXhNPeFqrmqs4u3RW5yTk9R9xK0Ck5cPng=]"}',
  'LOCAL-DECRYPT-TST-IM-0001',
  'E002Jo1mpzWoGoFvutn6NhYlAU6rOto2wonwKiKwAovBV0ND3Xbf7JUAx8CzIre1ooP7HQKBMRmWQG+H6JHiO8Ezqw==',
  '{"flag":"\u0000","inputMsgType":"JSON","guid":"LOCAL-DECRYPT-TST-IM-0001"}',
  (EXTRACT(EPOCH FROM NOW() - INTERVAL '5 minutes') * 1000)::bigint
);

INSERT INTO imagelog (application, servicegroup, service, status, data, datastring, guid, header, headerstring, insert_time)
VALUES (
  'LDP',
  'EduSG',
  'SE10002_select',
  'output',
  'E002Jo1mpzWoGoFvutn6NhYlAZFdy39CBxWMQaAq0e7unKunsgpy51NV+mERaZoMTfW2v+SMbTYlxTufk+A4Y8KSSiL9BZe1VmzZW1ef9yy1x4Q=',
  '{"result":"ok","blob":"[E002Jo1mpzWoGoFvutn6NhYlAdiy57lU9YEjh+Nqbyl42mbp9cLuKmutk2uI36WBQX+TpBZPH/75HmSywRbQIQ24yGiLRUrDtC1RsaTGO7joUnU=]"}',
  'LOCAL-DECRYPT-TST-IM-0001',
  'E002Jo1mpzWoGoFvutn6NhYlASgF2TPWSy5pb5KLChQk0bbhuH8cnaG3cHmAJ+ayECN1JRNV00ZPU6rMMa8sREgIGl54Tjln2IvhCWdG2Q6m0yk=',
  '{"flag":"\u0000","responseCode":"200"}',
  (EXTRACT(EPOCH FROM NOW() - INTERVAL '6 minutes') * 1000)::bigint
);

INSERT INTO imagelog (application, servicegroup, service, status, data, datastring, guid, header, headerstring, insert_time)
VALUES (
  'LDP',
  'EduSG',
  'SE10003_insert',
  'input',
  'E002Jo1mpzWoGoFvutn6NhYlASVe69bvbrVgwwGR9QVHCWJ1Y+/xxrx5HczXv08EEixZl4irYi3bCSHImdmK8dFJ/g==',
  '{"user":"tester","secret":"[E002Jo1mpzWoGoFvutn6NhYlAWQnAqAu2AebABENsOTA1/GPr2JbkEqiE10szJy/4nFa+BFyHGsJkbs/NxyVTK+bXw==]"}',
  'LOCAL-DECRYPT-TST-IM-0002',
  'E002Jo1mpzWoGoFvutn6NhYlAY62TtZGxFzVd9toM5wyP/Q/qHxtzbcHVY/4l1HurF8KXKzfEw78h8HashLtKLZN2g==',
  '{"token":"[E002Jo1mpzWoGoFvutn6NhYlAZgBwsHy3JBBPkYVT2MhGb/Nc+VQPwvkSziW3BZ4C7L2xkEcZ5KBajkOsww2uzFT6w==]"}',
  (EXTRACT(EPOCH FROM NOW() - INTERVAL '7 minutes') * 1000)::bigint
);

INSERT INTO imagelog (application, servicegroup, service, status, data, datastring, guid, header, headerstring, insert_time)
VALUES (
  'SYSTEM_B',
  'GrpLoc',
  'SVC_DEC',
  'error',
  'E002Jo1mpzWoGoFvutn6NhYlAbDW33Epwds1/tExNOncs0Di6NC+KpygIhxvPyg1axaPXPucL1FQasZXadMqOIhAqg==',
  '{"code":"ERR","detail":"[E002Jo1mpzWoGoFvutn6NhYlAaSc2N7lpK+Y46qGgSUGCHcrBQOqs95lKI9YkrHBaWwHok17+Kb/FNYSpUBN+gwPkzZztDZS3wEihyHUjaO1o5A=]"}',
  'LOCAL-DECRYPT-TST-IM-0003',
  'E002Jo1mpzWoGoFvutn6NhYlAfauQyuWQV94bMM3D4gu/8MDVZjRu/UOK+Cm4WNR379yTPvgw8ssfbS9ihGfeyiFFA==',
  '{"guid":"LOCAL-DECRYPT-TST-IM-0003","code":"500"}',
  (EXTRACT(EPOCH FROM NOW() - INTERVAL '8 minutes') * 1000)::bigint
);

INSERT INTO imagelog (application, servicegroup, service, status, data, datastring, guid, header, headerstring, insert_time)
VALUES (
  'APP_C',
  'GrpLoc',
  'SVC_MIX',
  'input',
  'E002Jo1mpzWoGoFvutn6NhYlAd51/Tb5YFX9ADUcdFODP9RqYZ31jM8yT5U4UWSQoQyq9B9eb0KB5NLLDZ1NOoADcQ==',
  '{"mixed":"plain","nested":{"c":"[E002Jo1mpzWoGoFvutn6NhYlAbNsaQESA0REZEVe8wIGuCBDFeENcuiUC2Doi8RkMtQ0MB4cATHRq87I6FkGP94YGw==]"}}',
  'LOCAL-DECRYPT-TST-IM-0004',
  'E002Jo1mpzWoGoFvutn6NhYlAb5YSzp5SJYmJjWqwGmvbGQv35wcTcePmc3xYd0UImP4KcfjPbi/nq1qahJgzrZ+Cw==',
  '{"phase":"request","sid":"[E002Jo1mpzWoGoFvutn6NhYlASJubXlLvAGqaFSckTF/jWQ9fBbG4FeTnCatqNP3dVn9qIS4TQMOwEHDT0721Iz6tg==]"}',
  (EXTRACT(EPOCH FROM NOW() - INTERVAL '9 minutes') * 1000)::bigint
);

INSERT INTO imagelog (application, servicegroup, service, status, data, datastring, guid, header, headerstring, insert_time)
VALUES (
  'LDP',
  'EduSG',
  'SE10004_update',
  'input',
  'E002Jo1mpzWoGoFvutn6NhYlAcEwPCWke70FXXGkCBmGnUtZ81CNbrglXSndbEUFTp+nWHosIYARS/M8YtlR8jiOjg==',
  '{"id":"9001","name":"로컬테스트","diff":"[E002Jo1mpzWoGoFvutn6NhYlAaUpRTFrGQdrHeJ7Nht9VmQE23azcTSoZVzg73obUJF8oovPjbrPHq+X9ulMMJEVGw==]"}',
  'LOCAL-DECRYPT-TST-IM-0005',
  'E002Jo1mpzWoGoFvutn6NhYlAfKB3FG5ealutLFZo2koE4HnBDTV3oO+aLuG2+HpY7l8L0VMdzGnCpYk8qir9qUCgg==',
  '{"inputMsgType":"JSON"}',
  (EXTRACT(EPOCH FROM NOW() - INTERVAL '10 minutes') * 1000)::bigint
);

INSERT INTO imagelog (application, servicegroup, service, status, data, datastring, guid, header, headerstring, insert_time)
VALUES (
  'LDP',
  'EduSG',
  'SE10004_update',
  'output',
  'E002Jo1mpzWoGoFvutn6NhYlAcweCEIRvdoOGJoKw2Fbji2fse4ubM3A9aPAKXfk0jbMyErM2+bAqoWdNlolTaoyXA==',
  '{"updated":1,"audit":"[E002Jo1mpzWoGoFvutn6NhYlARwEwe2qz+3HIzpPZNtxZtdzOQ2OXPyerqG05nW/bCkSMsr6vBxFQrfb5cPwmqD7Cw==]"}',
  'LOCAL-DECRYPT-TST-IM-0005',
  'E002Jo1mpzWoGoFvutn6NhYlAfYS/xnekZI7tjTb389QJ//Qsv+uhpRm/fAHNDrPPLk0d0XSa+9J6Gm2PADpmI7Ogw==',
  '{"responseCode":"200"}',
  (EXTRACT(EPOCH FROM NOW() - INTERVAL '11 minutes') * 1000)::bigint
);

INSERT INTO imagelog (application, servicegroup, service, status, data, datastring, guid, header, headerstring, insert_time)
VALUES (
  'MONITOR',
  'Ops',
  'Trace',
  'input',
  'E002Jo1mpzWoGoFvutn6NhYlAZfEJOB4PoVvEIliq4cOS1FIFD+rhLUyNTb0QvbcoNQX15BIxPoZRxpGz/lzR9wazRYnmSqNz7/OFlAvIYxvB78=',
  '{"span":"[E002Jo1mpzWoGoFvutn6NhYlAU0wPvYEeYYaum6lilkndhNKMN674Oe1cVF3/apQ4YfM3igrim9VrEE1Tz10UAsBgA==]","parent":"[E002Jo1mpzWoGoFvutn6NhYlAb0+N/y06Pjdewig01DCuwInF9eJO2FCvls5uhTC5sy0hT1r+nvW+uJ3sEWZMTVZkw==]"}',
  'LOCAL-DECRYPT-TST-IM-0006',
  'E002Jo1mpzWoGoFvutn6NhYlAZgWwT1/F2cxl1pGow3RmCrZ+0Fsl1JJnPXhBnwBTcSb0EnXeFgoX+U0qtZ+5imtqQ==',
  '{}',
  (EXTRACT(EPOCH FROM NOW() - INTERVAL '12 minutes') * 1000)::bigint
);

INSERT INTO imagelog (application, servicegroup, service, status, data, datastring, guid, header, headerstring, insert_time)
VALUES (
  'BATCH',
  'Job',
  'J_DEC',
  'output',
  'E002Jo1mpzWoGoFvutn6NhYlAfwebXIyWjShATJFeulQdRyztVMOOVd4Bo81tHeGwc07CSNatvTI0hQJ0m1lqZ1IGY8rjlyb2+VXx+mVvU8AZPA=',
  '{"batchId":"b1","checksum":"[E002Jo1mpzWoGoFvutn6NhYlAdwjHcMfoFnpdusvMhryQWTfIzEqYwekwcHGAgkXCqARFLdunUJmf0E4NMm1uuKwHg==]"}',
  'LOCAL-DECRYPT-TST-IM-0007',
  'E002Jo1mpzWoGoFvutn6NhYlAYVohs3nKAT/OSkU0S6iesdjGkJUOAXldEGdsw2gBoO+fe7TH13yR+5iv+XS9RrEPQ==',
  '{"done":true}',
  (EXTRACT(EPOCH FROM NOW() - INTERVAL '13 minutes') * 1000)::bigint
);

INSERT INTO imagelog (application, servicegroup, service, status, data, datastring, guid, header, headerstring, insert_time)
VALUES (
  'LDP',
  'EduSG',
  'SE10002_select',
  'input',
  'E002Jo1mpzWoGoFvutn6NhYlAXYrCSwtff13UABcLKIM5pnV4mHUyk+bKjmNDp2a8MZsohDwhgA4uuP+6SSVTFZAyg==',
  '{"pair":"A","payload":"[E002Jo1mpzWoGoFvutn6NhYlAa6BBZn/LzmRh7PVftrU4blRK3S+uQEVT8eYTDecQSIVk1mv0rmo9BT0URGxunyuXQ==]"}',
  'LOCAL-DECRYPT-TST-IM-0008',
  'E002Jo1mpzWoGoFvutn6NhYlAVa3G+V+eT4iBN0VptEkPANvze9BpA6NKJyKS444jUzhTWcQ4Dqb9ly49BuSq5Hzbw==',
  '{}',
  (EXTRACT(EPOCH FROM NOW() - INTERVAL '14 minutes') * 1000)::bigint
);

INSERT INTO imagelog (application, servicegroup, service, status, data, datastring, guid, header, headerstring, insert_time)
VALUES (
  'LDP',
  'EduSG',
  'SE10002_select',
  'output',
  'E002Jo1mpzWoGoFvutn6NhYlAYRc9vCBkXaTXveGt86QcGFDDGFNEo+ukUAeTYdqrad8qKGnbQgEygqnyVI1Zy0j9Q==',
  '{"pair":"B","payload":"[E002Jo1mpzWoGoFvutn6NhYlAYACnpeCieEnL/36eXJgDbaQrXbMxdlvAI6Z/370Dm3gHRQ7bhDk/2o+O2K1nWQ15g==]"}',
  'LOCAL-DECRYPT-TST-IM-0008',
  'E002Jo1mpzWoGoFvutn6NhYlAXhSzm9/523fzYXzxrleVyH0PmeuhUmntP+1Us8TnWDxCeeWm2XFBRIi7AK6PSl9WA==',
  '{}',
  (EXTRACT(EPOCH FROM NOW() - INTERVAL '15 minutes') * 1000)::bigint
);

INSERT INTO imagelog (application, servicegroup, service, status, data, datastring, guid, header, headerstring, insert_time)
VALUES (
  'LDP',
  'EduSG',
  'SE10005_delete',
  'input',
  'E002Jo1mpzWoGoFvutn6NhYlARj9SDDuBvA9rlZrlqr1Etw/4SfjkS/ORrDziC06VeSjWXYrV6bgdj5trMcZY6rmRw==',
  '{"id":"[E002Jo1mpzWoGoFvutn6NhYlAZJysWkJAd8te0VUOklBH1Q62bhSu0GYMkndcgw+Lbsh7IU1np4wvy9/kHT4ghxJxA==]"}',
  'LOCAL-DECRYPT-TST-IM-0009',
  'E002Jo1mpzWoGoFvutn6NhYlAQPkiTccJxBpSnYAdBIGyW7JSOue0r75owBMrP35NUadhwy1Y+nvHouNzr+jBZIRow==',
  '{"reason":"[E002Jo1mpzWoGoFvutn6NhYlAcg0ASMaQZ8pTIhoNfiKgEqbw3aMRVyySFH278WYL5G3sSFPkBONzk2a7XWRw+VRZQ==]"}',
  (EXTRACT(EPOCH FROM NOW() - INTERVAL '16 minutes') * 1000)::bigint
);

INSERT INTO imagelog (application, servicegroup, service, status, data, datastring, guid, header, headerstring, insert_time)
VALUES (
  'LDP',
  'EduSG',
  'SE10002_select',
  'input',
  'E002Jo1mpzWoGoFvutn6NhYlAd91NjG1BE/vvRnwHHQ7c+MjRFiIK/ZT6+y8QayhmpVje7eaovxICFp9ODgtvYPfYQ==',
  '{"ws":"  [E002Jo1mpzWoGoFvutn6NhYlARrcEfhdZiXzmUclNccP+JpNx57RVO7uXT+S8VLCpX2BY9CiNiCvGDbVVJG8Pvt1Bg==]  \t"}',
  'LOCAL-DECRYPT-TST-IM-0010',
  'E002Jo1mpzWoGoFvutn6NhYlAX4ozjnAYGl8HmpCoUdS3IoaCndkUrbcY4cVvNo91OUqKH4f9zUmvh0CaAv7XDeTeQ==',
  '{"note":"bracket edge cases"}',
  (EXTRACT(EPOCH FROM NOW() - INTERVAL '17 minutes') * 1000)::bigint
);

INSERT INTO imagelog (application, servicegroup, service, status, data, datastring, guid, header, headerstring, insert_time)
VALUES (
  'LDP',
  'EduSG',
  'SE10002_select',
  'output',
  'E002Jo1mpzWoGoFvutn6NhYlAYsY10jZwo3tOS0Al3spMrMEO7DNFpXwCAaDAh6YEVg8hi3CAdgIpvXtjJboIJUI8A==',
  '{"items":[{"k":"[E002Jo1mpzWoGoFvutn6NhYlAf6o2KUMxsJSjsVJlhjtnqyjxn8GVNPMdGQYUpl6ddJXPoY5HRkfL1nxq1Qh29wHQg==]"},{"k":"[E002Jo1mpzWoGoFvutn6NhYlAUtkVI6Maq8NE+cyhWI3cbsK2LUNUnZIGyQqOpKq23aoeAzFMZHFEcW1ItcfworqkA==]"}]}',
  'LOCAL-DECRYPT-TST-IM-0011',
  'E002Jo1mpzWoGoFvutn6NhYlAdWWKccK/ta9cjOdXV4QqktBn+WUnVMxiVujUsJH3OFpl75IwlwdJTrKeMUN3WLSFA==',
  '{"responseCode":"200"}',
  (EXTRACT(EPOCH FROM NOW() - INTERVAL '18 minutes') * 1000)::bigint
);

