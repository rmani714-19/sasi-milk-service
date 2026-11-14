import { useMemo, useState } from 'react';
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
import TableContainer from '@mui/material/TableContainer';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import ChevronLeftIcon from '@mui/icons-material/ChevronLeft';
import ChevronRightIcon from '@mui/icons-material/ChevronRight';
import { useQuery } from '@tanstack/react-query';
import { api } from '../../lib/api';
import { litres, money } from '../../lib/format';
import { regionLabel } from '../../lib/region';
import { useRegions } from '../../api/common';
import { lineStatusLabel } from '../../lib/paymentLabels';
import { PageHeader } from '../../components/PageHeader';
import { StatusChip } from '../../components/StatusChip';
import { EmptyState, QueryBoundary, TableSkeleton } from '../../components/ui';
import { ExportButtons } from './PaymentReportsPage';

interface ProducerTotal {
  producerId: number;
  producerNumber: string;
  producerName: string;
  regionId?: number | null;
  region: string;
  days: number;
  litres: number;
  totalAmount: number;
  batchNumber?: string | null;
  netPayable?: number | null;
  paymentStatus?: string | null;
  paymentReference?: string | null;
}

const iso = (d: Date) =>
  `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;

function weekOf(anchor: string) {
  const d = new Date(anchor + 'T00:00:00');
  const start = new Date(d);
  start.setDate(d.getDate() - ((d.getDay() + 6) % 7));
  const end = new Date(start);
  end.setDate(start.getDate() + 6);
  return { from: iso(start), to: iso(end) };
}
function monthOf(anchor: string) {
  const d = new Date(anchor + '-01T00:00:00');
  return { from: iso(new Date(d.getFullYear(), d.getMonth(), 1)), to: iso(new Date(d.getFullYear(), d.getMonth() + 1, 0)) };
}

export function WeeklyVerificationPage() {
  return <VerificationPage frequency="WEEKLY" />;
}
export function MonthlyVerificationPage() {
  return <VerificationPage frequency="MONTHLY" />;
}

/**
 * Producer ID with the weekly (or monthly) total amount, for checking the numbers before a payment run is
 * prepared and again after payment. Shows the batch's net payable and payment status once a run exists.
 */
function VerificationPage({ frequency }: { frequency: 'WEEKLY' | 'MONTHLY' }) {
  const weekly = frequency === 'WEEKLY';
  const kind = weekly ? 'Weekly' : 'Monthly';
  const regions = useRegions();
  const [weekAnchor, setWeekAnchor] = useState(iso(new Date()));
  const [monthAnchor, setMonthAnchor] = useState(iso(new Date()).slice(0, 7));
  const [regionId, setRegionId] = useState(''); // '' = all, 'none' = no region
  const [q, setQ] = useState('');
  const [sort, setSort] = useState<'id' | 'amount'>('id');

  const range = weekly ? weekOf(weekAnchor) : monthOf(monthAnchor);
  const regionParam = regionId === '' ? undefined : regionId === 'none' ? 0 : Number(regionId);

  const list = useQuery({
    queryKey: ['producer-totals', frequency, range.from, range.to, regionParam],
    queryFn: async () =>
      (
        await api.get<ProducerTotal[]>('/reports/producer-totals', {
          params: { frequency, from: range.from, to: range.to, regionId: regionParam },
        })
      ).data,
  });

  const shift = (dir: -1 | 1) => {
    if (weekly) {
      const d = new Date(weekAnchor + 'T00:00:00');
      d.setDate(d.getDate() + dir * 7);
      setWeekAnchor(iso(d));
    } else {
      const d = new Date(monthAnchor + '-01T00:00:00');
      d.setMonth(d.getMonth() + dir);
      setMonthAnchor(iso(d).slice(0, 7));
    }
  };

  const rows = useMemo(() => {
    const needle = q.trim().toLowerCase();
    const filtered = (list.data ?? []).filter(
      (r) => !needle || r.producerNumber.toLowerCase().includes(needle) || r.producerName.toLowerCase().includes(needle),
    );
    return [...filtered].sort(
      sort === 'amount'
        ? (a, b) => Number(b.totalAmount) - Number(a.totalAmount)
        : (a, b) => a.producerNumber.localeCompare(b.producerNumber, undefined, { numeric: true }),
    );
  }, [list.data, q, sort]);

  const totalAmount = rows.reduce((s, r) => s + Number(r.totalAmount), 0);
  const totalLitres = rows.reduce((s, r) => s + Number(r.litres), 0);
  const totalNet = rows.reduce((s, r) => s + Number(r.netPayable ?? 0), 0);
  const paid = rows.filter((r) => r.paymentStatus === 'SUCCESS').reduce((s, r) => s + Number(r.netPayable ?? 0), 0);
  const inBatch = rows.filter((r) => r.batchNumber).length;

  const exportBase = `/reports/producer-totals?frequency=${frequency}&from=${range.from}&to=${range.to}${
    regionParam === undefined ? '' : `&regionId=${regionParam}`
  }`;

  return (
    <>
      <PageHeader
        title={`${kind} Verification`}
        subtitle={`Producer ID and ${kind.toLowerCase()} total amount — check the numbers before the payment run and after payment`}
      />

      <Card sx={{ mb: 3 }}>
        <CardContent>
          <Stack direction="row" spacing={1.5} alignItems="center" flexWrap="wrap" useFlexGap>
            <Button size="small" variant="outlined" onClick={() => shift(-1)} sx={{ minWidth: 0, flexShrink: 0 }} aria-label="Previous">
              <ChevronLeftIcon fontSize="small" />
            </Button>
            {weekly ? (
              <TextField
                type="date"
                size="small"
                label="Week containing"
                value={weekAnchor}
                onChange={(e) => setWeekAnchor(e.target.value)}
                InputLabelProps={{ shrink: true }}
                sx={{ width: 190, flexShrink: 0 }}
              />
            ) : (
              <TextField
                type="month"
                size="small"
                label="Month"
                value={monthAnchor}
                onChange={(e) => setMonthAnchor(e.target.value)}
                InputLabelProps={{ shrink: true }}
                sx={{ width: 190, flexShrink: 0 }}
              />
            )}
            <Button size="small" variant="outlined" onClick={() => shift(1)} sx={{ minWidth: 0, flexShrink: 0 }} aria-label="Next">
              <ChevronRightIcon fontSize="small" />
            </Button>
            <Typography variant="body2" color="text.secondary" sx={{ flexShrink: 0, whiteSpace: 'nowrap' }}>
              {range.from} → {range.to}
            </Typography>
            <TextField select size="small" label="Region / area" value={regionId} onChange={(e) => setRegionId(e.target.value)} sx={{ width: 200, flexShrink: 0 }}>
              <MenuItem value="">All regions</MenuItem>
              {(regions.data ?? [])
                .filter((r) => r.status === 'ACTIVE')
                .map((r) => (
                  <MenuItem key={r.id} value={String(r.id)}>
                    {regionLabel(r)}
                  </MenuItem>
                ))}
              <MenuItem value="none">No region assigned</MenuItem>
            </TextField>
            <TextField select size="small" label="Order by" value={sort} onChange={(e) => setSort(e.target.value as 'id' | 'amount')} sx={{ width: 190, flexShrink: 0 }}>
              <MenuItem value="id">Producer ID</MenuItem>
              <MenuItem value="amount">{kind} total (highest first)</MenuItem>
            </TextField>
            <TextField
              size="small"
              placeholder="Search producer ID or name"
              value={q}
              onChange={(e) => setQ(e.target.value)}
              sx={{ minWidth: 200, flex: '1 1 200px' }}
            />
          </Stack>
        </CardContent>
      </Card>

      <Grid container spacing={2} sx={{ mb: 3 }}>
        {[
          ['Producers', String(rows.length)],
          ['Total litres', litres(totalLitres)],
          [`${kind} total amount`, money(totalAmount)],
          ['Net payable (in batch)', inBatch > 0 ? money(totalNet) : '—'],
          ['Confirmed paid', inBatch > 0 ? money(paid) : '—'],
        ].map(([k, v]) => (
          <Grid size={{ xs: 6, md: 2.4 }} key={k}>
            <Card sx={{ height: '100%' }}>
              <CardContent sx={{ py: 1.5 }}>
                <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 500 }}>
                  {k}
                </Typography>
                <Typography sx={{ fontSize: 20, fontWeight: 700, mt: 0.25 }}>{v}</Typography>
              </CardContent>
            </Card>
          </Grid>
        ))}
      </Grid>

      <Box sx={{ mb: 2 }}>
        <ExportButtons base={exportBase} name={`${kind.toLowerCase()}-producer-totals-${range.from}-to-${range.to}`} />
      </Box>

      <Card>
        <QueryBoundary
          query={list}
          skeleton={<TableSkeleton cols={8} />}
          empty={() => rows.length === 0}
          emptyState={<EmptyState title="No collections" description={`No ${kind.toLowerCase()} producers have collections in this period.`} />}
        >
          {() => (
            <TableContainer sx={{ maxHeight: 'calc(100vh - 460px)' }}>
              <Table size="small" stickyHeader>
                <TableHead>
                  <TableRow>
                    <TableCell>Producer ID</TableCell>
                    <TableCell>Name</TableCell>
                    <TableCell>Region</TableCell>
                    <TableCell align="right">Days</TableCell>
                    <TableCell align="right">Litres</TableCell>
                    <TableCell align="right">{kind} total amount</TableCell>
                    <TableCell align="right">Net payable</TableCell>
                    <TableCell>Payment</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {rows.map((r) => (
                    <TableRow key={r.producerId} hover>
                      <TableCell sx={{ fontWeight: 700 }}>{r.producerNumber}</TableCell>
                      <TableCell>{r.producerName}</TableCell>
                      <TableCell>{r.region || '—'}</TableCell>
                      <TableCell align="right">{r.days}</TableCell>
                      <TableCell align="right">{litres(r.litres)}</TableCell>
                      <TableCell align="right" sx={{ fontWeight: 700 }}>
                        {money(r.totalAmount)}
                      </TableCell>
                      <TableCell align="right">{r.netPayable != null ? money(r.netPayable) : '—'}</TableCell>
                      <TableCell>
                        {r.paymentStatus ? (
                          <>
                            <StatusChip status={r.paymentStatus} label={lineStatusLabel(r.paymentStatus)} />
                            <Typography variant="caption" color="text.secondary" display="block">
                              {r.batchNumber}
                              {r.paymentReference ? ` · Ref ${r.paymentReference}` : ''}
                            </Typography>
                          </>
                        ) : (
                          <Typography variant="caption" color="text.secondary">
                            Not in a payment batch yet
                          </Typography>
                        )}
                      </TableCell>
                    </TableRow>
                  ))}
                  <TableRow>
                    <TableCell colSpan={4} sx={{ fontWeight: 700 }}>
                      Total ({rows.length} producers)
                    </TableCell>
                    <TableCell align="right" sx={{ fontWeight: 700 }}>
                      {litres(totalLitres)}
                    </TableCell>
                    <TableCell align="right" sx={{ fontWeight: 700 }}>
                      {money(totalAmount)}
                    </TableCell>
                    <TableCell align="right" sx={{ fontWeight: 700 }}>
                      {inBatch > 0 ? money(totalNet) : '—'}
                    </TableCell>
                    <TableCell />
                  </TableRow>
                </TableBody>
              </Table>
            </TableContainer>
          )}
        </QueryBoundary>
      </Card>
      <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 1.5 }}>
        {kind} total amount = milk amount + incentive for the period. Net payable is what remains after deductions and
        advance recovery, as set in the payment batch.
      </Typography>
    </>
  );
}
