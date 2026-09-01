import { useState } from 'react';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Chip from '@mui/material/Chip';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogTitle from '@mui/material/DialogTitle';
import Grid from '@mui/material/Grid2';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import AddIcon from '@mui/icons-material/Add';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../lib/api';
import { PageHeader } from '../components/PageHeader';
import { useToast } from '../components/ui';
import { useAuth } from '../lib/auth';
import type { VehicleStatus } from '../api/types';

export function VehiclesPage() {
  const toast = useToast();
  const qc = useQueryClient();
  const { can } = useAuth();
  const [open, setOpen] = useState(false);
  const [f, setF] = useState({ code: '', registrationNumber: '', capacityLitres: '' });

  const status = useQuery({
    queryKey: ['vehicles', 'status'],
    queryFn: async () => (await api.get<VehicleStatus[]>('/vehicles/status')).data,
    refetchInterval: 15_000,
  });
  const create = useMutation({
    mutationFn: async () =>
      (await api.post('/vehicles', {
        code: f.code,
        registrationNumber: f.registrationNumber,
        capacityLitres: f.capacityLitres ? Number(f.capacityLitres) : undefined,
      })).data,
    onSuccess: () => {
      toast.success('Vehicle added');
      qc.invalidateQueries({ queryKey: ['vehicles'] });
      setOpen(false);
      setF({ code: '', registrationNumber: '', capacityLitres: '' });
    },
    onError: toast.fromError,
  });

  return (
    <>
      <PageHeader
        title="Vehicles"
        subtitle="Phase 2 — GPS provider integration pending"
        action={
          can('ALL') && (
            <Button variant="contained" startIcon={<AddIcon />} onClick={() => setOpen(true)}>
              Add vehicle
            </Button>
          )
        }
      />
      <Grid container spacing={2}>
        {status.data?.map((v) => (
          <Grid size={{ xs: 12, sm: 6, md: 4 }} key={v.vehicleId}>
            <Card>
              <CardContent>
                <Stack direction="row" justifyContent="space-between" alignItems="center">
                  <Typography variant="h6">{v.code}</Typography>
                  <Chip size="small" label={v.online ? 'Online' : 'Offline'} color={v.online ? 'success' : 'default'} />
                </Stack>
                <Typography variant="body2" color="text.secondary">
                  {v.registrationNumber}
                </Typography>
                <Typography variant="body2" sx={{ mt: 1 }}>
                  {v.latitude != null ? `${v.latitude.toFixed(4)}, ${v.longitude?.toFixed(4)}` : 'No GPS fix'}
                  {v.speedKmph != null && ` · ${v.speedKmph} km/h`}
                </Typography>
                {v.lastFixAt && (
                  <Typography variant="caption" color="text.secondary">
                    last fix {new Date(v.lastFixAt).toLocaleString()}
                  </Typography>
                )}
              </CardContent>
            </Card>
          </Grid>
        ))}
        {status.data?.length === 0 && (
          <Grid size={12}>
            <Typography color="text.secondary" sx={{ p: 2 }}>
              No vehicles registered.
            </Typography>
          </Grid>
        )}
      </Grid>

      <Dialog open={open} onClose={() => setOpen(false)} fullWidth maxWidth="xs">
        <DialogTitle>Add vehicle</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }}>
            <TextField label="Code" value={f.code} onChange={(e) => setF({ ...f, code: e.target.value })} />
            <TextField label="Registration number" value={f.registrationNumber} onChange={(e) => setF({ ...f, registrationNumber: e.target.value })} />
            <TextField label="Capacity (litres)" type="number" value={f.capacityLitres} onChange={(e) => setF({ ...f, capacityLitres: e.target.value })} />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setOpen(false)}>Cancel</Button>
          <Button variant="contained" disabled={!f.code || !f.registrationNumber || create.isPending} onClick={() => create.mutate()}>
            Save
          </Button>
        </DialogActions>
      </Dialog>
    </>
  );
}
