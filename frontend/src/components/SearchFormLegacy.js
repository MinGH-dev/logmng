import React, { useState, useEffect } from 'react';
import './SearchForm.css';

/**
 * PB FEP v1.0.0 (screen pb-feplog) — 레거시 다필드 검색 폼.
 * PB FEP v2.0.0(pb-fep-log-search)은 SearchForm(컴팩트 와이어프레임) 사용.
 */
const getTodayDateTime = (hours, minutes, seconds) => {
  const today = new Date();
  const year = today.getFullYear();
  const month = String(today.getMonth() + 1).padStart(2, '0');
  const day = String(today.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}T${String(hours).padStart(2, '0')}:${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`;
};

const legacyStoredParamsToFormFields = (stored) => {
  if (!stored || typeof stored !== 'object') return null;
  const toDatetimeLocalFromApi = (s) => {
    if (!s) return '';
    const str = String(s).trim();
    if (str.includes(' ')) {
      const normalized = str.replace(' ', 'T');
      return normalized.length >= 19 ? normalized.substring(0, 19) : normalized;
    }
    return str.substring(0, 19);
  };
  const kw = stored.keywords;
  const keywordsStr = Array.isArray(kw) ? kw.join(', ') : kw != null ? String(kw) : '';
  const media =
    stored.media_gb != null
      ? String(stored.media_gb)
      : stored.mediaCode != null
        ? String(stored.mediaCode)
        : '';
  const tr = stored.tr_code != null ? String(stored.tr_code) : stored.trCode != null ? String(stored.trCode) : '';
  return {
    startDate: toDatetimeLocalFromApi(stored.startDate),
    endDate: toDatetimeLocalFromApi(stored.endDate),
    media_gb: media,
    tr_code: tr,
    loginId: stored.loginId != null ? String(stored.loginId) : '',
    keywords: keywordsStr,
  };
};

const SearchFormLegacy = ({ onSearch, initialFromSearchParams = null }) => {
  const [formData, setFormData] = useState({
    startDate: getTodayDateTime(0, 0, 0),
    endDate: getTodayDateTime(23, 59, 59),
    media_gb: '',
    tr_code: '',
    loginId: '',
    keywords: '',
  });

  const [errors, setErrors] = useState({});

  useEffect(() => {
    const patch = legacyStoredParamsToFormFields(initialFromSearchParams);
    if (patch) {
      setFormData((prev) => ({
        ...prev,
        ...patch,
        startDate: patch.startDate || prev.startDate,
        endDate: patch.endDate || prev.endDate,
      }));
    }
  }, [initialFromSearchParams]);

  const handleInputChange = (e) => {
    const { name, value, type, checked } = e.target;
    setFormData((prev) => ({
      ...prev,
      [name]: type === 'checkbox' ? checked : value,
    }));

    if (errors[name] || (name === 'startDate' || name === 'endDate' ? errors.dateRange : false)) {
      setErrors((prev) => {
        const next = { ...prev, [name]: '' };
        if (name === 'startDate' || name === 'endDate') next.dateRange = '';
        return next;
      });
    }
  };

  const handleSubmit = (e) => {
    e.preventDefault();

    const newErrors = {};
    if (!formData.startDate) newErrors.startDate = '시작일시는 필수입니다.';
    if (!formData.endDate) newErrors.endDate = '종료일시는 필수입니다.';
    if (!formData.tr_code) newErrors.tr_code = 'TR Code는 필수입니다.';
    if (formData.startDate && formData.endDate && new Date(formData.startDate) > new Date(formData.endDate)) {
      newErrors.dateRange = '종료일시는 시작일시보다 이전일 수 없습니다.';
    }

    if (Object.keys(newErrors).length > 0) {
      setErrors(newErrors);
      return;
    }

    const keywordsArray = formData.keywords
      ? formData.keywords.split(',').map((keyword) => keyword.trim()).filter(Boolean)
      : [];

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
      startDate: formatDateForAPI(formData.startDate),
      endDate: formatDateForAPI(formData.endDate),
      media_gb: formData.media_gb != null ? String(formData.media_gb).trim() : '',
      tr_code: formData.tr_code != null ? String(formData.tr_code).trim() : '',
      loginId: formData.loginId != null ? String(formData.loginId).trim() : '',
      keywords: keywordsArray,
    };

    onSearch(searchParams);
  };

  const handleReset = () => {
    setFormData({
      startDate: getTodayDateTime(0, 0, 0),
      endDate: getTodayDateTime(23, 59, 59),
      media_gb: '',
      tr_code: '',
      loginId: '',
      keywords: '',
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
              aria-describedby={
                [errors.startDate && 'startDate-error', errors.dateRange && 'search-form-date-range-error']
                  .filter(Boolean)
                  .join(' ') || undefined
              }
            />
            {errors.startDate && (
              <span id="startDate-error" className="error-message" role="alert">
                {errors.startDate}
              </span>
            )}
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
              aria-describedby={
                [errors.endDate && 'endDate-error', errors.dateRange && 'search-form-date-range-error']
                  .filter(Boolean)
                  .join(' ') || undefined
              }
            />
            {errors.endDate && (
              <span id="endDate-error" className="error-message" role="alert">
                {errors.endDate}
              </span>
            )}
            {errors.dateRange && (
              <span id="search-form-date-range-error" className="error-message" role="alert">
                {errors.dateRange}
              </span>
            )}
          </div>

          <div className="form-group">
            <label htmlFor="media_gb">매체코드</label>
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
            {errors.media_gb && (
              <span id="media_gb-error" className="error-message" role="alert">
                {errors.media_gb}
              </span>
            )}
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
            {errors.tr_code && (
              <span id="tr_code-error" className="error-message" role="alert">
                {errors.tr_code}
              </span>
            )}
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
              onChange={handleInputChange}
              placeholder="키워드1, 키워드2, 키워드3 (OR 조건으로 검색)"
            />
          </div>
        </div>

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

export default SearchFormLegacy;
