import React, { useState, useEffect } from 'react';
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

const UserActivityLogSearchForm = ({ onSearch, loading, initialServerDate }) => {
  const serverRange = toDateTimeLocal(initialServerDate);
  const [formData, setFormData] = useState({
    startDate: serverRange ? serverRange.start : getTodayStart(),
    endDate: serverRange ? serverRange.end : getTodayEnd(),
    userId: '',
    username: '',
    actionType: '',
    ipAddress: '',
  });

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
  };

  const handleSubmit = (e) => {
    e.preventDefault();
    // 날짜 형식을 API 요청 형식으로 변환
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
      actionType: '',
      ipAddress: '',
    };
    setFormData(resetData);
    // 날짜 형식을 API 요청 형식으로 변환하여 검색
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

  return (
    <form className="activity-log-search-form" onSubmit={handleSubmit}>
      <div className="search-form-row">
        <div className="form-group">
          <label htmlFor="startDate">시작 날짜</label>
          <input
            type="datetime-local"
            id="startDate"
            name="startDate"
            value={formData.startDate}
            onChange={handleInputChange}
            className="form-control"
          />
        </div>

        <div className="form-group">
          <label htmlFor="endDate">종료 날짜</label>
          <input
            type="datetime-local"
            id="endDate"
            name="endDate"
            value={formData.endDate}
            onChange={handleInputChange}
            className="form-control"
          />
        </div>
      </div>

      <div className="search-form-row">
        <div className="form-group">
          <label htmlFor="userId">사용자 ID</label>
          <input
            type="text"
            id="userId"
            name="userId"
            value={formData.userId}
            onChange={handleInputChange}
            className="form-control"
            placeholder="사용자 ID"
          />
        </div>

        <div className="form-group">
          <label htmlFor="username">사용자명</label>
          <input
            type="text"
            id="username"
            name="username"
            value={formData.username}
            onChange={handleInputChange}
            className="form-control"
            placeholder="사용자명"
          />
        </div>
      </div>

      <div className="search-form-row">
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

      <div className="search-form-actions">
        <button type="submit" className="btn btn-primary" disabled={loading}>
          {loading ? '검색 중...' : '검색'}
        </button>
        <button type="button" className="btn btn-secondary" onClick={handleReset}>
          초기화
        </button>
      </div>
    </form>
  );
};

export default UserActivityLogSearchForm;

