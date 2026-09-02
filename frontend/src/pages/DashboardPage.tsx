import type { ReactNode } from 'react';
import { Link as RouterLink } from 'react-router-dom';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Grid from '@mui/material/Grid2';
import Stack from '@mui/material/Stack';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import Typography from '@mui/material/Typography';
import AddIcon from '@mui/icons-material/Add';
import WbSunnyOutlinedIcon from '@mui/icons-material/WbSunnyOutlined';
import Brightness3OutlinedIcon from '@mui/icons-material/Brightness3Outlined';
import LocalDrinkOutlinedIcon from '@mui/icons-material/LocalDrinkOutlined';
import PeopleOutlinedIcon from '@mui/icons-material/PeopleOutlined';
import PaymentsOutlinedIcon from '@mui/icons-material/PaymentsOutlined';
import ReceiptLongOutlinedIcon from '@mui/icons-material/ReceiptLongOutlined';
import LocalShippingOutlinedIcon from '@mui/icons-material/LocalShippingOutlined';
import { useQuery } from '@tanstack/react-query';
import { api } from '../lib/api';
import { litres, money, today } from '../lib/format';
import { PageHeader } from '../components/PageHeader';
import { StatusChip } from '../components/StatusChip';
import { CardGridSkeleton, EmptyState, SectionTitle } from '../components/ui';
import type { Collection, Page, SettlementSummary, VehicleStatus } from '../api/types';

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

const OPEN_SETTLEMENT = ['DRAFT', 'CALCULATED', 'REVIEWED', 'APPROVED', 'PAYOUT_INITIATED', 'PARTIALLY_PAID', 'FAILED'];

function daysAgo(n: number): string {
  const d = new Date();
  d.setDate(d.getDate() - n);
  return d.toISOString().slice(0, 10);
}

