import { useMemo, useState } from 'react';
import { Link as RouterLink } from 'react-router-dom';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import MenuItem from '@mui/material/MenuItem';
import Stack from '@mui/material/Stack';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableContainer from '@mui/material/TableContainer';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import TextField from '@mui/material/TextField';
import { useQuery } from '@tanstack/react-query';
import { api } from '../../lib/api';
import { money } from '../../lib/format';
import { useProducerLookup } from '../../api/common';
import { PageHeader } from '../../components/PageHeader';
import { StatusChip } from '../../components/StatusChip';
import { EmptyState, TableSkeleton } from '../../components/ui';
import type { Payout } from '../../api/types';

interface SalaryPaymentRow {
  itemId: number;
  batchNumber?: string;
  periodMonth?: string;
  driverCode: string;
  driverName: string;
  netSalary: number;
  paymentStatus: string;
  paymentMethod?: string;
  paymentReference?: string;
  paidAt?: string;
}

interface Row {
  key: string;
  type: 'FARMER' | 'DRIVER';
  reference: string;
  recipient: string;
  period: string;
  amount: number;
  method: string;
  status: string;
  paidAt?: string;
  txnRef: string;
  settlementId?: number;
}

export function PaymentHistoryPage() {
  const producers = useProducerLookup();
  const [type, setType] = useState('');
  const [status, setStatus] = useState('');
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [q, setQ] = useState('');

  const payouts = useQuery({
    queryKey: ['payouts', 'all'],
    queryFn: async () => (await api.get<Payout[]>('/payouts')).data,
  });
  const salary = useQuery({
    queryKey: ['salary', 'payments'],
    queryFn: async () => (await api.get<SalaryPaymentRow[]>('/salary-batches/payments')).data,
  });

  const rows = useMemo<Row[]>(() => {
    const farmer: Row[] = (payouts.data ?? []).map((p) => ({
      key: `f-${p.id}`,
      type: 'FARMER',
      reference: p.providerPayoutId ?? `PO-${p.id}`,
      recipient: producers.label(p.producerId),
      period: `settlement #${p.settlementId}`,
      amount: Number(p.amount),
      method: p.provider === 'MANUAL' ? 'BANK_TRANSFER' : p.provider,
      status: p.status,
      paidAt: p.settledAt,
      txnRef: p.utr ?? '—',
      settlementId: p.settlementId,
    }));
    const driver: Row[] = (salary.data ?? []).map((s) => ({
      key: `d-${s.itemId}`,
      type: 'DRIVER',
      reference: s.batchNumber ?? `SAL-${s.itemId}`,
      recipient: `${s.driverName} (${s.driverCode})`,
      period: s.periodMonth ? s.periodMonth.slice(0, 7) : '—',
      amount: Number(s.netSalary),
      method: s.paymentMethod ?? '—',
      status: s.paymentStatus,
      paidAt: s.paidAt,
      txnRef: s.paymentReference ?? '—',
    }));
    return [...farmer, ...driver].sort((a, b) => (a.paidAt ?? '') < (b.paidAt ?? '') ? 1 : -1);
  }, [payouts.data, salary.data, producers]);

  const filtered = rows.filter((r) => {
    if (type && r.type !== type) return false;
    if (status && r.status !== status) return false;
    if (from && (!r.paidAt || r.paidAt.slice(0, 10) < from)) return false;
    if (to && (!r.paidAt || r.paidAt.slice(0, 10) > to)) return false;
    if (q) {
      const s = q.toLowerCase();
      if (![r.reference, r.recipient, r.txnRef].some((x) => x.toLowerCase().includes(s))) return false;
    }
    return true;
  });

  const loading = payouts.isLoading || salary.isLoading;

  return (
    <>
      <PageHeader title="Payment History" subtitle="Every farmer payout and driver-salary payment" />

      <Card sx={{ mb: 2 }}>
        <CardContent>
          <Stack direction={{ xs: 'column', md: 'row' }} spacing={1.5} flexWrap="wrap" useFlexGap>
            <TextField select size="small" label="Type" value={type} onChange={(e) => setType(e.target.value)} sx={{ minWidth: 130 }}>
              <MenuItem value="">All types</MenuItem>
              <MenuItem value="FARMER">Farmer</MenuItem>
              <MenuItem value="DRIVER">Driver salary</MenuItem>
            </TextField>
            <TextField select size="small" label="Status" value={status} onChange={(e) => setStatus(e.target.value)} sx={{ minWidth: 140 }}>
              <MenuItem value="">All statuses</MenuItem>
              {['PENDING', 'PROCESSING', 'INITIATED', 'SUCCESS', 'PAID', 'FAILED', 'ON_HOLD', 'CANCELLED'].map((s) => (
                <MenuItem key={s} value={s}>
                  {s}
                </MenuItem>
              ))}
            </TextField>
            <TextField type="date" size="small" label="From" value={from} onChange={(e) => setFrom(e.target.value)} InputLabelProps={{ shrink: true }} />
            <TextField type="date" size="small" label="To" value={to} onChange={(e) => setTo(e.target.value)} InputLabelProps={{ shrink: true }} />
            <TextField size="small" placeholder="Search reference / recipient" value={q} onChange={(e) => setQ(e.target.value)} sx={{ minWidth: 220, flex: 1 }} />
          </Stack>
        </CardContent>
      </Card>

      <Card>
        {loading ? (
          <TableSkeleton cols={8} />
        ) : filtered.length === 0 ? (
          <EmptyState title="No payments" description="Payments appear here once farmer payouts or driver salaries are processed." />
        ) : (
          <TableContainer sx={{ maxHeight: 'calc(100vh - 320px)' }}>
            <Table size="small" stickyHeader>
              <TableHead>
                <TableRow>
                  <TableCell>Reference</TableCell>
                  <TableCell>Type</TableCell>
                  <TableCell>Recipient</TableCell>
                  <TableCell>Period</TableCell>
                  <TableCell align="right">Amount</TableCell>
                  <TableCell>Method</TableCell>
                  <TableCell>Status</TableCell>
                  <TableCell>Txn ref</TableCell>
                  <TableCell align="right" />
                </TableRow>
              </TableHead>
              <TableBody>
                {filtered.map((r) => (
                  <TableRow key={r.key} hover>
                    <TableCell sx={{ fontWeight: 600 }}>{r.reference}</TableCell>
                    <TableCell>{r.type === 'FARMER' ? 'Farmer' : 'Driver'}</TableCell>
                    <TableCell>{r.recipient}</TableCell>
                    <TableCell>{r.period}</TableCell>
                    <TableCell align="right" sx={{ fontWeight: 700 }}>
                      {money(r.amount)}
                    </TableCell>
                    <TableCell>{r.method.replace(/_/g, ' ')}</TableCell>
                    <TableCell>
                      <StatusChip status={r.status} />
                    </TableCell>
                    <TableCell>{r.txnRef}</TableCell>
                    <TableCell align="right">
                      {r.settlementId ? (
                        <Button size="small" component={RouterLink} to={`/settlements/${r.settlementId}`}>
                          View
                        </Button>
                      ) : (
                        <Box />
                      )}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        )}
      </Card>
    </>
  );
}
