import React, { useState, useEffect } from 'react';
import './SearchForm.css';

const pad2 = (n) => String(n).padStart(2, '0');

const defaultRangeForToday = () => {
  const d = new Date();
  const y = d.getFullYear();
  const m = pad2(d.getMonth() + 1);
  const day = pad2(d.getDate());
  return {
    inquiryDate: `${y}-${m}-${day}`,
    startTime: '09:00',
    endTime: '23:59',
  };
};

/** Parse "yyyy-MM-dd HH:mm:ss" or ISO-ish into date + HH:mm for time inputs. */
const parseApiDatetimeToParts = (s) => {
  if (!s) return { date: '', time: '' };
  const str = String(s).trim();
  const normalized = str.includes(' ') ? str.replace(' ', 'T') : str;
  const m = normalized.match(/^(\d{4}-\d{2}-\d{2})[T ](\d{2}):(\d{2})/);
  if (m) return { date: m[1], time: `${m[2]}:${m[3]}` };
  return { date: '', time: '' };
};

const storedParamsToFormFields = (stored) => {
  if (!stored || typeof stored !== 'object') return null;
  const kw = stored.keywords;
  const keywordsStr = Array.isArray(kw) ? kw.join(', ') : kw != null ? String(kw) : '';
  const tr = stored.tr_code != null ? String(stored.tr_code) : stored.trCode != null ? String(stored.trCode) : '';
  const startParts = parseApiDatetimeToParts(stored.startDate);
  const endParts = parseApiDatetimeToParts(stored.endDate);
  const inquiryDate = startParts.date || endParts.date;
  return {
    inquiryDate: inquiryDate || defaultRangeForToday().inquiryDate,
    startTime: startParts.time || defaultRangeForToday().startTime,
    endTime: endParts.time || defaultRangeForToday().endTime,
    loginId: stored.loginId != null ? String(stored.loginId) : '',
    trCode: tr,
    keywords: keywordsStr,
  };
};

/**
 * PB FEP 로그 검색 화면(pb-fep-log-search) 전용 — 컴팩트 단일 행 (req 20260326 wireframe / notes v11).
 * PB FEP Log(pb-feplog)는 SearchFormLegacy 사용.
 */
