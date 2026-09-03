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
import ToggleButton from '@mui/material/ToggleButton';
import ToggleButtonGroup from '@mui/material/ToggleButtonGroup';
import Typography from '@mui/material/Typography';
import PlayArrowIcon from '@mui/icons-material/PlayArrow';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../../lib/api';
import { money } from '../../lib/format';
import { PageHeader } from '../../components/PageHeader';
import { StatusChip } from '../../components/StatusChip';
import { EmptyState, QueryBoundary, TableSkeleton, useToast } from '../../components/ui';
import type { SettlementSummary } from '../../api/types';

type Freq = 'WEEKLY' | 'MONTHLY';

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

const OPEN = ['DRAFT', 'CALCULATED', 'REVIEWED', 'APPROVED', 'PAYOUT_INITIATED', 'PARTIALLY_PAID', 'FAILED'];

export function FarmerPaymentsPage() {
  const nav = useNavigate();
  const toast = useToast();
  const qc = useQueryClient();
  const [freq, setFreq] = useState<Freq>('WEEKLY');
  const [weekAnchor, setWeekAnchor] = useState(iso(new Date()));
  const [monthAnchor, setMonthAnchor] = useState(iso(new Date()).slice(0, 7));
  const [statusFilter, setStatusFilter] = useState('');

  const range = freq === 'WEEKLY' ? weekRange(weekAnchor) : monthRange(monthAnchor);

  const list = useQuery({
    queryKey: ['settlements'],
    queryFn: async () => (await api.get<SettlementSummary[]>('/settlements')).data,
  });

  const create = useMutation({
    mutationFn: async () =>
      (await api.post<{ internalId: number }>('/settlements', { periodStart: range.from, periodEnd: range.to })).data,
    onSuccess: (d) => {
      toast.success('Payment run created');
      qc.invalidateQueries({ queryKey: ['settlements'] });
      nav(`/settlements/${d.internalId}`);
    },
    onError: toast.fromError,
  });

  const all = list.data ?? [];
  const matching = useMemo(
    () => all.find((s) => s.periodStart === range.from && s.periodEnd === range.to),
    [all, range.from, range.to],
  );

  const shown = useMemo(() => {
    return all
      .filter((s) => !statusFilter || s.status === statusFilter)
      .sort((a, b) => (a.periodStart < b.periodStart ? 1 : -1));
  }, [all, statusFilter]);

  const thisWeek = all.find((s) => {
    const w = weekRange(iso(new Date()));
    return s.periodStart === w.from && s.periodEnd === w.to;
  });
  const thisMonth = all.find((s) => {
    const m = monthRange(iso(new Date()).slice(0, 7));
    return s.periodStart === m.from && s.periodEnd === m.to;
  });
  const pendingNet = all.filter((s) => OPEN.includes(s.status)).reduce((x, s) => x + Number(s.totalNetPayable), 0);
  const paidNet = all.filter((s) => s.status === 'PAID').reduce((x, s) => x + Number(s.totalNetPayable), 0);
  const failedCount = all.filter((s) => s.status === 'FAILED').length;

  return (
    <>
      <PageHeader
        title="Farmer Payments"
        subtitle="Weekly & monthly milk payments — Calculate → Review → Approve → Pay → Confirm"
      />

      <Grid container spacing={2} sx={{ mb: 3 }}>
        <SummaryCard label="This week" value={thisWeek ? money(thisWeek.totalNetPayable) : '—'} hint={thisWeek?.status} />
        <SummaryCard label="This month" value={thisMonth ? money(thisMonth.totalNetPayable) : '—'} hint={thisMonth?.status} />
        <SummaryCard label="Pending" value={money(pendingNet)} hint={`${all.filter((s) => OPEN.includes(s.status)).length} runs`} />
        <SummaryCard label="Paid" value={money(paidNet)} hint={`${all.filter((s) => s.status === 'PAID').length} runs`} />
        <SummaryCard label="Failed" value={String(failedCount)} hint={failedCount ? 'needs attention' : 'none'} tone={failedCount ? 'error' : undefined} />
      </Grid>

      <Card sx={{ mb: 3 }}>
        <CardContent>
          <Stack direction={{ xs: 'column', md: 'row' }} spacing={2} alignItems={{ md: 'center' }}>
            <ToggleButtonGroup
              exclusive
              size="small"
              value={freq}
              onChange={(_, v: Freq | null) => v && setFreq(v)}
            >
              <ToggleButton value="WEEKLY">Weekly</ToggleButton>
              <ToggleButton value="MONTHLY">Monthly</ToggleButton>
            </ToggleButtonGroup>

            {freq === 'WEEKLY' ? (
              <TextField
                type="date"
                label="Week of"
                size="small"
                value={weekAnchor}
                onChange={(e) => setWeekAnchor(e.target.value)}
                InputLabelProps={{ shrink: true }}
              />
            ) : (
              <TextField
                type="month"
                label="Month"
                size="small"
                value={monthAnchor}
                onChange={(e) => setMonthAnchor(e.target.value)}
                InputLabelProps={{ shrink: true }}
              />
            )}

            <Typography variant="body2" color="text.secondary">
              {range.from} → {range.to}
            </Typography>

            <TextField select size="small" label="Status" value={statusFilter} onChange={(e) => setStatusFilter(e.target.value)} sx={{ minWidth: 160 }}>
              <MenuItem value="">All</MenuItem>
              {['DRAFT', 'CALCULATED', 'REVIEWED', 'APPROVED', 'PAYOUT_INITIATED', 'PARTIALLY_PAID', 'PAID', 'FAILED', 'CANCELLED'].map((s) => (
                <MenuItem key={s} value={s}>
                  {s.replace(/_/g, ' ')}
                </MenuItem>
              ))}
            </TextField>

            <Box sx={{ flex: 1 }} />
            <Button
              variant="contained"
              startIcon={<PlayArrowIcon />}
              disabled={create.isPending}
              onClick={() => (matching ? nav(`/settlements/${matching.internalId}`) : create.mutate())}
            >
              {matching ? 'Open this run' : `Start ${freq === 'WEEKLY' ? 'weekly' : 'monthly'} run`}
            </Button>
          </Stack>
        </CardContent>
      </Card>

      <Card>
        <QueryBoundary
          query={list}
          skeleton={<TableSkeleton cols={6} />}
          empty={() => shown.length === 0}
          emptyState={<EmptyState title="No payment runs" description="Start a weekly or monthly run above." />}
        >
          {() => (
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell>Run</TableCell>
                  <TableCell>Period</TableCell>
                  <TableCell align="right">Producers</TableCell>
                  <TableCell align="right">Net Payable</TableCell>
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
                    <TableCell align="right">{s.totalProducers}</TableCell>
                    <TableCell align="right" sx={{ fontWeight: 700 }}>
                      {money(s.totalNetPayable)}
                    </TableCell>
                    <TableCell>
                      <StatusChip status={s.status} />
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
            <Typography variant="caption" color="text.secondary" noWrap>
              {hint}
            </Typography>
          )}
        </CardContent>
      </Card>
    </Grid>
  );
}
