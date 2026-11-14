import { useEffect, useState } from 'react';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Checkbox from '@mui/material/Checkbox';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogTitle from '@mui/material/DialogTitle';
import FormControlLabel from '@mui/material/FormControlLabel';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import CancelOutlinedIcon from '@mui/icons-material/CancelOutlined';
import { useQuery } from '@tanstack/react-query';
import { api } from '../lib/api';
import { StatusChip } from './StatusChip';
import type { PreValidationReport } from '../api/types';

/* -------------------------------------------------------- pre-validation */

export function PreValidationCard({ base, onResult }: { base: string; onResult?: (r: PreValidationReport) => void }) {
  const q = useQuery({
    queryKey: ['pre-validation', base],
    queryFn: async () => (await api.get<PreValidationReport>(`${base}/pre-validation`)).data,
  });
  useEffect(() => {
    if (q.data && onResult) onResult(q.data);
  }, [q.data, onResult]);

  const r = q.data;
  return (
    <Card sx={{ mb: 2 }}>
      <CardContent>
        <Stack direction="row" alignItems="center" spacing={1} sx={{ mb: 1 }}>
          <Typography variant="subtitle2" sx={{ fontWeight: 700, flex: 1 }}>
            Pre-validation
          </Typography>
          {r && <StatusChip status={r.result === 'READY_FOR_APPROVAL' ? 'VERIFIED' : 'FAILED'} label={r.result.replace(/_/g, ' ')} />}
          <Button size="small" onClick={() => q.refetch()} disabled={q.isFetching}>
            {q.isFetching ? 'Checking…' : 'Re-run'}
          </Button>
        </Stack>
        <Stack spacing={0.5}>
          {(r?.checks ?? []).map((c) => (
            <Stack key={c.key} direction="row" alignItems="flex-start" spacing={1}>
              {c.passed ? (
                <CheckCircleIcon sx={{ fontSize: 18, color: 'success.main', mt: '1px' }} />
              ) : (
                <CancelOutlinedIcon sx={{ fontSize: 18, color: c.blocking ? 'error.main' : 'warning.main', mt: '1px' }} />
              )}
              <Box>
                <Typography variant="body2" sx={{ fontWeight: c.blocking ? 600 : 400 }}>
                  {c.label}
                  {!c.blocking && (
                    <Typography component="span" variant="caption" color="text.secondary">
                      {' '}
                      · advisory
                    </Typography>
                  )}
                </Typography>
                {c.detail && (
                  <Typography variant="caption" color={c.passed ? 'text.secondary' : c.blocking ? 'error.main' : 'warning.main'}>
                    {c.detail}
                  </Typography>
                )}
              </Box>
            </Stack>
          ))}
        </Stack>
        {r?.result === 'BLOCKED' && (
          <Alert severity="error" sx={{ mt: 1.5 }}>
            Approval is blocked until every required check passes.
          </Alert>
        )}
      </CardContent>
    </Card>
  );
}

/* -------------------------------------------------------- owner approval */

export function OwnerApprovalDialog({
  open,
  lines,
  busy,
  onConfirm,
  onClose,
}: {
  open: boolean;
  lines: [string, string][];
  busy: boolean;
  onConfirm: () => void;
  onClose: () => void;
}) {
  const [checked, setChecked] = useState(false);
  useEffect(() => {
    if (open) setChecked(false);
  }, [open]);
  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="xs">
      <DialogTitle>Owner approval</DialogTitle>
      <DialogContent>
        <Stack spacing={0.75} sx={{ mb: 2 }}>
          {lines.map(([k, v]) => (
            <Stack key={k} direction="row" justifyContent="space-between" spacing={2}>
              <Typography variant="body2" color="text.secondary">
                {k}
              </Typography>
              <Typography variant="body2" sx={{ fontWeight: 600 }}>
                {v}
              </Typography>
            </Stack>
          ))}
        </Stack>
        <FormControlLabel
          control={<Checkbox checked={checked} onChange={(e) => setChecked(e.target.checked)} />}
          label="I have reviewed and approved this payment batch."
        />
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button variant="contained" color="success" disabled={busy || !checked} onClick={onConfirm}>
          {busy ? 'Approving…' : 'Approve batch'}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
