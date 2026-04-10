import React, { useState } from 'react';
import DataTable, { EmptyTableBody } from './DataTable';
import './ImageLogTable.css';
import logger from '../utils/logger';
import { getApiBaseUrl } from '../config/runtimeApi';
import { getUserFriendlyErrorMessage, DECRYPTION_NOT_APPROVED_MESSAGE } from '../utils/security';

/** CI eslint (react-scripts)는 컴포넌트 내부 async 콜백의 import 사용을 간혹 미검출함 — 래퍼로 참조 고정 */
function buildJavaFwDecryptApiUrl(screenId) {
  const apiBaseUrl = getApiBaseUrl();
  return screenId
    ? `${apiBaseUrl}/logs/decrypt/java_fw_imglog?screen=${encodeURIComponent(screenId)}`
    : `${apiBaseUrl}/logs/decrypt/java_fw_imglog`;
}

/**
 * Parse fetch response body once; returns `{ parsed, rawText }`.
 * `parsed` is null if body is empty or not JSON.
 */
function parseResponseBodyJson(rawText) {
  const raw = rawText == null ? '' : String(rawText);
  if (!raw.trim()) {
    return { parsed: {}, rawText: raw };
  }
  try {
    const parsed = JSON.parse(raw);
    return {
      parsed: parsed && typeof parsed === 'object' ? parsed : {},
      rawText: raw,
    };
  } catch {
    return { parsed: null, rawText: raw };
  }
}

/**
 * ApiResponse-style message for alerts (message, error string, or nested error.message).
 * @param {object} payload - normalized object from JSON body
 * @returns {string|null}
 */
function getApiErrorMessageForAlert(payload) {
  if (!payload || typeof payload !== 'object') return null;
  if (typeof payload.message === 'string' && payload.message.trim() !== '') {
    return payload.message.trim();
  }
  if (typeof payload.error === 'string' && payload.error.trim() !== '') {
    return payload.error.trim();
  }
  if (
    payload.error &&
    typeof payload.error === 'object' &&
    typeof payload.error.message === 'string' &&
    payload.error.message.trim() !== ''
  ) {
    return payload.error.message.trim();
  }
  return null;
}

const IMAGE_LOG_COLUMNS = [
  { key: 'insert_time', label: 'insert_time', sortable: true },
  { key: 'application', label: 'application', sortable: true },
  { key: 'servicegroup', label: 'servicegroup', sortable: true },
  { key: 'service', label: 'service', sortable: true },
  { key: 'status', label: 'status', sortable: true },
  { key: 'guid', label: 'guid', sortable: false },
  { key: 'datastring', label: 'datastring', sortable: false },
  { key: 'headerstring', label: 'headerstring', sortable: false },
  { key: 'pretty', label: 'Pretty', sortable: false },
  { key: 'decrypt', label: '복호화', sortable: false },
];

