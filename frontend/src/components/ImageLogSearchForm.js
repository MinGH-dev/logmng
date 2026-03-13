import React, { useState, useEffect } from 'react';
import './SearchForm.css';

const ImageLogSearchForm = ({ onSearch, initialFormValues }) => {
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
    application: '',
    servicegroup: '',
    service: '',
    datastring: '',
    headerstring: '',
    keywords: '',
    showDecryptOption: false
  });

  const [errors, setErrors] = useState({});

  // 재조회 등으로 부모가 전달한 초기값이 있으면 폼 상태 동기화 (검색 조건창에 동일 조건 표시)
  useEffect(() => {
    if (!initialFormValues || typeof initialFormValues !== 'object') return;
    setFormData(prev => {
      const normalized = {
        startDate: initialFormValues.startDate != null ? String(initialFormValues.startDate) : prev.startDate,
        endDate: initialFormValues.endDate != null ? String(initialFormValues.endDate) : prev.endDate,
        application: initialFormValues.application != null ? String(initialFormValues.application) : '',
        servicegroup: initialFormValues.servicegroup != null ? String(initialFormValues.servicegroup) : '',
        service: initialFormValues.service != null ? String(initialFormValues.service) : '',
        datastring: initialFormValues.datastring != null ? String(initialFormValues.datastring) : '',
        headerstring: initialFormValues.headerstring != null ? String(initialFormValues.headerstring) : '',
        keywords: initialFormValues.keywords != null ? String(initialFormValues.keywords) : '',
        showDecryptOption: Boolean(initialFormValues.showDecryptOption)
      };
      return { ...prev, ...normalized };
    });
  }, [initialFormValues]);

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

  // 키워드 입력 시 복호화 옵션 활성화
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
    e.stopPropagation();
    
    // 필수 필드 검증
    const newErrors = {};
    if (!formData.startDate) newErrors.startDate = '시작일시는 필수입니다.';
    if (!formData.endDate) newErrors.endDate = '종료일시는 필수입니다.';
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

    // 날짜 형식 변환 (YYYY-MM-DDTHH:mm:ss -> yyyy-MM-dd HH:mm:ss 형식)
    const formatDateForAPI = (dateStr) => {
      if (!dateStr) return '';
      const date = new Date(dateStr);
      const year = date.getFullYear();
      const month = String(date.getMonth() + 1).padStart(2, '0');
      const day = String(date.getDate()).padStart(2, '0');
      const hours = String(date.getHours()).padStart(2, '0');
      const minutes = String(date.getMinutes()).padStart(2, '0');
      const seconds = String(date.getSeconds()).padStart(2, '0');
      return `${year}-${month}-${day} ${hours}:${minutes}:${seconds}`;
    };

    const searchParams = {
      ...formData,
      startDate: formatDateForAPI(formData.startDate),
      endDate: formatDateForAPI(formData.endDate),
      keywords: keywordsArray,
      // datastring과 headerstring 값이 제대로 전달되도록 명시적으로 설정
      datastring: formData.datastring ? String(formData.datastring).trim() : '',
      headerstring: formData.headerstring ? String(formData.headerstring).trim() : '',
      application: formData.application ? String(formData.application).trim() : '',
      servicegroup: formData.servicegroup ? String(formData.servicegroup).trim() : '',
      service: formData.service ? String(formData.service).trim() : ''
    };
    
    if (typeof onSearch === 'function') {
      onSearch(searchParams);
    } else {
      console.error('onSearch 함수가 없습니다!');
    }
  };

  // 폼 초기화
  const handleReset = () => {
    setFormData({
      startDate: getTodayDateTime(0, 0, 0), // 오늘 00:00:00
      endDate: getTodayDateTime(23, 59, 59), // 오늘 23:59:59
      application: '',
      servicegroup: '',
      service: '',
      datastring: '',
      headerstring: '',
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
              aria-describedby={[errors.startDate && 'startDate-error', errors.dateRange && 'image-log-search-form-date-range-error'].filter(Boolean).join(' ') || undefined}
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
              aria-describedby={[errors.endDate && 'endDate-error', errors.dateRange && 'image-log-search-form-date-range-error'].filter(Boolean).join(' ') || undefined}
            />
            {errors.endDate && <span id="endDate-error" className="error-message" role="alert">{errors.endDate}</span>}
            {errors.dateRange && <span id="image-log-search-form-date-range-error" className="error-message" role="alert">{errors.dateRange}</span>}
          </div>

          <div className="form-group">
            <label htmlFor="application">시스템 명</label>
            <input
              type="text"
              id="application"
              name="application"
              value={formData.application}
              onChange={handleInputChange}
              placeholder="시스템 명"
            />
          </div>

          <div className="form-group">
            <label htmlFor="servicegroup">서비스그룹</label>
            <input
              type="text"
              id="servicegroup"
              name="servicegroup"
              value={formData.servicegroup}
              onChange={handleInputChange}
              placeholder="서비스그룹"
            />
          </div>

          <div className="form-group">
            <label htmlFor="service">서비스명</label>
            <input
              type="text"
              id="service"
              name="service"
              value={formData.service}
              onChange={handleInputChange}
              placeholder="서비스명"
            />
          </div>

          <div className="form-group">
            <label htmlFor="datastring">데이터</label>
            <input
              type="text"
              id="datastring"
              name="datastring"
              value={formData.datastring || ''}
              onChange={handleInputChange}
              placeholder="데이터 검색"
              autoComplete="off"
            />
          </div>

          <div className="form-group">
            <label htmlFor="headerstring">헤더</label>
            <input
              type="text"
              id="headerstring"
              name="headerstring"
              value={formData.headerstring}
              onChange={handleInputChange}
              placeholder="헤더 검색"
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

export default ImageLogSearchForm;

