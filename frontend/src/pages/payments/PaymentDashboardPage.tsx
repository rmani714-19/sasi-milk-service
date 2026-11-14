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
import { useQuery } from '@tanstack/react-query';
import { api } from '../../lib/api';
import { openPdf } from '../../api/common';
import { money } from '../../lib/format';
import { batchStatusLabel, lineStatusLabel } from '../../lib/paymentLabels';
import { PageHeader } from '../../components/PageHeader';
import { StatusChip } from '../../components/StatusChip';
import { QueryBoundary, SectionTitle, useToast } from '../../components/ui';

interface DashBatch {
  id: number;
  number: string;
  frequency: 'WEEKLY' | 'MONTHLY' | 'ALL';
  periodStart: string;
  periodEnd: string;
  status: string;
  producers: number;
  netPayable: number;
}
interface DashBill {
  settlementId: number;
  itemId: number;
  billNumber: string;
  producerNumber: string;
  producerName: string;
  netPayable: number;
  generatedAt: string;
}
interface Dashboard {
  totalPayable: number;
  weeklyPayable: number;
  monthlyPayable: number;
  driverSalaryPayable: number;
  billsGenerated: number;
  pendingSubmissionCount: number;
  pendingSubmissionAmount: number;
  submittedCount: number;
  submittedAmount: number;
  confirmedPaid: number;
  recentBatches: DashBatch[];
  pendingSubmissions: DashBatch[];
  recentBills: DashBill[];
  trend: { periodStart: string; frequency: string; netPayable: number }[];
  upcoming: { kind: 'WEEKLY' | 'MONTHLY' | 'DRIVER_SALARY'; label: string; periodStart: string; periodEnd: string; batchId?: number | null; status: string }[];
  recentActivity: {
    settlementId: number;
    settlementNumber: string;
    subjectType: 'SETTLEMENT' | 'ITEM';
    fromStatus?: string;
    toStatus: string;
    note?: string;
    changedAt: string;
  }[];
}