const ImageLogTable = ({
  logs,
  loading,
  sortConfig,
  onSort,
  currentPage,
  totalPages,
  totalCount = 0,
  onPageChange,
  pageSize = 20,
  onPageSizeChange,
  keywords = [],
  searchParams = {},
  searchHistoryId = null,
  hasDecryptPermission = true,
  decryptionAllowed = null,
  screenId = null,
}) => {
  // decryptionAllowed: GET /api/decrypt/allowed — req 20260320: allowedRows [{ guid, status }]; guids만 있으면 레거시(guid-only)
  const allowedGuids = decryptionAllowed && Array.isArray(decryptionAllowed.guids) ? decryptionAllowed.guids : [];
  const allowedRows = decryptionAllowed && Array.isArray(decryptionAllowed.allowedRows) ? decryptionAllowed.allowedRows : [];
  const validUntil = decryptionAllowed?.validUntil ?? null;

  const normalizeAllowedStatus = (s) => (s == null || s === '' ? '' : String(s).trim());

  const isAllowedForRow = (guid, status) => {
    if (!guid) return false;
    const until = validUntil ? new Date(validUntil) : null;
    if (until && until.getTime() <= Date.now()) return false;
    const rowSt = normalizeAllowedStatus(status);
    const g = String(guid).trim();
    if (allowedRows.length > 0) {
      return allowedRows.some((r) => {
        if (!r || r.guid == null) return false;
        const rg = String(r.guid).trim();
        const rs = normalizeAllowedStatus(r.status ?? r.row_status);
        return rg === g && rs === rowSt;
      });
    }
    if (!allowedGuids.length) return false;
    return allowedGuids.includes(guid);
  };
  // 시간 포맷팅
  const formatTime = (timeString) => {
    if (!timeString) return '';
    
    // datetime-local 형식 처리 (YYYY-MM-DDTHH:mm:ss)
    if (typeof timeString === 'string' && timeString.includes('T')) {
      try {
        const date = new Date(timeString);
        const year = date.getFullYear();
        const month = String(date.getMonth() + 1).padStart(2, '0');
        const day = String(date.getDate()).padStart(2, '0');
        const hours = String(date.getHours()).padStart(2, '0');
        const minutes = String(date.getMinutes()).padStart(2, '0');
        const seconds = String(date.getSeconds()).padStart(2, '0');
        return `${year}-${month}-${day} ${hours}:${minutes}:${seconds}`;
      } catch (error) {
        return timeString;
      }
    }
    
    // yyyy-MM-dd HH:mm:ss 형식
    if (typeof timeString === 'string' && timeString.match(/^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}$/)) {
      return timeString;
    }
    
    return timeString;
  };

  // JSON 문자열을 pretty print로 변환
  const formatJsonString = (text, pretty = false) => {
    if (!text) return '';
    
    try {
      const parsed = JSON.parse(text);
      return pretty ? JSON.stringify(parsed, null, 2) : JSON.stringify(parsed);
    } catch (e) {
      return text; // JSON이 아니면 원본 반환
    }
  };
  
  // 키워드 하이라이트 HTML 문자열 생성 (암호화된 값 전체 하이라이트 포함)
  // 반환값: HTML 문자열 (React 컴포넌트가 아닌 순수 HTML 문자열)
  const highlightKeywordsAsHtml = (text, keywords, originalText = null, hasEncryptedMatch = false, fieldKeyword = null) => {
    if (!text && !originalText) {
      return text || '';
    }
    
    // 키워드 배열 구성 (keywords + fieldKeyword)
    const allKeywords = [];
    if (keywords && Array.isArray(keywords) && keywords.length > 0) {
      allKeywords.push(...keywords);
    }
    if (fieldKeyword && typeof fieldKeyword === 'string' && fieldKeyword.trim() !== '') {
      allKeywords.push(fieldKeyword.trim());
    }
    
    if (allKeywords.length === 0) {
      return text || originalText || '';
    }
    
    // 하이라이트는 항상 원본 텍스트에서 수행 (암호화된 값 패턴을 찾기 위해)
    const sourceText = originalText || text;
    if (!sourceText) {
      return text || '';
    }
    
    let highlightedText = String(sourceText); // 원본 텍스트로 시작
    
    // 암호화된 값 패턴: 쌍따옴표 안의 문자열 값 "[xxx]" 만 (배열 [1,2,3] 등 쌍따옴표 밖의 [...] 제외). 중첩 "[[]]" 은 안쪽 [x] 만 매칭됨.
    const quotedBracketPattern = /"(\[[^\]]*\])"/g;
    const encryptedMatches = [];
    const tempSource = String(sourceText);
    let match;
    quotedBracketPattern.lastIndex = 0;
    while ((match = quotedBracketPattern.exec(tempSource)) !== null) {
      const fullMatch = match[1]; // [....] 부분만 (쌍따옴표 제외)
      encryptedMatches.push({
        fullMatch,
        encryptedContent: fullMatch.slice(1, -1),
        index: match.index + 1,
        length: fullMatch.length
      });
    }
    
    // 각 키워드에 대해 처리
    allKeywords.forEach(keyword => {
      if (!keyword || typeof keyword !== 'string' || keyword.trim() === '') return;
      const trimmedKeyword = keyword.trim();
      const escapedKeyword = trimmedKeyword.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
      
      // 1. 암호화된 값 전체 하이라이트
      // 백엔드에서 메타데이터로 암호화된 값에서 매칭되었는지 알려주거나,
      // 검색 결과에 포함된 로우라면 암호화된 값에서 매칭되었을 가능성이 높으므로 하이라이트
      // 키워드가 있고 암호화된 값이 있으면 하이라이트 (hasEncryptedMatch가 false여도)
      if (encryptedMatches.length > 0 && (hasEncryptedMatch || fieldKeyword)) {
        encryptedMatches.forEach(encryptedMatch => {
          const encryptedValue = encryptedMatch.fullMatch;
          // 이미 하이라이트되지 않은 경우에만 처리
          if (!highlightedText.includes(`<mark class="encrypted-highlight">${encryptedValue}</mark>`)) {
            const escapedEncrypted = encryptedValue.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
            const regex = new RegExp(escapedEncrypted, 'g');
            highlightedText = highlightedText.replace(regex, (match) => {
              // 이미 하이라이트된 부분인지 확인
              if (match.includes('<mark')) {
                return match;
              }
              return `<mark class="encrypted-highlight">${match}</mark>`;
            });
          }
        });
      }
      
      // 2. 일반 텍스트에서 키워드 하이라이트 (암호화된 값이 아닌 부분)
      // 먼저 암호화된 값 부분을 임시로 마스킹
      const placeholders = [];
      let maskedText = highlightedText;
      
      encryptedMatches.forEach((encryptedMatch, idx) => {
        const placeholder = `__ENCRYPTED_${idx}__`;
        placeholders.push({
          placeholder,
          value: encryptedMatch.fullMatch
        });
        maskedText = maskedText.replace(encryptedMatch.fullMatch, placeholder);
      });
      
      // 마스킹된 텍스트에서 키워드 하이라이트
      const keywordRegex = new RegExp(`(${escapedKeyword})`, 'gi');
      maskedText = maskedText.replace(keywordRegex, (match, p1) => {
        return `<mark>${p1}</mark>`;
      });
      
      // 플레이스홀더를 원래 암호화된 값으로 복원
      placeholders.forEach(({ placeholder, value }) => {
        maskedText = maskedText.replace(placeholder, value);
      });
      
      highlightedText = maskedText;
    });
    
    return highlightedText;
  };
  
  // 키워드 하이라이트 (React 컴포넌트 반환)
  const highlightKeywords = (text, keywords, originalText = null, hasEncryptedMatch = false, fieldKeyword = null) => {
    const highlightedHtml = highlightKeywordsAsHtml(text, keywords, originalText, hasEncryptedMatch, fieldKeyword);
    return <span dangerouslySetInnerHTML={{ __html: highlightedHtml }} />;
  };
  
  // 상세 보기 상태
  const [selectedLog, setSelectedLog] = useState(null);

  // Pretty 출력 상태 — decrypt와 동일하게 guid+status 복합 키 (getLogKey)
  const [prettyLogs, setPrettyLogs] = useState(new Set());
  
  // 복호화 상태 (각 로그별로 관리) - guid+status를 key로 사용
  const [decryptedLogs, setDecryptedLogs] = useState(new Map());
  const [decryptingLogs, setDecryptingLogs] = useState(new Set());
  
  // guid와 status를 조합한 고유 키 생성
  const getLogKey = (guid, status) => {
    return `${guid}::${status || ''}`;
  };
  
  // 상세 보기 닫기
  const handleCloseDetail = () => {
    setSelectedLog(null);
  };
  
  // Pretty 출력 토글 (guid + status — decrypt와 동일 키)
  const togglePretty = (guid, status) => {
    const key = getLogKey(guid, status);
    setPrettyLogs((prev) => {
      const newSet = new Set(prev);
      if (newSet.has(key)) {
        newSet.delete(key);
      } else {
        newSet.add(key);
      }
      return newSet;
    });
  };

  const isPretty = (guid, status) => {
    return prettyLogs.has(getLogKey(guid, status));
  };
  
  // 복호화 처리
  const handleDecrypt = async (guid, status, e) => {
    if (e) {
      e.preventDefault();
      e.stopPropagation();
    }
    
    const logKey = getLogKey(guid, status);
    logger.debug('🔓 handleDecrypt 호출:', { guid, status, logKey });
    
    if (decryptingLogs.has(logKey)) {
      logger.debug('🔓 이미 복호화 중:', { guid, status, logKey });
      return; // 이미 복호화 중이면 무시
    }
    
    logger.debug('🔓 복호화 시작:', { guid, status, logKey });
    setDecryptingLogs(prev => {
      const newSet = new Set(prev);
      newSet.add(logKey);
      logger.debug('🔓 decryptingLogs 업데이트:', { guid, status, logKey, newSetSize: newSet.size });
      return newSet;
    });
    
    try {
      const decryptUrl = buildJavaFwDecryptApiUrl(screenId);
      logger.debug('🔓 복호화 API 호출:', { apiUrl: decryptUrl, guid, status, screenId });
      const body = { guid, status };
      if (searchHistoryId != null) body.searchHistoryId = searchHistoryId;
      const response = await fetch(decryptUrl, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        credentials: 'include', // 세션 쿠키 전달
        body: JSON.stringify(body)
      });
      
      const rawText = await response.text();
      const { parsed, rawText: bodyText } = parseResponseBodyJson(rawText);
      const payload = parsed !== null ? parsed : {};

      logger.debug('🔓 복호화 API 응답 상태:', { status: response.status, hasJson: parsed !== null });
      
      if (!response.ok) {
        if (response.status === 403) {
          logger.debug('🔓 복호화 API 403 응답 body:', {
            code: payload?.code,
            detailCode: payload?.detailCode,
            error: payload?.error,
          });
          if (payload.code === 'DECRYPTION_NOT_APPROVED') {
            logger.debug('🔓 복호화 승인 미완료:', { code: payload.code });
            alert(getUserFriendlyErrorMessage('복호화', payload));
            return;
          }
          if (payload.code === 'ROW_NOT_IN_APPROVED_SNAPSHOT') {
            logger.debug('🔓 승인된 검색 결과에 없는 항목 복호화 시도:', { code: payload.code });
            alert(
              getApiErrorMessageForAlert(payload) ||
                '승인된 검색 결과에 포함된 항목만 복호화할 수 있습니다.'
            );
            return;
          }
          const msg403 = getApiErrorMessageForAlert(payload);
          alert(msg403 || getUserFriendlyErrorMessage('복호화', payload));
          return;
        }

        const apiMsg = getApiErrorMessageForAlert(payload);
        logger.error('🔓 복호화 API 오류:', {
          status: response.status,
          code: payload.code,
          body: bodyText?.slice?.(0, 500),
        });
        if (apiMsg) {
          alert(apiMsg);
        } else {
          alert(getUserFriendlyErrorMessage('복호화', new Error(`HTTP error! status: ${response.status}`)));
        }
        return;
      }

      if (parsed === null) {
        logger.error('🔓 복호화 API: 성공 상태이나 본문이 JSON이 아님:', { preview: bodyText?.slice?.(0, 200) });
        alert(getUserFriendlyErrorMessage('복호화', new Error('Invalid response')));
        return;
      }

      const result = parsed;
      logger.debug('🔓 복호화 API 결과:', { 
        success: result.success,
        hasData: !!result.data,
        hasDecryptedDatastring: !!result.data?.decrypted_datastring,
        hasDecryptedHeaderstring: !!result.data?.decrypted_headerstring
      });
      
      if (result.success) {
        logger.debug('🔓 복호화 성공:', { 
          guid,
          hasDecryptedDatastring: !!result.data.decrypted_datastring,
          hasDecryptedHeaderstring: !!result.data.decrypted_headerstring
        });
        setDecryptedLogs(prev => {
          const newMap = new Map(prev);
          newMap.set(logKey, result.data);
          logger.debug('🔓 decryptedLogs 업데이트:', { 
            guid, 
            status,
            logKey,
            newMapSize: newMap.size, 
            hasData: newMap.has(logKey),
            hasDecryptedDatastring: !!newMap.get(logKey)?.decrypted_datastring,
            hasDecryptedHeaderstring: !!newMap.get(logKey)?.decrypted_headerstring
          });
          return newMap;
        });
      } else {
        logger.error('🔓 복호화 실패:', { error: result.error });
        alert(getUserFriendlyErrorMessage('복호화', result.error || result.message));
      }
    } catch (error) {
      logger.error('🔓 복호화 중 오류 발생:', { error: error.message });
      alert(getUserFriendlyErrorMessage('복호화', error));
    } finally {
      setDecryptingLogs(prev => {
        const newSet = new Set(prev);
        newSet.delete(logKey);
        return newSet;
      });
    }
  };
  
  // 복호화 해제 처리
  const handleDecryptCancel = (guid, status) => {
    const logKey = getLogKey(guid, status);
    setDecryptedLogs(prev => {
      const newMap = new Map(prev);
      newMap.delete(logKey);
      return newMap;
    });
  };
  
  // 복호화된 데이터 가져오기
  const getDecryptedData = (guid, status) => {
    const logKey = getLogKey(guid, status);
    const data = decryptedLogs.get(logKey);
    if (data) {
      logger.debug('🔓 getDecryptedData 호출:', { guid, hasData: !!data, keys: Object.keys(data) });
    }
    return data;
  };
  
  // 복호화 여부 확인
  const isDecrypted = (guid, status) => {
    const logKey = getLogKey(guid, status);
    const result = decryptedLogs.has(logKey);
    if (result) {
      logger.debug('🔓 isDecrypted 호출:', { guid, result, decryptedLogsSize: decryptedLogs.size });
    }
    return result;
  };
  
  // 복호화 중 여부 확인
  const isDecrypting = (guid, status) => {
    const logKey = getLogKey(guid, status);
    return decryptingLogs.has(logKey);
  };

  const effectiveSortConfig = sortConfig && sortConfig.key ? sortConfig : null;
  const pagination = {
    currentPage,
    totalPages,
    onPageChange,
    infoText: `총 ${totalCount.toLocaleString()}건`,
  };

  const tableBody = logs.length === 0 ? (
    <EmptyTableBody colSpan={10} message="검색 결과가 없습니다." />
  ) : (
    logs.map((log, index) => {
                const logGuid = log.guid || `log-${index}`;
                const logStatus = log.status || '';
                const isPrettyMode = isPretty(logGuid, logStatus);
                const decryptedData = getDecryptedData(logGuid, logStatus);
                const isDecryptedRow = isDecrypted(logGuid, logStatus);
                const isDecryptingRow = isDecrypting(logGuid, logStatus);
                
                // 디버깅 로그
                if (isDecryptedRow || decryptedData) {
                  logger.debug('🔓 복호화 상태 확인:', {
                    logGuid,
                    isDecryptedRow,
                    hasDecryptedData: !!decryptedData,
                    decryptedDataKeys: decryptedData ? Object.keys(decryptedData) : []
                  });
                }
                
                // 원본 데이터 사용 (암호화된 값은 [...] 형태로 표시)
                // 복호화 버튼을 클릭한 경우에만 복호화된 값 표시
                let datastringValue = log.datastring || '';
                let headerstringValue = log.headerstring || '';
                let originalDatastring = log.datastring || '';
                let originalHeaderstring = log.headerstring || '';
                
                // 복호화된 데이터가 있으면 복호화된 값 사용
                if (isDecryptedRow && decryptedData) {
                  logger.debug('🔓 복호화된 데이터 적용:', {
                    logGuid,
                    hasOriginalDatastring: !!log.datastring,
                    hasDecryptedDatastring: !!decryptedData.decrypted_datastring
                  });
                  if (decryptedData.decrypted_datastring) {
                    datastringValue = decryptedData.decrypted_datastring;
                    // 복호화된 값을 원본으로 사용 (하이라이트를 위해)
                    originalDatastring = decryptedData.decrypted_datastring;
                  }
                  if (decryptedData.decrypted_headerstring) {
                    headerstringValue = decryptedData.decrypted_headerstring;
                    // 복호화된 값을 원본으로 사용 (하이라이트를 위해)
                    originalHeaderstring = decryptedData.decrypted_headerstring;
                  }
                }
                
                return (
                  <tr key={`imagelog-${index}-${logGuid}`}>
                    <td>{formatTime(log.insert_time || log.timestamp)}</td>
                    <td>{log.application || ''}</td>
                    <td>{log.servicegroup || ''}</td>
                    <td>{log.service || ''}</td>
                    <td>{log.status || ''}</td>
                    <td>{logGuid}</td>
                    <td className={`tr-data-cell ${isPrettyMode ? 'pretty-mode' : ''}`}>
                      {isPrettyMode ? (
                        <pre 
                          className="json-pretty-text"
                          dangerouslySetInnerHTML={{
                            __html: highlightKeywordsAsHtml(
                              formatJsonString(datastringValue, true),
                              keywords,
                              formatJsonString(originalDatastring, true),
                              log._datastring_has_encrypted_match === true,
                              searchParams?.datastring // datastring 필드 검색 키워드 추가
                            )
                          }}
                        />
                      ) : (
                        <span className="tr-data-text">
                          {highlightKeywords(
                            datastringValue, 
                            keywords, 
                            originalDatastring,
                            log._datastring_has_encrypted_match === true,
                            searchParams?.datastring // datastring 필드 검색 키워드 추가
                          )}
                        </span>
                      )}
                    </td>
                    <td className={`tr-data-cell ${isPrettyMode ? 'pretty-mode' : ''}`}>
                      {isPrettyMode ? (
                        <pre 
                          className="json-pretty-text"
                          dangerouslySetInnerHTML={{
                            __html: highlightKeywordsAsHtml(
                              formatJsonString(headerstringValue, true),
                              keywords,
                              formatJsonString(originalHeaderstring, true),
                              log._headerstring_has_encrypted_match === true,
                              searchParams?.headerstring // headerstring 필드 검색 키워드 추가
                            )
                          }}
                        />
                      ) : (
                        <span className="tr-data-text">
                          {highlightKeywords(
                            headerstringValue, 
                            keywords, 
                            originalHeaderstring,
                            log._headerstring_has_encrypted_match === true,
                            searchParams?.headerstring // headerstring 필드 검색 키워드 추가
                          )}
                        </span>
                      )}
                    </td>
                    <td className="pretty-action-cell">
                      <button
                        type="button"
                        className={`pretty-btn ${isPrettyMode ? 'active' : ''}`}
                        onClick={() => togglePretty(logGuid, logStatus)}
                        title={isPrettyMode ? 'Pretty 출력 끄기' : 'Pretty 출력 켜기'}
                        aria-label={isPrettyMode ? 'Pretty 출력 끄기' : 'Pretty 출력 켜기'}
                      >
                        {isPrettyMode ? 'Pretty OFF' : 'Pretty'}
                      </button>
                    </td>
                    <td className="decrypt-action-cell">
                      {(() => {
                        // Encrypted for decrypt-button visibility: datastring or headerstring must contain
                        // at least one quoted bracket-wrapped value (e.g. "[ciphertext]" as a JSON string value).
                        // Do not use log.data/log.header presence; plain rows may have non-empty data/header.
                        // Plain JSON arrays like [1,2,3] are not matched (no surrounding quotes).
                        const quotedBracketPattern = /"\[[^\]]*\]"/;
                        const hasEncryptedData = [log.datastring, log.headerstring].some(
                          (s) => typeof s === 'string' && quotedBracketPattern.test(s)
                        );

                        if (!hasEncryptedData) {
                          return <span className="no-encrypted-data">-</span>;
                        }

                        if (!hasDecryptPermission) {
                          return (
                            <span className="decrypt-permission-message" role="status" title="복호화 권한이 없습니다.">
                              복호화 권한이 없습니다.
                            </span>
                          );
                        }

                        // 복호화 상태에 따라 버튼 텍스트와 동작 변경
                        if (isDecryptedRow) {
                          return (
                            <button
                              type="button"
                              className="decrypt-btn decrypt-cancel-btn"
                              onClick={(e) => {
                                logger.debug('🔓 복호화 해제 버튼 클릭:', { logGuid, status: logStatus });
                                e.preventDefault();
                                e.stopPropagation();
                                handleDecryptCancel(logGuid, logStatus);
                              }}
                              title="복호화 해제"
                              aria-label="복호화 해제"
                            >
                              복호화 해제
                            </button>
                          );
                        }

                        // req 20260318: 허용 목록(decryption-allowed) 기준으로 normal vs dimmed
                        const allowed = isAllowedForRow(logGuid, logStatus);
                        if (!allowed) {
                          return (
                            <button
                              type="button"
                              className="decrypt-btn decrypt-btn--not-allowed"
                              onClick={(e) => {
                                e.preventDefault();
                                e.stopPropagation();
                                alert(DECRYPTION_NOT_APPROVED_MESSAGE);
                              }}
                              title="복호화 승인 요청을 먼저 진행해 주세요"
                              aria-label="복호화 (승인 필요)"
                            >
                              복호화
                            </button>
                          );
                        }

                        return (
                          <button
                            type="button"
                            className={`decrypt-btn ${isDecryptingRow ? 'decrypting' : ''}`}
                            onClick={(e) => {
                              logger.debug('🔓 복호화 버튼 클릭:', { logGuid });
                              e.preventDefault();
                              e.stopPropagation();
                              handleDecrypt(logGuid, log.status, e);
                            }}
                            disabled={isDecryptingRow}
                            title={isDecryptingRow ? '복호화 중' : '복호화'}
                            aria-label={isDecryptingRow ? '복호화 중' : '복호화'}
                          >
                            {isDecryptingRow ? '복호화 중...' : '복호화'}
                          </button>
                        );
                      })()}
                    </td>
                  </tr>
                );
              })
  );

  return (
    <>
      <DataTable
        columns={IMAGE_LOG_COLUMNS}
        sortConfig={effectiveSortConfig}
        onSort={onSort}
        loading={loading}
        emptyMessage="검색 결과가 없습니다."
        emptyColSpan={10}
        pagination={pagination}
        pageSize={pageSize}
        onPageSizeChange={onPageSizeChange}
        containerClassName="log-table-container--fill"
        paginationFooterOrder="info-buttons-size"
        ariaLabel="이미지 로그 검색 결과"
      >
        {tableBody}
      </DataTable>
      {/* 상세 보기 모달 */}
      {selectedLog && (
        <div className="detail-modal-overlay" onClick={handleCloseDetail}>
          <div className="detail-modal-content" onClick={(e) => e.stopPropagation()}>
            <div className="detail-modal-header">
              <h2>로그 상세 정보</h2>
              <button className="close-btn" onClick={handleCloseDetail}>×</button>
            </div>
            <div className="detail-modal-body">
              <div className="detail-section">
                <h3>기본 정보</h3>
                <table className="detail-table">
                  <tbody>
                    <tr><td>GUID</td><td>{selectedLog.guid || ''}</td></tr>
                    <tr><td>Application</td><td>{selectedLog.application || ''}</td></tr>
                    <tr><td>Service Group</td><td>{selectedLog.servicegroup || ''}</td></tr>
                    <tr><td>Service</td><td>{selectedLog.service || ''}</td></tr>
                    <tr><td>Status</td><td>{selectedLog.status || ''}</td></tr>
                    <tr><td>Insert Time</td><td>{formatTime(selectedLog.insert_time || selectedLog.timestamp)}</td></tr>
                  </tbody>
                </table>
              </div>
              
              <div className="detail-section">
                <div className="detail-section-header">
                  <h3>Data String</h3>
                  <label className="pretty-print-toggle">
                    <input
                      type="checkbox"
                      checked={isPretty(selectedLog.guid || '', selectedLog.status || '')}
                      onChange={() => togglePretty(selectedLog.guid || '', selectedLog.status || '')}
                    />
                    Pretty Print
                  </label>
                </div>
                <pre className="json-content">
                  {(() => {
                    const selectedLogGuid = selectedLog.guid || '';
                    const selectedLogStatus = selectedLog.status || '';
                    const decryptedDataForSelected = getDecryptedData(selectedLogGuid, selectedLogStatus);
                    const datastringToShow = decryptedDataForSelected?.decrypted_datastring
                      || selectedLog.datastring
                      || selectedLog.decrypted_data
                      || selectedLog.data
                      || '';
                    const modalPretty = isPretty(selectedLogGuid, selectedLogStatus);
                    return formatJsonString(datastringToShow, modalPretty);
                  })()}
                </pre>
              </div>

              <div className="detail-section">
                <div className="detail-section-header">
                  <h3>Header String</h3>
                  <label className="pretty-print-toggle">
                    <input
                      type="checkbox"
                      checked={isPretty(selectedLog.guid || '', selectedLog.status || '')}
                      onChange={() => togglePretty(selectedLog.guid || '', selectedLog.status || '')}
                    />
                    Pretty Print
                  </label>
                </div>
                <pre className="json-content">
                  {(() => {
                    const selectedLogGuid = selectedLog.guid || '';
                    const selectedLogStatus = selectedLog.status || '';
                    const decryptedDataForSelected = getDecryptedData(selectedLogGuid, selectedLogStatus);
                    const headerstringToShow = decryptedDataForSelected?.decrypted_headerstring
                      || selectedLog.headerstring
                      || selectedLog.decrypted_header
                      || selectedLog.header
                      || '';
                    const modalPretty = isPretty(selectedLogGuid, selectedLogStatus);
                    return formatJsonString(headerstringToShow, modalPretty);
                  })()}
                </pre>
              </div>
            </div>
          </div>
        </div>
      )}
    </>
  );
};

export default ImageLogTable;

