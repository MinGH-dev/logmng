import React, { useState } from 'react';
import './SearchForm.css';

const SearchForm = ({ onSearch }) => {
  // 오늘 날짜의 00:00:00부터 23:59:59까지 기본값 설정
  const getTodayDateTime = (hours, minutes, seconds) => {
    const today = new Date();
    const year = today.getFullYear();
    const month = String(today.getMonth() + 1).padStart(2, '0');
    const day = String(today.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}T${String(hours).padStart(2, '0')}:${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`;
  };

  const [formData, setFormData] = useState({
    startDate: getTodayDateTime(0, 0, 0), // 오늘 00:00:00
    endDate: getTodayDateTime(23, 59, 59), // 오늘 23:59:59
    media_gb: '',
    tr_code: '',
    loginId: '',
    keywords: '',
    showDecryptOption: false
  });

  const [errors, setErrors] = useState({});

  // 폼 데이터 변경 처리
  const handleInputChange = (e) => {
    const { name, value, type, checked } = e.target;
    setFormData(prev => ({
      ...prev,
      [name]: type === 'checkbox' ? checked : value
    }));

    // 에러 제거 (날짜 변경 시 날짜 범위 에러도 제거)
    if (errors[name] || (name === 'startDate' || name === 'endDate' ? errors.dateRange : false)) {
      setErrors(prev => {
        const next = { ...prev, [name]: '' };
        if (name === 'startDate' || name === 'endDate') next.dateRange = '';
        return next;
      });
    }
  };

  // 계좌번호 입력 시 복호화 옵션 활성화
  const handleKeywordsChange = (e) => {
    const value = e.target.value;
    setFormData(prev => ({
      ...prev,
      keywords: value,
      showDecryptOption: value.trim() !== ''
    }));
  };

  // 검색 실행
  const handleSubmit = (e) => {
    e.preventDefault();
    
    // 필수 필드 검증
    const newErrors = {};
    if (!formData.startDate) newErrors.startDate = '시작일시는 필수입니다.';
    if (!formData.endDate) newErrors.endDate = '종료일시는 필수입니다.';
    if (!formData.tr_code) newErrors.tr_code = 'TR Code는 필수입니다.';
    // 날짜 범위: 시작 ≤ 종료 (date-search.md) — 시작일/종료일 둘 다 aria-invalid·aria-describedby 적용
    if (formData.startDate && formData.endDate && new Date(formData.startDate) > new Date(formData.endDate)) {
      newErrors.dateRange = '종료일시는 시작일시보다 이전일 수 없습니다.';
    }

    if (Object.keys(newErrors).length > 0) {
      setErrors(newErrors);
      return;
    }

    // 키워드 배열로 변환
    const keywordsArray = formData.keywords
      ? formData.keywords.split(',').map(keyword => keyword.trim()).filter(keyword => keyword)
      : [];

    // 날짜 형식 변환 (YYYY-MM-DDTHH:mm -> HH24MISSMS3 형식)
    const formatDateForAPI = (dateStr) => {
      if (!dateStr) return '';
      const date = new Date(dateStr);
      const hours = String(date.getHours()).padStart(2, '0');
      const minutes = String(date.getMinutes()).padStart(2, '0');
      const seconds = String(date.getSeconds()).padStart(2, '0');
      const milliseconds = String(date.getMilliseconds()).padStart(3, '0');
      return `${hours}${minutes}${seconds}${milliseconds}`;
    };

    const searchParams = {
      ...formData,
      startDate: formatDateForAPI(formData.startDate),
      endDate: formatDateForAPI(formData.endDate),
      keywords: keywordsArray
    };

    console.log('🔍 SearchForm에서 전송할 데이터:', searchParams);
    onSearch(searchParams);
  };

  // 폼 초기화
  const handleReset = () => {
    setFormData({
      startDate: getTodayDateTime(0, 0, 0), // 오늘 00:00:00
      endDate: getTodayDateTime(23, 59, 59), // 오늘 23:59:59
      media_gb: '',
      tr_code: '',
      loginId: '',
      keywords: '',
      showDecryptOption: false
    });
    setErrors({});
  };

  return (
    <div className="search-form-container">
      <form onSubmit={handleSubmit} className="search-form">
        <div className="form-row-single">
          <div className="form-group">
            <label htmlFor="startDate">
              시작일시 <span className="required">*</span>
            </label>
            <input
              type="datetime-local"
              id="startDate"
              name="startDate"
              value={formData.startDate}
              onChange={handleInputChange}
              className={errors.startDate || errors.dateRange ? 'error' : ''}
              step="1"
              aria-invalid={!!(errors.startDate || errors.dateRange)}
              aria-describedby={[errors.startDate && 'startDate-error', errors.dateRange && 'search-form-date-range-error'].filter(Boolean).join(' ') || undefined}
            />
            {errors.startDate && <span id="startDate-error" className="error-message" role="alert">{errors.startDate}</span>}
          </div>

          <div className="form-group">
            <label htmlFor="endDate">
              종료일시 <span className="required">*</span>
            </label>
            <input
              type="datetime-local"
              id="endDate"
              name="endDate"
              value={formData.endDate}
              onChange={handleInputChange}
              className={errors.endDate || errors.dateRange ? 'error' : ''}
              step="1"
              aria-invalid={!!(errors.endDate || errors.dateRange)}
              aria-describedby={[errors.endDate && 'endDate-error', errors.dateRange && 'search-form-date-range-error'].filter(Boolean).join(' ') || undefined}
            />
            {errors.endDate && <span id="endDate-error" className="error-message" role="alert">{errors.endDate}</span>}
            {errors.dateRange && <span id="search-form-date-range-error" className="error-message" role="alert">{errors.dateRange}</span>}
          </div>

          <div className="form-group">
            <label htmlFor="media_gb">
              매체코드
            </label>
            <input
              type="text"
              id="media_gb"
              name="media_gb"
              value={formData.media_gb}
              onChange={handleInputChange}
              placeholder="매체코드"
              className={errors.media_gb ? 'error' : ''}
              aria-invalid={!!errors.media_gb}
              aria-describedby={errors.media_gb ? 'media_gb-error' : undefined}
            />
            {errors.media_gb && <span id="media_gb-error" className="error-message" role="alert">{errors.media_gb}</span>}
          </div>

          <div className="form-group">
            <label htmlFor="tr_code">
              TR Code <span className="required">*</span>
            </label>
            <input
              type="text"
              id="tr_code"
              name="tr_code"
              value={formData.tr_code}
              onChange={handleInputChange}
              placeholder="TR Code"
              className={errors.tr_code ? 'error' : ''}
              aria-invalid={!!errors.tr_code}
              aria-describedby={errors.tr_code ? 'tr_code-error' : undefined}
            />
            {errors.tr_code && <span id="tr_code-error" className="error-message" role="alert">{errors.tr_code}</span>}
          </div>

          <div className="form-group">
            <label htmlFor="loginId">Login ID</label>
            <input
              type="text"
              id="loginId"
              name="loginId"
              value={formData.loginId}
              onChange={handleInputChange}
              placeholder="Login ID"
            />
          </div>

          <div className="form-group">
            <label htmlFor="keywords">키워드 검색</label>
            <input
              type="text"
              id="keywords"
              name="keywords"
              value={formData.keywords}
              onChange={handleKeywordsChange}
              placeholder="키워드1, 키워드2, 키워드3 (OR 조건으로 검색)"
            />
          </div>
        </div>

        {formData.showDecryptOption && (
          <div className="form-row">
            <div className="form-group checkbox-group">
              <label>
                <input
                  type="checkbox"
                  name="decryptData"
                  checked={formData.decryptData || false}
                  onChange={handleInputChange}
                />
                키워드 검색 시 복호화 여부 체크
              </label>
            </div>
          </div>
        )}

        <div className="form-actions">
          <button type="submit" className="btn btn-primary">
            검색
          </button>
          <button type="button" className="btn btn-secondary" onClick={handleReset}>
            초기화
          </button>
        </div>
      </form>
    </div>
  );
};

export default SearchForm; 