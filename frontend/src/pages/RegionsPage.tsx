import { useState } from 'react';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
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
import PlaceOutlinedIcon from '@mui/icons-material/PlaceOutlined';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../lib/api';
import { makeRegionCode, regionDescription, regionLabel, splitRegionCode } from '../lib/region';
import * as V from '../lib/validate';
import { PageHeader } from '../components/PageHeader';
import { FormSection } from '../components/FormSection';
import { StatusChip } from '../components/StatusChip';
import { EmptyState, QueryBoundary, TableSkeleton, useToast } from '../components/ui';
import { useAuth } from '../lib/auth';
import type { Region } from '../api/types';

export function RegionsPage() {
  const toast = useToast();
  const qc = useQueryClient();
  const { can } = useAuth();
  const manage = can('MANAGE_RATES');
  const [addOpen, setAddOpen] = useState(false);
  const [edit, setEdit] = useState<Region | null>(null);

  const list = useQuery({
    queryKey: ['regions'],
    queryFn: async () => (await api.get<Region[]>('/regions')).data,
  });

  const create = useMutation({
    mutationFn: async (body: { code: string; name: string; description: string }) =>
      (await api.post('/regions', body)).data,
    onSuccess: () => {
      toast.success('Region added');
      qc.invalidateQueries({ queryKey: ['regions'] });
      setAddOpen(false);
    },
    onError: toast.fromError,
  });

  const update = useMutation({
    mutationFn: async ({ id, body }: { id: number; body: { name: string; description: string; status: string } }) =>
      (await api.put(`/regions/${id}`, body)).data,
    onSuccess: () => {
      toast.success('Region updated');
      qc.invalidateQueries({ queryKey: ['regions'] });
      setEdit(null);
    },
    onError: toast.fromError,
  });

  return (
    <>
      <PageHeader
        title="Regions"
        subtitle="Local collection points — labelled area–dairy number–place for producers who bring milk straight to the dairy"
        action={
          manage && (
            <Button variant="contained" startIcon={<AddIcon />} onClick={() => setAddOpen(true)}>
              Add region
            </Button>
          )
        }
      />
      <Card>
        <QueryBoundary
          query={list}
          skeleton={<TableSkeleton cols={5} />}
          empty={(d) => d.length === 0}
          emptyState={
            <EmptyState
              icon={<PlaceOutlinedIcon />}
              title="No regions yet"
              description="Add a region so producers can be grouped by their local dairy point."
              action={
                manage && (
                  <Button variant="contained" startIcon={<AddIcon />} onClick={() => setAddOpen(true)}>
                    Add region
                  </Button>
                )
              }
            />
          }
        >
          {(d) => (
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell>Region</TableCell>
                  <TableCell>Area</TableCell>
                  <TableCell>Dairy no.</TableCell>
                  <TableCell>Place</TableCell>
                  <TableCell>Status</TableCell>
                  {manage && <TableCell align="right" />}
                </TableRow>
              </TableHead>
              <TableBody>
                {d.map((r) => {
                  const { area, dairyNo } = splitRegionCode(r.code);
                  return (
                    <TableRow key={r.id}>
                      <TableCell sx={{ fontWeight: 600 }}>{regionLabel(r)}</TableCell>
                      <TableCell>{area}</TableCell>
                      <TableCell>{dairyNo || '—'}</TableCell>
                      <TableCell>{r.name}</TableCell>
                      <TableCell>
                        <StatusChip status={r.status} />
                      </TableCell>
                      {manage && (
                        <TableCell align="right">
                          <Button size="small" onClick={() => setEdit(r)}>
                            Edit
                          </Button>
                        </TableCell>
                      )}
                    </TableRow>
                  );
                })}
              </TableBody>
            </Table>
          )}
        </QueryBoundary>
      </Card>

      <AddRegionDialog
        open={addOpen}
        busy={create.isPending}
        onClose={() => setAddOpen(false)}
        onSubmit={(body) => create.mutate(body)}
      />
      <EditRegionDialog
        key={edit?.id ?? 'none'}
        region={edit}
        busy={update.isPending}
        onClose={() => setEdit(null)}
        onSubmit={(body) => edit && update.mutate({ id: edit.id, body })}
      />
    </>
  );
}

const BLANK = { area: '', dairyNo: '', place: '' };

