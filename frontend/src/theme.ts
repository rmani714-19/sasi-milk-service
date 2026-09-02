import { createTheme, type Theme } from '@mui/material/styles';

export type ThemeMode = 'morning' | 'evening';

/**
 * SASI MILK SERVICE design system.
 * One centralised palette (Milk Green + Milk Blue) with semantic colours.
 * `morning` and `evening` are two deliberately different light skins so the
 * operator can *feel* which collection shift is active — morning is bright and
 * warm, evening is dim, cool and green-filled. All colours come from here.
 */

const FONT = '"Inter", "Roboto", "Segoe UI", Arial, sans-serif';

// --- brand + semantic tokens (shared by both variants) ---
const BRAND = {
  primary: '#2E7D5B',
  primaryDark: '#1F5A41',
  primaryLight: '#E8F4EE',
  secondary: '#3B82A0',
  success: '#2E7D32',
  warning: '#C58A00',
  error: '#C62828',
  info: '#2563A6',
  textPrimary: '#17231E',
  textSecondary: '#5F6F68',
  textMuted: '#87948E',
  border: '#DDE5E1',
  surface: '#FFFFFF',
  surfaceAlt: '#F1F5F3',
  tableHeadBg: '#F1F5F3',
  tableHeadText: '#34443D',
  zebra: '#FAFCFB',
  rowHover: '#E8F4EE',
};

interface Variant {
  /** solid fallback (also used by MUI internals) — the settled bottom tone of the gradient */
  pageBg: string;
  /** full sky gradient painted on <body> */
  pageGradient: string;
  paper: string;
  border: string;
  appBarBg: string;
  appBarText: string;
  appBarBorder: string;
  /** shift badge shown in the app bar */
  badgeBg: string;
  badgeText: string;
}

interface ModeAccents {
  navBg: string;
  navActiveBg: string;
  navActiveFg: string;
  navHoverBg: string;
  badgeBg: string;
  badgeText: string;
  label: string;
}

const VARIANTS: Record<ThemeMode, Variant & ModeAccents> = {
  // day — sky blue overhead with a yellow sun-glow at the horizon
  morning: {
    pageBg: '#F6F8F5',
    pageGradient:
      'linear-gradient(180deg, #2E9FDE 0%, #6FB9E7 6%, #ADD8EF 12%, #E6EFE0 18%, #FCE7A6 24%, #FBF2CE 33%, #F7F8F2 55%, #F6F8F5 100%)',
    paper: '#EAF3FB',
    border: '#D2E3EF',
    appBarBg: 'rgba(224, 240, 251, 0.72)',
    appBarText: BRAND.textPrimary,
    appBarBorder: 'rgba(90, 150, 195, 0.30)',
    navBg: 'rgba(233, 243, 251, 0.88)',
    navActiveBg: '#D7EBF8',
    navActiveFg: '#1B6C9E',
    navHoverBg: '#E7F1F8',
    badgeBg: '#FFDD6B',
    badgeText: '#7A5B00',
    label: 'Morning shift',
  },
  // dusk — burnt orange sky up top settling into warm brown / tan
  evening: {
    pageBg: '#EFE9E1',
    pageGradient:
      'linear-gradient(180deg, #E8781F 0%, #C9611F 4%, #97481F 8%, #7B4A31 12%, #B08862 17%, #DAC8B2 24%, #ECE5DB 40%, #EFE9E1 100%)',
    paper: '#F4E9DD',
    border: '#DDCAB4',
    appBarBg: 'rgba(84, 47, 26, 0.55)',
    appBarText: '#F7EFE6',
    appBarBorder: 'rgba(255, 255, 255, 0.14)',
    navBg: 'rgba(244, 233, 221, 0.88)',
    navActiveBg: '#EEDDCA',
    navActiveFg: '#8A4A1E',
    navHoverBg: '#F0E6D9',
    badgeBg: 'rgba(255, 255, 255, 0.20)',
    badgeText: '#F7EFE6',
    label: 'Evening shift',
  },
};

