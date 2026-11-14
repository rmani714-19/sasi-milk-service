import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
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
import PlayArrowIcon from '@mui/icons-material/PlayArrow';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../../lib/api';
import { useRegions } from '../../api/common';
import { regionLabel } from '../../lib/region';
import { litres, money } from '../../lib/format';
import { batchStatusLabel } from '../../lib/paymentLabels';
import { PageHeader } from '../../components/PageHeader';
import { StatusChip } from '../../components/StatusChip';
import { EmptyState, QueryBoundary, TableSkeleton, useToast } from '../../components/ui';
import type { SettlementSummary } from '../../api/types';

type Frequency = 'WEEKLY' | 'MONTHLY';

const iso = (d: Date) =>
  `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;

function weekRange(anchor: string) {
  const d = new Date(anchor);
  const day = (d.getDay() + 6) % 7; // Mon = 0
  const start = new Date(d);
  start.setDate(d.getDate() - day);
  const end = new Date(start);
  end.setDate(start.getDate() + 6);
  return { from: iso(start), to: iso(end) };
}
function monthRange(anchor: string) {
  const d = new Date(anchor + '-01');
  return {
    from: iso(new Date(d.getFullYear(), d.getMonth(), 1)),
    to: iso(new Date(d.getFullYear(), d.getMonth() + 1, 0)),
  };
}

const OPEN = [
  'DRAFT',
  'CALCULATED',
  'UNDER_VERIFICATION',
  'VERIFIED',
  'APPROVED',
  'PAYOUT_INITIATED',
  'PARTIALLY_PAID',
  'FAILED',
];

export function WeeklyFarmerPaymentPage() {
  return <FarmerPaymentPage frequency="WEEKLY" />;
}
export function MonthlyFarmerPaymentPage() {
  return <FarmerPaymentPage frequency="MONTHLY" />;
}

function FarmerPaymentPage({ frequency }: { frequency: Frequency }) {
  const nav = useNavigate();
  const toast = useToast();
  const qc = useQueryClient();
  const weekly = frequency === 'WEEKLY';
  const [weekAnchor, setWeekAnchor] = useState(iso(new Date()));
  const [monthAnchor, setMonthAnchor] = useState(iso(new Date()).slice(0, 7));
  const [statusFilter, setStatusFilter] = useState('');
  const [regionId, setRegionId] = useState(''); // '' = every region
  const regions = useRegions();

  const range = weekly ? weekRange(weekAnchor) : monthRange(monthAnchor);

  const list = useQuery({
    queryKey: ['settlements'],
    queryFn: async () => (await api.get<SettlementSummary[]>('/settlements')).data,
  });

  const create = useMutation({
    mutationFn: async () =>
      (
        await api.post<{ internalId: number }>('/settlements', {
          periodStart: range.from,
          periodEnd: range.to,
          paymentFrequency: frequency,
          regionId: regionId ? Number(regionId) : undefined,
        })
      ).data,
    onSuccess: (d) => {
      toast.success(`${weekly ? 'Weekly' : 'Monthly'} payment run created`);
      qc.invalidateQueries({ queryKey: ['settlements'] });
      nav(`/settlements/${d.internalId}`);
    },
    onError: toast.fromError,
  });

  const mine = useMemo(
    () => (list.data ?? []).filter((s) => s.paymentFrequency === frequency),
    [list.data, frequency],
  );
  const matching = useMemo(
    () =>
      mine.find(
        (s) =>
          s.periodStart === range.from &&
          s.periodEnd === range.to &&
          s.status !== 'CANCELLED' &&
          String(s.regionId ?? '') === regionId,
      ),
    [mine, range.from, range.to, regionId],
  );
  const shown = useMemo(
    () =>
      mine
        .filter((s) => !statusFilter || s.status === statusFilter)
        .sort((a, b) => (a.periodStart < b.periodStart ? 1 : -1)),
    [mine, statusFilter],
  );

  const thisPeriod = matching;
  const openRuns = mine.filter((s) => OPEN.includes(s.status));
  const pendingNet = openRuns.reduce((x, s) => x + Number(s.totalNetPayable), 0);
  const paidRuns = mine.filter((s) => s.status === 'PAID');
  const paidNet = paidRuns.reduce((x, s) => x + Number(s.totalNetPayable), 0);
  const failedCount = mine.filter((s) => s.status === 'FAILED').length;

  return (
    <>
      <PageHeader
        title={weekly ? 'Weekly Farmer Payment' : 'Monthly Farmer Payment'}
        subtitle={`${
          weekly ? 'Weekly' : 'Monthly'
        } producers only — Calculate → Verify → Generate bills → Approve → Submit to bank → Confirm payment`}
      />

      <Grid container spacing={2} sx={{ mb: 3 }}>
        <SummaryCard
          label={weekly ? 'This week' : 'This month'}
          value={thisPeriod ? money(thisPeriod.totalNetPayable) : '—'}
          hint={thisPeriod ? batchStatusLabel(thisPeriod.status) : 'not started'}
        />
        <SummaryCard label="Producers due" value={thisPeriod ? String(thisPeriod.totalProducers) : '—'} hint={`${range.from} → ${range.to}`} />
        <SummaryCard label="Pending" value={money(pendingNet)} hint={`${openRuns.length} open run${openRuns.length === 1 ? '' : 's'}`} />
        <SummaryCard label="Completed" value={money(paidNet)} hint={`${paidRuns.length} run${paidRuns.length === 1 ? '' : 's'}`} />
        <SummaryCard label="Failed" value={String(failedCount)} hint={failedCount ? 'needs attention' : 'none'} tone={failedCount ? 'error' : undefined} />
      </Grid>

      <Card sx={{ mb: 3 }}>
        <CardContent>
          <Stack direction="row" spacing={2} alignItems="center" flexWrap="wrap" useFlexGap>
            {weekly ? (
              <TextField
                type="date"
                label="Week of"
                size="small"
                value={weekAnchor}
                onChange={(e) => setWeekAnchor(e.target.value)}
                InputLabelProps={{ shrink: true }}
                sx={{ width: 190, flexShrink: 0 }}
              />
            ) : (
              <TextField
                type="month"
                label="Month"
                size="small"
                value={monthAnchor}
                onChange={(e) => setMonthAnchor(e.target.value)}
                InputLabelProps={{ shrink: true }}
                sx={{ width: 190, flexShrink: 0 }}
              />
            )}
            <Typography variant="body2" color="text.secondary" sx={{ flexShrink: 0, whiteSpace: 'nowrap' }}>
              {range.from} → {range.to}
            </Typography>
            <TextField
              select
              size="small"
              label="Status"
              value={statusFilter}
              onChange={(e) => setStatusFilter(e.target.value)}
              sx={{ width: 180, flexShrink: 0 }}
            >
              <MenuItem value="">All</MenuItem>
              {['DRAFT', 'CALCULATED', 'UNDER_VERIFICATION', 'VERIFIED', 'APPROVED', 'PAYOUT_INITIATED', 'PARTIALLY_PAID', 'PAID', 'FAILED', 'CANCELLED'].map((s) => (
                <MenuItem key={s} value={s}>
                  {batchStatusLabel(s)}
                </MenuItem>
              ))}
            </TextField>
            <TextField
              select
              size="small"
              label="Region / area"
              value={regionId}
              onChange={(e) => setRegionId(e.target.value)}
              sx={{ width: 200, flexShrink: 0 }}
            >
              <MenuItem value="">All regions</MenuItem>
              {(regions.data ?? [])
                .filter((r) => r.status === 'ACTIVE')
                .map((r) => (
                  <MenuItem key={r.id} value={String(r.id)}>
                    {regionLabel(r)}
                  </MenuItem>
                ))}
            </TextField>
            <Box sx={{ flex: '1 1 0', minWidth: 0 }} />
            <Button
              variant="contained"
              startIcon={<PlayArrowIcon />}
              disabled={create.isPending}
              onClick={() => (matching ? nav(`/settlements/${matching.internalId}`) : create.mutate())}
              sx={{ flexShrink: 0, whiteSpace: 'nowrap' }}
            >
              {matching ? 'Open this run' : `Start ${weekly ? 'weekly' : 'monthly'} run${regionId ? ' for this region' : ''}`}
            </Button>
          </Stack>
        </CardContent>
      </Card>

      <Card>
        <QueryBoundary
          query={list}
          skeleton={<TableSkeleton cols={7} />}
          empty={() => shown.length === 0}
          emptyState={<EmptyState title="No payment runs" description={`Start a ${weekly ? 'weekly' : 'monthly'} run above.`} />}
        >
          {() => (
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell>Run</TableCell>
                  <TableCell>Period</TableCell>
                  <TableCell>Region</TableCell>
                  <TableCell align="right">Producers</TableCell>
                  <TableCell align="right">Total litres</TableCell>
                  <TableCell align="right">Net payable</TableCell>
                  <TableCell>Status</TableCell>
                  <TableCell align="right" />
                </TableRow>
              </TableHead>
              <TableBody>
                {shown.map((s) => (
                  <TableRow key={s.id} hover sx={{ cursor: 'pointer' }} onClick={() => nav(`/settlements/${s.internalId}`)}>
                    <TableCell sx={{ fontWeight: 600 }}>{s.settlementNumber}</TableCell>
                    <TableCell>
                      {s.periodStart} → {s.periodEnd}
                    </TableCell>
                    <TableCell>
                      {s.regionId == null ? 'All regions' : regionLabel(regions.data?.find((r) => r.id === s.regionId))}
                    </TableCell>
                    <TableCell align="right">{s.totalProducers}</TableCell>
                    <TableCell align="right">{litres(s.totalLitres)}</TableCell>
                    <TableCell align="right" sx={{ fontWeight: 700 }}>
                      {money(s.totalNetPayable)}
                    </TableCell>
                    <TableCell>
                      <StatusChip status={s.status} label={batchStatusLabel(s.status)} />
                    </TableCell>
                    <TableCell align="right">
                      <Button size="small">Open</Button>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </QueryBoundary>
      </Card>
    </>
  );
}

function SummaryCard({
  label,
  value,
  hint,
  tone,
}: {
  label: string;
  value: string;
  hint?: string;
  tone?: 'error';
}) {
  return (
    <Grid size={{ xs: 6, sm: 4, md: 2.4 }}>
      <Card sx={{ height: '100%' }}>
        <CardContent sx={{ py: 1.5 }}>
          <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 500 }}>
            {label}
          </Typography>
          <Typography sx={{ fontSize: 20, fontWeight: 700, mt: 0.25 }} color={tone === 'error' ? 'error.main' : 'text.primary'}>
            {value}
          </Typography>
          {hint && (
            <Typography variant="caption" color="text.secondary" noWrap sx={{ display: 'block' }}>
              {hint}
            </Typography>
          )}
        </CardContent>
      </Card>
    </Grid>
  );
}
