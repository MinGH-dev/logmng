/**
 * 메뉴 트리 구조 (AppSidebar, PermissionGroupPanel 등에서 공유)
 * specs/permission-group-hierarchy.spec.yaml §4, docs/contract.md
 */
import {
  Search as SearchIcon,
  History as HistoryIcon,
  BarChart as BarChartIcon,
  Settings as SettingsIcon,
  Search as SearchSecondIcon,
  History as HistorySecondIcon,
  List as ListIcon,
  PendingActions as PendingIcon,
  Assessment as AssessmentIcon,
  People as PeopleIcon,
  Business as BusinessIcon,
} from '@mui/icons-material';

/** 허용 화면 ID 목록 (spec §4.1). user-permission-hierarchy: redirect to user-management (Option B). */
export const ALLOWED_SCREEN_IDS = [
  'main',
  'search-history',
  'activity-log',
  'statistics',
  'pending-approvals',
  'user-management',
  'department-approvers',
  'user-permission-hierarchy',
];

/** 2-depth 메뉴 트리 (1차: 그룹, 2차: leaf screens) */
export const MENU_TREE = [
  {
    id: 'log-search',
    label: '로그 검색',
    icon: SearchIcon,
    children: [
      { id: 'search-main', label: '검색하기', view: 'main' },
      { id: 'search-history', label: '검색 이력', view: 'search-history' },
    ],
  },
  {
    id: 'history',
    label: '이력·승인',
    icon: HistoryIcon,
    children: [
      { id: 'activity-log', label: '활동 이력', view: 'activity-log' },
      { id: 'pending-approvals', label: '승인 대기', view: 'pending-approvals' },
    ],
  },
  {
    id: 'statistics',
    label: '통계',
    icon: BarChartIcon,
    children: [{ id: 'statistics-view', label: '활동로그 통계', view: 'statistics' }],
  },
  {
    id: 'admin',
    label: '관리',
    icon: SettingsIcon,
    adminOnly: true,
    children: [
      { id: 'user-management', label: '사용자 관리', view: 'user-management' },
      { id: 'department-approvers', label: '부서별 결재자', view: 'department-approvers' },
    ],
  },
];

export const SECOND_ICONS = {
  'search-main': SearchSecondIcon,
  'search-history': HistorySecondIcon,
  'activity-log': ListIcon,
  'pending-approvals': PendingIcon,
  'statistics-view': AssessmentIcon,
  'user-management': PeopleIcon,
  'department-approvers': BusinessIcon,
};
