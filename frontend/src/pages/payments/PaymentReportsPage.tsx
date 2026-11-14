import { useState } from 'react';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import ButtonGroup from '@mui/material/ButtonGroup';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import MenuItem from '@mui/material/MenuItem';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import PictureAsPdfIcon from '@mui/icons-material/PictureAsPdf';
import GridOnIcon from '@mui/icons-material/GridOn';
import DescriptionIcon from '@mui/icons-material/Description';
import PrintOutlinedIcon from '@mui/icons-material/PrintOutlined';
import { useQuery } from '@tanstack/react-query';
import { api } from '../../lib/api';
import { downloadFile, openPdf } from '../../api/common';
import { batchStatusLabel } from '../../lib/paymentLabels';
import { PageHeader } from '../../components/PageHeader';
import { SectionTitle, useToast } from '../../components/ui';
import { ProducerPicker } from '../../components/ProducerPicker';
import type { Producer, SalaryBatchSummary, SettlementSummary } from '../../api/types';

/** Start of the current year — payment batches are weekly/monthly, so a 30-day window hides most of them. */
const monthAgo = () => `${new Date().getFullYear()}-01-01`;
const today = () => new Date().toISOString().slice(0, 10);

export function ExportButtons({ base, name }: { base: string; name: string }) {
  const toast = useToast();
  const sep = base.includes('?') ? '&' : '?';
  const go = (fmt: string, ext: string) => downloadFile(`${base}${sep}format=${fmt}`, `${name}.${ext}`).catch(toast.fromError);
  return (
    <ButtonGroup size="small" variant="outlined">
      <Button startIcon={<PictureAsPdfIcon />} onClick={() => go('pdf', 'pdf')}>
        PDF
      </Button>
      <Button startIcon={<GridOnIcon />} onClick={() => go('xlsx', 'xlsx')}>
        Excel
      </Button>
      <Button startIcon={<DescriptionIcon />} onClick={() => go('csv', 'csv')}>
        CSV
      </Button>
      <Button startIcon={<PrintOutlinedIcon />} onClick={() => openPdf(`${base}${sep}format=pdf`).catch(toast.fromError)}>
        Print
      </Button>
    </ButtonGroup>
  );
}

