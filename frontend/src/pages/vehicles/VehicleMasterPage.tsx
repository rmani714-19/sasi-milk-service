import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogTitle from '@mui/material/DialogTitle';
import Grid from '@mui/material/Grid2';
import MenuItem from '@mui/material/MenuItem';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableContainer from '@mui/material/TableContainer';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import TextField from '@mui/material/TextField';
import AddIcon from '@mui/icons-material/Add';
import LocalShippingOutlinedIcon from '@mui/icons-material/LocalShippingOutlined';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../../lib/api';
import { useDriverLookup } from '../../api/common';
import { PageHeader } from '../../components/PageHeader';
import { StatusChip } from '../../components/StatusChip';
import { EmptyState, QueryBoundary, TableSkeleton, useToast } from '../../components/ui';
import { useAuth } from '../../lib/auth';
import type { Vehicle } from '../../api/types';

const VEHICLE_TYPES = ['CAR', 'VAN', 'MINI_TRUCK', 'TRUCK', 'OTHER'];

interface VehicleForm {
  code: string;
  vehicleNumber: string;
  vehicleType: string;
  make: string;
  model: string;
  modelYear: string;
  capacityLitres: string;
  registrationDate: string;
  insuranceExpiry: string;
  pollutionExpiry: string;
  notes: string;
}

const EMPTY_FORM: VehicleForm = {
  code: '', vehicleNumber: '', vehicleType: 'OTHER', make: '', model: '', modelYear: '',
  capacityLitres: '', registrationDate: '', insuranceExpiry: '', pollutionExpiry: '', notes: '',
};