/** the full sky gradient per mode — also used by the login screen */
export const MODE_GRADIENTS: Record<ThemeMode, string> = {
  morning: VARIANTS.morning.pageGradient,
  evening: VARIANTS.evening.pageGradient,
};

// status chip colour pairs — used by <StatusChip/>
export const STATUS_TOKENS: Record<string, { bg: string; fg: string }> = {
  positive: { bg: '#E8F4EE', fg: '#2E7D32' },
  brand: { bg: '#E8F4EE', fg: '#2E7D5B' },
  pending: { bg: '#FFF7E0', fg: '#8A6500' },
  danger: { bg: '#FDECEC', fg: '#C62828' },
  info: { bg: '#E7F0F7', fg: '#2563A6' },
  neutral: { bg: '#F1F5F3', fg: '#5F6F68' },
};

/** Per-mode accents consumed by the layout (shift badge + sidebar skin). */
export const MODE_STYLES: Record<ThemeMode, ModeAccents> = {
  morning: {
    navBg: VARIANTS.morning.navBg,
    navActiveBg: VARIANTS.morning.navActiveBg,
    navActiveFg: VARIANTS.morning.navActiveFg,
    navHoverBg: VARIANTS.morning.navHoverBg,
    badgeBg: VARIANTS.morning.badgeBg,
    badgeText: VARIANTS.morning.badgeText,
    label: VARIANTS.morning.label,
  },
  evening: {
    navBg: VARIANTS.evening.navBg,
    navActiveBg: VARIANTS.evening.navActiveBg,
    navActiveFg: VARIANTS.evening.navActiveFg,
    navHoverBg: VARIANTS.evening.navHoverBg,
    badgeBg: VARIANTS.evening.badgeBg,
    badgeText: VARIANTS.evening.badgeText,
    label: VARIANTS.evening.label,
  },
};

