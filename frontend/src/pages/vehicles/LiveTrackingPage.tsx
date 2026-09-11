import { useMemo, useState } from 'react';
import { Link as RouterLink } from 'react-router-dom';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Chip from '@mui/material/Chip';
import Grid from '@mui/material/Grid2';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { useQuery } from '@tanstack/react-query';
import { api } from '../../lib/api';
import { useDriverLookup } from '../../api/common';
import { PageHeader } from '../../components/PageHeader';
import { StatusChip } from '../../components/StatusChip';
import { VehicleMap, type MapMarker } from '../../components/vehicles/VehicleMap';
import type { Vehicle, VehicleLocationPoint, VehicleStatus } from '../../api/types';

export function LiveTrackingPage() {
  const drivers = useDriverLookup();
  const [selected, setSelected] = useState<number | null>(null);
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');

  const vehicles = useQuery({
    queryKey: ['vehicles'],
    queryFn: async () => (await api.get<Vehicle[]>('/vehicles')).data,
  });

  // Polling today; a provider webhook or an SSE/WebSocket push is the natural
  // next step once real hardware is connected — see docs on VehicleTrackingProvider.
  const status = useQuery({
    queryKey: ['vehicles', 'status'],
    queryFn: async () => (await api.get<VehicleStatus[]>('/vehicles/status')).data,
    refetchInterval: 15_000,
  });

  const showingRoute = !!from && !!to;
  const route = useQuery({
    enabled: !!selected && showingRoute,
    queryKey: ['vehicles', selected, 'route', from, to],
    queryFn: async () =>
      (
        await api.get<VehicleLocationPoint[]>(`/vehicles/${selected}/location/history`, {
          params: { from: new Date(from).toISOString(), to: new Date(to).toISOString(), limit: 1000 },
        })
      ).data,
  });

  const vehicleById = useMemo(() => new Map((vehicles.data ?? []).map((v) => [v.id, v])), [vehicles.data]);
  const statusById = useMemo(() => new Map((status.data ?? []).map((s) => [s.vehicleId, s])), [status.data]);

  const markers: MapMarker[] = (status.data ?? [])
    .filter((s) => s.latitude != null && s.longitude != null)
    .map((s) => ({
      id: s.vehicleId,
      lat: s.latitude as number,
      lng: s.longitude as number,
      label: `${s.code} · ${s.registrationNumber}`,
      online: s.online,
    }));

  const selectedVehicle = selected != null ? vehicleById.get(selected) : undefined;
  const selectedStatus = selected != null ? statusById.get(selected) : undefined;

  return (
    <>
      <PageHeader title="Live Tracking" subtitle="Where every vehicle is right now, and where one has been" />

      <Grid container spacing={2}>
        <Grid size={{ xs: 12, md: 8 }}>
          <Card>
            <VehicleMap
              markers={markers}
              route={showingRoute ? (route.data ?? []).map((p) => ({ lat: p.latitude, lng: p.longitude })) : undefined}
              selectedId={selected ?? undefined}
              onSelect={setSelected}
              height={520}
            />
          </Card>
        </Grid>

        <Grid size={{ xs: 12, md: 4 }}>
          <Card sx={{ mb: 2 }}>
            <CardContent>
              <Typography variant="subtitle2" sx={{ fontWeight: 700, mb: 1 }}>
                Vehicles
              </Typography>
              <Stack spacing={1}>
                {(vehicles.data ?? []).map((v) => {
                  const s = statusById.get(v.id);
                  return (
                    <Box
                      key={v.id}
                      onClick={() => setSelected(v.id)}
                      sx={{
                        p: 1, borderRadius: 1.5, cursor: 'pointer',
                        border: 1, borderColor: v.id === selected ? 'primary.main' : 'divider',
                        bgcolor: v.id === selected ? 'action.selected' : 'transparent',
                      }}
                    >
                      <Stack direction="row" justifyContent="space-between" alignItems="center">
                        <Typography variant="body2" sx={{ fontWeight: 600 }}>
                          {v.code} · {v.vehicleNumber}
                        </Typography>
                        <StatusChip status={s?.online ? 'ACTIVE' : 'INACTIVE'} label={s?.online ? 'Online' : 'Offline'} />
                      </Stack>
                      <Typography variant="caption" color="text.secondary">
                        {drivers.label(v.driverId)}
                        {s?.speedKmph != null && ` · ${s.speedKmph} km/h`}
                      </Typography>
                    </Box>
                  );
                })}
              </Stack>
            </CardContent>
          </Card>

          {selectedVehicle && (
            <Card sx={{ mb: 2 }}>
              <CardContent>
                <Typography variant="subtitle2" sx={{ fontWeight: 700 }}>
                  {selectedVehicle.code} · {selectedVehicle.vehicleNumber}
                </Typography>
                <Stack spacing={0.5} sx={{ mt: 1 }}>
                  <Typography variant="body2">Driver: {drivers.label(selectedVehicle.driverId)}</Typography>
                  <Typography variant="body2">
                    Speed: {selectedStatus?.speedKmph != null ? `${selectedStatus.speedKmph} km/h` : '—'}
                  </Typography>
                  <Typography variant="body2" color="text.secondary">
                    {selectedStatus?.lastFixAt
                      ? `Last update ${new Date(selectedStatus.lastFixAt).toLocaleString()}`
                      : 'No fix received yet'}
                  </Typography>
                </Stack>
              </CardContent>
            </Card>
          )}

          <Card>
            <CardContent>
              <Typography variant="subtitle2" sx={{ fontWeight: 700, mb: 1 }}>
                Route history
              </Typography>
              <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 1 }}>
                Select a vehicle, then a date/time range, to draw its past route.
              </Typography>
              <Stack spacing={1.5}>
                <TextField size="small" type="datetime-local" label="From" InputLabelProps={{ shrink: true }}
                  value={from} onChange={(e) => setFrom(e.target.value)} />
                <TextField size="small" type="datetime-local" label="To" InputLabelProps={{ shrink: true }}
                  value={to} onChange={(e) => setTo(e.target.value)} />
                {showingRoute && (
                  <Chip
                    size="small"
                    onDelete={() => { setFrom(''); setTo(''); }}
                    label={`${(route.data ?? []).length} points · route shown in blue`}
                  />
                )}
                {!selected && <Typography variant="caption" color="warning.main">Pick a vehicle first</Typography>}
                <Button size="small" variant="outlined" component={RouterLink} to={selected ? `/vehicles/${selected}` : '#'}
                  disabled={!selected}>
                  Open vehicle details
                </Button>
              </Stack>
            </CardContent>
          </Card>
        </Grid>
      </Grid>
    </>
  );
}