export function VehicleMasterPage() {
  const nav = useNavigate();
  const toast = useToast();
  const qc = useQueryClient();
  const { can } = useAuth();
  const drivers = useDriverLookup();
  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState<Vehicle | null>(null);
  const [form, setForm] = useState<VehicleForm>(EMPTY_FORM);

  const q = useQuery({
    queryKey: ['vehicles'],
    queryFn: async () => (await api.get<Vehicle[]>('/vehicles')).data,
  });

  const set = <K extends keyof VehicleForm>(k: K, v: VehicleForm[K]) => setForm((f) => ({ ...f, [k]: v }));

  const openCreate = () => {
    setEditing(null);
    setForm(EMPTY_FORM);
    setOpen(true);
  };
  const openEdit = (v: Vehicle) => {
    setEditing(v);
    setForm({
      code: v.code, vehicleNumber: v.vehicleNumber, vehicleType: v.vehicleType, make: v.make ?? '',
      model: v.model ?? '', modelYear: v.modelYear ? String(v.modelYear) : '',
      capacityLitres: v.capacityLitres != null ? String(v.capacityLitres) : '',
      registrationDate: v.registrationDate ?? '', insuranceExpiry: v.insuranceExpiry ?? '',
      pollutionExpiry: v.pollutionExpiry ?? '', notes: v.notes ?? '',
    });
    setOpen(true);
  };

  const payload = () => ({
    vehicleType: form.vehicleType,
    make: form.make || undefined,
    model: form.model || undefined,
    modelYear: form.modelYear ? Number(form.modelYear) : undefined,
    capacityLitres: form.capacityLitres ? Number(form.capacityLitres) : undefined,
    registrationDate: form.registrationDate || undefined,
    insuranceExpiry: form.insuranceExpiry || undefined,
    pollutionExpiry: form.pollutionExpiry || undefined,
    notes: form.notes || undefined,
  });

  const save = useMutation({
    mutationFn: async () =>
      editing
        ? (await api.put(`/vehicles/${editing.id}`, payload())).data
        : (await api.post('/vehicles', { ...payload(), code: form.code, vehicleNumber: form.vehicleNumber })).data,
    onSuccess: () => {
      toast.success(editing ? 'Vehicle updated' : 'Vehicle added');
      qc.invalidateQueries({ queryKey: ['vehicles'] });
      setOpen(false);
    },
    onError: toast.fromError,
  });

  const canManage = can('MANAGE_VEHICLES') || can('ALL');
  const valid = !editing ? !!form.code.trim() && !!form.vehicleNumber.trim() : true;

  return (
    <>
      <PageHeader
        title="Vehicle Master"
        subtitle="Vehicles, driver assignment, and GPS/camera hardware"
        action={
          canManage && (
            <Button variant="contained" startIcon={<AddIcon />} onClick={openCreate}>
              Add vehicle
            </Button>
          )
        }
      />

      <Card>
        <QueryBoundary
          query={q}
          skeleton={<TableSkeleton cols={7} />}
          empty={(data) => data.length === 0}
          emptyState={
            <EmptyState
              icon={<LocalShippingOutlinedIcon />}
              title="No vehicles registered"
              description="Add a vehicle to start tracking it."
              action={
                canManage && (
                  <Button variant="contained" startIcon={<AddIcon />} onClick={openCreate}>
                    Add vehicle
                  </Button>
                )
              }
            />
          }
        >
          {(list) => (
            <TableContainer>
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell>Code</TableCell>
                    <TableCell>Vehicle No</TableCell>
                    <TableCell>Type</TableCell>
                    <TableCell>Make / Model</TableCell>
                    <TableCell>Driver</TableCell>
                    <TableCell>Status</TableCell>
                    <TableCell align="right" />
                  </TableRow>
                </TableHead>
                <TableBody>
                  {list.map((v) => (
                    <TableRow key={v.id} hover sx={{ cursor: 'pointer' }} onClick={() => nav(`/vehicles/${v.id}`)}>
                      <TableCell sx={{ fontWeight: 600 }}>{v.code}</TableCell>
                      <TableCell>{v.vehicleNumber}</TableCell>
                      <TableCell>{v.vehicleType.replace(/_/g, ' ')}</TableCell>
                      <TableCell>{[v.make, v.model].filter(Boolean).join(' ') || '—'}</TableCell>
                      <TableCell>{drivers.label(v.driverId)}</TableCell>
                      <TableCell>
                        <StatusChip status={v.status} />
                      </TableCell>
                      <TableCell align="right" onClick={(e) => e.stopPropagation()}>
                        {canManage && (
                          <Button size="small" onClick={() => openEdit(v)}>
                            Edit
                          </Button>
                        )}
                        <Button size="small" onClick={() => nav(`/vehicles/${v.id}`)}>
                          Open
                        </Button>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
          )}
        </QueryBoundary>
      </Card>

      <Dialog open={open} onClose={() => setOpen(false)} fullWidth maxWidth="sm">
        <DialogTitle>{editing ? `Edit ${editing.vehicleNumber}` : 'Add vehicle'}</DialogTitle>
        <DialogContent>
          <Grid container spacing={2} sx={{ mt: 0.5 }}>
            {!editing && (
              <>
                <Grid size={6}>
                  <TextField label="Code" fullWidth value={form.code} onChange={(e) => set('code', e.target.value)} />
                </Grid>
                <Grid size={6}>
                  <TextField label="Vehicle number" fullWidth value={form.vehicleNumber}
                    onChange={(e) => set('vehicleNumber', e.target.value.toUpperCase())} />
                </Grid>
              </>
            )}
            <Grid size={6}>
              <TextField select label="Type" fullWidth value={form.vehicleType} onChange={(e) => set('vehicleType', e.target.value)}>
                {VEHICLE_TYPES.map((t) => (
                  <MenuItem key={t} value={t}>{t.replace(/_/g, ' ')}</MenuItem>
                ))}
              </TextField>
            </Grid>
            <Grid size={6}>
              <TextField label="Capacity (litres)" type="number" fullWidth value={form.capacityLitres}
                onChange={(e) => set('capacityLitres', e.target.value)} />
            </Grid>
            <Grid size={6}>
              <TextField label="Make" fullWidth value={form.make} onChange={(e) => set('make', e.target.value)} />
            </Grid>
            <Grid size={6}>
              <TextField label="Model" fullWidth value={form.model} onChange={(e) => set('model', e.target.value)} />
            </Grid>
            <Grid size={6}>
              <TextField label="Model year" type="number" fullWidth value={form.modelYear}
                onChange={(e) => set('modelYear', e.target.value)} />
            </Grid>
            <Grid size={6}>
              <TextField label="Registration date" type="date" fullWidth InputLabelProps={{ shrink: true }}
                value={form.registrationDate} onChange={(e) => set('registrationDate', e.target.value)} />
            </Grid>
            <Grid size={6}>
              <TextField label="Insurance expiry" type="date" fullWidth InputLabelProps={{ shrink: true }}
                value={form.insuranceExpiry} onChange={(e) => set('insuranceExpiry', e.target.value)} />
            </Grid>
            <Grid size={6}>
              <TextField label="Pollution (PUC) expiry" type="date" fullWidth InputLabelProps={{ shrink: true }}
                value={form.pollutionExpiry} onChange={(e) => set('pollutionExpiry', e.target.value)} />
            </Grid>
            <Grid size={12}>
              <TextField label="Notes" fullWidth multiline minRows={2} value={form.notes} onChange={(e) => set('notes', e.target.value)} />
            </Grid>
          </Grid>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setOpen(false)}>Cancel</Button>
          <Button variant="contained" disabled={!valid || save.isPending} onClick={() => save.mutate()}>
            {save.isPending ? 'Saving…' : 'Save'}
          </Button>
        </DialogActions>
      </Dialog>
    </>
  );
}
