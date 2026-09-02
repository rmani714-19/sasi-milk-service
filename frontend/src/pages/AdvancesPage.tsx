import { useState } from 'react';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Grid from '@mui/material/Grid2';
import MenuItem from '@mui/material/MenuItem';
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
import { money, today } from '../lib/format';
import { PageHeader } from '../components/PageHeader';
import { ProducerPicker } from '../components/ProducerPicker';
import { StatusChip } from '../components/StatusChip';
import { EmptyState, SectionTitle, useToast } from '../components/ui';
import type { Producer } from '../api/types';

interface AdvanceAccount {
  id: number;
  advanceDate: string;
  originalAmount: number;
  recoveryMode: string;
  recoveryAmount?: number;
  recoveryPercentage?: number;
  outstandingAmount: number;
  status: string;
}
interface AdvanceTxn {
  id: number;
  txnType: string;
  amount: number;
  balanceAfter: number;
  txnDate: string;
}
interface ProducerAdvances {
  totalOutstanding: number;
  accounts: AdvanceAccount[];
  ledger: AdvanceTxn[];
}

export function AdvancesPage() {
  const toast = useToast();
  const qc = useQueryClient();
  const [producer, setProducer] = useState<Producer | null>(null);
  const [f, setF] = useState({ amount: '', recoveryMode: 'FIXED_PER_WEEK', recoveryAmount: '', recoveryPercentage: '', advanceDate: today() });
  const set = (k: keyof typeof f) => (e: { target: { value: string } }) => setF({ ...f, [k]: e.target.value });

  const data = useQuery({
    queryKey: ['advances', producer?.id],
    queryFn: async () => (await api.get<ProducerAdvances>('/advances', { params: { producerId: producer!.id } })).data,
    enabled: !!producer,
  });
  const create = useMutation({
    mutationFn: async () =>
      (await api.post('/advances', {
        producerId: producer!.id,
        amount: Number(f.amount),
        recoveryMode: f.recoveryMode,
        recoveryAmount: f.recoveryMode === 'FIXED_PER_WEEK' ? Number(f.recoveryAmount) : undefined,
        recoveryPercentage: f.recoveryMode === 'PERCENTAGE_OF_NET' ? Number(f.recoveryPercentage) : undefined,
        advanceDate: f.advanceDate,
      })).data,
    onSuccess: () => {
      toast.success('Advance created');
      setF({ ...f, amount: '', recoveryAmount: '', recoveryPercentage: '' });
      qc.invalidateQueries({ queryKey: ['advances'] });
    },
    onError: toast.fromError,
  });

  return (
    <>
      <PageHeader title="Advances" subtitle="Weekly recovery is applied automatically at settlement, oldest advance first" />
      <Stack spacing={3}>
        <ProducerPicker value={producer} onChange={setProducer} />
        {!producer && (
          <Card>
            <EmptyState title="Pick a producer" description="Choose a producer to issue advances and view the recovery ledger." />
          </Card>
        )}
        {producer && data.data && (
          <>
            <Card sx={{ bgcolor: 'primary.light', borderColor: 'primary.main' }}>
              <CardContent>
                <Typography color="text.secondary" variant="body2" fontWeight={500}>
                  Total outstanding
                </Typography>
                <Typography sx={{ fontSize: 28, fontWeight: 700 }} color="primary.dark">
                  {money(data.data.totalOutstanding)}
                </Typography>
              </CardContent>
            </Card>

            <Card>
              <CardContent>
                <SectionTitle sx={{ mb: 2 }}>Issue an advance</SectionTitle>
                <Grid container spacing={2}>
                  <Grid size={{ xs: 6, sm: 3 }}>
                    <TextField label="Advance amount" type="number" value={f.amount} onChange={set('amount')} />
                  </Grid>
                  <Grid size={{ xs: 6, sm: 3 }}>
                    <TextField select label="Recovery" value={f.recoveryMode} onChange={set('recoveryMode')}>
                      <MenuItem value="FIXED_PER_WEEK">Fixed / week</MenuItem>
                      <MenuItem value="PERCENTAGE_OF_NET">% of net</MenuItem>
                    </TextField>
                  </Grid>
                  <Grid size={{ xs: 6, sm: 3 }}>
                    {f.recoveryMode === 'FIXED_PER_WEEK' ? (
                      <TextField label="Amount / week" type="number" value={f.recoveryAmount} onChange={set('recoveryAmount')} />
                    ) : (
                      <TextField label="Percentage" type="number" value={f.recoveryPercentage} onChange={set('recoveryPercentage')} />
                    )}
                  </Grid>
                  <Grid size={{ xs: 6, sm: 3 }}>
                    <Button
                      fullWidth
                      variant="contained"
                      sx={{ height: '100%' }}
                      disabled={!Number(f.amount) || create.isPending}
                      onClick={() => create.mutate()}
                    >
                      Create advance
                    </Button>
                  </Grid>
                </Grid>
              </CardContent>
            </Card>

            <Box>
              <SectionTitle>Accounts</SectionTitle>
              <Card>
                <Table size="small">
                  <TableHead>
                    <TableRow>
                      <TableCell>Date</TableCell>
                      <TableCell align="right">Original</TableCell>
                      <TableCell>Recovery</TableCell>
                      <TableCell align="right">Outstanding</TableCell>
                      <TableCell>Status</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {data.data.accounts.map((a) => (
                      <TableRow key={a.id}>
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
                    {data.data.accounts.length === 0 && (
                      <TableRow>
                        <TableCell colSpan={5} sx={{ textAlign: 'center', py: 4, color: 'text.secondary' }}>
                          No advances for this producer
                        </TableCell>
                      </TableRow>
                    )}
                  </TableBody>
                </Table>
              </Card>
            </Box>
          </>
        )}
      </Stack>
    </>
  );
}
