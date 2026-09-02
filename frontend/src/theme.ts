import { createTheme, type Theme } from '@mui/material/styles';

export type ThemeMode = 'morning' | 'evening';

const HEADING = '"Poppins", "Segoe UI", system-ui, sans-serif';
const BODY = '"Nunito Sans", "Segoe UI", system-ui, sans-serif';

/**
 * Palette taken from the Sasi Dairy Farm site: dairy green, mustard gold, cream.
 * Morning is warm and light; evening deepens the greens and cools the ground.
 */
const PALETTES: Record<ThemeMode, {
  primary: string;
  primaryDark: string;
  secondary: string;
  bg: string;
  paper: string;
  border: string;
  appBar: string;
  appBarText: string;
}> = {
  morning: {
    primary: '#2f7d33',
    primaryDark: '#1f5d24',
    secondary: '#c9a227',
    bg: '#f8f3e3',
    paper: '#ffffff',
    border: '#e9e1c8',
    appBar: '#fffdf6',
    appBarText: '#1f5d24',
  },
  evening: {
    primary: '#245e28',
    primaryDark: '#14401a',
    secondary: '#b08320',
    bg: '#e9ebe0',
    paper: '#fbfbf6',
    border: '#d6d8c7',
    appBar: '#1f5d24',
    appBarText: '#f6f2e2',
  },
};

export function buildTheme(mode: ThemeMode): Theme {
  const p = PALETTES[mode];
  return createTheme({
    palette: {
      mode: 'light',
      primary: { main: p.primary, dark: p.primaryDark },
      secondary: { main: p.secondary },
      background: { default: p.bg, paper: p.paper },
      divider: p.border,
      text: { primary: '#2b2b26', secondary: '#5f6058' },
    },
    shape: { borderRadius: 12 },
    typography: {
      fontFamily: BODY,
      h4: { fontFamily: HEADING, fontWeight: 700 },
      h5: { fontFamily: HEADING, fontWeight: 700 },
      h6: { fontFamily: HEADING, fontWeight: 700 },
      subtitle1: { fontFamily: HEADING, fontWeight: 600 },
      subtitle2: { fontFamily: HEADING, fontWeight: 600 },
      button: { textTransform: 'none', fontWeight: 600, fontFamily: HEADING },
      overline: { fontFamily: HEADING, fontWeight: 700, letterSpacing: 1 },
    },
    components: {
      MuiButton: { defaultProps: { disableElevation: true } },
      MuiTextField: { defaultProps: { size: 'small', fullWidth: true } },
      MuiPaper: { styleOverrides: { root: { backgroundImage: 'none' } } },
      MuiCard: {
        styleOverrides: {
          root: { border: `1px solid ${p.border}`, boxShadow: '0 1px 2px rgba(31,93,36,0.06)' },
        },
      },
      MuiAppBar: {
        styleOverrides: {
          colorInherit: { backgroundColor: p.appBar, color: p.appBarText },
        },
      },
      MuiTableHead: {
        styleOverrides: {
          root: { '& .MuiTableCell-head': { fontFamily: HEADING, fontWeight: 700, color: '#5f6058' } },
        },
      },
    },
  });
}
