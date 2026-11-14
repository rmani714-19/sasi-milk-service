import { useMemo, useState } from 'react';
import { useParams } from 'react-router-dom';
import Accordion from '@mui/material/Accordion';
import AccordionDetails from '@mui/material/AccordionDetails';
import AccordionSummary from '@mui/material/AccordionSummary';
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
import LinearProgress from '@mui/material/LinearProgress';
import MenuItem from '@mui/material/MenuItem';
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
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import PictureAsPdfOutlinedIcon from '@mui/icons-material/PictureAsPdfOutlined';
import ReceiptLongOutlinedIcon from '@mui/icons-material/ReceiptLongOutlined';
import SendOutlinedIcon from '@mui/icons-material/SendOutlined';
import TableChartOutlinedIcon from '@mui/icons-material/TableChartOutlined';
import PlaceOutlinedIcon from '@mui/icons-material/PlaceOutlined';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../lib/api';
import { litres, money } from '../lib/format';
import { downloadFile, openPdf, useRegions } from '../api/common';
import { batchStatusLabel, lineStatusLabel } from '../lib/paymentLabels';
import { regionLabel } from '../lib/region';
import { PageHeader } from '../components/PageHeader';
import { StatusChip } from '../components/StatusChip';
import { OwnerApprovalDialog, PreValidationCard } from '../components/PaymentGates';
import { ConfirmDialog, QueryBoundary, SectionTitle, useToast } from '../components/ui';
import { useAuth } from '../lib/auth';
import type { DailyCollectionRow, Reconciliation, Payout, PreValidationReport, Region, SettlementDetail, SettlementItem, StatusHistoryEntry } from '../api/types';

interface RegionGroup {
  key: string;
  label: string;
  items: SettlementItem[];
}

/** Group producer lines by region so a large run can be reviewed area by area. */
function groupByRegion(items: SettlementItem[], regions: Region[] | undefined): RegionGroup[] {
  const byId = new Map((regions ?? []).map((r) => [r.id, r]));
  const groups = new Map<string, RegionGroup>();
  for (const it of items) {
    const key = it.regionId != null ? String(it.regionId) : 'none';
    if (!groups.has(key)) {
      groups.set(key, {
        key,
        label: it.regionId != null ? regionLabel(byId.get(it.regionId)) : 'No region assigned',
        items: [],
      });
    }
    groups.get(key)!.items.push(it);
  }
  return [...groups.values()].sort((a, b) => {
    if (a.key === 'none') return 1;
    if (b.key === 'none') return -1;
    return a.label.localeCompare(b.label);
  });
}

/** The five steps a payment batch goes through, in plain words. */
const WIZARD = ['Prepare', 'Review & verify', 'Generate bills', 'Approve', 'Bank & confirm'] as const;

function wizardStep(status: string, allBilled: boolean): number {
  switch (status) {
    case 'DRAFT':
      return 0;
    case 'CALCULATED':
    case 'REVIEWED':
    case 'UNDER_VERIFICATION':
      return 1;
    case 'VERIFIED':
      return allBilled ? 3 : 2;
    case 'APPROVED':
    case 'PAYOUT_INITIATED':
    case 'PARTIALLY_PAID':
    case 'FAILED':
      return 4;
    case 'PAID':
      return 5;
    default:
      return 0;
  }
}

function items_byId(s: SettlementDetail, itemId: number): number {
  return s.items.find((i) => i.id === itemId)?.producerId ?? -1;
}

const todayIso = () => new Date().toISOString().slice(0, 10);

type SortKey = 'producerId' | 'name' | 'net' | 'litres';

const SORTERS: Record<SortKey, (a: SettlementItem, b: SettlementItem) => number> = {
  producerId: (a, b) => (a.producerNumber ?? '').localeCompare(b.producerNumber ?? '', undefined, { numeric: true }),
  name: (a, b) => (a.producerName ?? '').localeCompare(b.producerName ?? ''),
  net: (a, b) => Number(b.netPayable) - Number(a.netPayable),
  litres: (a, b) => Number(b.totalLitres) - Number(a.totalLitres),
};

