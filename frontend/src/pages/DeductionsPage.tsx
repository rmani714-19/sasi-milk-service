import { useState } from 'react';
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
import AddIcon from '@mui/icons-material/Add';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../lib/api';
import { useProducerLookup } from '../api/common';
import { money, today } from '../lib/format';
import { PageHeader } from '../components/PageHeader';
import { ProducerPicker } from '../components/ProducerPicker';
import { StatusChip } from '../components/StatusChip';
import { EmptyState, QueryBoundary, TableSkeleton, useToast } from '../components/ui';
import type { Producer } from '../api/types';

interface DType { id: number; code: string; name: string }
interface DTxn { id: number; producerId: number; deductionTypeId: number; amount: number; txnDate: string; description?: string; status: string }

export function DeductionsPage() {
  const toast = useToast();
  const qc = useQueryClient();
  const producers = useProducerLookup();
  const [filterProducer, setFilterProducer] = useState<Producer | null>(null);
  const [addOpen, setAddOpen] = useState(false);

  const types = useQuery({ queryKey: ['deduction-types'], queryFn: async () => (await api.get<DType[]>('/deductions/types')).data });
  const list = useQuery({
    queryKey: ['deductions', filterProducer?.id ?? 'all'],
    queryFn: async () =>
      (await api.get<DTxn[]>('/deductions', { params: filterProducer ? { producerId: filterProducer.id } : {} })).data,
  });

  const create = useMutation({
    mutationFn: async (body: Record<string, unknown>) => (await api.post('/deductions', body)).data,
    onSuccess: () => {
      toast.success('Deduction added');
      qc.invalidateQueries({ queryKey: ['deductions'] });
      setAddOpen(false);
    },
    onError: toast.fromError,
  });

  const typeName = (id: number) => types.data?.find((t) => t.id === id)?.name ?? types.data?.find((t) => t.id === id)?.code ?? id;

  return (
    <>
      <PageHeader
        title="Deductions"
        subtitle="Fixed rupee amounts — cow feed, emergency cash, etc. Applied at settlement."
        action={
          <Button variant="contained" startIcon={<AddIcon />} onClick={() => setAddOpen(true)}>
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
          skeleton={<TableSkeleton cols={6} />}
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
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
          )}
        </QueryBoundary>
      </Card>

      <AddDeductionDialog
        open={addOpen}
        types={types.data ?? []}
        defaultProducer={filterProducer}
        busy={create.isPending}
        onClose={() => setAddOpen(false)}
        onSubmit={(body) => create.mutate(body)}
      />
    </>
  );
}

function AddDeductionDialog({
  open,
  types,
  defaultProducer,
  busy,
  onClose,
  onSubmit,
}: {
  open: boolean;
  types: DType[];
  defaultProducer: Producer | null;
  busy: boolean;
  onClose: () => void;
  onSubmit: (body: Record<string, unknown>) => void;
}) {
  const [producer, setProducer] = useState<Producer | null>(defaultProducer);
  const [f, setF] = useState({ typeCode: 'FEED', amount: '', txnDate: today(), description: '' });
  const set = (k: keyof typeof f) => (e: { target: { value: string } }) => setF({ ...f, [k]: e.target.value });
  const valid = !!producer && Number(f.amount) > 0;

  return (
    <Dialog
      open={open}
      onClose={onClose}
      fullWidth
      maxWidth="xs"
      TransitionProps={{
        onEnter: () => {
          setProducer(defaultProducer);
          setF({ typeCode: 'FEED', amount: '', txnDate: today(), description: '' });
        },
      }}
    >
      <DialogTitle>Add deduction</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          <ProducerPicker value={producer} onChange={setProducer} />
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
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button
          variant="contained"
          disabled={busy || !valid}
          onClick={() =>
            onSubmit({
              producerId: producer!.id,
              typeCode: f.typeCode,
              amount: Number(f.amount),
              txnDate: f.txnDate,
              description: f.description || undefined,
            })
          }
        >
          {busy ? 'Saving…' : 'Add deduction'}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
