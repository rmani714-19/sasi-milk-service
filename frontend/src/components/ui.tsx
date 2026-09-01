import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from 'react';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import CircularProgress from '@mui/material/CircularProgress';
import Snackbar from '@mui/material/Snackbar';
import Typography from '@mui/material/Typography';
import type { UseQueryResult } from '@tanstack/react-query';
import { errorMessage } from '../lib/api';

export function Loading({ label }: { label?: string }) {
  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 2, py: 6 }}>
      <CircularProgress />
      {label && <Typography color="text.secondary">{label}</Typography>}
    </Box>
  );
}

export function ErrorBox({ error }: { error: unknown }) {
  return <Alert severity="error" sx={{ my: 2 }}>{errorMessage(error)}</Alert>;
}

export function Empty({ label = 'Nothing here yet' }: { label?: string }) {
  return (
    <Box sx={{ textAlign: 'center', py: 6, color: 'text.secondary' }}>
      <Typography>{label}</Typography>
    </Box>
  );
}

/** Render loading / error / empty / content for a react-query result. */
export function QueryBoundary<T>({
  query,
  children,
  empty,
}: {
  query: UseQueryResult<T>;
  children: (data: T) => ReactNode;
  empty?: (data: T) => boolean;
}) {
  if (query.isLoading) return <Loading />;
  if (query.isError) return <ErrorBox error={query.error} />;
  if (query.data === undefined) return <Empty />;
  if (empty?.(query.data)) return <Empty />;
  return <>{children(query.data)}</>;
}

// ---- toast ----

type ToastFn = (message: string) => void;
interface ToastApi {
  success: ToastFn;
  error: ToastFn;
  fromError: (err: unknown) => void;
}
const ToastContext = createContext<ToastApi | null>(null);

export function ToastProvider({ children }: { children: ReactNode }) {
  const [msg, setMsg] = useState<{ text: string; severity: 'success' | 'error' } | null>(null);

  const api = useMemo<ToastApi>(
    () => ({
      success: (text) => setMsg({ text, severity: 'success' }),
      error: (text) => setMsg({ text, severity: 'error' }),
      fromError: (err) => setMsg({ text: errorMessage(err), severity: 'error' }),
    }),
    [],
  );

  const close = useCallback(() => setMsg(null), []);

  return (
    <ToastContext.Provider value={api}>
      {children}
      <Snackbar
        open={!!msg}
        autoHideDuration={4000}
        onClose={close}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}
      >
        {msg ? (
          <Alert severity={msg.severity} onClose={close} variant="filled" sx={{ width: '100%' }}>
            {msg.text}
          </Alert>
        ) : undefined}
      </Snackbar>
    </ToastContext.Provider>
  );
}

export function useToast(): ToastApi {
  const ctx = useContext(ToastContext);
  if (!ctx) throw new Error('useToast must be used within ToastProvider');
  return ctx;
}