function AddRegionDialog({
  open,
  busy,
  onClose,
  onSubmit,
}: {
  open: boolean;
  busy: boolean;
  onClose: () => void;
  onSubmit: (b: { code: string; name: string; description: string }) => void;
}) {
  const [f, setF] = useState(BLANK);
  const [tried, setTried] = useState(false);
  const set =
    (k: keyof typeof f, transform?: (v: string) => string) =>
    (e: { target: { value: string } }) =>
      setF((s) => ({ ...s, [k]: transform ? transform(e.target.value) : e.target.value }));

  const errors = {
    area: V.required('Area')(f.area),
    dairyNo: !f.dairyNo.trim() ? 'Dairy number is required' : !/^\d{1,6}$/.test(f.dairyNo.trim()) ? 'Digits only' : '',
    place: V.required('Place')(f.place),
  };
  const showErr = (k: keyof typeof errors) => (tried && errors[k]) || '';
  const valid = V.allValid(errors);
  const preview = valid ? `${f.area.trim().toUpperCase()}-${f.dairyNo.trim()}-${f.place.trim()}` : '';

  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="xs" TransitionProps={{ onExited: () => { setF(BLANK); setTried(false); } }}>
      <DialogTitle>Add region</DialogTitle>
      <DialogContent dividers>
        <FormSection title="Collection point" hint="Shown everywhere as area–dairy number–place.">
          <TextField
            label="Area"
            value={f.area}
            onChange={set('area', (v) => v.replace(/[^A-Za-z0-9 ]/g, ''))}
            error={!!showErr('area')}
            helperText={showErr('area') || 'e.g. NORTH, Kottur'}
            autoFocus
          />
          <TextField
            label="Dairy number"
            value={f.dairyNo}
            onChange={set('dairyNo', (v) => V.onlyDigits(v).slice(0, 6))}
            error={!!showErr('dairyNo')}
            helperText={showErr('dairyNo')}
            inputProps={{ inputMode: 'numeric' }}
          />
          <TextField
            label="Place"
            value={f.place}
            onChange={set('place')}
            error={!!showErr('place')}
            helperText={showErr('place') || 'Village / landmark near the dairy'}
          />
          {preview && (
            <Typography variant="caption" color="text.secondary">
              Label: <strong>{preview}</strong>
            </Typography>
          )}
        </FormSection>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button
          variant="contained"
          disabled={busy}
          onClick={() => {
            setTried(true);
            if (!valid) return;
            onSubmit({
              code: makeRegionCode(f.area, f.dairyNo),
              name: f.place.trim(),
              description: regionDescription(f.area, f.dairyNo, f.place),
            });
          }}
        >
          {busy ? 'Saving…' : 'Save region'}
        </Button>
      </DialogActions>
    </Dialog>
  );
}

function EditRegionDialog({
  region,
  busy,
  onClose,
  onSubmit,
}: {
  region: Region | null;
  busy: boolean;
  onClose: () => void;
  onSubmit: (b: { name: string; description: string; status: string }) => void;
}) {
  const parts = region ? splitRegionCode(region.code) : { area: '', dairyNo: '' };
  const [place, setPlace] = useState(region?.name ?? '');
  const [status, setStatus] = useState(region?.status ?? 'ACTIVE');
  const [tried, setTried] = useState(false);
  const placeErr = tried && !place.trim() ? 'Place is required' : '';

  return (
    <Dialog open={!!region} onClose={onClose} fullWidth maxWidth="xs">
      <DialogTitle>Edit region</DialogTitle>
      <DialogContent dividers>
        <FormSection title="Collection point">
          <Stack direction="row" spacing={2}>
            <TextField label="Area" value={parts.area} disabled />
            <TextField label="Dairy number" value={parts.dairyNo} disabled />
          </Stack>
          <Typography variant="caption" color="text.secondary">
            Area and dairy number are fixed once created.
          </Typography>
          <TextField
            label="Place"
            value={place}
            onChange={(e) => setPlace(e.target.value)}
            error={!!placeErr}
            helperText={placeErr}
          />
          <TextField select label="Status" value={status} onChange={(e) => setStatus(e.target.value)}>
            <MenuItem value="ACTIVE">Active</MenuItem>
            <MenuItem value="INACTIVE">Inactive</MenuItem>
          </TextField>
        </FormSection>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button
          variant="contained"
          disabled={busy}
          onClick={() => {
            setTried(true);
            if (!place.trim()) return;
            onSubmit({
              name: place.trim(),
              description: regionDescription(parts.area, parts.dairyNo, place),
              status,
            });
          }}
        >
          Save changes
        </Button>
      </DialogActions>
    </Dialog>
  );
}
