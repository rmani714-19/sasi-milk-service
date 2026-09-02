import { useState } from 'react';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Grid from '@mui/material/Grid2';
import Stack from '@mui/material/Stack';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import TextField from '@mui/material/TextField';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../lib/api';
import { rate, today } from '../lib/format';
import { PageHeader } from '../components/PageHeader';
import { ProducerPicker } from '../components/ProducerPicker';
import { StatusChip } from '../components/StatusChip';
import { EmptyState, SectionTitle, useToast } from '../components/ui';
import type { Producer } from '../api/types';

interface CommonInc { id: number; ratePerLitre: number; effectiveDate: string; endDate?: string; status: string; description?: string }
interface IndInc { id: number; producerId: number; ratePerLitre: number; effectiveDate: string; endDate?: string; status: string }

export function IncentivesPage() {
  const toast = useToast();
  const qc = useQueryClient();
  const [common, setCommon] = useState({ ratePerLitre: '', effectiveDate: today() });
  const [producer, setProducer] = useState<Producer | null>(null);
  const [ind, setInd] = useState({ ratePerLitre: '', effectiveDate: today() });

  const commonList = useQuery({ queryKey: ['incentives', 'common'], queryFn: async () => (await api.get<CommonInc[]>('/incentives/common')).data });
  const indList = useQuery({
    queryKey: ['incentives', 'ind', producer?.id],
    queryFn: async () => (await api.get<IndInc[]>('/incentives/individual', { params: { producerId: producer!.id } })).data,
    enabled: !!producer,
  });

  const addCommon = useMutation({
    mutationFn: async () => (await api.post('/incentives/common', { ratePerLitre: Number(common.ratePerLitre), effectiveDate: common.effectiveDate })).data,
    onSuccess: () => {
      toast.success('Common incentive added');
      setCommon({ ...common, ratePerLitre: '' });
      qc.invalidateQueries({ queryKey: ['incentives', 'common'] });
    },
    onError: toast.fromError,
  });
  const addInd = useMutation({
    mutationFn: async () => (await api.post('/incentives/individual', { producerId: producer!.id, ratePerLitre: Number(ind.ratePerLitre), effectiveDate: ind.effectiveDate })).data,
    onSuccess: () => {
      toast.success('Individual incentive added');
      setInd({ ...ind, ratePerLitre: '' });
      qc.invalidateQueries({ queryKey: ['incentives', 'ind'] });
    },
    onError: toast.fromError,
  });

  return (
    <>
      <PageHeader title="Incentives" subtitle="Common + individual rates, combined then capped per litre by centre settings" />
      <Grid container spacing={3}>
        <Grid size={{ xs: 12, md: 6 }}>
          <SectionTitle>Common incentive</SectionTitle>
          <Card sx={{ mb: 2 }}>
            <CardContent>
              <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} alignItems={{ sm: 'flex-start' }}>
                <TextField label="Rate / litre" type="number" value={common.ratePerLitre} onChange={(e) => setCommon({ ...common, ratePerLitre: e.target.value })} />
                <TextField label="Effective from" type="date" value={common.effectiveDate} onChange={(e) => setCommon({ ...common, effectiveDate: e.target.value })} InputLabelProps={{ shrink: true }} />
                <Button variant="contained" sx={{ minWidth: 96 }} disabled={!Number(common.ratePerLitre) || addCommon.isPending} onClick={() => addCommon.mutate()}>
                  Add
                </Button>
              </Stack>
            </CardContent>
          </Card>
          <Card>
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell>From</TableCell>
                  <TableCell align="right">Rate</TableCell>
                  <TableCell>Status</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {commonList.data?.map((c) => (
                  <TableRow key={c.id}>
                    <TableCell>{c.effectiveDate}</TableCell>
                    <TableCell align="right">{rate(c.ratePerLitre)}</TableCell>
                    <TableCell>
                      <StatusChip status={c.status} />
                    </TableCell>
                  </TableRow>
                ))}
                {commonList.data?.length === 0 && (
                  <TableRow>
                    <TableCell colSpan={3} sx={{ textAlign: 'center', py: 3, color: 'text.secondary' }}>
                      No common incentive set
                    </TableCell>
                  </TableRow>
                )}
              </TableBody>
            </Table>
          </Card>
        </Grid>

        <Grid size={{ xs: 12, md: 6 }}>
          <SectionTitle>Individual incentive</SectionTitle>
          <Stack spacing={2}>
            <ProducerPicker value={producer} onChange={setProducer} />
            {!producer && (
              <Card>
                <EmptyState title="Pick a producer" description="Choose a producer to view and add individual incentives." />
              </Card>
            )}
            {producer && (
              <>
                <Card>
                  <CardContent>
                    <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} alignItems={{ sm: 'flex-start' }}>
                      <TextField label="Rate / litre" type="number" value={ind.ratePerLitre} onChange={(e) => setInd({ ...ind, ratePerLitre: e.target.value })} />
                      <TextField label="Effective from" type="date" value={ind.effectiveDate} onChange={(e) => setInd({ ...ind, effectiveDate: e.target.value })} InputLabelProps={{ shrink: true }} />
                      <Button variant="contained" sx={{ minWidth: 96 }} disabled={!Number(ind.ratePerLitre) || addInd.isPending} onClick={() => addInd.mutate()}>
                        Add
                      </Button>
                    </Stack>
                  </CardContent>
                </Card>
                <Card>
                  <Table size="small">
                    <TableHead>
                      <TableRow>
                        <TableCell>From</TableCell>
                        <TableCell align="right">Rate</TableCell>
                        <TableCell>Status</TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {indList.data?.map((c) => (
                        <TableRow key={c.id}>
                          <TableCell>{c.effectiveDate}</TableCell>
                          <TableCell align="right">{rate(c.ratePerLitre)}</TableCell>
                          <TableCell>
                            <StatusChip status={c.status} />
                          </TableCell>
                        </TableRow>
                      ))}
                      {indList.data?.length === 0 && (
                        <TableRow>
                          <TableCell colSpan={3} sx={{ textAlign: 'center', py: 3, color: 'text.secondary' }}>
                            No individual incentive for this producer
                          </TableCell>
                        </TableRow>
                      )}
                    </TableBody>
                  </Table>
                </Card>
              </>
            )}
          </Stack>
        </Grid>
      </Grid>
    </>
  );
}
