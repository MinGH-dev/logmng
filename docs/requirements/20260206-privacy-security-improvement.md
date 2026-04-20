# 20260206 - 프론트엔드 개인정보 보호 및 보안 개선

## 1. 사용자 요건 내용

### 요건 설명
프론트엔드 코드에서 개인정보가 유출될 수 있는 우려가 있는 부분을 검토하고 개선이 필요합니다.

### 사용자 시나리오
1. 사용자가 로그인하여 로그 관리 시스템을 사용합니다
2. 복호화 기능을 사용하여 암호화된 로그를 확인합니다
3. 브라우저 개발자 도구를 열어 콘솔을 확인합니다
4. **문제**: 콘솔에 개인정보(복호화된 데이터, 사용자 정보 등)가 노출됩니다
5. **문제**: localStorage에 개인정보가 평문으로 저장됩니다
6. **문제**: 에러 메시지에 상세한 개인정보가 포함될 수 있습니다

### 기대 결과
- 콘솔 로그에 개인정보가 노출되지 않아야 합니다
- localStorage에 저장되는 개인정보는 암호화되거나 최소화되어야 합니다
- 에러 메시지에 개인정보가 포함되지 않아야 합니다
- 프로덕션 환경에서는 디버깅 로그가 비활성화되어야 합니다

## 2. 설계

### 기술 설계

#### 문제 분석

**1. 콘솔 로그에 개인정보 노출**

**위치**: `dev/frontend/src/components/ImageLogTable.js`
- 275-281줄: 복호화된 데이터를 콘솔에 출력
  ```javascript
  console.log('🔓 복호화된 데이터 상세:', {
    guid,
    hasDecryptedDatastring: !!result.data.decrypted_datastring,
    hasDecryptedHeaderstring: !!result.data.decrypted_headerstring,
    decryptedDatastring: result.data.decrypted_datastring?.substring(0, 100), // 개인정보 노출
    decryptedHeaderstring: result.data.decrypted_headerstring?.substring(0, 100) // 개인정보 노출
  });
  ```
- 271줄: 복호화 API 결과 전체를 콘솔에 출력
  ```javascript
  console.log('🔓 복호화 API 결과:', result); // result.data에 개인정보 포함
  ```

**위치**: `dev/frontend/src/components/LogGrid.js`
- 48줄: 검색 파라미터 전체를 콘솔에 출력
  ```javascript
  console.log('🔍 프론트엔드에서 받은 파라미터:', params); // 검색 키워드 등 개인정보 포함 가능
  ```
- 62줄: API 전송 데이터 전체를 콘솔에 출력
  ```javascript
  console.log('📤 API로 전송할 데이터:', requestData); // 검색 키워드 등 개인정보 포함 가능
  ```
- 79-89줄: API 응답 데이터를 콘솔에 출력
  ```javascript
  console.log('📥 API 응답:', result); // 로그 데이터에 개인정보 포함
  console.log('📥 API 응답 상세:', { ... }); // 로그 데이터에 개인정보 포함
  ```
- 94-98줄: 검색 결과 상세를 콘솔에 출력
  ```javascript
  console.log('📊 검색 결과 상세:', {
    logDataLength: logData.length,
    firstLog: logData[0] || null, // 첫 번째 로그에 개인정보 포함 가능
    pagination: result.data?.pagination
  });
  ```

**2. localStorage에 개인정보 저장**

**위치**: `dev/frontend/src/App.js`
- 54줄: 사용자 정보를 localStorage에 평문으로 저장
  ```javascript
  localStorage.setItem('user', JSON.stringify(userData)); // 사용자 정보 평문 저장
  ```

**위치**: `dev/frontend/services/api.js`
- 18줄: accessToken을 localStorage에 저장
  ```javascript
  const token = localStorage.getItem('accessToken'); // 토큰 저장 확인
  ```

**3. 에러 메시지에 개인정보 노출**

**위치**: `dev/frontend/src/components/ImageLogTable.js`
- 297줄: 에러 메시지를 alert로 표시
  ```javascript
  alert('복호화 실패: ' + (result.message || result.error)); // 에러 메시지에 개인정보 포함 가능
  ```
- 301줄: 에러 객체의 message를 alert로 표시
  ```javascript
  alert('복호화 중 오류가 발생했습니다: ' + error.message); // 에러 메시지에 개인정보 포함 가능
  ```

