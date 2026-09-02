import { useState } from 'react';
import { useParams } from 'react-router-dom';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogTitle from '@mui/material/DialogTitle';
import Grid from '@mui/material/Grid2';
import Stack from '@mui/material/Stack';
import Step from '@mui/material/Step';
import StepLabel from '@mui/material/StepLabel';
import Stepper from '@mui/material/Stepper';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../lib/api';
import { litres, money } from '../lib/format';
import { PageHeader } from '../components/PageHeader';
import { StatusChip } from '../components/StatusChip';
import { ConfirmDialog, QueryBoundary, SectionTitle, useToast } from '../components/ui';
import { useAuth } from '../lib/auth';
import type { Payout, SettlementDetail } from '../api/types';

const LIFECYCLE = ['DRAFT', 'CALCULATED', 'REVIEWED', 'APPROVED', 'PAID'] as const;

function stepIndex(status: string): number {
  switch (status) {
    case 'DRAFT':
      return 0;
    case 'CALCULATED':
      return 1;
    case 'REVIEWED':
      return 2;
    case 'APPROVED':
      return 3;
    case 'PAYOUT_INITIATED':
    case 'PARTIALLY_PAID':
      return 4;
    case 'PAID':
      return 5;
    default:
      return 0;
  }
}

export function SettlementDetailPage() {
  const { id } = useParams();
  const toast = useToast();
  const qc = useQueryClient();
  const { can } = useAuth();
  const [confirmCancel, setConfirmCancel] = useState(false);
  const [utrFor, setUtrFor] = useState<Payout | null>(null);
  const [utr, setUtr] = useState('');

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
      setConfirmCancel(false);
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
        const active = stepIndex(s.status);
        const terminal = s.status === 'CANCELLED' || s.status === 'FAILED';
        return (
          <>
            <PageHeader
              title={s.settlementNumber}
              subtitle={`${s.periodStart} → ${s.periodEnd} · rounding ${s.roundingMode}/${s.roundingScale}`}
              action={<StatusChip status={s.status} />}
            />

            <Card sx={{ mb: 3 }}>
              <CardContent>
                <Stepper activeStep={active} alternativeLabel>
                  {LIFECYCLE.map((label) => (
                    <Step key={label}>
                      <StepLabel error={terminal && label === LIFECYCLE[active]}>
                        {label.charAt(0) + label.slice(1).toLowerCase()}
                      </StepLabel>
                    </Step>
                  ))}
                </Stepper>
              </CardContent>
            </Card>

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
                <Button color="error" variant="outlined" disabled={busy} onClick={() => setConfirmCancel(true)}>
                  Cancel
                </Button>
              )}
            </Stack>

            <Grid container spacing={2} sx={{ mb: 3 }}>
              {[
                ['Producers', String(s.totalProducers), false],
                ['Total litres', litres(s.totalLitres), false],
                ['Milk amount', money(s.totalMilkAmount), false],
                ['Incentive', money(s.totalIncentive), false],
                ['Deductions', money(s.totalDeduction), false],
                ['Advance recovery', money(s.totalAdvanceRecovery), false],
                ['Round off', money(s.totalRoundOff), false],
                ['Net payable', money(s.totalNetPayable), true],
              ].map(([k, v, emphasize]) => (
                <Grid size={{ xs: 6, md: 3 }} key={k as string}>
                  <Card
                    sx={
                      emphasize
                        ? { bgcolor: 'primary.light', borderColor: 'primary.main', height: '100%' }
                        : { height: '100%' }
                    }
                  >
                    <CardContent>
                      <Typography variant="body2" color="text.secondary" sx={{ fontSize: 13, fontWeight: 500 }}>
                        {k}
                      </Typography>
                      <Typography
                        sx={{ fontSize: emphasize ? 24 : 20, fontWeight: 700, mt: 0.25 }}
                        color={emphasize ? 'primary.dark' : 'text.primary'}
                      >
                        {v}
                      </Typography>
                    </CardContent>
                  </Card>
                </Grid>
              ))}
            </Grid>

            <SectionTitle>Producer lines</SectionTitle>
            <Card sx={{ mb: 4, overflowX: 'auto' }}>
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell>Producer</TableCell>
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
                      <TableCell align="right" sx={{ fontWeight: 700 }}>
                        {money(it.netPayable)}
                      </TableCell>
                      <TableCell>
                        <StatusChip status={it.paymentStatus} />
                      </TableCell>
                    </TableRow>
                  ))}
                  {s.items.length === 0 && (
                    <TableRow>
                      <TableCell colSpan={8} sx={{ textAlign: 'center', py: 4, color: 'text.secondary' }}>
                        No lines yet — calculate the settlement.
                      </TableCell>
                    </TableRow>
                  )}
                </TableBody>
              </Table>
            </Card>

            {payouts.data && payouts.data.length > 0 && (
              <>
                <SectionTitle>Payouts</SectionTitle>
                <Card sx={{ overflowX: 'auto' }}>
                  <Table size="small">
                    <TableHead>
                      <TableRow>
                        <TableCell>Producer</TableCell>
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
                          <TableCell align="right" sx={{ fontWeight: 600 }}>
                            {money(p.amount)}
                          </TableCell>
                          <TableCell>{p.provider}</TableCell>
                          <TableCell>
                            <StatusChip status={p.status} />
                          </TableCell>
                          <TableCell>{p.utr ?? '—'}</TableCell>
                          <TableCell align="right">
                            {can('APPROVE_SETTLEMENT') && ['PROCESSING', 'INITIATED', 'PENDING'].includes(p.status) && (
                              <Button
                                size="small"
                                onClick={() => {
                                  setUtr('');
                                  setUtrFor(p);
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

            <ConfirmDialog
              open={confirmCancel}
              title="Cancel this settlement?"
              message="Collections are detached, pending deductions are released and advance recoveries are reversed. This cannot be undone."
              confirmLabel="Cancel settlement"
              destructive
              busy={lifecycle.isPending}
              onConfirm={() => run('cancel', 'Cancelled')}
              onClose={() => setConfirmCancel(false)}
            />

            <Dialog open={!!utrFor} onClose={() => setUtrFor(null)} fullWidth maxWidth="xs">
              <DialogTitle>Mark payout as paid</DialogTitle>
              <DialogContent>
                <Box sx={{ mb: 2, mt: 0.5 }}>
                  <Typography variant="body2" color="text.secondary">
                    Producer #{utrFor?.producerId} · {utrFor ? money(utrFor.amount) : ''}
                  </Typography>
                </Box>
                <TextField
                  label="Bank UTR / reference"
                  value={utr}
                  onChange={(e) => setUtr(e.target.value)}
                  autoFocus
                />
              </DialogContent>
              <DialogActions>
                <Button onClick={() => setUtrFor(null)}>Cancel</Button>
                <Button
                  variant="contained"
                  disabled={!utr.trim()}
                  onClick={() => {
                    const p = utrFor;
                    setUtrFor(null);
                    if (p) payoutAction(p.id, 'mark-paid', { utr: utr.trim() }).catch(toast.fromError);
                  }}
                >
                  Mark paid
                </Button>
              </DialogActions>
            </Dialog>
          </>
        );
      }}
    </QueryBoundary>
  );
}
