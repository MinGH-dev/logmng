# 프론트엔드 보안 가이드

## 개요

이 문서는 로그 관리 시스템 프론트엔드의 보안 가이드입니다. 개인정보 보호 및 보안 개선 사항을 문서화하고, 개발자들이 보안을 유지하면서 개발할 수 있도록 가이드라인을 제공합니다.

**작성일**: 2026-02-06  
**버전**: 1.0

## 목차

1. [보안 원칙](#보안-원칙)
2. [로그 관리](#로그-관리)
3. [localStorage 보안](#localstorage-보안)
4. [에러 처리](#에러-처리)
5. [개인정보 마스킹](#개인정보-마스킹)
6. [개발 가이드라인](#개발-가이드라인)
7. [체크리스트](#체크리스트)

---

## 보안 원칙

### 1. 최소 권한 원칙
- 필요한 최소한의 정보만 저장하고 전송
- 민감한 정보는 서버 측에서만 관리

### 2. 방어적 프로그래밍
- 모든 사용자 입력 검증
- 에러 메시지에 개인정보 포함 금지
- 프로덕션 환경에서 디버깅 정보 노출 금지

### 3. 환경별 보안 설정
- 개발 환경: 상세한 로그 허용 (개인정보 마스킹)
- 프로덕션 환경: 최소한의 로그만 허용 (ERROR, WARN만)

---

## 로그 관리

### logger 유틸리티 사용

프로젝트에서는 `logger` 유틸리티를 사용하여 로그를 출력합니다. 이 유틸리티는 환경에 따라 자동으로 로그 레벨을 관리하고 개인정보를 마스킹합니다.

#### 사용 방법

```javascript
import logger from '../utils/logger';

// DEBUG 레벨 (개발 환경에서만)
logger.debug('디버그 메시지', { data: someData });

// INFO 레벨
logger.info('정보 메시지', { data: someData });

// WARN 레벨
logger.warn('경고 메시지', { data: someData });

// ERROR 레벨 (모든 환경에서 출력)
logger.error('에러 메시지', { error: error.message });
```

#### 로그 레벨

- **개발 환경**: DEBUG, INFO, WARN, ERROR 모두 출력
- **프로덕션 환경**: WARN, ERROR만 출력

#### 개인정보 자동 마스킹

`logger`는 자동으로 개인정보를 감지하고 마스킹합니다:

```javascript
// 자동 마스킹되는 키워드
- decrypted_datastring
- decrypted_headerstring
- datastring
- headerstring
- password
- token
- accessToken
- refreshToken
- email
- phone
- accountNumber
- loginId
- username
- keywords
- searchParams
```

#### 금지 사항

❌ **절대 하지 말아야 할 것들:**

```javascript
// ❌ 직접 console.log 사용 (개인정보 노출 위험)
console.log('복호화된 데이터:', decryptedData);

// ❌ 프로덕션에서도 디버깅 로그 출력
if (process.env.NODE_ENV === 'production') {
  console.log('프로덕션 로그'); // ❌
}

// ❌ 개인정보를 포함한 로그
console.log('사용자 정보:', userData); // ❌
```

✅ **올바른 사용:**

```javascript
// ✅ logger 사용 (자동 마스킹)
logger.debug('복호화된 데이터:', { hasData: !!decryptedData });

// ✅ 마스킹된 정보만 로그
logger.info('검색 완료:', { count: results.length });
```

---

## localStorage 보안

### security 유틸리티 사용

`localStorage`에 데이터를 저장할 때는 `security` 유틸리티를 사용합니다.

#### 사용자 정보 저장

```javascript
import { saveMinimalUserData, getMinimalUserData, clearUserData } from '../utils/security';

// ✅ 최소한의 정보만 저장
saveMinimalUserData(userData); // username만 저장

// ✅ 사용자 정보 가져오기
const user = getMinimalUserData();

// ✅ 로그아웃 시 모든 데이터 삭제
clearUserData();
```

#### 저장되는 정보

**저장되는 정보:**
- `username` (최소한의 식별 정보)

**저장하지 않는 정보:**
- `password` (비밀번호)
- `email` (이메일)
- `token` (토큰은 별도 관리)
- 기타 민감한 개인정보

#### 토큰 관리

현재는 `localStorage`에 토큰을 저장하지만, 향후 `httpOnly` 쿠키로 전환을 검토 중입니다.

```javascript
import { getSecureStorage, setSecureStorage, removeSecureStorage } from '../utils/security';

// ✅ 안전한 토큰 저장
setSecureStorage('accessToken', token);

// ✅ 안전한 토큰 가져오기
const token = getSecureStorage('accessToken');

// ✅ 토큰 삭제
removeSecureStorage('accessToken');
```

#### 금지 사항

❌ **절대 하지 말아야 할 것들:**

```javascript
// ❌ 직접 localStorage 사용 (보안 유틸리티 우회)
localStorage.setItem('user', JSON.stringify(userData)); // ❌

// ❌ 민감한 정보 저장
localStorage.setItem('password', password); // ❌
localStorage.setItem('email', email); // ❌

// ❌ 전체 사용자 객체 저장
localStorage.setItem('user', JSON.stringify(fullUserData)); // ❌
```

✅ **올바른 사용:**

```javascript
// ✅ security 유틸리티 사용
saveMinimalUserData(userData); // ✅

// ✅ 최소한의 정보만 저장
setSecureStorage('selectedLogType', logType); // ✅
```

---

## 에러 처리

### 에러 메시지 일반화

사용자에게 표시하는 에러 메시지는 일반화되어야 하며, 개인정보를 포함하지 않아야 합니다.

#### getUserFriendlyErrorMessage 사용

```javascript
import { getUserFriendlyErrorMessage } from '../utils/security';

try {
  // 복호화 작업
} catch (error) {
  // ✅ 사용자 친화적 메시지
  alert(getUserFriendlyErrorMessage('복호화', error));
  // 프로덕션: "복호화 중 오류가 발생했습니다. 관리자에게 문의하세요."
  // 개발: "복호화 중 오류가 발생했습니다. 관리자에게 문의하세요. (Error: ...)"
}
```

#### 지원되는 컨텍스트

- `'복호화'`: 복호화 관련 에러
- `'검색'`: 검색 관련 에러
- `'인증'`: 인증 관련 에러
- `'기본'`: 기타 에러

#### 금지 사항

❌ **절대 하지 말아야 할 것들:**

```javascript
// ❌ 에러 객체 전체 표시
alert('에러: ' + error); // ❌

// ❌ 에러 메시지 직접 표시
alert('복호화 실패: ' + error.message); // ❌ (개인정보 포함 가능)

// ❌ 스택 트레이스 표시
alert('에러: ' + error.stack); // ❌
```

✅ **올바른 사용:**

```javascript
// ✅ 일반화된 메시지
alert(getUserFriendlyErrorMessage('복호화', error)); // ✅

// ✅ 개발 환경에서만 상세 정보
if (process.env.NODE_ENV === 'development') {
  logger.error('상세 에러:', { error: error.message }); // ✅
}
```

---

## 개인정보 마스킹

### 수동 마스킹

필요한 경우 수동으로 개인정보를 마스킹할 수 있습니다.

```javascript
import { maskSensitiveData, maskSensitiveObject } from '../utils/logger';

// 문자열 마스킹
const masked = maskSensitiveData('1234567890', 4, 4);
// 결과: "1234****7890"

// 객체 마스킹
const maskedObj = maskSensitiveObject({
  username: 'user123',
  password: 'secret',
  email: 'user@example.com'
});
// 결과: { username: 'user***', password: '******', email: 'user@******' }
```

### 자동 마스킹

`logger`를 사용하면 자동으로 마스킹됩니다:

```javascript
logger.debug('사용자 데이터:', {
  username: 'user123',
  password: 'secret',
  email: 'user@example.com'
});
// 자동으로 마스킹되어 출력됨
```

---

## 개발 가이드라인

### 1. 새 컴포넌트 작성 시

```javascript
import logger from '../utils/logger';
import { getUserFriendlyErrorMessage } from '../utils/security';

const MyComponent = () => {
  // ✅ logger 사용
  logger.debug('컴포넌트 마운트');
  
  const handleAction = async () => {
    try {
      // 작업 수행
    } catch (error) {
      // ✅ 일반화된 에러 메시지
      alert(getUserFriendlyErrorMessage('기본', error));
      logger.error('작업 실패:', { error: error.message });
    }
  };
};
```

### 2. API 호출 시

```javascript
import logger from '../utils/logger';

const fetchData = async () => {
  try {
    logger.debug('API 호출 시작:', { endpoint: '/api/data' });
    const response = await fetch('/api/data');
    const data = await response.json();
    
    // ✅ 마스킹된 데이터만 로그
    logger.debug('API 응답:', { 
      success: data.success,
      count: data.items?.length 
    });
    
    return data;
  } catch (error) {
    logger.error('API 호출 실패:', { error: error.message });
    throw error;
  }
};
```

### 3. 사용자 정보 처리 시

```javascript
import { saveMinimalUserData, getMinimalUserData } from '../utils/security';

// ✅ 최소한의 정보만 저장
const handleLogin = (userData) => {
  saveMinimalUserData(userData);
  // username만 저장됨
};

// ✅ 안전하게 가져오기
const user = getMinimalUserData();
```

---

## 체크리스트

### 코드 리뷰 체크리스트

개발자가 코드를 작성하거나 리뷰할 때 다음 사항을 확인하세요:

#### 로그 관리
- [ ] `console.log`, `console.error` 대신 `logger` 사용
- [ ] 프로덕션 환경에서 DEBUG/INFO 로그가 비활성화되는지 확인
- [ ] 개인정보가 포함된 로그는 마스킹되었는지 확인
- [ ] 에러 로그에 개인정보가 포함되지 않았는지 확인

#### localStorage 관리
- [ ] `localStorage` 직접 사용 대신 `security` 유틸리티 사용
- [ ] 저장되는 사용자 정보가 최소화되었는지 확인
- [ ] 민감한 정보(비밀번호, 이메일 등)가 저장되지 않았는지 확인
- [ ] 로그아웃 시 모든 사용자 데이터가 삭제되는지 확인

#### 에러 처리
- [ ] 사용자에게 표시하는 에러 메시지가 일반화되었는지 확인
- [ ] 에러 메시지에 개인정보가 포함되지 않았는지 확인
- [ ] `getUserFriendlyErrorMessage`를 사용했는지 확인
- [ ] 개발 환경에서만 상세한 에러 정보가 표시되는지 확인

#### 개인정보 보호
- [ ] 복호화된 데이터가 로그에 노출되지 않았는지 확인
- [ ] 검색 키워드가 로그에 노출되지 않았는지 확인
- [ ] API 요청/응답 데이터에 불필요한 개인정보가 포함되지 않았는지 확인

### 배포 전 체크리스트

프로덕션 배포 전에 다음을 확인하세요:

- [ ] 프로덕션 빌드에서 DEBUG/INFO 로그가 비활성화되었는지 확인
- [ ] 브라우저 콘솔에서 개인정보가 노출되지 않는지 확인
- [ ] localStorage에 민감한 정보가 저장되지 않았는지 확인
- [ ] 에러 메시지에 개인정보가 포함되지 않았는지 확인
- [ ] HTTPS 사용 여부 확인
- [ ] 네트워크 요청/응답에 불필요한 개인정보가 포함되지 않았는지 확인

---

## 테스트 방법

### 1. 콘솔 로그 확인

1. 브라우저 개발자 도구 열기 (F12)
2. Console 탭 확인
3. 복호화 기능 사용
4. 개인정보가 마스킹되어 출력되는지 확인

### 2. localStorage 확인

1. 브라우저 개발자 도구 열기 (F12)
2. Application > Local Storage 확인
3. 저장된 데이터에 민감한 정보가 포함되지 않았는지 확인

### 3. 에러 메시지 확인

1. 의도적으로 에러 발생시키기
2. 사용자에게 표시되는 에러 메시지 확인
3. 개인정보가 포함되지 않았는지 확인

### 4. 프로덕션 빌드 확인

```bash
npm run build
```

빌드 후 다음을 확인:
- 콘솔에 DEBUG/INFO 로그가 출력되지 않는지
- 개인정보가 노출되지 않는지

---

## 향후 개선 사항

### 단기 (1-3개월)
- [ ] 토큰 저장 방식 개선 (httpOnly 쿠키 검토)
- [ ] 추가 보안 테스트 케이스 작성
- [ ] 보안 감사 자동화

### 장기 (3-6개월)
- [ ] 클라이언트 측 데이터 암호화 (선택사항)
- [ ] CSP (Content Security Policy) 적용
- [ ] 보안 모니터링 시스템 구축

---

## 참고 자료

- [OWASP Top 10](https://owasp.org/www-project-top-ten/)
- [React 보안 가이드](https://reactjs.org/docs/security.html)
- [웹 보안 가이드](https://developer.mozilla.org/ko/docs/Web/Security)

---

## 문의

보안 관련 문의사항이나 발견한 보안 취약점이 있다면, 즉시 개발팀에 보고해주세요.

**작성자**: 개발팀  
**최종 수정일**: 2026-02-06





