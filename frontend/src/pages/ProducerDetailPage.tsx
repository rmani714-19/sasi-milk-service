import { useState } from 'react';
import { useParams } from 'react-router-dom';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogTitle from '@mui/material/DialogTitle';
import Grid from '@mui/material/Grid2';
import MenuItem from '@mui/material/MenuItem';
import Stack from '@mui/material/Stack';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../lib/api';
import { useRegions } from '../api/common';
import { PageHeader } from '../components/PageHeader';
import { FormSection } from '../components/FormSection';
import { StatusChip } from '../components/StatusChip';
import { ConfirmDialog, QueryBoundary, SectionTitle, useToast } from '../components/ui';
import { useAuth } from '../lib/auth';
import type { BankAccount, Producer } from '../api/types';

export function ProducerDetailPage() {
  const { id } = useParams();
  const toast = useToast();
  const qc = useQueryClient();
  const { can } = useAuth();
  const [bankOpen, setBankOpen] = useState(false);
  const [editOpen, setEditOpen] = useState(false);
  const [editBank, setEditBank] = useState<BankAccount | null>(null);
  const [confirmDeactivate, setConfirmDeactivate] = useState(false);
  const [confirmBank, setConfirmBank] = useState<BankAccount | null>(null);
  const regions = useRegions();

  const producer = useQuery({
    queryKey: ['producer', id],
    queryFn: async () => (await api.get<Producer>(`/producers/${id}`)).data,
  });
  const banks = useQuery({
    queryKey: ['producer', id, 'banks'],
    queryFn: async () => (await api.get<BankAccount[]>(`/producers/${id}/bank-accounts`)).data,
    enabled: can('MANAGE_PRODUCERS'),
  });

  const deactivate = useMutation({
    mutationFn: async () => (await api.post(`/producers/${id}/deactivate`)).data,
    onSuccess: () => {
      toast.success('Producer deactivated');
      setConfirmDeactivate(false);
      qc.invalidateQueries({ queryKey: ['producer', id] });
    },
    onError: toast.fromError,
  });

  const addBank = useMutation({
    mutationFn: async (body: Record<string, unknown>) =>
      (await api.post(`/producers/${id}/bank-accounts`, body)).data,
    onSuccess: () => {
      toast.success('Bank account added');
      qc.invalidateQueries({ queryKey: ['producer', id, 'banks'] });
      setBankOpen(false);
    },
    onError: toast.fromError,
  });

  const edit = useMutation({
    mutationFn: async (body: Record<string, unknown>) => (await api.put(`/producers/${id}`, body)).data,
    onSuccess: () => {
      toast.success('Producer updated');
      qc.invalidateQueries({ queryKey: ['producer', id] });
      qc.invalidateQueries({ queryKey: ['producers'] });
      setEditOpen(false);
    },
    onError: toast.fromError,
  });

  const bankInvalidate = () => qc.invalidateQueries({ queryKey: ['producer', id, 'banks'] });

  const updateBank = useMutation({
    mutationFn: async ({ accountId, body }: { accountId: number; body: Record<string, unknown> }) =>
      (await api.put(`/producers/${id}/bank-accounts/${accountId}`, body)).data,
    onSuccess: () => {
      toast.success('Bank account updated');
      bankInvalidate();
      setEditBank(null);
    },
    onError: toast.fromError,
  });
  const setPrimary = useMutation({
    mutationFn: async (accountId: number) =>
      (await api.post(`/producers/${id}/bank-accounts/${accountId}/primary`)).data,
    onSuccess: () => {
      toast.success('Primary account changed');
      bankInvalidate();
    },
    onError: toast.fromError,
  });
  const deactivateBank = useMutation({
    mutationFn: async (accountId: number) =>
      (await api.post(`/producers/${id}/bank-accounts/${accountId}/deactivate`)).data,
    onSuccess: () => {
      toast.success('Bank account deactivated');
      setConfirmBank(null);
      bankInvalidate();
    },
    onError: toast.fromError,
  });

  return (
    <QueryBoundary query={producer}>
      {(p) => (
        <>
          <PageHeader
            title={p.producerName}
            subtitle={`${p.producerNumber} · joined ${p.joiningDate}`}
            action={
              can('MANAGE_PRODUCERS') && (
                <Stack direction="row" spacing={1}>
                  <Button variant="outlined" onClick={() => setEditOpen(true)}>
                    Edit
                  </Button>
                  {p.status === 'ACTIVE' && (
                    <Button color="error" variant="outlined" onClick={() => setConfirmDeactivate(true)}>
                      Deactivate
                    </Button>
                  )}
                </Stack>
              )
            }
          />
          <Grid container spacing={3}>
            <Grid size={{ xs: 12, md: 5 }}>
              <Card>
                <CardContent>
                  <Stack spacing={1.25}>
                    <Row label="Status" value={<StatusChip status={p.status} />} />
                    <Row label="Mobile" value={p.mobileNumber ?? '—'} />
                    <Row label="Alternate mobile" value={p.alternateMobile ?? '—'} />
                    <Row label="GPay / UPI" value={p.upiId ?? '—'} />
                    <Row label="Email" value={p.email ?? '—'} />
                    <Row label="Aadhaar" value={p.aadhaarMasked ?? '—'} />
                    <Row label="Address" value={p.address ?? '—'} />
                    <Row label="QR token" value={<Typography component="code" sx={{ fontSize: 12 }}>{p.qrCode}</Typography>} />
                  </Stack>
                </CardContent>
              </Card>
            </Grid>
            <Grid size={{ xs: 12, md: 7 }}>
              <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 1.5 }}>
                <SectionTitle sx={{ mb: 0 }}>Bank accounts</SectionTitle>
                {can('MANAGE_PRODUCERS') && (
                  <Button size="small" variant="outlined" onClick={() => setBankOpen(true)}>
                    Add account
                  </Button>
                )}
              </Stack>
              <Card sx={{ overflowX: 'auto' }}>
                {can('MANAGE_PRODUCERS') ? (
                  <QueryBoundary query={banks} empty={(d) => d.length === 0}>
                    {(d) => (
                      <Table size="small">
                        <TableHead>
                          <TableRow>
                            <TableCell>Holder</TableCell>
                            <TableCell>Account</TableCell>
                            <TableCell>IFSC</TableCell>
                            <TableCell>Status</TableCell>
                            <TableCell align="right">Actions</TableCell>
                          </TableRow>
                        </TableHead>
                        <TableBody>
                          {d.map((b) => (
                            <TableRow key={b.id} sx={{ opacity: b.status === 'ACTIVE' ? 1 : 0.55 }}>
                              <TableCell>{b.accountHolderName}</TableCell>
                              <TableCell>{b.accountNumberMasked}</TableCell>
                              <TableCell>{b.ifscCode}</TableCell>
                              <TableCell>
                                {b.primary ? (
                                  <StatusChip status="PRIMARY" tone="brand" label="Primary" />
                                ) : (
                                  <StatusChip status={b.status} />
                                )}
                              </TableCell>
                              <TableCell align="right">
                                <Stack direction="row" spacing={0.5} justifyContent="flex-end">
                                  <Button size="small" onClick={() => setEditBank(b)}>
                                    Edit
                                  </Button>
                                  {b.status === 'ACTIVE' && !b.primary && (
                                    <Button size="small" onClick={() => setPrimary.mutate(b.id)}>
                                      Make primary
                                    </Button>
                                  )}
                                  {b.status === 'ACTIVE' && (
                                    <Button size="small" color="error" onClick={() => setConfirmBank(b)}>
                                      Deactivate
                                    </Button>
                                  )}
                                </Stack>
                              </TableCell>
                            </TableRow>
                          ))}
                        </TableBody>
                      </Table>
                    )}
                  </QueryBoundary>
                ) : (
                  <CardContent>
                    <Typography color="text.secondary">Not permitted to view bank details.</Typography>
                  </CardContent>
                )}
              </Card>
            </Grid>
          </Grid>

          <BankDialog
            open={bankOpen}
            busy={addBank.isPending}
            onClose={() => setBankOpen(false)}
            onSubmit={(body) => addBank.mutate(body)}
          />
          <EditBankDialog
            key={editBank?.id ?? 'none'}
            account={editBank}
            busy={updateBank.isPending}
            onClose={() => setEditBank(null)}
            onSubmit={(body) => editBank && updateBank.mutate({ accountId: editBank.id, body })}
          />
          <EditProducerDialog
            key={editOpen ? 'open' : 'closed'}
            open={editOpen}
            producer={p}
            regions={regions.data ?? []}
            busy={edit.isPending}
            onClose={() => setEditOpen(false)}
            onSubmit={(body) => edit.mutate(body)}
          />

          <ConfirmDialog
            open={confirmDeactivate}
            title="Deactivate producer?"
            message={`${p.producerName} (${p.producerNumber}) will stop appearing for new collections and settlements. Existing records are kept.`}
            confirmLabel="Deactivate"
            destructive
            busy={deactivate.isPending}
            onConfirm={() => deactivate.mutate()}
            onClose={() => setConfirmDeactivate(false)}
          />
          <ConfirmDialog
            open={!!confirmBank}
            title="Deactivate bank account?"
            message={`Account ${confirmBank?.accountNumberMasked ?? ''} will no longer be used for payouts.`}
            confirmLabel="Deactivate"
            destructive
            busy={deactivateBank.isPending}
            onConfirm={() => confirmBank && deactivateBank.mutate(confirmBank.id)}
            onClose={() => setConfirmBank(null)}
          />
        </>
      )}
    </QueryBoundary>
  );
}

