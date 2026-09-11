import { useState } from 'react';
import { useParams } from 'react-router-dom';
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
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import VideocamOffOutlinedIcon from '@mui/icons-material/VideocamOffOutlined';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../../lib/api';
import { useDriverLookup } from '../../api/common';
import { PageHeader } from '../../components/PageHeader';
import { StatusChip } from '../../components/StatusChip';
import { ConfirmDialog, EmptyState, QueryBoundary, TableSkeleton, useToast } from '../../components/ui';
import { useAuth } from '../../lib/auth';
import type {
  LiveStreamInfo, Vehicle, VehicleAlert, VehicleCamera, VehicleDevice, VehicleDeviceAssignment,
  VehicleDriverAssignment, VehicleLocationPoint, VehicleRecording, VehicleTrip,
} from '../../api/types';

const TABS = ['Overview', 'Devices', 'Cameras', 'Trips', 'Alerts'] as const;

export function VehicleDetailPage() {
  const { vehicleId } = useParams();
  const vid = Number(vehicleId);
  const toast = useToast();
  const qc = useQueryClient();
  const { can } = useAuth();
  const drivers = useDriverLookup();
  const [tab, setTab] = useState(0);

  const canManage = can('MANAGE_VEHICLES') || can('ALL');
  const canManageDevices = can('MANAGE_DEVICE_ASSIGNMENT') || canManage;

  const vehicle = useQuery({
    queryKey: ['vehicles', vid],
    queryFn: async () => (await api.get<Vehicle>(`/vehicles/${vid}`)).data,
  });
  const location = useQuery({
    queryKey: ['vehicles', vid, 'location'],
    queryFn: async () => {
      try {
        return (await api.get<VehicleLocationPoint>(`/vehicles/${vid}/location`)).data;
      } catch {
        return null;
      }
    },
    refetchInterval: 15_000,
  });
  const driverHistory = useQuery({
    queryKey: ['vehicles', vid, 'driver-history'],
    queryFn: async () => (await api.get<VehicleDriverAssignment[]>(`/vehicles/${vid}/driver/history`)).data,
  });

  const invalidateVehicle = () => {
    qc.invalidateQueries({ queryKey: ['vehicles', vid] });
    qc.invalidateQueries({ queryKey: ['vehicles', vid, 'driver-history'] });
    qc.invalidateQueries({ queryKey: ['vehicles'] });
  };

  const statusChange = useMutation({
    mutationFn: async (status: string) => (await api.patch(`/vehicles/${vid}/status`, { status })).data,
    onSuccess: () => { toast.success('Status updated'); invalidateVehicle(); },
    onError: toast.fromError,
  });

  const [driverDialog, setDriverDialog] = useState(false);
  const [driverPick, setDriverPick] = useState('');
  const assignDriver = useMutation({
    mutationFn: async () => (await api.post(`/vehicles/${vid}/driver/assign`, { driverId: Number(driverPick) })).data,
    onSuccess: () => { toast.success('Driver assigned'); invalidateVehicle(); setDriverDialog(false); setDriverPick(''); },
    onError: toast.fromError,
  });
  const [confirmUnassign, setConfirmUnassign] = useState(false);
  const unassignDriver = useMutation({
    mutationFn: async () => (await api.post(`/vehicles/${vid}/driver/unassign`)).data,
    onSuccess: () => { toast.success('Driver unassigned'); invalidateVehicle(); setConfirmUnassign(false); },
    onError: toast.fromError,
  });

  if (!vehicleId || Number.isNaN(vid)) {
    return <EmptyState title="Vehicle not found" />;
  }

  return (
    <>
      <PageHeader
        title={vehicle.data ? `${vehicle.data.code} · ${vehicle.data.vehicleNumber}` : 'Vehicle'}
        subtitle="Vehicle information, tracking, cameras, trips and alerts"
        action={
          vehicle.data && canManage && (
            <TextField select size="small" label="Status" value={vehicle.data.status}
              onChange={(e) => statusChange.mutate(e.target.value)} sx={{ width: 160 }}>
              <MenuItem value="ACTIVE">Active</MenuItem>
              <MenuItem value="INACTIVE">Inactive</MenuItem>
              <MenuItem value="MAINTENANCE">Maintenance</MenuItem>
            </TextField>
          )
        }
      />

      <QueryBoundary query={vehicle} skeleton={<TableSkeleton cols={4} />}>
        {(v) => (
          <>
            <Tabs value={tab} onChange={(_, t) => setTab(t)} sx={{ mb: 2 }}>
              {TABS.map((t) => <Tab key={t} label={t} />)}
            </Tabs>

            {tab === 0 && (
              <Grid container spacing={2}>
                <Grid size={{ xs: 12, md: 6 }}>
                  <Card sx={{ mb: 2 }}>
                    <CardContent>
                      <Typography variant="subtitle2" sx={{ fontWeight: 700, mb: 1 }}>Vehicle information</Typography>
                      <Stack spacing={0.5}>
                        <Row k="Type" v={v.vehicleType.replace(/_/g, ' ')} />
                        <Row k="Make / model" v={[v.make, v.model].filter(Boolean).join(' ') || '—'} />
                        <Row k="Model year" v={v.modelYear ?? '—'} />
                        <Row k="Capacity" v={v.capacityLitres != null ? `${v.capacityLitres} L` : '—'} />
                        <Row k="Registration date" v={v.registrationDate ?? '—'} />
                        <Row k="Insurance expiry" v={v.insuranceExpiry ?? '—'} />
                        <Row k="Pollution (PUC) expiry" v={v.pollutionExpiry ?? '—'} />
                        {v.notes && <Row k="Notes" v={v.notes} />}
                      </Stack>
                    </CardContent>
                  </Card>

                  <Card>
                    <CardContent>
                      <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 1 }}>
                        <Typography variant="subtitle2" sx={{ fontWeight: 700 }}>Current driver</Typography>
                        {canManage && (
                          v.driverId ? (
                            <Button size="small" color="warning" onClick={() => setConfirmUnassign(true)}>Unassign</Button>
                          ) : (
                            <Button size="small" onClick={() => setDriverDialog(true)}>Assign driver</Button>
                          )
                        )}
                      </Stack>
                      <Typography variant="body2">{drivers.label(v.driverId)}</Typography>
                      <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 1.5, mb: 0.5 }}>
                        History
                      </Typography>
                      <Stack spacing={0.5}>
                        {(driverHistory.data ?? []).map((a) => (
                          <Typography key={a.id} variant="caption" color="text.secondary">
                            {drivers.label(a.driverId)} · {a.assignedFrom} → {a.assignedTo ?? 'present'}
                          </Typography>
                        ))}
                      </Stack>
                    </CardContent>
                  </Card>
                </Grid>

                <Grid size={{ xs: 12, md: 6 }}>
                  <Card>
                    <CardContent>
                      <Typography variant="subtitle2" sx={{ fontWeight: 700, mb: 1 }}>Current location</Typography>
                      {location.data ? (
                        <Stack spacing={0.5}>
                          <Row k="Coordinates" v={`${location.data.latitude.toFixed(5)}, ${location.data.longitude.toFixed(5)}`} />
                          <Row k="Speed" v={location.data.speedKmph != null ? `${location.data.speedKmph} km/h` : '—'} />
                          <Row k="Ignition" v={location.data.ignitionStatus ?? '—'} />
                          <Row k="Motion" v={location.data.motionStatus ?? '—'} />
                          <Row k="Last update" v={new Date(location.data.providerTimestamp).toLocaleString()} />
                        </Stack>
                      ) : (
                        <Typography variant="body2" color="text.secondary">
                          No location has been received yet for this vehicle.
                        </Typography>
                      )}
                    </CardContent>
                  </Card>
                </Grid>
              </Grid>
            )}

            {tab === 1 && <DevicesTab vehicleId={vid} canManage={canManageDevices} />}
            {tab === 2 && <CamerasTab vehicleId={vid} canManage={canManage} />}
            {tab === 3 && <TripsTab vehicleId={vid} />}
            {tab === 4 && <AlertsTab vehicleId={vid} />}
          </>
        )}
      </QueryBoundary>

      <Dialog open={driverDialog} onClose={() => setDriverDialog(false)} fullWidth maxWidth="xs">
        <DialogTitle>Assign driver</DialogTitle>
        <DialogContent>
          <TextField select fullWidth label="Driver" sx={{ mt: 1 }} value={driverPick}
            onChange={(e) => setDriverPick(e.target.value)}>
            {(drivers.data ?? []).map((d) => (
              <MenuItem key={d.id} value={d.id}>{d.code} · {d.name}</MenuItem>
            ))}
          </TextField>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDriverDialog(false)}>Cancel</Button>
          <Button variant="contained" disabled={!driverPick || assignDriver.isPending} onClick={() => assignDriver.mutate()}>
            Assign
          </Button>
        </DialogActions>
      </Dialog>

      <ConfirmDialog
        open={confirmUnassign}
        title="Unassign driver"
        message="This driver will no longer be linked to this vehicle. Past trips keep the driver who was assigned at the time."
        confirmLabel="Unassign"
        busy={unassignDriver.isPending}
        onConfirm={() => unassignDriver.mutate()}
        onClose={() => setConfirmUnassign(false)}
      />
    </>
  );
}

