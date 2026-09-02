import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import Chip from '@mui/material/Chip';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogTitle from '@mui/material/DialogTitle';
import MenuItem from '@mui/material/MenuItem';
import Stack from '@mui/material/Stack';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import AddIcon from '@mui/icons-material/Add';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../lib/api';
import { useRegions } from '../api/common';
import { PageHeader } from '../components/PageHeader';
import { QueryBoundary, useToast } from '../components/ui';
import { useAuth } from '../lib/auth';
import type { Page, Producer, Region } from '../api/types';

export function ProducersPage() {
  const nav = useNavigate();
  const toast = useToast();
  const qc = useQueryClient();
  const { can } = useAuth();
  const regions = useRegions();
  const [q, setQ] = useState('');
  const [open, setOpen] = useState(false);

  const list = useQuery({
    queryKey: ['producers', { q }],
    queryFn: async () =>
      (await api.get<Page<Producer>>('/producers', { params: { q: q || undefined, size: 100 } })).data,
  });

  const create = useMutation({
    mutationFn: async (body: Record<string, unknown>) => (await api.post('/producers', body)).data,
    onSuccess: () => {
      toast.success('Farmer added');
      qc.invalidateQueries({ queryKey: ['producers'] });
      setOpen(false);
    },
    onError: toast.fromError,
  });

  const regionName = (id?: number) => regions.data?.find((r) => r.id === id)?.code ?? '—';

  return (
    <>
      <PageHeader
        title="Farmers"
        action={
          can('MANAGE_PRODUCERS') && (
            <Button variant="contained" startIcon={<AddIcon />} onClick={() => setOpen(true)}>
              Add farmer
            </Button>
          )
        }
      />
      <TextField
        placeholder="Search farmers by name or number"
        value={q}
        onChange={(e) => setQ(e.target.value)}
        sx={{ mb: 2, maxWidth: 360 }}
      />
      <Card>
        <QueryBoundary query={list} empty={(d) => d.content.length === 0}>
          {(d) => (
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell>Number</TableCell>
                  <TableCell>Name</TableCell>
                  <TableCell>Mobile</TableCell>
                  <TableCell>Region</TableCell>
                  <TableCell>Status</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {d.content.map((p) => (
                  <TableRow
                    key={p.id}
                    hover
                    sx={{ cursor: 'pointer' }}
                    onClick={() => nav(`/producers/${p.id}`)}
                  >
                    <TableCell>{p.producerNumber}</TableCell>
                    <TableCell>{p.producerName}</TableCell>
                    <TableCell>{p.mobileNumber ?? '—'}</TableCell>
                    <TableCell>{regionName(p.regionId)}</TableCell>
                    <TableCell>
                      <Chip
                        size="small"
                        label={p.status}
                        color={p.status === 'ACTIVE' ? 'success' : 'default'}
                        variant="outlined"
                      />
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </QueryBoundary>
      </Card>

      <ProducerDialog
        open={open}
        regions={regions.data ?? []}
        busy={create.isPending}
        onClose={() => setOpen(false)}
        onSubmit={(body) => create.mutate(body)}
      />
    </>
  );
}

const BLANK = {
  producerName: '',
  mobileNumber: '',
  alternateMobile: '',
  email: '',
  address: '',
  upiId: '',
  aadhaarNumber: '',
  regionId: '',
  accountHolderName: '',
  accountNumber: '',
  bankName: '',
  bankBranch: '',
  ifscCode: '',
};

function ProducerDialog({
  open,
  regions,
  busy,
  onClose,
  onSubmit,
}: {
  open: boolean;
  regions: Region[];
  busy: boolean;
  onClose: () => void;
  onSubmit: (body: Record<string, unknown>) => void;
}) {
  const [f, setF] = useState(BLANK);
  const set = (k: keyof typeof f) => (e: { target: { value: string } }) => setF({ ...f, [k]: e.target.value });
  const und = (v: string) => (v.trim() ? v.trim() : undefined);

  const bankReady =
    !!f.accountHolderName.trim() && !!f.accountNumber.trim() && !!f.bankName.trim() && !!f.ifscCode.trim();
  const ready = !!f.producerName.trim() && bankReady;

  const submit = () => {
    onSubmit({
      producerName: f.producerName.trim(),
      mobileNumber: und(f.mobileNumber),
      alternateMobile: und(f.alternateMobile),
      email: und(f.email),
      address: und(f.address),
      upiId: und(f.upiId),
      aadhaarNumber: f.aadhaarNumber ? f.aadhaarNumber.replace(/\D/g, '') : undefined,
      regionId: f.regionId ? Number(f.regionId) : undefined,
      bank: {
        accountHolderName: f.accountHolderName.trim(),
        accountNumber: f.accountNumber.trim(),
        bankName: f.bankName.trim(),
        bankBranch: und(f.bankBranch),
        ifscCode: f.ifscCode.trim().toUpperCase(),
      },
    });
  };

  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="sm" TransitionProps={{ onExited: () => setF(BLANK) }}>
      <DialogTitle>Add farmer</DialogTitle>
      <DialogContent dividers>
        <Stack spacing={2}>
          <SectionLabel>Identity</SectionLabel>
          <Typography variant="caption" color="text.secondary" sx={{ mt: -0.5 }}>
            Farmer number is assigned automatically on save.
          </Typography>
          <TextField label="Farmer name" value={f.producerName} onChange={set('producerName')} autoFocus required />
          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
            <TextField label="Aadhaar number (12 digits)" value={f.aadhaarNumber} onChange={set('aadhaarNumber')} inputProps={{ inputMode: 'numeric', maxLength: 14 }} />
            <TextField select label="Region" value={f.regionId} onChange={set('regionId')}>
              <MenuItem value="">— none —</MenuItem>
              {regions.map((r) => (
                <MenuItem key={r.id} value={r.id}>
                  {r.code} · {r.name}
                </MenuItem>
              ))}
            </TextField>
          </Stack>
          <TextField label="Address" value={f.address} onChange={set('address')} multiline minRows={2} />

          <SectionLabel>Contact</SectionLabel>
          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
            <TextField label="Mobile number" value={f.mobileNumber} onChange={set('mobileNumber')} inputProps={{ inputMode: 'numeric' }} />
            <TextField label="Alternate mobile" value={f.alternateMobile} onChange={set('alternateMobile')} inputProps={{ inputMode: 'numeric' }} />
          </Stack>
          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
            <TextField label="GPay / UPI ID" value={f.upiId} onChange={set('upiId')} />
            <TextField label="Email (optional)" value={f.email} onChange={set('email')} />
          </Stack>

          <SectionLabel>Bank details (required)</SectionLabel>
          <TextField label="Account holder name" value={f.accountHolderName} onChange={set('accountHolderName')} required />
          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
            <TextField label="Account number" value={f.accountNumber} onChange={set('accountNumber')} inputProps={{ inputMode: 'numeric' }} required />
            <TextField label="IFSC code" value={f.ifscCode} onChange={set('ifscCode')} required />
          </Stack>
          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
            <TextField label="Bank name" value={f.bankName} onChange={set('bankName')} required />
            <TextField label="Branch (optional)" value={f.bankBranch} onChange={set('bankBranch')} />
          </Stack>
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button variant="contained" disabled={busy || !ready} onClick={submit}>
          Save
        </Button>
      </DialogActions>
    </Dialog>
  );
}

function SectionLabel({ children }: { children: React.ReactNode }) {
  return (
    <Typography variant="overline" color="text.secondary" sx={{ mt: 1, lineHeight: 1 }}>
      {children}
    </Typography>
  );
}