function EditProducerDialog({
  open,
  producer,
  regions,
  busy,
  onClose,
  onSubmit,
}: {
  open: boolean;
  producer: Producer;
  regions: { id: number; code: string; name: string }[];
  busy: boolean;
  onClose: () => void;
  onSubmit: (body: Record<string, unknown>) => void;
}) {
  const [f, setF] = useState({
    producerName: producer.producerName,
    mobileNumber: producer.mobileNumber ?? '',
    alternateMobile: producer.alternateMobile ?? '',
    email: producer.email ?? '',
    address: producer.address ?? '',
    upiId: producer.upiId ?? '',
    regionId: producer.regionId ? String(producer.regionId) : '',
    aadhaarNumber: '',
    regionChangeReason: '',
  });
  const set = (k: keyof typeof f) => (e: { target: { value: string } }) => setF({ ...f, [k]: e.target.value });
  const und = (v: string) => (v.trim() ? v.trim() : undefined);
  const regionChanged = f.regionId !== (producer.regionId ? String(producer.regionId) : '');

  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle>Edit {producer.producerNumber}</DialogTitle>
      <DialogContent dividers>
        <Stack spacing={3}>
          <FormSection title="Details">
            <TextField label="Producer name" value={f.producerName} onChange={set('producerName')} required />
            <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
              <TextField label="Mobile number" value={f.mobileNumber} onChange={set('mobileNumber')} inputProps={{ inputMode: 'numeric' }} />
              <TextField label="Alternate mobile" value={f.alternateMobile} onChange={set('alternateMobile')} inputProps={{ inputMode: 'numeric' }} />
            </Stack>
            <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
              <TextField label="GPay / UPI ID" value={f.upiId} onChange={set('upiId')} />
              <TextField label="Email" value={f.email} onChange={set('email')} />
            </Stack>
            <TextField
              label="Aadhaar number"
              value={f.aadhaarNumber}
              onChange={set('aadhaarNumber')}
              inputProps={{ inputMode: 'numeric', maxLength: 14 }}
              helperText={producer.aadhaarMasked ? `Current: ${producer.aadhaarMasked} — leave blank to keep` : 'Leave blank if not applicable'}
            />
            <TextField label="Address" value={f.address} onChange={set('address')} multiline minRows={2} />
            <TextField select label="Region" value={f.regionId} onChange={set('regionId')}>
              <MenuItem value="">— none —</MenuItem>
              {regions.map((r) => (
                <MenuItem key={r.id} value={r.id}>
                  {r.code} · {r.name}
                </MenuItem>
              ))}
            </TextField>
            {regionChanged && (
              <TextField label="Reason for region change" value={f.regionChangeReason} onChange={set('regionChangeReason')} />
            )}
          </FormSection>
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button
          variant="contained"
          disabled={busy || !f.producerName.trim()}
          onClick={() =>
            onSubmit({
              producerName: f.producerName.trim(),
              mobileNumber: und(f.mobileNumber),
              alternateMobile: und(f.alternateMobile),
              email: und(f.email),
              address: und(f.address),
              upiId: und(f.upiId),
              aadhaarNumber: f.aadhaarNumber ? f.aadhaarNumber.replace(/\D/g, '') : undefined,
              regionId: f.regionId ? Number(f.regionId) : undefined,
              regionChangeReason: und(f.regionChangeReason),
            })
          }
        >
          Save changes
        </Button>
      </DialogActions>
    </Dialog>
  );
}

