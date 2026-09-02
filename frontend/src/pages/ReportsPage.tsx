import { useState } from 'react';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Stack from '@mui/material/Stack';
import Tab from '@mui/material/Tab';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import Tabs from '@mui/material/Tabs';
import TextField from '@mui/material/TextField';
import DownloadIcon from '@mui/icons-material/Download';
import { useQuery } from '@tanstack/react-query';
import { api } from '../lib/api';
import { downloadCsv } from '../api/common';
import { litres, money, today } from '../lib/format';
import { PageHeader } from '../components/PageHeader';
import { ProducerPicker } from '../components/ProducerPicker';
import { useToast } from '../components/ui';
import type { Producer } from '../api/types';

const monthAgo = () => {
  const d = new Date();
  d.setDate(d.getDate() - 30);
  return d.toISOString().slice(0, 10);
};

export function ReportsPage() {
  const [tab, setTab] = useState(0);
  const [from, setFrom] = useState(monthAgo());
  const [to, setTo] = useState(today());

  return (
    <>
      <PageHeader title="Reports" />
      <Card sx={{ mb: 3 }}>
        <CardContent>
          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
            <TextField label="From" type="date" value={from} onChange={(e) => setFrom(e.target.value)} InputLabelProps={{ shrink: true }} />
            <TextField label="To" type="date" value={to} onChange={(e) => setTo(e.target.value)} InputLabelProps={{ shrink: true }} />
          </Stack>
        </CardContent>
      </Card>

      <Tabs value={tab} onChange={(_, v) => setTab(v)} sx={{ mb: 2 }} variant="scrollable">
        <Tab label="Daily collection" />
        <Tab label="By region" />
        <Tab label="Farmer statement" />
      </Tabs>

      {tab === 0 && <DailyCollection from={from} to={to} />}
      {tab === 1 && <RegionReport from={from} to={to} />}
      {tab === 2 && <ProducerStatement from={from} to={to} />}
    </>
  );
}

function CsvButton({ url, name }: { url: string; name: string }) {
  const toast = useToast();
  return (
    <Button
      size="small"
      startIcon={<DownloadIcon />}
      onClick={() => downloadCsv(url, name).catch(toast.fromError)}
      sx={{ mb: 1 }}
    >
      CSV
    </Button>
  );
}

function DailyCollection({ from, to }: { from: string; to: string }) {
  const q = useQuery({
    queryKey: ['report', 'daily', from, to],
    queryFn: async () =>
      (await api.get<Record<string, number | string>[]>('/reports/daily-collection', { params: { from, to } })).data,
  });
  return (
    <>
      <CsvButton url={`/reports/daily-collection?from=${from}&to=${to}&format=csv`} name="daily-collection.csv" />
      <Card sx={{ overflowX: 'auto' }}>
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell>Date</TableCell>
              <TableCell align="right">Morning</TableCell>
              <TableCell align="right">Evening</TableCell>
              <TableCell align="right">Total</TableCell>
              <TableCell align="right">Farmers</TableCell>
              <TableCell align="right">Avg rate</TableCell>
              <TableCell align="right">Amount</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {q.data?.map((r) => (
              <TableRow key={String(r.date)}>
                <TableCell>{String(r.date)}</TableCell>
                <TableCell align="right">{litres(r.morningLitres as number)}</TableCell>
                <TableCell align="right">{litres(r.eveningLitres as number)}</TableCell>
                <TableCell align="right">{litres(r.totalLitres as number)}</TableCell>
                <TableCell align="right">{r.producerCount}</TableCell>
                <TableCell align="right">₹{Number(r.averageRate).toFixed(2)}</TableCell>
                <TableCell align="right">{money(r.totalAmount as number)}</TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </Card>
    </>
  );
}

function RegionReport({ from, to }: { from: string; to: string }) {
  const q = useQuery({
    queryKey: ['report', 'region', from, to],
    queryFn: async () =>
      (await api.get<Record<string, number | string>[]>('/reports/region', { params: { from, to } })).data,
  });
  return (
    <>
      <CsvButton url={`/reports/region?from=${from}&to=${to}&format=csv`} name="region-report.csv" />
      <Card sx={{ overflowX: 'auto' }}>
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell>Region</TableCell>
              <TableCell align="right">Farmers</TableCell>
              <TableCell align="right">Total litres</TableCell>
              <TableCell align="right">Avg rate</TableCell>
              <TableCell align="right">Amount</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {q.data?.map((r) => (
              <TableRow key={String(r.regionCode)}>
                <TableCell>
                  {String(r.regionCode)} · {String(r.regionName)}
                </TableCell>
                <TableCell align="right">{r.producerCount}</TableCell>
                <TableCell align="right">{litres(r.totalLitres as number)}</TableCell>
                <TableCell align="right">₹{Number(r.averageRate).toFixed(2)}</TableCell>
                <TableCell align="right">{money(r.totalAmount as number)}</TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </Card>
    </>
  );
}

function ProducerStatement({ from, to }: { from: string; to: string }) {
  const [producer, setProducer] = useState<Producer | null>(null);
  const q = useQuery({
    queryKey: ['report', 'statement', producer?.id, from, to],
    queryFn: async () =>
      (await api.get<Record<string, number>>('/reports/producer-statement', {
        params: { producerId: producer!.id, from, to },
      })).data,
    enabled: !!producer,
  });
  return (
    <Stack spacing={2}>
      <ProducerPicker value={producer} onChange={setProducer} />
      {q.data && (
        <Card>
          <Table size="small">
            <TableBody>
              {[
                ['Total litres', litres(q.data.totalLitres)],
                ['Milk value', money(q.data.totalMilkAmount)],
                ['Incentives', money(q.data.totalIncentive)],
                ['Collections', String(q.data.collectionCount)],
                ['Deductions', money(q.data.totalDeductions)],
                ['Advance taken', money(q.data.totalAdvance)],
                ['Advance recovered', money(q.data.advanceRecovered)],
                ['Advance outstanding', money(q.data.advanceOutstanding)],
                ['Total paid', money(q.data.totalPaid)],
              ].map(([k, v]) => (
                <TableRow key={k}>
                  <TableCell sx={{ color: 'text.secondary' }}>{k}</TableCell>
                  <TableCell align="right">{v}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </Card>
      )}
    </Stack>
  );
}
