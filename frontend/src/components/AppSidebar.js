import React, { useState, useMemo } from 'react';
import { Sidebar, Menu, MenuItem, SubMenu } from 'react-pro-sidebar';
import { useTheme } from '@mui/material/styles';
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
  AccountTree as AccountTreeIcon,
  Group as GroupIcon,
} from '@mui/icons-material';

/** §2.1: Submenu indent (24px–32px); use 28px to match 8px grid */
const SUBMENU_INDENT_PX = 28;

const DRAWER_WIDTH_OPEN = 240;
const DRAWER_WIDTH_COLLAPSED = 64;

const MENU_TREE = [
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
      { id: 'user-permission-hierarchy', label: '사용자 권한 계층', view: 'user-permission-hierarchy' },
      { id: 'permission-group-management', label: '권한 그룹 관리', view: 'permission-group-management' },
    ],
  },
];

const SECOND_ICONS = {
  'search-main': SearchSecondIcon,
  'search-history': HistorySecondIcon,
  'activity-log': ListIcon,
  'pending-approvals': PendingIcon,
  'statistics-view': AssessmentIcon,
  'user-management': PeopleIcon,
  'department-approvers': BusinessIcon,
  'user-permission-hierarchy': AccountTreeIcon,
  'permission-group-management': GroupIcon,
};

function AppSidebar({
  open,
  isAdmin,
  currentView,
  onNavigate,
  onSearchMain,
}) {
  const theme = useTheme();

  const isActive = (item) => {
    if (item.view === 'main') return currentView === 'main';
    return currentView === item.view;
  };

  const filteredTree = MENU_TREE.filter((node) => !node.adminOnly || isAdmin);

  /** Controlled open state per SubMenu for aria-expanded (§2.1 a11y) */
  const [openMenus, setOpenMenus] = useState({});
  const getSubMenuOpen = (node) =>
    openMenus[node.id] !== undefined ? openMenus[node.id] : node.children.some((c) => isActive(c));
  const setSubMenuOpen = (nodeId, open) =>
    setOpenMenus((prev) => ({ ...prev, [nodeId]: open }));

  const handleChildClick = (child) => {
    if (child.view === 'main') {
      onSearchMain();
    } else {
      onNavigate(child.view);
    }
  };

  const menuItemStyles = useMemo(
    () => {
      const topLevelPadding = 12;
      return {
        root: {
          fontFamily: theme.typography.fontFamily,
        },
        /** Same alignment for all top-level rows (로그 검색, 이력·승인, 통계, 관리) */
        subMenuRoot: {
          fontFamily: theme.typography.fontFamily,
        },
        button: ({ level, active, rtl }) => {
          const base = {
            fontFamily: theme.typography.fontFamily,
            '&:hover': {
              backgroundColor: theme.palette.action.hover,
            },
          };
          const activeStyle = {
            borderLeft: active ? `3px solid ${theme.palette.primary.main}` : '3px solid transparent',
            backgroundColor: active ? theme.palette.action.selected : undefined,
          };
          if (level === 0) {
            return {
              ...base,
              ...activeStyle,
              paddingLeft: topLevelPadding,
              paddingRight: topLevelPadding,
            };
          }
          /** §2.1: Child indent 24px–32px; same as "이력·승인" children */
          const indentPx = 20 + SUBMENU_INDENT_PX;
          return {
            ...base,
            ...activeStyle,
            ...(rtl ? { paddingRight: indentPx } : { paddingLeft: indentPx }),
          };
        },
        label: {
          fontFamily: theme.typography.fontFamily,
        },
        /** §2.1: Submenu content indent so "검색하기"/"검색 이력" align with "활동 이력"/"승인 대기" */
        subMenuContent: {
          paddingLeft: SUBMENU_INDENT_PX,
        },
      };
    },
    [theme]
  );

  return (
    <Sidebar
      collapsed={!open}
      width={`${DRAWER_WIDTH_OPEN}px`}
      collapsedWidth={`${DRAWER_WIDTH_COLLAPSED}px`}
      backgroundColor={theme.palette.background.paper}
      rootStyles={{
        borderRight: `1px solid ${theme.palette.divider}`,
        height: '100vh',
        overflow: 'hidden',
        position: 'relative',
        display: 'flex',
        flexDirection: 'column',
      }}
      transitionDuration={theme.transitions.duration.enteringScreen}
      aria-label="사이드바 메뉴"
    >
      {/* §6.2: Explicit maxHeight so scroll works regardless of parent flex chain (react-pro-sidebar container height:100% may not get a containing block) */}
      <div
        style={{
          maxHeight: '100vh',
          overflowY: 'auto',
          overflowX: 'hidden',
        }}
      >
        <Menu
          menuItemStyles={menuItemStyles}
          closeOnClick
        >
        {filteredTree.map((node, index) => {
          const Icon = node.icon;
          const hasActiveChild = node.children.some((c) => isActive(c));
          const subMenuOpen = getSubMenuOpen(node);
          const isFirstSubMenu = index === 0;
          return (
            <SubMenu
              key={node.id}
              className={isFirstSubMenu ? 'sidebar-first-submenu' : undefined}
              label={node.label}
              icon={<Icon />}
              defaultOpen={hasActiveChild}
              open={subMenuOpen}
              onOpenChange={(open) => setSubMenuOpen(node.id, open)}
              active={hasActiveChild}
              aria-expanded={subMenuOpen}
              aria-haspopup="menu"
            >
              {node.children.map((child) => {
                const SecondIcon = SECOND_ICONS[child.id];
                const active = isActive(child);
                return (
                  <MenuItem
                    key={child.id}
                    icon={SecondIcon ? <SecondIcon fontSize="small" /> : undefined}
                    active={active}
                    onClick={() => handleChildClick(child)}
                    aria-current={active ? 'page' : undefined}
                  >
                    {child.label}
                  </MenuItem>
                );
              })}
            </SubMenu>
          );
        })}
      </Menu>
      </div>
    </Sidebar>
  );
}

export default AppSidebar;
export { DRAWER_WIDTH_OPEN, DRAWER_WIDTH_COLLAPSED };
