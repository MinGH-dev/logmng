# 20260206 - Pretty 모드 하이라이팅 표시 문제 수정

## 1. 사용자 요건 내용

### 요건 설명
검색 결과에서 Pretty 모드로 전환하면 키워드 하이라이팅이 표시되지 않는 문제가 있습니다.

### 사용자 시나리오
1. 사용자가 로그를 검색합니다.
2. 검색 결과에서 키워드가 하이라이팅되어 표시됩니다.
3. 사용자가 Pretty 버튼을 클릭하여 Pretty 모드로 전환합니다.
4. **문제**: 하이라이팅이 사라지고 일반 텍스트로만 표시됩니다.

### 기대 결과
- Pretty 모드로 전환해도 키워드 하이라이팅이 유지되어야 합니다.
- 일반 모드와 Pretty 모드 모두에서 하이라이팅이 정상적으로 작동해야 합니다.

## 2. 설계

### 기술 설계

**현재 문제점:**
- `ImageLogTable.js`에서 `isPrettyMode`가 true일 때는 `formatJsonString`으로 포맷된 JSON을 `<pre>` 태그로 직접 표시합니다.
- `isPrettyMode`가 false일 때만 `highlightKeywords` 함수를 사용하여 하이라이팅을 적용합니다.
- Pretty 모드에서는 하이라이팅 로직이 적용되지 않습니다.

**해결 방안:**
1. Pretty 모드에서도 하이라이팅을 적용하도록 수정합니다.
2. `formatJsonString`으로 포맷한 후, 하이라이팅을 적용합니다.
3. 하이라이팅된 HTML을 `<pre>` 태그 내부에 `dangerouslySetInnerHTML`로 렌더링합니다.

**구현 방법:**
- `formatJsonString` 함수 호출 후 결과에 `highlightKeywords` 함수를 적용합니다.
- 하이라이팅된 결과를 `<pre>` 태그 내부에 `dangerouslySetInnerHTML`로 렌더링합니다.
- CSS에서 `<pre>` 태그 내부의 `<mark>` 태그 스타일을 정의합니다.

### 변경 파일 목록
- `dev/frontend/src/components/ImageLogTable.js` - 하이라이팅 로직 수정
  - `highlightKeywordsAsHtml` 함수 추가: HTML 문자열만 반환하는 헬퍼 함수
  - `highlightKeywords` 함수 수정: `highlightKeywordsAsHtml`을 사용하도록 변경
  - Pretty 모드 렌더링 수정: `highlightKeywordsAsHtml`을 사용하여 하이라이팅 적용
- `dev/frontend/src/components/ImageLogTable.css` - Pretty 모드 하이라이팅 스타일 추가
  - `.json-pretty-text mark` 스타일 추가
  - `.json-pretty-text mark.encrypted-highlight` 스타일 추가

### 데이터베이스 변경사항
없음

## 3. 테스트 수행 방안

### 테스트 시나리오
1. **일반 모드 하이라이팅 확인**
   - 로그를 검색합니다.
   - 검색 키워드가 하이라이팅되어 표시되는지 확인합니다.

2. **Pretty 모드 하이라이팅 확인**
   - 로그를 검색합니다.
   - Pretty 버튼을 클릭하여 Pretty 모드로 전환합니다.
   - 검색 키워드가 하이라이팅되어 표시되는지 확인합니다.

3. **모드 전환 시 하이라이팅 유지 확인**
   - 일반 모드에서 하이라이팅이 표시되는 상태에서 Pretty 모드로 전환합니다.
   - 하이라이팅이 유지되는지 확인합니다.
   - 다시 일반 모드로 전환하여 하이라이팅이 유지되는지 확인합니다.

### 테스트 데이터
- 검색 키워드가 포함된 로그 데이터
- JSON 형식의 datastring, headerstring 데이터

### 테스트 환경
- 개발 환경 (dev/frontend)
- 브라우저 콘솔에서 하이라이팅 적용 여부 확인

## 4. 체크리스트
- [x] 프론트엔드 검증 완료 (코드 수정 완료, 실제 테스트 필요)
- [x] 백엔드 검증 완료 (해당 없음)
- [ ] 통합 테스트 완료 (브라우저에서 실제 테스트 필요)
- [x] 문서화 완료

## 5. 테스트 결과

### 구현 완료 사항
- `highlightKeywordsAsHtml` 함수 추가: HTML 문자열만 반환하는 헬퍼 함수 생성
- Pretty 모드에서 하이라이팅 적용: `formatJsonString`으로 포맷한 후 `highlightKeywordsAsHtml` 적용
- CSS 스타일 추가: Pretty 모드 내부 하이라이팅 스타일 정의

### 테스트 필요 사항
- 브라우저에서 실제 검색 후 Pretty 모드 전환 시 하이라이팅 확인
- 일반 모드와 Pretty 모드 간 전환 시 하이라이팅 유지 확인
- 암호화된 값 하이라이팅이 Pretty 모드에서도 정상 작동하는지 확인

### 구현 세부사항
1. **하이라이팅 로직 분리**
   - `highlightKeywordsAsHtml`: HTML 문자열 반환 (Pretty 모드용)
   - `highlightKeywords`: React 컴포넌트 반환 (일반 모드용)
   
2. **Pretty 모드 수정**
   - `formatJsonString`으로 포맷한 결과에 `highlightKeywordsAsHtml` 적용
   - `<pre>` 태그에 `dangerouslySetInnerHTML`로 하이라이팅된 HTML 렌더링

3. **CSS 스타일 추가**
   - `.json-pretty-text mark`: 일반 키워드 하이라이팅 스타일
   - `.json-pretty-text mark.encrypted-highlight`: 암호화된 값 하이라이팅 스타일

