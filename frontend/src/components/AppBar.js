import React from 'react';
import { AppBar as MuiAppBar, Toolbar, IconButton, Typography, Button, Box } from '@mui/material';
import MenuIcon from '@mui/icons-material/Menu';
import LogoutIcon from '@mui/icons-material/Logout';
import AccountCircleIcon from '@mui/icons-material/AccountCircle';
import { DRAWER_WIDTH_OPEN, DRAWER_WIDTH_COLLAPSED } from './AppSidebar';

function AppBar({ sidebarOpen, onToggleSidebar, teamName, userName, onLogout, onOpenMyPage }) {
  const drawerWidth = sidebarOpen ? DRAWER_WIDTH_OPEN : DRAWER_WIDTH_COLLAPSED;
  const displayName = (userName && String(userName).trim()) ? String(userName).trim() : '사용자';
  const teamPart = (teamName && String(teamName).trim()) ? `[${String(teamName).trim()}] ` : '';
  const greeting = `${teamPart}${displayName}`.trim() || '사용자';
  return (
    <MuiAppBar
      position="fixed"
      sx={{
        left: drawerWidth,
        right: 0,
        width: `calc(100vw - ${drawerWidth}px)`,
        zIndex: (theme) => theme.zIndex.drawer + 1,
        transition: (theme) =>
          theme.transitions.create(['left', 'width'], {
            easing: theme.transitions.easing.sharp,
            duration: theme.transitions.duration.enteringScreen,
          }),
      }}
    >
      <Toolbar>
        <IconButton
          color="inherit"
          aria-label={sidebarOpen ? '사이드바 닫기' : '사이드바 열기'}
          onClick={onToggleSidebar}
          edge="start"
          sx={{ mr: 1 }}
        >
          <MenuIcon />
        </IconButton>
        <Typography variant="h6" component="span" sx={{ flexGrow: 1 }}>
          로그 관리 시스템
        </Typography>
        <Box component="span" sx={{ display: 'inline-flex', alignItems: 'center', mr: 1 }}>
          <Typography variant="body2" component="span" sx={{ mr: 0.5 }}>
            {greeting}
          </Typography>
          {onOpenMyPage ? (
            <IconButton
              color="inherit"
              onClick={onOpenMyPage}
              aria-label="마이페이지"
              title="마이페이지"
              size="small"
              edge={false}
            >
              <AccountCircleIcon fontSize="small" aria-hidden />
            </IconButton>
          ) : null}
        </Box>
        <Button
          color="inherit"
          onClick={onLogout}
          startIcon={<LogoutIcon />}
          aria-label="로그아웃"
        >
          로그아웃
        </Button>
      </Toolbar>
    </MuiAppBar>
  );
}

export default AppBar;
