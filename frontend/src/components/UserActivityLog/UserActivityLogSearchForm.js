import React, { useState, useEffect } from 'react';
import UserContextFilterBlock from '../common/UserContextFilterBlock';
import './UserActivityLog.css';

/**
 * 오늘 날짜의 시작 시간 (00:00:00)을 datetime-local 형식으로 반환
 */
const getTodayStart = () => {
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  const year = today.getFullYear();
  const month = String(today.getMonth() + 1).padStart(2, '0');
  const day = String(today.getDate()).padStart(2, '0');
  const hours = String(today.getHours()).padStart(2, '0');
  const minutes = String(today.getMinutes()).padStart(2, '0');
  return `${year}-${month}-${day}T${hours}:${minutes}`;
};

/**
 * 오늘 날짜의 종료 시간 (23:59:59)을 datetime-local 형식으로 반환
 */
const getTodayEnd = () => {
  const today = new Date();
  today.setHours(23, 59, 59, 999);
  const year = today.getFullYear();
  const month = String(today.getMonth() + 1).padStart(2, '0');
  const day = String(today.getDate()).padStart(2, '0');
  const hours = String(today.getHours()).padStart(2, '0');
  const minutes = String(today.getMinutes()).padStart(2, '0');
  return `${year}-${month}-${day}T${hours}:${minutes}`;
};

/**
 * datetime-local 형식을 API 요청 형식(yyyy-MM-dd HH:mm:ss)으로 변환.
 * 종료 시각이 23:59이면 초를 59로 넣어 당일 끝까지 포함되도록 함.
 */
const formatDateForAPI = (dateTimeLocal) => {
  if (!dateTimeLocal) return '';
  // datetime-local 형식: "YYYY-MM-DDTHH:mm"
  // API 형식: "YYYY-MM-DD HH:mm:ss"
  const [date, time] = dateTimeLocal.split('T');
  const seconds = (time === '23:59') ? '59' : '00';
  return `${date} ${time}:${seconds}`;
};

const toDateTimeLocal = (dateStr) => {
  if (!dateStr || !/^\d{4}-\d{2}-\d{2}$/.test(dateStr)) return null;
  return { start: `${dateStr}T00:00`, end: `${dateStr}T23:59` };
};

