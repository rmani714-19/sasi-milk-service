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
import MenuItem from '@mui/material/MenuItem';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import CancelOutlinedIcon from '@mui/icons-material/CancelOutlined';
import AccountBalanceOutlinedIcon from '@mui/icons-material/AccountBalanceOutlined';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../lib/api';
import { money } from '../lib/format';
import { StatusChip } from './StatusChip';
import { useToast } from './ui';
import type { BankBalanceCheck, PreValidationReport } from '../api/types';

/* -------------------------------------------------------- bank balance */

export function BankBalanceCard({
  base,
  requiredAmount,
  canVerify,
  onChange,
}: {
  base: string; // e.g. /settlements/7  or  /salary-batches/4
  requiredAmount: number;
  canVerify: boolean;
  onChange?: (check: BankBalanceCheck | null) => void;
}) {
  const toast = useToast();
  const qc = useQueryClient();
  const [open, setOpen] = useState(false);

  const q = useQuery({
    queryKey: ['bank-balance', base],
    queryFn: async () => {
      const { data } = await api.get<BankBalanceCheck | ''>(`${base}/bank-balance`);
      return data && typeof data === 'object' ? data : null;
    },
  });
  useEffect(() => {
    if (onChange) onChange(q.data ?? null);
  }, [q.data, onChange]);

  const save = useMutation({
    mutationFn: async (body: Record<string, unknown>) => (await api.post(`${base}/bank-balance`, body)).data,
    onSuccess: () => {
      toast.success('Bank balance recorded');
      qc.invalidateQueries({ queryKey: ['bank-balance', base] });
      qc.invalidateQueries({ queryKey: ['pre-validation', base] });
      setOpen(false);
    },
    onError: toast.fromError,
  });

  const c = q.data;
  return (
    <Card sx={{ mb: 2 }}>
      <CardContent>
        <Stack direction="row" alignItems="center" spacing={1} sx={{ mb: 1 }}>
          <AccountBalanceOutlinedIcon color="action" fontSize="small" />
          <Typography variant="subtitle2" sx={{ fontWeight: 700, flex: 1 }}>
            Owner bank balance
          </Typography>
          {c && <StatusChip status={c.sufficient ? 'VERIFIED' : 'FAILED'} label={c.sufficient ? 'Sufficient' : 'Insufficient'} />}
        </Stack>

        {!c ? (
          <Typography variant="body2" color="text.secondary">
            Required <b>{money(requiredAmount)}</b>. Record the owner's available balance before approval — the figure is
            entered by an authorized user, never estimated.
          </Typography>
        ) : (
          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={{ xs: 0.5, sm: 3 }} sx={{ flexWrap: 'wrap' }}>
            <Metric k="Bank account" v={c.bankAccountLabel} />
            <Metric k="Available" v={money(c.availableBalance)} />
            <Metric k="Batch amount" v={money(c.requiredAmount)} />
            <Metric k="Remaining after" v={money(c.remainingAfter)} tone={c.remainingAfter < 0 ? 'error' : undefined} />
            <Metric k="Method" v={c.method} />
            <Metric k="Verified" v={new Date(c.verifiedAt).toLocaleString()} />
          </Stack>
        )}

        {c && Number(c.requiredAmount) !== Number(requiredAmount) && (
          <Alert severity="warning" sx={{ mt: 1.5 }}>
            The batch amount changed since this check ({money(c.requiredAmount)} → {money(requiredAmount)}). Re-verify the
            bank balance.
          </Alert>
        )}

        {canVerify && (
          <Button size="small" variant={c ? 'text' : 'contained'} sx={{ mt: 1.5 }} onClick={() => setOpen(true)}>
            {c ? 'Re-verify balance' : 'Verify bank balance'}
          </Button>
        )}
      </CardContent>

      <BalanceDialog
        open={open}
        requiredAmount={requiredAmount}
        busy={save.isPending}
        onClose={() => setOpen(false)}
        onSave={(body) => save.mutate(body)}
      />
    </Card>
  );
}

function Metric({ k, v, tone }: { k: string; v: string; tone?: 'error' }) {
  return (
    <Box sx={{ minWidth: 120 }}>
      <Typography variant="caption" color="text.secondary" display="block">
        {k}
      </Typography>
      <Typography variant="body2" sx={{ fontWeight: 600 }} color={tone === 'error' ? 'error.main' : 'text.primary'}>
        {v}
      </Typography>
    </Box>
  );
}

function BalanceDialog({
  open,
  requiredAmount,
  busy,
  onClose,
  onSave,
}: {
  open: boolean;
  requiredAmount: number;
  busy: boolean;
  onClose: () => void;
  onSave: (body: Record<string, unknown>) => void;
}) {
  const [label, setLabel] = useState('');
  const [balance, setBalance] = useState('');
  const [method, setMethod] = useState('MANUAL');
  const [note, setNote] = useState('');
  useEffect(() => {
    if (open) {
      setLabel('');
      setBalance('');
      setMethod('MANUAL');
      setNote('');
    }
  }, [open]);
  const num = Number(balance);
  const remaining = num - requiredAmount;
  const valid = !!label.trim() && balance !== '' && num >= 0 && !Number.isNaN(num);
  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="xs">
      <DialogTitle>Verify bank balance</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          <Typography variant="body2" color="text.secondary">
            Batch amount to pay: <b>{money(requiredAmount)}</b>
          </Typography>
          <TextField label="Bank account" value={label} onChange={(e) => setLabel(e.target.value)} placeholder="e.g. SASI Dairy Current A/c ****4432" autoFocus />
          <TextField
            label="Available balance"
            type="number"
            value={balance}
            onChange={(e) => setBalance(e.target.value)}
            inputProps={{ min: 0, step: '0.01', inputMode: 'decimal' }}
          />
          <TextField select label="Verification method" value={method} onChange={(e) => setMethod(e.target.value)}>
            <MenuItem value="MANUAL">Manual (checked passbook / statement)</MenuItem>
            <MenuItem value="AUTOMATIC">Automatic (bank integration)</MenuItem>
          </TextField>
          <TextField label="Note (optional)" value={note} onChange={(e) => setNote(e.target.value)} />
          {balance !== '' && (
            <Alert severity={remaining >= 0 ? 'success' : 'error'}>
              Remaining after payment: <b>{money(remaining)}</b> — {remaining >= 0 ? 'sufficient' : 'INSUFFICIENT'}
            </Alert>
          )}
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button
          variant="contained"
          disabled={busy || !valid}
          onClick={() => onSave({ bankAccountLabel: label.trim(), availableBalance: num, method, note: note.trim() || undefined })}
        >
          {busy ? 'Saving…' : 'Record balance'}
        </Button>
      </DialogActions>
    </Dialog>
  );
}

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