function Row({ k, v }: { k: string; v: React.ReactNode }) {
  return (
    <Stack direction="row" justifyContent="space-between">
      <Typography variant="body2" color="text.secondary">{k}</Typography>
      <Typography variant="body2" sx={{ fontWeight: 600 }}>{v}</Typography>
    </Stack>
  );
}

/* --------------------------------------------------------------- devices */

function DevicesTab({ vehicleId, canManage }: { vehicleId: number; canManage: boolean }) {
  const toast = useToast();
  const qc = useQueryClient();
  const [attachOpen, setAttachOpen] = useState(false);
  const [detachTarget, setDetachTarget] = useState<VehicleDevice | null>(null);
  const [pickDeviceId, setPickDeviceId] = useState('');
  const [installDate, setInstallDate] = useState(new Date().toISOString().slice(0, 10));
  const [removeDate, setRemoveDate] = useState(new Date().toISOString().slice(0, 10));
  const [remarks, setRemarks] = useState('');

  const current = useQuery({
    queryKey: ['vehicles', vehicleId, 'devices'],
    queryFn: async () => (await api.get<VehicleDevice[]>(`/vehicles/${vehicleId}/devices`)).data,
  });
  const history = useQuery({
    queryKey: ['vehicles', vehicleId, 'devices', 'history'],
    queryFn: async () => (await api.get<VehicleDeviceAssignment[]>(`/vehicles/${vehicleId}/devices/history`)).data,
  });
  const available = useQuery({
    enabled: attachOpen,
    queryKey: ['vehicles', 'devices', 'available'],
    queryFn: async () => (await api.get<VehicleDevice[]>('/vehicles/devices/available')).data,
  });

  const invalidate = () => {
    qc.invalidateQueries({ queryKey: ['vehicles', vehicleId, 'devices'] });
    qc.invalidateQueries({ queryKey: ['vehicles', vehicleId, 'devices', 'history'] });
    qc.invalidateQueries({ queryKey: ['vehicles', 'devices', 'available'] });
  };

  const attach = useMutation({
    mutationFn: async () =>
      (await api.post(`/vehicles/${vehicleId}/devices/assign`, {
        deviceId: Number(pickDeviceId), installationDate: installDate, primary: true, remarks: remarks || undefined,
      })).data,
    onSuccess: () => { toast.success('Device attached'); invalidate(); setAttachOpen(false); setPickDeviceId(''); setRemarks(''); },
    onError: toast.fromError,
  });
  const detach = useMutation({
    mutationFn: async () =>
      (await api.post(`/vehicles/${vehicleId}/devices/${detachTarget!.id}/detach`, {
        removalDate: removeDate, remarks: remarks || undefined,
      })).data,
    onSuccess: () => { toast.success('Device detached'); invalidate(); setDetachTarget(null); setRemarks(''); },
    onError: toast.fromError,
  });

  return (
    <Grid container spacing={2}>
      <Grid size={12}>
        <Card sx={{ mb: 2 }}>
          <CardContent>
            <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 1 }}>
              <Typography variant="subtitle2" sx={{ fontWeight: 700 }}>Attached devices</Typography>
              {canManage && <Button size="small" onClick={() => setAttachOpen(true)}>Attach device</Button>}
            </Stack>
            <QueryBoundary query={current} skeleton={<TableSkeleton cols={5} />}
              empty={(d) => d.length === 0}
              emptyState={<EmptyState title="No device attached" description="Attach a GPS/camera device to start tracking this vehicle." />}>
              {(list) => (
                <TableContainer>
                  <Table size="small">
                    <TableHead>
                      <TableRow>
                        <TableCell>Type</TableCell>
                        <TableCell>Model / IMEI</TableCell>
                        <TableCell>Installed</TableCell>
                        <TableCell>Primary</TableCell>
                        <TableCell>Status</TableCell>
                        <TableCell align="right" />
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {list.map((d) => (
                        <TableRow key={d.id}>
                          <TableCell>{d.deviceType.replace(/_/g, ' ')}</TableCell>
                          <TableCell>{[d.deviceModel, d.imei].filter(Boolean).join(' · ') || d.externalDeviceId}</TableCell>
                          <TableCell>{d.installationDate ?? '—'}</TableCell>
                          <TableCell>{d.primary ? <Chip size="small" label="Primary" color="primary" /> : '—'}</TableCell>
                          <TableCell><StatusChip status={d.status} /></TableCell>
                          <TableCell align="right">
                            {canManage && (
                              <Button size="small" color="warning" onClick={() => setDetachTarget(d)}>Detach</Button>
                            )}
                          </TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </TableContainer>
              )}
            </QueryBoundary>
          </CardContent>
        </Card>

        <Card>
          <CardContent>
            <Typography variant="subtitle2" sx={{ fontWeight: 700, mb: 1 }}>Assignment history</Typography>
            <Stack spacing={0.5}>
              {(history.data ?? []).map((a) => (
                <Typography key={a.id} variant="caption" color="text.secondary">
                  Device #{a.deviceId} · {a.assignedFrom} → {a.assignedTo ?? 'present'}
                  {a.remarks && ` — ${a.remarks}`}
                </Typography>
              ))}
              {(history.data ?? []).length === 0 && (
                <Typography variant="caption" color="text.secondary">No history yet.</Typography>
              )}
            </Stack>
          </CardContent>
        </Card>
      </Grid>

      <Dialog open={attachOpen} onClose={() => setAttachOpen(false)} fullWidth maxWidth="xs">
        <DialogTitle>Attach a device</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }}>
            <TextField select label="Available device" value={pickDeviceId} onChange={(e) => setPickDeviceId(e.target.value)}>
              {(available.data ?? []).map((d) => (
                <MenuItem key={d.id} value={d.id}>
                  {d.deviceType} · {d.externalDeviceId}{d.imei ? ` · IMEI ${d.imei}` : ''}
                </MenuItem>
              ))}
            </TextField>
            {(available.data ?? []).length === 0 && (
              <Typography variant="caption" color="text.secondary">
                No unattached devices. Register one first from Vehicle Master.
              </Typography>
            )}
            <TextField type="date" label="Installation date" InputLabelProps={{ shrink: true }}
              value={installDate} onChange={(e) => setInstallDate(e.target.value)} />
            <TextField label="Remarks (optional)" value={remarks} onChange={(e) => setRemarks(e.target.value)} />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setAttachOpen(false)}>Cancel</Button>
          <Button variant="contained" disabled={!pickDeviceId || attach.isPending} onClick={() => attach.mutate()}>
            Attach
          </Button>
        </DialogActions>
      </Dialog>

      <Dialog open={!!detachTarget} onClose={() => setDetachTarget(null)} fullWidth maxWidth="xs">
        <DialogTitle>Detach device</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }}>
            <Typography variant="body2">
              {detachTarget?.deviceType} · {detachTarget?.externalDeviceId}
            </Typography>
            <TextField type="date" label="Removal date" InputLabelProps={{ shrink: true }}
              value={removeDate} onChange={(e) => setRemoveDate(e.target.value)} />
            <TextField label="Reason / remarks" value={remarks} onChange={(e) => setRemarks(e.target.value)} />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDetachTarget(null)}>Cancel</Button>
          <Button variant="contained" color="warning" disabled={detach.isPending} onClick={() => detach.mutate()}>
            Detach
          </Button>
        </DialogActions>
      </Dialog>
    </Grid>
  );
}

