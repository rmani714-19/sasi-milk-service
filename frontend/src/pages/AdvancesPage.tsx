import { useEffect, useState } from 'react';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogTitle from '@mui/material/DialogTitle';
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
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../lib/api';
import { useProducerLookup } from '../api/common';
import { money, today } from '../lib/format';
import { PageHeader } from '../components/PageHeader';
import { ProducerPicker } from '../components/ProducerPicker';
import { StatusChip } from '../components/StatusChip';
import { ConfirmDialog, EmptyState, QueryBoundary, TableSkeleton, useToast } from '../components/ui';
import type { Producer } from '../api/types';

interface AdvanceAccount {
  id: number;
  producerId: number;
  advanceDate: string;
  originalAmount: number;
  recoveryMode: string;
  recoveryAmount?: number;
  recoveryPercentage?: number;
  outstandingAmount: number;
  status: string;
}
interface ProducerAdvances {
  totalOutstanding: number;
  accounts: AdvanceAccount[];
}

export function AdvancesPage() {
  const toast = useToast();
  const qc = useQueryClient();
  const producers = useProducerLookup();
  const [filterProducer, setFilterProducer] = useState<Producer | null>(null);
  const [dialog, setDialog] = useState<{ mode: 'add' } | { mode: 'edit'; row: AdvanceAccount } | null>(null);
  const [deleting, setDeleting] = useState<AdvanceAccount | null>(null);

  const data = useQuery({
    queryKey: ['advances', filterProducer?.id ?? 'all'],
    queryFn: async () =>
      (await api.get<ProducerAdvances>('/advances', { params: filterProducer ? { producerId: filterProducer.id } : {} })).data,
  });

  const invalidate = () => qc.invalidateQueries({ queryKey: ['advances'] });

  const create = useMutation({
    mutationFn: async (body: Record<string, unknown>) => (await api.post('/advances', body)).data,
    onSuccess: () => {
      toast.success('Advance created');
      invalidate();
      setDialog(null);
    },
    onError: toast.fromError,
  });
  const update = useMutation({
    mutationFn: async ({ id, body }: { id: number; body: Record<string, unknown> }) =>
      (await api.put(`/advances/${id}`, body)).data,
    onSuccess: () => {
      toast.success('Advance updated');
      invalidate();
      setDialog(null);
    },
    onError: toast.fromError,
  });
  const del = useMutation({
    mutationFn: async (id: number) => (await api.delete(`/advances/${id}`)).data,
    onSuccess: () => {
      toast.success('Advance deleted');
      invalidate();
      setDeleting(null);
    },
    onError: toast.fromError,
  });

  const untouched = (a: AdvanceAccount) => a.status === 'ACTIVE' && a.outstandingAmount === a.originalAmount;

  return (
    <>
      <PageHeader
        title="Advances"
        subtitle="Weekly recovery is applied automatically at settlement, oldest advance first"
        action={
          <Button variant="contained" startIcon={<AddIcon />} onClick={() => setDialog({ mode: 'add' })}>
            Create advance
          </Button>
        }
      />

      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} sx={{ mb: 2 }} alignItems={{ sm: 'center' }}>
        <Box sx={{ minWidth: 280, flex: { xs: 1, sm: 'unset' } }}>
          <ProducerPicker value={filterProducer} onChange={setFilterProducer} label="Filter by producer" />
        </Box>
        {filterProducer && (
          <Button size="small" onClick={() => setFilterProducer(null)}>
            Clear filter
          </Button>
        )}
      </Stack>

      <Card sx={{ mb: 3, bgcolor: 'primary.light', borderColor: 'primary.main' }}>
        <CardContent>
          <Typography color="text.secondary" variant="body2" fontWeight={500}>
            {filterProducer ? 'Outstanding for this producer' : 'Total outstanding (all producers)'}
          </Typography>
          <Typography sx={{ fontSize: 28, fontWeight: 700 }} color="primary.dark">
            {money(data.data?.totalOutstanding)}
          </Typography>
        </CardContent>
      </Card>

      <Card>
        <QueryBoundary
          query={data}
          skeleton={<TableSkeleton cols={7} />}
          empty={(d) => d.accounts.length === 0}
          emptyState={
            <EmptyState
              title={filterProducer ? 'No advances for this producer' : 'No advances issued'}
              description="Advances you issue appear here and are recovered automatically at settlement."
            />
          }
        >
          {(d) => (
            <TableContainer sx={{ maxHeight: 'calc(100vh - 360px)' }}>
              <Table size="small" stickyHeader>
                <TableHead>
                  <TableRow>
                    <TableCell>Producer</TableCell>
                    <TableCell>Date</TableCell>
                    <TableCell align="right">Original</TableCell>
                    <TableCell>Recovery</TableCell>
                    <TableCell align="right">Outstanding</TableCell>
                    <TableCell>Status</TableCell>
                    <TableCell align="right">Actions</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {d.accounts.map((a) => (
                    <TableRow key={a.id} hover>
                      <TableCell sx={{ fontWeight: 600 }}>{producers.label(a.producerId)}</TableCell>
                      <TableCell>{a.advanceDate}</TableCell>
                      <TableCell align="right">{money(a.originalAmount)}</TableCell>
                      <TableCell>
                        {a.recoveryMode === 'FIXED_PER_WEEK' ? `${money(a.recoveryAmount)}/wk` : `${a.recoveryPercentage}%`}
                      </TableCell>
                      <TableCell align="right" sx={{ fontWeight: 600 }}>
                        {money(a.outstandingAmount)}
                      </TableCell>
                      <TableCell>
                        <StatusChip status={a.status} />
                      </TableCell>
                      <TableCell align="right">
                        {a.status === 'ACTIVE' ? (
                          <Stack direction="row" spacing={0.5} justifyContent="flex-end">
                            <Button size="small" onClick={() => setDialog({ mode: 'edit', row: a })}>
                              Edit
                            </Button>
                            <Button
                              size="small"
                              color="error"
                              disabled={!untouched(a)}
                              title={untouched(a) ? '' : 'Recovery has started — cannot delete'}
                              onClick={() => setDeleting(a)}
                            >
                              Delete
                            </Button>
                          </Stack>
                        ) : (
                          <Typography variant="caption" color="text.secondary">
                            —
                          </Typography>
                        )}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
          )}
        </QueryBoundary>
      </Card>

      <AdvanceDialog
        key={dialog?.mode === 'edit' ? `edit-${dialog.row.id}` : 'add'}
        state={dialog}
        defaultProducer={filterProducer}
        producerLabel={dialog?.mode === 'edit' ? producers.label(dialog.row.producerId) : ''}
        principalLocked={dialog?.mode === 'edit' ? !untouched(dialog.row) : false}
        busy={create.isPending || update.isPending}
        onClose={() => setDialog(null)}
        onCreate={(body) => create.mutate(body)}
        onUpdate={(id, body) => update.mutate({ id, body })}
      />

      <ConfirmDialog
        open={!!deleting}
        title="Delete this advance?"
        message={
          deleting
            ? `${money(deleting.originalAmount)} advance for ${producers.label(deleting.producerId)} will be removed. Only possible before any recovery.`
            : ''
        }
        confirmLabel="Delete"
        destructive
        busy={del.isPending}
        onConfirm={() => deleting && del.mutate(deleting.id)}
        onClose={() => setDeleting(null)}
      />
    </>
  );
}

type DialogState = { mode: 'add' } | { mode: 'edit'; row: AdvanceAccount } | null;

function AdvanceDialog({
  state,
  defaultProducer,
  producerLabel,
  principalLocked,
  busy,
  onClose,
  onCreate,
  onUpdate,
}: {
  state: DialogState;
  defaultProducer: Producer | null;
  producerLabel: string;
  principalLocked: boolean;
  busy: boolean;
  onClose: () => void;
  onCreate: (body: Record<string, unknown>) => void;
  onUpdate: (id: number, body: Record<string, unknown>) => void;
}) {
  const editing = state?.mode === 'edit' ? state.row : null;
  const [producer, setProducer] = useState<Producer | null>(defaultProducer);
  const [f, setF] = useState({
    amount: '',
    recoveryMode: 'FIXED_PER_WEEK',
    recoveryAmount: '',
    recoveryPercentage: '',
    advanceDate: today(),
  });
  const set = (k: keyof typeof f) => (e: { target: { value: string } }) => setF({ ...f, [k]: e.target.value });

  useEffect(() => {
    if (editing) {
      setF({
        amount: String(editing.originalAmount),
        recoveryMode: editing.recoveryMode,
        recoveryAmount: editing.recoveryAmount != null ? String(editing.recoveryAmount) : '',
        recoveryPercentage: editing.recoveryPercentage != null ? String(editing.recoveryPercentage) : '',
        advanceDate: editing.advanceDate,
      });
    } else {
      setProducer(defaultProducer);
      setF({ amount: '', recoveryMode: 'FIXED_PER_WEEK', recoveryAmount: '', recoveryPercentage: '', advanceDate: today() });
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [state]);

  const recoveryOk =
    f.recoveryMode === 'FIXED_PER_WEEK' ? Number(f.recoveryAmount) > 0 : Number(f.recoveryPercentage) > 0;
  const valid = (editing || !!producer) && Number(f.amount) > 0 && recoveryOk;

  const submit = () => {
    const body = {
      amount: Number(f.amount),
      recoveryMode: f.recoveryMode,
      recoveryAmount: f.recoveryMode === 'FIXED_PER_WEEK' ? Number(f.recoveryAmount) : undefined,
      recoveryPercentage: f.recoveryMode === 'PERCENTAGE_OF_NET' ? Number(f.recoveryPercentage) : undefined,
      advanceDate: f.advanceDate,
    };
    if (editing) onUpdate(editing.id, body);
    else onCreate({ producerId: producer!.id, ...body });
  };

  return (
    <Dialog open={!!state} onClose={onClose} fullWidth maxWidth="xs">
      <DialogTitle>{editing ? 'Edit advance' : 'Create advance'}</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          {editing ? (
            <TextField label="Producer" value={producerLabel} disabled />
          ) : (
            <ProducerPicker value={producer} onChange={setProducer} />
          )}
          <TextField
            label="Advance amount"
            type="number"
            value={f.amount}
            onChange={set('amount')}
            disabled={principalLocked}
            helperText={principalLocked ? 'Recovery has started — amount is locked' : undefined}
            inputProps={{ inputMode: 'decimal', min: '0' }}
          />
          <TextField select label="Recovery" value={f.recoveryMode} onChange={set('recoveryMode')}>
            <MenuItem value="FIXED_PER_WEEK">Fixed / week</MenuItem>
            <MenuItem value="PERCENTAGE_OF_NET">% of net</MenuItem>
          </TextField>
          {f.recoveryMode === 'FIXED_PER_WEEK' ? (
            <TextField label="Amount / week" type="number" value={f.recoveryAmount} onChange={set('recoveryAmount')} inputProps={{ inputMode: 'decimal', min: '0' }} />
          ) : (
            <TextField label="Percentage" type="number" value={f.recoveryPercentage} onChange={set('recoveryPercentage')} inputProps={{ inputMode: 'decimal', min: '0', max: '100' }} />
          )}
          <TextField label="Advance date" type="date" value={f.advanceDate} onChange={set('advanceDate')} InputLabelProps={{ shrink: true }} />
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button variant="contained" disabled={busy || !valid} onClick={submit}>
          {busy ? 'Saving…' : editing ? 'Save changes' : 'Create advance'}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