function Row({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <Stack direction="row" justifyContent="space-between" spacing={2}>
      <Typography color="text.secondary" variant="body2">
        {label}
      </Typography>
      <Typography variant="body2" sx={{ textAlign: 'right', fontWeight: 500 }}>
        {value}
      </Typography>
    </Stack>
  );
}

function BankDialog({
  open,
  busy,
  onClose,
  onSubmit,
}: {
  open: boolean;
  busy: boolean;
  onClose: () => void;
  onSubmit: (b: Record<string, unknown>) => void;
}) {
  const [f, setF] = useState({ accountHolderName: '', accountNumber: '', ifscCode: '', bankName: '' });
  const set = (k: keyof typeof f) => (e: { target: { value: string } }) => setF({ ...f, [k]: e.target.value });
  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="xs">
      <DialogTitle>Add bank account</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          <TextField label="Account holder name" value={f.accountHolderName} onChange={set('accountHolderName')} autoFocus />
          <TextField label="Account number" value={f.accountNumber} onChange={set('accountNumber')} />
          <TextField label="IFSC" value={f.ifscCode} onChange={set('ifscCode')} />
          <TextField label="Bank name" value={f.bankName} onChange={set('bankName')} />
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button
          variant="contained"
          disabled={busy || !f.accountHolderName || !f.accountNumber || !f.ifscCode}
          onClick={() =>
            onSubmit({
              accountHolderName: f.accountHolderName.trim(),
              accountNumber: f.accountNumber.trim(),
              ifscCode: f.ifscCode.trim().toUpperCase(),
              bankName: f.bankName || undefined,
            })
          }
        >
          Save
        </Button>
      </DialogActions>
    </Dialog>
  );
}