/* --------------------------------------------------------------- cameras */

const CAMERA_TYPES = ['FRONT', 'CABIN', 'REAR', 'SIDE', 'OTHER'];

function CamerasTab({ vehicleId, canManage }: { vehicleId: number; canManage: boolean }) {
  const toast = useToast();
  const qc = useQueryClient();
  const [regOpen, setRegOpen] = useState(false);
  const [deviceId, setDeviceId] = useState('');
  const [cameraType, setCameraType] = useState('FRONT');
  const [cameraName, setCameraName] = useState('');

  const cameras = useQuery({
    queryKey: ['vehicles', vehicleId, 'cameras'],
    queryFn: async () => (await api.get<VehicleCamera[]>(`/vehicles/${vehicleId}/cameras`)).data,
  });
  const devices = useQuery({
    enabled: regOpen,
    queryKey: ['vehicles', vehicleId, 'devices'],
    queryFn: async () => (await api.get<VehicleDevice[]>(`/vehicles/${vehicleId}/devices`)).data,
  });

  const register = useMutation({
    mutationFn: async () =>
      (await api.post(`/vehicles/${vehicleId}/cameras`, {
        deviceId: Number(deviceId), cameraType, cameraName: cameraName || undefined,
      })).data,
    onSuccess: () => {
      toast.success('Camera registered');
      qc.invalidateQueries({ queryKey: ['vehicles', vehicleId, 'cameras'] });
      setRegOpen(false); setDeviceId(''); setCameraName('');
    },
    onError: toast.fromError,
  });

  return (
    <Card>
      <CardContent>
        <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 1 }}>
          <Typography variant="subtitle2" sx={{ fontWeight: 700 }}>Cameras</Typography>
          {canManage && <Button size="small" onClick={() => setRegOpen(true)}>Register camera</Button>}
        </Stack>
        <QueryBoundary query={cameras} skeleton={<TableSkeleton cols={3} />}
          empty={(d) => d.length === 0}
          emptyState={<EmptyState icon={<VideocamOffOutlinedIcon />} title="No cameras registered"
            description="Register a camera channel on this vehicle's device to view it here." />}>
          {(list) => (
            <Grid container spacing={2}>
              {list.map((c) => <CameraCard key={c.id} vehicleId={vehicleId} camera={c} />)}
            </Grid>
          )}
        </QueryBoundary>
      </CardContent>

      <Dialog open={regOpen} onClose={() => setRegOpen(false)} fullWidth maxWidth="xs">
        <DialogTitle>Register a camera</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }}>
            <TextField select label="Device" value={deviceId} onChange={(e) => setDeviceId(e.target.value)}>
              {(devices.data ?? []).map((d) => (
                <MenuItem key={d.id} value={d.id}>{d.deviceType} · {d.externalDeviceId}</MenuItem>
              ))}
            </TextField>
            <TextField select label="Camera type" value={cameraType} onChange={(e) => setCameraType(e.target.value)}>
              {CAMERA_TYPES.map((t) => <MenuItem key={t} value={t}>{t}</MenuItem>)}
            </TextField>
            <TextField label="Name (optional)" value={cameraName} onChange={(e) => setCameraName(e.target.value)} />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setRegOpen(false)}>Cancel</Button>
          <Button variant="contained" disabled={!deviceId || register.isPending} onClick={() => register.mutate()}>
            Register
          </Button>
        </DialogActions>
      </Dialog>
    </Card>
  );
}

