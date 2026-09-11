import { useState } from 'react';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import MenuItem from '@mui/material/MenuItem';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableContainer from '@mui/material/TableContainer';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import TextField from '@mui/material/TextField';
import WarningAmberOutlinedIcon from '@mui/icons-material/WarningAmberOutlined';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../../lib/api';
import { PageHeader } from '../../components/PageHeader';
import { StatusChip } from '../../components/StatusChip';
import { EmptyState, QueryBoundary, TableSkeleton, useToast } from '../../components/ui';
import type { Vehicle, VehicleAlert } from '../../api/types';

export function VehicleAlertsPage() {
  const toast = useToast();
  const qc = useQueryClient();
  const [status, setStatus] = useState('OPEN');

  const vehicles = useQuery({
    queryKey: ['vehicles'],
    queryFn: async () => (await api.get<Vehicle[]>('/vehicles')).data,
  });
  const vehicleLabel = (id: number) => vehicles.data?.find((v) => v.id === id)?.vehicleNumber ?? `#${id}`;

  const alerts = useQuery({
    queryKey: ['vehicle-alerts', status],
    queryFn: async () =>
      (await api.get<VehicleAlert[]>('/vehicles/alerts', { params: status ? { status } : {} })).data,
    refetchInterval: 30_000,
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
    <>
      <PageHeader title="Vehicle Alerts" subtitle="Overspeed, device-offline, and other conditions worth a look" />

      <Card sx={{ mb: 2 }}>
        <CardContent>
          <TextField select size="small" label="Status" value={status} onChange={(e) => setStatus(e.target.value)}
            sx={{ width: 200 }}>
            <MenuItem value="OPEN">Open</MenuItem>
            <MenuItem value="ACKNOWLEDGED">Acknowledged</MenuItem>
            <MenuItem value="">All</MenuItem>
          </TextField>
        </CardContent>
      </Card>

      <Card>
        <QueryBoundary
          query={alerts}
          skeleton={<TableSkeleton cols={6} />}
          empty={(data) => data.length === 0}
          emptyState={
            <EmptyState icon={<WarningAmberOutlinedIcon />} title="No alerts"
              description="Overspeed and device-offline conditions will show up here as they happen." />
          }
        >
          {(list) => (
            <TableContainer>
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell>Vehicle</TableCell>
                    <TableCell>Type</TableCell>
                    <TableCell>Severity</TableCell>
                    <TableCell>Message</TableCell>
                    <TableCell>Occurred</TableCell>
                    <TableCell>Status</TableCell>
                    <TableCell align="right" />
                  </TableRow>
                </TableHead>
                <TableBody>
                  {list.map((a) => (
                    <TableRow key={a.id} hover>
                      <TableCell sx={{ fontWeight: 600 }}>{vehicleLabel(a.vehicleId)}</TableCell>
                      <TableCell>{a.alertType.replace(/_/g, ' ')}</TableCell>
                      <TableCell>
                        <StatusChip
                          status={a.severity === 'CRITICAL' ? 'FAILED' : a.severity === 'WARNING' ? 'PENDING' : 'ACTIVE'}
                          label={a.severity}
                        />
                      </TableCell>
                      <TableCell>{a.message}</TableCell>
                      <TableCell>{new Date(a.occurredAt).toLocaleString()}</TableCell>
                      <TableCell>
                        <StatusChip status={a.status === 'OPEN' ? 'PENDING' : 'ACTIVE'} label={a.status} />
                      </TableCell>
                      <TableCell align="right">
                        {a.status === 'OPEN' && (
                          <Button size="small" disabled={ack.isPending} onClick={() => ack.mutate(a.id)}>
                            Acknowledge
                          </Button>
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
    </>
  );
}
