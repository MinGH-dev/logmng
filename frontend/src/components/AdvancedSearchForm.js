import React, { useState, useEffect, useRef, useMemo, useCallback } from 'react';
import './AdvancedSearchForm.css';
import { getApiBaseUrl } from '../config/runtimeApi';
import {
  parseContext,
  dedupeFieldsByName,
  getCanonicalFieldFragment,
  buildFiltersFromAdvancedInput,
} from './advancedSearchFormUtils';

const AdvancedSearchForm = ({ logType, onSearch }) => {
  const [tokens, setTokens] = useState([]);
  const [inputValue, setInputValue] = useState('');
  const [suggestions, setSuggestions] = useState([]);
  const [showSuggestions, setShowSuggestions] = useState(false);
  const [showFieldPicker, setShowFieldPicker] = useState(false);
  const [suggestionIndex, setSuggestionIndex] = useState(-1);
  const [fieldMetadata, setFieldMetadata] = useState([]);
  const [fieldMetadataLoading, setFieldMetadataLoading] = useState(false);
  const [currentContext, setCurrentContext] = useState('field'); // field | operator | value
  const inputRef = useRef(null);
  const suggestionRef = useRef(null);

  const searchableFieldsList = useMemo(() => dedupeFieldsByName(fieldMetadata), [fieldMetadata]);

  // 필드 메타데이터 로드
  useEffect(() => {
    if (logType && logType.id === 'java_fw_imglog') {
      loadFieldMetadata();
    }
  }, [logType]);

  // 메타데이터가 늦게 도착해도 포커스 중이면 필드 피커를 연다 (TC-04)
  useEffect(() => {
    if (logType?.id !== 'java_fw_imglog' || searchableFieldsList.length === 0) return;
    const el = inputRef.current;
    if (!el || document.activeElement !== el) return;
    if (inputValue.trim()) return;
    const ctx = parseContext(inputValue, tokens, fieldMetadata);
    if (ctx.context === 'field') {
      setShowFieldPicker(true);
    }
  }, [searchableFieldsList.length, logType?.id, inputValue, tokens, fieldMetadata]);

  const loadFieldMetadata = async () => {
    setFieldMetadataLoading(true);
    try {
      const apiBaseUrl = getApiBaseUrl();
      const response = await fetch(`${apiBaseUrl}/log-types/java_fw_imglog/fields`);
      const result = await response.json();

      if (result.success) {
        setFieldMetadata(result.data || []);
      }
    } catch (error) {
      console.error('필드 메타데이터 로드 실패:', error);
    } finally {
      setFieldMetadataLoading(false);
    }
  };

  // 추천 조회
  const fetchSuggestions = async (context, prefix, fieldName) => {
    if (!logType || logType.id !== 'java_fw_imglog') return;

    try {
      const apiBaseUrl = getApiBaseUrl();
      const url = `${apiBaseUrl}/search/suggest?logType=java_fw_imglog&context=${context}&prefix=${encodeURIComponent(prefix || '')}${fieldName ? `&fieldName=${encodeURIComponent(fieldName)}` : ''}`;
      const response = await fetch(url);
      const result = await response.json();

      if (result.success) {
        setSuggestions(result.data || []);
        setShowSuggestions(true);
        setSuggestionIndex(-1);
      }
    } catch (error) {
      console.error('추천 조회 실패:', error);
    }
  };

  const applyCaretAtEnd = useCallback((fragment) => {
    const place = () => {
      const el = inputRef.current;
      if (!el) return;
      const len = fragment.length;
      el.focus();
      try {
        el.setSelectionRange(len, len);
      } catch {
        /* ignore */
      }
    };
    requestAnimationFrame(() => {
      requestAnimationFrame(place);
    });
  }, []);

  const handleFieldMetadataSelect = (meta) => {
    const fragment = getCanonicalFieldFragment(meta);
    setInputValue(fragment);
    setShowFieldPicker(false);
    setShowSuggestions(false);
    setSuggestionIndex(-1);
    const ctx = parseContext(fragment, tokens, fieldMetadata);
    setCurrentContext(ctx.context);
    applyCaretAtEnd(fragment);
  };

  // 입력값 변경 처리
  const handleInputChange = (e) => {
    const value = e.target.value;
    setInputValue(value);

    const context = parseContext(value, tokens, fieldMetadata);
    setCurrentContext(context.context);

    if (value.trim()) {
      setShowFieldPicker(false);
      clearTimeout(window.suggestionTimeout);
      window.suggestionTimeout = setTimeout(() => {
        fetchSuggestions(context.context, context.prefix, context.field);
      }, 200);
    } else {
      setShowSuggestions(false);
      setSuggestions([]);
    }
  };

  // 추천 선택
  const handleSuggestionSelect = (suggestion) => {
    const value = suggestion.value;
    const label = suggestion.label;

    if (currentContext === 'field') {
      addToken({ type: 'field', value, label });
      setInputValue('');
      setCurrentContext('operator');
      setShowFieldPicker(false);
      setTimeout(() => fetchSuggestions('operator', '', value), 100);
    } else if (currentContext === 'operator') {
      const lastToken = tokens[tokens.length - 1];
      if (lastToken && lastToken.type === 'field') {
        const updatedTokens = [...tokens];
        updatedTokens[updatedTokens.length - 1] = {
          ...lastToken,
          operator: value,
          operatorLabel: label,
        };
        setTokens(updatedTokens);
        setInputValue('');
        setCurrentContext('value');
        setTimeout(() => fetchSuggestions('value', '', lastToken.value), 100);
      }
    } else if (currentContext === 'value') {
      const lastToken = tokens[tokens.length - 1];
      if (lastToken && lastToken.type === 'field' && lastToken.operator) {
        const updatedTokens = tokens.slice(0, -1);
        setTokens(updatedTokens);
        addFilterGroup(
          lastToken.value,
          lastToken.operator,
          value,
          lastToken.label,
          lastToken.operatorLabel,
          label
        );
      }
    }
  };

  // 토큰 추가 (개별 토큰)
  const addToken = (token) => {
    const newToken = {
      id: Date.now().toString(),
      ...token,
      status: 'confirmed',
    };
    setTokens([...tokens, newToken]);
  };

  // 필터 그룹 추가 (필드명 + 연산자 + 값)
  const addFilterGroup = (field, operator, value, fieldLabel, operatorLabel, valueLabel) => {
    const filterGroup = {
      id: Date.now().toString(),
      type: 'filter',
      field,
      operator,
      value,
      fieldLabel: fieldLabel || field,
      operatorLabel: operatorLabel || operator,
      valueLabel: valueLabel || value,
      status: 'confirmed',
    };
    setTokens([...tokens, filterGroup]);
    setInputValue('');
    setShowSuggestions(false);
    setShowFieldPicker(false);
    setCurrentContext('field');
  };

  // 토큰 삭제
  const handleTokenDelete = (tokenId) => {
    setTokens(tokens.filter((t) => t.id !== tokenId));
  };

  // 토큰 편집
  const handleTokenEdit = (tokenId) => {
    const token = tokens.find((t) => t.id === tokenId);
    if (token) {
      if (token.type === 'filter') {
        handleTokenDelete(tokenId);
        addToken({
          type: 'field',
          value: token.field,
          label: token.fieldLabel,
          operator: token.operator,
          operatorLabel: token.operatorLabel,
        });
        setInputValue(token.value);
        setCurrentContext('value');
        setTimeout(() => {
          fetchSuggestions('value', '', token.field);
          inputRef.current?.focus();
        }, 100);
      } else if (token.type === 'field') {
        setInputValue(token.value);
        setCurrentContext('operator');
        handleTokenDelete(tokenId);
        setTimeout(() => {
          fetchSuggestions('operator', '', token.value);
          inputRef.current?.focus();
        }, 100);
      } else {
        handleTokenDelete(tokenId);
        inputRef.current?.focus();
      }
    }
  };

  const getDropdownEntries = () => {
    if (showFieldPicker && searchableFieldsList.length > 0) {
      return searchableFieldsList.map((meta) => ({ kind: 'meta', meta }));
    }
    if (!showFieldPicker && showSuggestions && suggestions.length > 0) {
      return suggestions.map((suggestion) => ({ kind: 'api', suggestion }));
    }
    return [];
  };

  const showFieldLoadingOnly =
    showFieldPicker && fieldMetadataLoading && searchableFieldsList.length === 0;
  const showFieldDropdown = showFieldPicker && searchableFieldsList.length > 0;
  const showApiDropdown = !showFieldPicker && showSuggestions && suggestions.length > 0;

  // 키보드 이벤트 처리
  const handleKeyDown = (e) => {
    if (showFieldLoadingOnly && e.key === 'Escape') {
      e.preventDefault();
      setShowFieldPicker(false);
      return;
    }
    const entries = getDropdownEntries();
    if (entries.length > 0) {
      if (e.key === 'ArrowDown') {
        e.preventDefault();
        setSuggestionIndex((prev) => (prev < entries.length - 1 ? prev + 1 : prev));
      } else if (e.key === 'ArrowUp') {
        e.preventDefault();
        setSuggestionIndex((prev) => (prev > 0 ? prev - 1 : -1));
      } else if (e.key === 'Enter') {
        e.preventDefault();
        const idx = suggestionIndex >= 0 ? suggestionIndex : 0;
        const picked = entries[idx];
        if (picked?.kind === 'meta') {
          handleFieldMetadataSelect(picked.meta);
        } else if (picked?.kind === 'api') {
          handleSuggestionSelect(picked.suggestion);
        } else {
          handleEnter();
        }
      } else if (e.key === 'Escape') {
        e.preventDefault();
        setShowSuggestions(false);
        setShowFieldPicker(false);
      }
    } else if (e.key === 'Enter') {
      handleEnter();
    } else if (e.key === 'Backspace' && inputValue === '' && tokens.length > 0) {
      handleTokenDelete(tokens[tokens.length - 1].id);
    }
  };

  // 엔터 키 처리
  const handleEnter = () => {
    if (inputValue.trim()) {
      if (currentContext === 'field') {
        const field = fieldMetadata.find(
          (f) =>
            f.name.toLowerCase().startsWith(inputValue.toLowerCase()) ||
            f.label.toLowerCase().includes(inputValue.toLowerCase())
        );
        if (field) {
          addToken({ type: 'field', value: field.name, label: field.label });
          setInputValue('');
          setCurrentContext('operator');
          setShowFieldPicker(false);
          fetchSuggestions('operator', '', field.name);
        }
      } else if (currentContext === 'value') {
        const lastToken = tokens[tokens.length - 1];
        if (lastToken && lastToken.type === 'field' && lastToken.operator) {
          const updatedTokens = tokens.slice(0, -1);
          setTokens(updatedTokens);
          addFilterGroup(
            lastToken.value,
            lastToken.operator,
            inputValue.trim(),
            lastToken.label,
            lastToken.operatorLabel,
            inputValue.trim()
          );
        }
      }
    }
  };

  // 검색 실행
  const handleSearch = () => {
    const filters = buildFiltersFromAdvancedInput({ tokens, inputValue, fieldMetadata });

    const today = new Date();
    const startDate = new Date(today.getFullYear(), today.getMonth(), today.getDate(), 0, 0, 0);
    const endDate = new Date(today.getFullYear(), today.getMonth(), today.getDate(), 23, 59, 59);

    const formatDate = (date) => {
      const year = date.getFullYear();
      const month = String(date.getMonth() + 1).padStart(2, '0');
      const day = String(date.getDate()).padStart(2, '0');
      const hours = String(date.getHours()).padStart(2, '0');
      const minutes = String(date.getMinutes()).padStart(2, '0');
      const seconds = String(date.getSeconds()).padStart(2, '0');
      return `${year}-${month}-${day} ${hours}:${minutes}:${seconds}`;
    };

    const searchRequest = {
      logType: 'java_fw_imglog',
      startDate: formatDate(startDate),
      endDate: formatDate(endDate),
      queryText: inputValue.trim() || null,
      filters,
      sort: [
        {
          field: 'insert_time',
          direction: 'desc',
        },
      ],
      pagination: {
        page: 1,
        pageSize: 50,
      },
      decryptData: false,
    };

    onSearch(searchRequest);
  };

  // 토큰 타입별 스타일
  const getTokenClassName = (token) => {
    let className = 'search-token';
    if (token.status === 'invalid') {
      className += ' invalid';
    }
    return className;
  };

  return (
    <div className="advanced-search-form">
      <div className="search-input-container">
        <div className="token-list">
          {tokens.map((token) => (
            <span
              key={token.id}
              className={getTokenClassName(token)}
              onClick={() => handleTokenEdit(token.id)}
              title="클릭하여 편집"
            >
              {token.type === 'filter' && (
                <>
                  <span className="filter-field">{token.fieldLabel || token.field}</span>
                  <span className="filter-operator">{token.operatorLabel || token.operator}</span>
                  <span className="filter-value">{token.valueLabel || token.value}</span>
                </>
              )}
              {token.type === 'field' && `${token.label || token.value}:`}
              {token.type === 'operator' && token.label}
              {token.type === 'value' && (token.label || token.value)}
              <button
                type="button"
                className="token-delete"
                onClick={(e) => {
                  e.stopPropagation();
                  handleTokenDelete(token.id);
                }}
              >
                ×
              </button>
            </span>
          ))}
        </div>
        <input
          ref={inputRef}
          type="text"
          className="search-input"
          placeholder="필드명을 입력하세요 (예: status, application)"
          value={inputValue}
          onChange={handleInputChange}
          onKeyDown={handleKeyDown}
          onFocus={() => {
            const ctx = parseContext(inputValue, tokens, fieldMetadata);
            if (
              logType &&
              logType.id === 'java_fw_imglog' &&
              ctx.context === 'field' &&
              !inputValue.trim()
            ) {
              setShowFieldPicker(true);
              setSuggestionIndex(-1);
            }
            if (inputValue.trim() && suggestions.length > 0) {
              setShowSuggestions(true);
            }
          }}
          onBlur={() => {
            setTimeout(() => {
              setShowSuggestions(false);
              setShowFieldPicker(false);
            }, 200);
          }}
          aria-autocomplete="list"
        />
        {showFieldLoadingOnly && (
          <div
            className="suggestion-dropdown"
            id="advanced-search-suggestions"
            ref={suggestionRef}
            role="status"
            data-testid="advanced-search-field-picker-loading"
          >
            <div className="suggestion-item field-picker-loading">필드 목록 로딩 중…</div>
          </div>
        )}
        {showFieldDropdown && (
          <div
            className="suggestion-dropdown"
            id="advanced-search-suggestions"
            ref={suggestionRef}
            role="listbox"
            data-testid="advanced-search-field-picker"
          >
            {searchableFieldsList.map((meta, index) => (
              <div
                key={meta.name}
                role="option"
                aria-selected={index === suggestionIndex}
                data-testid={`field-picker-item-${meta.name}`}
                className={`suggestion-item ${index === suggestionIndex ? 'selected' : ''}`}
                onMouseDown={(e) => e.preventDefault()}
                onClick={() => handleFieldMetadataSelect(meta)}
                onMouseEnter={() => setSuggestionIndex(index)}
              >
                <span className="suggestion-label">{meta.label || meta.name}</span>
                <span className="suggestion-description">{meta.name}</span>
              </div>
            ))}
          </div>
        )}
        {showApiDropdown && (
          <div
            className="suggestion-dropdown"
            id="advanced-search-suggestions"
            ref={suggestionRef}
            role="listbox"
            data-testid="advanced-search-suggest-dropdown"
          >
            {suggestions.map((suggestion, index) => (
              <div
                key={index}
                role="option"
                aria-selected={index === suggestionIndex}
                className={`suggestion-item ${index === suggestionIndex ? 'selected' : ''}`}
                onMouseDown={(e) => e.preventDefault()}
                onClick={() => handleSuggestionSelect(suggestion)}
                onMouseEnter={() => setSuggestionIndex(index)}
              >
                <span className="suggestion-label">{suggestion.label}</span>
                {suggestion.description && (
                  <span className="suggestion-description">{suggestion.description}</span>
                )}
              </div>
            ))}
          </div>
        )}
      </div>
      <div className="search-actions">
        <button type="button" className="search-btn" onClick={handleSearch}>
          검색
        </button>
        <button
          type="button"
          className="clear-btn"
          onClick={() => {
            setTokens([]);
            setInputValue('');
            setShowSuggestions(false);
            setShowFieldPicker(false);
          }}
        >
          초기화
        </button>
      </div>
    </div>
  );
};

export default AdvancedSearchForm;
