import { useState } from 'react';
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
import { EmptyState, QueryBoundary, TableSkeleton, useToast } from '../components/ui';
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
  const [addOpen, setAddOpen] = useState(false);

  const data = useQuery({
    queryKey: ['advances', filterProducer?.id ?? 'all'],
    queryFn: async () =>
      (await api.get<ProducerAdvances>('/advances', { params: filterProducer ? { producerId: filterProducer.id } : {} })).data,
  });

  const create = useMutation({
    mutationFn: async (body: Record<string, unknown>) => (await api.post('/advances', body)).data,
    onSuccess: () => {
      toast.success('Advance created');
      qc.invalidateQueries({ queryKey: ['advances'] });
      setAddOpen(false);
    },
    onError: toast.fromError,
  });

  return (
    <>
      <PageHeader
        title="Advances"
        subtitle="Weekly recovery is applied automatically at settlement, oldest advance first"
        action={
          <Button variant="contained" startIcon={<AddIcon />} onClick={() => setAddOpen(true)}>
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
          skeleton={<TableSkeleton cols={6} />}
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
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
          )}
        </QueryBoundary>
      </Card>

      <CreateAdvanceDialog
        open={addOpen}
        defaultProducer={filterProducer}
        busy={create.isPending}
        onClose={() => setAddOpen(false)}
        onSubmit={(body) => create.mutate(body)}
      />
    </>
  );
}

function CreateAdvanceDialog({
  open,
  defaultProducer,
  busy,
  onClose,
  onSubmit,
}: {
  open: boolean;
  defaultProducer: Producer | null;
  busy: boolean;
  onClose: () => void;
  onSubmit: (body: Record<string, unknown>) => void;
}) {
  const [producer, setProducer] = useState<Producer | null>(defaultProducer);
  const [f, setF] = useState({
    amount: '',
    recoveryMode: 'FIXED_PER_WEEK',
    recoveryAmount: '',
    recoveryPercentage: '',
    advanceDate: today(),
  });
  const set = (k: keyof typeof f) => (e: { target: { value: string } }) => setF({ ...f, [k]: e.target.value });

  const valid =
    !!producer &&
    Number(f.amount) > 0 &&
    (f.recoveryMode === 'FIXED_PER_WEEK' ? Number(f.recoveryAmount) > 0 : Number(f.recoveryPercentage) > 0);

  return (
    <Dialog
      open={open}
      onClose={onClose}
      fullWidth
      maxWidth="xs"
      TransitionProps={{
        onEnter: () => {
          setProducer(defaultProducer);
          setF({ amount: '', recoveryMode: 'FIXED_PER_WEEK', recoveryAmount: '', recoveryPercentage: '', advanceDate: today() });
        },
      }}
    >
      <DialogTitle>Create advance</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          <ProducerPicker value={producer} onChange={setProducer} />
          <TextField label="Advance amount" type="number" value={f.amount} onChange={set('amount')} inputProps={{ inputMode: 'decimal', min: '0' }} />
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
        <Button
          variant="contained"
          disabled={busy || !valid}
          onClick={() =>
            onSubmit({
              producerId: producer!.id,
              amount: Number(f.amount),
              recoveryMode: f.recoveryMode,
              recoveryAmount: f.recoveryMode === 'FIXED_PER_WEEK' ? Number(f.recoveryAmount) : undefined,
              recoveryPercentage: f.recoveryMode === 'PERCENTAGE_OF_NET' ? Number(f.recoveryPercentage) : undefined,
              advanceDate: f.advanceDate,
            })
          }
        >
          {busy ? 'Saving…' : 'Create advance'}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