export function buildTheme(mode: ThemeMode): Theme {
  const v = VARIANTS[mode];

  return createTheme({
    palette: {
      mode: 'light',
      primary: { main: BRAND.primary, dark: BRAND.primaryDark, light: BRAND.primaryLight, contrastText: '#FFFFFF' },
      secondary: { main: BRAND.secondary, contrastText: '#FFFFFF' },
      success: { main: BRAND.success },
      warning: { main: BRAND.warning },
      error: { main: BRAND.error },
      info: { main: BRAND.info },
      background: { default: v.pageBg, paper: v.paper },
      divider: v.border,
      text: { primary: BRAND.textPrimary, secondary: BRAND.textSecondary, disabled: BRAND.textMuted },
    },
    shape: { borderRadius: 8 },
    spacing: 8,
    typography: {
      fontFamily: FONT,
      h4: { fontSize: '1.75rem', fontWeight: 700, lineHeight: 1.2, letterSpacing: '-0.01em' },
      h5: { fontSize: '1.25rem', fontWeight: 600, lineHeight: 1.3 },
      h6: { fontSize: '1.125rem', fontWeight: 600, lineHeight: 1.35 },
      subtitle1: { fontSize: '1rem', fontWeight: 600 },
      subtitle2: { fontSize: '0.875rem', fontWeight: 600 },
      body1: { fontSize: '0.9375rem', fontWeight: 400 },
      body2: { fontSize: '0.875rem', fontWeight: 400 },
      caption: { fontSize: '0.75rem', fontWeight: 400 },
      button: { fontSize: '0.875rem', fontWeight: 600, textTransform: 'none' },
      overline: { fontSize: '0.6875rem', fontWeight: 700, letterSpacing: '0.08em' },
    },
    components: {
      MuiCssBaseline: {
        styleOverrides: {
          body: {
            backgroundColor: v.pageBg,
            backgroundImage: v.pageGradient,
            backgroundAttachment: 'fixed',
            backgroundRepeat: 'no-repeat',
            minHeight: '100vh',
            WebkitFontSmoothing: 'antialiased',
            MozOsxFontSmoothing: 'grayscale',
          },
          '#root': { backgroundColor: 'transparent' },
          '::selection': { backgroundColor: BRAND.primaryLight },
        },
      },
      MuiPaper: { styleOverrides: { root: { backgroundImage: 'none' } } },
      MuiButton: {
        defaultProps: { disableElevation: true },
        styleOverrides: {
          root: { borderRadius: 8, minHeight: 40, paddingInline: 16 },
          sizeLarge: { minHeight: 44, fontSize: '0.9375rem' },
          sizeSmall: { minHeight: 32, paddingInline: 12 },
          containedPrimary: { '&:hover': { backgroundColor: BRAND.primaryDark } },
          outlined: { borderColor: v.border },
        },
      },
      MuiIconButton: { styleOverrides: { root: { borderRadius: 8 } } },
      MuiToggleButton: {
        styleOverrides: {
          root: {
            textTransform: 'none',
            fontWeight: 600,
            borderColor: v.border,
            '&.Mui-selected': {
              backgroundColor: BRAND.primaryLight,
              color: BRAND.primaryDark,
              '&:hover': { backgroundColor: BRAND.primaryLight },
            },
          },
        },
      },
      MuiTextField: { defaultProps: { size: 'small', fullWidth: true } },
      MuiOutlinedInput: {
        styleOverrides: {
          root: {
            borderRadius: 8,
            backgroundColor: BRAND.surface,
            '& .MuiOutlinedInput-notchedOutline': { borderColor: v.border },
            '&:hover .MuiOutlinedInput-notchedOutline': { borderColor: BRAND.textMuted },
            '&.Mui-focused .MuiOutlinedInput-notchedOutline': { borderColor: BRAND.primary, borderWidth: 2 },
          },
        },
      },
      MuiInputLabel: { styleOverrides: { root: { fontSize: '0.8125rem', fontWeight: 500 } } },
      MuiFormHelperText: { styleOverrides: { root: { marginLeft: 2 } } },
      MuiCard: {
        styleOverrides: {
          root: {
            backgroundColor: '#FFFFFF',
            border: `1px solid ${v.border}`,
            borderRadius: 12,
            boxShadow: '0 1px 2px rgba(23, 35, 30, 0.06), 0 10px 28px rgba(23, 35, 30, 0.06)',
          },
        },
      },
      MuiChip: {
        styleOverrides: {
          root: { borderRadius: 8, fontWeight: 600, fontSize: '0.75rem' },
          sizeSmall: { height: 22 },
          outlined: { borderColor: v.border },
        },
      },
      MuiDialog: { styleOverrides: { paper: { borderRadius: 12 } } },
      MuiDialogTitle: { styleOverrides: { root: { fontSize: '1.125rem', fontWeight: 600 } } },
      MuiAppBar: {
        styleOverrides: {
          colorInherit: {
            backgroundColor: v.appBarBg,
            color: v.appBarText,
            borderBottom: `1px solid ${v.appBarBorder}`,
            backdropFilter: 'blur(12px)',
            WebkitBackdropFilter: 'blur(12px)',
          },
        },
      },
      MuiTableCell: {
        styleOverrides: {
          root: { fontSize: '0.8125rem', borderColor: v.border, paddingTop: 10, paddingBottom: 10 },
          head: {
            backgroundColor: BRAND.tableHeadBg,
            color: BRAND.tableHeadText,
            fontWeight: 600,
            fontSize: '0.8125rem',
            whiteSpace: 'nowrap',
          },
        },
      },
      MuiTable: {
        styleOverrides: {
          root: {
            '& tbody .MuiTableRow-root:nth-of-type(even)': { backgroundColor: BRAND.zebra },
            '& tbody .MuiTableRow-root:hover': { backgroundColor: BRAND.rowHover },
          },
        },
      },
      MuiTableContainer: { styleOverrides: { root: { borderRadius: 12 } } },
      MuiTooltip: {
        styleOverrides: {
          tooltip: { fontSize: '0.75rem', backgroundColor: BRAND.textPrimary },
          arrow: { color: BRAND.textPrimary },
        },
      },
      MuiAlert: { styleOverrides: { root: { borderRadius: 8, fontSize: '0.875rem' } } },
    },
  });
}

/** Palette constants for the few places that need raw values (sidebar, print). */
export const PALETTE = BRAND;