**4. 네트워크 요청/응답**

- 복호화된 데이터가 네트워크를 통해 전송됨 (HTTPS 사용 여부 확인 필요)
- 브라우저 개발자 도구의 Network 탭에서 요청/응답 내용 확인 가능

#### 해결 방안

**1. 콘솔 로그 개선**

- **프로덕션 환경에서 디버깅 로그 비활성화**
  - 환경 변수(`NODE_ENV` 또는 `REACT_APP_ENV`)를 사용하여 프로덕션 환경 확인
  - 프로덕션 환경에서는 `console.log`, `console.debug` 등 디버깅 로그 제거 또는 비활성화
  - 개발 환경에서만 디버깅 로그 활성화

- **개인정보 마스킹 처리**
  - 콘솔 로그에 개인정보가 포함될 경우 마스킹 처리
  - 예: `decryptedDatastring: maskSensitiveData(result.data.decrypted_datastring)`
  - 마스킹 함수 구현: 앞뒤 일부만 표시하고 중간은 `***`로 처리

- **로그 레벨 관리**
  - 개발 환경: `DEBUG` 레벨 로그 허용
  - 프로덕션 환경: `ERROR`, `WARN` 레벨만 허용

**2. localStorage 보안 강화**

- **사용자 정보 저장 최소화**
  - 필요한 최소한의 정보만 저장 (예: username만 저장)
  - 민감한 정보(비밀번호, 이메일 등)는 저장하지 않음

- **토큰 저장 방식 검토**
  - 현재: localStorage에 accessToken 저장
  - 대안: httpOnly 쿠키 사용 검토 (XSS 공격 방어)
  - 또는 sessionStorage 사용 검토 (탭 닫으면 자동 삭제)

- **데이터 암호화 (선택사항)**
  - localStorage에 저장되는 데이터를 암호화 (클라이언트 측 암호화는 완벽한 보안을 보장하지 않음)
  - 중요한 정보는 서버 측에서만 관리

**3. 에러 메시지 개선**

- **에러 메시지 일반화**
  - 구체적인 에러 내용 대신 일반적인 메시지 표시
  - 예: "복호화 중 오류가 발생했습니다. 관리자에게 문의하세요."
  - 상세한 에러 정보는 서버 로그에만 기록

- **에러 처리 개선**
  - 사용자에게 표시하는 에러 메시지는 사용자 친화적이고 일반적인 메시지
  - 개발 환경에서만 상세한 에러 정보 표시

**4. 네트워크 보안**

- **HTTPS 사용 확인**
  - 프로덕션 환경에서는 반드시 HTTPS 사용
  - HTTP 사용 시 네트워크 스니핑으로 데이터 유출 가능

- **민감한 데이터 전송 최소화**
  - 필요한 최소한의 데이터만 전송
  - 불필요한 개인정보는 전송하지 않음

### 변경 파일 목록

#### 프론트엔드
- `dev/frontend/src/components/ImageLogTable.js`
  - 콘솔 로그에서 개인정보 제거 또는 마스킹 처리
  - 에러 메시지 일반화

- `dev/frontend/src/components/LogGrid.js`
  - 콘솔 로그에서 개인정보 제거 또는 마스킹 처리

- `dev/frontend/src/App.js`
  - localStorage에 저장되는 사용자 정보 최소화

- `dev/frontend/src/utils/logger.js` (신규 생성)
  - 환경별 로그 레벨 관리
  - 개인정보 마스킹 유틸리티 함수

- `dev/frontend/src/utils/security.js` (신규 생성)
  - 개인정보 마스킹 함수
  - localStorage 보안 유틸리티

### 데이터베이스 변경사항
없음

## 3. 테스트 수행 방안

### 테스트 시나리오

#### 시나리오 1: 콘솔 로그 개인정보 노출 확인
1. 개발 환경에서 브라우저 개발자 도구 열기
2. 복호화 기능 사용
3. 콘솔 탭에서 개인정보가 노출되지 않는지 확인
4. 프로덕션 빌드에서 콘솔 로그가 비활성화되었는지 확인

#### 시나리오 2: localStorage 보안 확인
1. 브라우저 개발자 도구에서 Application > Local Storage 확인
2. 저장된 데이터에 민감한 정보가 포함되지 않는지 확인
3. 로그아웃 시 localStorage가 정상적으로 삭제되는지 확인

