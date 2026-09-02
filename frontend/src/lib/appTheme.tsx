import { createContext, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import CssBaseline from '@mui/material/CssBaseline';
import { ThemeProvider } from '@mui/material/styles';
import { buildTheme, type ThemeMode } from '../theme';

/** Morning look 6am–1pm, evening after — same rule as the collection shift. */
export function modeForNow(d = new Date()): ThemeMode {
  const h = d.getHours();
  return h >= 6 && h < 13 ? 'morning' : 'evening';
}

interface AppThemeState {
  mode: ThemeMode;
  auto: boolean;
  toggle: () => void;
  resetAuto: () => void;
}

const AppThemeContext = createContext<AppThemeState | null>(null);

export function AppThemeProvider({ children }: { children: ReactNode }) {
  const [mode, setMode] = useState<ThemeMode>(modeForNow);
  const manual = useRef(false);

  useEffect(() => {
    const tick = () => {
      if (!manual.current) setMode(modeForNow());
    };
    const t = setInterval(tick, 60_000);
    return () => clearInterval(t);
  }, []);

  const value = useMemo<AppThemeState>(
    () => ({
      mode,
      auto: !manual.current,
      toggle: () => {
        manual.current = true;
        setMode((m) => (m === 'morning' ? 'evening' : 'morning'));
      },
      resetAuto: () => {
        manual.current = false;
        setMode(modeForNow());
      },
    }),
    [mode],
  );

  const theme = useMemo(() => buildTheme(mode), [mode]);

  return (
    <AppThemeContext.Provider value={value}>
      <ThemeProvider theme={theme}>
        <CssBaseline />
        {children}
      </ThemeProvider>
    </AppThemeContext.Provider>
  );
}

export function useAppTheme(): AppThemeState {
  const ctx = useContext(AppThemeContext);
  if (!ctx) throw new Error('useAppTheme must be used within AppThemeProvider');
  return ctx;
}
