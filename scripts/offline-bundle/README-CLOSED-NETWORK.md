# 폐쇄망(Closed-network) 오프라인 번들

이 디렉터리 루트에 **`CLOSED-NETWORK-BUNDLE`** 파일이 있으면, 폐쇄망용 레이아웃입니다. 일반 오프라인 번들과 동일하게 `install-offline.sh`로 설치하되, 데이터베이스 초기화 단계에서 아래 환경 변수를 반드시 맞춥니다.

## 앱 서버에 psql이 없을 때

DB는 원격이어도 DDL을 이 서버에서 돌리면 **`psql` 클라이언트**가 필요합니다. 번들 제작 시 `scripts/download-psql-for-bundle.sh` 로 `tools/psql-deb/`를 채운 뒤 tarball을 만들고, 설치 서버에서 `./install-offline.sh install-psql` 또는 `db` 단계에서 자동 설치(Debian/Ubuntu)를 시도합니다. 자세한 내용은 `README-OFFLINE.md` 를 참고하세요.

## 스키마 적용 순서

`db/setup.sh`는 저장소(빌드 시점)와 **동일한 순서**로 DDL·마이그레이션·초기 데이터를 적용합니다. 번들에 포함된 `db/`가 소스와 동일한 트리이므로, 폐쇄망에서도 리포지토리 기준과 같은 적용 순서를 유지합니다.

## 폐쇄망 DB 단계에서 필수 환경 변수

개발용 대용량 시드(PB FEP pagination/bmsg 샘플, imagelog 샘플 마이그레이션 등)를 건너뛰고, **관리자만** 넣으려면 다음을 설정합니다.

- **`INIT_DATA_FILE=init-data-closed-network-admin-only.sql`**
- **`CLOSED_NETWORK_MINIMAL=1`**

번들 루트에 `CLOSED-NETWORK-BUNDLE` 마커가 있으면, `install-offline.sh`의 DB 단계에서 위 변수를 **아직 설정하지 않은 경우에만** 자동으로 동일 값을 내보냅니다. 이미 `export`한 값이 있으면 덮어쓰지 않습니다.

수동으로 동일하게 쓰는 예:

```bash
export INIT_DATA_FILE=init-data-closed-network-admin-only.sql
export CLOSED_NETWORK_MINIMAL=1
./install-offline.sh db
# 또는
./install-offline.sh all
```

## 초기 관리자 계정

단일 관리자 계정(기존 `init-data`와 동일한 기본값):

| 항목 | 값 |
|------|-----|
| 사용자명 | `admin` |
| 사용자 ID | `20269999` |
| 초기 비밀번호 | `admin123` |

운영 환경에서는 설치 직후 비밀번호 변경 및 정책 적용을 권장합니다.

## 참고

- 일반 오프라인 설명: `README-OFFLINE.md`
- DB 상세: `docs/DB_SETUP_GUIDE.md`(번들 내 `docs/`에 복사됨)
- **PB·ImageLog JDBC**: `var/logmng.env`에 전용 URL을 넣으면(해당 기능이 포함된 백엔드 기준) **풀 분리**, 비우면 Primary 단일 풀·스키마 분리 — 요약은 `README-OFFLINE.md` 의 「PB·ImageLog 전용 JDBC」; 변수 표는 `docs/contract.md`.
