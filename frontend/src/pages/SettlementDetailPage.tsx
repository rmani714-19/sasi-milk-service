import { useState } from 'react';
import { useParams } from 'react-router-dom';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Chip from '@mui/material/Chip';
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
import TableContainer from '@mui/material/TableContainer';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../lib/api';
import { litres, money } from '../lib/format';
import { PageHeader } from '../components/PageHeader';
import { StatusChip } from '../components/StatusChip';
import { ConfirmDialog, QueryBoundary, SectionTitle, useToast } from '../components/ui';
import { useAuth } from '../lib/auth';
import type { DailyCollectionRow, Payout, SettlementDetail, SettlementItem } from '../api/types';

const LIFECYCLE = ['DRAFT', 'CALCULATED', 'VERIFIED', 'APPROVED', 'PAID'] as const;

function stepIndex(status: string): number {
  switch (status) {
    case 'DRAFT':
      return 0;
    case 'CALCULATED':
    case 'REVIEWED':
    case 'UNDER_VERIFICATION':
      return 1;
    case 'VERIFIED':
      return 2;
    case 'APPROVED':
    case 'PAYOUT_INITIATED':
    case 'PARTIALLY_PAID':
      return 3;
    case 'PAID':
      return 4;
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
  const [detailFor, setDetailFor] = useState<SettlementItem | null>(null);
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

  const invalidate = () => {
    qc.invalidateQueries({ queryKey: ['settlement', id] });
    qc.invalidateQueries({ queryKey: ['payouts', id] });
  };

  const lifecycle = useMutation({
    mutationFn: async ({ path }: { path: string; ok: string }) =>
      (await api.post(`/settlements/${id}/${path}`)).data,
    onSuccess: (_d, v) => {
      toast.success(v.ok);
      setConfirmCancel(false);
      invalidate();
    },
    onError: toast.fromError,
  });
  const run = (path: string, ok: string) => lifecycle.mutate({ path, ok });

  const verifyItem = useMutation({
    mutationFn: async ({ itemId, action }: { itemId: number; action: 'verify' | 'unverify' }) =>
      (await api.post(`/settlements/${id}/items/${itemId}/${action}`)).data,
    onSuccess: (_d, v) => {
      toast.success(v.action === 'verify' ? 'Producer verified' : 'Verification cleared');
      invalidate();
    },
    onError: toast.fromError,
  });

  const initiatePayouts = useMutation({
    mutationFn: async () => (await api.post(`/settlements/${id}/payouts`)).data,
    onSuccess: () => {
      toast.success('Payouts initiated');
      invalidate();
    },
    onError: toast.fromError,
  });
  const payoutAction = (payoutId: number, path: string, body?: unknown) =>
    api.post(`/payouts/${payoutId}/${path}`, body).then(invalidate);

  return (
    <QueryBoundary query={detail}>
      {(s) => {
        const busy = lifecycle.isPending || initiatePayouts.isPending || verifyItem.isPending;
        const active = stepIndex(s.status);
        const terminal = s.status === 'CANCELLED' || s.status === 'FAILED';
        const legacyAll = s.paymentFrequency === 'ALL';
        const preApproval = ['CALCULATED', 'REVIEWED', 'UNDER_VERIFICATION', 'VERIFIED'].includes(s.status);
        const canApprove = s.status === 'VERIFIED' || (legacyAll && s.status === 'REVIEWED');
        return (
          <>
            <PageHeader
              title={s.settlementNumber}
              subtitle={`${s.paymentFrequency === 'ALL' ? '' : s.paymentFrequency + ' · '}${s.periodStart} → ${s.periodEnd} · rounding ${s.roundingMode}/${s.roundingScale}`}
              action={<StatusChip status={s.status} />}
            />

            <Card sx={{ mb: 3 }}>
              <CardContent>
                <Stepper activeStep={active} alternativeLabel>
                  {LIFECYCLE.map((label, i) => (
                    <Step key={label}>
                      <StepLabel error={terminal && i === active}>
                        {label === 'VERIFIED' && s.status === 'UNDER_VERIFICATION'
                          ? 'Verifying…'
                          : label.charAt(0) + label.slice(1).toLowerCase()}
                      </StepLabel>
                    </Step>
                  ))}
                </Stepper>
              </CardContent>
            </Card>

            <Stack direction="row" spacing={1} sx={{ mb: 3, flexWrap: 'wrap', gap: 1 }}>
              {['DRAFT', 'CALCULATED', 'REVIEWED', 'UNDER_VERIFICATION', 'VERIFIED'].includes(s.status) &&
                can('GENERATE_SETTLEMENT') && (
                  <Button variant="contained" disabled={busy} onClick={() => run('calculate', 'Recalculated')}>
                    {s.status === 'DRAFT' ? 'Calculate' : 'Recalculate'}
                  </Button>
                )}
              {legacyAll && s.status === 'CALCULATED' && can('REVIEW_SETTLEMENT') && (
                <Button variant="outlined" disabled={busy} onClick={() => run('review', 'Marked reviewed')}>
                  Mark reviewed
                </Button>
              )}
              {preApproval && s.pendingVerificationCount > 0 && s.totalProducers > 0 && can('VERIFY_PAYMENT') && (
                <Button
                  variant="outlined"
                  startIcon={<CheckCircleIcon />}
                  disabled={busy}
                  onClick={() => run('verify', 'All producers verified')}
                >
                  Verify all ({s.pendingVerificationCount})
                </Button>
              )}
              {canApprove && can('APPROVE_SETTLEMENT') && (
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
              {['DRAFT', 'CALCULATED', 'REVIEWED', 'UNDER_VERIFICATION', 'VERIFIED'].includes(s.status) &&
                can('GENERATE_SETTLEMENT') && (
                  <Button color="error" variant="outlined" disabled={busy} onClick={() => setConfirmCancel(true)}>
                    Cancel
                  </Button>
                )}
            </Stack>

            <Grid container spacing={2} sx={{ mb: 3 }}>
              {[
                ['Producers due', String(s.totalProducers), false],
                ['Total litres', litres(s.totalLitres), false],
                ['Milk amount', money(s.totalMilkAmount), false],
                ['Incentive', money(s.totalIncentive), false],
                ['Deductions', money(s.totalDeduction), false],
                ['Advance recovery', money(s.totalAdvanceRecovery), false],
                ['Verified', `${s.verifiedCount}/${s.totalProducers}`, false],
                ['Net payable', money(s.totalNetPayable), true],
              ].map(([k, v, emphasize]) => (
                <Grid size={{ xs: 6, md: 3 }} key={k as string}>
                  <Card sx={emphasize ? { bgcolor: 'primary.light', borderColor: 'primary.main', height: '100%' } : { height: '100%' }}>
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

            <SectionTitle>Producer verification</SectionTitle>
            <Card sx={{ mb: 4 }}>
              <TableContainer sx={{ maxHeight: 'calc(100vh - 360px)' }}>
                <Table size="small" stickyHeader>
                  <TableHead>
                    <TableRow>
                      <TableCell>Producer</TableCell>
                      <TableCell align="right">Litres</TableCell>
                      <TableCell align="right">Milk</TableCell>
                      <TableCell align="right">Incentive</TableCell>
                      <TableCell align="right">Deduction</TableCell>
                      <TableCell align="right">Advance</TableCell>
                      <TableCell align="right">Net payable</TableCell>
                      <TableCell>Verification</TableCell>
                      <TableCell>Payment</TableCell>
                      <TableCell align="right">Action</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {s.items.map((it) => {
                      const verified = it.verificationStatus === 'VERIFIED';
                      const drift = verified && it.verifiedAmount != null && it.verifiedAmount !== it.netPayable;
                      return (
                        <TableRow key={it.id} hover>
                          <TableCell>
                            <Typography variant="body2" fontWeight={600}>
                              {it.producerName ?? `Producer #${it.producerId}`}
                            </Typography>
                            <Typography variant="caption" color="text.secondary">
                              {it.producerNumber ?? `#${it.producerId}`}
                            </Typography>
                          </TableCell>
                          <TableCell align="right">{litres(it.totalLitres)}</TableCell>
                          <TableCell align="right">{money(it.milkAmount)}</TableCell>
                          <TableCell align="right">{money(it.incentiveAmount)}</TableCell>
                          <TableCell align="right">{money(it.totalDeduction - it.advanceRecovery)}</TableCell>
                          <TableCell align="right">{money(it.advanceRecovery)}</TableCell>
                          <TableCell align="right" sx={{ fontWeight: 700 }}>
                            {money(it.netPayable)}
                          </TableCell>
                          <TableCell>
                            {drift ? (
                              <Chip size="small" color="warning" label="Re-verify" />
                            ) : (
                              <StatusChip status={it.verificationStatus} />
                            )}
                          </TableCell>
                          <TableCell>
                            <StatusChip status={it.paymentStatus} />
                          </TableCell>
                          <TableCell align="right">
                            <Stack direction="row" spacing={0.5} justifyContent="flex-end">
                              <Button size="small" onClick={() => setDetailFor(it)}>
                                View
                              </Button>
                              {preApproval && can('VERIFY_PAYMENT') &&
                                (verified && !drift ? (
                                  <Button
                                    size="small"
                                    color="inherit"
                                    disabled={busy}
                                    onClick={() => verifyItem.mutate({ itemId: it.id, action: 'unverify' })}
                                  >
                                    Unverify
                                  </Button>
                                ) : (
                                  <Button
                                    size="small"
                                    variant="contained"
                                    disabled={busy}
                                    onClick={() => verifyItem.mutate({ itemId: it.id, action: 'verify' })}
                                  >
                                    Verify
                                  </Button>
                                ))}
                            </Stack>
                          </TableCell>
                        </TableRow>
                      );
                    })}
                    {s.items.length === 0 && (
                      <TableRow>
                        <TableCell colSpan={10} sx={{ textAlign: 'center', py: 4, color: 'text.secondary' }}>
                          No lines yet — calculate the settlement.
                        </TableCell>
                      </TableRow>
                    )}
                  </TableBody>
                </Table>
              </TableContainer>
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

            <ProducerBreakdownDialog settlementId={id!} item={detailFor} onClose={() => setDetailFor(null)} />

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
                <TextField label="Bank UTR / reference" value={utr} onChange={(e) => setUtr(e.target.value)} autoFocus />
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

function ProducerBreakdownDialog({
  settlementId,
  item,
  onClose,
}: {
  settlementId: string;
  item: SettlementItem | null;
  onClose: () => void;
}) {
  const rows = useQuery({
    queryKey: ['settlement', settlementId, 'item', item?.id, 'collections'],
    queryFn: async () =>
      (await api.get<DailyCollectionRow[]>(`/settlements/${settlementId}/items/${item!.id}/collections`)).data,
    enabled: !!item,
  });

  return (
    <Dialog open={!!item} onClose={onClose} fullWidth maxWidth="md">
      <DialogTitle>
        {item?.producerName ?? `Producer #${item?.producerId}`}{' '}
        <Typography component="span" variant="body2" color="text.secondary">
          · {item?.producerNumber ?? ''}
        </Typography>
      </DialogTitle>
      <DialogContent dividers>
        {item && (
          <Grid container spacing={1.5} sx={{ mb: 2 }}>
            {[
              ['Total litres', litres(item.totalLitres)],
              ['Milk amount', money(item.milkAmount)],
              ['Incentives', money(item.incentiveAmount)],
              ['Feed deduction', money(item.feedDeduction)],
              ['Veterinary deduction', money(item.veterinaryDeduction)],
              ['Other deduction', money(item.otherDeduction)],
              ['Advance recovery', money(item.advanceRecovery)],
              ['Adjustments', money(item.adjustmentAmount)],
              ['Net payable', money(item.netPayable)],
            ].map(([k, v]) => (
              <Grid size={{ xs: 6, sm: 4 }} key={k}>
                <Typography variant="caption" color="text.secondary" display="block">
                  {k}
                </Typography>
                <Typography variant="body2" fontWeight={600}>
                  {v}
                </Typography>
              </Grid>
            ))}
          </Grid>
        )}
        <SectionTitle sx={{ mb: 1 }}>Daily collections</SectionTitle>
        <QueryBoundary query={rows} empty={(d) => d.length === 0}>
          {(d) => (
            <TableContainer sx={{ maxHeight: 360 }}>
              <Table size="small" stickyHeader>
                <TableHead>
                  <TableRow>
                    <TableCell>Date</TableCell>
                    <TableCell>Shift</TableCell>
                    <TableCell>Reference</TableCell>
                    <TableCell align="right">Litres</TableCell>
                    <TableCell align="right">FAT</TableCell>
                    <TableCell align="right">SNF</TableCell>
                    <TableCell align="right">Rate/L</TableCell>
                    <TableCell align="right">Milk amount</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {d.map((r) => (
                    <TableRow key={r.collectionId}>
                      <TableCell>{r.collectionDate}</TableCell>
                      <TableCell>{r.shift}</TableCell>
                      <TableCell>{r.collectionNumber}</TableCell>
                      <TableCell align="right">{litres(r.litres)}</TableCell>
                      <TableCell align="right">{r.fatPercentage}</TableCell>
                      <TableCell align="right">{r.snfPercentage}</TableCell>
                      <TableCell align="right">{money(r.ratePerLitre)}</TableCell>
                      <TableCell align="right">{money(r.milkAmount)}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
          )}
        </QueryBoundary>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Close</Button>
      </DialogActions>
    </Dialog>
  );
}
