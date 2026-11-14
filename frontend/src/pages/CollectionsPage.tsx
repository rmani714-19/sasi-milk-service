import { useEffect, useState } from 'react';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogTitle from '@mui/material/DialogTitle';
import InputAdornment from '@mui/material/InputAdornment';
import MenuItem from '@mui/material/MenuItem';
import Stack from '@mui/material/Stack';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableContainer from '@mui/material/TableContainer';
import TableHead from '@mui/material/TableHead';
import TablePagination from '@mui/material/TablePagination';
import TableRow from '@mui/material/TableRow';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import EditOutlinedIcon from '@mui/icons-material/EditOutlined';
import SearchIcon from '@mui/icons-material/Search';
import ListAltOutlinedIcon from '@mui/icons-material/ListAltOutlined';
import { format as formatDate, subDays } from 'date-fns';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../lib/api';
import { litres as fmtL, money, rate as fmtRate } from '../lib/format';
import { useAuth } from '../lib/auth';
import { PageHeader } from '../components/PageHeader';
import { StatusChip } from '../components/StatusChip';
import { EmptyState, QueryBoundary, TableSkeleton, useToast } from '../components/ui';
import type { Collection, Page } from '../api/types';

const iso = (d: Date) => formatDate(d, 'yyyy-MM-dd');

const SORTS = [
  { value: 'date_desc', label: 'Newest first' },
  { value: 'date_asc', label: 'Oldest first' },
  { value: 'litres_desc', label: 'Litres — highest first' },
  { value: 'litres_asc', label: 'Litres — lowest first' },
  { value: 'amount_desc', label: 'Amount — highest first' },
  { value: 'amount_asc', label: 'Amount — lowest first' },
];