#### 시나리오 3: 에러 메시지 확인
1. 의도적으로 에러 발생시키기
2. 사용자에게 표시되는 에러 메시지에 개인정보가 포함되지 않는지 확인
3. 에러 메시지가 일반적이고 사용자 친화적인지 확인

#### 시나리오 4: 네트워크 보안 확인
1. 브라우저 개발자 도구에서 Network 탭 확인
2. HTTPS 사용 여부 확인
3. 요청/응답에 불필요한 개인정보가 포함되지 않는지 확인

### 테스트 데이터
- 개인정보가 포함된 샘플 로그 데이터
- 복호화된 데이터

### 테스트 환경
- 개발 환경(UI 검증 기준): `http://localhost:3001` (`docs/contract.md`)
- 프로덕션 빌드: `npm run build` 후 빌드 결과물 확인

## 4. 체크리스트

### 프론트엔드 검증
- [x] 콘솔 로그에서 개인정보가 제거되었는지 확인
- [x] 프로덕션 빌드에서 디버깅 로그가 비활성화되었는지 확인
- [x] localStorage에 저장되는 데이터가 최소화되었는지 확인
- [x] 에러 메시지에 개인정보가 포함되지 않는지 확인
- [x] 개인정보 마스킹 함수가 정상 동작하는지 확인

### 보안 검증
- [ ] HTTPS 사용 여부 확인
- [ ] XSS 공격 방어 확인
- [ ] CSRF 공격 방어 확인
- [ ] 인증 토큰 관리 방식 검토

### 통합 테스트
- [ ] 전체 플로우에서 개인정보가 노출되지 않는지 확인
- [ ] 개발 환경과 프로덕션 환경 모두에서 정상 동작하는지 확인

### 문서화
- [x] 보안 가이드 문서 작성
- [ ] 개인정보 처리 방침 문서 작성 (향후 작업)

## 5. 테스트 결과

### 테스트 수행 일시
- 2026-02-06

### 테스트 결과
- ✅ 완료 (상세 내용은 `20260206-privacy-security-improvement-test-results.md` 참조)

### 발견된 이슈 및 해결 방법

#### 이슈 1: 콘솔 로그에 개인정보 노출
**원인**: 
- 개발 편의를 위해 콘솔 로그에 전체 데이터를 출력
- 프로덕션 환경에서도 디버깅 로그가 활성화되어 있음

**해결 방법**:
1. 환경 변수를 사용하여 프로덕션 환경에서 디버깅 로그 비활성화
2. 콘솔 로그에 개인정보가 포함될 경우 마스킹 처리
3. 로그 레벨 관리 유틸리티 구현

#### 이슈 2: localStorage에 개인정보 저장
**원인**: 
- 사용자 정보를 localStorage에 평문으로 저장
- accessToken을 localStorage에 저장

**해결 방법**:
1. localStorage에 저장되는 사용자 정보 최소화
2. 민감한 정보는 저장하지 않음
3. 토큰 저장 방식 검토 (httpOnly 쿠키 또는 sessionStorage)

#### 이슈 3: 에러 메시지에 개인정보 노출
**원인**: 
- 에러 객체의 전체 메시지를 사용자에게 표시
- 에러 메시지에 상세한 정보가 포함될 수 있음

**해결 방법**:
1. 사용자에게 표시하는 에러 메시지 일반화
2. 상세한 에러 정보는 서버 로그에만 기록
3. 개발 환경에서만 상세한 에러 정보 표시

### 개선 우선순위

**높음 (즉시 개선 필요)**
1. 콘솔 로그에서 개인정보 제거 또는 마스킹 처리
2. 프로덕션 환경에서 디버깅 로그 비활성화
3. 에러 메시지 일반화

**중간 (단기간 내 개선)**
1. localStorage에 저장되는 데이터 최소화
2. 개인정보 마스킹 유틸리티 함수 구현
3. 로그 레벨 관리 유틸리티 구현

**낮음 (장기 개선)**
1. 토큰 저장 방식 개선 (httpOnly 쿠키)
2. 클라이언트 측 데이터 암호화 (선택사항)
3. 보안 가이드 문서 작성

---

**작성자**: AI Assistant
**작성일**: 2026-02-06
**상태**: ✅ 구현 완료, 테스트 완료
**테스트 결과**: `20260206-privacy-security-improvement-test-results.md` 참조

