import React, { useState, useEffect } from 'react';
import SearchForm from './SearchForm';
import LogTable from './LogTable';
import { logDbApi } from '../services/api';
import './LogGrid.css';

const LogGrid = () => {
  const [logs, setLogs] = useState([]);
  const [loading, setLoading] = useState(false);
  const [currentPage, setCurrentPage] = useState(1);
  const [totalPages, setTotalPages] = useState(1);
  const [sortField, setSortField] = useState('log_time');
  const [sortDirection, setSortDirection] = useState('desc');

  // 검색 조건 상태
  const [searchParams, setSearchParams] = useState({
    startDate: '',
    endDate: '',
    media_gb: '',
    tr_code: '',
    loginId: '',
    ipAddress: '',
    sessionId: '',
    deviceType: '',
    includeSend: true,
    includeRecv: true,
    accountNumbers: []
  });

  // 검색 실행 함수
  const handleSearch = async (params) => {
    console.log('🔍 검색 시작:', params);
    setLoading(true);
    setSearchParams(params);
    
    try {
      const searchRequest = {
        ...params,
        page: currentPage,
        pageSize: 10,
        sortField: sortField,
        sortDirection: sortDirection
      };
      
      console.log('📤 API 요청 데이터:', searchRequest);
      
      // DB API 호출
      const result = await logDbApi.searchLogsDB(searchRequest);
      
      console.log('📥 API 응답:', result);
      
      if (result.success) {
        console.log('✅ 검색 성공:', result.data.length, '건');
        setLogs(result.data);
        setTotalPages(result.pagination.totalPages);
        setCurrentPage(result.pagination.currentPage);
      } else {
        console.error('❌ API 오류:', result.error);
      }
    } catch (error) {
      console.error('❌ 검색 중 오류 발생:', error);
    } finally {
      setLoading(false);
    }
  };

  // 정렬 처리
  const handleSort = async (field) => {
    let newSortDirection = 'asc';
    
    if (sortField === field) {
      newSortDirection = sortDirection === 'asc' ? 'desc' : 'asc';
    }
    
    setSortField(field);
    setSortDirection(newSortDirection);
    
    // 현재 검색 조건으로 API 재호출
    if (Object.keys(searchParams).length > 0) {
      try {
        const result = await logDbApi.searchLogsDB({
          ...searchParams,
          page: currentPage,
          pageSize: 10,
          sortField: field,
          sortDirection: newSortDirection
        });
        
        if (result.success) {
          setLogs(result.data);
          setTotalPages(result.pagination.totalPages);
        } else {
          console.error('API 오류:', result.error);
        }
      } catch (error) {
        console.error('정렬 중 오류 발생:', error);
      }
    }
  };

  // 페이지 변경
  const handlePageChange = async (page) => {
    setCurrentPage(page);
    
    // 현재 검색 조건으로 API 재호출
    if (Object.keys(searchParams).length > 0) {
      try {
        const result = await logDbApi.searchLogsDB({
          ...searchParams,
          page: page,
          pageSize: 10,
          sortField: sortField,
          sortDirection: sortDirection
        });
        
        if (result.success) {
          setLogs(result.data);
          setTotalPages(result.pagination.totalPages);
        } else {
          console.error('API 오류:', result.error);
        }
      } catch (error) {
        console.error('페이지 변경 중 오류 발생:', error);
      }
    }
  };



  // 테스트 함수
  const handleTest = async () => {
    console.log('🧪 테스트 시작');
    try {
      const testParams = {
        startDate: '2025-01-01 00:00:00',
        endDate: '2025-01-31 23:59:59',
        media_gb: 'WE',
        tr_code: 'TRANSFER',
        includeSend: true,
        includeRecv: true
      };
      
      console.log('🧪 테스트 파라미터:', testParams);
      const result = await logDbApi.searchLogsDB(testParams);
      console.log('🧪 테스트 결과:', result);
      
      if (result.success) {
        setLogs(result.data);
        setTotalPages(result.pagination.totalPages);
        console.log('🧪 테스트 성공:', result.data.length, '건');
      }
    } catch (error) {
      console.error('🧪 테스트 오류:', error);
    }
  };

  return (
    <div className="log-grid">
      <div style={{ padding: '10px', border: '1px solid #ccc', margin: '10px' }}>
        <h3>테스트 버튼</h3>
        <button onClick={handleTest} style={{ padding: '10px', margin: '5px' }}>
          API 테스트 (WE + TRANSFER)
        </button>
        <button onClick={() => console.log('현재 로그:', logs)} style={{ padding: '10px', margin: '5px' }}>
          현재 로그 상태 확인
        </button>
      </div>
      
      <SearchForm onSearch={handleSearch} />
      <LogTable 
        logs={logs}
        loading={loading}
        sortField={sortField}
        sortDirection={sortDirection}
        onSort={handleSort}
        currentPage={currentPage}
        totalPages={totalPages}
        onPageChange={handlePageChange}
        keywords={searchParams.keywords || []}
      />
    </div>
  );
};

export default LogGrid; 