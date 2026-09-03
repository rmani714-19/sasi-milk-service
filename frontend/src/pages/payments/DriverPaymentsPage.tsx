import { useEffect, useState, type ReactNode } from 'react';
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
import Grid from '@mui/material/Grid2';
import MenuItem from '@mui/material/MenuItem';
import Stack from '@mui/material/Stack';
import Step from '@mui/material/Step';
import StepLabel from '@mui/material/StepLabel';
import Stepper from '@mui/material/Stepper';
import Tab from '@mui/material/Tab';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableContainer from '@mui/material/TableContainer';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import Tabs from '@mui/material/Tabs';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import AddIcon from '@mui/icons-material/Add';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../../lib/api';
import { money } from '../../lib/format';
import * as V from '../../lib/validate';
import { useAuth } from '../../lib/auth';
import { PageHeader } from '../../components/PageHeader';
import { FormSection } from '../../components/FormSection';
import { StatusChip } from '../../components/StatusChip';
import { BankBalanceCard, OwnerApprovalDialog, PreValidationCard } from '../../components/PaymentGates';
import { ConfirmDialog, EmptyState, QueryBoundary, SectionTitle, TableSkeleton, useToast } from '../../components/ui';
import type {
  BankBalanceCheck,
  Driver,
  DriverBankDetails,
  PreValidationReport,
  SalaryBatchDetail,
  SalaryBatchSummary,
  SalaryItem,
  VehicleStatus,
} from '../../api/types';

const LIFECYCLE = ['Draft', 'Calculated', 'Reviewed', 'Bank balance', 'Pre-validation', 'Approved', 'Paid'] as const;
function stepIndex(s: string, balanceOk = false, preValReady = false) {
  switch (s) {
    case 'DRAFT':
      return 0;
    case 'CALCULATED':
      return 1;
    case 'REVIEWED':
      return 2 + (balanceOk ? 1 : 0) + (balanceOk && preValReady ? 1 : 0);
    case 'APPROVED':
    case 'PAYMENT_INITIATED':
    case 'PARTIALLY_PAID':
      return 5;
    case 'PAID':
      return 6;
    default:
      return 0;
  }
}
const monthKey = () => new Date().toISOString().slice(0, 7);

export function DriverPaymentsPage() {
  const [tab, setTab] = useState(0);
  return (
    <>
      <PageHeader title="Driver Salary" subtitle="Monthly driver salary — Calculate → Review → Approve → Pay → Confirm" />
      <Tabs value={tab} onChange={(_, v) => setTab(v)} sx={{ mb: 2 }}>
        <Tab label="Salary Runs" />
        <Tab label="Drivers" />
      </Tabs>
      {tab === 0 ? <SalaryRuns /> : <DriversTab />}
    </>
  );
}

/* ------------------------------------------------------------------ Salary runs */