function CameraCard({ vehicleId, camera }: { vehicleId: number; camera: VehicleCamera }) {
  const [viewing, setViewing] = useState(false);
  const live = useQuery({
    enabled: viewing,
    queryKey: ['vehicles', vehicleId, 'cameras', camera.id, 'live'],
    queryFn: async () => (await api.get<LiveStreamInfo>(`/vehicles/${vehicleId}/cameras/${camera.id}/live`)).data,
  });
  const recordings = useQuery({
    enabled: viewing,
    queryKey: ['vehicles', vehicleId, 'cameras', camera.id, 'recordings'],
    queryFn: async () => (await api.get<VehicleRecording[]>(`/vehicles/${vehicleId}/cameras/${camera.id}/recordings`)).data,
  });

  return (
    <Grid size={{ xs: 12, sm: 6 }}>
      <Card variant="outlined">
        <CardContent>
          <Stack direction="row" justifyContent="space-between" alignItems="center">
            <Typography variant="body2" sx={{ fontWeight: 700 }}>
              {camera.cameraName || camera.cameraType}
            </Typography>
            <StatusChip status={camera.status} />
          </Stack>
          <Button size="small" sx={{ mt: 1 }} onClick={() => setViewing((v) => !v)}>
            {viewing ? 'Hide' : 'View'}
          </Button>
          {viewing && (
            <Box sx={{ mt: 1.5 }}>
              {live.isLoading ? (
                <Typography variant="caption" color="text.secondary">Checking…</Typography>
              ) : live.data?.available ? (
                <video src={live.data.url} controls autoPlay muted style={{ width: '100%', borderRadius: 8 }} />
              ) : (
                <Box sx={{ p: 2, textAlign: 'center', bgcolor: 'action.hover', borderRadius: 2 }}>
                  <VideocamOffOutlinedIcon color="disabled" />
                  <Typography variant="caption" color="text.secondary" display="block">
                    Camera offline — no live stream available. Connect a camera provider to enable this.
                  </Typography>
                </Box>
              )}
              <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 1 }}>
                {(recordings.data ?? []).length > 0
                  ? `${recordings.data!.length} recording(s) available`
                  : 'No recordings available'}
              </Typography>
            </Box>
          )}
        </CardContent>
      </Card>
    </Grid>
  );
}

