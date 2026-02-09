import React from 'react';
import './App.css';
import LogGrid from './components/LogGrid';

function App() {
  return (
    <div className="App">
      <header className="App-header">
        <h1>로그 관리 시스템</h1>
      </header>
      <main>
        <LogGrid />
      </main>
    </div>
  );
}

export default App; 