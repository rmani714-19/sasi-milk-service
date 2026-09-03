import { useEffect, useState } from 'react';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
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

interface DType { id: number; code: string; name: string }
interface DTxn {
  id: number;
  producerId: number;
  deductionTypeId: number;
  amount: number;
  txnDate: string;
  description?: string;
  status: string;
}

export function DeductionsPage() {
  const toast = useToast();
  const qc = useQueryClient();
  const producers = useProducerLookup();
  const [filterProducer, setFilterProducer] = useState<Producer | null>(null);
  const [dialog, setDialog] = useState<{ mode: 'add' } | { mode: 'edit'; row: DTxn } | null>(null);
  const [deleting, setDeleting] = useState<DTxn | null>(null);

  const types = useQuery({ queryKey: ['deduction-types'], queryFn: async () => (await api.get<DType[]>('/deductions/types')).data });
  const list = useQuery({
    queryKey: ['deductions', filterProducer?.id ?? 'all'],
    queryFn: async () =>
      (await api.get<DTxn[]>('/deductions', { params: filterProducer ? { producerId: filterProducer.id } : {} })).data,
  });

  const invalidate = () => qc.invalidateQueries({ queryKey: ['deductions'] });

  const create = useMutation({
    mutationFn: async (body: Record<string, unknown>) => (await api.post('/deductions', body)).data,
    onSuccess: () => {
      toast.success('Deduction added');
      invalidate();
      setDialog(null);
    },
    onError: toast.fromError,
  });
  const update = useMutation({
    mutationFn: async ({ id, body }: { id: number; body: Record<string, unknown> }) =>
      (await api.put(`/deductions/${id}`, body)).data,
    onSuccess: () => {
      toast.success('Deduction updated');
      invalidate();
      setDialog(null);
    },
    onError: toast.fromError,
  });
  const del = useMutation({
    mutationFn: async (id: number) => (await api.delete(`/deductions/${id}`)).data,
    onSuccess: () => {
      toast.success('Deduction deleted');
      invalidate();
      setDeleting(null);
    },
    onError: toast.fromError,
  });

  const typeName = (id: number) => {
    const t = types.data?.find((x) => x.id === id);
    return t?.name ?? t?.code ?? id;
  };

  return (
    <>
      <PageHeader
        title="Deductions"
        subtitle="Fixed rupee amounts — cow feed, emergency cash, etc. Applied at settlement."
        action={
          <Button variant="contained" startIcon={<AddIcon />} onClick={() => setDialog({ mode: 'add' })}>
            Add deduction
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

      <Card>
        <QueryBoundary
          query={list}
          skeleton={<TableSkeleton cols={7} />}
          empty={(d) => d.length === 0}
          emptyState={
            <EmptyState
              title={filterProducer ? 'No deductions for this producer' : 'No deductions recorded'}
              description="Deductions you add appear here and are applied at the next settlement."
            />
          }
        >
          {(d) => (
            <TableContainer sx={{ maxHeight: 'calc(100vh - 300px)' }}>
              <Table size="small" stickyHeader>
                <TableHead>
                  <TableRow>
                    <TableCell>Producer</TableCell>
                    <TableCell>Date</TableCell>
                    <TableCell>Type</TableCell>
                    <TableCell align="right">Amount</TableCell>
                    <TableCell>Status</TableCell>
                    <TableCell>Note</TableCell>
                    <TableCell align="right">Actions</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {d.map((row) => (
                    <TableRow key={row.id} hover>
                      <TableCell sx={{ fontWeight: 600 }}>{producers.label(row.producerId)}</TableCell>
                      <TableCell>{row.txnDate}</TableCell>
                      <TableCell>{typeName(row.deductionTypeId)}</TableCell>
                      <TableCell align="right" sx={{ fontWeight: 600 }}>
                        {money(row.amount)}
                      </TableCell>
                      <TableCell>
                        <StatusChip status={row.status} />
                      </TableCell>
                      <TableCell>{row.description ?? ''}</TableCell>
                      <TableCell align="right">
                        {row.status === 'PENDING' ? (
                          <Stack direction="row" spacing={0.5} justifyContent="flex-end">
                            <Button size="small" onClick={() => setDialog({ mode: 'edit', row })}>
                              Edit
                            </Button>
                            <Button size="small" color="error" onClick={() => setDeleting(row)}>
                              Delete
                            </Button>
                          </Stack>
                        ) : (
                          <Typography variant="caption" color="text.secondary">
                            {row.status === 'APPLIED' ? 'settled' : '—'}
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

      <DeductionDialog
        key={dialog?.mode === 'edit' ? `edit-${dialog.row.id}` : 'add'}
        state={dialog}
        types={types.data ?? []}
        typeName={typeName}
        defaultProducer={filterProducer}
        producerLabel={dialog?.mode === 'edit' ? producers.label(dialog.row.producerId) : ''}
        busy={create.isPending || update.isPending}
        onClose={() => setDialog(null)}
        onCreate={(body) => create.mutate(body)}
        onUpdate={(id, body) => update.mutate({ id, body })}
      />

      <ConfirmDialog
        open={!!deleting}
        title="Delete this deduction?"
        message={
          deleting
            ? `${money(deleting.amount)} — ${typeName(deleting.deductionTypeId)} for ${producers.label(deleting.producerId)} will be removed. This only works before it is settled.`
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

type DialogState = { mode: 'add' } | { mode: 'edit'; row: DTxn } | null;

function DeductionDialog({
  state,
  types,
  typeName,
  defaultProducer,
  producerLabel,
  busy,
  onClose,
  onCreate,
  onUpdate,
}: {
  state: DialogState;
  types: DType[];
  typeName: (id: number) => string | number;
  defaultProducer: Producer | null;
  producerLabel: string;
  busy: boolean;
  onClose: () => void;
  onCreate: (body: Record<string, unknown>) => void;
  onUpdate: (id: number, body: Record<string, unknown>) => void;
}) {
  const editing = state?.mode === 'edit' ? state.row : null;
  const [producer, setProducer] = useState<Producer | null>(defaultProducer);
  const [f, setF] = useState({ typeCode: 'FEED', amount: '', txnDate: today(), description: '' });
  const set = (k: keyof typeof f) => (e: { target: { value: string } }) => setF({ ...f, [k]: e.target.value });

  useEffect(() => {
    if (editing) {
      setF({
        typeCode: types.find((t) => t.id === editing.deductionTypeId)?.code ?? 'FEED',
        amount: String(editing.amount),
        txnDate: editing.txnDate,
        description: editing.description ?? '',
      });
    } else {
      setProducer(defaultProducer);
      setF({ typeCode: 'FEED', amount: '', txnDate: today(), description: '' });
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [state]);

  const valid = (editing || !!producer) && Number(f.amount) > 0;

  const submit = () => {
    const body = {
      typeCode: f.typeCode,
      amount: Number(f.amount),
      txnDate: f.txnDate,
      description: f.description || undefined,
    };
    if (editing) onUpdate(editing.id, body);
    else onCreate({ producerId: producer!.id, ...body });
  };

  return (
    <Dialog open={!!state} onClose={onClose} fullWidth maxWidth="xs">
      <DialogTitle>{editing ? 'Edit deduction' : 'Add deduction'}</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          {editing ? (
            <TextField label="Producer" value={producerLabel} disabled />
          ) : (
            <ProducerPicker value={producer} onChange={setProducer} />
          )}
          <TextField select label="Type" value={f.typeCode} onChange={set('typeCode')}>
            {types.map((t) => (
              <MenuItem key={t.id} value={t.code}>
                {t.name}
              </MenuItem>
            ))}
          </TextField>
          <TextField label="Amount" type="number" value={f.amount} onChange={set('amount')} inputProps={{ inputMode: 'decimal', min: '0' }} />
          <TextField label="Date" type="date" value={f.txnDate} onChange={set('txnDate')} InputLabelProps={{ shrink: true }} />
          <TextField label="Note (optional)" value={f.description} onChange={set('description')} />
          {editing && (
            <Typography variant="caption" color="text.secondary">
              Editing {typeName(editing.deductionTypeId)} · pending
            </Typography>
          )}
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button variant="contained" disabled={busy || !valid} onClick={submit}>
          {busy ? 'Saving…' : editing ? 'Save changes' : 'Add deduction'}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
