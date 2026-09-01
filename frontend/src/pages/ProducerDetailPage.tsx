import { useState } from 'react';
import { useParams } from 'react-router-dom';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Chip from '@mui/material/Chip';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogTitle from '@mui/material/DialogTitle';
import Grid from '@mui/material/Grid2';
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
import { PageHeader } from '../components/PageHeader';
import { QueryBoundary, useToast } from '../components/ui';
import { useAuth } from '../lib/auth';
import type { BankAccount, Producer } from '../api/types';

export function ProducerDetailPage() {
  const { id } = useParams();
  const toast = useToast();
  const qc = useQueryClient();
  const { can } = useAuth();
  const [bankOpen, setBankOpen] = useState(false);

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

  return (
    <QueryBoundary query={producer}>
      {(p) => (
        <>
          <PageHeader
            title={p.producerName}
            subtitle={`${p.producerNumber} · joined ${p.joiningDate}`}
            action={
              can('MANAGE_PRODUCERS') &&
              p.status === 'ACTIVE' && (
                <Button color="error" onClick={() => deactivate.mutate()} disabled={deactivate.isPending}>
                  Deactivate
                </Button>
              )
            }
          />
          <Grid container spacing={3}>
            <Grid size={{ xs: 12, md: 5 }}>
              <Card>
                <CardContent>
                  <Stack spacing={1}>
                    <Row label="Status" value={<Chip size="small" label={p.status} color={p.status === 'ACTIVE' ? 'success' : 'default'} />} />
                    <Row label="Mobile" value={p.mobileNumber ?? '—'} />
                    <Row label="Alternate mobile" value={p.alternateMobile ?? '—'} />
                    <Row label="GPay / UPI" value={p.upiId ?? '—'} />
                    <Row label="Email" value={p.email ?? '—'} />
                    <Row label="Aadhaar" value={p.aadhaarMasked ?? '—'} />
                    <Row label="Address" value={p.address ?? '—'} />
                    <Row label="QR token" value={<code>{p.qrCode}</code>} />
                  </Stack>
                </CardContent>
              </Card>
            </Grid>
            <Grid size={{ xs: 12, md: 7 }}>
              <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 1 }}>
                <Typography variant="h6">Bank accounts</Typography>
                {can('MANAGE_PRODUCERS') && (
                  <Button size="small" variant="outlined" onClick={() => setBankOpen(true)}>
                    Add account
                  </Button>
                )}
              </Stack>
              <Card>
                {can('MANAGE_PRODUCERS') ? (
                  <QueryBoundary query={banks} empty={(d) => d.length === 0}>
                    {(d) => (
                      <Table size="small">
                        <TableHead>
                          <TableRow>
                            <TableCell>Holder</TableCell>
                            <TableCell>Account</TableCell>
                            <TableCell>IFSC</TableCell>
                            <TableCell>Primary</TableCell>
                          </TableRow>
                        </TableHead>
                        <TableBody>
                          {d.map((b) => (
                            <TableRow key={b.id}>
                              <TableCell>{b.accountHolderName}</TableCell>
                              <TableCell>{b.accountNumberMasked}</TableCell>
                              <TableCell>{b.ifscCode}</TableCell>
                              <TableCell>{b.primary ? '✓' : ''}</TableCell>
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
        </>
      )}
    </QueryBoundary>
  );
}

function Row({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <Stack direction="row" justifyContent="space-between" spacing={2}>
      <Typography color="text.secondary">{label}</Typography>
      <Typography sx={{ textAlign: 'right' }}>{value}</Typography>
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
