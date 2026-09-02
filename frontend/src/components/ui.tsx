import {
  createContext,
  useCallback,
  useContext,
  useMemo,
  useState,
  type ReactNode,
} from 'react';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import CircularProgress from '@mui/material/CircularProgress';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogContentText from '@mui/material/DialogContentText';
import DialogTitle from '@mui/material/DialogTitle';
import Skeleton from '@mui/material/Skeleton';
import Snackbar from '@mui/material/Snackbar';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import InboxIcon from '@mui/icons-material/Inbox';
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
  return (
    <Alert severity="error" sx={{ my: 2 }}>
      {errorMessage(error)}
    </Alert>
  );
}

/** Rich empty state: icon, message, and an optional call to action. */
export function EmptyState({
  title = 'Nothing here yet',
  description,
  action,
  icon,
}: {
  title?: string;
  description?: ReactNode;
  action?: ReactNode;
  icon?: ReactNode;
}) {
  return (
    <Box sx={{ textAlign: 'center', py: 7, px: 3 }}>
      <Box sx={{ color: 'text.disabled', mb: 1.5, '& svg': { fontSize: 44 } }}>
        {icon ?? <InboxIcon />}
      </Box>
      <Typography variant="subtitle1" sx={{ fontWeight: 600 }}>
        {title}
      </Typography>
      {description && (
        <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5, maxWidth: 380, mx: 'auto' }}>
          {description}
        </Typography>
      )}
      {action && <Box sx={{ mt: 2.5 }}>{action}</Box>}
    </Box>
  );
}

/** Back-compat alias for the older <Empty label=… /> usage. */
export function Empty({ label }: { label?: string }) {
  return <EmptyState title={label ?? 'Nothing here yet'} />;
}

export function TableSkeleton({ rows = 6, cols = 4 }: { rows?: number; cols?: number }) {
  return (
    <Box sx={{ p: 2 }}>
      <Skeleton variant="rounded" height={34} sx={{ mb: 1 }} />
      {Array.from({ length: rows }).map((_, r) => (
        <Stack key={r} direction="row" spacing={2} sx={{ py: 1 }}>
          {Array.from({ length: cols }).map((__, c) => (
            <Skeleton key={c} variant="text" sx={{ flex: c === 0 ? 2 : 1, fontSize: '1rem' }} />
          ))}
        </Stack>
      ))}
    </Box>
  );
}

export function CardGridSkeleton({ count = 4, height = 96 }: { count?: number; height?: number }) {
  return (
    <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: '1fr 1fr', md: 'repeat(4, 1fr)' } }}>
      {Array.from({ length: count }).map((_, i) => (
        <Skeleton key={i} variant="rounded" height={height} />
      ))}
    </Box>
  );
}

export function SectionTitle({ children, sx }: { children: ReactNode; sx?: object }) {
  return (
    <Typography variant="h6" sx={{ mb: 1.5, ...sx }}>
      {children}
    </Typography>
  );
}

/** Render loading / error / empty / content for a react-query result. */
export function QueryBoundary<T>({
  query,
  children,
  empty,
  emptyState,
  skeleton,
}: {
  query: UseQueryResult<T>;
  children: (data: T) => ReactNode;
  empty?: (data: T) => boolean;
  emptyState?: ReactNode;
  skeleton?: ReactNode;
}) {
  if (query.isLoading) return <>{skeleton ?? <Loading />}</>;
  if (query.isError) return <ErrorBox error={query.error} />;
  if (query.data === undefined) return <>{emptyState ?? <EmptyState />}</>;
  if (empty?.(query.data)) return <>{emptyState ?? <EmptyState />}</>;
  return <>{children(query.data)}</>;
}

// ---- confirm dialog ----

export function ConfirmDialog({
  open,
  title,
  message,
  confirmLabel = 'Confirm',
  cancelLabel = 'Cancel',
  destructive = false,
  busy = false,
  onConfirm,
  onClose,
}: {
  open: boolean;
  title: string;
  message: ReactNode;
  confirmLabel?: string;
  cancelLabel?: string;
  destructive?: boolean;
  busy?: boolean;
  onConfirm: () => void;
  onClose: () => void;
}) {
  return (
    <Dialog open={open} onClose={onClose} maxWidth="xs" fullWidth>
      <DialogTitle>{title}</DialogTitle>
      <DialogContent>
        <DialogContentText sx={{ fontSize: '0.9375rem' }}>{message}</DialogContentText>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose} disabled={busy}>
          {cancelLabel}
        </Button>
        <Button
          variant="contained"
          color={destructive ? 'error' : 'primary'}
          onClick={onConfirm}
          disabled={busy}
        >
          {busy ? 'Working…' : confirmLabel}
        </Button>
      </DialogActions>
    </Dialog>
  );
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
