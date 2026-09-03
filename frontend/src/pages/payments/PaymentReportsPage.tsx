import { useState } from 'react';
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
import { useQuery } from '@tanstack/react-query';
import { api } from '../../lib/api';
import { downloadFile } from '../../api/common';
import { PageHeader } from '../../components/PageHeader';
import { SectionTitle, useToast } from '../../components/ui';
import { ProducerPicker } from '../../components/ProducerPicker';
import type { Producer, SalaryBatchSummary, SettlementSummary } from '../../api/types';

const monthAgo = () => {
  const d = new Date();
  d.setDate(d.getDate() - 30);
  return d.toISOString().slice(0, 10);
};
const today = () => new Date().toISOString().slice(0, 10);

function ExportButtons({ base, name }: { base: string; name: string }) {
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
    </ButtonGroup>
  );
}

export function PaymentReportsPage() {
  const [from, setFrom] = useState(monthAgo());
  const [to, setTo] = useState(today());
  const [settlementId, setSettlementId] = useState('');
  const [batchId, setBatchId] = useState('');
  const [producer, setProducer] = useState<Producer | null>(null);

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

      <SectionTitle>Farmer payments</SectionTitle>
      <Card sx={{ mb: 3 }}>
        <CardContent>
          <Stack spacing={2.5}>
            <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} alignItems={{ sm: 'center' }}>
              <TextField
                select
                label="Payment run"
                size="small"
                value={settlementId}
                onChange={(e) => setSettlementId(e.target.value)}
                sx={{ minWidth: 260 }}
              >
                <MenuItem value="">Select a run…</MenuItem>
                {(settlements.data ?? []).map((s) => (
                  <MenuItem key={s.id} value={String(s.internalId)}>
                    {s.settlementNumber} · {s.periodStart}→{s.periodEnd} · {s.status}
                  </MenuItem>
                ))}
              </TextField>
              {settlementId && (
                <ExportButtons base={`/reports/settlement/${settlementId}/payments`} name={`payment-run-${settlementId}`} />
              )}
            </Stack>
            <Typography variant="caption" color="text.secondary">
              The run report lists every producer with milk amount, deductions, advance recovery, net payable and payment status.
            </Typography>

            <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} alignItems={{ sm: 'center' }}>
              <TextField label="From" type="date" size="small" value={from} onChange={(e) => setFrom(e.target.value)} InputLabelProps={{ shrink: true }} />
              <TextField label="To" type="date" size="small" value={to} onChange={(e) => setTo(e.target.value)} InputLabelProps={{ shrink: true }} />
              <ExportButtons base={`/reports/region?from=${from}&to=${to}`} name="region-payments" />
              <Typography variant="caption" color="text.secondary">
                Region-wise payment summary
              </Typography>
            </Stack>

            <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} alignItems={{ sm: 'center' }}>
              <ProducerPicker value={producer} onChange={setProducer} label="Producer statement" />
              {producer && (
                <ExportButtons
                  base={`/reports/producer-statement?producerId=${producer.id}&from=${from}&to=${to}`}
                  name={`producer-${producer.producerNumber}-statement`}
                />
              )}
            </Stack>
          </Stack>
        </CardContent>
      </Card>

      <SectionTitle>Driver salary</SectionTitle>
      <Card>
        <CardContent>
          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} alignItems={{ sm: 'center' }}>
            <TextField
              select
              label="Salary batch"
              size="small"
              value={batchId}
              onChange={(e) => setBatchId(e.target.value)}
              sx={{ minWidth: 260 }}
            >
              <MenuItem value="">Select a batch…</MenuItem>
              {(batches.data ?? []).map((b) => (
                <MenuItem key={b.id} value={String(b.id)}>
                  {b.batchNumber} · {b.periodMonth.slice(0, 7)} · {b.status}
                </MenuItem>
              ))}
            </TextField>
            {batchId && <ExportButtons base={`/salary-batches/${batchId}/report`} name={`driver-salary-${batchId}`} />}
          </Stack>
          <Typography variant="caption" color="text.secondary" sx={{ mt: 1, display: 'block' }}>
            Pending / failed payments are on the Payment History screen — filter by status.
          </Typography>
        </CardContent>
      </Card>
    </>
  );
}
