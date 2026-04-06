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
  LabelOutlined,
  FactCheck as FactCheckIcon,
} from '@mui/icons-material';

/** 허용 화면 ID 목록 (spec §4.1). 로그 검색: pb-feplog, pb-fep-log-search, java-fw-imagelog. */
export const ALLOWED_SCREEN_IDS = [
  'pb-feplog',
  'pb-fep-log-search',
  'java-fw-imagelog',
  'search-history',
  'activity-log',
  'activity-log-access-audit',
  'statistics',
  'pending-approvals',
  'user-management',
  'user-permission-hierarchy',
  'permission-group-management',
  'screen-display-labels',
];

/** 2-depth 메뉴 트리 (1차: 그룹, 2차: leaf screens). 로그 검색: PB FEP v1.0.0, PB FEP v2.0.0, Java FW Image Log; 이력·승인 = 활동 이력 → 검색 이력 → 복호화 승인 관리. */
export const MENU_TREE = [
  {
    id: 'log-search',
    label: '로그 검색',
    icon: SearchIcon,
    children: [
      { id: 'pb-feplog', label: 'PB FEP v1.0.0', view: 'pb-feplog' },
      { id: 'pb-fep-log-search', label: 'PB FEP v2.0.0', view: 'pb-fep-log-search' },
      { id: 'java-fw-imagelog', label: 'Java FW Image Log', view: 'java-fw-imagelog' },
    ],
  },
  {
    id: 'history',
    label: '이력·승인',
    icon: HistoryIcon,
    children: [
      { id: 'activity-log', label: '활동 이력', view: 'activity-log' },
      {
        id: 'activity-log-access-audit',
        label: '활동 로그 접근 감사',
        view: 'activity-log-access-audit',
      },
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
      { id: 'permission-group-management', label: '권한 그룹 관리 v1.0.0', view: 'permission-group-management' },
      { id: 'permission-group-screen-matrix', label: '권한 그룹 관리 v2.0.0', view: 'permission-group-screen-matrix' },
      {
        id: 'screen-display-labels',
        label: '화면 표시 이름',
        view: 'screen-display-labels',
        systemAdminOnly: true,
      },
    ],
  },
];

/** Top-level group ids in MENU_TREE order — parent dropdown / API allowlist (req 20260407-screen-menu-parent-order). */
export const PARENT_GROUP_IDS = ['log-search', 'history', 'statistics', 'admin'];

export const SECOND_ICONS = {
  'pb-feplog': SearchSecondIcon,
  'pb-fep-log-search': SearchSecondIcon,
  'java-fw-imagelog': SearchSecondIcon,
  'search-history': HistorySecondIcon,
  'activity-log': ListIcon,
  'activity-log-access-audit': FactCheckIcon,
  'pending-approvals': PendingIcon,
  'statistics-view': AssessmentIcon,
  'user-management': PeopleIcon,
  'permission-group-management': GroupWorkIcon,
  'permission-group-screen-matrix': TableChartIcon,
  'screen-display-labels': LabelOutlined,
};

/**
 * Admin “화면 표시 이름” form rows — spec menu-display-labels whitelist + matrix leaf.
 * Server may reject unknown ids; order is UX-only.
 */
export const SCREEN_DISPLAY_LABEL_FORM_IDS = [
  'main',
  'pb-feplog',
  'pb-fep-log-search',
  'java-fw-imagelog',
  'search-history',
  'activity-log',
  'activity-log-access-audit',
  'statistics',
  'pending-approvals',
  'user-management',
  'department-approvers',
  'user-permission-hierarchy',
  'permission-group-management',
  'permission-group-screen-matrix',
];

/** Defaults for screen ids not present as MENU_TREE leaves (spec extras). */
export const SCREEN_DISPLAY_LABEL_DEFAULTS_EXTRA = {
  main: '메인',
  'department-approvers': '부서 결재자',
  'user-permission-hierarchy': '사용자 권한 계층',
};

/**
 * User-facing default label for a screenId (hardcoded tree + extras).
 * @param {string} screenId
 */
export function getDefaultScreenLabelForScreenId(screenId) {
  for (const node of MENU_TREE) {
    for (const child of node.children || []) {
      if (child.view === screenId) return child.label;
    }
  }
  return SCREEN_DISPLAY_LABEL_DEFAULTS_EXTRA[screenId] ?? screenId;
}

/** Default top-level group id for a leaf `view`, or undefined if not a MENU_TREE leaf. */
export function getDefaultParentGroupIdForScreenId(screenId) {
  for (const node of MENU_TREE) {
    for (const child of node.children || []) {
      if (child.view === screenId) return node.id;
    }
  }
  return undefined;
}

/** Default sibling index for a leaf under its MENU_TREE parent, or 0 if unknown. */
export function getDefaultSortOrderForScreenId(screenId) {
  for (const node of MENU_TREE) {
    const children = node.children || [];
    for (let i = 0; i < children.length; i += 1) {
      if (children[i].view === screenId) return i;
    }
  }
  return 0;
}

/** Ordered screen IDs for first-allowed-screen (menu order). req 20260318. */
export const ORDERED_SCREEN_IDS = [
  'pb-feplog',
  'pb-fep-log-search',
  'java-fw-imagelog',
  'search-history',
  'activity-log',
  'activity-log-access-audit',
  'pending-approvals',
  'statistics',
  'user-management',
  'user-permission-hierarchy',
  'permission-group-management',
  'screen-display-labels',
];