const SearchForm = ({ onSearch, initialFromSearchParams = null }) => {
  const initial = defaultRangeForToday();
  const [formData, setFormData] = useState({
    inquiryDate: initial.inquiryDate,
    startTime: initial.startTime,
    endTime: initial.endTime,
    loginId: '',
    trCode: '',
    keywords: '',
  });

  useEffect(() => {
    const patch = storedParamsToFormFields(initialFromSearchParams);
    if (patch) {
      setFormData((prev) => ({
        ...prev,
        ...patch,
        inquiryDate: patch.inquiryDate || prev.inquiryDate,
        startTime: patch.startTime || prev.startTime,
        endTime: patch.endTime || prev.endTime,
      }));
    }
  }, [initialFromSearchParams]);

  const [errors, setErrors] = useState({});

  const handleInputChange = (e) => {
    const { name, value } = e.target;
    setFormData((prev) => ({ ...prev, [name]: value }));
    if (errors[name]) {
      setErrors((prev) => ({ ...prev, [name]: '' }));
    }
  };

  const composeBounds = (inquiryDate, startTime, endTime) => {
    if (!inquiryDate || !startTime || !endTime) return { startDt: null, endDt: null };
    const st = String(startTime).trim();
    const et = String(endTime).trim();
    const startDt = new Date(`${inquiryDate}T${st.length === 5 ? `${st}:00` : st}`);
    let endDt = new Date(`${inquiryDate}T${et.length === 5 ? `${et}:59` : et}`);
    if (et.length === 5) {
      endDt.setSeconds(59, 0);
    }
    return { startDt: Number.isNaN(startDt.getTime()) ? null : startDt, endDt: Number.isNaN(endDt.getTime()) ? null : endDt };
  };

  const handleSubmit = (e) => {
    e.preventDefault();

    const newErrors = {};
    if (!formData.inquiryDate || !String(formData.inquiryDate).trim()) {
      newErrors.inquiryDate = '조회일자는 필수입니다.';
    }
    if (!formData.startTime || !String(formData.startTime).trim()) {
      newErrors.startTime = '시작시간은 필수입니다.';
    }
    if (!formData.endTime || !String(formData.endTime).trim()) {
      newErrors.endTime = '종료시간은 필수입니다.';
    }
    if (!formData.loginId || !String(formData.loginId).trim()) {
      newErrors.loginId = 'Login ID는 필수입니다.';
    }

    const { startDt, endDt } = composeBounds(
      formData.inquiryDate,
      formData.startTime,
      formData.endTime
    );
    if (startDt && endDt && startDt > endDt) {
      newErrors.range = '시작 시각은 종료 시각보다 늦을 수 없습니다.';
    }
    if ((formData.inquiryDate && formData.startTime && formData.endTime) && (!startDt || !endDt)) {
      newErrors.range = newErrors.range || '날짜·시간 형식이 올바르지 않습니다.';
    }

    if (Object.keys(newErrors).length > 0) {
      setErrors(newErrors);
      return;
    }

    const fmt = (d) => {
      const y = d.getFullYear();
      const m = pad2(d.getMonth() + 1);
      const day = pad2(d.getDate());
      const h = pad2(d.getHours());
      const min = pad2(d.getMinutes());
      const sec = pad2(d.getSeconds());
      return `${y}-${m}-${day} ${h}:${min}:${sec}`;
    };

    const keywordsArray = formData.keywords
      ? formData.keywords.split(',').map((k) => k.trim()).filter(Boolean)
      : [];

    const searchParams = {
      tr_code: formData.trCode != null ? String(formData.trCode).trim() : '',
      loginId: formData.loginId.trim(),
      startDate: fmt(startDt),
      endDate: fmt(endDt),
      keywords: keywordsArray,
    };

    onSearch(searchParams);
  };

  const handleReset = () => {
    const r = defaultRangeForToday();
    setFormData({
      inquiryDate: r.inquiryDate,
      startTime: r.startTime,
      endTime: r.endTime,
      loginId: '',
      trCode: '',
      keywords: '',
    });
    setErrors({});
  };

  return (
    <div className="search-form-container search-form-container--pb-fep">
      <form onSubmit={handleSubmit} className="search-form search-form--pb-fep-compact">
        <div className="form-row-single form-row-single--pb-fep">
          <div className="form-group form-group--compact">
            <label htmlFor="inquiryDate">
              조회일자 <span className="required">*</span>
            </label>
            <input
              type="date"
              id="inquiryDate"
              name="inquiryDate"
              value={formData.inquiryDate}
              onChange={handleInputChange}
              className={errors.inquiryDate || errors.range ? 'error' : ''}
              aria-invalid={!!(errors.inquiryDate || errors.range)}
              aria-describedby={errors.range ? 'search-range-error' : errors.inquiryDate ? 'inquiryDate-error' : undefined}
            />
            {errors.inquiryDate && (
              <span id="inquiryDate-error" className="error-message" role="alert">{errors.inquiryDate}</span>
            )}
          </div>

          <div className="form-group form-group--compact">
            <label htmlFor="startTime">
              시작시간 <span className="required">*</span>
            </label>
            <input
              type="time"
              id="startTime"
              name="startTime"
              step={60}
              value={formData.startTime}
              onChange={handleInputChange}
              className={errors.startTime || errors.range ? 'error' : ''}
              aria-invalid={!!(errors.startTime || errors.range)}
            />
            {errors.startTime && (
              <span id="startTime-error" className="error-message" role="alert">{errors.startTime}</span>
            )}
          </div>

          <div className="form-group form-group--compact">
            <label htmlFor="endTime">
              종료시간 <span className="required">*</span>
            </label>
            <input
              type="time"
              id="endTime"
              name="endTime"
              step={60}
              value={formData.endTime}
              onChange={handleInputChange}
              className={errors.endTime || errors.range ? 'error' : ''}
              aria-invalid={!!(errors.endTime || errors.range)}
            />
            {errors.endTime && (
              <span id="endTime-error" className="error-message" role="alert">{errors.endTime}</span>
            )}
          </div>

          <div className="form-group form-group--compact">
            <label htmlFor="loginId">Login ID <span className="required">*</span></label>
            <input
              type="text"
              id="loginId"
              name="loginId"
              value={formData.loginId}
              onChange={handleInputChange}
              placeholder="Login ID"
              className={errors.loginId ? 'error' : ''}
              aria-invalid={!!errors.loginId}
              aria-describedby={errors.loginId ? 'loginId-error' : undefined}
            />
            {errors.loginId && <span id="loginId-error" className="error-message" role="alert">{errors.loginId}</span>}
          </div>

          <div className="form-group form-group--compact">
            <label htmlFor="trCode">TR Code</label>
            <input
              type="text"
              id="trCode"
              name="trCode"
              value={formData.trCode}
              onChange={handleInputChange}
              placeholder="TR Code"
            />
          </div>

          <div className="form-group form-group--compact form-group--keywords">
            <label htmlFor="keywords">키워드 검색</label>
            <input
              type="text"
              id="keywords"
              name="keywords"
              value={formData.keywords}
              onChange={handleInputChange}
              placeholder="쉼표로 구분 (예: a, b)"
            />
          </div>

          <div className="form-actions form-actions-inline form-actions--pb-fep">
            <button type="submit" className="btn btn-primary btn-compact">
              검색
            </button>
            <button type="button" className="btn btn-secondary btn-compact" onClick={handleReset}>
              초기화
            </button>
          </div>
        </div>
        {errors.range && (
          <p id="search-range-error" className="error-message error-message--row" role="alert">{errors.range}</p>
        )}
      </form>
    </div>
  );
};

export default SearchForm;