function SalaryRuns() {
  const toast = useToast();
  const qc = useQueryClient();
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [month, setMonth] = useState(monthKey());
  const [editItem, setEditItem] = useState<SalaryItem | null>(null);
  const [payItem, setPayItem] = useState<SalaryItem | null>(null);
  const [confirmCancel, setConfirmCancel] = useState(false);
  const [balanceCheck, setBalanceCheck] = useState<BankBalanceCheck | null>(null);
  const [preVal, setPreVal] = useState<PreValidationReport | null>(null);
  const [approveOpen, setApproveOpen] = useState(false);

  const batches = useQuery({
    queryKey: ['salary-batches'],
    queryFn: async () => (await api.get<SalaryBatchSummary[]>('/salary-batches')).data,
  });
  useEffect(() => {
    if (selectedId == null && batches.data && batches.data.length) setSelectedId(batches.data[0].id);
  }, [batches.data, selectedId]);

  const detail = useQuery({
    queryKey: ['salary-batch', selectedId],
    queryFn: async () => (await api.get<SalaryBatchDetail>(`/salary-batches/${selectedId}`)).data,
    enabled: selectedId != null,
  });
  const driverList = useQuery({
    queryKey: ['drivers'],
    queryFn: async () => (await api.get<Driver[]>('/drivers')).data,
    staleTime: 60_000,
  });

  const invalidate = () => {
    qc.invalidateQueries({ queryKey: ['salary-batches'] });
    qc.invalidateQueries({ queryKey: ['salary-batch'] });
    qc.invalidateQueries({ queryKey: ['salary', 'payments'] });
  };

  const create = useMutation({
    mutationFn: async () => (await api.post<SalaryBatchDetail>('/salary-batches', { month: `${month}-01` })).data,
    onSuccess: (d) => {
      toast.success('Salary batch created');
      invalidate();
      setSelectedId(d.id);
    },
    onError: toast.fromError,
  });
  const lifecycle = useMutation({
    mutationFn: async ({ path }: { path: string; ok: string }) =>
      (await api.post(`/salary-batches/${selectedId}/${path}`)).data,
    onSuccess: (_d, v) => {
      toast.success(v.ok);
      setConfirmCancel(false);
      invalidate();
    },
    onError: toast.fromError,
  });
  const saveItem = useMutation({
    mutationFn: async ({ id, body }: { id: number; body: Record<string, unknown> }) =>
      (await api.put(`/salary-batches/items/${id}`, body)).data,
    onSuccess: () => {
      toast.success('Salary line updated');
      invalidate();
      setEditItem(null);
    },
    onError: toast.fromError,
  });
  const doPay = useMutation({
    mutationFn: async ({ id, body }: { id: number; body: Record<string, unknown> }) =>
      (await api.post(`/salary-batches/items/${id}/pay`, body)).data,
    onSuccess: () => {
      toast.success('Salary paid');
      invalidate();
      setPayItem(null);
    },
    onError: toast.fromError,
  });
  const retry = useMutation({
    mutationFn: async (id: number) => (await api.post(`/salary-batches/items/${id}/retry`)).data,
    onSuccess: () => {
      toast.success('Retry queued');
      invalidate();
    },
    onError: toast.fromError,
  });
  const approveMut = useMutation({
    mutationFn: async () => (await api.post(`/salary-batches/${selectedId}/approve`, { confirmed: true })).data,
    onSuccess: () => {
      toast.success('Salary batch approved');
      setApproveOpen(false);
      invalidate();
    },
    onError: toast.fromError,
  });

  const d = detail.data;
  const editable = d && ['DRAFT', 'CALCULATED', 'REVIEWED'].includes(d.status);
  const payable = d && ['APPROVED', 'PAYMENT_INITIATED', 'PARTIALLY_PAID'].includes(d.status);
  const pending = d ? d.totalNet - d.totalPaid : 0;

  return (
    <>
      <Card sx={{ mb: 2 }}>
        <CardContent>
          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} alignItems={{ sm: 'center' }}>
            <TextField
              select
              size="small"
              label="Batch"
              value={selectedId ?? ''}
              onChange={(e) => setSelectedId(Number(e.target.value))}
              sx={{ minWidth: 240 }}
            >
              {(batches.data ?? []).map((b) => (
                <MenuItem key={b.id} value={b.id}>
                  {b.batchNumber} · {b.periodMonth.slice(0, 7)} · {b.status.replace(/_/g, ' ')}
                </MenuItem>
              ))}
              {(batches.data ?? []).length === 0 && <MenuItem value="">No batches yet</MenuItem>}
            </TextField>
            <Box sx={{ flex: 1 }} />
            <TextField type="month" size="small" label="New month" value={month} onChange={(e) => setMonth(e.target.value)} InputLabelProps={{ shrink: true }} />
            <Button variant="contained" startIcon={<AddIcon />} disabled={create.isPending} onClick={() => create.mutate()}>
              Create run
            </Button>
          </Stack>
        </CardContent>
      </Card>

      {selectedId == null ? (
        <Card>
          <EmptyState title="No salary run selected" description="Create a run for a month to calculate driver salaries." />
        </Card>
      ) : (
        <QueryBoundary query={detail} skeleton={<TableSkeleton cols={8} />}>
          {(b) => {
            const balanceOk = !!balanceCheck?.sufficient && Number(balanceCheck.requiredAmount) === Number(b.totalNet);
            const preValReady = preVal?.result === 'READY_FOR_APPROVAL';
            const showGates = ['REVIEWED', 'APPROVED', 'PAYMENT_INITIATED', 'PARTIALLY_PAID', 'PAID', 'FAILED'].includes(b.status);
            return (
            <>
              <Card sx={{ mb: 2 }}>
                <CardContent>
                  <Stack direction="row" justifyContent="space-between" alignItems="center" flexWrap="wrap" gap={1}>
                    <Typography variant="h6">
                      {b.batchNumber}{' '}
                      <Typography component="span" variant="body2" color="text.secondary">
                        · {b.periodMonth.slice(0, 7)}
                      </Typography>
                    </Typography>
                    <StatusChip status={b.status} />
                  </Stack>
                  <Stepper activeStep={stepIndex(b.status, balanceOk, preValReady)} alternativeLabel sx={{ mt: 2 }}>
                    {LIFECYCLE.map((s) => (
                      <Step key={s}>
                        <StepLabel error={b.status === 'CANCELLED' || b.status === 'FAILED'}>{s}</StepLabel>
                      </Step>
                    ))}
                  </Stepper>
                  <Stack direction="row" spacing={1} sx={{ mt: 2, flexWrap: 'wrap', gap: 1 }}>
                    {['DRAFT', 'CALCULATED', 'REVIEWED'].includes(b.status) && (
                      <Button variant="contained" disabled={lifecycle.isPending} onClick={() => lifecycle.mutate({ path: 'calculate', ok: 'Recalculated' })}>
                        {b.status === 'DRAFT' ? 'Calculate' : 'Recalculate'}
                      </Button>
                    )}
                    {b.status === 'CALCULATED' && (
                      <Button variant="outlined" disabled={lifecycle.isPending} onClick={() => lifecycle.mutate({ path: 'review', ok: 'Marked reviewed' })}>
                        Mark reviewed
                      </Button>
                    )}
                    {b.status === 'REVIEWED' && (
                      <Button
                        variant="contained"
                        color="success"
                        disabled={lifecycle.isPending || !(balanceOk && preValReady)}
                        onClick={() => setApproveOpen(true)}
                      >
                        Approve
                      </Button>
                    )}
                    {!['PAID', 'CANCELLED'].includes(b.status) && (
                      <Button color="error" variant="outlined" disabled={lifecycle.isPending} onClick={() => setConfirmCancel(true)}>
                        Cancel
                      </Button>
                    )}
                  </Stack>
                </CardContent>
              </Card>

              {showGates && (
                <>
                  <SectionTitle>Bank balance &amp; pre-validation</SectionTitle>
                  <BankBalanceCard
                    base={`/salary-batches/${b.id}`}
                    requiredAmount={b.totalNet}
                    canVerify
                    onChange={setBalanceCheck}
                  />
                  <PreValidationCard base={`/salary-batches/${b.id}`} onResult={setPreVal} />
                </>
              )}

              <Grid container spacing={2} sx={{ mb: 2 }}>
                {[
                  ['Drivers', String(b.totalDrivers)],
                  ['Gross', money(b.totalGross)],
                  ['Deductions', money(b.totalDeductions)],
                  ['Net salary', money(b.totalNet)],
                  ['Paid', money(b.totalPaid)],
                  ['Pending', money(pending)],
                ].map(([k, v]) => (
                  <Grid size={{ xs: 6, sm: 4, md: 2 }} key={k}>
                    <Card sx={{ height: '100%' }}>
                      <CardContent sx={{ py: 1.25 }}>
                        <Typography variant="caption" color="text.secondary">
                          {k}
                        </Typography>
                        <Typography sx={{ fontSize: 17, fontWeight: 700 }}>{v}</Typography>
                      </CardContent>
                    </Card>
                  </Grid>
                ))}
              </Grid>

              <Card>
                {b.items.length === 0 ? (
                  <EmptyState title="Not calculated yet" description="Press Calculate to load active drivers and their salary components." />
                ) : (
                  <TableContainer sx={{ maxHeight: 'calc(100vh - 420px)' }}>
                    <Table size="small" stickyHeader>
                      <TableHead>
                        <TableRow>
                          <TableCell>Driver</TableCell>
                          <TableCell align="right">Basic</TableCell>
                          <TableCell align="right">Days</TableCell>
                          <TableCell align="right">Allow.</TableCell>
                          <TableCell align="right">OT</TableCell>
                          <TableCell align="right">Gross</TableCell>
                          <TableCell align="right">Deduct.</TableCell>
                          <TableCell align="right">Net</TableCell>
                          <TableCell>Status</TableCell>
                          <TableCell align="right">Action</TableCell>
                        </TableRow>
                      </TableHead>
                      <TableBody>
                        {b.items.map((it) => (
                          <TableRow key={it.id} hover>
                            <TableCell>
                              <DriverName id={it.driverId} />
                              <Typography variant="caption" color="text.secondary" display="block">
                                {it.salaryType}
                              </Typography>
                            </TableCell>
                            <TableCell align="right">{money(it.basicSalary)}</TableCell>
                            <TableCell align="right">{Number(it.workingDays)}</TableCell>
                            <TableCell align="right">{money(it.allowances)}</TableCell>
                            <TableCell align="right">{money(it.overtime)}</TableCell>
                            <TableCell align="right">{money(it.grossAmount)}</TableCell>
                            <TableCell align="right">{money(it.totalDeduction)}</TableCell>
                            <TableCell align="right" sx={{ fontWeight: 700 }}>
                              {money(it.netSalary)}
                            </TableCell>
                            <TableCell>
                              <StatusChip status={it.paymentStatus} />
                            </TableCell>
                            <TableCell align="right">
                              <Stack direction="row" spacing={0.5} justifyContent="flex-end">
                                {editable && it.paymentStatus === 'PENDING' && (
                                  <Button size="small" onClick={() => setEditItem(it)}>
                                    Edit
                                  </Button>
                                )}
                                {payable && (it.paymentStatus === 'PENDING' || it.paymentStatus === 'FAILED') && (
                                  <Button size="small" variant="contained" onClick={() => setPayItem(it)}>
                                    Pay
                                  </Button>
                                )}
                                {it.paymentStatus === 'FAILED' && (
                                  <Button size="small" onClick={() => retry.mutate(it.id)}>
                                    Retry
                                  </Button>
                                )}
                              </Stack>
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
          }}
        </QueryBoundary>
      )}

      {detail.data && (
        <OwnerApprovalDialog
          open={approveOpen}
          busy={approveMut.isPending}
          lines={[
            ['Salary batch', detail.data.batchNumber],
            ['Drivers', String(detail.data.totalDrivers)],
            ['Net salary', money(detail.data.totalNet)],
            ['Bank balance', balanceCheck ? money(balanceCheck.availableBalance) : '—'],
            ['Remaining after', balanceCheck ? money(balanceCheck.remainingAfter) : '—'],
            ['Validation', preVal?.result.replace(/_/g, ' ') ?? '—'],
          ]}
          onConfirm={() => approveMut.mutate()}
          onClose={() => setApproveOpen(false)}
        />
      )}

      <EditItemDialog item={editItem} busy={saveItem.isPending} onClose={() => setEditItem(null)} onSave={(body) => editItem && saveItem.mutate({ id: editItem.id, body })} />
      <PayDialog
        item={payItem}
        driver={payItem ? driverList.data?.find((x) => x.id === payItem.driverId) : undefined}
        busy={doPay.isPending}
        onClose={() => setPayItem(null)}
        onPay={(body) => payItem && doPay.mutate({ id: payItem.id, body })}
      />
      <ConfirmDialog
        open={confirmCancel}
        title="Cancel this salary run?"
        message="The batch is dropped. Only possible before any salary in it is paid."
        confirmLabel="Cancel run"
        destructive
        busy={lifecycle.isPending}
        onConfirm={() => lifecycle.mutate({ path: 'cancel', ok: 'Cancelled' })}
        onClose={() => setConfirmCancel(false)}
      />
    </>
  );
}

function DriverName({ id }: { id: number }) {
  const { data } = useQuery({
    queryKey: ['drivers'],
    queryFn: async () => (await api.get<Driver[]>('/drivers')).data,
    staleTime: 60_000,
  });
  const d = data?.find((x) => x.id === id);
  return (
    <Typography variant="body2" fontWeight={600} component="span">
      {d ? `${d.name} (${d.code})` : `Driver #${id}`}
    </Typography>
  );
}

const NUM = ['workingDays', 'basicSalary', 'allowances', 'overtime', 'otherEarnings', 'salaryAdvance', 'loanRecovery', 'penalty', 'otherDeduction'] as const;
const LABELS: Record<(typeof NUM)[number], string> = {
  workingDays: 'Working days',
  basicSalary: 'Basic salary',
  allowances: 'Allowances',
  overtime: 'Overtime',
  otherEarnings: 'Other earnings',
  salaryAdvance: 'Salary advance',
  loanRecovery: 'Loan recovery',
  penalty: 'Penalty',
  otherDeduction: 'Other deduction',
};

function EditItemDialog({
  item,
  busy,
  onClose,
  onSave,
}: {
  item: SalaryItem | null;
  busy: boolean;
  onClose: () => void;
  onSave: (body: Record<string, unknown>) => void;
}) {
  const [f, setF] = useState<Record<string, string>>({});
  useEffect(() => {
    if (item) setF(Object.fromEntries(NUM.map((k) => [k, String(item[k] ?? 0)])));
  }, [item]);
  const preview = () => {
    const base = item?.salaryType === 'PER_DAY' ? Number(f.basicSalary) * Number(f.workingDays) : Number(f.basicSalary);
    const gross = base + Number(f.allowances) + Number(f.overtime) + Number(f.otherEarnings);
    const ded = Number(f.salaryAdvance) + Number(f.loanRecovery) + Number(f.penalty) + Number(f.otherDeduction);
    return { gross, ded, net: Math.max(0, gross - ded) };
  };
  const p = preview();
  return (
    <Dialog open={!!item} onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle>Edit salary components</DialogTitle>
      <DialogContent>
        <FormSection title="Earnings">
          <Grid container spacing={2}>
            {(['basicSalary', 'workingDays', 'allowances', 'overtime', 'otherEarnings'] as const).map((k) => (
              <Grid size={{ xs: 6 }} key={k}>
                <TextField
                  label={LABELS[k]}
                  type="number"
                  fullWidth
                  size="small"
                  value={f[k] ?? ''}
                  onChange={(e) => setF({ ...f, [k]: e.target.value })}
                  inputProps={{ inputMode: 'decimal', min: '0' }}
                />
              </Grid>
            ))}
          </Grid>
        </FormSection>
        <FormSection title="Deductions">
          <Grid container spacing={2}>
            {(['salaryAdvance', 'loanRecovery', 'penalty', 'otherDeduction'] as const).map((k) => (
              <Grid size={{ xs: 6 }} key={k}>
                <TextField
                  label={LABELS[k]}
                  type="number"
                  fullWidth
                  size="small"
                  value={f[k] ?? ''}
                  onChange={(e) => setF({ ...f, [k]: e.target.value })}
                  inputProps={{ inputMode: 'decimal', min: '0' }}
                />
              </Grid>
            ))}
          </Grid>
        </FormSection>
        <Stack direction="row" spacing={3} sx={{ mt: 2 }}>
          <Typography variant="body2" color="text.secondary">
            Gross <b>{money(p.gross)}</b>
          </Typography>
          <Typography variant="body2" color="text.secondary">
            Deductions <b>{money(p.ded)}</b>
          </Typography>
          <Typography variant="body2" color="primary.dark">
            Net <b>{money(p.net)}</b>
          </Typography>
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button
          variant="contained"
          disabled={busy}
          onClick={() => onSave(Object.fromEntries(NUM.map((k) => [k, Number(f[k] || 0)])))}
        >
          Save changes
        </Button>
      </DialogActions>
    </Dialog>
  );
}

function PayDialog({
  item,
  driver,
  busy,
  onClose,
  onPay,
}: {
  item: SalaryItem | null;
  driver?: Driver;
  busy: boolean;
  onClose: () => void;
  onPay: (body: Record<string, unknown>) => void;
}) {
  const [method, setMethod] = useState('BANK_TRANSFER');
  const [reference, setReference] = useState('');
  const [note, setNote] = useState('');
  useEffect(() => {
    if (item) {
      setMethod('BANK_TRANSFER');
      setReference('');
      setNote('');
    }
  }, [item]);
  const needsRef = method !== 'CASH';
  const bankBlocked = method === 'BANK_TRANSFER' && driver != null && !driver.bankComplete;
  return (
    <Dialog open={!!item} onClose={onClose} fullWidth maxWidth="xs">
      <DialogTitle>Record salary payment</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          <Typography variant="body2" color="text.secondary">
            Net salary <b>{item ? money(item.netSalary) : ''}</b>
          </Typography>
          <TextField select label="Method" value={method} onChange={(e) => setMethod(e.target.value)}>
            <MenuItem value="BANK_TRANSFER">Bank transfer</MenuItem>
            <MenuItem value="CASH">Cash</MenuItem>
            <MenuItem value="OTHER">Other</MenuItem>
          </TextField>
          {bankBlocked && (
            <Alert severity="warning">
              Bank details are incomplete. Update the driver's bank details before processing salary, or pay by cash.
            </Alert>
          )}
          {method === 'BANK_TRANSFER' && driver?.bankComplete && (
            <Typography variant="caption" color="text.secondary">
              To {driver.bankName} · {driver.accountNumberMasked} · {driver.ifscCode}
            </Typography>
          )}
          <TextField
            label={needsRef ? 'Reference / UTR' : 'Reference (optional)'}
            value={reference}
            onChange={(e) => setReference(e.target.value)}
          />
          <TextField label="Note (optional)" value={note} onChange={(e) => setNote(e.target.value)} />
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button
          variant="contained"
          disabled={busy || bankBlocked || (needsRef && !reference.trim())}
          onClick={() => onPay({ method, reference: reference.trim() || undefined, note: note.trim() || undefined })}
        >
          {busy ? 'Saving…' : 'Mark paid'}
        </Button>
      </DialogActions>
    </Dialog>
  );
}

/* ------------------------------------------------------------------ Drivers tab */

function DriversTab() {
  const toast = useToast();
  const qc = useQueryClient();
  const { can } = useAuth();
  const canViewBank = can('PROCESS_PAYMENT') || can('ALL');
  const [dialog, setDialog] = useState<{ mode: 'add' } | { mode: 'edit'; row: Driver } | null>(null);
  const [deactivate, setDeactivate] = useState<Driver | null>(null);
  const [bankFor, setBankFor] = useState<Driver | null>(null);

  const list = useQuery({ queryKey: ['drivers'], queryFn: async () => (await api.get<Driver[]>('/drivers')).data });
  const vehicles = useQuery({
    queryKey: ['vehicles', 'status'],
    queryFn: async () => (await api.get<VehicleStatus[]>('/vehicles/status')).data,
  });
  const invalidate = () => qc.invalidateQueries({ queryKey: ['drivers'] });

  const save = useMutation({
    mutationFn: async ({ id, body }: { id?: number; body: Record<string, unknown> }) =>
      id ? (await api.put(`/drivers/${id}`, body)).data : (await api.post('/drivers', body)).data,
    onSuccess: () => {
      toast.success('Driver saved');
      invalidate();
      setDialog(null);
    },
    onError: toast.fromError,
  });
  const off = useMutation({
    mutationFn: async (id: number) => (await api.post(`/drivers/${id}/deactivate`)).data,
    onSuccess: () => {
      toast.success('Driver deactivated');
      invalidate();
      setDeactivate(null);
    },
    onError: toast.fromError,
  });

  const vName = (id?: number) => {
    const v = vehicles.data?.find((x) => x.vehicleId === id);
    return v ? v.code : id ? `#${id}` : '—';
  };

  return (
    <>
      <SectionTitle
        sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}
      >
        <span>Drivers</span>
        <Button size="small" variant="contained" startIcon={<AddIcon />} onClick={() => setDialog({ mode: 'add' })}>
          Add driver
        </Button>
      </SectionTitle>
      <Card>
        <QueryBoundary
          query={list}
          skeleton={<TableSkeleton cols={9} />}
          empty={(d) => d.length === 0}
          emptyState={<EmptyState title="No drivers" description="Add drivers to run monthly salary." />}
        >
          {(d) => (
            <TableContainer sx={{ maxHeight: 'calc(100vh - 320px)' }}>
              <Table size="small" stickyHeader>
                <TableHead>
                  <TableRow>
                    <TableCell>Driver ID</TableCell>
                    <TableCell>Driver Name</TableCell>
                    <TableCell>Mobile</TableCell>
                    <TableCell>Vehicle</TableCell>
                    <TableCell>Salary</TableCell>
                    <TableCell>Bank</TableCell>
                    <TableCell>Account</TableCell>
                    <TableCell>Status</TableCell>
                    <TableCell align="right">Actions</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {d.map((row) => (
                    <TableRow key={row.id} hover>
                      <TableCell sx={{ fontWeight: 600 }}>{row.code}</TableCell>
                      <TableCell>{row.name}</TableCell>
                      <TableCell>{row.mobile ?? '—'}</TableCell>
                      <TableCell>{vName(row.vehicleId)}</TableCell>
                      <TableCell>
                        {row.salaryType === 'PER_DAY'
                          ? `${money(row.perDayRate)}/day`
                          : `${money(row.monthlySalary)}/mo`}
                        <Chip size="small" variant="outlined" label={row.salaryType} sx={{ ml: 1 }} />
                      </TableCell>
                      <TableCell>
                        {row.bankName ?? (
                          <Chip size="small" color="warning" variant="outlined" label="Not set" />
                        )}
                      </TableCell>
                      <TableCell sx={{ fontVariantNumeric: 'tabular-nums' }}>
                        {row.accountNumberMasked ?? '—'}
                      </TableCell>
                      <TableCell>
                        <StatusChip status={row.status} />
                      </TableCell>
                      <TableCell align="right">
                        <Stack direction="row" spacing={0.5} justifyContent="flex-end">
                          {canViewBank && row.bankComplete && (
                            <Button size="small" onClick={() => setBankFor(row)}>
                              View
                            </Button>
                          )}
                          <Button size="small" onClick={() => setDialog({ mode: 'edit', row })}>
                            Edit
                          </Button>
                          {row.status === 'ACTIVE' && (
                            <Button size="small" color="error" onClick={() => setDeactivate(row)}>
                              Deactivate
                            </Button>
                          )}
                        </Stack>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
          )}
        </QueryBoundary>
      </Card>

      <DriverDialog
        key={dialog?.mode === 'edit' ? `e${dialog.row.id}` : 'add'}
        state={dialog}
        vehicles={vehicles.data ?? []}
        busy={save.isPending}
        onClose={() => setDialog(null)}
        onSave={(id, body) => save.mutate({ id, body })}
      />
      <BankViewDialog driver={bankFor} onClose={() => setBankFor(null)} />
      <ConfirmDialog
        open={!!deactivate}
        title="Deactivate driver?"
        message={`${deactivate?.name ?? ''} will be excluded from new salary runs.`}
        confirmLabel="Deactivate"
        destructive
        busy={off.isPending}
        onConfirm={() => deactivate && off.mutate(deactivate.id)}
        onClose={() => setDeactivate(null)}
      />
    </>
  );
}

const DRIVER_BLANK = {
  name: '',
  mobile: '',
  joiningDate: '',
  vehicleId: '',
  salaryType: 'FIXED',
  monthlySalary: '',
  perDayRate: '',
  status: 'ACTIVE',
  accountHolderName: '',
  accountNumber: '',
  ifscCode: '',
  bankName: '',
  branchName: '',
};

function DriverDialog({
  state,
  vehicles,
  busy,
  onClose,
  onSave,
}: {
  state: { mode: 'add' } | { mode: 'edit'; row: Driver } | null;
  vehicles: VehicleStatus[];
  busy: boolean;
  onClose: () => void;
  onSave: (id: number | undefined, body: Record<string, unknown>) => void;
}) {
  const editing = state?.mode === 'edit' ? state.row : null;
  const [f, setF] = useState(DRIVER_BLANK);
  const [tried, setTried] = useState(false);
  useEffect(() => {
    setTried(false);
    if (editing) {
      setF({
        name: editing.name,
        mobile: editing.mobile ?? '',
        joiningDate: editing.joiningDate ?? '',
        vehicleId: editing.vehicleId ? String(editing.vehicleId) : '',
        salaryType: editing.salaryType,
        monthlySalary: editing.monthlySalary != null ? String(editing.monthlySalary) : '',
        perDayRate: editing.perDayRate != null ? String(editing.perDayRate) : '',
        status: editing.status,
        accountHolderName: editing.accountHolderName ?? '',
        accountNumber: '',
        ifscCode: editing.ifscCode ?? '',
        bankName: editing.bankName ?? '',
        branchName: editing.branchName ?? '',
      });
    } else {
      setF(DRIVER_BLANK);
    }
  }, [state]);
  const set =
    (k: keyof typeof f, transform?: (v: string) => string) =>
    (e: { target: { value: string } }) =>
      setF((s) => ({ ...s, [k]: transform ? transform(e.target.value) : e.target.value }));

  const keepAccount = !!editing?.accountNumberMasked; // edit: blank account number = keep existing
  const errors: Record<string, string> = {
    name: V.required('Driver name')(f.name),
    mobile: V.mobile(f.mobile, false),
    monthlySalary: f.salaryType !== 'PER_DAY' && !(Number(f.monthlySalary) >= 0) ? 'Enter a monthly salary' : '',
    perDayRate: f.salaryType === 'PER_DAY' && !(Number(f.perDayRate) > 0) ? 'Enter a per-day rate' : '',
    accountHolderName: V.required('Account holder name')(f.accountHolderName),
    accountNumber: V.accountNumber(f.accountNumber, keepAccount),
    ifscCode: V.ifsc(f.ifscCode),
    bankName: V.required('Bank name')(f.bankName),
  };
  const showErr = (k: string) => (tried ? errors[k] ?? '' : '');
  const valid = V.allValid(errors);

  const submit = () => {
    setTried(true);
    if (!valid) return;
    onSave(editing?.id, {
      name: f.name.trim(),
      mobile: f.mobile.trim() || undefined,
      joiningDate: f.joiningDate || undefined,
      vehicleId: f.vehicleId ? Number(f.vehicleId) : undefined,
      salaryType: f.salaryType,
      monthlySalary: f.salaryType === 'PER_DAY' ? 0 : Number(f.monthlySalary || 0),
      perDayRate: f.salaryType === 'PER_DAY' ? Number(f.perDayRate || 0) : undefined,
      status: editing ? f.status : undefined,
      accountHolderName: f.accountHolderName.trim(),
      accountNumber: f.accountNumber.trim() || undefined,
      ifscCode: f.ifscCode.trim().toUpperCase(),
      bankName: f.bankName.trim(),
      branchName: f.branchName.trim() || undefined,
    });
  };

  return (
    <Dialog open={!!state} onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle>{editing ? `Edit ${editing.code}` : 'Add driver'}</DialogTitle>
      <DialogContent dividers>
        <Stack spacing={3}>
          <FormSection title="Basic Information">
            <Grid container spacing={2}>
              <Grid size={{ xs: 12, sm: 6 }}>
                <TextField fullWidth label="Driver Name" value={f.name} onChange={set('name')} error={!!showErr('name')} helperText={showErr('name')} required />
              </Grid>
              <Grid size={{ xs: 12, sm: 6 }}>
                <TextField
                  fullWidth
                  label="Mobile"
                  value={f.mobile}
                  onChange={set('mobile', (v) => V.onlyDigits(v).slice(0, 10))}
                  error={!!showErr('mobile')}
                  helperText={showErr('mobile')}
                  inputProps={{ inputMode: 'numeric', maxLength: 10 }}
                  required
                />
              </Grid>
              <Grid size={{ xs: 12, sm: 6 }}>
                <TextField fullWidth label="Joining Date" type="date" value={f.joiningDate} onChange={set('joiningDate')} InputLabelProps={{ shrink: true }} required />
              </Grid>
              {editing && (
                <Grid size={{ xs: 12, sm: 6 }}>
                  <TextField select fullWidth label="Status" value={f.status} onChange={set('status')}>
                    <MenuItem value="ACTIVE">Active</MenuItem>
                    <MenuItem value="INACTIVE">Inactive</MenuItem>
                  </TextField>
                </Grid>
              )}
            </Grid>
          </FormSection>

          <FormSection title="Vehicle Assignment">
            <TextField select label="Assigned Vehicle" value={f.vehicleId} onChange={set('vehicleId')}>
              <MenuItem value="">— none —</MenuItem>
              {vehicles.map((v) => (
                <MenuItem key={v.vehicleId} value={String(v.vehicleId)}>
                  {v.code} · {v.registrationNumber}
                </MenuItem>
              ))}
            </TextField>
          </FormSection>

          <FormSection title="Salary Information">
            <Grid container spacing={2}>
              <Grid size={{ xs: 12, sm: 6 }}>
                <TextField select fullWidth label="Salary Type" value={f.salaryType} onChange={set('salaryType')}>
                  <MenuItem value="FIXED">Fixed monthly</MenuItem>
                  <MenuItem value="PER_DAY">Per day</MenuItem>
                  <MenuItem value="CUSTOM">Custom</MenuItem>
                </TextField>
              </Grid>
              <Grid size={{ xs: 12, sm: 6 }}>
                {f.salaryType === 'PER_DAY' ? (
                  <TextField
                    fullWidth
                    label="Per-day Rate"
                    type="number"
                    value={f.perDayRate}
                    onChange={set('perDayRate')}
                    error={!!showErr('perDayRate')}
                    helperText={showErr('perDayRate')}
                    inputProps={{ inputMode: 'decimal', min: '0' }}
                  />
                ) : (
                  <TextField
                    fullWidth
                    label="Monthly Salary"
                    type="number"
                    value={f.monthlySalary}
                    onChange={set('monthlySalary')}
                    error={!!showErr('monthlySalary')}
                    helperText={showErr('monthlySalary')}
                    inputProps={{ inputMode: 'decimal', min: '0' }}
                    required
                  />
                )}
              </Grid>
            </Grid>
          </FormSection>

          <FormSection title="Bank Details" hint="Used to pay monthly salary by bank transfer.">
            <Grid container spacing={2}>
              <Grid size={{ xs: 12 }}>
                <TextField
                  fullWidth
                  label="Account Holder Name"
                  value={f.accountHolderName}
                  onChange={set('accountHolderName')}
                  error={!!showErr('accountHolderName')}
                  helperText={showErr('accountHolderName')}
                  required
                />
              </Grid>
              <Grid size={{ xs: 12, sm: 6 }}>
                <TextField
                  fullWidth
                  label="Account Number"
                  value={f.accountNumber}
                  onChange={set('accountNumber', (v) => V.onlyDigits(v).slice(0, 18))}
                  error={!!showErr('accountNumber')}
                  helperText={
                    showErr('accountNumber') ||
                    (keepAccount ? `Current: ${editing?.accountNumberMasked} — leave blank to keep` : 'Digits only, 9–18 long')
                  }
                  inputProps={{ inputMode: 'numeric', maxLength: 18 }}
                  required={!keepAccount}
                />
              </Grid>
              <Grid size={{ xs: 12, sm: 6 }}>
                <TextField
                  fullWidth
                  label="IFSC Code"
                  value={f.ifscCode}
                  onChange={set('ifscCode', (v) => V.upper(v).replace(/[^A-Z0-9]/g, '').slice(0, 11))}
                  error={!!showErr('ifscCode')}
                  helperText={showErr('ifscCode')}
                  inputProps={{ maxLength: 11, style: { textTransform: 'uppercase' } }}
                  required
                />
              </Grid>
              <Grid size={{ xs: 12, sm: 6 }}>
                <TextField
                  fullWidth
                  label="Bank Name"
                  value={f.bankName}
                  onChange={set('bankName')}
                  error={!!showErr('bankName')}
                  helperText={showErr('bankName')}
                  required
                />
              </Grid>
              <Grid size={{ xs: 12, sm: 6 }}>
                <TextField fullWidth label="Branch" value={f.branchName} onChange={set('branchName')} />
              </Grid>
            </Grid>
          </FormSection>

          {tried && !valid && (
            <Typography variant="caption" color="error">
              Please fix the highlighted fields.
            </Typography>
          )}
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button variant="contained" disabled={busy} onClick={submit}>
          {busy ? 'Saving…' : editing ? 'Save changes' : 'Add driver'}
        </Button>
      </DialogActions>
    </Dialog>
  );
}

function BankViewDialog({ driver, onClose }: { driver: Driver | null; onClose: () => void }) {
  const [reveal, setReveal] = useState(false);
  useEffect(() => setReveal(false), [driver]);
  const full = useQuery({
    queryKey: ['driver-bank', driver?.id],
    queryFn: async () => (await api.get<DriverBankDetails>(`/drivers/${driver!.id}/bank-details`)).data,
    enabled: !!driver && reveal,
  });
  return (
    <Dialog open={!!driver} onClose={onClose} fullWidth maxWidth="xs">
      <DialogTitle>Bank details — {driver?.name}</DialogTitle>
      <DialogContent dividers>
        <Stack spacing={1.25}>
          <Line label="Account holder" value={driver?.accountHolderName ?? '—'} />
          <Line
            label="Account number"
            value={reveal ? full.data?.accountNumber ?? 'Loading…' : driver?.accountNumberMasked ?? '—'}
          />
          <Line label="IFSC" value={driver?.ifscCode ?? '—'} />
          <Line label="Bank" value={driver?.bankName ?? '—'} />
          <Line label="Branch" value={driver?.branchName ?? '—'} />
          {driver?.bankUpdatedAt && (
            <Line label="Last updated" value={new Date(driver.bankUpdatedAt).toLocaleString()} />
          )}
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={() => setReveal((r) => !r)}>{reveal ? 'Hide full number' : 'Show full number'}</Button>
        <Box sx={{ flex: 1 }} />
        <Button onClick={onClose}>Close</Button>
      </DialogActions>
    </Dialog>
  );
}

function Line({ label, value }: { label: string; value: ReactNode }) {
  return (
    <Stack direction="row" justifyContent="space-between" spacing={2}>
      <Typography variant="body2" color="text.secondary">
        {label}
      </Typography>
      <Typography variant="body2" sx={{ fontWeight: 600, textAlign: 'right' }}>
        {value}
      </Typography>
    </Stack>
  );
}
