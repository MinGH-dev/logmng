import { getErrorMessage, getLoginFailureMessage } from './errorMessage';

describe('getErrorMessage', () => {
  test('DEPARTMENT_NOT_FOUND 코드는 부서 안내 문구로 매핑한다', () => {
    expect(getErrorMessage({ code: 'DEPARTMENT_NOT_FOUND', status: 404 }, 'fallback')).toBe(
      '부서를 찾을 수 없습니다.'
    );
  });

  test('404이지만 서버 메시지가 있으면 code 없을 때 그 메시지를 쓴다', () => {
    expect(
      getErrorMessage({ status: 404, message: '부서를 찾을 수 없습니다.' }, 'fallback')
    ).toBe('부서를 찾을 수 없습니다.');
  });

  test('404이고 메시지가 HTTP 상태만이면 일반 문구', () => {
    expect(getErrorMessage({ status: 404, message: 'HTTP 404' }, 'fallback')).toBe('찾을 수 없습니다.');
  });
});

describe('getLoginFailureMessage', () => {
  const credentials =
    '❌ 인증 정보가 올바르지 않습니다.\n사용자명과 비밀번호를 다시 확인해주세요.';

  test('USER_ACCOUNT_DISABLED → 비활성/삭제 계정 안내', () => {
    expect(
      getLoginFailureMessage(401, {
        success: false,
        code: 'USER_ACCOUNT_DISABLED',
        error: 'ignored for this code',
      })
    ).toMatch(/비활성/);
  });

  test('INVALID_INPUT → 서버 error 우선', () => {
    expect(
      getLoginFailureMessage(400, {
        success: false,
        code: 'INVALID_INPUT',
        error: 'principal은 필수입니다.',
      })
    ).toBe('principal은 필수입니다.');
  });

  test('INVALID_INPUT → error 없으면 기본 입력 안내', () => {
    const msg = getLoginFailureMessage(400, { success: false, code: 'INVALID_INPUT' });
    expect(msg).toMatch(/입력/);
  });

  test('INVALID_CREDENTIALS → 자격 증명 문구', () => {
    expect(
      getLoginFailureMessage(401, { success: false, code: 'INVALID_CREDENTIALS' })
    ).toBe(credentials);
  });

  test('401 + code 없음 → 자격 증명 문구', () => {
    expect(getLoginFailureMessage(401, { success: false, error: 'Bad creds' })).toBe(credentials);
  });

  test('UNAUTHORIZED → 세션/재로그인 안내', () => {
    expect(getLoginFailureMessage(401, { success: false, code: 'UNAUTHORIZED' })).toMatch(/세션/);
  });

  test('DIRECTORY_AUTH_FAILED → 자격 증명과 동일 계열 문구', () => {
    expect(
      getLoginFailureMessage(401, { success: false, code: 'DIRECTORY_AUTH_FAILED' })
    ).toBe(credentials);
  });
});
