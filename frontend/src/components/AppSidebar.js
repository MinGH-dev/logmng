import React, { useState, useMemo, useEffect, useRef } from 'react';
import { Sidebar, Menu, MenuItem, SubMenu } from 'react-pro-sidebar';
import { useTheme } from '@mui/material/styles';
import { MENU_TREE, SECOND_ICONS } from '../constants/menuTree';
import { isHrSyncPocMenuEnabled } from '../config/hrSyncPocUi';
import logger from '../utils/logger';

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
  menuTree = MENU_TREE,
}) {
  const theme = useTheme();
  const sidebarHostRef = useRef(null);

  const isActive = (item) => currentView === item.view;

  const filteredTree = useMemo(() => {
    const ids = Array.isArray(allowedScreenIds) ? allowedScreenIds : [];
    /** Show child when user has access. user-management and permission-group-management also accept user-permission-hierarchy. */
    const canShowChild = (c) => {
      if (c.systemAdminOnly === true && !isAdmin) return false;
      if (c.view === 'hr-sync-poc' && !isHrSyncPocMenuEnabled()) return false;
      if (c.view === 'user-management-v2-poc' && !isHrSyncPocMenuEnabled()) return false;
      if (!c?.view || !ids?.length) return false;
      if (c.view === 'user-management') return ids.includes('user-management') || ids.includes('user-permission-hierarchy');
      if (c.view === 'hr-sync-poc') {
        return ids.includes('user-management') || ids.includes('user-permission-hierarchy');
      }
      if (c.view === 'user-management-v2') {
        return ids.includes('user-management-v2') || ids.includes('user-management') || ids.includes('user-permission-hierarchy');
      }
      if (c.view === 'user-management-v2-poc') {
        return (
          ids.includes('user-management-v2-poc') ||
          ids.includes('hr-sync-poc') ||
          ids.includes('user-management-v2') ||
          ids.includes('user-management') ||
          ids.includes('user-permission-hierarchy')
        );
      }
      if (c.view === 'permission-group-management' || c.view === 'permission-group-screen-matrix') {
        return ids.includes('permission-group-management') || ids.includes('user-permission-hierarchy');
      }
      return ids.includes(c.view);
    };
    return menuTree.filter((node) => {
      if (node.adminOnly && !isAdmin) {
        const hasAnyAllowedChild = node.children?.some((c) => canShowChild(c));
        if (!hasAnyAllowedChild) return false;
      }
      if (isAdmin) return true;
      if (ids.length === 0) return false;
      const hasAnyAllowedChild = node.children.some((c) => canShowChild(c));
      return hasAnyAllowedChild;
    }).map((node) => {
      const stripHrSyncWhenMenuOff = (children) =>
        (children || []).filter(
          (c) =>
            !(
              (c.view === 'hr-sync-poc' || c.view === 'user-management-v2-poc') &&
              !isHrSyncPocMenuEnabled()
            )
        );
      if (isAdmin) {
        return { ...node, children: stripHrSyncWhenMenuOff(node.children) };
      }
      return {
        ...node,
        children: stripHrSyncWhenMenuOff(node.children.filter((c) => canShowChild(c))),
      };
    });
  }, [isAdmin, allowedScreenIds, menuTree]);

  /** Controlled open state per SubMenu for aria-expanded (§2.1 a11y) */
  const [openMenus, setOpenMenus] = useState({});
  const getSubMenuOpen = (node) =>
    openMenus[node.id] !== undefined ? openMenus[node.id] : node.children.some((c) => isActive(c));
  const setSubMenuOpen = (nodeId, open) =>
    setOpenMenus((prev) => ({ ...prev, [nodeId]: open }));

  useEffect(() => {
    const hasOpenSubmenu =
      Object.values(openMenus).some(Boolean) ||
      filteredTree.some((node) =>
        openMenus[node.id] !== undefined
          ? openMenus[node.id]
          : node.children.some((child) => currentView === child.view)
      );
    if (open || !hasOpenSubmenu) return;
    const host = sidebarHostRef.current;
    const sidebarRoot = host?.querySelector('.ps-sidebar-root');
    const submenuContent = host?.querySelector('.ps-submenu-content');
    const appMain = document.querySelector('main');
    if (!sidebarRoot || !appMain) return;
    const sidebarStyles = window.getComputedStyle(sidebarRoot);
    const mainStyles = window.getComputedStyle(appMain);
    const submenuStyles = submenuContent ? window.getComputedStyle(submenuContent) : null;
    logger.debug('Sidebar collapsed submenu layer diagnostic', {
      collapsed: !open,
      hasOpenSubmenu,
      sidebar: {
        overflow: sidebarStyles.overflow,
        overflowX: sidebarStyles.overflowX,
        overflowY: sidebarStyles.overflowY,
        zIndex: sidebarStyles.zIndex,
        position: sidebarStyles.position,
      },
      submenu: submenuStyles
        ? {
            zIndex: submenuStyles.zIndex,
            position: submenuStyles.position,
          }
        : null,
      main: {
        overflowX: mainStyles.overflowX,
        zIndex: mainStyles.zIndex,
        position: mainStyles.position,
      },
    });
  }, [open, openMenus, filteredTree, currentView]);

  const handleChildClick = (child) => {
    onNavigate(child.view);
  };

  const menuItemStyles = useMemo(
    () => {
      const topLevelPadding = 12;
      const isCollapsed = !open;
      const collapsedChildPadding = 0;
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
          /** Expanded mode keeps child indent. Collapsed mode removes extra left gap in popout submenu. */
          const indentPx = 20 + SUBMENU_INDENT_PX;
          if (isCollapsed) {
            return {
              ...base,
              ...activeStyle,
              paddingLeft: collapsedChildPadding,
              paddingRight: collapsedChildPadding,
            };
          }
          return {
            ...base,
            ...activeStyle,
            ...(rtl ? { paddingRight: indentPx } : { paddingLeft: indentPx }),
          };
        },
        label: {
          fontFamily: theme.typography.fontFamily,
        },
        /** Expanded uses indent; collapsed popout removes left whitespace for submenu items. */
        subMenuContent: {
          paddingLeft: isCollapsed ? 0 : SUBMENU_INDENT_PX,
        },
      };
    },
    [theme, open]
  );

  return (
    <div ref={sidebarHostRef}>
      <Sidebar
        collapsed={!open}
        width={`${DRAWER_WIDTH_OPEN}px`}
        collapsedWidth={`${DRAWER_WIDTH_COLLAPSED}px`}
        backgroundColor={theme.palette.background.paper}
        rootStyles={{
          borderRight: `1px solid ${theme.palette.divider}`,
          height: '100vh',
          overflow: 'visible',
          position: 'relative',
          zIndex: theme.zIndex.drawer,
          flexShrink: 0,
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
    </div>
  );
}

export default AppSidebar;
export { DRAWER_WIDTH_OPEN, DRAWER_WIDTH_COLLAPSED };
