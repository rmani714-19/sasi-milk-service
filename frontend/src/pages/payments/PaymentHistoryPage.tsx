import { useEffect, useMemo, useState } from 'react';
import { Link as RouterLink } from 'react-router-dom';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Chip from '@mui/material/Chip';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogTitle from '@mui/material/DialogTitle';
import Divider from '@mui/material/Divider';
import FormControlLabel from '@mui/material/FormControlLabel';
import Grid from '@mui/material/Grid2';
import MenuItem from '@mui/material/MenuItem';
import Stack from '@mui/material/Stack';
import Switch from '@mui/material/Switch';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableContainer from '@mui/material/TableContainer';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import AddOutlinedIcon from '@mui/icons-material/AddOutlined';
import FlagOutlinedIcon from '@mui/icons-material/FlagOutlined';
import PrintOutlinedIcon from '@mui/icons-material/PrintOutlined';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../../lib/api';
import { money } from '../../lib/format';
import { useAuth } from '../../lib/auth';
import { useProducerLookup } from '../../api/common';
import { PageHeader } from '../../components/PageHeader';
import { StatusChip } from '../../components/StatusChip';
import { FormSection } from '../../components/FormSection';
import { EmptyState, TableSkeleton, useToast } from '../../components/ui';
import { PaymentReceipt } from '../../components/PaymentReceipt';
import type { CenterSettings, Expense, ExpenseCategory, Payout, PaymentFlag, PaymentFlagSubjectType } from '../../api/types';

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
  failureReason?: string;
}

interface Row {
  key: string;
  type: 'FARMER' | 'DRIVER' | 'EXPENSE';
  subjectType: PaymentFlagSubjectType;
  subjectId: number;
  reference: string;
  recipient: string;
  period: string;
  amount: number;
  method: string;
  status: string;
  paidAt?: string;
  txnRef: string;
  settlementId?: number;
  failureReason?: string;
}

const TYPE_LABEL: Record<Row['type'], string> = {
  FARMER: 'Farmer',
  DRIVER: 'Driver salary',
  EXPENSE: 'Expense',
};

const RECEIPT_KIND: Record<Row['type'], string> = {
  FARMER: 'Farmer Settlement Payout',
  DRIVER: 'Driver Salary',
  EXPENSE: 'Business Expense',
};

const CATEGORY_LABEL: Record<ExpenseCategory, string> = {
  FUEL: 'Fuel',
  MAINTENANCE: 'Maintenance',
  RENT: 'Rent',
  UTILITIES: 'Utilities',
  OFFICE_SUPPLIES: 'Office supplies',
  TRANSPORT: 'Transport',
  SALARY_OTHER: 'Other salary cost',
  MISC: 'Miscellaneous',
  OTHER: 'Other',
};

const SUCCESS_STATUSES = new Set(['PAID', 'SUCCESS']);

