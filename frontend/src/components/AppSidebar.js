import React, { useState, useMemo } from 'react';
import { Sidebar, Menu, MenuItem, SubMenu } from 'react-pro-sidebar';
import { useTheme } from '@mui/material/styles';
import { MENU_TREE, SECOND_ICONS } from '../constants/menuTree';

/** §2.1: Submenu indent (24px–32px); use 28px to match 8px grid */
const SUBMENU_INDENT_PX = 28;

const DRAWER_WIDTH_OPEN = 240;
const DRAWER_WIDTH_COLLAPSED = 64;

function AppSidebar({
  open,
  isAdmin,
  allowedScreenIds = [],
  currentView,
  onNavigate,
}) {
  const theme = useTheme();

  const isActive = (item) => currentView === item.view;

  const filteredTree = useMemo(() => {
    const ids = Array.isArray(allowedScreenIds) ? allowedScreenIds : [];
    /** Show child when user has access. user-management and permission-group-management also accept user-permission-hierarchy. */
    const canShowChild = (c) => {
      if (!c?.view || !ids?.length) return false;
      if (c.view === 'user-management') return ids.includes('user-management') || ids.includes('user-permission-hierarchy');
      if (c.view === 'permission-group-management' || c.view === 'permission-group-screen-matrix') {
        return ids.includes('permission-group-management') || ids.includes('user-permission-hierarchy');
      }
      return ids.includes(c.view);
    };
    return MENU_TREE.filter((node) => {
      if (node.adminOnly && !isAdmin) {
        const hasAnyAllowedChild = node.children?.some((c) => canShowChild(c));
        if (!hasAnyAllowedChild) return false;
      }
      if (isAdmin) return true;
      if (ids.length === 0) return false;
      const hasAnyAllowedChild = node.children.some((c) => canShowChild(c));
      return hasAnyAllowedChild;
    }).map((node) => {
      if (isAdmin) return node;
      return {
        ...node,
        children: node.children.filter((c) => canShowChild(c)),
      };
    });
  }, [isAdmin, allowedScreenIds]);

  /** Controlled open state per SubMenu for aria-expanded (§2.1 a11y) */
  const [openMenus, setOpenMenus] = useState({});
  const getSubMenuOpen = (node) =>
    openMenus[node.id] !== undefined ? openMenus[node.id] : node.children.some((c) => isActive(c));
  const setSubMenuOpen = (nodeId, open) =>
    setOpenMenus((prev) => ({ ...prev, [nodeId]: open }));

  const handleChildClick = (child) => {
    onNavigate(child.view);
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
        /** §2.1: Submenu content indent so "PB FEP Log"/"검색 이력" align with "활동 이력"/"복호화 승인 관리" */
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
