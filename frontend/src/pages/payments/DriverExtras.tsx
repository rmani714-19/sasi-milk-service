import { useEffect, useState } from 'react';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import Checkbox from '@mui/material/Checkbox';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogTitle from '@mui/material/DialogTitle';
import FormControlLabel from '@mui/material/FormControlLabel';
import MenuItem from '@mui/material/MenuItem';
import Stack from '@mui/material/Stack';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import AddIcon from '@mui/icons-material/Add';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../../lib/api';
import { money } from '../../lib/format';
import { regionLabel } from '../../lib/region';
import { useRegions } from '../../api/common';
import { StatusChip } from '../../components/StatusChip';
import { EmptyState, QueryBoundary, SectionTitle, TableSkeleton, useToast } from '../../components/ui';
import type { Driver, VehicleStatus } from '../../api/types';

const todayIso = () => new Date().toISOString().slice(0, 10);

/* ------------------------------------------------------------------ route */

/** Choose the regions (areas) a driver collects from — their route. The salary statement reports litres collected there. */
export function DriverRouteDialog({ driver, onClose }: { driver: Driver | null; onClose: () => void }) {
  const toast = useToast();
  const qc = useQueryClient();
  const regions = useRegions();
  const [picked, setPicked] = useState<number[]>([]);
  useEffect(() => setPicked(driver?.routeRegionIds ?? []), [driver]);

  const save = useMutation({
    mutationFn: async () => (await api.put(`/drivers/${driver!.id}/route`, { regionIds: picked })).data,
    onSuccess: () => {
      toast.success('Route saved');
      qc.invalidateQueries({ queryKey: ['drivers'] });
      onClose();
    },
    onError: toast.fromError,
  });

  const toggle = (id: number) => setPicked((p) => (p.includes(id) ? p.filter((x) => x !== id) : [...p, id]));

  return (
    <Dialog open={!!driver} onClose={onClose} fullWidth maxWidth="xs">
      <DialogTitle>Route — {driver?.name}</DialogTitle>
      <DialogContent dividers>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 1.5 }}>
          Tick the regions / areas this driver collects milk from. The monthly salary statement shows the litres collected
          there, day by day.
        </Typography>
        <Stack>
          {(regions.data ?? [])
            .filter((r) => r.status === 'ACTIVE')
            .map((r) => (
              <FormControlLabel
                key={r.id}
                control={<Checkbox checked={picked.includes(r.id)} onChange={() => toggle(r.id)} />}
                label={regionLabel(r)}
              />
            ))}
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button variant="contained" disabled={save.isPending} onClick={() => save.mutate()}>
          Save route
        </Button>
      </DialogActions>
    </Dialog>
  );
}

/* ------------------------------------------------------------------ advances */

interface Advance {
  id: number;
  advanceDate: string;
  amount: number;
  reason?: string;
  status: 'OPEN' | 'DEDUCTED' | 'CANCELLED';
}

