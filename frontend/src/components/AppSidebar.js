import React, { useState } from 'react';
import {
  Drawer,
  List,
  ListItemButton,
  ListItemIcon,
  ListItemText,
  Collapse,
  Popover,
  useTheme,
} from '@mui/material';
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
  const [expanded, setExpanded] = useState({});
  const [popoverAnchor, setPopoverAnchor] = useState(null);
  const [popoverParent, setPopoverParent] = useState(null);

  const isActive = (item) => {
    if (item.view === 'main') return currentView === 'main';
    return currentView === item.view;
  };

  const handleFirstLevelClick = (node, e) => {
    if (!open) {
      setPopoverAnchor(e.currentTarget);
      setPopoverParent(node);
    } else {
      setExpanded((prev) => ({ ...prev, [node.id]: !prev[node.id] }));
    }
  };

  const handleSecondLevelClick = (child, e) => {
    if (child.view === 'main') {
      onSearchMain();
    } else {
      onNavigate(child.view);
    }
    setPopoverAnchor(null);
  };

  const closePopover = () => setPopoverAnchor(null);

  const filteredTree = MENU_TREE.filter((node) => !node.adminOnly || isAdmin);

  return (
    <>
      <Drawer
        variant="permanent"
        sx={{
          width: open ? DRAWER_WIDTH_OPEN : DRAWER_WIDTH_COLLAPSED,
          flexShrink: 0,
          '& .MuiDrawer-paper': {
            width: open ? DRAWER_WIDTH_OPEN : DRAWER_WIDTH_COLLAPSED,
            boxSizing: 'border-box',
            transition: theme.transitions.create('width', {
              easing: theme.transitions.easing.sharp,
              duration: theme.transitions.duration.enteringScreen,
            }),
            overflowX: 'hidden',
            mt: 0,
            borderRight: `1px solid ${theme.palette.divider}`,
          },
        }}
      >
        <List disablePadding sx={{ pt: 1 }}>
          {filteredTree.map((node) => {
            const Icon = node.icon;
            const isExpandedOpen = expanded[node.id];
            const hasActiveChild = node.children.some((c) => isActive(c));

            return (
              <React.Fragment key={node.id}>
                <ListItemButton
                  onClick={(e) => handleFirstLevelClick(node, e)}
                  sx={{
                    minHeight: 48,
                    justifyContent: open ? 'initial' : 'center',
                    px: open ? 2 : 1.5,
                    borderLeft: hasActiveChild ? `3px solid ${theme.palette.primary.main}` : '3px solid transparent',
                    bgcolor: hasActiveChild ? 'action.selected' : undefined,
                  }}
                  aria-expanded={open ? isExpandedOpen : undefined}
                >
                  <ListItemIcon sx={{ minWidth: open ? 56 : 0, justifyContent: 'center' }}>
                    <Icon />
                  </ListItemIcon>
                  {open && <ListItemText primary={node.label} primaryTypographyProps={{ noWrap: true }} />}
                </ListItemButton>
                {open && (
                  <Collapse in={isExpandedOpen || hasActiveChild} timeout="auto" unmountOnExit>
                    <List component="div" disablePadding>
                      {node.children.map((child) => {
                        const SecondIcon = SECOND_ICONS[child.id];
                        const active = isActive(child);
                        return (
                          <ListItemButton
                            key={child.id}
                            onClick={() => {
                              if (child.view === 'main') onSearchMain();
                              else onNavigate(child.view);
                            }}
                            sx={{ pl: 4, borderLeft: active ? `3px solid ${theme.palette.primary.main}` : '3px solid transparent', bgcolor: active ? 'action.selected' : undefined }}
                            aria-current={active ? 'page' : undefined}
                          >
                            {SecondIcon && (
                              <ListItemIcon sx={{ minWidth: 40 }}>
                                <SecondIcon fontSize="small" />
                              </ListItemIcon>
                            )}
                            <ListItemText primary={child.label} primaryTypographyProps={{ noWrap: true }} />
                          </ListItemButton>
                        );
                      })}
                    </List>
                  </Collapse>
                )}
              </React.Fragment>
            );
          })}
        </List>
      </Drawer>
      <Popover
        open={Boolean(popoverAnchor)}
        anchorEl={popoverAnchor}
        onClose={closePopover}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
        transformOrigin={{ vertical: 'top', horizontal: 'left' }}
      >
        {popoverParent && (
          <List sx={{ py: 0, minWidth: 180 }}>
            {popoverParent.children.map((child) => {
              const active = isActive(child);
              return (
                <ListItemButton
                  key={child.id}
                  onClick={(e) => handleSecondLevelClick(child, e)}
                  aria-current={active ? 'page' : undefined}
                  selected={active}
                >
                  <ListItemText primary={child.label} />
                </ListItemButton>
              );
            })}
          </List>
        )}
      </Popover>
    </>
  );
}

export default AppSidebar;
export { DRAWER_WIDTH_OPEN, DRAWER_WIDTH_COLLAPSED };
