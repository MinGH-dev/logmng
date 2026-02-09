import React, { useState } from 'react';
import './SearchForm.css';

const SearchForm = ({ onSearch }) => {
  const [formData, setFormData] = useState({
    startDate: '',
    endDate: '',
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

    // 에러 제거
    if (errors[name]) {
      setErrors(prev => ({
        ...prev,
        [name]: ''
      }));
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
    if (!formData.media_gb) newErrors.media_gb = '매체코드는 필수입니다.';
    if (!formData.tr_code) newErrors.tr_code = 'TR Code는 필수입니다.';

    if (Object.keys(newErrors).length > 0) {
      setErrors(newErrors);
      return;
    }

    // 키워드 배열로 변환
    const keywordsArray = formData.keywords
      ? formData.keywords.split(',').map(keyword => keyword.trim()).filter(keyword => keyword)
      : [];

    const searchParams = {
      ...formData,
      keywords: keywordsArray
    };

    onSearch(searchParams);
  };

  // 폼 초기화
  const handleReset = () => {
    setFormData({
      startDate: '',
      endDate: '',
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
        <div className="form-row">
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
              className={errors.startDate ? 'error' : ''}
            />
            {errors.startDate && <span className="error-message">{errors.startDate}</span>}
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
              className={errors.endDate ? 'error' : ''}
            />
            {errors.endDate && <span className="error-message">{errors.endDate}</span>}
          </div>
        </div>

        <div className="form-row">
          <div className="form-group">
            <label htmlFor="media_gb">
              매체코드 <span className="required">*</span>
            </label>
            <input
              type="text"
              id="media_gb"
              name="media_gb"
              value={formData.media_gb}
              onChange={handleInputChange}
              placeholder="매체코드를 입력하세요"
              className={errors.media_gb ? 'error' : ''}
            />
            {errors.media_gb && <span className="error-message">{errors.media_gb}</span>}
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
              placeholder="TR Code를 입력하세요"
              className={errors.tr_code ? 'error' : ''}
            />
            {errors.tr_code && <span className="error-message">{errors.tr_code}</span>}
          </div>
        </div>

        <div className="form-row">
          <div className="form-group">
            <label htmlFor="loginId">Login ID</label>
            <input
              type="text"
              id="loginId"
              name="loginId"
              value={formData.loginId}
              onChange={handleInputChange}
              placeholder="Login ID를 입력하세요"
            />
          </div>

          <div className="form-group">
            <label htmlFor="keywords">키워드 검색 (복합 검색)</label>
            <input
              type="text"
              id="keywords"
              name="keywords"
              value={formData.keywords}
              onChange={handleKeywordsChange}
              placeholder="계좌번호, 키워드를 쉼표로 구분하여 입력하세요 (예: 1234567890,이체,부산지점)"
            />
            <small className="form-text">
              계좌번호, 거래유형, 지점명, 담당자명 등을 쉼표로 구분하여 입력하세요
            </small>
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