export function PaymentHistoryPage() {
  const producers = useProducerLookup();
  const { can } = useAuth();
  const canFlag = can('VERIFY_PAYMENT') || can('PROCESS_PAYMENT') || can('APPROVE_SETTLEMENT') || can('GENERATE_SETTLEMENT') || can('ALL');
  const canAddExpense = can('MANAGE_EXPENSES') || can('ALL');

  const [type, setType] = useState('');
  const [status, setStatus] = useState('');
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [q, setQ] = useState('');
  const [flaggedOnly, setFlaggedOnly] = useState(false);
  const [detailRow, setDetailRow] = useState<Row | null>(null);
  const [addExpenseOpen, setAddExpenseOpen] = useState(false);
  const [receipt, setReceipt] = useState<{ kindLabel: string; reference: string; recipient: string; period: string; amount: number; method: string; txnRef: string; status: string; paidAt?: string } | null>(null);

  const center = useQuery({
    queryKey: ['center'],
    queryFn: async () => (await api.get<CenterSettings>('/center')).data,
  });
  const payouts = useQuery({
    queryKey: ['payouts', 'all'],
    queryFn: async () => (await api.get<Payout[]>('/payouts')).data,
  });
  const salary = useQuery({
    queryKey: ['salary', 'payments'],
    queryFn: async () => (await api.get<SalaryPaymentRow[]>('/salary-batches/payments')).data,
  });
  const expenses = useQuery({
    queryKey: ['expenses'],
    queryFn: async () => (await api.get<Expense[]>('/expenses')).data,
  });
  const flags = useQuery({
    queryKey: ['payment-flags', 'open'],
    queryFn: async () => (await api.get<PaymentFlag[]>('/payment-flags')).data,
  });

  const flagMap = useMemo(() => {
    const m = new Map<string, PaymentFlag>();
    for (const f of flags.data ?? []) m.set(`${f.subjectType}:${f.subjectId}`, f);
    return m;
  }, [flags.data]);

  const rows = useMemo<Row[]>(() => {
    const farmer: Row[] = (payouts.data ?? []).map((p) => ({
      key: `f-${p.id}`,
      type: 'FARMER',
      subjectType: 'PAYOUT',
      subjectId: p.id,
      reference: p.providerPayoutId ?? `PO-${p.id}`,
      recipient: producers.label(p.producerId),
      period: `settlement #${p.settlementId}`,
      amount: Number(p.amount),
      method: p.provider === 'MANUAL' ? 'BANK_TRANSFER' : p.provider,
      status: p.status,
      paidAt: p.settledAt,
      txnRef: p.utr ?? '—',
      settlementId: p.settlementId,
      failureReason: p.failureReason,
    }));
    const driver: Row[] = (salary.data ?? []).map((s) => ({
      key: `d-${s.itemId}`,
      type: 'DRIVER',
      subjectType: 'SALARY_ITEM',
      subjectId: s.itemId,
      reference: s.batchNumber ?? `SAL-${s.itemId}`,
      recipient: `${s.driverName} (${s.driverCode})`,
      period: s.periodMonth ? s.periodMonth.slice(0, 7) : '—',
      amount: Number(s.netSalary),
      method: s.paymentMethod ?? '—',
      status: s.paymentStatus,
      paidAt: s.paidAt,
      txnRef: s.paymentReference ?? '—',
      failureReason: s.failureReason,
    }));
    const expense: Row[] = (expenses.data ?? []).map((e) => ({
      key: `e-${e.id}`,
      type: 'EXPENSE',
      subjectType: 'EXPENSE',
      subjectId: e.id,
      reference: e.expenseNumber,
      recipient: CATEGORY_LABEL[e.category] ?? e.category,
      period: e.expenseDate,
      amount: Number(e.amount),
      method: e.paymentMethod,
      status: e.status,
      paidAt: e.paidAt,
      txnRef: e.paymentReference ?? '—',
    }));
    return [...farmer, ...driver, ...expense].sort((a, b) => ((a.paidAt ?? '') < (b.paidAt ?? '') ? 1 : -1));
  }, [payouts.data, salary.data, expenses.data, producers]);

  const filtered = rows.filter((r) => {
    if (type && r.type !== type) return false;
    if (status && r.status !== status) return false;
    if (from && (!r.paidAt || r.paidAt.slice(0, 10) < from)) return false;
    if (to && (!r.paidAt || r.paidAt.slice(0, 10) > to)) return false;
    if (flaggedOnly && !flagMap.has(`${r.subjectType}:${r.subjectId}`)) return false;
    if (q) {
      const s = q.toLowerCase();
      if (![r.reference, r.recipient, r.txnRef].some((x) => x.toLowerCase().includes(s))) return false;
    }
    return true;
  });

  const thisMonth = new Date().toISOString().slice(0, 7);
  const paidThisMonth = rows
    .filter((r) => SUCCESS_STATUSES.has(r.status) && r.paidAt?.slice(0, 7) === thisMonth)
    .reduce((sum, r) => sum + r.amount, 0);
  const failedCount = rows.filter((r) => r.status === 'FAILED').length;
  const flaggedCount = flags.data?.length ?? 0;

  const loading = payouts.isLoading || salary.isLoading || expenses.isLoading;

  const printReceipt = (r: Row) => {
    setReceipt({
      kindLabel: RECEIPT_KIND[r.type],
      reference: r.reference,
      recipient: r.recipient,
      period: r.period,
      amount: r.amount,
      method: r.method,
      txnRef: r.txnRef,
      status: r.status,
      paidAt: r.paidAt,
    });
    requestAnimationFrame(() => window.print());
  };

  return (
    <>
      <PageHeader
        title="Payment Activity Center"
        subtitle="Every farmer payout, driver salary and business expense — verify, flag, and keep a record of each"
      />

      <Grid container spacing={2} sx={{ mb: 3 }}>
        <SummaryCard label="Paid this month" value={money(paidThisMonth)} hint={new Date().toLocaleString('en-IN', { month: 'long', year: 'numeric' })} />
        <SummaryCard label="Failed" value={String(failedCount)} hint={failedCount ? 'needs attention' : 'none'} tone={failedCount ? 'error' : undefined} />
        <SummaryCard label="Flagged" value={String(flaggedCount)} hint={flaggedCount ? 'open queries' : 'none'} tone={flaggedCount ? 'error' : undefined} />
        <SummaryCard label="Total logged" value={String(rows.length)} hint="all activity types" />
      </Grid>

      <Card sx={{ mb: 2 }}>
        <CardContent>
          <Stack direction="row" spacing={1.5} alignItems="center" flexWrap="wrap" useFlexGap>
            <TextField
              select
              size="small"
              label="Type"
              value={type}
              onChange={(e) => setType(e.target.value)}
              sx={{ width: 160, flexShrink: 0 }}
            >
              <MenuItem value="">All types</MenuItem>
              <MenuItem value="FARMER">Farmer</MenuItem>
              <MenuItem value="DRIVER">Driver salary</MenuItem>
              <MenuItem value="EXPENSE">Expense</MenuItem>
            </TextField>
            <TextField
              select
              size="small"
              label="Status"
              value={status}
              onChange={(e) => setStatus(e.target.value)}
              sx={{ width: 160, flexShrink: 0 }}
            >
              <MenuItem value="">All statuses</MenuItem>
              {['PENDING', 'PROCESSING', 'INITIATED', 'SUCCESS', 'PAID', 'FAILED', 'ON_HOLD', 'CANCELLED'].map((s) => (
                <MenuItem key={s} value={s}>
                  {s}
                </MenuItem>
              ))}
            </TextField>
            <TextField
              type="date"
              size="small"
              label="From"
              value={from}
              onChange={(e) => setFrom(e.target.value)}
              InputLabelProps={{ shrink: true }}
              sx={{ width: 170, flexShrink: 0 }}
            />
            <TextField
              type="date"
              size="small"
              label="To"
              value={to}
              onChange={(e) => setTo(e.target.value)}
              InputLabelProps={{ shrink: true }}
              sx={{ width: 170, flexShrink: 0 }}
            />
            <TextField
              size="small"
              placeholder="Search reference / recipient"
              value={q}
              onChange={(e) => setQ(e.target.value)}
              sx={{ minWidth: 220, flex: '1 1 220px' }}
            />
            <FormControlLabel
              sx={{ flexShrink: 0, ml: 0 }}
              control={<Switch size="small" checked={flaggedOnly} onChange={(e) => setFlaggedOnly(e.target.checked)} />}
              label="Flagged only"
            />
            {canAddExpense && (
              <Button
                variant="contained"
                size="small"
                startIcon={<AddOutlinedIcon />}
                onClick={() => setAddExpenseOpen(true)}
                sx={{ flexShrink: 0, whiteSpace: 'nowrap' }}
              >
                Add expense
              </Button>
            )}
          </Stack>
        </CardContent>
      </Card>

      <Card>
        {loading ? (
          <TableSkeleton cols={8} />
        ) : filtered.length === 0 ? (
          <EmptyState title="No payments" description="Payments appear here once farmer payouts, driver salaries or expenses are recorded." />
        ) : (
          <TableContainer sx={{ maxHeight: 'calc(100vh - 380px)' }}>
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
                </TableRow>
              </TableHead>
              <TableBody>
                {filtered.map((r) => {
                  const flag = flagMap.get(`${r.subjectType}:${r.subjectId}`);
                  return (
                    <TableRow key={r.key} hover sx={{ cursor: 'pointer' }} onClick={() => setDetailRow(r)}>
                      <TableCell sx={{ fontWeight: 600 }}>{r.reference}</TableCell>
                      <TableCell>{TYPE_LABEL[r.type]}</TableCell>
                      <TableCell>{r.recipient}</TableCell>
                      <TableCell>{r.period}</TableCell>
                      <TableCell align="right" sx={{ fontWeight: 700 }}>
                        {money(r.amount)}
                      </TableCell>
                      <TableCell>{r.method.replace(/_/g, ' ')}</TableCell>
                      <TableCell>
                        <Stack direction="row" spacing={0.5} alignItems="center">
                          <StatusChip status={r.status} />
                          {flag && <Chip size="small" icon={<FlagOutlinedIcon sx={{ fontSize: 14 }} />} label="Flagged" color="error" variant="outlined" />}
                        </Stack>
                      </TableCell>
                      <TableCell>{r.txnRef}</TableCell>
                    </TableRow>
                  );
                })}
              </TableBody>
            </Table>
          </TableContainer>
        )}
      </Card>

      <TransactionDetailDialog
        row={detailRow}
        flag={detailRow ? flagMap.get(`${detailRow.subjectType}:${detailRow.subjectId}`) : undefined}
        canFlag={canFlag}
        onClose={() => setDetailRow(null)}
        onPrint={() => detailRow && printReceipt(detailRow)}
      />

      <AddExpenseDialog open={addExpenseOpen} onClose={() => setAddExpenseOpen(false)} />

      <PaymentReceipt data={receipt} centerName={center.data?.centerName ?? 'SASI MILK SERVICE'} centerAddress={center.data?.address} />
    </>
  );
}

