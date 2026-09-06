import { useEffect, useState } from 'react';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Chip from '@mui/material/Chip';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogTitle from '@mui/material/DialogTitle';
import Stack from '@mui/material/Stack';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import AddOutlinedIcon from '@mui/icons-material/AddOutlined';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../lib/api';
import * as V from '../lib/validate';
import { useAuth } from '../lib/auth';
import { FormSection } from './FormSection';
import { StatusChip } from './StatusChip';
import { ConfirmDialog, useToast } from './ui';
import type { CenterBankAccount } from '../api/types';

interface FormState {
  id?: number;
  accountHolderName: string;
  accountNumber: string;
  ifsc: string;
  bankName: string;
  label: string;
}

const EMPTY: FormState = { accountHolderName: '', accountNumber: '', ifsc: '', bankName: '', label: '' };

export function CenterBankAccounts() {
  const toast = useToast();
  const qc = useQueryClient();
  const { can } = useAuth();
  const canManage = can('MANAGE_RATES') || can('ALL');

  const [dialog, setDialog] = useState<FormState | null>(null);
  const [deactivating, setDeactivating] = useState<CenterBankAccount | null>(null);

  const q = useQuery({
    queryKey: ['center-bank-accounts'],
    queryFn: async () => (await api.get<CenterBankAccount[]>('/center/bank-accounts')).data,
  });

  const invalidate = () => {
    qc.invalidateQueries({ queryKey: ['center-bank-accounts'] });
  };

  const act = useMutation({
    mutationFn: async ({ id, action }: { id: number; action: 'verify' | 'unverify' | 'primary' | 'deactivate' }) =>
      (await api.post(`/center/bank-accounts/${id}/${action}`)).data,
    onSuccess: (_d, v) => {
      toast.success(
        v.action === 'verify'
          ? 'Account verified'
          : v.action === 'unverify'
            ? 'Verification removed'
            : v.action === 'primary'
              ? 'Primary account set'
              : 'Account deactivated',
      );
      setDeactivating(null);
      invalidate();
    },
    onError: toast.fromError,
  });

  const accounts = q.data ?? [];
  const activeVerified = accounts.filter((a) => a.status === 'ACTIVE' && a.verified).length;

  return (
    <Card>
      <CardContent>
        <FormSection
          title="Business bank accounts"
          hint="Add the accounts you pay farmers and drivers from. Each must be verified before it can be selected during a payment run — you pick the account, record its balance, and the system checks it covers the batch."
        >
          {activeVerified === 0 && (
            <Alert severity="warning">
              No verified account yet — add an account and mark it verified, otherwise payment runs can't record a bank
              balance and approval stays blocked.
            </Alert>
          )}

          {accounts.length > 0 && (
            <Box sx={{ overflowX: 'auto' }}>
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell>Account</TableCell>
                    <TableCell>Bank / IFSC</TableCell>
                    <TableCell>Number</TableCell>
                    <TableCell>Status</TableCell>
                    {canManage && <TableCell align="right">Actions</TableCell>}
                  </TableRow>
                </TableHead>
                <TableBody>
                  {accounts.map((a) => (
                    <TableRow key={a.id} sx={{ opacity: a.status === 'INACTIVE' ? 0.5 : 1 }}>
                      <TableCell>
                        <Typography variant="body2" sx={{ fontWeight: 600 }}>
                          {a.label || a.accountHolderName}
                        </Typography>
                        {a.label && (
                          <Typography variant="caption" color="text.secondary">
                            {a.accountHolderName}
                          </Typography>
                        )}
                      </TableCell>
                      <TableCell>
                        <Typography variant="body2">{a.bankName}</Typography>
                        <Typography variant="caption" color="text.secondary">
                          {a.ifsc}
                        </Typography>
                      </TableCell>
                      <TableCell>{a.accountNumberMasked}</TableCell>
                      <TableCell>
                        <Stack direction="row" spacing={0.5} flexWrap="wrap" useFlexGap>
                          {a.status === 'INACTIVE' ? (
                            <StatusChip status="INACTIVE" />
                          ) : (
                            <StatusChip status={a.verified ? 'VERIFIED' : 'PENDING_VERIFICATION'} label={a.verified ? 'Verified' : 'Not verified'} />
                          )}
                          {a.primary && a.status === 'ACTIVE' && <Chip size="small" color="primary" label="Primary" />}
                        </Stack>
                      </TableCell>
                      {canManage && (
                        <TableCell align="right">
                          {a.status === 'ACTIVE' && (
                            <Stack direction="row" spacing={0.5} justifyContent="flex-end" flexWrap="wrap" useFlexGap>
                              {!a.verified ? (
                                <Button size="small" variant="contained" disabled={act.isPending} onClick={() => act.mutate({ id: a.id, action: 'verify' })}>
                                  Verify
                                </Button>
                              ) : (
                                <>
                                  {!a.primary && (
                                    <Button size="small" disabled={act.isPending} onClick={() => act.mutate({ id: a.id, action: 'primary' })}>
                                      Make primary
                                    </Button>
                                  )}
                                  <Button size="small" disabled={act.isPending} onClick={() => act.mutate({ id: a.id, action: 'unverify' })}>
                                    Unverify
                                  </Button>
                                </>
                              )}
                              <Button
                                size="small"
                                onClick={() =>
                                  setDialog({
                                    id: a.id,
                                    accountHolderName: a.accountHolderName,
                                    accountNumber: '',
                                    ifsc: a.ifsc,
                                    bankName: a.bankName,
                                    label: a.label ?? '',
                                  })
                                }
                              >
                                Edit
                              </Button>
                              <Button size="small" color="error" onClick={() => setDeactivating(a)}>
                                Deactivate
                              </Button>
                            </Stack>
                          )}
                        </TableCell>
                      )}
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </Box>
          )}

          {canManage && (
            <Button startIcon={<AddOutlinedIcon />} onClick={() => setDialog({ ...EMPTY })} sx={{ alignSelf: 'flex-start' }}>
              Add account
            </Button>
          )}
        </FormSection>
      </CardContent>

      <AccountDialog state={dialog} onClose={() => setDialog(null)} onSaved={invalidate} />

      <ConfirmDialog
        open={!!deactivating}
        title="Deactivate bank account"
        message={
          deactivating
            ? `Deactivate "${deactivating.label || deactivating.bankName} ${deactivating.accountNumberMasked}"? It can no longer be selected for payments.`
            : ''
        }
        confirmLabel="Deactivate"
        destructive
        busy={act.isPending}
        onConfirm={() => deactivating && act.mutate({ id: deactivating.id, action: 'deactivate' })}
        onClose={() => setDeactivating(null)}
      />
    </Card>
  );
}

function AccountDialog({ state, onClose, onSaved }: { state: FormState | null; onClose: () => void; onSaved: () => void }) {
  const toast = useToast();
  const [form, setForm] = useState<FormState>(EMPTY);
  const [tried, setTried] = useState(false);

  useEffect(() => {
    if (state) {
      setForm(state);
      setTried(false);
    }
  }, [state]);

  const editing = !!state?.id;
  const set = <K extends keyof FormState>(k: K, v: FormState[K]) => setForm((f) => ({ ...f, [k]: v }));

  const errors: Record<string, string> = {
    accountHolderName: V.required('Account holder name')(form.accountHolderName),
    accountNumber: V.accountNumber(form.accountNumber, editing),
    ifsc: V.ifsc(form.ifsc),
    bankName: V.required('Bank name')(form.bankName),
  };
  const showErr = (k: string) => (tried ? errors[k] ?? '' : '');
  const valid = V.allValid(errors);

  const save = useMutation({
    mutationFn: async () => {
      const body = {
        accountHolderName: form.accountHolderName.trim(),
        accountNumber: form.accountNumber.trim() || undefined,
        ifsc: form.ifsc.trim(),
        bankName: form.bankName.trim(),
        label: form.label.trim() || undefined,
      };
      return state?.id
        ? (await api.put(`/center/bank-accounts/${state.id}`, body)).data
        : (await api.post('/center/bank-accounts', body)).data;
    },
    onSuccess: () => {
      toast.success(editing ? 'Account updated' : 'Account added — verify it before using it for payments');
      onSaved();
      onClose();
    },
    onError: toast.fromError,
  });

  return (
    <Dialog open={!!state} onClose={onClose} maxWidth="xs" fullWidth>
      <DialogTitle>{editing ? 'Edit bank account' : 'Add bank account'}</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          <TextField
            label="Account holder name"
            value={form.accountHolderName}
            onChange={(e) => set('accountHolderName', e.target.value)}
            error={!!showErr('accountHolderName')}
            helperText={showErr('accountHolderName')}
          />
          <TextField
            label="Account number"
            value={form.accountNumber}
            onChange={(e) => set('accountNumber', V.onlyDigits(e.target.value).slice(0, 18))}
            error={!!showErr('accountNumber')}
            helperText={showErr('accountNumber') || (editing ? 'Leave blank to keep the current number' : 'Digits only, 9–18 long')}
            inputProps={{ inputMode: 'numeric', maxLength: 18 }}
          />
          <TextField
            label="IFSC code"
            value={form.ifsc}
            onChange={(e) => set('ifsc', V.upper(e.target.value).replace(/[^A-Z0-9]/g, '').slice(0, 11))}
            error={!!showErr('ifsc')}
            helperText={showErr('ifsc')}
            inputProps={{ maxLength: 11, style: { textTransform: 'uppercase' } }}
          />
          <TextField
            label="Bank name"
            value={form.bankName}
            onChange={(e) => set('bankName', e.target.value)}
            error={!!showErr('bankName')}
            helperText={showErr('bankName')}
          />
          <TextField
            label="Label (optional)"
            placeholder="e.g. HDFC Current – main"
            value={form.label}
            onChange={(e) => set('label', e.target.value)}
          />
          {editing && (
            <Typography variant="caption" color="text.secondary">
              Changing the holder name, number or IFSC will clear the Verified status — the account must be re-verified.
            </Typography>
          )}
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button
          variant="contained"
          disabled={save.isPending}
          onClick={() => {
            setTried(true);
            if (valid) save.mutate();
          }}
        >
          {save.isPending ? 'Saving…' : editing ? 'Save' : 'Add account'}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
