import { useParams } from 'react-router-dom';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Chip from '@mui/material/Chip';
import Grid from '@mui/material/Grid2';
import Stack from '@mui/material/Stack';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import Typography from '@mui/material/Typography';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../lib/api';
import { litres, money } from '../lib/format';
import { PageHeader } from '../components/PageHeader';
import { QueryBoundary, useToast } from '../components/ui';
import { useAuth } from '../lib/auth';
import { statusColor } from './settlementStatus';
import type { Payout, SettlementDetail } from '../api/types';

export function SettlementDetailPage() {
  const { id } = useParams();
  const toast = useToast();
  const qc = useQueryClient();
  const { can } = useAuth();

  const detail = useQuery({
    queryKey: ['settlement', id],
    queryFn: async () => (await api.get<SettlementDetail>(`/settlements/${id}`)).data,
  });
  const payouts = useQuery({
    queryKey: ['payouts', id],
    queryFn: async () => (await api.get<Payout[]>('/payouts', { params: { settlementId: id } })).data,
    enabled: !!detail.data && detail.data.status !== 'DRAFT',
  });

  const lifecycle = useMutation({
    mutationFn: async ({ path }: { path: string; ok: string }) =>
      (await api.post(`/settlements/${id}/${path}`)).data,
    onSuccess: (_d, v) => {
      toast.success(v.ok);
      qc.invalidateQueries({ queryKey: ['settlement', id] });
      qc.invalidateQueries({ queryKey: ['payouts', id] });
    },
    onError: toast.fromError,
  });
  const run = (path: string, ok: string) => lifecycle.mutate({ path, ok });

  const initiatePayouts = useMutation({
    mutationFn: async () => (await api.post(`/settlements/${id}/payouts`)).data,
    onSuccess: () => {
      toast.success('Payouts initiated');
      qc.invalidateQueries({ queryKey: ['settlement', id] });
      qc.invalidateQueries({ queryKey: ['payouts', id] });
    },
    onError: toast.fromError,
  });
  const payoutAction = (payoutId: number, path: string, body?: unknown) =>
    api.post(`/payouts/${payoutId}/${path}`, body).then(() => {
      qc.invalidateQueries({ queryKey: ['settlement', id] });
      qc.invalidateQueries({ queryKey: ['payouts', id] });
    });

  return (
    <QueryBoundary query={detail}>
      {(s) => {
        const busy = lifecycle.isPending || initiatePayouts.isPending;
        return (
          <>
            <PageHeader
              title={s.settlementNumber}
              subtitle={`${s.periodStart} → ${s.periodEnd} · rounding ${s.roundingMode}/${s.roundingScale}`}
              action={<Chip label={s.status} color={statusColor(s.status)} />}
            />

            <Stack direction="row" spacing={1} sx={{ mb: 3, flexWrap: 'wrap', gap: 1 }}>
              {['DRAFT', 'CALCULATED', 'REVIEWED'].includes(s.status) && can('GENERATE_SETTLEMENT') && (
                <Button variant="contained" disabled={busy} onClick={() => run('calculate', 'Recalculated')}>
                  {s.status === 'DRAFT' ? 'Calculate' : 'Recalculate'}
                </Button>
              )}
              {s.status === 'CALCULATED' && can('REVIEW_SETTLEMENT') && (
                <Button variant="outlined" disabled={busy} onClick={() => run('review', 'Marked reviewed')}>
                  Mark reviewed
                </Button>
              )}
              {s.status === 'REVIEWED' && can('APPROVE_SETTLEMENT') && (
                <Button variant="contained" color="success" disabled={busy} onClick={() => run('approve', 'Approved')}>
                  Approve
                </Button>
              )}
              {['APPROVED', 'PAYOUT_INITIATED', 'PARTIALLY_PAID', 'FAILED'].includes(s.status) &&
                can('APPROVE_SETTLEMENT') && (
                  <Button variant="contained" disabled={busy} onClick={() => initiatePayouts.mutate()}>
                    Initiate payouts
                  </Button>
                )}
              {['DRAFT', 'CALCULATED', 'REVIEWED'].includes(s.status) && can('GENERATE_SETTLEMENT') && (
                <Button color="error" disabled={busy} onClick={() => run('cancel', 'Cancelled')}>
                  Cancel
                </Button>
              )}
            </Stack>

            <Grid container spacing={2} sx={{ mb: 3 }}>
              {[
                ['Farmers', String(s.totalProducers)],
                ['Total litres', litres(s.totalLitres)],
                ['Milk amount', money(s.totalMilkAmount)],
                ['Incentive', money(s.totalIncentive)],
                ['Deductions', money(s.totalDeduction)],
                ['Advance recovery', money(s.totalAdvanceRecovery)],
                ['Round off', money(s.totalRoundOff)],
                ['Net payable', money(s.totalNetPayable)],
              ].map(([k, v]) => (
                <Grid size={{ xs: 6, md: 3 }} key={k}>
                  <Card>
                    <CardContent>
                      <Typography variant="body2" color="text.secondary">
                        {k}
                      </Typography>
                      <Typography variant="h6">{v}</Typography>
                    </CardContent>
                  </Card>
                </Grid>
              ))}
            </Grid>

            <Typography variant="h6" sx={{ mb: 1 }}>
              Farmer lines
            </Typography>
            <Card sx={{ mb: 4, overflowX: 'auto' }}>
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell>Farmer</TableCell>
                    <TableCell align="right">Litres</TableCell>
                    <TableCell align="right">Gross</TableCell>
                    <TableCell align="right">Deduction</TableCell>
                    <TableCell align="right">Advance</TableCell>
                    <TableCell align="right">Round</TableCell>
                    <TableCell align="right">Net payable</TableCell>
                    <TableCell>Payment</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {s.items.map((it) => (
                    <TableRow key={it.id}>
                      <TableCell>#{it.producerId}</TableCell>
                      <TableCell align="right">{litres(it.totalLitres)}</TableCell>
                      <TableCell align="right">{money(it.grossAmount)}</TableCell>
                      <TableCell align="right">{money(it.totalDeduction - it.advanceRecovery)}</TableCell>
                      <TableCell align="right">{money(it.advanceRecovery)}</TableCell>
                      <TableCell align="right">{money(it.roundOff)}</TableCell>
                      <TableCell align="right">
                        <strong>{money(it.netPayable)}</strong>
                      </TableCell>
                      <TableCell>
                        <Chip size="small" variant="outlined" label={it.paymentStatus} />
                      </TableCell>
                    </TableRow>
                  ))}
                  {s.items.length === 0 && (
                    <TableRow>
                      <TableCell colSpan={8} sx={{ textAlign: 'center', py: 3, color: 'text.secondary' }}>
                        No lines yet — calculate the settlement.
                      </TableCell>
                    </TableRow>
                  )}
                </TableBody>
              </Table>
            </Card>

            {payouts.data && payouts.data.length > 0 && (
              <>
                <Typography variant="h6" sx={{ mb: 1 }}>
                  Payouts
                </Typography>
                <Card sx={{ overflowX: 'auto' }}>
                  <Table size="small">
                    <TableHead>
                      <TableRow>
                        <TableCell>Farmer</TableCell>
                        <TableCell align="right">Amount</TableCell>
                        <TableCell>Provider</TableCell>
                        <TableCell>Status</TableCell>
                        <TableCell>UTR</TableCell>
                        <TableCell />
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {payouts.data.map((p) => (
                        <TableRow key={p.id}>
                          <TableCell>#{p.producerId}</TableCell>
                          <TableCell align="right">{money(p.amount)}</TableCell>
                          <TableCell>{p.provider}</TableCell>
                          <TableCell>
                            <Chip size="small" variant="outlined" label={p.status} color={statusColor(p.status)} />
                          </TableCell>
                          <TableCell>{p.utr ?? '—'}</TableCell>
                          <TableCell align="right">
                            {can('APPROVE_SETTLEMENT') && ['PROCESSING', 'INITIATED', 'PENDING'].includes(p.status) && (
                              <Button
                                size="small"
                                onClick={() => {
                                  const utr = window.prompt('Bank UTR / reference');
                                  if (utr) payoutAction(p.id, 'mark-paid', { utr }).catch(toast.fromError);
                                }}
                              >
                                Mark paid
                              </Button>
                            )}
                            {can('APPROVE_SETTLEMENT') && p.status === 'FAILED' && (
                              <Button size="small" onClick={() => payoutAction(p.id, 'retry').catch(toast.fromError)}>
                                Retry
                              </Button>
                            )}
                          </TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </Card>
              </>
            )}
          </>
        );
      }}
    </QueryBoundary>
  );
}
