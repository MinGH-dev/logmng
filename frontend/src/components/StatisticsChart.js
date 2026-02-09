import React from 'react';
import {
  Chart as ChartJS,
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement,
  BarElement,
  Title,
  Tooltip,
  Legend
} from 'chart.js';
import { Line, Bar } from 'react-chartjs-2';
import './StatisticsChart.css';

ChartJS.register(
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement,
  BarElement,
  Title,
  Tooltip,
  Legend
);

const StatisticsChart = ({ statisticsData, statisticsType }) => {
  if (!statisticsData) return null;

  let chartData;
  let chartOptions;

  if (statisticsType === 'daily' || statisticsType === 'monthly') {
    const labels = statisticsData.dailyStats?.map(stat => stat.date) || [];
    const searchData = statisticsData.dailyStats?.map(stat => stat.totalSearches || 0) || [];
    const decryptData = statisticsData.dailyStats?.map(stat => stat.totalDecrypts || 0) || [];
    const loginData = statisticsData.dailyStats?.map(stat => stat.totalLogins || 0) || [];

    chartData = {
      labels,
      datasets: [
        {
          label: '검색 횟수',
          data: searchData,
          borderColor: 'rgb(75, 192, 192)',
          backgroundColor: 'rgba(75, 192, 192, 0.2)',
          tension: 0.1
        },
        {
          label: '복호화 횟수',
          data: decryptData,
          borderColor: 'rgb(255, 99, 132)',
          backgroundColor: 'rgba(255, 99, 132, 0.2)',
          tension: 0.1
        },
        {
          label: '로그인 횟수',
          data: loginData,
          borderColor: 'rgb(54, 162, 235)',
          backgroundColor: 'rgba(54, 162, 235, 0.2)',
          tension: 0.1
        }
      ]
    };

    chartOptions = {
      responsive: true,
      plugins: {
        legend: {
          position: 'top',
        },
        title: {
          display: true,
          text: statisticsType === 'daily' ? '일별 통계' : '월별 통계'
        }
      },
      scales: {
        y: {
          beginAtZero: true
        }
      }
    };
  } else if (statisticsType === 'user') {
    // 사용자별 통계
    const labels = statisticsData.dailyStats?.map(stat => stat.date) || [];
    const searchData = statisticsData.dailyStats?.map(stat => stat.searchCount || 0) || [];
    const decryptData = statisticsData.dailyStats?.map(stat => stat.decryptCount || 0) || [];
    const loginData = statisticsData.dailyStats?.map(stat => stat.loginCount || 0) || [];

    chartData = {
      labels,
      datasets: [
        {
          label: '검색 횟수',
          data: searchData,
          borderColor: 'rgb(75, 192, 192)',
          backgroundColor: 'rgba(75, 192, 192, 0.2)',
          tension: 0.1
        },
        {
          label: '복호화 횟수',
          data: decryptData,
          borderColor: 'rgb(255, 99, 132)',
          backgroundColor: 'rgba(255, 99, 132, 0.2)',
          tension: 0.1
        },
        {
          label: '로그인 횟수',
          data: loginData,
          borderColor: 'rgb(54, 162, 235)',
          backgroundColor: 'rgba(54, 162, 235, 0.2)',
          tension: 0.1
        }
      ]
    };

    chartOptions = {
      responsive: true,
      plugins: {
        legend: {
          position: 'top',
        },
        title: {
          display: true,
          text: `사용자별 통계 (${statisticsData.userId})`
        }
      },
      scales: {
        y: {
          beginAtZero: true
        }
      }
    };
  }

  if (!chartData || !chartOptions) {
    return <div className="no-chart-data">차트 데이터가 없습니다.</div>;
  }

  return (
    <div className="statistics-chart">
      <Line data={chartData} options={chartOptions} />
    </div>
  );
};

export default StatisticsChart;