function SummaryCard({ label, value, hint, tone }: { label: string; value: string; hint?: string; tone?: 'error' }) {
  return (
    <Grid size={{ xs: 6, sm: 3 }}>
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

/* -------------------------------------------------------- transaction detail + flag */

function TransactionDetailDialog({
  row,
  flag,
  canFlag,
  onClose,
  onPrint,
}: {
  row: Row | null;
  flag: PaymentFlag | undefined;
  canFlag: boolean;
  onClose: () => void;
  onPrint: () => void;
}) {
  const toast = useToast();
  const qc = useQueryClient();
  const [note, setNote] = useState('');
  const [resolutionNote, setResolutionNote] = useState('');

  const history = useQuery({
    queryKey: ['payment-flags', 'history', row?.subjectType, row?.subjectId],
    queryFn: async () =>
      (await api.get<PaymentFlag[]>('/payment-flags/history', { params: { subjectType: row!.subjectType, subjectId: row!.subjectId } })).data,
    enabled: !!row,
  });

  useEffect(() => {
    setNote('');
    setResolutionNote('');
  }, [row]);

  const raise = useMutation({
    mutationFn: async () =>
      (await api.post('/payment-flags', { subjectType: row!.subjectType, subjectId: row!.subjectId, note })).data,
    onSuccess: () => {
      toast.success('Transaction flagged for review');
      setNote('');
      qc.invalidateQueries({ queryKey: ['payment-flags'] });
    },
    onError: toast.fromError,
  });

  const resolve = useMutation({
    mutationFn: async () => (await api.post(`/payment-flags/${flag!.id}/resolve`, { resolutionNote })).data,
    onSuccess: () => {
      toast.success('Flag resolved');
      setResolutionNote('');
      qc.invalidateQueries({ queryKey: ['payment-flags'] });
    },
    onError: toast.fromError,
  });

  if (!row) return null;
  const canPrint = SUCCESS_STATUSES.has(row.status);
  const resolvedFlags = (history.data ?? []).filter((f) => f.status === 'RESOLVED');

  return (
    <Dialog open={!!row} onClose={onClose} maxWidth="sm" fullWidth>
      <DialogTitle>
        <Stack direction="row" spacing={1} alignItems="center">
          <span>{row.reference}</span>
          <StatusChip status={row.status} />
        </Stack>
      </DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 0.5 }}>
          <Grid container spacing={1.5}>
            <Detail label="Type" value={TYPE_LABEL[row.type]} />
            <Detail label="Recipient / Category" value={row.recipient} />
            <Detail label="Period / Date" value={row.period} />
            <Detail label="Amount" value={money(row.amount)} bold />
            <Detail label="Method" value={row.method.replace(/_/g, ' ')} />
            <Detail label="Txn / UTR ref" value={row.txnRef} />
            {row.paidAt && <Detail label="Paid at" value={new Date(row.paidAt).toLocaleString('en-IN')} />}
          </Grid>

          {row.failureReason && <Alert severity="error">{row.failureReason}</Alert>}

          {row.settlementId && (
            <Button component={RouterLink} to={`/settlements/${row.settlementId}`} size="small" sx={{ alignSelf: 'flex-start' }}>
              Open full settlement →
            </Button>
          )}

          <Divider />

          <FormSection title="Flag / query" hint="Raise a query if something looks wrong with this transaction (wrong UTR, producer complaint, etc.) — resolve it once sorted out.">
            {flag ? (
              <Alert severity="warning">
                <Typography variant="body2" sx={{ fontWeight: 600 }}>
                  {flag.note}
                </Typography>
                <Typography variant="caption" color="text.secondary" display="block" sx={{ mt: 0.5 }}>
                  Flagged {new Date(flag.flaggedAt).toLocaleString('en-IN')}
                </Typography>
                {canFlag && (
                  <Stack spacing={1} sx={{ mt: 1.5 }}>
                    <TextField
                      size="small"
                      label="Resolution note (optional)"
                      value={resolutionNote}
                      onChange={(e) => setResolutionNote(e.target.value)}
                    />
                    <Button
                      size="small"
                      variant="contained"
                      color="warning"
                      disabled={resolve.isPending}
                      onClick={() => resolve.mutate()}
                      sx={{ alignSelf: 'flex-start' }}
                    >
                      {resolve.isPending ? 'Resolving…' : 'Mark resolved'}
                    </Button>
                  </Stack>
                )}
              </Alert>
            ) : canFlag ? (
              <Stack direction="row" spacing={1}>
                <TextField
                  size="small"
                  fullWidth
                  placeholder="What's wrong? (e.g. wrong UTR, producer says not received)"
                  value={note}
                  onChange={(e) => setNote(e.target.value)}
                />
                <Button
                  variant="outlined"
                  color="warning"
                  startIcon={<FlagOutlinedIcon />}
                  disabled={!note.trim() || raise.isPending}
                  onClick={() => raise.mutate()}
                  sx={{ flexShrink: 0, whiteSpace: 'nowrap' }}
                >
                  Flag
                </Button>
              </Stack>
            ) : (
              <Typography variant="body2" color="text.secondary">
                No open query on this transaction.
              </Typography>
            )}

            {resolvedFlags.length > 0 && (
              <Stack spacing={0.5} sx={{ mt: 1 }}>
                <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 600 }}>
                  Past queries
                </Typography>
                {resolvedFlags.map((f) => (
                  <Typography key={f.id} variant="caption" color="text.secondary">
                    "{f.note}" — resolved {f.resolvedAt ? new Date(f.resolvedAt).toLocaleDateString('en-IN') : ''}
                    {f.resolutionNote ? ` (${f.resolutionNote})` : ''}
                  </Typography>
                ))}
              </Stack>
            )}
          </FormSection>
        </Stack>
      </DialogContent>
      <DialogActions>
        {canPrint && (
          <Button startIcon={<PrintOutlinedIcon />} onClick={onPrint}>
            Print receipt
          </Button>
        )}
        <Box sx={{ flex: 1 }} />
        <Button onClick={onClose}>Close</Button>
      </DialogActions>
    </Dialog>
  );
}

