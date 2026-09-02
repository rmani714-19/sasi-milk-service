import { useState } from 'react';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogTitle from '@mui/material/DialogTitle';
import Grid from '@mui/material/Grid2';
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
import { api } from '../lib/api';
import { rate, today } from '../lib/format';
import { useRegions } from '../api/common';
import { regionShort } from '../lib/region';
import { PageHeader } from '../components/PageHeader';
import { StatusChip } from '../components/StatusChip';
import { ConfirmDialog, EmptyState, QueryBoundary, SectionTitle, TableSkeleton, useToast } from '../components/ui';
import type { RateChart, Region } from '../api/types';

export function RateChartsPage() {
  const toast = useToast();
  const qc = useQueryClient();
  const regions = useRegions();
  const [open, setOpen] = useState(false);
  const [confirmArchive, setConfirmArchive] = useState<RateChart | null>(null);

  const list = useQuery({
    queryKey: ['rates'],
    queryFn: async () => (await api.get<RateChart[]>('/rates')).data,
  });
  const create = useMutation({
    mutationFn: async (b: Record<string, unknown>) => (await api.post('/rates', b)).data,
    onSuccess: () => {
      toast.success('Rate chart created');
      qc.invalidateQueries({ queryKey: ['rates'] });
      setOpen(false);
    },
    onError: toast.fromError,
  });
  const archive = useMutation({
    mutationFn: async (id: number) => (await api.post(`/rates/${id}/archive`)).data,
    onSuccess: () => {
      toast.success('Archived');
      setConfirmArchive(null);
      qc.invalidateQueries({ queryKey: ['rates'] });
    },
    onError: toast.fromError,
  });

  return (
    <>
      <PageHeader
        title="Rate Chart"
        subtitle="Rate / litre = (FAT % + SNF %) × TS value ÷ 100"
        action={
          <Button variant="contained" startIcon={<AddIcon />} onClick={() => setOpen(true)}>
            New rate
          </Button>
        }
      />

      <Grid container spacing={3}>
        <Grid size={{ xs: 12, md: 4 }}>
          <SectionTitle>Rate calculator</SectionTitle>
          <RateCalculator />
        </Grid>
        <Grid size={{ xs: 12, md: 8 }}>
          <SectionTitle>Effective rates</SectionTitle>
          <Card>
            <QueryBoundary
              query={list}
              skeleton={<TableSkeleton cols={6} />}
              empty={(d) => d.length === 0}
              emptyState={<EmptyState title="No rate charts" description="Add a TS value to start pricing milk collections." />}
            >
              {(d) => (
                <Table size="small">
                  <TableHead>
                    <TableRow>
                      <TableCell>Scope</TableCell>
                      <TableCell>Region</TableCell>
                      <TableCell align="right">TS value</TableCell>
                      <TableCell>From</TableCell>
                      <TableCell>To</TableCell>
                      <TableCell>Status</TableCell>
                      <TableCell />
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {d.map((r) => (
                      <TableRow key={r.id}>
                        <TableCell sx={{ fontWeight: 600 }}>{r.scope}</TableCell>
                        <TableCell>{regionShort(regions.data?.find((x) => x.id === r.regionId))}</TableCell>
                        <TableCell align="right">{Number(r.tsValue).toFixed(4)}</TableCell>
                        <TableCell>{r.effectiveFrom}</TableCell>
                        <TableCell>{r.effectiveTo ?? '—'}</TableCell>
                        <TableCell>
                          <StatusChip status={r.status} />
                        </TableCell>
                        <TableCell align="right">
                          {r.status === 'ACTIVE' && (
                            <Button size="small" color="error" onClick={() => setConfirmArchive(r)}>
                              Archive
                            </Button>
                          )}
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              )}
            </QueryBoundary>
          </Card>
        </Grid>
      </Grid>

      <Dialog open={open} onClose={() => setOpen(false)} fullWidth maxWidth="xs">
        <RateForm regions={regions.data ?? []} busy={create.isPending} onCancel={() => setOpen(false)} onSubmit={(b) => create.mutate(b)} />
      </Dialog>

      <ConfirmDialog
        open={!!confirmArchive}
        title="Archive this rate?"
        message="Collections after the effective-to date will fall back to the next active rate. This does not change past collections."
        confirmLabel="Archive"
        destructive
        busy={archive.isPending}
        onConfirm={() => confirmArchive && archive.mutate(confirmArchive.id)}
        onClose={() => setConfirmArchive(null)}
      />
    </>
  );
}

function RateCalculator() {
  const [fat, setFat] = useState('4.1');
  const [snf, setSnf] = useState('8.6');
  const [ts, setTs] = useState('7');
  const f = Number(fat);
  const s = Number(snf);
  const t = Number(ts);
  const result = f && s && t ? ((f + s) * t) / 100 : null;
  return (
    <Card>
      <CardContent>
        <Stack spacing={2}>
          <Stack direction="row" spacing={2}>
            <TextField label="FAT %" type="number" value={fat} onChange={(e) => setFat(e.target.value)} inputProps={{ step: '0.1' }} />
            <TextField label="SNF %" type="number" value={snf} onChange={(e) => setSnf(e.target.value)} inputProps={{ step: '0.1' }} />
          </Stack>
          <TextField label="TS value" type="number" value={ts} onChange={(e) => setTs(e.target.value)} inputProps={{ step: '0.0001' }} />
          <Stack
            direction="row"
            justifyContent="space-between"
            alignItems="baseline"
            sx={{ bgcolor: 'primary.light', borderRadius: 2, p: 2 }}
          >
            <Typography color="text.secondary" fontWeight={500}>
              Rate / litre
            </Typography>
            <Typography sx={{ fontSize: 24, fontWeight: 700 }} color="primary.dark">
              {result != null ? rate(result) : '—'}
            </Typography>
          </Stack>
          <Typography variant="caption" color="text.secondary">
            Preview only — the server resolves the effective TS value at collection time.
          </Typography>
        </Stack>
      </CardContent>
    </Card>
  );
}

function RateForm({
  regions,
  busy,
  onCancel,
  onSubmit,
}: {
  regions: Region[];
  busy: boolean;
  onCancel: () => void;
  onSubmit: (b: Record<string, unknown>) => void;
}) {
  const [f, setF] = useState({ scope: 'COMMON', regionId: '', tsValue: '', effectiveFrom: today(), effectiveTo: '' });
  const set = (k: keyof typeof f) => (e: { target: { value: string } }) => setF({ ...f, [k]: e.target.value });
  return (
    <>
      <DialogTitle>New rate chart</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          <TextField select label="Scope" value={f.scope} onChange={set('scope')}>
            <MenuItem value="COMMON">Common</MenuItem>
            <MenuItem value="REGION">Region</MenuItem>
          </TextField>
          {f.scope === 'REGION' && (
            <TextField select label="Region" value={f.regionId} onChange={set('regionId')}>
              {regions.map((r) => (
                <MenuItem key={r.id} value={r.id}>
                  {regionShort(r)} · {r.name}
                </MenuItem>
              ))}
            </TextField>
          )}
          <TextField label="TS value" type="number" value={f.tsValue} onChange={set('tsValue')} />
          <TextField label="Effective from" type="date" value={f.effectiveFrom} onChange={set('effectiveFrom')} InputLabelProps={{ shrink: true }} />
          <TextField label="Effective to (optional)" type="date" value={f.effectiveTo} onChange={set('effectiveTo')} InputLabelProps={{ shrink: true }} />
          {f.tsValue && (
            <Typography variant="caption" color="text.secondary">
              e.g. FAT 4.1 + SNF 8.6 → {rate(((4.1 + 8.6) * Number(f.tsValue)) / 100)}/L
            </Typography>
          )}
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onCancel}>Cancel</Button>
        <Button
          variant="contained"
          disabled={busy || !f.tsValue || (f.scope === 'REGION' && !f.regionId)}
          onClick={() =>
            onSubmit({
              scope: f.scope,
              regionId: f.scope === 'REGION' ? Number(f.regionId) : undefined,
              tsValue: Number(f.tsValue),
              effectiveFrom: f.effectiveFrom,
              effectiveTo: f.effectiveTo || undefined,
            })
          }
        >
          Create
        </Button>
      </DialogActions>
    </>
  );
}
