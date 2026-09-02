import { useEffect, useMemo, useRef, useState } from 'react';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Chip from '@mui/material/Chip';
import Divider from '@mui/material/Divider';
import Grid from '@mui/material/Grid2';
import Stack from '@mui/material/Stack';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import TextField from '@mui/material/TextField';
import ToggleButton from '@mui/material/ToggleButton';
import ToggleButtonGroup from '@mui/material/ToggleButtonGroup';
import Tooltip from '@mui/material/Tooltip';
import Typography from '@mui/material/Typography';
import IconButton from '@mui/material/IconButton';
import PrintIcon from '@mui/icons-material/Print';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../lib/api';
import { litres as fmtL, money, rate as fmtRate, today, uuid } from '../lib/format';
import { queueCollection } from '../lib/offline';
import { PrintableReceipt } from '../components/PrintableReceipt';
import { useToast } from '../components/ui';
import type { CenterSettings, Collection, Page, Producer, RatePreview } from '../api/types';

type Shift = 'MORNING' | 'EVENING';

/** MORNING for 6am–12pm, EVENING for 1pm onward (per center operating hours). */
function shiftForNow(d = new Date()): Shift {
  const h = d.getHours();
  return h >= 6 && h < 13 ? 'MORNING' : 'EVENING';
}