const iso = (d: Date) =>
  `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;

const PRESETS: Record<string, () => { from: string; to: string }> = {
  month: () => {
    const n = new Date();
    return { from: iso(new Date(n.getFullYear(), n.getMonth(), 1)), to: iso(new Date(n.getFullYear(), n.getMonth() + 1, 0)) };
  },
  quarter: () => {
    const n = new Date();
    return { from: iso(new Date(n.getFullYear(), n.getMonth() - 2, 1)), to: iso(new Date(n.getFullYear(), n.getMonth() + 1, 0)) };
  },
  year: () => {
    const n = new Date();
    return { from: iso(new Date(n.getFullYear(), 0, 1)), to: iso(new Date(n.getFullYear(), 11, 31)) };
  },
};

export function PaymentDashboardPage() {
  const nav = useNavigate();
  const toast = useToast();
  const [preset, setPreset] = useState('year');
  const [from, setFrom] = useState(PRESETS.year().from);
  const [to, setTo] = useState(PRESETS.year().to);
  const [type, setType] = useState('');
  const [statusFilter, setStatusFilter] = useState('');

  const q = useQuery({
    queryKey: ['payment-dashboard', from, to],
    queryFn: async () =>
      (await api.get<Dashboard>('/reports/payment-dashboard', { params: { from, to } })).data,
    enabled: !!from && !!to && from <= to,
  });

  const pickPreset = (p: string) => {
    setPreset(p);
    if (PRESETS[p]) {
      const r = PRESETS[p]();
      setFrom(r.from);
      setTo(r.to);
    }
  };

  return (
    <>
      <PageHeader
        title="Payment Dashboard"
        subtitle="Prepare, bill, hand to the bank and track — the bank does the paying, this keeps the records"
      />

      <Card sx={{ mb: 3 }}>
        <CardContent>
          <Stack direction="row" spacing={2} alignItems="center" flexWrap="wrap" useFlexGap>
            <TextField select size="small" label="Period" value={preset} onChange={(e) => pickPreset(e.target.value)} sx={{ width: 170, flexShrink: 0 }}>
              <MenuItem value="month">This month</MenuItem>
              <MenuItem value="quarter">Last 3 months</MenuItem>
              <MenuItem value="year">This year</MenuItem>
              <MenuItem value="custom">Custom range</MenuItem>
            </TextField>
            <TextField
              type="date"
              size="small"
              label="From"
              value={from}
              onChange={(e) => {
                setFrom(e.target.value);
                setPreset('custom');
              }}
              InputLabelProps={{ shrink: true }}
              sx={{ width: 170, flexShrink: 0 }}
            />
            <TextField
              type="date"
              size="small"
              label="To"
              value={to}
              onChange={(e) => {
                setTo(e.target.value);
                setPreset('custom');
              }}
              InputLabelProps={{ shrink: true }}
              sx={{ width: 170, flexShrink: 0 }}
            />
            <TextField select size="small" label="Payment type" value={type} onChange={(e) => setType(e.target.value)} sx={{ width: 170, flexShrink: 0 }}>
              <MenuItem value="">All farmer payments</MenuItem>
              <MenuItem value="WEEKLY">Weekly</MenuItem>
              <MenuItem value="MONTHLY">Monthly</MenuItem>
            </TextField>
            <TextField select size="small" label="Batch status" value={statusFilter} onChange={(e) => setStatusFilter(e.target.value)} sx={{ width: 200, flexShrink: 0 }}>
              <MenuItem value="">All statuses</MenuItem>
              {['DRAFT', 'CALCULATED', 'UNDER_VERIFICATION', 'VERIFIED', 'APPROVED', 'PAYOUT_INITIATED', 'PARTIALLY_PAID', 'PAID'].map((st) => (
                <MenuItem key={st} value={st}>
                  {batchStatusLabel(st)}
                </MenuItem>
              ))}
            </TextField>
          </Stack>
        </CardContent>
      </Card>

      <QueryBoundary query={q}>
        {(d) => <DashboardBody d={d} type={type} status={statusFilter} nav={nav} onError={toast.fromError} />}
      </QueryBoundary>
    </>
  );
}

function DashboardBody({
  d,
  type,
  status,
  nav,
  onError,
}: {
  d: Dashboard;
  type: string;
  status: string;
  nav: (to: string) => void;
  onError: (e: unknown) => void;
}) {
  const keep = (b: DashBatch) => (!type || b.frequency === type) && (!status || b.status === status);
  const recent = useMemo(() => d.recentBatches.filter(keep), [d.recentBatches, type, status]); // eslint-disable-line react-hooks/exhaustive-deps
  const pending = useMemo(() => d.pendingSubmissions.filter(keep), [d.pendingSubmissions, type, status]); // eslint-disable-line react-hooks/exhaustive-deps
  const trend = useMemo(
    () => d.trend.filter((t) => !type || t.frequency === type),
    [d.trend, type],
  );
  const maxTrend = Math.max(1, ...trend.map((t) => Number(t.netPayable)));
  const open = (b: DashBatch) => nav(`/settlements/${b.id}`);

  const cards: { label: string; value: string; hint?: string; tone?: 'warn' | 'good' }[] = [
    { label: 'Total farmer payable', value: money(d.totalPayable), hint: 'weekly + monthly in this period' },
    { label: 'Weekly payable', value: money(d.weeklyPayable) },
    { label: 'Monthly payable', value: money(d.monthlyPayable) },
    { label: 'Driver salary', value: money(d.driverSalaryPayable), hint: 'this period' },
    { label: 'Bills generated', value: String(d.billsGenerated) },
    {
      label: 'Waiting for bank submission',
      value: money(d.pendingSubmissionAmount),
      hint: `${d.pendingSubmissionCount} approved batch${d.pendingSubmissionCount === 1 ? '' : 'es'}`,
      tone: d.pendingSubmissionCount > 0 ? 'warn' : undefined,
    },
    {
      label: 'Submitted to bank',
      value: money(d.submittedAmount),
      hint: `${d.submittedCount} batch${d.submittedCount === 1 ? '' : 'es'} awaiting confirmation`,
    },
    { label: 'Payment confirmed', value: money(d.confirmedPaid), hint: 'paid, bank reference recorded', tone: 'good' },
  ];


  return (
    <>
      <Grid container spacing={2} sx={{ mb: 3 }}>
        {cards.map(({ label, value, hint, tone }) => (
          <Grid size={{ xs: 6, md: 3 }} key={label}>
            <Card sx={{ height: '100%' }}>
              <CardContent sx={{ py: 1.75 }}>
                <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 500 }}>
                  {label}
                </Typography>
                <Typography
                  sx={{ fontSize: 22, fontWeight: 700, mt: 0.25 }}
                  color={tone === 'warn' ? 'warning.main' : tone === 'good' ? 'success.main' : 'text.primary'}
                >
                  {value}
                </Typography>
                {hint && (
                  <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>
                    {hint}
                  </Typography>
                )}
              </CardContent>
            </Card>
          </Grid>
        ))}
      </Grid>

      {d.upcoming.length > 0 && (
        <>
          <SectionTitle>Upcoming payments</SectionTitle>
          <Card sx={{ mb: 3, overflowX: 'auto' }}>
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell>Payment</TableCell>
                  <TableCell>Period</TableCell>
                  <TableCell>Status</TableCell>
                  <TableCell />
                </TableRow>
              </TableHead>
              <TableBody>
                {d.upcoming
                  .filter((u) => !type || u.kind === type)
                  .map((u, i) => (
                    <TableRow key={i} hover>
                      <TableCell sx={{ fontWeight: 600 }}>{u.label}</TableCell>
                      <TableCell>
                        {u.periodStart} → {u.periodEnd}
                      </TableCell>
                      <TableCell>
                        {u.status === 'NOT_STARTED' ? (
                          <StatusChip status="PENDING" label="Not started" />
                        ) : (
                          <StatusChip status={u.status} label={batchStatusLabel(u.status)} />
                        )}
                      </TableCell>
                      <TableCell align="right">
                        <Button
                          size="small"
                          onClick={() =>
                            nav(
                              u.batchId && u.kind !== 'DRIVER_SALARY'
                                ? `/settlements/${u.batchId}`
                                : u.kind === 'WEEKLY'
                                  ? '/payments/weekly'
                                  : u.kind === 'MONTHLY'
                                    ? '/payments/monthly'
                                    : '/payments/drivers',
                            )
                          }
                        >
                          {u.status === 'NOT_STARTED' ? 'Start' : 'Open'}
                        </Button>
                      </TableCell>
                    </TableRow>
                  ))}
              </TableBody>
            </Table>
          </Card>
        </>
      )}

      {pending.length > 0 && (
        <>
          <SectionTitle>Pending bank submissions</SectionTitle>
          <Card sx={{ mb: 3, overflowX: 'auto' }}>
            <BatchTable rows={pending} onOpen={open} actionLabel="Submit" />
          </Card>
        </>
      )}

      <Grid container spacing={2} sx={{ mb: 3 }}>
        <Grid size={{ xs: 12, md: 7 }}>
          <SectionTitle>Recent payment batches</SectionTitle>
          <Card sx={{ overflowX: 'auto' }}>
            {recent.length === 0 ? (
              <Typography color="text.secondary" sx={{ p: 3, textAlign: 'center' }}>
                No payment batches in this period.
              </Typography>
            ) : (
              <BatchTable rows={recent} onOpen={open} actionLabel="Open" />
            )}
          </Card>
        </Grid>
        <Grid size={{ xs: 12, md: 5 }}>
          <SectionTitle>Payable by period</SectionTitle>
          <Card sx={{ height: 'calc(100% - 36px)' }}>
            <CardContent>
              {trend.length === 0 ? (
                <Typography color="text.secondary" sx={{ textAlign: 'center', py: 3 }}>
                  Nothing to chart yet.
                </Typography>
              ) : (
                <Stack spacing={1}>
                  {trend.map((t, i) => (
                    <Stack key={i} direction="row" spacing={1.5} alignItems="center">
                      <Typography variant="caption" color="text.secondary" sx={{ width: 78, flexShrink: 0 }}>
                        {t.periodStart.slice(5)} · {t.frequency === 'MONTHLY' ? 'Mo' : 'Wk'}
                      </Typography>
                      <Box sx={{ flex: 1, bgcolor: 'action.hover', borderRadius: 1, height: 14 }}>
                        <Box
                          sx={{
                            width: `${(Number(t.netPayable) / maxTrend) * 100}%`,
                            minWidth: Number(t.netPayable) > 0 ? 4 : 0,
                            height: '100%',
                            borderRadius: 1,
                            bgcolor: t.frequency === 'MONTHLY' ? 'secondary.main' : 'primary.main',
                          }}
                        />
                      </Box>
                      <Typography variant="caption" sx={{ width: 78, textAlign: 'right', fontWeight: 600 }}>
                        {money(t.netPayable)}
                      </Typography>
                    </Stack>
                  ))}
                </Stack>
              )}
            </CardContent>
          </Card>
        </Grid>
      </Grid>

      <Grid container spacing={2}>
        <Grid size={{ xs: 12, md: 6 }}>
          <SectionTitle>Recent bills</SectionTitle>
          <Card sx={{ overflowX: 'auto' }}>
            {d.recentBills.length === 0 ? (
              <Typography color="text.secondary" sx={{ p: 3, textAlign: 'center' }}>
                No bills generated yet.
              </Typography>
            ) : (
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell>Bill</TableCell>
                    <TableCell>Producer</TableCell>
                    <TableCell align="right">Net payable</TableCell>
                    <TableCell />
                  </TableRow>
                </TableHead>
                <TableBody>
                  {d.recentBills.map((b) => (
                    <TableRow key={b.itemId} hover>
                      <TableCell sx={{ fontWeight: 600 }}>{b.billNumber}</TableCell>
                      <TableCell>
                        {b.producerName}
                        <Typography variant="caption" color="text.secondary" display="block">
                          {b.producerNumber}
                        </Typography>
                      </TableCell>
                      <TableCell align="right">{money(b.netPayable)}</TableCell>
                      <TableCell align="right">
                        <Button
                          size="small"
                          onClick={() => openPdf(`/settlements/${b.settlementId}/items/${b.itemId}/bill`).catch(onError)}
                        >
                          View
                        </Button>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            )}
          </Card>
        </Grid>
        <Grid size={{ xs: 12, md: 6 }}>
          <SectionTitle>Recent activity</SectionTitle>
          <Card>
            <CardContent>
              {d.recentActivity.length === 0 ? (
                <Typography color="text.secondary" sx={{ textAlign: 'center', py: 2 }}>
                  No activity yet.
                </Typography>
              ) : (
                <Stack spacing={1.25}>
                  {d.recentActivity.map((a, i) => (
                    <Box key={i}>
                      <Typography variant="body2">
                        <b>{a.settlementNumber}</b> ·{' '}
                        {a.subjectType === 'ITEM'
                          ? `producer payment ${a.fromStatus ? lineStatusLabel(a.fromStatus) + ' → ' : ''}${lineStatusLabel(a.toStatus)}`
                          : a.fromStatus && a.fromStatus !== a.toStatus
                            ? `${batchStatusLabel(a.fromStatus)} → ${batchStatusLabel(a.toStatus)}`
                            : (a.note ?? batchStatusLabel(a.toStatus))}
                      </Typography>
                      <Typography variant="caption" color="text.secondary">
                        {new Date(a.changedAt).toLocaleString()}
                      </Typography>
                    </Box>
                  ))}
                </Stack>
              )}
            </CardContent>
          </Card>
        </Grid>
      </Grid>
    </>
  );
}

function BatchTable({ rows, onOpen, actionLabel }: { rows: DashBatch[]; onOpen: (b: DashBatch) => void; actionLabel: string }) {
  return (
    <Table size="small">
      <TableHead>
        <TableRow>
          <TableCell>Batch</TableCell>
          <TableCell>Period</TableCell>
          <TableCell align="right">Producers</TableCell>
          <TableCell align="right">Net payable</TableCell>
          <TableCell>Status</TableCell>
          <TableCell />
        </TableRow>
      </TableHead>
      <TableBody>
        {rows.map((b) => (
          <TableRow key={b.id} hover sx={{ cursor: 'pointer' }} onClick={() => onOpen(b)}>
            <TableCell sx={{ fontWeight: 600 }}>
              {b.number}
              <Typography variant="caption" color="text.secondary" display="block">
                {b.frequency === 'MONTHLY' ? 'Monthly' : b.frequency === 'WEEKLY' ? 'Weekly' : 'Other'}
              </Typography>
            </TableCell>
            <TableCell>
              {b.periodStart} → {b.periodEnd}
            </TableCell>
            <TableCell align="right">{b.producers}</TableCell>
            <TableCell align="right" sx={{ fontWeight: 700 }}>
              {money(b.netPayable)}
            </TableCell>
            <TableCell>
              <StatusChip status={b.status} label={batchStatusLabel(b.status)} />
            </TableCell>
            <TableCell align="right">
              <Button size="small">{actionLabel}</Button>
            </TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}