/** Money given to the driver ahead of salary. Open advances are deducted by the next salary run automatically. */
export function DriverAdvancesDialog({ driver, onClose }: { driver: Driver | null; onClose: () => void }) {
  const toast = useToast();
  const qc = useQueryClient();
  const [date, setDate] = useState(todayIso());
  const [amount, setAmount] = useState('');
  const [reason, setReason] = useState('');

  const list = useQuery({
    queryKey: ['driver-advances', driver?.id],
    queryFn: async () => (await api.get<Advance[]>(`/drivers/${driver!.id}/advances`)).data,
    enabled: !!driver,
  });
  const refresh = () => qc.invalidateQueries({ queryKey: ['driver-advances', driver?.id] });

  const give = useMutation({
    mutationFn: async () =>
      (await api.post(`/drivers/${driver!.id}/advances`, { advanceDate: date, amount: Number(amount), reason: reason.trim() || undefined })).data,
    onSuccess: () => {
      toast.success('Advance recorded');
      setAmount('');
      setReason('');
      refresh();
    },
    onError: toast.fromError,
  });
  const cancel = useMutation({
    mutationFn: async (id: number) => (await api.post(`/driver-advances/${id}/cancel`)).data,
    onSuccess: () => {
      toast.success('Advance cancelled');
      refresh();
    },
    onError: toast.fromError,
  });

  const open = (list.data ?? []).filter((a) => a.status === 'OPEN').reduce((s, a) => s + Number(a.amount), 0);

  return (
    <Dialog open={!!driver} onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle>Advances — {driver?.name}</DialogTitle>
      <DialogContent dividers>
        <Stack direction="row" spacing={1.5} flexWrap="wrap" useFlexGap sx={{ mb: 2 }}>
          <TextField
            type="date"
            size="small"
            label="Date given"
            value={date}
            onChange={(e) => setDate(e.target.value)}
            InputLabelProps={{ shrink: true }}
            sx={{ width: 160, flexShrink: 0 }}
          />
          <TextField
            size="small"
            label="Amount"
            type="number"
            value={amount}
            onChange={(e) => setAmount(e.target.value)}
            sx={{ width: 130, flexShrink: 0 }}
          />
          <TextField size="small" label="Reason (optional)" value={reason} onChange={(e) => setReason(e.target.value)} sx={{ flex: '1 1 160px', minWidth: 140 }} />
          <Button
            variant="contained"
            startIcon={<AddIcon />}
            disabled={!(Number(amount) > 0) || !date || give.isPending}
            onClick={() => give.mutate()}
            sx={{ flexShrink: 0 }}
          >
            Add
          </Button>
        </Stack>
        <Typography variant="body2" sx={{ mb: 1 }}>
          Open advances to deduct from the next salary: <b>{money(open)}</b>
        </Typography>
        <QueryBoundary
          query={list}
          skeleton={<TableSkeleton cols={4} rows={3} />}
          empty={(d) => d.length === 0}
          emptyState={<EmptyState title="No advances" description="Add one above when you give the driver money ahead of salary." />}
        >
          {(rows) => (
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell>Date</TableCell>
                  <TableCell>Reason</TableCell>
                  <TableCell align="right">Amount</TableCell>
                  <TableCell>Status</TableCell>
                  <TableCell />
                </TableRow>
              </TableHead>
              <TableBody>
                {rows.map((a) => (
                  <TableRow key={a.id}>
                    <TableCell>{a.advanceDate}</TableCell>
                    <TableCell>{a.reason ?? '—'}</TableCell>
                    <TableCell align="right">{money(a.amount)}</TableCell>
                    <TableCell>
                      <StatusChip status={a.status} label={a.status === 'OPEN' ? 'To be deducted' : a.status === 'DEDUCTED' ? 'Deducted' : 'Cancelled'} />
                    </TableCell>
                    <TableCell align="right">
                      {a.status === 'OPEN' && (
                        <Button size="small" color="error" disabled={cancel.isPending} onClick={() => cancel.mutate(a.id)}>
                          Cancel
                        </Button>
                      )}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </QueryBoundary>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Close</Button>
      </DialogActions>
    </Dialog>
  );
}

/* ------------------------------------------------------------------ vehicle expenses */

interface Expense {
  id: number;
  expenseNumber: string;
  category: string;
  description?: string;
  amount: number;
  expenseDate: string;
  paymentMethod: string;
  status: string;
  vehicleId?: number;
}

const CATEGORIES = ['FUEL', 'MAINTENANCE', 'TRANSPORT', 'MISC', 'OTHER'] as const;
const pretty = (s: string) => s.charAt(0) + s.slice(1).toLowerCase().replace(/_/g, ' ');

/** What was spent on each vehicle (fuel, repairs…). The driver's salary statement lists these for the month. */
export function VehicleExpensesTab() {
  const toast = useToast();
  const qc = useQueryClient();
  const [vehicleFilter, setVehicleFilter] = useState('');
  const [adding, setAdding] = useState(false);

  const vehicles = useQuery({
    queryKey: ['vehicles', 'status'],
    queryFn: async () => (await api.get<VehicleStatus[]>('/vehicles/status')).data,
  });
  const list = useQuery({
    queryKey: ['expenses'],
    queryFn: async () => (await api.get<Expense[]>('/expenses')).data,
  });
  const vName = (id?: number) => {
    const v = vehicles.data?.find((x) => x.vehicleId === id);
    return v ? `${v.registrationNumber} (${v.code})` : id ? `Vehicle #${id}` : '—';
  };

  const cancel = useMutation({
    mutationFn: async (id: number) => api.post(`/expenses/${id}/cancel`),
    onSuccess: () => {
      toast.success('Expense cancelled');
      qc.invalidateQueries({ queryKey: ['expenses'] });
    },
    onError: toast.fromError,
  });

  return (
    <>
      <SectionTitle sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <span>Vehicle expenses</span>
        <Button size="small" variant="contained" startIcon={<AddIcon />} onClick={() => setAdding(true)}>
          Add expense
        </Button>
      </SectionTitle>
      <Box sx={{ mb: 2 }}>
        <TextField select size="small" label="Vehicle" value={vehicleFilter} onChange={(e) => setVehicleFilter(e.target.value)} sx={{ width: 240 }}>
          <MenuItem value="">All vehicles</MenuItem>
          {(vehicles.data ?? []).map((v) => (
            <MenuItem key={v.vehicleId} value={String(v.vehicleId)}>
              {v.registrationNumber} ({v.code})
            </MenuItem>
          ))}
        </TextField>
      </Box>
      <Card>
        <QueryBoundary
          query={list}
          skeleton={<TableSkeleton cols={6} />}
          empty={(d) => d.filter((e) => e.vehicleId != null).length === 0}
          emptyState={<EmptyState title="No vehicle expenses" description="Record fuel, repairs and other costs against a vehicle." />}
        >
          {(all) => {
            const rows = all
              .filter((e) => e.vehicleId != null && e.status !== 'CANCELLED')
              .filter((e) => !vehicleFilter || String(e.vehicleId) === vehicleFilter);
            const total = rows.reduce((s, e) => s + Number(e.amount), 0);
            return (
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell>Date</TableCell>
                    <TableCell>Vehicle</TableCell>
                    <TableCell>Category</TableCell>
                    <TableCell>Description</TableCell>
                    <TableCell align="right">Amount</TableCell>
                    <TableCell />
                  </TableRow>
                </TableHead>
                <TableBody>
                  {rows.map((e) => (
                    <TableRow key={e.id} hover>
                      <TableCell>{e.expenseDate}</TableCell>
                      <TableCell>{vName(e.vehicleId)}</TableCell>
                      <TableCell>{pretty(e.category)}</TableCell>
                      <TableCell>{e.description ?? '—'}</TableCell>
                      <TableCell align="right" sx={{ fontWeight: 600 }}>
                        {money(e.amount)}
                      </TableCell>
                      <TableCell align="right">
                        <Button size="small" color="error" disabled={cancel.isPending} onClick={() => cancel.mutate(e.id)}>
                          Cancel
                        </Button>
                      </TableCell>
                    </TableRow>
                  ))}
                  <TableRow>
                    <TableCell colSpan={4} sx={{ fontWeight: 700 }}>
                      Total
                    </TableCell>
                    <TableCell align="right" sx={{ fontWeight: 700 }}>
                      {money(total)}
                    </TableCell>
                    <TableCell />
                  </TableRow>
                </TableBody>
              </Table>
            );
          }}
        </QueryBoundary>
      </Card>
      <ExpenseDialog open={adding} vehicles={vehicles.data ?? []} onClose={() => setAdding(false)} />
    </>
  );
}

function ExpenseDialog({ open, vehicles, onClose }: { open: boolean; vehicles: VehicleStatus[]; onClose: () => void }) {
  const toast = useToast();
  const qc = useQueryClient();
  const [vehicleId, setVehicleId] = useState('');
  const [category, setCategory] = useState<string>('FUEL');
  const [amount, setAmount] = useState('');
  const [date, setDate] = useState(todayIso());
  const [description, setDescription] = useState('');
  const [method, setMethod] = useState('CASH');

  const save = useMutation({
    mutationFn: async () =>
      (
        await api.post('/expenses', {
          vehicleId: Number(vehicleId),
          category,
          amount: Number(amount),
          expenseDate: date,
          description: description.trim() || undefined,
          paymentMethod: method,
        })
      ).data,
    onSuccess: () => {
      toast.success('Expense recorded');
      qc.invalidateQueries({ queryKey: ['expenses'] });
      setAmount('');
      setDescription('');
      onClose();
    },
    onError: toast.fromError,
  });

  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="xs">
      <DialogTitle>Add vehicle expense</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          <TextField select label="Vehicle" value={vehicleId} onChange={(e) => setVehicleId(e.target.value)} required>
            {vehicles.map((v) => (
              <MenuItem key={v.vehicleId} value={String(v.vehicleId)}>
                {v.registrationNumber} ({v.code})
              </MenuItem>
            ))}
          </TextField>
          <TextField select label="Category" value={category} onChange={(e) => setCategory(e.target.value)}>
            {CATEGORIES.map((c) => (
              <MenuItem key={c} value={c}>
                {pretty(c)}
              </MenuItem>
            ))}
          </TextField>
          <TextField label="Amount" type="number" value={amount} onChange={(e) => setAmount(e.target.value)} required />
          <TextField label="Date" type="date" value={date} onChange={(e) => setDate(e.target.value)} InputLabelProps={{ shrink: true }} />
          <TextField select label="Paid by" value={method} onChange={(e) => setMethod(e.target.value)}>
            {['CASH', 'UPI', 'BANK_TRANSFER', 'CHEQUE', 'OTHER'].map((m) => (
              <MenuItem key={m} value={m}>
                {pretty(m)}
              </MenuItem>
            ))}
          </TextField>
          <TextField label="Description (optional)" value={description} onChange={(e) => setDescription(e.target.value)} />
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button variant="contained" disabled={!vehicleId || !(Number(amount) > 0) || save.isPending} onClick={() => save.mutate()}>
          Save
        </Button>
      </DialogActions>
    </Dialog>
  );
}
