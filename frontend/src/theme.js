import { createTheme } from '@mui/material/styles';

/** Enterprise-style theme: primary blue/grey, restrained background and borders. */
export const appTheme = createTheme({
  palette: {
    primary: { main: '#1976d2' },
    secondary: { main: '#5c6bc0' },
    background: { default: '#f5f5f5', paper: '#ffffff' },
  },
  typography: {
    fontFamily: '"Noto Sans KR", "Roboto", "Helvetica", "Arial", sans-serif',
  },
  shape: { borderRadius: 4 },
});