const UserActivityLogSearchForm = ({ onSearch, loading, initialServerDate, hideUserFilters = false, departmentList = [] }) => {
  const serverRange = toDateTimeLocal(initialServerDate);
  const [formData, setFormData] = useState({
    startDate: serverRange ? serverRange.start : getTodayStart(),
    endDate: serverRange ? serverRange.end : getTodayEnd(),
    userId: '',
    username: '',
    department: '',
    actionType: '',
    ipAddress: '',
  });
  const [errors, setErrors] = useState({});

  // 서버 날짜가 나중에 도착하면 폼 표시를 서버 기준 '오늘'로 맞춤 (초기 검색은 List에서 서버 날짜로 이미 실행됨)
  useEffect(() => {
    if (!initialServerDate) return;
    const range = toDateTimeLocal(initialServerDate);
    if (range) {
      setFormData((prev) => ({
        ...prev,
        startDate: range.start,
        endDate: range.end,
      }));
    }
  }, [initialServerDate]);

  const handleInputChange = (e) => {
    const { name, value } = e.target;
    setFormData(prev => ({
      ...prev,
      [name]: value
    }));
    if (errors[name] || (name === 'startDate' || name === 'endDate' ? errors.dateRange : false)) {
      setErrors(prev => {
        const next = { ...prev, [name]: '' };
        if (name === 'startDate' || name === 'endDate') next.dateRange = '';
        return next;
      });
    }
  };

  const handleSubmit = (e) => {
    e.preventDefault();
    const newErrors = {};
    // 날짜 범위: 시작 ≤ 종료 (date-search.md) — 시작일/종료일 둘 다 aria-invalid·aria-describedby 적용
    if (formData.startDate && formData.endDate && new Date(formData.startDate) > new Date(formData.endDate)) {
      newErrors.dateRange = '종료일시는 시작일시보다 이전일 수 없습니다.';
    }
    if (Object.keys(newErrors).length > 0) {
      setErrors(newErrors);
      return;
    }
    setErrors({});
    const searchParams = {
      ...formData,
      startDate: formatDateForAPI(formData.startDate),
      endDate: formatDateForAPI(formData.endDate),
    };
    onSearch(searchParams);
  };

  const handleReset = () => {
    const resetData = {
      startDate: getTodayStart(),
      endDate: getTodayEnd(),
      userId: '',
      username: '',
      department: '',
      actionType: '',
      ipAddress: '',
    };
    setFormData(resetData);
    setErrors({});
    onSearch({
      startDate: formatDateForAPI(resetData.startDate),
      endDate: formatDateForAPI(resetData.endDate),
    });
  };

  const actionTypes = [
    { value: '', label: '전체' },
    { value: 'LOGIN', label: '로그인' },
    { value: 'LOGOUT', label: '로그아웃' },
    { value: 'SEARCH', label: '검색' },
    { value: 'VIEW', label: '조회' },
    { value: 'DECRYPT', label: '복호화' },
    { value: 'ADVANCED_SEARCH', label: '고급 검색' },
    { value: 'EXPORT', label: '내보내기' },
  ];

  const dateRangeErrorId = 'user-activity-log-search-form-date-range-error';

  return (
    <form
      className="activity-log-search-form sf-compact-panel"
      onSubmit={handleSubmit}
      aria-label="사용자 활동 이력 검색 조건"
      aria-busy={loading}
    >
      {/* Filter body always visible; no collapsible "필터 접기" per req 20260313-activity-log-statistics-design-standards */}
      <div id="activity-log-search-filters-body">
        {/* Row 1: 시작 일시, 종료 일시 only (req 20260313) */}
        <div className="search-form-row-1">
          <div className="form-group">
            <label htmlFor="startDate">시작 일시</label>
            <input
              type="datetime-local"
              id="startDate"
              name="startDate"
              value={formData.startDate}
              onChange={handleInputChange}
              className={`form-control${errors.startDate || errors.dateRange ? ' error' : ''}`}
              aria-invalid={!!(errors.startDate || errors.dateRange)}
              aria-describedby={[errors.startDate && 'startDate-error', errors.dateRange && dateRangeErrorId].filter(Boolean).join(' ') || undefined}
            />
            {errors.startDate && <span id="startDate-error" className="error-message" role="alert">{errors.startDate}</span>}
          </div>
          <div className="form-group">
            <label htmlFor="endDate">종료 일시</label>
            <input
              type="datetime-local"
              id="endDate"
              name="endDate"
              value={formData.endDate}
              onChange={handleInputChange}
              className={`form-control${errors.endDate || errors.dateRange ? ' error' : ''}`}
              aria-invalid={!!(errors.endDate || errors.dateRange)}
              aria-describedby={[errors.endDate && 'endDate-error', errors.dateRange && dateRangeErrorId].filter(Boolean).join(' ') || undefined}
            />
            {errors.endDate && <span id="endDate-error" className="error-message" role="alert">{errors.endDate}</span>}
          </div>
          {errors.dateRange && (
            <div className="search-form-row-1__date-error" aria-live="polite">
              <span id={dateRangeErrorId} className="error-message" role="alert">{errors.dateRange}</span>
            </div>
          )}
        </div>

        {/* Row 2: 사용자 block + 기타 조건 (title above) + 검색/초기화 in filter actions row */}
        <div className="search-form-row-2" role="group" aria-labelledby="activity-log-search-row2-heading">
          <h2 id="activity-log-search-row2-heading" className="activity-log-search-form__row2-heading-sr-only">검색 조건</h2>
          <UserContextFilterBlock
            blockLabel="사용자"
            hideUserFilters={hideUserFilters}
            departmentList={departmentList}
            values={{
              department: formData.department,
              username: formData.username,
              userId: formData.userId,
            }}
            onChange={(name, value) => setFormData((prev) => ({ ...prev, [name]: value }))}
            idPrefix="activity-log-search"
            compact
            usernameMaxLength={5}
          />
          {/* 기타 조건: scope=self 시 전체 숨김 (req 20260313); 제목 필드 위 배치 */}
          {!hideUserFilters && (
            <div className="search-form-row-2__extra" role="group" aria-labelledby="activity-log-search-extra-heading">
              <h4 id="activity-log-search-extra-heading" className="search-form-block__heading">기타 조건</h4>
              <div className="search-form-row-2__extra-fields">
                <div className="form-group">
                  <label htmlFor="actionType">액션 타입</label>
                  <select
                    id="actionType"
                    name="actionType"
                    value={formData.actionType}
                    onChange={handleInputChange}
                    className="form-control"
                  >
                    {actionTypes.map(type => (
                      <option key={type.value} value={type.value}>
                        {type.label}
                      </option>
                    ))}
                  </select>
                </div>
                <div className="form-group">
                  <label htmlFor="ipAddress">IP 주소</label>
                  <input
                    type="text"
                    id="ipAddress"
                    name="ipAddress"
                    value={formData.ipAddress}
                    onChange={handleInputChange}
                    className="form-control"
                    placeholder="IP 주소"
                  />
                </div>
              </div>
            </div>
          )}
          <div className="search-form-actions">
            <button type="submit" className="btn btn-primary sf-btn" disabled={loading} aria-busy={loading}>
              {loading ? (
                <>
                  <span className="activity-log-search-form__spinner" aria-hidden="true" />
                  검색 중...
                </>
              ) : (
                '검색'
              )}
            </button>
            <button type="button" className="btn btn-secondary sf-btn" onClick={handleReset}>
              초기화
            </button>
          </div>
        </div>
      </div>
    </form>
  );
};

export default UserActivityLogSearchForm;

