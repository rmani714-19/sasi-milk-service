import { useState } from 'react';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import Chip from '@mui/material/Chip';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogTitle from '@mui/material/DialogTitle';
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
import { PageHeader } from '../components/PageHeader';
import { QueryBoundary, useToast } from '../components/ui';
import type { RateChart } from '../api/types';

export function RateChartsPage() {
  const toast = useToast();
  const qc = useQueryClient();
  const regions = useRegions();
  const [open, setOpen] = useState(false);

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
      qc.invalidateQueries({ queryKey: ['rates'] });
    },
    onError: toast.fromError,
  });

  return (
    <>
      <PageHeader
        title="Rate Chart"
        subtitle="Rate/litre = (FAT% + SNF%) × TS value ÷ 100"
        action={
          <Button variant="contained" startIcon={<AddIcon />} onClick={() => setOpen(true)}>
            New rate
          </Button>
        }
      />
      <Card>
        <QueryBoundary query={list} empty={(d) => d.length === 0}>
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
                    <TableCell>{r.scope}</TableCell>
                    <TableCell>{regions.data?.find((x) => x.id === r.regionId)?.code ?? '—'}</TableCell>
                    <TableCell align="right">{Number(r.tsValue).toFixed(4)}</TableCell>
                    <TableCell>{r.effectiveFrom}</TableCell>
                    <TableCell>{r.effectiveTo ?? '—'}</TableCell>
                    <TableCell>
                      <Chip size="small" label={r.status} variant="outlined" color={r.status === 'ACTIVE' ? 'success' : 'default'} />
                    </TableCell>
                    <TableCell align="right">
                      {r.status === 'ACTIVE' && (
                        <Button size="small" onClick={() => archive.mutate(r.id)}>
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

      <Dialog open={open} onClose={() => setOpen(false)} fullWidth maxWidth="xs">
        <RateForm regions={regions.data ?? []} busy={create.isPending} onCancel={() => setOpen(false)} onSubmit={(b) => create.mutate(b)} />
      </Dialog>
    </>
  );
}

function RateForm({
  regions,
  busy,
  onCancel,
  onSubmit,
}: {
  regions: { id: number; code: string }[];
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
                  {r.code}
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
