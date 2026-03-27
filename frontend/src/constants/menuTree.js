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
  GroupWork as GroupWorkIcon,
  TableChart as TableChartIcon,
} from '@mui/icons-material';

/** 허용 화면 ID 목록 (spec §4.1). 로그 검색: pb-feplog, pb-fep-log-search, java-fw-imagelog. */
export const ALLOWED_SCREEN_IDS = [
  'pb-feplog',
  'pb-fep-log-search',
  'java-fw-imagelog',
  'search-history',
  'activity-log',
  'statistics',
  'pending-approvals',
  'user-management',
  'user-permission-hierarchy',
  'permission-group-management',
];

/** 2-depth 메뉴 트리 (1차: 그룹, 2차: leaf screens). 로그 검색: PB FEP Log, PB FEP 로그 검색, Java FW Image Log; 이력·승인 = 활동 이력 → 검색 이력 → 복호화 승인 관리. */
export const MENU_TREE = [
  {
    id: 'log-search',
    label: '로그 검색',
    icon: SearchIcon,
    children: [
      { id: 'pb-feplog', label: 'PB FEP Log', view: 'pb-feplog' },
      { id: 'pb-fep-log-search', label: 'PB FEP 로그 검색', view: 'pb-fep-log-search' },
      { id: 'java-fw-imagelog', label: 'Java FW Image Log', view: 'java-fw-imagelog' },
    ],
  },
  {
    id: 'history',
    label: '이력·승인',
    icon: HistoryIcon,
    children: [
      { id: 'activity-log', label: '활동 이력', view: 'activity-log' },
      { id: 'search-history', label: '검색 이력', view: 'search-history' },
      { id: 'pending-approvals', label: '복호화 승인 관리', view: 'pending-approvals' },
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
      { id: 'permission-group-management', label: '권한 그룹 관리', view: 'permission-group-management' },
      { id: 'permission-group-screen-matrix', label: '권한 그룹 — 화면별 기능', view: 'permission-group-screen-matrix' },
    ],
  },
];

export const SECOND_ICONS = {
  'pb-feplog': SearchSecondIcon,
  'pb-fep-log-search': SearchSecondIcon,
  'java-fw-imagelog': SearchSecondIcon,
  'search-history': HistorySecondIcon,
  'activity-log': ListIcon,
  'pending-approvals': PendingIcon,
  'statistics-view': AssessmentIcon,
  'user-management': PeopleIcon,
  'permission-group-management': GroupWorkIcon,
  'permission-group-screen-matrix': TableChartIcon,
};

/** Ordered screen IDs for first-allowed-screen (menu order). req 20260318. */
export const ORDERED_SCREEN_IDS = [
  'pb-feplog',
  'pb-fep-log-search',
  'java-fw-imagelog',
  'search-history',
  'activity-log',
  'pending-approvals',
  'statistics',
  'user-management',
  'user-permission-hierarchy',
  'permission-group-management',
];
