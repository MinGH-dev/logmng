import React from 'react';
import { AppBar as MuiAppBar, Toolbar, IconButton, Typography, Button } from '@mui/material';
import MenuIcon from '@mui/icons-material/Menu';
import LogoutIcon from '@mui/icons-material/Logout';

function AppBar({ sidebarOpen, onToggleSidebar, username, onLogout }) {
  return (
    <MuiAppBar position="fixed" sx={{ zIndex: (theme) => theme.zIndex.drawer + 1 }}>
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
        <Typography variant="body2" component="span" sx={{ mr: 1 }}>
          환영합니다, {username || ''}님
        </Typography>
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