export function CollectionsPage() {
  const toast = useToast();
  const qc = useQueryClient();
  const { can } = useAuth();
  const canEdit = can('MANAGE_PRODUCERS') || can('ALL');

  const [from, setFrom] = useState(iso(new Date()));
  const [to, setTo] = useState(iso(new Date()));
  const [shift, setShift] = useState('');
  const [status, setStatus] = useState('');
  const [sort, setSort] = useState('date_desc');
  const [search, setSearch] = useState('');
  const [q, setQ] = useState('');
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(50);

  // debounce the producer search so each keystroke doesn't hit the server
  useEffect(() => {
    const t = setTimeout(() => {
      setQ(search.trim());
      setPage(0);
    }, 300);
    return () => clearTimeout(t);
  }, [search]);

  const list = useQuery({
    queryKey: ['collections', 'browse', from, to, shift, status, sort, q, page, size],
    queryFn: async () =>
      (
        await api.get<Page<Collection>>('/collections/browse', {
          params: {
            from: from || undefined,
            to: to || undefined,
            shift: shift || undefined,
            status: status || undefined,
            q: q || undefined,
            sort,
            page,
            size,
          },
        })
      ).data,
    placeholderData: (prev) => prev,
  });

  const reset = <T,>(setter: (v: T) => void) => (v: T) => {
    setter(v);
    setPage(0);
  };
  const preset = (days: number) => {
    setFrom(iso(subDays(new Date(), days)));
    setTo(iso(new Date()));
    setPage(0);
  };

  const [editing, setEditing] = useState<Collection | null>(null);
  const [eLitres, setELitres] = useState('');
  const [eFat, setEFat] = useState('');
  const [eSnf, setESnf] = useState('');

  const openEdit = (c: Collection) => {
    setEditing(c);
    setELitres(String(c.litres));
    setEFat(c.qualityPending ? '' : String(c.fat));
    setESnf(c.qualityPending ? '' : String(c.snf));
  };

  const qualityTouched = eFat.trim() !== '' || eSnf.trim() !== '';
  const qualityOk = Number(eFat) > 0 && Number(eSnf) > 0;
  const qualityError = qualityTouched && !qualityOk ? 'Enter both FAT and SNF' : '';
  const litresOk = Number(eLitres) > 0;

  const save = useMutation({
    mutationFn: async () =>
      (
        await api.put<Collection>(`/collections/${editing!.id}`, {
          litres: Number(eLitres),
          ...(qualityOk ? { fat: Number(eFat), snf: Number(eSnf) } : {}),
        })
      ).data,
    onSuccess: (c) => {
      toast.success(`${c.collectionNumber} updated`);
      qc.invalidateQueries({ queryKey: ['collections'] });
      setEditing(null);
    },
    onError: toast.fromError,
  });

  return (
    <>
      <PageHeader title="Collections" subtitle="Every milk collection, any date — search, sort and correct entries" />

      <Card sx={{ mb: 2 }}>
        <CardContent>
          <Stack direction="row" spacing={1.5} alignItems="center" flexWrap="wrap" useFlexGap>
            <TextField
              type="date"
              size="small"
              label="From"
              value={from}
              onChange={(e) => reset(setFrom)(e.target.value)}
              InputLabelProps={{ shrink: true }}
              sx={{ width: 160, flexShrink: 0 }}
            />
            <TextField
              type="date"
              size="small"
              label="To"
              value={to}
              onChange={(e) => reset(setTo)(e.target.value)}
              InputLabelProps={{ shrink: true }}
              sx={{ width: 160, flexShrink: 0 }}
            />
            <Button size="small" onClick={() => preset(0)} sx={{ flexShrink: 0 }}>
              Today
            </Button>
            <Button size="small" onClick={() => preset(1)} sx={{ flexShrink: 0 }}>
              Last 2 days
            </Button>
            <Button size="small" onClick={() => preset(6)} sx={{ flexShrink: 0 }}>
              Last 7 days
            </Button>
            <Button size="small" onClick={() => preset(29)} sx={{ flexShrink: 0 }}>
              Last 30 days
            </Button>
            <Button
              size="small"
              onClick={() => {
                setFrom('');
                setTo('');
                setPage(0);
              }}
              sx={{ flexShrink: 0 }}
            >
              All dates
            </Button>
          </Stack>

          <Stack direction="row" spacing={1.5} alignItems="center" flexWrap="wrap" useFlexGap sx={{ mt: 2 }}>
            <TextField
              size="small"
              placeholder="Producer number or name"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              InputProps={{
                startAdornment: (
                  <InputAdornment position="start">
                    <SearchIcon fontSize="small" />
                  </InputAdornment>
                ),
              }}
              sx={{ width: 260, flexShrink: 0 }}
            />
            <TextField
              select
              size="small"
              label="Shift"
              value={shift}
              onChange={(e) => reset(setShift)(e.target.value)}
              sx={{ width: 140, flexShrink: 0 }}
            >
              <MenuItem value="">Both</MenuItem>
              <MenuItem value="MORNING">Morning</MenuItem>
              <MenuItem value="EVENING">Evening</MenuItem>
            </TextField>
            <TextField
              select
              size="small"
              label="Status"
              value={status}
              onChange={(e) => reset(setStatus)(e.target.value)}
              sx={{ width: 170, flexShrink: 0 }}
            >
              <MenuItem value="">All</MenuItem>
              <MenuItem value="CONFIRMED">Confirmed</MenuItem>
              <MenuItem value="PENDING_QUALITY">Pending quality</MenuItem>
              <MenuItem value="VOID">Void</MenuItem>
            </TextField>
            <TextField
              select
              size="small"
              label="Sort by"
              value={sort}
              onChange={(e) => reset(setSort)(e.target.value)}
              sx={{ width: 220, flexShrink: 0 }}
            >
              {SORTS.map((s) => (
                <MenuItem key={s.value} value={s.value}>
                  {s.label}
                </MenuItem>
              ))}
            </TextField>
          </Stack>
        </CardContent>
      </Card>

      <Card>
        <QueryBoundary
          query={list}
          skeleton={<TableSkeleton cols={10} />}
          empty={(d) => d.content.length === 0}
          emptyState={
            <EmptyState
              icon={<ListAltOutlinedIcon />}
              title="No collections found"
              description="Try a wider date range or clear the producer search."
            />
          }
        >
          {(data) => (
            <>
              <TableContainer sx={{ maxHeight: 'calc(100vh - 380px)' }}>
                <Table size="small" stickyHeader>
                  <TableHead>
                    <TableRow>
                      <TableCell>Date</TableCell>
                      <TableCell>Shift</TableCell>
                      <TableCell>Collection no</TableCell>
                      <TableCell>Producer no</TableCell>
                      <TableCell>Producer</TableCell>
                      <TableCell align="right">Litres</TableCell>
                      <TableCell align="right">FAT</TableCell>
                      <TableCell align="right">SNF</TableCell>
                      <TableCell align="right">Rate</TableCell>
                      <TableCell align="right">Amount</TableCell>
                      <TableCell>Status</TableCell>
                      <TableCell align="right" />
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {data.content.map((c) => (
                      <TableRow key={c.id} hover>
                        <TableCell sx={{ whiteSpace: 'nowrap' }}>{c.collectionDate}</TableCell>
                        <TableCell>{c.shift === 'MORNING' ? 'Morning' : 'Evening'}</TableCell>
                        <TableCell sx={{ fontFamily: 'monospace', fontSize: 12, whiteSpace: 'nowrap' }}>{c.collectionNumber}</TableCell>
                        <TableCell sx={{ fontWeight: 600 }}>{c.producerNumber ?? `#${c.producerId}`}</TableCell>
                        <TableCell>{c.producerName ?? '—'}</TableCell>
                        <TableCell align="right" sx={{ fontWeight: 600, whiteSpace: 'nowrap' }}>
                          {fmtL(c.litres)}
                        </TableCell>
                        <TableCell align="right">{c.qualityPending ? '—' : Number(c.fat).toFixed(1)}</TableCell>
                        <TableCell align="right">{c.qualityPending ? '—' : Number(c.snf).toFixed(1)}</TableCell>
                        <TableCell align="right">
                          {c.qualityPending ? '—' : fmtRate(c.breakdown.ratePerLitre)}
                        </TableCell>
                        <TableCell align="right" sx={{ fontWeight: 700 }}>
                          {c.qualityPending ? '—' : money(c.breakdown.netAmount)}
                        </TableCell>
                        <TableCell>
                          <StatusChip status={c.status} />
                        </TableCell>
                        <TableCell align="right">
                          {canEdit && c.status !== 'VOID' && (
                            <Button size="small" startIcon={<EditOutlinedIcon />} onClick={() => openEdit(c)}>
                              Edit
                            </Button>
                          )}
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </TableContainer>
              <TablePagination
                component="div"
                count={data.totalElements}
                page={data.page}
                onPageChange={(_, p) => setPage(p)}
                rowsPerPage={size}
                onRowsPerPageChange={(e) => {
                  setSize(Number(e.target.value));
                  setPage(0);
                }}
                rowsPerPageOptions={[25, 50, 100, 200]}
              />
            </>
          )}
        </QueryBoundary>
      </Card>

      <Dialog open={!!editing} onClose={() => setEditing(null)} fullWidth maxWidth="xs">
        <DialogTitle>Edit collection</DialogTitle>
        <DialogContent>
          {editing && (
            <Stack spacing={2} sx={{ mt: 1 }}>
              <Box>
                <Typography variant="body2" sx={{ fontWeight: 600 }}>
                  {editing.producerNumber} · {editing.producerName}
                </Typography>
                <Typography variant="caption" color="text.secondary">
                  {editing.collectionNumber} · {editing.collectionDate} ·{' '}
                  {editing.shift === 'MORNING' ? 'Morning' : 'Evening'}
                </Typography>
              </Box>
              <TextField
                label="Milk quantity"
                type="number"
                value={eLitres}
                onChange={(e) => setELitres(e.target.value)}
                error={!litresOk}
                helperText={litresOk ? undefined : 'Enter litres greater than zero'}
                autoFocus
                inputProps={{ step: '0.1', min: '0', inputMode: 'decimal' }}
                InputProps={{ endAdornment: <InputAdornment position="end">L</InputAdornment> }}
              />
              <Stack direction="row" spacing={2}>
                <TextField
                  label="FAT"
                  type="number"
                  value={eFat}
                  onChange={(e) => setEFat(e.target.value)}
                  error={!!qualityError}
                  inputProps={{ step: '0.1', min: '0', inputMode: 'decimal' }}
                  InputProps={{ endAdornment: <InputAdornment position="end">%</InputAdornment> }}
                />
                <TextField
                  label="SNF"
                  type="number"
                  value={eSnf}
                  onChange={(e) => setESnf(e.target.value)}
                  error={!!qualityError}
                  inputProps={{ step: '0.1', min: '0', inputMode: 'decimal' }}
                  InputProps={{ endAdornment: <InputAdornment position="end">%</InputAdornment> }}
                />
              </Stack>
              <Typography variant="caption" color={qualityError ? 'error' : 'text.secondary'}>
                {qualityError ||
                  'The rate and amount are recalculated from the litres, FAT and SNF. Collections already in a settlement run cannot be edited.'}
              </Typography>
            </Stack>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setEditing(null)}>Cancel</Button>
          <Button variant="contained" disabled={!litresOk || !!qualityError || save.isPending} onClick={() => save.mutate()}>
            {save.isPending ? 'Saving…' : 'Save changes'}
          </Button>
        </DialogActions>
      </Dialog>
    </>
  );
}
