# 릴리스 노트 — 2026-03-20 (이미지로그 복합 행 키 및 배포·오프라인)

## 요약

Java FW 이미지로그(`java_fw_imglog`)는 동일 `guid`에 서로 다른 `status`가 공존할 수 있어, 제품 전 구간에서 **(guid, status)** 를 권위 있는 행 키로 사용합니다.

## 사용자·운영 관점 변경

- 복호화 승인·허용 목록·실행·검색 이력의 요청 행 표현이 **guid + status** 기준으로 맞춰져, 잘못된 행에 대한 복호화/표시 혼선을 줄입니다.
- 정적(오프라인) 프론트 배포 시 API 주소 등을 **런타임 설정 파일**로 바꿀 수 있습니다.
- CORS·인증 관련 설정이 일부 조정되었습니다(별도 환경에서 프리플라이트/헤더 이슈 완화).

## 요구사항 추적

- `docs/requirements/20260320-imagelog-guid-status-composite-key.md`

## 마이그레이션

- DB: `backend/src/main/resources/db/migrate-*.sql` 및 `airgap-only-*.sql` 참조. 배포 전 운영 DB에 해당 마이그레이션 적용 여부를 확인하세요.

## 상세

- 전체 변경 목록: 루트 `CHANGELOG.md` — **2026-03-20** 항목.