const iso = (d: Date) =>
  `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;

type Span = 'WEEK' | 'MONTH' | 'CUSTOM';

/** Monday–Sunday around any date. */
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

/** Per-producer milk statement for a week, a month or any custom range — every collection with FAT, SNF, rate and amount. */
function ProducerStatementCard() {
  const [producer, setProducer] = useState<Producer | null>(null);
  const [span, setSpan] = useState<Span>('MONTH');
  const [weekAnchor, setWeekAnchor] = useState(iso(new Date()));
  const [monthAnchor, setMonthAnchor] = useState(iso(new Date()).slice(0, 7));
  const [customFrom, setCustomFrom] = useState(iso(new Date(new Date().getFullYear(), new Date().getMonth(), 1)));
  const [customTo, setCustomTo] = useState(iso(new Date()));

  const range =
    span === 'WEEK' ? weekOf(weekAnchor) : span === 'MONTH' ? monthOf(monthAnchor) : { from: customFrom, to: customTo };
  const valid = !!range.from && !!range.to && range.from <= range.to;

  return (
    <Card sx={{ mb: 3 }}>
      <CardContent>
        <Stack spacing={2}>
          <Typography variant="body2" color="text.secondary">
            Every collection for the producer — date, morning/evening, time, litres, FAT, SNF, rate per litre and amount —
            with totals, deductions, advances and payments.
          </Typography>
          <Stack direction="row" spacing={2} alignItems="center" flexWrap="wrap" useFlexGap>
            <Box sx={{ width: 320, flexShrink: 0 }}>
              <ProducerPicker value={producer} onChange={setProducer} label="Producer" />
            </Box>
            <ButtonGroup size="small" variant="outlined" sx={{ flexShrink: 0 }}>
              {(['WEEK', 'MONTH', 'CUSTOM'] as const).map((k) => (
                <Button key={k} variant={span === k ? 'contained' : 'outlined'} onClick={() => setSpan(k)}>
                  {k === 'WEEK' ? 'Weekly' : k === 'MONTH' ? 'Monthly' : 'Custom range'}
                </Button>
              ))}
            </ButtonGroup>
            {span === 'WEEK' && (
              <TextField
                type="date"
                size="small"
                label="Week containing"
                value={weekAnchor}
                onChange={(e) => setWeekAnchor(e.target.value)}
                InputLabelProps={{ shrink: true }}
                sx={{ width: 180, flexShrink: 0 }}
              />
            )}
            {span === 'MONTH' && (
              <TextField
                type="month"
                size="small"
                label="Month"
                value={monthAnchor}
                onChange={(e) => setMonthAnchor(e.target.value)}
                InputLabelProps={{ shrink: true }}
                sx={{ width: 180, flexShrink: 0 }}
              />
            )}
            {span === 'CUSTOM' && (
              <>
                <TextField
                  type="date"
                  size="small"
                  label="From"
                  value={customFrom}
                  onChange={(e) => setCustomFrom(e.target.value)}
                  InputLabelProps={{ shrink: true }}
                  sx={{ width: 170, flexShrink: 0 }}
                />
                <TextField
                  type="date"
                  size="small"
                  label="To"
                  value={customTo}
                  onChange={(e) => setCustomTo(e.target.value)}
                  InputLabelProps={{ shrink: true }}
                  sx={{ width: 170, flexShrink: 0 }}
                />
              </>
            )}
            <Typography variant="body2" color="text.secondary" sx={{ flexShrink: 0, whiteSpace: 'nowrap' }}>
              {valid ? `${range.from} → ${range.to}` : 'Pick a valid range'}
            </Typography>
          </Stack>
          {producer && valid ? (
            <ExportButtons
              base={`/reports/producer-statement?producerId=${producer.id}&from=${range.from}&to=${range.to}`}
              name={`statement-${producer.producerNumber}-${range.from}-to-${range.to}`}
            />
          ) : (
            <Typography variant="caption" color="text.secondary">
              Choose a producer to download the statement.
            </Typography>
          )}
        </Stack>
      </CardContent>
    </Card>
  );
}

export function PaymentReportsPage() {
  const [from, setFrom] = useState(monthAgo());
  const [to, setTo] = useState(today());
  const [settlementId, setSettlementId] = useState('');
  const [batchId, setBatchId] = useState('');

  const settlements = useQuery({
    queryKey: ['settlements'],
    queryFn: async () => (await api.get<SettlementSummary[]>('/settlements')).data,
  });
  const batches = useQuery({
    queryKey: ['salary-batches'],
    queryFn: async () => (await api.get<SalaryBatchSummary[]>('/salary-batches')).data,
  });

  return (
    <>
      <PageHeader title="Payment Reports" subtitle="Farmer payment and driver-salary reports — PDF / Excel / CSV" />

      <SectionTitle>Producer statement</SectionTitle>
      <ProducerStatementCard />

      <SectionTitle>Farmer payments</SectionTitle>
      <Card sx={{ mb: 3 }}>
        <CardContent>
          <Stack spacing={2.5}>
            <Stack direction="row" spacing={2} alignItems="center" flexWrap="wrap" useFlexGap>
              <TextField
                select
                label="Payment run"
                size="small"
                value={settlementId}
                onChange={(e) => setSettlementId(e.target.value)}
                sx={{ width: 320, flexShrink: 0 }}
              >
                <MenuItem value="">Select a run…</MenuItem>
                {(settlements.data ?? []).map((s) => (
                  <MenuItem key={s.id} value={String(s.internalId)}>
                    {s.settlementNumber} · {s.periodStart}→{s.periodEnd} · {batchStatusLabel(s.status)}
                  </MenuItem>
                ))}
              </TextField>
              {settlementId && (
                <Stack spacing={1} sx={{ flexShrink: 0 }}>
                  <Stack direction="row" spacing={1} alignItems="center">
                    <Typography variant="caption" sx={{ width: 150, fontWeight: 600 }}>Batch summary</Typography>
                    <ExportButtons base={`/reports/settlement/${settlementId}/summary`} name={`payment-summary-${settlementId}`} />
                  </Stack>
                  <Stack direction="row" spacing={1} alignItems="center">
                    <Typography variant="caption" sx={{ width: 150, fontWeight: 600 }}>Payment status</Typography>
                    <ExportButtons base={`/reports/settlement/${settlementId}/payments`} name={`payment-run-${settlementId}`} />
                  </Stack>
                </Stack>
              )}
            </Stack>
            <Typography variant="caption" color="text.secondary" sx={{ display: settlementId ? 'block' : 'none' }}>
              Batch summary: every producer with milk amount, incentive, deductions, net payable, bill number and bank reference — the sheet for the bank submission.
            </Typography>

            <Stack direction="row" spacing={2} alignItems="center" flexWrap="wrap" useFlexGap>
              <TextField
                label="From"
                type="date"
                size="small"
                value={from}
                onChange={(e) => setFrom(e.target.value)}
                InputLabelProps={{ shrink: true }}
                sx={{ width: 170, flexShrink: 0 }}
              />
              <TextField
                label="To"
                type="date"
                size="small"
                value={to}
                onChange={(e) => setTo(e.target.value)}
                InputLabelProps={{ shrink: true }}
                sx={{ width: 170, flexShrink: 0 }}
              />
              <Box sx={{ flexShrink: 0 }}>
                <ExportButtons base={`/reports/region?from=${from}&to=${to}`} name="region-payments" />
              </Box>
              <Typography variant="caption" color="text.secondary" sx={{ flexShrink: 0, whiteSpace: 'nowrap' }}>
                Region-wise payment summary
              </Typography>
            </Stack>

          </Stack>
        </CardContent>
      </Card>

      <SectionTitle>Payment tracking reports</SectionTitle>
      <Card sx={{ mb: 3 }}>
        <CardContent>
          <Stack spacing={2}>
            <Typography variant="caption" color="text.secondary">
              Uses the From / To dates above, matched to each payment batch's period.
            </Typography>
            {(
              [
                ['pending', 'Pending payments', 'Producers whose payment is not yet confirmed by the bank'],
                ['bank-submissions', 'Bank submissions', 'Batches handed to the bank, with date and bank reference'],
                ['confirmations', 'Payment confirmations', 'Payments confirmed as paid, with bank transaction reference'],
                ['incentives-deductions', 'Incentives & deductions', 'Incentive, feed, veterinary, other deductions and advance recovery by batch'],
                ['period-summary', 'Period-wise summary', 'Weekly and monthly batches side by side'],
              ] as const
            ).map(([kind, title, hint]) => (
              <Stack key={kind} direction="row" spacing={2} alignItems="center" flexWrap="wrap" useFlexGap>
                <Box sx={{ width: 220, flexShrink: 0 }}>
                  <Typography variant="body2" sx={{ fontWeight: 600 }}>{title}</Typography>
                  <Typography variant="caption" color="text.secondary">{hint}</Typography>
                </Box>
                <Box sx={{ flexShrink: 0 }}>
                  <ExportButtons base={`/reports/payments/${kind}?from=${from}&to=${to}`} name={`payment-${kind}`} />
                </Box>
              </Stack>
            ))}
          </Stack>
        </CardContent>
      </Card>

      <SectionTitle>Driver salary</SectionTitle>
      <Card>
        <CardContent>
          <Stack direction="row" spacing={2} alignItems="center" flexWrap="wrap" useFlexGap>
            <TextField
              select
              label="Salary batch"
              size="small"
              value={batchId}
              onChange={(e) => setBatchId(e.target.value)}
              sx={{ width: 320, flexShrink: 0 }}
            >
              <MenuItem value="">Select a batch…</MenuItem>
              {(batches.data ?? []).map((b) => (
                <MenuItem key={b.id} value={String(b.id)}>
                  {b.batchNumber} · {b.periodMonth.slice(0, 7)} · {b.status}
                </MenuItem>
              ))}
            </TextField>
            {batchId && (
              <Box sx={{ flexShrink: 0 }}>
                <ExportButtons base={`/salary-batches/${batchId}/report`} name={`driver-salary-${batchId}`} />
              </Box>
            )}
          </Stack>
          <Typography variant="caption" color="text.secondary" sx={{ mt: 1, display: 'block' }}>
            Pending and failed payments are on the Payment Activity screen — filter by status.
          </Typography>
        </CardContent>
      </Card>
    </>
  );
}
