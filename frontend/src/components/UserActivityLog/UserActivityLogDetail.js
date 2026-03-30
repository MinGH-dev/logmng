import React from 'react';
import { getActivityActionTypeLabel } from '../../utils/activityActionTypeOptions';
import './UserActivityLog.css';

const UserActivityLogDetail = ({ log, onClose, actionTypeLabelMap = {} }) => {
  if (!log) {
    return null;
  }

  const formatDateTime = (dateTimeStr) => {
    if (!dateTimeStr) return '-';
    try {
      const date = new Date(dateTimeStr);
      return date.toLocaleString('ko-KR');
    } catch (e) {
      return dateTimeStr;
    }
  };

  const formatJSON = (obj) => {
    if (!obj) return '-';
    try {
      let parsed;
      if (typeof obj === 'string') {
        parsed = JSON.parse(obj);
      } else {
        parsed = obj;
      }
      // 민감한 정보 마스킹
      const masked = maskSensitiveData(parsed);
      return JSON.stringify(masked, null, 2);
    } catch (e) {
      return maskSensitiveData(obj.toString());
    }
  };

  /**
   * 민감한 정보 마스킹 처리
   */
  const maskSensitiveData = (data) => {
    if (!data) return data;
    
    if (typeof data === 'string') {
      // 비밀번호 패턴 마스킹
      let masked = data.replace(/"password"\s*:\s*"([^"]+)"/gi, '"password":"***"');
      masked = masked.replace(/"pwd"\s*:\s*"([^"]+)"/gi, '"pwd":"***"');
      masked = masked.replace(/"secret"\s*:\s*"([^"]+)"/gi, '"secret":"***"');
      masked = masked.replace(/"token"\s*:\s*"([^"]+)"/gi, '"token":"***"');
      
      // 주민등록번호 패턴 마스킹
      masked = masked.replace(/(\d{6}-)\d{7}/g, '$1*******');
      
      // 신용카드 번호 패턴 마스킹
      masked = masked.replace(/(\d{4}-)\d{4}-\d{4}-(\d{4})/g, '$1****-****-$2');
      
      // 전화번호 패턴 마스킹
      masked = masked.replace(/(\d{3}-)\d{4}(-\d{4})/g, '$1****$2');
      
      // 이메일 패턴 마스킹
      masked = masked.replace(/([a-zA-Z0-9])[a-zA-Z0-9]*@/g, '$1***@');
      
      return masked;
    }
    
    if (typeof data === 'object') {
      if (Array.isArray(data)) {
        return data.map(item => maskSensitiveData(item));
      }
      
      const masked = {};
      for (const key in data) {
        if (Object.prototype.hasOwnProperty.call(data, key)) {
          const lowerKey = key.toLowerCase();
          // 민감한 필드는 마스킹
          if (lowerKey.includes('password') || lowerKey.includes('pwd') || 
              lowerKey.includes('secret') || lowerKey.includes('token')) {
            masked[key] = '***';
          } else {
            masked[key] = maskSensitiveData(data[key]);
          }
        }
      }
      return masked;
    }
    
    return data;
  };

  return (
    <div className="activity-log-detail-overlay" onClick={onClose}>
      <div className="activity-log-detail-modal" onClick={(e) => e.stopPropagation()}>
        <div className="activity-log-detail-header">
          <h2>활동 이력 상세</h2>
          <button className="close-button" onClick={onClose}>×</button>
        </div>
        <div className="activity-log-detail-content">
          <div className="detail-section">
            <h3>기본 정보</h3>
            <table className="detail-table">
              <tbody>
                <tr>
                  <th>ID</th>
                  <td>{log.id}</td>
                </tr>
                <tr>
                  <th>사용자 ID</th>
                  <td>{log.user_id || '-'}</td>
                </tr>
                <tr>
                  <th>사용자명</th>
                  <td>{log.username || '-'}</td>
                </tr>
                <tr>
                  <th>액션 타입</th>
                  <td>{getActivityActionTypeLabel(log.action_type, actionTypeLabelMap)}</td>
                </tr>
                <tr>
                  <th>IP 주소</th>
                  <td>{log.ip_address || '-'}</td>
                </tr>
                <tr>
                  <th>User-Agent</th>
                  <td>{log.user_agent || '-'}</td>
                </tr>
                <tr>
                  <th>요청 메서드</th>
                  <td>{log.request_method || '-'}</td>
                </tr>
                <tr>
                  <th>요청 경로</th>
                  <td>{log.request_path || '-'}</td>
                </tr>
                <tr>
                  <th>응답 상태</th>
                  <td>{log.response_status || '-'}</td>
                </tr>
                <tr>
                  <th>응답 시간</th>
                  <td>
                    {log.response_time_ms != null
                      ? `${log.response_time_ms}ms`
                      : '-'}
                  </td>
                </tr>
                <tr>
                  <th>성공 여부</th>
                  <td>{log.success ? '성공' : '실패'}</td>
                </tr>
                <tr>
                  <th>에러 메시지</th>
                  <td>{log.error_message || '-'}</td>
                </tr>
                <tr>
                  <th>생성일시</th>
                  <td>{formatDateTime(log.created_at)}</td>
                </tr>
                <tr>
                  <th>수정일시</th>
                  <td>{formatDateTime(log.updated_at)}</td>
                </tr>
              </tbody>
            </table>
          </div>

          {log.action_detail && (
            <div className="detail-section">
              <h3>액션 상세</h3>
              {log.action_detail.searchSummary && (
                <div className="search-summary">
                  <h4>검색 결과 요약</h4>
                  <table className="summary-table">
                    <tbody>
                      {log.action_detail.searchSummary.totalCount != null && (
                        <tr>
                          <th>전체 건수</th>
                          <td>
                            {log.action_detail.searchSummary.totalCount.toLocaleString()}건
                            <span className="summary-description">(검색 조건에 맞는 전체 결과)</span>
                          </td>
                        </tr>
                      )}
                      {log.action_detail.searchSummary.resultCount != null && (
                        <tr>
                          <th>반환 건수</th>
                          <td>
                            {log.action_detail.searchSummary.resultCount.toLocaleString()}건
                            <span className="summary-description">(현재 페이지에서 실제로 반환된 데이터)</span>
                          </td>
                        </tr>
                      )}
                      {log.action_detail.searchSummary.currentPage != null && (
                        <tr>
                          <th>현재 페이지</th>
                          <td>
                            {log.action_detail.searchSummary.currentPage}페이지
                            {log.action_detail.searchSummary.totalPages != null && 
                             log.action_detail.searchSummary.totalPages > 1 && (
                              <span className="summary-description">
                                (전체 {log.action_detail.searchSummary.totalPages}페이지 중)
                              </span>
                            )}
                          </td>
                        </tr>
                      )}
                      {log.action_detail.searchSummary.totalPages != null && (
                        <tr>
                          <th>전체 페이지</th>
                          <td>
                            {log.action_detail.searchSummary.totalPages}페이지
                            <span className="summary-description">
                              (페이지당 표시 가능한 최대 건수 기준)
                            </span>
                          </td>
                        </tr>
                      )}
                    </tbody>
                  </table>
                </div>
              )}
              {log.action_detail.requestParams && (
                <div className="search-conditions">
                  <h4>검색 조건</h4>
                  {(() => {
                    const requestParams = log.action_detail.requestParams;
                    // requestParams.request 또는 requestParams의 직접 속성 확인
                    let request = requestParams.request;
                    
                    // request가 문자열인 경우 (JSON 문자열로 저장된 경우) 파싱 시도
                    if (typeof request === 'string') {
                      try {
                        request = JSON.parse(request);
                      } catch (e) {
                        // 파싱 실패 시 원본 사용
                      }
                    }
                    
                    // request가 없으면 requestParams 자체를 확인
                    if (!request && typeof requestParams === 'object') {
                      // requestParams의 직접 속성들을 확인
                      if (requestParams.logType || requestParams.startDate) {
                        request = requestParams;
                      }
                    }
                    
                    if (request && typeof request === 'object' && !Array.isArray(request)) {
                      // 검색 조건을 구조화하여 표시
                      return (
                        <div className="search-conditions-detail">
                          <table className="summary-table">
                            <tbody>
                              {request.logType && (
                                <tr>
                                  <th>로그 타입</th>
                                  <td>{request.logType}</td>
                                </tr>
                              )}
                              {request.startDate && (
                                <tr>
                                  <th>시작 날짜</th>
                                  <td>{request.startDate}</td>
                                </tr>
                              )}
                              {request.endDate && (
                                <tr>
                                  <th>종료 날짜</th>
                                  <td>{request.endDate}</td>
                                </tr>
                              )}
                              {request.page && (
                                <tr>
                                  <th>페이지</th>
                                  <td>{request.page} / {request.pageSize ? `페이지당 ${request.pageSize}건` : ''}</td>
                                </tr>
                              )}
                              {request.logType === 'java_fw_imglog' && (
                                <>
                                  {request.application && (
                                    <tr>
                                      <th>Application</th>
                                      <td>{request.application}</td>
                                    </tr>
                                  )}
                                  {request.servicegroup && (
                                    <tr>
                                      <th>Service Group</th>
                                      <td>{request.servicegroup}</td>
                                    </tr>
                                  )}
                                  {request.service && (
                                    <tr>
                                      <th>Service</th>
                                      <td>{request.service}</td>
                                    </tr>
                                  )}
                                  {request.datastring && (
                                    <tr>
                                      <th>Data String</th>
                                      <td>{request.datastring}</td>
                                    </tr>
                                  )}
                                  {request.headerstring && (
                                    <tr>
                                      <th>Header String</th>
                                      <td>{request.headerstring}</td>
                                    </tr>
                                  )}
                                  {request.keywords && Array.isArray(request.keywords) && request.keywords.length > 0 && (
                                    <tr>
                                      <th>Keywords</th>
                                      <td>{request.keywords.join(', ')}</td>
                                    </tr>
                                  )}
                                </>
                              )}
                              {request.logType === 'pb_feplog' && (
                                <>
                                  {request.mediaCode && (
                                    <tr>
                                      <th>매체 코드</th>
                                      <td>{request.mediaCode}</td>
                                    </tr>
                                  )}
                                  {request.trCode && (
                                    <tr>
                                      <th>TR 코드</th>
                                      <td>{request.trCode}</td>
                                    </tr>
                                  )}
                                  {request.loginId && (
                                    <tr>
                                      <th>로그인 ID</th>
                                      <td>{request.loginId}</td>
                                    </tr>
                                  )}
                                </>
                              )}
                            </tbody>
                          </table>
                          <details className="json-details">
                            <summary>전체 JSON 보기</summary>
                            <pre className="json-content json-pretty">{formatJSON(requestParams)}</pre>
                          </details>
                        </div>
                      );
                    } else {
                      // 구조화되지 않은 경우 원본 JSON 표시
                      return <pre className="json-content json-pretty">{formatJSON(requestParams)}</pre>;
                    }
                  })()}
                </div>
              )}
            </div>
          )}

          {log.request_params && (
            <div className="detail-section">
              <h3>요청 파라미터</h3>
              <pre className="json-content">{formatJSON(log.request_params)}</pre>
            </div>
          )}
        </div>
        <div className="activity-log-detail-footer">
          <button className="btn btn-primary" onClick={onClose}>
            닫기
          </button>
        </div>
      </div>
    </div>
  );
};

export default UserActivityLogDetail;