export function CollectionPage() {
  const qc = useQueryClient();
  const toast = useToast();
  const producerRef = useRef<HTMLInputElement>(null);
  const shiftPickedManually = useRef(false);

  const [date, setDate] = useState(today());
  const [shift, setShift] = useState<Shift>(shiftForNow());
  const [ref, setRef] = useState('');
  const [producer, setProducer] = useState<Producer | null>(null);
  const [notFound, setNotFound] = useState(false);
  const [litres, setLitres] = useState('');
  const [fat, setFat] = useState('');
  const [snf, setSnf] = useState('');
  const [preview, setPreview] = useState<RatePreview | null>(null);
  const [saving, setSaving] = useState(false);
  const [lastSaved, setLastSaved] = useState<Collection | null>(null);
  const [receiptFor, setReceiptFor] = useState<Collection | null>(null);

  const center = useQuery({
    queryKey: ['center'],
    queryFn: async () => (await api.get<CenterSettings>('/center')).data,
    staleTime: 5 * 60_000,
  });
  const centerName = center.data?.centerName ?? 'SASI MILK SERVICE';
  const centerAddress = center.data?.address ?? '';
  const print = (c: Collection) => {
    setReceiptFor(c);
    requestAnimationFrame(() => window.print());
  };

  const recent = useQuery({
    queryKey: ['collections', { date }],
    queryFn: async () =>
      (await api.get<Page<Collection>>('/collections', { params: { date, size: 20 } })).data,
  });

  // auto-follow the clock (morning/evening) until the operator picks a shift manually
  useEffect(() => {
    if (shiftPickedManually.current || date !== today()) return;
    const tick = () => {
      if (!shiftPickedManually.current && date === today()) setShift(shiftForNow());
    };
    tick();
    const t = setInterval(tick, 60_000);
    return () => clearInterval(t);
  }, [date]);

  async function resolveProducer() {
    const q = ref.trim();
    if (!q) return;
    setNotFound(false);
    try {
      const { data } = await api.get<Page<Producer>>('/producers', { params: { q, size: 5 } });
      const match =
        data.content.find((p) => p.producerNumber.toLowerCase() === q.toLowerCase()) ?? data.content[0];
      if (match) {
        setProducer(match);
      } else {
        setProducer(null);
        setNotFound(true);
      }
    } catch {
      setProducer(null);
      setNotFound(true);
    }
  }

  // live rate preview
  useEffect(() => {
    const f = Number(fat);
    const s = Number(snf);
    if (!producer?.regionId || !f || !s) {
      setPreview(null);
      return;
    }
    const t = setTimeout(async () => {
      try {
        const { data } = await api.post<RatePreview>('/rates/preview', {
          regionId: producer.regionId,
          collectionDate: date,
          fat: f,
          snf: s,
        });
        setPreview(data);
      } catch {
        setPreview(null);
      }
    }, 350);
    return () => clearTimeout(t);
  }, [producer?.regionId, fat, snf, date]);

  const estBase = useMemo(() => {
    const l = Number(litres);
    return preview && l ? l * preview.ratePerLitre : null;
  }, [litres, preview]);

  const canSave =
    (producer || (notFound && ref.trim())) && Number(litres) > 0 && Number(fat) > 0 && Number(snf) > 0;

  function resetEntry(keepProducer: boolean) {
    setLitres('');
    setFat('');
    setSnf('');
    setPreview(null);
    if (!keepProducer) {
      setRef('');
      setProducer(null);
      setNotFound(false);
    }
    producerRef.current?.focus();
  }

  async function save() {
    if (!canSave) return;
    setSaving(true);
    const payload = {
      clientTransactionId: uuid(),
      producerReference: producer?.producerNumber ?? ref.trim(),
      collectionDate: date,
      shift,
      litres: Number(litres),
      fat: Number(fat),
      snf: Number(snf),
    };
    try {
      if (!navigator.onLine) throw new Error('offline');
      const { data } = await api.post<Collection>('/collections', payload);
      toast.success(
        `${data.collectionNumber} · ${fmtL(data.litres)} · ${money(data.breakdown.netAmount)}`,
      );
      setLastSaved(data);
      qc.invalidateQueries({ queryKey: ['collections'] });
      resetEntry(false);
    } catch (err) {
      const status = (err as { response?: { status?: number } }).response?.status;
      if (!navigator.onLine || !status) {
        await queueCollection(payload);
        window.dispatchEvent(new CustomEvent('sasi:queued'));
        toast.success('Saved offline — will sync when back online');
        resetEntry(false);
      } else {
        toast.fromError(err);
      }
    } finally {
      setSaving(false);
    }
  }

  return (
    <>
    <PrintableReceipt collection={receiptFor} centerName={centerName} centerAddress={centerAddress} />
    <Grid container spacing={3}>
      <Grid size={{ xs: 12, md: 7, lg: 6 }}>
        <Card>
          <CardContent>
            <Stack spacing={2.5}>
              <Stack direction="row" spacing={2}>
                <TextField
                  label="Date"
                  type="date"
                  value={date}
                  onChange={(e) => setDate(e.target.value)}
                  InputLabelProps={{ shrink: true }}
                />
                <ToggleButtonGroup
                  exclusive
                  color="primary"
                  value={shift}
                  onChange={(_, v: Shift | null) => {
                    if (v) {
                      shiftPickedManually.current = true;
                      setShift(v);
                    }
                  }}
                  fullWidth
                >
                  <ToggleButton value="MORNING">Morning</ToggleButton>
                  <ToggleButton value="EVENING">Evening</ToggleButton>
                </ToggleButtonGroup>
              </Stack>

              <TextField
                label="Producer number / QR"
                inputRef={producerRef}
                value={ref}
                onChange={(e) => {
                  setRef(e.target.value);
                  setProducer(null);
                  setNotFound(false);
                }}
                onBlur={resolveProducer}
                onKeyDown={(e) => e.key === 'Enter' && resolveProducer()}
                autoFocus
                helperText={
                  producer
                    ? `${producer.producerName}${producer.regionId ? '' : ' — no region set'}`
                    : notFound
                      ? 'Not found — will be resolved on the server when you save'
                      : 'Scan the QR or type the number, then press Enter'
                }
                FormHelperTextProps={{ sx: { color: producer ? 'success.main' : undefined } }}
              />

              <Stack direction="row" spacing={2}>
                <TextField
                  label="Milk (litres)"
                  type="number"
                  value={litres}
                  onChange={(e) => setLitres(e.target.value)}
                  inputProps={{ step: '0.1', min: '0', inputMode: 'decimal' }}
                />
                <TextField
                  label="FAT %"
                  type="number"
                  value={fat}
                  onChange={(e) => setFat(e.target.value)}
                  inputProps={{ step: '0.1', min: '0', inputMode: 'decimal' }}
                />
                <TextField
                  label="SNF %"
                  type="number"
                  value={snf}
                  onChange={(e) => setSnf(e.target.value)}
                  inputProps={{ step: '0.1', min: '0', inputMode: 'decimal' }}
                />
              </Stack>

              <Box sx={{ bgcolor: 'action.hover', borderRadius: 2, p: 2 }}>
                <Stack direction="row" justifyContent="space-between" alignItems="center">
                  <Typography color="text.secondary">Rate / litre</Typography>
                  <Stack direction="row" spacing={1} alignItems="center">
                    <Typography fontWeight={700} component="span">
                      {preview ? fmtRate(preview.ratePerLitre) : '—'}
                    </Typography>
                    {preview && <Chip label={preview.scope} size="small" variant="outlined" />}
                  </Stack>
                </Stack>
                <Divider sx={{ my: 1 }} />
                <Stack direction="row" justifyContent="space-between">
                  <Typography color="text.secondary">Estimated amount</Typography>
                  <Typography variant="h6" color="primary">
                    {estBase != null ? money(estBase) : '—'}
                  </Typography>
                </Stack>
                <Typography variant="caption" color="text.secondary">
                  Final amount is calculated on the server when you save.
                </Typography>
              </Box>

              <Stack direction="row" spacing={2}>
                <Button
                  variant="contained"
                  size="large"
                  fullWidth
                  disabled={!canSave || saving}
                  onClick={save}
                >
                  {saving ? 'Saving…' : 'Save & next producer'}
                </Button>
                <Button size="large" onClick={() => resetEntry(false)} disabled={saving}>
                  Clear
                </Button>
              </Stack>

              {lastSaved && (
                <Box
                  sx={{
                    bgcolor: 'success.light',
                    color: 'success.contrastText',
                    borderRadius: 2,
                    p: 1.5,
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'space-between',
                    gap: 1,
                  }}
                >
                  <Box>
                    <Typography variant="body2" fontWeight={700}>
                      Saved · {lastSaved.collectionNumber}
                    </Typography>
                    <Typography variant="caption">
                      {fmtL(lastSaved.litres)} @ {fmtRate(lastSaved.breakdown.ratePerLitre)} ={' '}
                      {money(lastSaved.breakdown.netAmount)}
                    </Typography>
                  </Box>
                  <Button
                    variant="contained"
                    color="inherit"
                    startIcon={<PrintIcon />}
                    onClick={() => print(lastSaved)}
                    sx={{ color: 'success.dark', bgcolor: 'background.paper' }}
                  >
                    Print receipt
                  </Button>
                </Box>
              )}
            </Stack>
          </CardContent>
        </Card>
      </Grid>

      <Grid size={{ xs: 12, md: 5, lg: 6 }}>
        <Typography variant="h6" sx={{ mb: 1 }}>
          Today · {date}
        </Typography>
        <Card>
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>No.</TableCell>
                <TableCell>Producer</TableCell>
                <TableCell align="right">Litres</TableCell>
                <TableCell align="right">Net</TableCell>
                <TableCell align="right" />
              </TableRow>
            </TableHead>
            <TableBody>
              {recent.data?.content.map((c) => (
                <TableRow key={c.id}>
                  <TableCell>{c.collectionNumber}</TableCell>
                  <TableCell>{c.producerNumber ?? c.producerName ?? `#${c.producerId}`}</TableCell>
                  <TableCell align="right">{fmtL(c.litres)}</TableCell>
                  <TableCell align="right">{money(c.breakdown.netAmount)}</TableCell>
                  <TableCell align="right" padding="none">
                    <Tooltip title="Print receipt">
                      <IconButton size="small" onClick={() => print(c)}>
                        <PrintIcon fontSize="small" />
                      </IconButton>
                    </Tooltip>
                  </TableCell>
                </TableRow>
              ))}
              {recent.data && recent.data.content.length === 0 && (
                <TableRow>
                  <TableCell colSpan={5} sx={{ color: 'text.secondary', textAlign: 'center', py: 3 }}>
                    No collections recorded for this date.
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        </Card>
      </Grid>
    </Grid>
    </>
  );
}