/* ----------------------------------------------------------------- trips */

function TripsTab({ vehicleId }: { vehicleId: number }) {
  const trips = useQuery({
    queryKey: ['vehicles', vehicleId, 'trips'],
    queryFn: async () => (await api.get<VehicleTrip[]>(`/vehicles/${vehicleId}/trips`)).data,
  });

  return (
    <Card>
      <QueryBoundary query={trips} skeleton={<TableSkeleton cols={6} />}
        empty={(d) => d.length === 0}
        emptyState={<EmptyState title="No trips yet" description="Trips are detected automatically from telemetry once the vehicle moves." />}>
        {(list) => (
          <TableContainer>
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell>Started</TableCell>
                  <TableCell>Ended</TableCell>
                  <TableCell align="right">Distance</TableCell>
                  <TableCell align="right">Duration</TableCell>
                  <TableCell align="right">Max speed</TableCell>
                  <TableCell>Status</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {list.map((t) => (
                  <TableRow key={t.id} hover>
                    <TableCell>{new Date(t.startedAt).toLocaleString()}</TableCell>
                    <TableCell>{t.endedAt ? new Date(t.endedAt).toLocaleString() : '—'}</TableCell>
                    <TableCell align="right">{t.distanceKm != null ? `${t.distanceKm} km` : '—'}</TableCell>
                    <TableCell align="right">
                      {t.durationSeconds != null ? `${Math.round(t.durationSeconds / 60)} min` : '—'}
                    </TableCell>
                    <TableCell align="right">{t.maxSpeedKmph != null ? `${t.maxSpeedKmph} km/h` : '—'}</TableCell>
                    <TableCell><StatusChip status={t.status} /></TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        )}
      </QueryBoundary>
    </Card>
  );
}

/* ---------------------------------------------------------------- alerts */

function AlertsTab({ vehicleId }: { vehicleId: number }) {
  const toast = useToast();
  const qc = useQueryClient();
  const alerts = useQuery({
    queryKey: ['vehicle-alerts', 'byVehicle', vehicleId],
    queryFn: async () => (await api.get<VehicleAlert[]>('/vehicles/alerts', { params: { vehicleId } })).data,
  });
  const ack = useMutation({
    mutationFn: async (id: number) => (await api.post(`/vehicles/alerts/${id}/acknowledge`)).data,
    onSuccess: () => {
      toast.success('Alert acknowledged');
      qc.invalidateQueries({ queryKey: ['vehicle-alerts'] });
    },
    onError: toast.fromError,
  });

  return (
    <Card>
      <QueryBoundary query={alerts} skeleton={<TableSkeleton cols={4} />}
        empty={(d) => d.length === 0}
        emptyState={<EmptyState icon={<CheckCircleIcon color="success" />} title="No alerts for this vehicle" />}>
        {(list) => (
          <TableContainer>
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell>Type</TableCell>
                  <TableCell>Message</TableCell>
                  <TableCell>Occurred</TableCell>
                  <TableCell>Status</TableCell>
                  <TableCell align="right" />
                </TableRow>
              </TableHead>
              <TableBody>
                {list.map((a) => (
                  <TableRow key={a.id} hover>
                    <TableCell>{a.alertType.replace(/_/g, ' ')}</TableCell>
                    <TableCell>{a.message}</TableCell>
                    <TableCell>{new Date(a.occurredAt).toLocaleString()}</TableCell>
                    <TableCell><StatusChip status={a.status === 'OPEN' ? 'PENDING' : 'ACTIVE'} label={a.status} /></TableCell>
                    <TableCell align="right">
                      {a.status === 'OPEN' && (
                        <Button size="small" disabled={ack.isPending} onClick={() => ack.mutate(a.id)}>Acknowledge</Button>
                      )}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        )}
      </QueryBoundary>
    </Card>
  );
}
