# SVG 와이어프레임 (logmng)

## Chromium 기준이란?

Chrome 브라우저는 SVG를 **글자(XML)로 먼저 읽습니다.**  
그래서 **UTF-8로 깨끗하게 저장**되어 있고, **XML 문법이 맞아야** 브라우저·미리보기에서 안 깨집니다.

## 확인 방법

터미널에서 프로젝트 루트로 이동한 뒤:

```bash
./scripts/validate-svg.sh
```

전부 `OK`면 Chromium에서 열어도 같은 종류의 오류는 나지 않도록 맞춘 상태입니다.

## 폴더

- `primitives/` — 자주 쓰는 조각 (`common-pagination-bar`, `common-list-shell` 등)
- `scenes/` — 화면별 와이어프레임
- 목록형 화면 **활동 이력 / 검색 이력 / 복호화 승인 관리** (`logmng-step-05`, `06`, `08`)은 하단 **공통 페이징 바** 레이아웃과 맞춰 두었습니다.