function Kpi({
  label,
  value,
  hint,
  icon,
  to,
}: {
  label: string;
  value: string;
  hint?: string;
  icon: ReactNode;
  to?: string;
}) {
  return (
    <Card
      {...(to ? { component: RouterLink, to } : {})}
      sx={{
        height: '100%',
        display: 'block',
        textDecoration: 'none',
        transition: 'border-color .15s, box-shadow .15s',
        ...(to && { '&:hover': { borderColor: 'primary.main', boxShadow: '0 2px 10px rgba(23,35,30,.06)' } }),
      }}
    >
      <CardContent>
        <Stack direction="row" justifyContent="space-between" alignItems="flex-start">
          <Typography variant="body2" color="text.secondary" sx={{ fontSize: 13, fontWeight: 500 }}>
            {label}
          </Typography>
          <Box sx={{ color: 'primary.main', display: 'flex', '& svg': { fontSize: 24 } }}>{icon}</Box>
        </Stack>
        <Typography sx={{ fontSize: 26, fontWeight: 700, lineHeight: 1.2, mt: 0.5, color: 'text.primary' }}>
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

function WeekTrend({ rows }: { rows: DailyRow[] }) {
  const max = Math.max(1, ...rows.map((r) => r.totalLitres));
  return (
    <Card>
      <CardContent>
        <Stack direction="row" alignItems="flex-end" spacing={1.5} sx={{ height: 140 }}>
          {rows.map((r) => (
            <Stack key={r.date} alignItems="center" spacing={0.75} sx={{ flex: 1, minWidth: 0 }}>
              <Typography variant="caption" color="text.secondary" noWrap sx={{ fontSize: 11 }}>
                {litres(r.totalLitres).replace(' L', '')}
              </Typography>
              <Box
                sx={{
                  width: '100%',
                  maxWidth: 44,
                  height: `${(r.totalLitres / max) * 96 + 4}px`,
                  bgcolor: 'primary.light',
                  border: 1,
                  borderColor: 'primary.main',
                  borderRadius: 1.5,
                }}
              />
              <Typography variant="caption" color="text.secondary" sx={{ fontSize: 11 }}>
                {new Date(r.date).toLocaleDateString('en-IN', { weekday: 'short' })}
              </Typography>
            </Stack>
          ))}
        </Stack>
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
  const week = useQuery({
    queryKey: ['report', 'daily', 'week', d],
    queryFn: async () =>
      (await api.get<DailyRow[]>('/reports/daily-collection', { params: { from: daysAgo(6), to: d } })).data,
  });
  const settlements = useQuery({
    queryKey: ['settlements'],
    queryFn: async () => (await api.get<SettlementSummary[]>('/settlements')).data,
  });
  const vehicles = useQuery({
    queryKey: ['vehicles', 'status'],
    queryFn: async () => (await api.get<VehicleStatus[]>('/vehicles/status')).data,
  });
  const recent = useQuery({
    queryKey: ['collections', { date: d, dash: true }],
    queryFn: async () =>
      (await api.get<Page<Collection>>('/collections', { params: { date: d, size: 8 } })).data,
  });

  const row = daily.data?.[0];
  const latest = settlements.data?.[0];
  const openCount = settlements.data?.filter((s) => OPEN_SETTLEMENT.includes(s.status)).length ?? 0;
  const online = vehicles.data?.filter((v) => v.online).length ?? 0;

  return (
    <>
      <PageHeader
        title="Dashboard"
        subtitle={new Date().toLocaleDateString('en-IN', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' })}
        action={
          <Stack direction="row" spacing={1}>
            <Button component={RouterLink} to="/collection" variant="contained" startIcon={<AddIcon />}>
              Record collection
            </Button>
            <Button component={RouterLink} to="/settlements" variant="outlined">
              Settlement
            </Button>
          </Stack>
        }
      />

      {daily.isLoading ? (
        <CardGridSkeleton count={8} />
      ) : (
        <Grid container spacing={2}>
          <Grid size={{ xs: 6, sm: 4, md: 3 }}>
            <Kpi label="Morning collection" value={litres(row?.morningLitres)} icon={<WbSunnyOutlinedIcon />} />
          </Grid>
          <Grid size={{ xs: 6, sm: 4, md: 3 }}>
            <Kpi label="Evening collection" value={litres(row?.eveningLitres)} icon={<Brightness3OutlinedIcon />} />
          </Grid>
          <Grid size={{ xs: 6, sm: 4, md: 3 }}>
            <Kpi
              label="Total today"
              value={litres(row?.totalLitres)}
              hint={`${row?.collectionCount ?? 0} collections`}
              icon={<LocalDrinkOutlinedIcon />}
            />
          </Grid>
          <Grid size={{ xs: 6, sm: 4, md: 3 }}>
            <Kpi label="Producers today" value={String(row?.producerCount ?? 0)} icon={<PeopleOutlinedIcon />} />
          </Grid>
          <Grid size={{ xs: 6, sm: 4, md: 3 }}>
            <Kpi
              label="Amount today"
              value={money(row?.totalAmount)}
              hint={`avg ₹${(row?.averageRate ?? 0).toFixed(2)}/L`}
              icon={<PaymentsOutlinedIcon />}
            />
          </Grid>
          <Grid size={{ xs: 6, sm: 4, md: 3 }}>
            <Kpi
              label="Open settlements"
              value={String(openCount)}
              hint={latest ? `latest ${latest.settlementNumber}` : 'none yet'}
              icon={<ReceiptLongOutlinedIcon />}
              to="/settlements"
            />
          </Grid>
          <Grid size={{ xs: 6, sm: 4, md: 3 }}>
            <Kpi
              label="Vehicles online"
              value={`${online}/${vehicles.data?.length ?? 0}`}
              icon={<LocalShippingOutlinedIcon />}
              to="/vehicles"
            />
          </Grid>
          <Grid size={{ xs: 6, sm: 4, md: 3 }}>
            <Kpi
              label="Latest payout status"
              value={latest ? latest.status.replace(/_/g, ' ') : '—'}
              hint={latest ? money(latest.totalNetPayable) : undefined}
              icon={<PaymentsOutlinedIcon />}
              to={latest ? `/settlements/${latest.internalId}` : undefined}
            />
          </Grid>
        </Grid>
      )}

      <Grid container spacing={2} sx={{ mt: 1 }}>
        <Grid size={{ xs: 12, lg: 7 }}>
          <SectionTitle sx={{ mt: 2 }}>Collection — last 7 days</SectionTitle>
          {week.data && week.data.length > 0 ? (
            <WeekTrend rows={week.data} />
          ) : (
            <Card>
              <EmptyState title="No collection history" description="Records from the last week will appear here." />
            </Card>
          )}
        </Grid>

        <Grid size={{ xs: 12, lg: 5 }}>
          <SectionTitle sx={{ mt: 2 }}>Recent collections</SectionTitle>
          <Card>
            {recent.data && recent.data.content.length > 0 ? (
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell>No.</TableCell>
                    <TableCell>Producer</TableCell>
                    <TableCell align="right">Litres</TableCell>
                    <TableCell align="right">Net</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {recent.data.content.map((c) => (
                    <TableRow key={c.id}>
                      <TableCell>{c.collectionNumber}</TableCell>
                      <TableCell>{c.producerName ?? c.producerNumber ?? `#${c.producerId}`}</TableCell>
                      <TableCell align="right">{litres(c.litres)}</TableCell>
                      <TableCell align="right" sx={{ fontWeight: 600 }}>
                        {money(c.breakdown.netAmount)}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            ) : (
              <EmptyState
                title="No collections yet today"
                description="Recorded collections for today will show here."
                action={
                  <Button component={RouterLink} to="/collection" variant="contained" startIcon={<AddIcon />}>
                    Record collection
                  </Button>
                }
              />
            )}
          </Card>
        </Grid>
      </Grid>

      {latest && (
        <>
          <SectionTitle sx={{ mt: 3 }}>Latest settlement</SectionTitle>
          <Card component={RouterLink} to={`/settlements/${latest.internalId}`} sx={{ display: 'block', textDecoration: 'none' }}>
            <CardContent>
              <Stack direction="row" justifyContent="space-between" alignItems="center" flexWrap="wrap" gap={1.5}>
                <Box>
                  <Typography variant="subtitle1">{latest.settlementNumber}</Typography>
                  <Typography variant="body2" color="text.secondary">
                    {latest.periodStart} → {latest.periodEnd} · {latest.totalProducers} producers · {litres(latest.totalLitres)}
                  </Typography>
                </Box>
                <Stack direction="row" spacing={2} alignItems="center">
                  <Typography sx={{ fontSize: 20, fontWeight: 700 }}>{money(latest.totalNetPayable)}</Typography>
                  <StatusChip status={latest.status} />
                </Stack>
              </Stack>
            </CardContent>
          </Card>
        </>
      )}
    </>
  );
}
