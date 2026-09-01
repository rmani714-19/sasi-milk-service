import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
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
import AddIcon from '@mui/icons-material/Add';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../lib/api';
import { useRegions } from '../api/common';
import { PageHeader } from '../components/PageHeader';
import { QueryBoundary, useToast } from '../components/ui';
import { useAuth } from '../lib/auth';
import type { Page, Producer, Region } from '../api/types';

export function ProducersPage() {
  const nav = useNavigate();
  const toast = useToast();
  const qc = useQueryClient();
  const { can } = useAuth();
  const regions = useRegions();
  const [q, setQ] = useState('');
  const [open, setOpen] = useState(false);

  const list = useQuery({
    queryKey: ['producers', { q }],
    queryFn: async () =>
      (await api.get<Page<Producer>>('/producers', { params: { q: q || undefined, size: 100 } })).data,
  });

  const create = useMutation({
    mutationFn: async (body: Record<string, unknown>) => (await api.post('/producers', body)).data,
    onSuccess: () => {
      toast.success('Producer added');
      qc.invalidateQueries({ queryKey: ['producers'] });
      setOpen(false);
    },
    onError: toast.fromError,
  });

  const regionName = (id?: number) => regions.data?.find((r) => r.id === id)?.code ?? '—';

  return (
    <>
      <PageHeader
        title="Producers"
        action={
          can('MANAGE_PRODUCERS') && (
            <Button variant="contained" startIcon={<AddIcon />} onClick={() => setOpen(true)}>
              Add producer
            </Button>
          )
        }
      />
      <TextField
        placeholder="Search by name or number"
        value={q}
        onChange={(e) => setQ(e.target.value)}
        sx={{ mb: 2, maxWidth: 360 }}
      />
      <Card>
        <QueryBoundary query={list} empty={(d) => d.content.length === 0}>
          {(d) => (
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell>Number</TableCell>
                  <TableCell>Name</TableCell>
                  <TableCell>Mobile</TableCell>
                  <TableCell>Region</TableCell>
                  <TableCell>Status</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {d.content.map((p) => (
                  <TableRow
                    key={p.id}
                    hover
                    sx={{ cursor: 'pointer' }}
                    onClick={() => nav(`/producers/${p.id}`)}
                  >
                    <TableCell>{p.producerNumber}</TableCell>
                    <TableCell>{p.producerName}</TableCell>
                    <TableCell>{p.mobileNumber ?? '—'}</TableCell>
                    <TableCell>{regionName(p.regionId)}</TableCell>
                    <TableCell>
                      <Chip
                        size="small"
                        label={p.status}
                        color={p.status === 'ACTIVE' ? 'success' : 'default'}
                        variant="outlined"
                      />
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </QueryBoundary>
      </Card>

      <ProducerDialog
        open={open}
        regions={regions.data ?? []}
        busy={create.isPending}
        onClose={() => setOpen(false)}
        onSubmit={(body) => create.mutate(body)}
      />
    </>
  );
}

function ProducerDialog({
  open,
  regions,
  busy,
  onClose,
  onSubmit,
}: {
  open: boolean;
  regions: Region[];
  busy: boolean;
  onClose: () => void;
  onSubmit: (body: Record<string, unknown>) => void;
}) {
  const [f, setF] = useState({ producerName: '', mobileNumber: '', email: '', address: '', regionId: '' });
  const set = (k: keyof typeof f) => (e: { target: { value: string } }) => setF({ ...f, [k]: e.target.value });

  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle>Add producer</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          <TextField label="Name" value={f.producerName} onChange={set('producerName')} autoFocus />
          <TextField label="Mobile (10 digits)" value={f.mobileNumber} onChange={set('mobileNumber')} />
          <TextField label="Email" value={f.email} onChange={set('email')} />
          <TextField label="Address" value={f.address} onChange={set('address')} multiline minRows={2} />
          <TextField select label="Region" value={f.regionId} onChange={set('regionId')}>
            <MenuItem value="">— none —</MenuItem>
            {regions.map((r) => (
              <MenuItem key={r.id} value={r.id}>
                {r.code} · {r.name}
              </MenuItem>
            ))}
          </TextField>
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button
          variant="contained"
          disabled={busy || !f.producerName.trim()}
          onClick={() =>
            onSubmit({
              producerName: f.producerName.trim(),
              mobileNumber: f.mobileNumber || undefined,
              email: f.email || undefined,
              address: f.address || undefined,
              regionId: f.regionId ? Number(f.regionId) : undefined,
            })
          }
        >
          Save
        </Button>
      </DialogActions>
    </Dialog>
  );
}
