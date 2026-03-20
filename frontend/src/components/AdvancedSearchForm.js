import React, { useState, useEffect, useRef } from 'react';
import './AdvancedSearchForm.css';
import { getApiBaseUrl } from '../config/runtimeApi';

const AdvancedSearchForm = ({ logType, onSearch }) => {
  const [tokens, setTokens] = useState([]);
  const [inputValue, setInputValue] = useState('');
  const [suggestions, setSuggestions] = useState([]);
  const [showSuggestions, setShowSuggestions] = useState(false);
  const [suggestionIndex, setSuggestionIndex] = useState(-1);
  const [fieldMetadata, setFieldMetadata] = useState([]);
  const [currentContext, setCurrentContext] = useState('field'); // field | operator | value
  const inputRef = useRef(null);
  const suggestionRef = useRef(null);

  // 필드 메타데이터 로드
  useEffect(() => {
    if (logType && logType.id === 'java_fw_imglog') {
      loadFieldMetadata();
    }
  }, [logType]);

  // 필드 메타데이터 로드
  const loadFieldMetadata = async () => {
    try {
      const apiBaseUrl = getApiBaseUrl();
      const response = await fetch(`${apiBaseUrl}/log-types/java_fw_imglog/fields`);
      const result = await response.json();
      
      if (result.success) {
        setFieldMetadata(result.data || []);
      }
    } catch (error) {
      console.error('필드 메타데이터 로드 실패:', error);
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

  // 입력값 변경 처리
  const handleInputChange = (e) => {
    const value = e.target.value;
    setInputValue(value);
    
    // 입력값 파싱하여 컨텍스트 결정
    const context = parseContext(value);
    setCurrentContext(context.context);
    
    // 추천 조회 (디바운스)
    if (value.trim()) {
      clearTimeout(window.suggestionTimeout);
      window.suggestionTimeout = setTimeout(() => {
        fetchSuggestions(context.context, context.prefix, context.field);
      }, 200);
    } else {
      setShowSuggestions(false);
      setSuggestions([]);
    }
  };

  // 입력값 파싱하여 컨텍스트 결정
  const parseContext = (input) => {
    const trimmed = input.trim();
    
    // 마지막 토큰 확인
    const lastToken = tokens.length > 0 ? tokens[tokens.length - 1] : null;
    
    // 마지막 공백 이후의 텍스트
    const lastSpaceIndex = trimmed.lastIndexOf(' ');
    const currentPart = lastSpaceIndex >= 0 ? trimmed.substring(lastSpaceIndex + 1) : trimmed;
    
    // 필드명:값 형식인지 확인
    if (currentPart.includes(':')) {
      const [field, ...valueParts] = currentPart.split(':');
      if (valueParts.length > 0) {
        return {
          context: 'value',
          field: field,
          prefix: valueParts.join(':')
        };
      }
    }
    
    // 마지막 토큰이 필드명이면 연산자 추천
    if (lastToken && lastToken.type === 'field') {
      return {
        context: 'operator',
        field: lastToken.value,
        prefix: currentPart
      };
    }
    
    // 마지막 토큰이 연산자면 값 추천
    if (lastToken && lastToken.type === 'operator') {
      return {
        context: 'value',
        field: lastToken.field,
        prefix: currentPart
      };
    }
    
    // 필드명 추천
    return { context: 'field', prefix: currentPart };
  };

  // 추천 선택
  const handleSuggestionSelect = (suggestion) => {
    const value = suggestion.value;
    const label = suggestion.label;
    
    if (currentContext === 'field') {
      // 필드명 선택 → 연산자 추천
      // 임시 필드 토큰 추가 (나중에 그룹으로 변환)
      addToken({ type: 'field', value: value, label: label });
      setInputValue('');
      setCurrentContext('operator');
      setTimeout(() => fetchSuggestions('operator', '', value), 100);
    } else if (currentContext === 'operator') {
      // 연산자 선택 → 값 추천
      const lastToken = tokens[tokens.length - 1];
      if (lastToken && lastToken.type === 'field') {
        // 필드 토큰을 연산자 정보와 함께 업데이트
        const updatedTokens = [...tokens];
        updatedTokens[updatedTokens.length - 1] = {
          ...lastToken,
          operator: value,
          operatorLabel: label
        };
        setTokens(updatedTokens);
        setInputValue('');
        setCurrentContext('value');
        setTimeout(() => fetchSuggestions('value', '', lastToken.value), 100);
      }
    } else if (currentContext === 'value') {
      // 값 선택 → 필터 그룹 완성
      const lastToken = tokens[tokens.length - 1];
      if (lastToken && lastToken.type === 'field' && lastToken.operator) {
        // 마지막 필드 토큰을 필터 그룹으로 변환
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
      status: 'confirmed'
    };
    setTokens([...tokens, newToken]);
  };

  // 필터 그룹 추가 (필드명 + 연산자 + 값)
  const addFilterGroup = (field, operator, value, fieldLabel, operatorLabel, valueLabel) => {
    const filterGroup = {
      id: Date.now().toString(),
      type: 'filter',
      field: field,
      operator: operator,
      value: value,
      fieldLabel: fieldLabel || field,
      operatorLabel: operatorLabel || operator,
      valueLabel: valueLabel || value,
      status: 'confirmed'
    };
    setTokens([...tokens, filterGroup]);
    setInputValue('');
    setShowSuggestions(false);
    setCurrentContext('field');
  };

  // 토큰 삭제
  const handleTokenDelete = (tokenId) => {
    setTokens(tokens.filter(t => t.id !== tokenId));
  };

  // 토큰 편집
  const handleTokenEdit = (tokenId) => {
    // 토큰을 입력창으로 이동하여 편집
    const token = tokens.find(t => t.id === tokenId);
    if (token) {
      if (token.type === 'filter') {
        // 필터 그룹 편집: 필드명부터 다시 시작
        handleTokenDelete(tokenId);
        // 필드 토큰 추가 (연산자 정보 포함)
        addToken({ 
          type: 'field', 
          value: token.field, 
          label: token.fieldLabel, 
          operator: token.operator, 
          operatorLabel: token.operatorLabel 
        });
        setInputValue(token.value);
        setCurrentContext('value');
        setTimeout(() => {
          fetchSuggestions('value', '', token.field);
          inputRef.current?.focus();
        }, 100);
      } else if (token.type === 'field') {
        // 필드 토큰 편집: 연산자부터 다시 시작
        setInputValue(token.value);
        setCurrentContext('operator');
        handleTokenDelete(tokenId);
        setTimeout(() => {
          fetchSuggestions('operator', '', token.value);
          inputRef.current?.focus();
        }, 100);
      } else {
        // 개별 토큰 편집 (이론적으로는 필터 그룹만 있어야 함)
        handleTokenDelete(tokenId);
        inputRef.current?.focus();
      }
    }
  };

  // 키보드 이벤트 처리
  const handleKeyDown = (e) => {
    if (showSuggestions && suggestions.length > 0) {
      if (e.key === 'ArrowDown') {
        e.preventDefault();
        setSuggestionIndex(prev => 
          prev < suggestions.length - 1 ? prev + 1 : prev
        );
      } else if (e.key === 'ArrowUp') {
        e.preventDefault();
        setSuggestionIndex(prev => prev > 0 ? prev - 1 : -1);
      } else if (e.key === 'Enter') {
        e.preventDefault();
        if (suggestionIndex >= 0 && suggestionIndex < suggestions.length) {
          handleSuggestionSelect(suggestions[suggestionIndex]);
        } else if (suggestions.length > 0) {
          handleSuggestionSelect(suggestions[0]);
        } else {
          // 추천이 없으면 현재 입력값으로 토큰 생성 시도
          handleEnter();
        }
      } else if (e.key === 'Escape') {
        setShowSuggestions(false);
      }
    } else if (e.key === 'Enter') {
      handleEnter();
    } else if (e.key === 'Backspace' && inputValue === '' && tokens.length > 0) {
      // 입력창이 비어있을 때 백스페이스 → 마지막 토큰 삭제
      handleTokenDelete(tokens[tokens.length - 1].id);
    }
  };

  // 엔터 키 처리
  const handleEnter = () => {
    if (inputValue.trim()) {
      // 현재 컨텍스트에 따라 토큰 생성
      if (currentContext === 'field') {
        // 필드명으로 토큰 생성
        const field = fieldMetadata.find(f => 
          f.name.toLowerCase().startsWith(inputValue.toLowerCase()) ||
          f.label.toLowerCase().includes(inputValue.toLowerCase())
        );
        if (field) {
          addToken({ type: 'field', value: field.name, label: field.label });
          setInputValue('');
          setCurrentContext('operator');
          fetchSuggestions('operator', '', field.name);
        }
      } else if (currentContext === 'value') {
        // 값 입력 완료 → 필터 그룹 생성
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
    // 토큰을 AST로 변환
    const filters = buildFiltersFromTokens();
    
    // 오늘 날짜 기본값 설정
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
      filters: filters,
      sort: [
        {
          field: 'insert_time',
          direction: 'desc'
        }
      ],
      pagination: {
        page: 1,
        pageSize: 50
      },
      decryptData: false
    };
    
    onSearch(searchRequest);
  };

  // 토큰에서 필터 생성
  const buildFiltersFromTokens = () => {
    const filters = [];
    
    for (const token of tokens) {
      if (token.type === 'filter') {
        // 필터 그룹 토큰
        filters.push({
          field: token.field,
          operator: token.operator,
          value: token.value
        });
      } else if (token.type === 'field') {
        // 개별 필드 토큰 (미완성 필터)
        const operator = token.operator || ':';
        const value = inputValue.trim();
        if (value) {
          filters.push({
            field: token.value,
            operator: operator,
            value: value
          });
        }
      }
    }
    
    return filters;
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
          {tokens.map(token => (
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
            if (inputValue.trim() && suggestions.length > 0) {
              setShowSuggestions(true);
            }
          }}
          onBlur={() => {
            // 추천 클릭을 위해 약간의 지연
            setTimeout(() => setShowSuggestions(false), 200);
          }}
        />
        {showSuggestions && suggestions.length > 0 && (
          <div className="suggestion-dropdown" ref={suggestionRef}>
            {suggestions.map((suggestion, index) => (
              <div
                key={index}
                className={`suggestion-item ${index === suggestionIndex ? 'selected' : ''}`}
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
        <button className="search-btn" onClick={handleSearch}>
          검색
        </button>
        <button 
          className="clear-btn" 
          onClick={() => {
            setTokens([]);
            setInputValue('');
            setShowSuggestions(false);
          }}
        >
          초기화
        </button>
      </div>
    </div>
  );
};

export default AdvancedSearchForm;

