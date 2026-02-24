import React, { useState } from 'react';
import { format } from 'date-fns';
import './LogTable.css';
import './ImageLogTable.css';
import logger from '../utils/logger';
import { getUserFriendlyErrorMessage } from '../utils/security';

const ImageLogTable = ({ 
  logs, 
  loading, 
  sortField, 
  sortDirection, 
  onSort, 
  currentPage, 
  totalPages, 
  onPageChange,
  keywords = [],
  searchParams = {}, // 검색 파라미터 추가
  searchHistoryId = null // 이번 검색에 대한 복호화 승인 이력 ID (있을 때만 복호화 허용)
}) => {
  // 정렬 아이콘 렌더링
  const renderSortIcon = (field) => {
    if (sortField !== field) {
      return <span className="sort-icon">↕</span>;
    }
    return sortDirection === 'asc' ? 
      <span className="sort-icon">↑</span> : 
      <span className="sort-icon">↓</span>;
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
  const [prettyPrint, setPrettyPrint] = useState(true);
  
  // Pretty 출력 상태 (각 로그별로 관리)
  const [prettyLogs, setPrettyLogs] = useState(new Set());
  
  // 복호화 상태 (각 로그별로 관리) - guid+status를 key로 사용
  const [decryptedLogs, setDecryptedLogs] = useState(new Map());
  const [decryptingLogs, setDecryptingLogs] = useState(new Set());
  
  // guid와 status를 조합한 고유 키 생성
  const getLogKey = (guid, status) => {
    return `${guid}::${status || ''}`;
  };
  
  // 상세 보기 열기
  const handleViewDetail = (log) => {
    setSelectedLog(log);
  };
  
  // 상세 보기 닫기
  const handleCloseDetail = () => {
    setSelectedLog(null);
  };
  
  // Pretty 출력 토글
  const togglePretty = (guid) => {
    setPrettyLogs(prev => {
      const newSet = new Set(prev);
      if (newSet.has(guid)) {
        newSet.delete(guid);
      } else {
        newSet.add(guid);
      }
      return newSet;
    });
  };
  
  // Pretty 출력 여부 확인
  const isPretty = (guid) => {
    return prettyLogs.has(guid);
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
      const apiBaseUrl = process.env.REACT_APP_API_BASE_URL || 'http://localhost:9200/api';
      logger.debug('🔓 복호화 API 호출:', { apiUrl: `${apiBaseUrl}/logs/decrypt/java_fw_imglog`, guid, status });
      const body = { guid, status };
      if (searchHistoryId != null) body.searchHistoryId = searchHistoryId;
      const response = await fetch(`${apiBaseUrl}/logs/decrypt/java_fw_imglog`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        credentials: 'include', // 세션 쿠키 전달
        body: JSON.stringify(body)
      });
      
      logger.debug('🔓 복호화 API 응답 상태:', { status: response.status });
      
      if (response.status === 403) {
        let result = null;
        try {
          const text = await response.text();
          result = text ? JSON.parse(text) : {};
        } catch (_) {
          result = {};
        }
        if (result.code === 'DECRYPTION_NOT_APPROVED') {
          logger.debug('🔓 복호화 승인 미완료:', { code: result.code });
          alert(getUserFriendlyErrorMessage('복호화', result));
          return;
        }
        if (result.code === 'ROW_NOT_IN_APPROVED_SNAPSHOT') {
          logger.debug('🔓 승인된 검색 결과에 없는 항목 복호화 시도:', { code: result.code });
          alert(result.message || '승인된 검색 결과에 포함된 항목만 복호화할 수 있습니다.');
          return;
        }
        // 기타 403: 공통 안내 후 return (body 이미 소비됨)
        alert(getUserFriendlyErrorMessage('복호화', result));
        return;
      }
      
      if (!response.ok) {
        const errorText = await response.text();
        logger.error('🔓 복호화 API 오류:', { status: response.status, error: errorText });
        throw new Error(`HTTP error! status: ${response.status}`);
      }
      
      const result = await response.json();
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

  // 페이지 번호 생성
  const getPageNumbers = () => {
    const pages = [];
    const maxPages = 10;
    let startPage = Math.max(1, currentPage - Math.floor(maxPages / 2));
    let endPage = Math.min(totalPages, startPage + maxPages - 1);
    
    if (endPage - startPage < maxPages - 1) {
      startPage = Math.max(1, endPage - maxPages + 1);
    }
    
    for (let i = startPage; i <= endPage; i++) {
      pages.push(i);
    }
    
    return pages;
  };

  if (loading) {
    return (
      <div className="log-table-container">
        <div className="loading-container">
          <div className="loading-spinner"></div>
          <p>데이터를 불러오는 중...</p>
        </div>
      </div>
    );
  }

  return (
    <div className="log-table-container">
      <div className="table-wrapper">
        <table className="log-table">
          <thead>
            <tr>
              <th 
                onClick={() => onSort('insert_time')}
                className="sortable-header"
              >
                insert_time {renderSortIcon('insert_time')}
              </th>
              <th 
                onClick={() => onSort('application')}
                className="sortable-header"
              >
                application {renderSortIcon('application')}
              </th>
              <th 
                onClick={() => onSort('servicegroup')}
                className="sortable-header"
              >
                servicegroup {renderSortIcon('servicegroup')}
              </th>
              <th 
                onClick={() => onSort('service')}
                className="sortable-header"
              >
                service {renderSortIcon('service')}
              </th>
              <th 
                onClick={() => onSort('status')}
                className="sortable-header"
              >
                status {renderSortIcon('status')}
              </th>
              <th>guid</th>
              <th>datastring</th>
              <th>headerstring</th>
              <th>Pretty</th>
              <th>복호화</th>
            </tr>
          </thead>
          <tbody>
            {logs.length === 0 ? (
              <tr>
                <td colSpan="10" className="no-data">
                  검색 결과가 없습니다.
                </td>
              </tr>
            ) : (
              logs.map((log, index) => {
                const logGuid = log.guid || `log-${index}`;
                const logStatus = log.status || '';
                const isPrettyMode = isPretty(logGuid);
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
                        className={`pretty-btn ${isPrettyMode ? 'active' : ''}`}
                        onClick={() => togglePretty(logGuid)}
                        title={isPrettyMode ? 'Pretty 출력 끄기' : 'Pretty 출력 켜기'}
                      >
                        {isPrettyMode ? 'Pretty OFF' : 'Pretty'}
                      </button>
                    </td>
                    <td className="decrypt-action-cell">
                      {(() => {
                        // 암호화된 값이 있는지 확인 (datastring, headerstring에 [...] 형태가 있거나, data, header 필드가 있는 경우)
                        const hasEncryptedData = 
                          (log.datastring && log.datastring.includes('[') && log.datastring.includes(']')) ||
                          (log.headerstring && log.headerstring.includes('[') && log.headerstring.includes(']')) ||
                          (log.data && typeof log.data === 'string' && log.data.length > 0) ||
                          (log.header && typeof log.header === 'string' && log.header.length > 0);
                        
                        if (!hasEncryptedData) {
                          return <span className="no-encrypted-data">-</span>;
                        }
                        
                        // 복호화 상태에 따라 버튼 텍스트와 동작 변경
                        if (isDecryptedRow) {
                          return (
                            <button
                              className="decrypt-btn decrypt-cancel-btn"
                              onClick={(e) => {
                                logger.debug('🔓 복호화 해제 버튼 클릭:', { logGuid, status: logStatus });
                                e.preventDefault();
                                e.stopPropagation();
                                handleDecryptCancel(logGuid, logStatus);
                              }}
                              title="복호화 해제"
                            >
                              복호화 해제
                            </button>
                          );
                        }
                        
                        return (
                          <button
                            className={`decrypt-btn ${isDecryptingRow ? 'decrypting' : ''}`}
                            onClick={(e) => {
                              logger.debug('🔓 복호화 버튼 클릭:', { logGuid });
                              e.preventDefault();
                              e.stopPropagation();
                              handleDecrypt(logGuid, log.status, e);
                            }}
                            disabled={isDecryptingRow}
                            title="복호화"
                          >
                            {isDecryptingRow ? '복호화 중...' : '복호화'}
                          </button>
                        );
                      })()}
                    </td>
                  </tr>
                );
              })
            )}
          </tbody>
        </table>
      </div>
      
      {totalPages > 1 && (
        <div className="pagination">
          <button 
            onClick={() => onPageChange(1)}
            disabled={currentPage === 1}
            className="page-button"
          >
            처음
          </button>
          <button 
            onClick={() => onPageChange(currentPage - 1)}
            disabled={currentPage === 1}
            className="page-button"
          >
            이전
          </button>
          
          {getPageNumbers().map(page => (
            <button
              key={page}
              onClick={() => onPageChange(page)}
              className={`page-button ${currentPage === page ? 'active' : ''}`}
            >
              {page}
            </button>
          ))}
          
          <button 
            onClick={() => onPageChange(currentPage + 1)}
            disabled={currentPage === totalPages}
            className="page-button"
          >
            다음
          </button>
          <button 
            onClick={() => onPageChange(totalPages)}
            disabled={currentPage === totalPages}
            className="page-button"
          >
            마지막
          </button>
        </div>
      )}
      
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
                      checked={prettyPrint}
                      onChange={(e) => setPrettyPrint(e.target.checked)}
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
                    return formatJsonString(datastringToShow, prettyPrint);
                  })()}
                </pre>
              </div>
              
              <div className="detail-section">
                <div className="detail-section-header">
                  <h3>Header String</h3>
                  <label className="pretty-print-toggle">
                    <input 
                      type="checkbox" 
                      checked={prettyPrint}
                      onChange={(e) => setPrettyPrint(e.target.checked)}
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
                    return formatJsonString(headerstringToShow, prettyPrint);
                  })()}
                </pre>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default ImageLogTable;