export function SettlementDetailPage() {
  const { id } = useParams();
  const toast = useToast();
  const qc = useQueryClient();
  const { can } = useAuth();
  const [confirmCancel, setConfirmCancel] = useState(false);
  const [detailFor, setDetailFor] = useState<SettlementItem | null>(null);
  const [utrFor, setUtrFor] = useState<Payout | null>(null);
  const [bulk, setBulk] = useState<{ regionId: number | null; label: string; count: number; amount: number } | null>(null);
  const [bulkRef, setBulkRef] = useState('');
  const [bulkDate, setBulkDate] = useState(todayIso());
  const [failFor, setFailFor] = useState<Payout | null>(null);
  const [failReason, setFailReason] = useState('');
  const [utr, setUtr] = useState('');
  const [paidOn, setPaidOn] = useState(todayIso());
  const [submitOpen, setSubmitOpen] = useState(false);
  const [submitDate, setSubmitDate] = useState(todayIso());
  const [submitRef, setSubmitRef] = useState('');
  const [submitNote, setSubmitNote] = useState('');
  const [preVal, setPreVal] = useState<PreValidationReport | null>(null);
  const [approveOpen, setApproveOpen] = useState(false);

  const detail = useQuery({
    queryKey: ['settlement', id],
    queryFn: async () => (await api.get<SettlementDetail>(`/settlements/${id}`)).data,
  });
  const regions = useRegions();
  // Review controls: show everyone, or one region/area; order by producer ID, name, amount or litres.
  const [regionFilter, setRegionFilter] = useState(''); // '' = all, 'none' = no region, else region id
  const [sortKey, setSortKey] = useState<SortKey>('producerId');
  const [listMode, setListMode] = useState<'REGION' | 'LIST'>('REGION');

  const visibleItems = useMemo(() => {
    const all = detail.data?.items ?? [];
    const filtered = all.filter((it) =>
      regionFilter === '' ? true : regionFilter === 'none' ? it.regionId == null : String(it.regionId) === regionFilter,
    );
    return [...filtered].sort(SORTERS[sortKey]);
  }, [detail.data, regionFilter, sortKey]);
  const groups = useMemo(
    () =>
      listMode === 'LIST'
        ? visibleItems.length
          ? [{ key: 'all', label: 'All producers', items: visibleItems }]
          : []
        : groupByRegion(visibleItems, regions.data),
    [visibleItems, regions.data, listMode],
  );
  const regionOptions = useMemo(() => {
    const ids = new Set((detail.data?.items ?? []).map((it) => it.regionId));
    const byId = new Map((regions.data ?? []).map((r) => [r.id, r]));
    return {
      list: [...ids].filter((x): x is number => x != null).map((x) => ({ id: x, label: regionLabel(byId.get(x)) })),
      hasNone: ids.has(undefined) || ids.has(null as unknown as undefined),
    };
  }, [detail.data, regions.data]);
  const exportQuery = `${regionFilter === '' ? '' : `&regionId=${regionFilter === 'none' ? 0 : regionFilter}`}&sort=${
    sortKey === 'producerId' ? 'producerId' : sortKey
  }`;
  const payouts = useQuery({
    queryKey: ['payouts', id],
    queryFn: async () => (await api.get<Payout[]>('/payouts', { params: { settlementId: id } })).data,
    enabled: !!detail.data && detail.data.status !== 'DRAFT',
  });

  const history = useQuery({
    queryKey: ['settlement', id, 'history'],
    queryFn: async () => (await api.get<StatusHistoryEntry[]>(`/settlements/${id}/history`)).data,
    enabled: !!detail.data,
  });

  const postApproval = ['APPROVED', 'PAYOUT_INITIATED', 'PARTIALLY_PAID', 'PAID', 'FAILED'].includes(detail.data?.status ?? '');
  const recon = useQuery({
    queryKey: ['settlement', id, 'reconciliation'],
    queryFn: async () => (await api.get<Reconciliation>(`/settlements/${id}/reconciliation`)).data,
    enabled: postApproval,
  });

  const confirmMany = useMutation({
    mutationFn: async (b: { regionId: number | null; bankReference: string; paidOn: string }) =>
      (
        await api.post(`/settlements/${id}/payouts/confirm`, {
          regionId: b.regionId ?? undefined,
          bankReference: b.bankReference,
          paidOn: b.paidOn,
        })
      ).data,
    onSuccess: (d: unknown[]) => {
      toast.success(`${d.length} payment${d.length === 1 ? '' : 's'} confirmed`);
      setBulk(null);
      invalidate();
    },
    onError: toast.fromError,
  });

  const invalidate = () => {
    qc.invalidateQueries({ queryKey: ['settlement', id] });
    qc.invalidateQueries({ queryKey: ['payouts', id] });
  };

  const generateBills = useMutation({
    mutationFn: async () => (await api.post<{ generated: number; total: number }>(`/settlements/${id}/bills/generate`)).data,
    onSuccess: (d) => {
      toast.success(d.generated > 0 ? `${d.generated} bill${d.generated === 1 ? '' : 's'} generated` : 'All bills were already generated');
      invalidate();
    },
    onError: toast.fromError,
  });
  const fileJob = (job: () => Promise<void>) => job().catch(toast.fromError);

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

  // sequential — verifying concurrently would race the settlement's own optimistic-lock version
  const verifyRegion = useMutation({
    mutationFn: async (itemIds: number[]) => {
      for (const itemId of itemIds) {
        await api.post(`/settlements/${id}/items/${itemId}/verify`);
      }
    },
    onSuccess: () => {
      toast.success('Region verified');
      invalidate();
    },
    onError: toast.fromError,
  });

  /** Records the hand-over to the bank and lists the producers to pay. Nothing is sent to any bank from here. */
  const initiatePayouts = useMutation({
    mutationFn: async (body?: { submittedOn: string; bankReference?: string; note?: string }) =>
      (await api.post(`/settlements/${id}/payouts`, body)).data,
    onSuccess: (_d, v) => {
      toast.success(v ? 'Marked as submitted to bank' : 'Payment list refreshed');
      setSubmitOpen(false);
      invalidate();
    },
    onError: toast.fromError,
  });
  const approveMut = useMutation({
    mutationFn: async () => (await api.post(`/settlements/${id}/approve`, { confirmed: true })).data,
    onSuccess: () => {
      toast.success('Payment batch approved');
      setApproveOpen(false);
      invalidate();
    },
    onError: toast.fromError,
  });
  const payoutAction = (payoutId: number, path: string, body?: unknown) =>
    api.post(`/payouts/${payoutId}/${path}`, body).then(invalidate);

  return (
    <QueryBoundary query={detail}>
      {(s) => {
        const busy = lifecycle.isPending || initiatePayouts.isPending || verifyItem.isPending || verifyRegion.isPending || generateBills.isPending;
        const legacyAll = s.paymentFrequency === 'ALL';
        const preValReady = preVal?.result === 'READY_FOR_APPROVAL';
        const terminal = s.status === 'CANCELLED' || s.status === 'FAILED';
        const preApproval = ['CALCULATED', 'REVIEWED', 'UNDER_VERIFICATION', 'VERIFIED'].includes(s.status);
        const canApprove = s.status === 'VERIFIED' || (legacyAll && s.status === 'REVIEWED');
        const showGates = ['VERIFIED', 'APPROVED', 'PAYOUT_INITIATED', 'PARTIALLY_PAID', 'PAID', 'FAILED'].includes(s.status)
          || (legacyAll && s.status === 'REVIEWED');
        const gatesPass = legacyAll && s.status === 'REVIEWED' ? true : preValReady;
        const canBill = ['VERIFIED', 'APPROVED', 'PAYOUT_INITIATED', 'PARTIALLY_PAID', 'PAID'].includes(s.status);
        const allBilled = s.totalProducers > 0 && s.billsGenerated >= s.totalProducers;
        const active = wizardStep(s.status, allBilled);
        const hasLines = s.totalProducers > 0 && s.status !== 'DRAFT' && s.status !== 'CANCELLED';
        const producerNames = new Map(
          s.items.map((i) => [i.producerId, `${i.producerNumber ?? ''} · ${i.producerName ?? `Producer #${i.producerId}`}`] as const),
        );
        return (
          <>
            <PageHeader
              title={s.settlementNumber}
              subtitle={`${s.paymentFrequency === 'ALL' ? '' : s.paymentFrequency + ' · '}${s.regionId != null ? regionLabel(regions.data?.find((r) => r.id === s.regionId)) + ' · ' : ''}${s.periodStart} → ${s.periodEnd} · rounding ${s.roundingMode}/${s.roundingScale}`}
              action={<StatusChip status={s.status} label={batchStatusLabel(s.status)} />}
            />

            <Card sx={{ mb: 3 }}>
              <CardContent>
                <Stepper activeStep={terminal ? 0 : active} alternativeLabel>
                  {WIZARD.map((label, i) => (
                    <Step key={label}>
                      <StepLabel
                        error={terminal && i === active}
                        optional={
                          i === 4 && ['APPROVED', 'PAYOUT_INITIATED', 'PARTIALLY_PAID'].includes(s.status) ? (
                            <Typography variant="caption" color="text.secondary">
                              {s.status === 'APPROVED' ? 'Submit to bank' : 'Confirm payments'}
                            </Typography>
                          ) : i === 1 && s.status === 'UNDER_VERIFICATION' ? (
                            <Typography variant="caption" color="text.secondary">
                              {s.verifiedCount}/{s.totalProducers} verified
                            </Typography>
                          ) : undefined
                        }
                      >
                        {label}
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
                <Button
                  variant="contained"
                  color="success"
                  disabled={busy || !gatesPass}
                  onClick={() => setApproveOpen(true)}
                >
                  Approve
                </Button>
              )}
              {canBill && !allBilled && (can('GENERATE_SETTLEMENT') || can('GENERATE_BILLS')) && (
                <Button variant="outlined" startIcon={<ReceiptLongOutlinedIcon />} disabled={busy} onClick={() => generateBills.mutate()}>
                  Generate bills
                </Button>
              )}
              {allBilled && (
                <Button
                  variant="outlined"
                  startIcon={<PictureAsPdfOutlinedIcon />}
                  onClick={() => fileJob(() => downloadFile(`/settlements/${id}/bills`, `${s.settlementNumber}-bills.pdf`))}
                >
                  Download all bills
                </Button>
              )}
              {hasLines && (
                <>
                  <Button
                    variant="outlined"
                    startIcon={<PictureAsPdfOutlinedIcon />}
                    onClick={() => fileJob(() => downloadFile(`/reports/settlement/${id}/summary?format=pdf${exportQuery}`, `${s.settlementNumber}-summary.pdf`))}
                  >
                    Summary PDF
                  </Button>
                  <Button
                    variant="outlined"
                    startIcon={<TableChartOutlinedIcon />}
                    onClick={() => fileJob(() => downloadFile(`/reports/settlement/${id}/summary?format=xlsx${exportQuery}`, `${s.settlementNumber}-summary.xlsx`))}
                  >
                    Summary Excel
                  </Button>
                </>
              )}
              {s.status === 'APPROVED' && (can('APPROVE_SETTLEMENT') || can('SUBMIT_TO_BANK')) && (
                <Button variant="contained" startIcon={<SendOutlinedIcon />} disabled={busy} onClick={() => setSubmitOpen(true)}>
                  Mark as submitted to bank
                </Button>
              )}
              {['PAYOUT_INITIATED', 'PARTIALLY_PAID', 'FAILED'].includes(s.status) && (can('APPROVE_SETTLEMENT') || can('SUBMIT_TO_BANK')) && (
                <Button variant="outlined" disabled={busy} onClick={() => initiatePayouts.mutate(undefined)}>
                  Refresh payment list
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
                ['Bills generated', `${s.billsGenerated}/${s.totalProducers}`, false],
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

            {s.bankSubmission && (
              <Card sx={{ mb: 3, borderColor: 'primary.main' }}>
                <CardContent>
                  <Stack direction="row" spacing={1} alignItems="center" sx={{ mb: 1 }}>
                    <SendOutlinedIcon fontSize="small" color="primary" />
                    <Typography sx={{ fontWeight: 700 }}>Submitted to bank</Typography>
                  </Stack>
                  <Grid container spacing={2}>
                    {[
                      ['Submission date', s.bankSubmission.submittedOn],
                      ['Bank batch reference', s.bankSubmission.bankReference || '—'],
                      ['Producers', String(s.bankSubmission.producerCount)],
                      ['Amount submitted', money(s.bankSubmission.totalAmount)],
                      ['Note', s.bankSubmission.note || '—'],
                    ].map(([k, v]) => (
                      <Grid size={{ xs: 6, md: 'auto' }} key={k}>
                        <Typography variant="caption" color="text.secondary" display="block">{k}</Typography>
                        <Typography variant="body2" fontWeight={600}>{v}</Typography>
                      </Grid>
                    ))}
                  </Grid>
                  <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 1 }}>
                    This is a record only — the payment itself is made through your bank. Confirm each producer below once the bank has paid.
                  </Typography>
                </CardContent>
              </Card>
            )}

            <SectionTitle>Producer verification</SectionTitle>
            {groups.length === 0 ? (
              <Card sx={{ mb: 4 }}>
                <CardContent>
                  <Typography color="text.secondary" sx={{ textAlign: 'center', py: 3 }}>
                    {s.items.length > 0 ? 'No producers match this filter.' : 'No lines yet — calculate the settlement.'}
                  </Typography>
                </CardContent>
              </Card>
            ) : (
              <Box sx={{ mb: 4 }}>
                <Stack direction="row" spacing={2} alignItems="center" flexWrap="wrap" useFlexGap sx={{ mb: 1.5 }}>
                  <TextField
                    select
                    size="small"
                    label="Region / area"
                    value={regionFilter}
                    onChange={(e) => setRegionFilter(e.target.value)}
                    sx={{ width: 200, flexShrink: 0 }}
                  >
                    <MenuItem value="">All regions</MenuItem>
                    {regionOptions.list.map((r) => (
                      <MenuItem key={r.id} value={String(r.id)}>
                        {r.label}
                      </MenuItem>
                    ))}
                    {regionOptions.hasNone && <MenuItem value="none">No region assigned</MenuItem>}
                  </TextField>
                  <TextField
                    select
                    size="small"
                    label="Order by"
                    value={sortKey}
                    onChange={(e) => setSortKey(e.target.value as SortKey)}
                    sx={{ width: 200, flexShrink: 0 }}
                  >
                    <MenuItem value="producerId">Producer ID</MenuItem>
                    <MenuItem value="name">Producer name</MenuItem>
                    <MenuItem value="net">Net payable (highest first)</MenuItem>
                    <MenuItem value="litres">Litres (highest first)</MenuItem>
                  </TextField>
                  <TextField
                    select
                    size="small"
                    label="View"
                    value={listMode}
                    onChange={(e) => setListMode(e.target.value as 'REGION' | 'LIST')}
                    sx={{ width: 200, flexShrink: 0 }}
                  >
                    <MenuItem value="REGION">Grouped by region</MenuItem>
                    <MenuItem value="LIST">One full list</MenuItem>
                  </TextField>
                  <Typography variant="caption" color="text.secondary">
                    Showing {visibleItems.length} of {s.items.length} producers · the summary download follows this filter
                  </Typography>
                </Stack>
                {groups.map((g) => {
                  const groupVerified = g.items.filter((it) => it.verificationStatus === 'VERIFIED').length;
                  const groupLitres = g.items.reduce((sum, it) => sum + Number(it.totalLitres), 0);
                  const groupNet = g.items.reduce((sum, it) => sum + Number(it.netPayable), 0);
                  const groupDone = groupVerified === g.items.length;
                  return (
                    <Accordion key={g.key} defaultExpanded variant="outlined" sx={{ mb: 1.5, '&:before': { display: 'none' } }}>
                      <AccordionSummary expandIcon={<ExpandMoreIcon />}>
                        <Stack
                          direction="row"
                          spacing={2}
                          alignItems="center"
                          sx={{ width: '100%', pr: 1, flexWrap: 'wrap', rowGap: 1 }}
                        >
                          <Stack direction="row" spacing={0.75} alignItems="center">
                            <PlaceOutlinedIcon fontSize="small" color="action" />
                            <Typography sx={{ fontWeight: 700 }}>{g.label}</Typography>
                          </Stack>
                          <Chip size="small" variant="outlined" label={`${g.items.length} producer${g.items.length === 1 ? '' : 's'}`} />
                          <Typography variant="body2" color="text.secondary">
                            {litres(groupLitres)}
                          </Typography>
                          <Typography variant="body2" sx={{ fontWeight: 700 }}>
                            {money(groupNet)}
                          </Typography>
                          <StatusChip
                            status={groupDone ? 'VERIFIED' : 'PENDING_VERIFICATION'}
                            label={`Verified ${groupVerified}/${g.items.length}`}
                          />
                          <Box sx={{ flex: 1 }} />
                          {preApproval && !groupDone && can('VERIFY_PAYMENT') && (
                            <Button
                              size="small"
                              variant="outlined"
                              disabled={busy}
                              onClick={(e) => {
                                e.stopPropagation();
                                verifyRegion.mutate(
                                  g.items.filter((it) => it.verificationStatus !== 'VERIFIED').map((it) => it.id),
                                );
                              }}
                            >
                              {verifyRegion.isPending ? 'Verifying…' : g.key === 'all' ? 'Verify all shown' : 'Verify all in this region'}
                            </Button>
                          )}
                        </Stack>
                      </AccordionSummary>
                      <AccordionDetails sx={{ p: 0 }}>
                        <TableContainer sx={{ maxHeight: 420 }}>
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
                                <TableCell>Bill</TableCell>
                                <TableCell>Payment</TableCell>
                                <TableCell align="right">Action</TableCell>
                              </TableRow>
                            </TableHead>
                            <TableBody>
                              {g.items.map((it) => {
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
                                      {it.billNumber ? (
                                        <Typography variant="caption" sx={{ fontWeight: 600 }}>{it.billNumber}</Typography>
                                      ) : (
                                        <Typography variant="caption" color="text.secondary">Not generated</Typography>
                                      )}
                                    </TableCell>
                                    <TableCell>
                                      <StatusChip status={it.paymentStatus} label={lineStatusLabel(it.paymentStatus)} />
                                      {it.paymentReference && (
                                        <Typography variant="caption" color="text.secondary" display="block">
                                          Ref {it.paymentReference}
                                        </Typography>
                                      )}
                                    </TableCell>
                                    <TableCell align="right">
                                      <Stack direction="row" spacing={0.5} justifyContent="flex-end">
                                        <Button size="small" onClick={() => setDetailFor(it)}>
                                          View
                                        </Button>
                                        <Button
                                          size="small"
                                          onClick={() => fileJob(() => openPdf(`/settlements/${id}/items/${it.id}/bill`))}
                                        >
                                          {it.billNumber ? 'Bill' : 'Preview bill'}
                                        </Button>
                                        {it.billNumber && (
                                          <Button
                                            size="small"
                                            onClick={() =>
                                              fileJob(() =>
                                                downloadFile(`/settlements/${id}/items/${it.id}/bill?download=true`, `${it.billNumber}.pdf`),
                                              )
                                            }
                                          >
                                            PDF
                                          </Button>
                                        )}
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
                            </TableBody>
                          </Table>
                        </TableContainer>
                      </AccordionDetails>
                    </Accordion>
                  );
                })}
              </Box>
            )}

            {showGates && (
              <>
                <SectionTitle>Pre-validation</SectionTitle>
                <PreValidationCard base={`/settlements/${id}`} onResult={setPreVal} />
              </>
            )}

            {postApproval && recon.data && (
              <>
                <SectionTitle>Payment status — settlement vs payments</SectionTitle>
                <Card sx={{ mb: 3 }}>
                  <CardContent>
                    <Grid container spacing={2} sx={{ mb: 1.5 }}>
                      {[
                        ['Settlement total', money(recon.data.batchTotal)],
                        ['Confirmed paid', money(recon.data.paidTotal)],
                        ['Waiting for bank', money(recon.data.pendingTotal)],
                        ['Failed', money(recon.data.failedTotal)],
                      ].map(([k, v]) => (
                        <Grid size={{ xs: 6, md: 3 }} key={k}>
                          <Typography variant="caption" color="text.secondary" display="block">{k}</Typography>
                          <Typography sx={{ fontWeight: 700, fontSize: 18 }}>{v}</Typography>
                        </Grid>
                      ))}
                    </Grid>
                    <LinearProgress
                      variant="determinate"
                      value={recon.data.linesTotal > 0 ? Math.min(100, (recon.data.paidTotal / recon.data.linesTotal) * 100) : 0}
                      sx={{ height: 8, borderRadius: 4, mb: 1 }}
                    />
                    <Stack direction="row" spacing={1.5} alignItems="center" flexWrap="wrap" useFlexGap>
                      <StatusChip
                        status={recon.data.complete ? 'VERIFIED' : 'PENDING'}
                        label={
                          recon.data.complete
                            ? 'Settlement matches overall payment'
                            : `${money(recon.data.difference)} still to be confirmed`
                        }
                      />
                      <Typography variant="caption" color="text.secondary">
                        {recon.data.paidCount} of {recon.data.producers} producers paid
                        {recon.data.linesMatchBatch ? ' · producer lines add up to the settlement total' : ' · producer lines do NOT add up to the settlement total'}
                      </Typography>
                      <Box sx={{ flex: 1 }} />
                      {recon.data.pendingCount > 0 && (can('APPROVE_SETTLEMENT') || can('CONFIRM_PAYMENT')) && payouts.data && payouts.data.length > 0 && (
                        <Button
                          size="small"
                          variant="contained"
                          onClick={() => {
                            setBulkRef('');
                            setBulkDate(todayIso());
                            setBulk({ regionId: null, label: 'the whole batch', count: recon.data!.pendingCount, amount: recon.data!.pendingTotal });
                          }}
                        >
                          Confirm all payments ({recon.data.pendingCount})
                        </Button>
                      )}
                    </Stack>
                  </CardContent>
                  <TableContainer>
                    <Table size="small">
                      <TableHead>
                        <TableRow>
                          <TableCell>Region / area</TableCell>
                          <TableCell align="right">Producers paid</TableCell>
                          <TableCell align="right">Settlement</TableCell>
                          <TableCell align="right">Paid</TableCell>
                          <TableCell align="right">Waiting</TableCell>
                          <TableCell>Status</TableCell>
                          <TableCell />
                        </TableRow>
                      </TableHead>
                      <TableBody>
                        {recon.data.regions.map((r) => {
                          const label = r.regionId == null ? 'No region assigned' : regionLabel(regions.data?.find((x) => x.id === r.regionId));
                          return (
                            <TableRow key={String(r.regionId ?? 'none')}>
                              <TableCell sx={{ fontWeight: 600 }}>{label}</TableCell>
                              <TableCell align="right">
                                {r.paidCount}/{r.producers}
                              </TableCell>
                              <TableCell align="right">{money(r.expected)}</TableCell>
                              <TableCell align="right">{money(r.paid)}</TableCell>
                              <TableCell align="right">{money(r.pending + r.failed)}</TableCell>
                              <TableCell>
                                <StatusChip
                                  status={r.status === 'COMPLETED' ? 'PAID' : r.status === 'PARTIALLY_CONFIRMED' ? 'PARTIALLY_PAID' : r.status === 'FAILED' ? 'FAILED' : 'PENDING'}
                                  label={r.status === 'COMPLETED' ? 'Completed' : r.status === 'PARTIALLY_CONFIRMED' ? 'Partially confirmed' : r.status === 'FAILED' ? 'Failed' : 'Not confirmed'}
                                />
                              </TableCell>
                              <TableCell align="right">
                                {r.pendingCount > 0 && (can('APPROVE_SETTLEMENT') || can('CONFIRM_PAYMENT')) && payouts.data && payouts.data.length > 0 && (
                                  <Button
                                    size="small"
                                    onClick={() => {
                                      setBulkRef('');
                                      setBulkDate(todayIso());
                                      setBulk({ regionId: r.regionId ?? 0, label: label, count: r.pendingCount, amount: r.pending });
                                    }}
                                  >
                                    Confirm this region
                                  </Button>
                                )}
                              </TableCell>
                            </TableRow>
                          );
                        })}
                      </TableBody>
                    </Table>
                  </TableContainer>
                </Card>
              </>
            )}

            {payouts.data && payouts.data.length > 0 && (
              <>
                <SectionTitle>Payment confirmation</SectionTitle>
                <Card sx={{ overflowX: 'auto' }}>
                  <Table size="small">
                    <TableHead>
                      <TableRow>
                        <TableCell>Producer</TableCell>
                        <TableCell align="right">Amount</TableCell>
                        <TableCell>Status</TableCell>
                        <TableCell>Bank reference (UTR)</TableCell>
                        <TableCell>Paid on</TableCell>
                        <TableCell />
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {payouts.data.map((p) => (
                        <TableRow key={p.id}>
                          <TableCell>{producerNames.get(p.producerId) ?? `#${p.producerId}`}</TableCell>
                          <TableCell align="right" sx={{ fontWeight: 600 }}>
                            {money(p.amount)}
                          </TableCell>
                          <TableCell>
                            <StatusChip status={p.status} label={lineStatusLabel(p.status === 'SUCCESS' ? 'SUCCESS' : p.status)} />
                          </TableCell>
                          <TableCell>{p.utr ?? '—'}</TableCell>
                          <TableCell>{p.settledAt ? p.settledAt.slice(0, 10) : '—'}</TableCell>
                          <TableCell align="right">
                            {(can('APPROVE_SETTLEMENT') || can('CONFIRM_PAYMENT')) && ['PROCESSING', 'INITIATED', 'PENDING'].includes(p.status) && (
                              <Stack direction="row" spacing={0.5} justifyContent="flex-end">
                                <Button
                                  size="small"
                                  onClick={() => {
                                    setUtr('');
                                    setPaidOn(todayIso());
                                    setUtrFor(p);
                                  }}
                                >
                                  Confirm payment
                                </Button>
                                <Button
                                  size="small"
                                  color="error"
                                  onClick={() => {
                                    setFailReason('');
                                    setFailFor(p);
                                  }}
                                >
                                  Mark failed
                                </Button>
                              </Stack>
                            )}
                            {(can('APPROVE_SETTLEMENT') || can('CONFIRM_PAYMENT')) && p.status === 'FAILED' && (
                              <Stack direction="row" spacing={0.5} justifyContent="flex-end" alignItems="center">
                                {p.failureReason && (
                                  <Typography variant="caption" color="error">
                                    {p.failureReason}
                                  </Typography>
                                )}
                                <Button size="small" onClick={() => payoutAction(p.id, 'retry').catch(toast.fromError)}>
                                  Put back on bank list
                                </Button>
                              </Stack>
                            )}
                          </TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </Card>
              </>
            )}

            {history.data && history.data.length > 0 && (
              <>
                <SectionTitle>Activity</SectionTitle>
                <Card sx={{ mb: 3 }}>
                  <CardContent>
                    <Stack spacing={1.25}>
                      {history.data.slice(0, 12).map((h) => (
                        <Stack key={h.id} direction="row" spacing={2} alignItems="baseline" sx={{ flexWrap: 'wrap' }}>
                          <Typography variant="caption" color="text.secondary" sx={{ minWidth: 130 }}>
                            {new Date(h.changedAt).toLocaleString()}
                          </Typography>
                          <Typography variant="body2">
                            {h.subjectType === 'ITEM'
                              ? `${producerNames.get(items_byId(s, h.subjectId)) ?? 'Producer'}: ${h.fromStatus ? lineStatusLabel(h.fromStatus) + ' → ' : ''}${lineStatusLabel(h.toStatus)}`
                              : h.fromStatus && h.fromStatus !== h.toStatus
                                ? `Batch: ${batchStatusLabel(h.fromStatus)} → ${batchStatusLabel(h.toStatus)}`
                                : `Batch: ${h.note ?? batchStatusLabel(h.toStatus)}`}
                            {h.note && h.fromStatus !== h.toStatus ? ` — ${h.note}` : ''}
                          </Typography>
                        </Stack>
                      ))}
                    </Stack>
                  </CardContent>
                </Card>
              </>
            )}

            <ProducerBreakdownDialog settlementId={id!} item={detailFor} onClose={() => setDetailFor(null)} />

            <Dialog open={submitOpen} onClose={() => setSubmitOpen(false)} fullWidth maxWidth="xs">
              <DialogTitle>Mark as submitted to bank</DialogTitle>
              <DialogContent>
                <Typography variant="body2" color="text.secondary" sx={{ mb: 2, mt: 0.5 }}>
                  Use this after you have given the payment list to your bank (portal upload, cheque or letter).
                  Nothing is sent from this app — it only records that the batch went to the bank.
                </Typography>
                <Typography variant="body2" sx={{ mb: 2 }}>
                  <b>{s.settlementNumber}</b> · {s.totalProducers} producers · <b>{money(s.totalNetPayable)}</b>
                </Typography>
                <Stack spacing={2}>
                  <TextField
                    label="Submission date"
                    type="date"
                    value={submitDate}
                    onChange={(e) => setSubmitDate(e.target.value)}
                    slotProps={{ inputLabel: { shrink: true } }}
                  />
                  <TextField
                    label="Bank batch reference (optional)"
                    value={submitRef}
                    onChange={(e) => setSubmitRef(e.target.value)}
                  />
                  <TextField label="Note (optional)" value={submitNote} onChange={(e) => setSubmitNote(e.target.value)} />
                </Stack>
                {!allBilled && (
                  <Typography variant="caption" color="warning.main" sx={{ display: 'block', mt: 1.5 }}>
                    Bills have not been generated for every producer yet.
                  </Typography>
                )}
              </DialogContent>
              <DialogActions>
                <Button onClick={() => setSubmitOpen(false)}>Cancel</Button>
                <Button
                  variant="contained"
                  disabled={!submitDate || initiatePayouts.isPending}
                  onClick={() =>
                    initiatePayouts.mutate({
                      submittedOn: submitDate,
                      bankReference: submitRef.trim() || undefined,
                      note: submitNote.trim() || undefined,
                    })
                  }
                >
                  {initiatePayouts.isPending ? 'Saving…' : 'Mark as submitted'}
                </Button>
              </DialogActions>
            </Dialog>

            <OwnerApprovalDialog
              open={approveOpen}
              busy={approveMut.isPending}
              lines={[
                ['Payment batch', s.settlementNumber],
                ['Recipients', String(s.totalProducers)],
                ['Total litres', litres(s.totalLitres)],
                ['Total amount', money(s.totalNetPayable)],
                ['Validation', preVal?.result.replace(/_/g, ' ') ?? '—'],
              ]}
              onConfirm={() => approveMut.mutate()}
              onClose={() => setApproveOpen(false)}
            />

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

            <Dialog open={!!bulk} onClose={() => setBulk(null)} fullWidth maxWidth="xs">
              <DialogTitle>Confirm payments</DialogTitle>
              <DialogContent>
                <Typography variant="body2" sx={{ mb: 2, mt: 0.5 }}>
                  Mark <b>{bulk?.count}</b> payment{bulk?.count === 1 ? '' : 's'} totalling <b>{bulk ? money(bulk.amount) : ''}</b> as
                  paid for <b>{bulk?.label}</b>. Do this only after the bank has paid them.
                </Typography>
                <Stack spacing={2}>
                  <TextField
                    label="Bank reference (applies to all of these)"
                    value={bulkRef}
                    onChange={(e) => setBulkRef(e.target.value)}
                    autoFocus
                  />
                  <TextField
                    label="Payment date"
                    type="date"
                    value={bulkDate}
                    onChange={(e) => setBulkDate(e.target.value)}
                    slotProps={{ inputLabel: { shrink: true } }}
                  />
                </Stack>
              </DialogContent>
              <DialogActions>
                <Button onClick={() => setBulk(null)}>Cancel</Button>
                <Button
                  variant="contained"
                  disabled={!bulkRef.trim() || !bulkDate || confirmMany.isPending}
                  onClick={() => bulk && confirmMany.mutate({ regionId: bulk.regionId, bankReference: bulkRef.trim(), paidOn: bulkDate })}
                >
                  {confirmMany.isPending ? 'Confirming…' : 'Confirm payments'}
                </Button>
              </DialogActions>
            </Dialog>

            <Dialog open={!!failFor} onClose={() => setFailFor(null)} fullWidth maxWidth="xs">
              <DialogTitle>Mark payment as failed</DialogTitle>
              <DialogContent>
                <Typography variant="body2" color="text.secondary" sx={{ mb: 2, mt: 0.5 }}>
                  {failFor ? producerNames.get(failFor.producerId) ?? `Producer #${failFor.producerId}` : ''} ·{' '}
                  {failFor ? money(failFor.amount) : ''} — use this when the bank rejected or returned the payment.
                </Typography>
                <TextField label="What went wrong?" value={failReason} onChange={(e) => setFailReason(e.target.value)} autoFocus />
              </DialogContent>
              <DialogActions>
                <Button onClick={() => setFailFor(null)}>Cancel</Button>
                <Button
                  variant="contained"
                  color="error"
                  disabled={!failReason.trim()}
                  onClick={() => {
                    const p = failFor;
                    setFailFor(null);
                    if (p) payoutAction(p.id, 'mark-failed', { reason: failReason.trim() }).catch(toast.fromError);
                  }}
                >
                  Mark failed
                </Button>
              </DialogActions>
            </Dialog>

            <Dialog open={!!utrFor} onClose={() => setUtrFor(null)} fullWidth maxWidth="xs">
              <DialogTitle>Confirm payment</DialogTitle>
              <DialogContent>
                <Box sx={{ mb: 2, mt: 0.5 }}>
                  <Typography variant="body2" color="text.secondary">
                    {utrFor ? producerNames.get(utrFor.producerId) ?? `Producer #${utrFor.producerId}` : ''} ·{' '}
                    {utrFor ? money(utrFor.amount) : ''}
                  </Typography>
                  <Typography variant="caption" color="text.secondary">
                    Confirm only after your bank has paid this producer.
                  </Typography>
                </Box>
                <Stack spacing={2}>
                  <TextField label="Bank transaction reference (UTR)" value={utr} onChange={(e) => setUtr(e.target.value)} autoFocus />
                  <TextField
                    label="Payment date"
                    type="date"
                    value={paidOn}
                    onChange={(e) => setPaidOn(e.target.value)}
                    slotProps={{ inputLabel: { shrink: true } }}
                  />
                </Stack>
              </DialogContent>
              <DialogActions>
                <Button onClick={() => setUtrFor(null)}>Cancel</Button>
                <Button
                  variant="contained"
                  disabled={!utr.trim() || !paidOn}
                  onClick={() => {
                    const p = utrFor;
                    setUtrFor(null);
                    if (p) payoutAction(p.id, 'mark-paid', { utr: utr.trim(), paidOn }).catch(toast.fromError);
                  }}
                >
                  Confirm payment
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