function Detail({ label, value, bold }: { label: string; value: string; bold?: boolean }) {
  return (
    <Grid size={6}>
      <Typography variant="caption" color="text.secondary" display="block">
        {label}
      </Typography>
      <Typography variant="body2" sx={{ fontWeight: bold ? 700 : 500 }}>
        {value}
      </Typography>
    </Grid>
  );
}

/* -------------------------------------------------------- add expense */

const CATEGORIES: ExpenseCategory[] = ['FUEL', 'MAINTENANCE', 'RENT', 'UTILITIES', 'OFFICE_SUPPLIES', 'TRANSPORT', 'SALARY_OTHER', 'MISC', 'OTHER'];

function AddExpenseDialog({ open, onClose }: { open: boolean; onClose: () => void }) {
  const toast = useToast();
  const qc = useQueryClient();
  const today = new Date().toISOString().slice(0, 10);

  const [category, setCategory] = useState<ExpenseCategory>('MISC');
  const [description, setDescription] = useState('');
  const [amount, setAmount] = useState('');
  const [expenseDate, setExpenseDate] = useState(today);
  const [paymentMethod, setPaymentMethod] = useState('BANK_TRANSFER');
  const [paymentReference, setPaymentReference] = useState('');
  const [tried, setTried] = useState(false);

  useEffect(() => {
    if (open) {
      setCategory('MISC');
      setDescription('');
      setAmount('');
      setExpenseDate(today);
      setPaymentMethod('BANK_TRANSFER');
      setPaymentReference('');
      setTried(false);
    }
  }, [open, today]);

  const create = useMutation({
    mutationFn: async () =>
      (
        await api.post('/expenses', {
          category,
          description: description.trim() || undefined,
          amount: Number(amount),
          expenseDate,
          paymentMethod,
          paymentReference: paymentReference.trim() || undefined,
        })
      ).data,
    onSuccess: () => {
      toast.success('Expense recorded');
      qc.invalidateQueries({ queryKey: ['expenses'] });
      onClose();
    },
    onError: toast.fromError,
  });

  const amountNum = Number(amount);
  const valid = amount !== '' && amountNum > 0 && !!expenseDate;

  return (
    <Dialog open={open} onClose={onClose} maxWidth="xs" fullWidth>
      <DialogTitle>Add expense</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          <TextField select label="Category" value={category} onChange={(e) => setCategory(e.target.value as ExpenseCategory)}>
            {CATEGORIES.map((c) => (
              <MenuItem key={c} value={c}>
                {CATEGORY_LABEL[c]}
              </MenuItem>
            ))}
          </TextField>
          <TextField
            label="Amount"
            type="number"
            value={amount}
            onChange={(e) => setAmount(e.target.value)}
            error={tried && !valid}
            helperText={tried && !valid ? 'Enter an amount greater than zero' : undefined}
            inputProps={{ min: 0, step: '0.01', inputMode: 'decimal' }}
          />
          <TextField
            type="date"
            label="Expense date"
            value={expenseDate}
            onChange={(e) => setExpenseDate(e.target.value)}
            InputLabelProps={{ shrink: true }}
          />
          <TextField select label="Payment method" value={paymentMethod} onChange={(e) => setPaymentMethod(e.target.value)}>
            {['CASH', 'BANK_TRANSFER', 'UPI', 'CHEQUE', 'OTHER'].map((m) => (
              <MenuItem key={m} value={m}>
                {m.replace(/_/g, ' ')}
              </MenuItem>
            ))}
          </TextField>
          <TextField
            label="Reference (optional)"
            placeholder="UTR / cheque no. / txn id"
            value={paymentReference}
            onChange={(e) => setPaymentReference(e.target.value)}
          />
          <TextField
            label="Description (optional)"
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            multiline
            minRows={2}
          />
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button
          variant="contained"
          disabled={create.isPending}
          onClick={() => {
            setTried(true);
            if (valid) create.mutate();
          }}
        >
          {create.isPending ? 'Saving…' : 'Save expense'}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
