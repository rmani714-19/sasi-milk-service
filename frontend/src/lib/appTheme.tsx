import { createContext, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import CssBaseline from '@mui/material/CssBaseline';
import { ThemeProvider } from '@mui/material/styles';
import { buildTheme, type ThemeMode } from '../theme';

const MODE_KEY = 'sasi.themeMode';
const MANUAL_KEY = 'sasi.themeManual';

/** Morning look 6am–1pm, evening after — same rule as the collection shift. */
export function modeForNow(d = new Date()): ThemeMode {
  const h = d.getHours();
  return h >= 6 && h < 13 ? 'morning' : 'evening';
}

function readInitial(): { mode: ThemeMode; manual: boolean } {
  try {
    const manual = localStorage.getItem(MANUAL_KEY) === '1';
    const stored = localStorage.getItem(MODE_KEY);
    if (manual && (stored === 'morning' || stored === 'evening')) {
      return { mode: stored, manual: true };
    }
  } catch {
    /* ignore */
  }
  return { mode: modeForNow(), manual: false };
}

interface AppThemeState {
  mode: ThemeMode;
  auto: boolean;
  toggle: () => void;
  resetAuto: () => void;
}

const AppThemeContext = createContext<AppThemeState | null>(null);

export function AppThemeProvider({ children }: { children: ReactNode }) {
  const initial = useRef(readInitial()).current;
  const [mode, setMode] = useState<ThemeMode>(initial.mode);
  const manual = useRef(initial.manual);

  useEffect(() => {
    const tick = () => {
      if (!manual.current) setMode(modeForNow());
    };
    const t = setInterval(tick, 60_000);
    return () => clearInterval(t);
  }, []);

  const persist = (m: ThemeMode, isManual: boolean) => {
    try {
      localStorage.setItem(MANUAL_KEY, isManual ? '1' : '0');
      localStorage.setItem(MODE_KEY, m);
    } catch {
      /* ignore */
    }
  };

  const value = useMemo<AppThemeState>(
    () => ({
      mode,
      auto: !manual.current,
      toggle: () => {
        manual.current = true;
        setMode((m) => {
          const next = m === 'morning' ? 'evening' : 'morning';
          persist(next, true);
          return next;
        });
      },
      resetAuto: () => {
        manual.current = false;
        const now = modeForNow();
        persist(now, false);
        setMode(now);
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
