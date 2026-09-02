import { Link as RouterLink } from 'react-router-dom';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Grid from '@mui/material/Grid2';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import AddIcon from '@mui/icons-material/Add';
import { useQuery } from '@tanstack/react-query';
import { api } from '../lib/api';
import { litres, money, today } from '../lib/format';
import { PageHeader } from '../components/PageHeader';
import type { SettlementSummary, VehicleStatus } from '../api/types';

interface DailyRow {
  date: string;
  morningLitres: number;
  eveningLitres: number;
  totalLitres: number;
  producerCount: number;
  collectionCount: number;
  averageRate: number;
  totalAmount: number;
}

function Stat({ label, value, hint }: { label: string; value: string; hint?: string }) {
  return (
    <Card sx={{ height: '100%' }}>
      <CardContent>
        <Typography variant="body2" color="text.secondary">
          {label}
        </Typography>
        <Typography variant="h5" sx={{ my: 0.5 }}>
          {value}
        </Typography>
        {hint && (
          <Typography variant="caption" color="text.secondary">
            {hint}
          </Typography>
        )}
      </CardContent>
    </Card>
  );
}

export function DashboardPage() {
  const d = today();
  const daily = useQuery({
    queryKey: ['report', 'daily', d],
    queryFn: async () =>
      (await api.get<DailyRow[]>('/reports/daily-collection', { params: { from: d, to: d } })).data,
  });
  const settlements = useQuery({
    queryKey: ['settlements'],
    queryFn: async () => (await api.get<SettlementSummary[]>('/settlements')).data,
  });
  const vehicles = useQuery({
    queryKey: ['vehicles', 'status'],
    queryFn: async () => (await api.get<VehicleStatus[]>('/vehicles/status')).data,
  });

  const row = daily.data?.[0];
  const latest = settlements.data?.[0];
  const online = vehicles.data?.filter((v) => v.online).length ?? 0;

  return (
    <>
      <PageHeader
        title="Dashboard"
        subtitle={new Date().toDateString()}
        action={
          <Stack direction="row" spacing={1}>
            <Button component={RouterLink} to="/collection" variant="contained" startIcon={<AddIcon />}>
              New Collection
            </Button>
            <Button component={RouterLink} to="/settlements" variant="outlined">
              Weekly Settlement
            </Button>
          </Stack>
        }
      />
      <Grid container spacing={2}>
        <Grid size={{ xs: 6, md: 3 }}>
          <Stat label="Morning litres" value={litres(row?.morningLitres)} />
        </Grid>
        <Grid size={{ xs: 6, md: 3 }}>
          <Stat label="Evening litres" value={litres(row?.eveningLitres)} />
        </Grid>
        <Grid size={{ xs: 6, md: 3 }}>
          <Stat label="Total today" value={litres(row?.totalLitres)} hint={`${row?.collectionCount ?? 0} collections`} />
        </Grid>
        <Grid size={{ xs: 6, md: 3 }}>
          <Stat label="Amount today" value={money(row?.totalAmount)} hint={`avg ₹${(row?.averageRate ?? 0).toFixed(2)}/L`} />
        </Grid>
        <Grid size={{ xs: 6, md: 3 }}>
          <Stat label="Farmers today" value={String(row?.producerCount ?? 0)} />
        </Grid>
        <Grid size={{ xs: 6, md: 3 }}>
          <Stat
            label="Latest settlement"
            value={latest ? money(latest.totalNetPayable) : '—'}
            hint={latest ? `${latest.settlementNumber} · ${latest.status}` : 'none yet'}
          />
        </Grid>
        <Grid size={{ xs: 6, md: 3 }}>
          <Stat label="Vehicles online" value={`${online}/${vehicles.data?.length ?? 0}`} />
        </Grid>
      </Grid>
    </>
  );
}