function EditBankDialog({
  account,
  busy,
  onClose,
  onSubmit,
}: {
  account: BankAccount | null;
  busy: boolean;
  onClose: () => void;
  onSubmit: (b: Record<string, unknown>) => void;
}) {
  const [f, setF] = useState({
    accountHolderName: account?.accountHolderName ?? '',
    accountNumber: '',
    ifscCode: account?.ifscCode ?? '',
    bankName: account?.bankName ?? '',
    bankBranch: account?.bankBranch ?? '',
  });
  const set = (k: keyof typeof f) => (e: { target: { value: string } }) => setF({ ...f, [k]: e.target.value });

  return (
    <Dialog open={!!account} onClose={onClose} fullWidth maxWidth="xs">
      <DialogTitle>Edit bank account</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          <TextField label="Account holder name" value={f.accountHolderName} onChange={set('accountHolderName')} required />
          <TextField
            label="Account number"
            value={f.accountNumber}
            onChange={set('accountNumber')}
            inputProps={{ inputMode: 'numeric' }}
            helperText={account ? `Current: ${account.accountNumberMasked} — leave blank to keep` : ''}
          />
          <TextField label="IFSC" value={f.ifscCode} onChange={set('ifscCode')} required />
          <TextField label="Bank name" value={f.bankName} onChange={set('bankName')} />
          <TextField label="Branch" value={f.bankBranch} onChange={set('bankBranch')} />
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button
          variant="contained"
          disabled={busy || !f.accountHolderName.trim() || !f.ifscCode.trim()}
          onClick={() =>
            onSubmit({
              accountHolderName: f.accountHolderName.trim(),
              accountNumber: f.accountNumber.trim() || undefined,
              ifscCode: f.ifscCode.trim().toUpperCase(),
              bankName: f.bankName.trim() || undefined,
              bankBranch: f.bankBranch.trim() || undefined,
            })
          }
        >
          Save changes
        </Button>
      </DialogActions>
    </Dialog>
  );
}
