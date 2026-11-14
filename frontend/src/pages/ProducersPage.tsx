import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogTitle from '@mui/material/DialogTitle';
import InputAdornment from '@mui/material/InputAdornment';
import MenuItem from '@mui/material/MenuItem';
import Stack from '@mui/material/Stack';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableContainer from '@mui/material/TableContainer';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import AddIcon from '@mui/icons-material/Add';
import SearchIcon from '@mui/icons-material/Search';
import PeopleOutlinedIcon from '@mui/icons-material/PeopleOutlined';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../lib/api';
import { useRegions } from '../api/common';
import { regionShort } from '../lib/region';
import * as V from '../lib/validate';
import { PageHeader } from '../components/PageHeader';
import { FormSection } from '../components/FormSection';
import { StatusChip } from '../components/StatusChip';
import { EmptyState, QueryBoundary, TableSkeleton, useToast } from '../components/ui';
import { useAuth } from '../lib/auth';
import type { Page, Producer, Region } from '../api/types';

export function ProducersPage() {
  const nav = useNavigate();
  const toast = useToast();
  const qc = useQueryClient();
  const { can } = useAuth();
  const regions = useRegions();
  const [q, setQ] = useState('');
  const [regionId, setRegionId] = useState('');
  const [status, setStatus] = useState('');
  const [open, setOpen] = useState(false);

  const list = useQuery({
    queryKey: ['producers', { q }],
    queryFn: async () =>
      (await api.get<Page<Producer>>('/producers', { params: { q: q || undefined, size: 200 } })).data,
  });

  const create = useMutation({
    mutationFn: async (body: Record<string, unknown>) => (await api.post('/producers', body)).data,
    onSuccess: () => {
      toast.success('Producer added');
      qc.invalidateQueries({ queryKey: ['producers'] });
      setOpen(false);
    },
    onError: toast.fromError,
  });

  const regionOf = (id?: number) => regions.data?.find((r) => r.id === id);

  const rows = useMemo(() => {
    const all = list.data?.content ?? [];
    return all.filter(
      (p) =>
        (!regionId || String(p.regionId ?? '') === regionId) &&
        (!status || p.status === status),
    );
  }, [list.data, regionId, status]);

  return (
    <>
      <PageHeader
        title="Producers"
        subtitle="Milk suppliers registered at this centre"
        action={
          can('MANAGE_PRODUCERS') && (
            <Button variant="contained" startIcon={<AddIcon />} onClick={() => setOpen(true)}>
              Add producer
            </Button>
          )
        }
      />

      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} sx={{ mb: 2 }}>
        <TextField
          placeholder="Search by name or producer number"
          value={q}
          onChange={(e) => setQ(e.target.value)}
          sx={{ maxWidth: { sm: 340 } }}
          InputProps={{
            startAdornment: (
              <InputAdornment position="start">
                <SearchIcon color="action" fontSize="small" />
              </InputAdornment>
            ),
          }}
        />
        <TextField select label="Region" value={regionId} onChange={(e) => setRegionId(e.target.value)} sx={{ minWidth: 180 }}>
          <MenuItem value="">All regions</MenuItem>
          {regions.data?.map((r) => (
            <MenuItem key={r.id} value={String(r.id)}>
              {regionShort(r)}
            </MenuItem>
          ))}
        </TextField>
        <TextField select label="Status" value={status} onChange={(e) => setStatus(e.target.value)} sx={{ minWidth: 150 }}>
          <MenuItem value="">All statuses</MenuItem>
          <MenuItem value="ACTIVE">Active</MenuItem>
          <MenuItem value="INACTIVE">Inactive</MenuItem>
        </TextField>
      </Stack>

      <Card>
        <QueryBoundary
          query={list}
          skeleton={<TableSkeleton cols={6} />}
          empty={() => rows.length === 0}
          emptyState={
            <EmptyState
              icon={<PeopleOutlinedIcon />}
              title={q || regionId || status ? 'No matching producers' : 'No producers yet'}
              description={
                q || regionId || status
                  ? 'Adjust the search or filters.'
                  : 'Add your first milk producer to get started.'
              }
              action={
                can('MANAGE_PRODUCERS') &&
                !q &&
                !regionId &&
                !status && (
                  <Button variant="contained" startIcon={<AddIcon />} onClick={() => setOpen(true)}>
                    Add producer
                  </Button>
                )
              }
            />
          }
        >
          {() => (
            <TableContainer sx={{ maxHeight: 'calc(100vh - 280px)' }}>
              <Table size="small" stickyHeader>
                <TableHead>
                  <TableRow>
                    <TableCell>Number</TableCell>
                    <TableCell>Name</TableCell>
                    <TableCell>Mobile</TableCell>
                    <TableCell>Region</TableCell>
                    <TableCell>Payment</TableCell>
                    <TableCell>Status</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {rows.map((p) => (
                    <TableRow key={p.id} hover sx={{ cursor: 'pointer' }} onClick={() => nav(`/producers/${p.id}`)}>
                      <TableCell sx={{ fontWeight: 600 }}>{p.producerNumber}</TableCell>
                      <TableCell>{p.producerName}</TableCell>
                      <TableCell>{p.mobileNumber ?? '—'}</TableCell>
                      <TableCell>{regionShort(regionOf(p.regionId))}</TableCell>
                      <TableCell>{p.paymentFrequency === 'MONTHLY' ? 'Monthly' : 'Weekly'}</TableCell>
                      <TableCell>
                        <StatusChip status={p.status} />
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
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
  paymentFrequency: 'WEEKLY',
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
  const [touched, setTouched] = useState<Record<string, boolean>>({});
  const [tried, setTried] = useState(false);

  const reset = () => {
    setF(BLANK);
    setTouched({});
    setTried(false);
  };

  const set =
    (k: keyof typeof f, transform?: (v: string) => string) =>
    (e: { target: { value: string } }) => {
      const raw = e.target.value;
      setF((s) => ({ ...s, [k]: transform ? transform(raw) : raw }));
    };
  const blur = (k: string) => () => setTouched((t) => ({ ...t, [k]: true }));
  const und = (v: string) => (v.trim() ? v.trim() : undefined);

  const errors: Record<string, string> = {
    producerName: V.required('Producer name')(f.producerName),
    mobileNumber: V.mobile(f.mobileNumber),
    alternateMobile: V.mobile(f.alternateMobile),
    email: V.email(f.email),
    upiId: V.upiId(f.upiId),
    aadhaarNumber: V.aadhaar(f.aadhaarNumber),
  };
  const showErr = (k: string) => ((touched[k] || tried) && errors[k]) || '';
  const valid = V.allValid(errors);

  const submit = () => {
    setTried(true);
    if (!valid) return;
    onSubmit({
      producerName: f.producerName.trim(),
      mobileNumber: und(f.mobileNumber),
      alternateMobile: und(f.alternateMobile),
      email: und(f.email),
      address: und(f.address),
      upiId: und(f.upiId),
      aadhaarNumber: f.aadhaarNumber ? V.onlyDigits(f.aadhaarNumber) : undefined,
      regionId: f.regionId ? Number(f.regionId) : undefined,
      paymentFrequency: f.paymentFrequency,
    });
  };

  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="sm" TransitionProps={{ onExited: reset }}>
      <DialogTitle>Add producer</DialogTitle>
      <DialogContent dividers>
        <Stack spacing={3}>
          <FormSection title="Identity" hint="Producer number is assigned automatically on save.">
            <TextField
              label="Producer name"
              value={f.producerName}
              onChange={set('producerName')}
              onBlur={blur('producerName')}
              error={!!showErr('producerName')}
              helperText={showErr('producerName')}
              autoFocus
              required
            />
            <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
              <TextField
                label="Aadhaar number (12 digits)"
                value={f.aadhaarNumber}
                onChange={set('aadhaarNumber', (v) => V.onlyDigits(v).slice(0, 12))}
                onBlur={blur('aadhaarNumber')}
                error={!!showErr('aadhaarNumber')}
                helperText={showErr('aadhaarNumber')}
                inputProps={{ inputMode: 'numeric', maxLength: 12 }}
              />
              <TextField select label="Region" value={f.regionId} onChange={set('regionId')}>
                <MenuItem value="">— none —</MenuItem>
                {regions.map((r) => (
                  <MenuItem key={r.id} value={r.id}>
                    {regionShort(r)} · {r.name}
                  </MenuItem>
                ))}
              </TextField>
            </Stack>
            <TextField
              select
              label="Payment frequency"
              value={f.paymentFrequency}
              onChange={set('paymentFrequency')}
              required
              helperText="Weekly producers are paid in the weekly run; monthly producers in the monthly run."
            >
              <MenuItem value="WEEKLY">Weekly</MenuItem>
              <MenuItem value="MONTHLY">Monthly</MenuItem>
            </TextField>
            <TextField label="Address" value={f.address} onChange={set('address')} multiline minRows={2} />
          </FormSection>

          <FormSection title="Contact">
            <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
              <TextField
                label="Mobile number"
                value={f.mobileNumber}
                onChange={set('mobileNumber', (v) => V.onlyDigits(v).slice(0, 10))}
                onBlur={blur('mobileNumber')}
                error={!!showErr('mobileNumber')}
                helperText={showErr('mobileNumber')}
                inputProps={{ inputMode: 'numeric', maxLength: 10 }}
              />
              <TextField
                label="Alternate mobile"
                value={f.alternateMobile}
                onChange={set('alternateMobile', (v) => V.onlyDigits(v).slice(0, 10))}
                onBlur={blur('alternateMobile')}
                error={!!showErr('alternateMobile')}
                helperText={showErr('alternateMobile')}
                inputProps={{ inputMode: 'numeric', maxLength: 10 }}
              />
            </Stack>
            <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
              <TextField
                label="GPay / UPI ID"
                value={f.upiId}
                onChange={set('upiId')}
                onBlur={blur('upiId')}
                error={!!showErr('upiId')}
                helperText={showErr('upiId') || 'e.g. ramesh@okhdfcbank'}
              />
              <TextField
                label="Email (optional)"
                value={f.email}
                onChange={set('email')}
                onBlur={blur('email')}
                error={!!showErr('email')}
                helperText={showErr('email')}
              />
            </Stack>
          </FormSection>

          {tried && !valid && (
            <Typography variant="caption" color="error">
              Please fix the highlighted fields.
            </Typography>
          )}
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button variant="contained" disabled={busy} onClick={submit}>
          {busy ? 'Saving…' : 'Save producer'}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
