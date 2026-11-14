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
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../lib/api';
import { useRegions } from '../api/common';
import { regionShort } from '../lib/region';
import * as V from '../lib/validate';
import { PageHeader } from '../components/PageHeader';
import { FormSection } from '../components/FormSection';
import { StatusChip } from '../components/StatusChip';
import { ConfirmDialog, QueryBoundary, useToast } from '../components/ui';
import { useAuth } from '../lib/auth';
import type { Producer, Region } from '../api/types';

export function ProducerDetailPage() {
  const { id } = useParams();
  const toast = useToast();
  const qc = useQueryClient();
  const { can } = useAuth();
  const [editOpen, setEditOpen] = useState(false);
  const [confirmDeactivate, setConfirmDeactivate] = useState(false);
  const regions = useRegions();

  const producer = useQuery({
    queryKey: ['producer', id],
    queryFn: async () => (await api.get<Producer>(`/producers/${id}`)).data,
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
            <Grid size={{ xs: 12, md: 7 }}>
              <Card>
                <CardContent>
                  <Stack spacing={1.25}>
                    <Row label="Status" value={<StatusChip status={p.status} />} />
                    <Row
                      label="Payment frequency"
                      value={p.paymentFrequency === 'MONTHLY' ? 'Monthly' : 'Weekly'}
                    />
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
          </Grid>

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
  regions: Region[];
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
    paymentFrequency: producer.paymentFrequency ?? 'WEEKLY',
    aadhaarNumber: '',
    regionChangeReason: '',
  });
  const [touched, setTouched] = useState<Record<string, boolean>>({});
  const [tried, setTried] = useState(false);
  const set =
    (k: keyof typeof f, transform?: (v: string) => string) =>
    (e: { target: { value: string } }) =>
      setF((s) => ({ ...s, [k]: transform ? transform(e.target.value) : e.target.value }));
  const blur = (k: string) => () => setTouched((t) => ({ ...t, [k]: true }));
  const und = (v: string) => (v.trim() ? v.trim() : undefined);
  const regionChanged = f.regionId !== (producer.regionId ? String(producer.regionId) : '');

  const errors: Record<string, string> = {
    producerName: V.required('Producer name')(f.producerName),
    mobileNumber: V.mobile(f.mobileNumber),
    alternateMobile: V.mobile(f.alternateMobile),
    email: V.email(f.email),
    upiId: V.upiId(f.upiId),
    aadhaarNumber: V.aadhaar(f.aadhaarNumber),
    regionChangeReason: regionChanged ? V.required('A reason')(f.regionChangeReason) : '',
  };
  const showErr = (k: string) => ((touched[k] || tried) && errors[k]) || '';
  const valid = V.allValid(errors);

  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle>Edit {producer.producerNumber}</DialogTitle>
      <DialogContent dividers>
        <Stack spacing={3}>
          <FormSection title="Details">
            <TextField
              label="Producer name"
              value={f.producerName}
              onChange={set('producerName')}
              onBlur={blur('producerName')}
              error={!!showErr('producerName')}
              helperText={showErr('producerName')}
              required
            />
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
                helperText={showErr('upiId')}
              />
              <TextField
                label="Email"
                value={f.email}
                onChange={set('email')}
                onBlur={blur('email')}
                error={!!showErr('email')}
                helperText={showErr('email')}
              />
            </Stack>
            <TextField
              label="Aadhaar number"
              value={f.aadhaarNumber}
              onChange={set('aadhaarNumber', (v) => V.onlyDigits(v).slice(0, 12))}
              onBlur={blur('aadhaarNumber')}
              error={!!showErr('aadhaarNumber')}
              inputProps={{ inputMode: 'numeric', maxLength: 12 }}
              helperText={
                showErr('aadhaarNumber') ||
                (producer.aadhaarMasked ? `Current: ${producer.aadhaarMasked} — leave blank to keep` : 'Leave blank if not applicable')
              }
            />
            <TextField
              select
              label="Payment frequency"
              value={f.paymentFrequency}
              onChange={set('paymentFrequency')}
              helperText={
                f.paymentFrequency !== producer.paymentFrequency
                  ? 'Applies only to future payment runs — settled payments are unchanged.'
                  : 'Weekly producers are paid in the weekly run; monthly in the monthly run.'
              }
            >
              <MenuItem value="WEEKLY">Weekly</MenuItem>
              <MenuItem value="MONTHLY">Monthly</MenuItem>
            </TextField>
            <TextField label="Address" value={f.address} onChange={set('address')} multiline minRows={2} />
            <TextField select label="Region" value={f.regionId} onChange={set('regionId')}>
              <MenuItem value="">— none —</MenuItem>
              {regions.map((r) => (
                <MenuItem key={r.id} value={r.id}>
                  {regionShort(r)} · {r.name}
                </MenuItem>
              ))}
            </TextField>
            {regionChanged && (
              <TextField
                label="Reason for region change"
                value={f.regionChangeReason}
                onChange={set('regionChangeReason')}
                onBlur={blur('regionChangeReason')}
                error={!!showErr('regionChangeReason')}
                helperText={showErr('regionChangeReason')}
              />
            )}
          </FormSection>
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button
          variant="contained"
          disabled={busy}
          onClick={() => {
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
              regionChangeReason: und(f.regionChangeReason),
              paymentFrequency: f.paymentFrequency,
            });
          }}
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
