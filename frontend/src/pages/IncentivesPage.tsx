import { useMemo, useState } from 'react';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogTitle from '@mui/material/DialogTitle';
import Grid from '@mui/material/Grid2';
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
import CardGiftcardOutlinedIcon from '@mui/icons-material/CardGiftcardOutlined';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../lib/api';
import { money } from '../lib/format';
import { PageHeader } from '../components/PageHeader';
import { FormSection } from '../components/FormSection';
import { StatusChip } from '../components/StatusChip';
import { ConfirmDialog, EmptyState, QueryBoundary, TableSkeleton, useToast } from '../components/ui';
import { useAuth } from '../lib/auth';
import type { Incentive, IncentiveTarget, IncentiveType } from '../api/types';

const TYPE_LABELS: Record<IncentiveType, string> = {
  PER_LITRE: 'Per Litre',
  FIXED_AMOUNT: 'Fixed Amount',
  VOLUME_BONUS: 'Volume Bonus',
  DAILY_BONUS: 'Daily Bonus',
  WEEKLY_BONUS: 'Weekly Bonus',
  MONTHLY_BONUS: 'Monthly Bonus',
  RETENTION_BONUS: 'Retention Bonus',
  NEW_PRODUCER_BONUS: 'New Producer Bonus',
  COMPETITOR_RETENTION: 'Competitor Retention',
};
const TYPES = Object.keys(TYPE_LABELS) as IncentiveType[];

const todayLocal = () => {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
};
const amountLabel = (i: Pick<Incentive, 'amount' | 'incentiveType'>) =>
  i.incentiveType === 'PER_LITRE' ? `${money(i.amount)} / L` : money(i.amount);

