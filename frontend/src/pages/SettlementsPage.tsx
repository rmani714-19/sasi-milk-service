import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogTitle from '@mui/material/DialogTitle';
import Stack from '@mui/material/Stack';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import TextField from '@mui/material/TextField';
import AddIcon from '@mui/icons-material/Add';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../lib/api';
import { litres, money } from '../lib/format';
import { PageHeader } from '../components/PageHeader';
import { batchStatusLabel } from '../lib/paymentLabels';
import { StatusChip } from '../components/StatusChip';
import { EmptyState, QueryBoundary, TableSkeleton, useToast } from '../components/ui';
import type { SettlementSummary } from '../api/types';

export function SettlementsPage() {
  const nav = useNavigate();
  const toast = useToast();
  const qc = useQueryClient();
  const [open, setOpen] = useState(false);
  const [period, setPeriod] = useState({ periodStart: '', periodEnd: '' });

  const list = useQuery({
    queryKey: ['settlements'],
    queryFn: async () => (await api.get<SettlementSummary[]>('/settlements')).data,
  });
  const create = useMutation({
    mutationFn: async () => (await api.post<{ internalId: number }>('/settlements', period)).data,
    onSuccess: (d) => {
      toast.success('Settlement created');
      qc.invalidateQueries({ queryKey: ['settlements'] });
      setOpen(false);
      nav(`/settlements/${d.internalId}`);
    },
    onError: toast.fromError,
  });

  return (
    <>
      <PageHeader
        title="Settlement"
        subtitle="Weekly producer settlement runs and payouts"
        action={
          <Button variant="contained" startIcon={<AddIcon />} onClick={() => setOpen(true)}>
            New settlement
          </Button>
        }
      />
      <Card>
        <QueryBoundary
          query={list}
          skeleton={<TableSkeleton cols={6} />}
          empty={(d) => d.length === 0}
          emptyState={
            <EmptyState
              title="No settlements yet"
              description="Create a settlement for a date range to calculate producer payouts."
              action={
                <Button variant="contained" startIcon={<AddIcon />} onClick={() => setOpen(true)}>
                  New settlement
                </Button>
              }
            />
          }
        >
          {(d) => (
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell>Number</TableCell>
                  <TableCell>Period</TableCell>
                  <TableCell align="right">Producers</TableCell>
                  <TableCell align="right">Litres</TableCell>
                  <TableCell align="right">Net payable</TableCell>
                  <TableCell>Status</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {d.map((s) => (
                  <TableRow key={s.id} hover sx={{ cursor: 'pointer' }} onClick={() => nav(`/settlements/${s.internalId}`)}>
                    <TableCell sx={{ fontWeight: 600 }}>{s.settlementNumber}</TableCell>
                    <TableCell>
                      {s.periodStart} → {s.periodEnd}
                    </TableCell>
                    <TableCell align="right">{s.totalProducers}</TableCell>
                    <TableCell align="right">{litres(s.totalLitres)}</TableCell>
                    <TableCell align="right" sx={{ fontWeight: 700 }}>
                      {money(s.totalNetPayable)}
                    </TableCell>
                    <TableCell>
                      <StatusChip status={s.status} label={batchStatusLabel(s.status)} />
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </QueryBoundary>
      </Card>

      <Dialog open={open} onClose={() => setOpen(false)} fullWidth maxWidth="xs">
        <DialogTitle>New settlement</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }}>
            <TextField label="Period start" type="date" value={period.periodStart} onChange={(e) => setPeriod({ ...period, periodStart: e.target.value })} InputLabelProps={{ shrink: true }} />
            <TextField label="Period end" type="date" value={period.periodEnd} onChange={(e) => setPeriod({ ...period, periodEnd: e.target.value })} InputLabelProps={{ shrink: true }} />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setOpen(false)}>Cancel</Button>
          <Button variant="contained" disabled={!period.periodStart || !period.periodEnd || create.isPending} onClick={() => create.mutate()}>
            Create
          </Button>
        </DialogActions>
      </Dialog>
    </>
  );
}
