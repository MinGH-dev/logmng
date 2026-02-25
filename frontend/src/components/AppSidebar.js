import React from 'react';
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
} from '@mui/icons-material';

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

  const handleChildClick = (child) => {
    if (child.view === 'main') {
      onSearchMain();
    } else {
      onNavigate(child.view);
    }
  };

  const menuItemStyles = {
    root: {
      fontFamily: theme.typography.fontFamily,
    },
    button: ({ level, active }) => {
      const base = {
        fontFamily: theme.typography.fontFamily,
        '&:hover': {
          backgroundColor: theme.palette.action.hover,
        },
      };
      if (level === 0) {
        return {
          ...base,
          borderLeft: active ? `3px solid ${theme.palette.primary.main}` : '3px solid transparent',
          backgroundColor: active ? theme.palette.action.selected : undefined,
        };
      }
      return {
        ...base,
        borderLeft: active ? `3px solid ${theme.palette.primary.main}` : '3px solid transparent',
        backgroundColor: active ? theme.palette.action.selected : undefined,
      };
    },
    label: {
      fontFamily: theme.typography.fontFamily,
    },
  };

  return (
    <Sidebar
      collapsed={!open}
      width={`${DRAWER_WIDTH_OPEN}px`}
      collapsedWidth={`${DRAWER_WIDTH_COLLAPSED}px`}
      backgroundColor={theme.palette.background.paper}
      rootStyles={{
        borderRight: `1px solid ${theme.palette.divider}`,
        height: '100vh',
        position: 'relative',
      }}
      transitionDuration={theme.transitions.duration.enteringScreen}
      aria-label="사이드바 메뉴"
    >
      <Menu
        menuItemStyles={menuItemStyles}
        closeOnClick
      >
        {filteredTree.map((node) => {
          const Icon = node.icon;
          const hasActiveChild = node.children.some((c) => isActive(c));
          return (
            <SubMenu
              key={node.id}
              label={node.label}
              icon={<Icon />}
              defaultOpen={hasActiveChild}
              active={hasActiveChild}
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
    </Sidebar>
  );
}

export default AppSidebar;
export { DRAWER_WIDTH_OPEN, DRAWER_WIDTH_COLLAPSED };