export function IncentivesPage() {
  const toast = useToast();
  const qc = useQueryClient();
  const { can } = useAuth();
  const canManage = can('MANAGE_INCENTIVES');
  const canDelete = can('ALL');

  const [search, setSearch] = useState('');
  const [target, setTarget] = useState('ALL');
  const [type, setType] = useState('ALL');
  const [status, setStatus] = useState('ALL');

  const [editing, setEditing] = useState<Incentive | null>(null);
  const [adding, setAdding] = useState(false);
  const [confirmDeactivate, setConfirmDeactivate] = useState<Incentive | null>(null);
  const [confirmDelete, setConfirmDelete] = useState<Incentive | null>(null);

  const list = useQuery({
    queryKey: ['incentives', 'catalog'],
    queryFn: async () => (await api.get<Incentive[]>('/incentives')).data,
  });

  const invalidate = () => qc.invalidateQueries({ queryKey: ['incentives', 'catalog'] });

  const save = useMutation({
    mutationFn: async ({ id, body }: { id?: number; body: Record<string, unknown> }) =>
      id ? (await api.put(`/incentives/${id}`, body)).data : (await api.post('/incentives', body)).data,
    onSuccess: (_d, v) => {
      toast.success(v.id ? 'Incentive updated' : 'Incentive added');
      invalidate();
      setAdding(false);
      setEditing(null);
    },
    onError: toast.fromError,
  });
  const lifecycle = useMutation({
    mutationFn: async ({ id, action }: { id: number; action: 'activate' | 'deactivate' }) =>
      (await api.post(`/incentives/${id}/${action}`)).data,
    onSuccess: (_d, v) => {
      toast.success(v.action === 'activate' ? 'Incentive activated' : 'Incentive deactivated');
      invalidate();
      setConfirmDeactivate(null);
    },
    onError: toast.fromError,
  });
  const remove = useMutation({
    mutationFn: async (id: number) => (await api.delete(`/incentives/${id}`)).data,
    onSuccess: () => {
      toast.success('Incentive deleted');
      invalidate();
      setConfirmDelete(null);
    },
    onError: toast.fromError,
  });

  const rows = useMemo(() => {
    const q = search.trim().toLowerCase();
    return (list.data ?? []).filter(
      (i) =>
        (!q || i.name.toLowerCase().includes(q) || (i.description ?? '').toLowerCase().includes(q)) &&
        (target === 'ALL' || i.target === target) &&
        (type === 'ALL' || i.incentiveType === type) &&
        (status === 'ALL' || i.effectiveStatus === status),
    );
  }, [list.data, search, target, type, status]);

  return (
    <>
      <PageHeader
        title="Incentives"
        subtitle="Managed incentive campaigns with an effective period"
        action={
          canManage && (
            <Button variant="contained" startIcon={<AddIcon />} onClick={() => setAdding(true)}>
              Add Incentive
            </Button>
          )
        }
      />

      <Card sx={{ mb: 2, p: 1.5 }}>
        <Stack direction={{ xs: 'column', md: 'row' }} spacing={1.5} useFlexGap flexWrap="wrap">
          <TextField
            size="small"
            placeholder="Search incentive…"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            sx={{ minWidth: 220, flex: 1 }}
            InputProps={{
              startAdornment: (
                <InputAdornment position="start">
                  <SearchIcon fontSize="small" color="action" />
                </InputAdornment>
              ),
            }}
          />
          <TextField select size="small" label="Target" value={target} onChange={(e) => setTarget(e.target.value)} sx={{ minWidth: 140 }}>
            <MenuItem value="ALL">All targets</MenuItem>
            <MenuItem value="PRODUCER">Producer</MenuItem>
            <MenuItem value="MEDIATOR">Mediator</MenuItem>
          </TextField>
          <TextField select size="small" label="Incentive type" value={type} onChange={(e) => setType(e.target.value)} sx={{ minWidth: 180 }}>
            <MenuItem value="ALL">All types</MenuItem>
            {TYPES.map((t) => (
              <MenuItem key={t} value={t}>
                {TYPE_LABELS[t]}
              </MenuItem>
            ))}
          </TextField>
          <TextField select size="small" label="Status" value={status} onChange={(e) => setStatus(e.target.value)} sx={{ minWidth: 140 }}>
            <MenuItem value="ALL">All statuses</MenuItem>
            <MenuItem value="ACTIVE">Active</MenuItem>
            <MenuItem value="INACTIVE">Inactive</MenuItem>
            <MenuItem value="EXPIRED">Expired</MenuItem>
          </TextField>
        </Stack>
      </Card>

      <Card>
        <QueryBoundary
          query={list}
          skeleton={<TableSkeleton cols={9} />}
          empty={() => rows.length === 0}
          emptyState={
            <EmptyState
              icon={<CardGiftcardOutlinedIcon />}
              title="No incentives found."
              description={
                search || target !== 'ALL' || type !== 'ALL' || status !== 'ALL'
                  ? 'Adjust the search or filters.'
                  : 'Create your first incentive campaign.'
              }
              action={
                canManage &&
                !search &&
                target === 'ALL' &&
                type === 'ALL' &&
                status === 'ALL' && (
                  <Button variant="contained" startIcon={<AddIcon />} onClick={() => setAdding(true)}>
                    Add Incentive
                  </Button>
                )
              }
            />
          }
        >
          {() => (
            <TableContainer sx={{ maxHeight: 'calc(100vh - 300px)' }}>
              <Table size="small" stickyHeader>
                <TableHead>
                  <TableRow>
                    <TableCell>Incentive Name</TableCell>
                    <TableCell>Target</TableCell>
                    <TableCell>Type</TableCell>
                    <TableCell align="right">Amount</TableCell>
                    <TableCell>Effective From</TableCell>
                    <TableCell>Effective To</TableCell>
                    <TableCell>Status</TableCell>
                    <TableCell>Created By</TableCell>
                    <TableCell align="right">Actions</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {rows.map((i) => (
                    <TableRow key={i.id} hover>
                      <TableCell sx={{ fontWeight: 600 }}>
                        {i.name}
                        {i.description && (
                          <Typography variant="caption" color="text.secondary" display="block">
                            {i.description}
                          </Typography>
                        )}
                      </TableCell>
                      <TableCell>{i.target === 'MEDIATOR' ? 'Mediator' : 'Producer'}</TableCell>
                      <TableCell>{TYPE_LABELS[i.incentiveType]}</TableCell>
                      <TableCell align="right" sx={{ fontWeight: 700, whiteSpace: 'nowrap' }}>
                        {amountLabel(i)}
                      </TableCell>
                      <TableCell sx={{ whiteSpace: 'nowrap' }}>{i.effectiveFrom}</TableCell>
                      <TableCell sx={{ whiteSpace: 'nowrap' }}>{i.effectiveTo}</TableCell>
                      <TableCell>
                        <StatusChip status={i.effectiveStatus} />
                      </TableCell>
                      <TableCell>{i.createdByName ?? '—'}</TableCell>
                      <TableCell align="right">
                        <Stack direction="row" spacing={0.5} justifyContent="flex-end">
                          {canManage && (
                            <Button size="small" onClick={() => setEditing(i)}>
                              Edit
                            </Button>
                          )}
                          {canManage && i.status === 'INACTIVE' && (
                            <Button size="small" onClick={() => lifecycle.mutate({ id: i.id, action: 'activate' })}>
                              Activate
                            </Button>
                          )}
                          {canManage && i.status === 'ACTIVE' && (
                            <Button size="small" color="warning" onClick={() => setConfirmDeactivate(i)}>
                              Deactivate
                            </Button>
                          )}
                          {canDelete && !i.used && (
                            <Button size="small" color="error" onClick={() => setConfirmDelete(i)}>
                              Delete
                            </Button>
                          )}
                        </Stack>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
          )}
        </QueryBoundary>
      </Card>

      <IncentiveDialog
        key={editing ? `edit-${editing.id}` : 'add'}
        open={adding || !!editing}
        incentive={editing}
        busy={save.isPending}
        onClose={() => {
          setAdding(false);
          setEditing(null);
        }}
        onSubmit={(body) => save.mutate({ id: editing?.id, body })}
      />

      <ConfirmDialog
        open={!!confirmDeactivate}
        title="Deactivate incentive?"
        message="Future eligibility stops. Historical transactions and approved settlements are unchanged."
        confirmLabel="Deactivate"
        destructive
        busy={lifecycle.isPending}
        onConfirm={() => confirmDeactivate && lifecycle.mutate({ id: confirmDeactivate.id, action: 'deactivate' })}
        onClose={() => setConfirmDeactivate(null)}
      />
      <ConfirmDialog
        open={!!confirmDelete}
        title="Delete this incentive?"
        message={`"${confirmDelete?.name ?? ''}" will be permanently removed. This is only possible because it has never been active during a live period.`}
        confirmLabel="Delete"
        destructive
        busy={remove.isPending}
        onConfirm={() => confirmDelete && remove.mutate(confirmDelete.id)}
        onClose={() => setConfirmDelete(null)}
      />
    </>
  );
}

const BLANK = {
  name: '',
  description: '',
  target: 'PRODUCER' as IncentiveTarget,
  incentiveType: 'PER_LITRE' as IncentiveType,
  amount: '',
  effectiveFrom: todayLocal(),
  effectiveTo: '',
  status: 'ACTIVE' as 'ACTIVE' | 'INACTIVE',
};

function IncentiveDialog({
  open,
  incentive,
  busy,
  onClose,
  onSubmit,
}: {
  open: boolean;
  incentive: Incentive | null;
  busy: boolean;
  onClose: () => void;
  onSubmit: (body: Record<string, unknown>) => void;
}) {
  const [f, setF] = useState(() =>
    incentive
      ? {
          name: incentive.name,
          description: incentive.description ?? '',
          target: incentive.target,
          incentiveType: incentive.incentiveType,
          amount: String(incentive.amount),
          effectiveFrom: incentive.effectiveFrom,
          effectiveTo: incentive.effectiveTo,
          status: incentive.status,
        }
      : BLANK,
  );
  const [tried, setTried] = useState(false);
  const set = (k: keyof typeof f) => (e: { target: { value: string } }) => setF((s) => ({ ...s, [k]: e.target.value }));

  const errors = {
    name: !f.name.trim() ? 'Incentive name is required.' : '',
    amount: f.amount === '' || Number(f.amount) < 0 || Number.isNaN(Number(f.amount)) ? 'Enter a valid amount (0 or more).' : '',
    effectiveFrom: !f.effectiveFrom ? 'Effective From date is required.' : '',
    effectiveTo: !f.effectiveTo
      ? 'Effective To date is required.'
      : f.effectiveFrom && f.effectiveTo < f.effectiveFrom
        ? 'Effective To must be on or after Effective From.'
        : '',
  };
  const showErr = (k: keyof typeof errors) => (tried ? errors[k] : '');
  const valid = !Object.values(errors).some(Boolean);

  const submit = () => {
    setTried(true);
    if (!valid) return;
    onSubmit({
      name: f.name.trim(),
      target: f.target,
      incentiveType: f.incentiveType,
      amount: Number(f.amount),
      effectiveFrom: f.effectiveFrom,
      effectiveTo: f.effectiveTo,
      status: f.status,
      description: f.description.trim() || undefined,
    });
  };

  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle>{incentive ? 'Edit Incentive' : 'Add Incentive'}</DialogTitle>
      <DialogContent dividers>
        {incentive?.used && (
          <Alert severity="warning" sx={{ mb: 2 }}>
            This incentive has already been active during a live period. Changes apply only to future incentive
            calculations — historical payments will not be changed.
          </Alert>
        )}
        <Stack spacing={3}>
          <FormSection title="Basic Information">
            <TextField
              label="Incentive Name"
              value={f.name}
              onChange={set('name')}
              error={!!showErr('name')}
              helperText={showErr('name')}
              autoFocus
              required
            />
            <TextField label="Description" value={f.description} onChange={set('description')} multiline minRows={2} />
          </FormSection>

          <FormSection title="Incentive Configuration">
            <Grid container spacing={2}>
              <Grid size={{ xs: 12, sm: 6 }}>
                <TextField
                  select
                  fullWidth
                  label="Target"
                  value={f.target}
                  onChange={set('target')}
                  disabled={!!incentive}
                  helperText={incentive ? 'Fixed after creation' : ' '}
                >
                  <MenuItem value="PRODUCER">Producer</MenuItem>
                  <MenuItem value="MEDIATOR">Mediator</MenuItem>
                </TextField>
              </Grid>
              <Grid size={{ xs: 12, sm: 6 }}>
                <TextField
                  select
                  fullWidth
                  label="Incentive Type"
                  value={f.incentiveType}
                  onChange={set('incentiveType')}
                  disabled={!!incentive}
                  helperText={incentive ? 'Fixed after creation' : ' '}
                >
                  {TYPES.map((t) => (
                    <MenuItem key={t} value={t}>
                      {TYPE_LABELS[t]}
                    </MenuItem>
                  ))}
                </TextField>
              </Grid>
              <Grid size={{ xs: 12, sm: 6 }}>
                <TextField
                  fullWidth
                  label="Incentive Amount"
                  type="number"
                  value={f.amount}
                  onChange={set('amount')}
                  error={!!showErr('amount')}
                  helperText={showErr('amount') || (f.incentiveType === 'PER_LITRE' ? 'Per litre (₹)' : 'Amount (₹)')}
                  inputProps={{ min: 0, step: '0.01', inputMode: 'decimal' }}
                  required
                />
              </Grid>
            </Grid>
          </FormSection>

          <FormSection title="Effective Period">
            <Grid container spacing={2}>
              <Grid size={{ xs: 12, sm: 6 }}>
                <TextField
                  fullWidth
                  label="Effective From"
                  type="date"
                  value={f.effectiveFrom}
                  onChange={set('effectiveFrom')}
                  InputLabelProps={{ shrink: true }}
                  error={!!showErr('effectiveFrom')}
                  helperText={showErr('effectiveFrom')}
                  required
                />
                <Button size="small" onClick={() => setF((s) => ({ ...s, effectiveFrom: todayLocal() }))} sx={{ mt: 0.5 }}>
                  Today
                </Button>
              </Grid>
              <Grid size={{ xs: 12, sm: 6 }}>
                <TextField
                  fullWidth
                  label="Effective To"
                  type="date"
                  value={f.effectiveTo}
                  onChange={set('effectiveTo')}
                  InputLabelProps={{ shrink: true }}
                  inputProps={{ min: f.effectiveFrom || undefined }}
                  error={!!showErr('effectiveTo')}
                  helperText={showErr('effectiveTo') || 'Inclusive'}
                  required
                />
              </Grid>
            </Grid>
          </FormSection>

          <FormSection title="Status">
            <TextField select label="Status" value={f.status} onChange={set('status')} sx={{ maxWidth: 220 }}>
              <MenuItem value="ACTIVE">Active</MenuItem>
              <MenuItem value="INACTIVE">Inactive</MenuItem>
            </TextField>
          </FormSection>

          {tried && !valid && (
            <Box>
              <Typography variant="caption" color="error">
                Please fix the highlighted fields.
              </Typography>
            </Box>
          )}
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button variant="contained" disabled={busy} onClick={submit}>
          {busy ? 'Saving…' : incentive ? 'Confirm Changes' : 'Save Incentive'}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
