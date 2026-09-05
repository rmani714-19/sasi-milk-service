import {
  useEffect,
  useMemo,
  useRef,
  useState,
  type KeyboardEvent as ReactKeyboardEvent,
  type ReactNode,
  type RefObject,
} from 'react';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Chip from '@mui/material/Chip';
import Grid from '@mui/material/Grid2';
import InputAdornment from '@mui/material/InputAdornment';
import LinearProgress from '@mui/material/LinearProgress';
import Stack from '@mui/material/Stack';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableContainer from '@mui/material/TableContainer';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import TextField from '@mui/material/TextField';
import ToggleButton from '@mui/material/ToggleButton';
import ToggleButtonGroup from '@mui/material/ToggleButtonGroup';
import Tooltip from '@mui/material/Tooltip';
import Typography from '@mui/material/Typography';
import IconButton from '@mui/material/IconButton';
import PrintIcon from '@mui/icons-material/Print';
import QrCodeScannerIcon from '@mui/icons-material/QrCodeScanner';
import WbSunnyOutlinedIcon from '@mui/icons-material/WbSunnyOutlined';
import Brightness3OutlinedIcon from '@mui/icons-material/Brightness3Outlined';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import ScaleOutlinedIcon from '@mui/icons-material/ScaleOutlined';
import ChevronLeftIcon from '@mui/icons-material/ChevronLeft';
import ChevronRightIcon from '@mui/icons-material/ChevronRight';
import CalendarMonthOutlinedIcon from '@mui/icons-material/CalendarMonthOutlined';
import { DatePicker } from '@mui/x-date-pickers/DatePicker';
import { format as formatDate, parseISO, addDays, isSameDay } from 'date-fns';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../lib/api';
import { litres as fmtL, money, rate as fmtRate, today, uuid } from '../lib/format';
import { queueCollection } from '../lib/offline';
import { useRegions } from '../api/common';
import { regionShort } from '../lib/region';
import { useWeightBridge } from '../lib/weightBridge';
import { CollectionModeSelector, type CollectionMode } from '../components/collection/CollectionModeSelector';
import { WeightMachineStatus } from '../components/collection/WeightMachineStatus';
import { WeightDisplay } from '../components/collection/WeightDisplay';
import { PrintableReceipt } from '../components/PrintableReceipt';
import { StatusChip } from '../components/StatusChip';
import { useToast } from '../components/ui';
import type { CenterSettings, Collection, Page, Producer, RatePreview } from '../api/types';

type Shift = 'MORNING' | 'EVENING';

const MODE_KEY = 'sasi.collectionMode';
const SCALE_ID = 'iScale i-05-RS232';
/** below this the scale pan is considered empty and ready for the next can */
const REARM_KG = 0.15;

function readMode(): CollectionMode {
  try {
    return localStorage.getItem(MODE_KEY) === 'MANUAL' ? 'MANUAL' : 'AUTO';
  } catch {
    return 'AUTO';
  }
}

/** MORNING for 6am–12pm, EVENING for 1pm onward (per center operating hours). */
function shiftForNow(d = new Date()): Shift {
  const h = d.getHours();
  return h >= 6 && h < 13 ? 'MORNING' : 'EVENING';
}

const isEnter = (e: ReactKeyboardEvent) =>
  e.key === 'Enter' || e.key === 'Return' || e.code === 'Enter' || e.code === 'NumpadEnter';

export function CollectionPage() {
  const qc = useQueryClient();
  const toast = useToast();
  const regions = useRegions();
  const producerInputRef = useRef<HTMLInputElement>(null);
  const litresRef = useRef<HTMLInputElement>(null);
  const fatRef = useRef<HTMLInputElement>(null);
  const snfRef = useRef<HTMLInputElement>(null);
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
  const [triedSave, setTriedSave] = useState(false);
  const [lastSaved, setLastSaved] = useState<Collection | null>(null);
  const [receiptFor, setReceiptFor] = useState<Collection | null>(null);
  const [recentProducers, setRecentProducers] = useState<Producer[]>([]);

  // scale / entry mode
  const [mode, setMode] = useState<CollectionMode>(readMode);
  const [armed, setArmed] = useState(true); // AUTO: can capture the next stable weight
  const [entryModeById, setEntryModeById] = useState<Record<string, CollectionMode>>({});
  const capturedFocus = useRef(false);
  const bridge = useWeightBridge(mode === 'AUTO');

  // recent-list view controls
  const [listShift, setListShift] = useState<'ALL' | Shift>('ALL');
  const [listSearch, setListSearch] = useState('');

  const center = useQuery({
    queryKey: ['center'],
    queryFn: async () => (await api.get<CenterSettings>('/center')).data,
    staleTime: 5 * 60_000,
  });
  const centerName = center.data?.centerName ?? 'SASI MILK SERVICE';
  const centerAddress = center.data?.address ?? '';

  const producerTotal = useQuery({
    queryKey: ['producers', 'count'],
    queryFn: async () =>
      (await api.get<Page<Producer>>('/producers', { params: { size: 1 } })).data.totalElements,
    staleTime: 5 * 60_000,
  });

  const recent = useQuery({
    queryKey: ['collections', { date }],
    queryFn: async () =>
      (await api.get<Page<Collection>>('/collections', { params: { date, size: 300 } })).data,
  });

  const print = (c: Collection) => {
    setReceiptFor(c);
    requestAnimationFrame(() => window.print());
  };

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

  function selectProducer(p: Producer) {
    setProducer(p);
    setNotFound(false);
    setRef(p.producerNumber);
    setRecentProducers((prev) => [p, ...prev.filter((x) => x.id !== p.id)].slice(0, 6));
    // AUTO has no litres field — jump straight to FAT
    setTimeout(() => (mode === 'AUTO' ? fatRef : litresRef).current?.focus(), 0);
  }

  async function resolveProducer(q?: string) {
    const term = (q ?? ref).trim();
    if (!term) return;
    setNotFound(false);
    try {
      const { data } = await api.get<Page<Producer>>('/producers', { params: { q: term, size: 5 } });
      const match =
        data.content.find((p) => p.producerNumber.toLowerCase() === term.toLowerCase()) ?? data.content[0];
      if (match) {
        selectProducer(match);
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

  // AUTO: mirror the stable scale weight into the litres field
  useEffect(() => {
    if (mode !== 'AUTO' || !armed || !bridge.stableHeld) return;
    const kg = bridge.weightKg ?? 0;
    if (kg < 0.05) return;
    if (Math.abs(Number(litres) - kg) > 0.0005) setLitres(kg.toFixed(3));
    if (!capturedFocus.current && !fat) {
      capturedFocus.current = true;
      setTimeout(() => fatRef.current?.focus(), 0);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [mode, armed, bridge.stableHeld, bridge.weightKg]);

  // AUTO: re-arm once the pan has been emptied after a save
  useEffect(() => {
    if (mode === 'AUTO' && !armed && (bridge.weightKg ?? 1) < REARM_KG) setArmed(true);
  }, [mode, armed, bridge.weightKg]);

  function switchMode(m: CollectionMode) {
    if (m === mode) return;
    if (mode === 'MANUAL' && m === 'AUTO' && Number(litres) > 0) {
      if (!window.confirm('Switch to Automatic Scale? The quantity you typed will be replaced by the scale reading.')) {
        return;
      }
      setLitres('');
    }
    if (mode === 'AUTO' && m === 'MANUAL') {
      // keep the captured weight as an editable starting value — nothing is lost
      capturedFocus.current = false;
    }
    setArmed(true);
    setMode(m);
    try {
      localStorage.setItem(MODE_KEY, m);
    } catch {
      /* ignore */
    }
  }

  async function doTare() {
    const r = await bridge.tare();
    if (r.ok) toast.success('Tare command sent to the scale');
    else toast.error(r.instruction ?? 'Press the ZERO / TARE button on the scale');
  }

  const errors = {
    producer: !producer && !(notFound && ref.trim()) ? 'Scan or pick a producer' : '',
    litres:
      Number(litres) > 0
        ? ''
        : mode === 'AUTO'
          ? 'Waiting for a stable weight from the scale'
          : 'Enter litres',
    fat: Number(fat) > 0 ? '' : 'Enter FAT %',
    snf: Number(snf) > 0 ? '' : 'Enter SNF %',
  };
  const canSave = !errors.producer && !errors.litres && !errors.fat && !errors.snf;
  const showErr = (k: keyof typeof errors) => (triedSave ? errors[k] : '');

  function resetEntry(keepProducer: boolean) {
    setLitres('');
    setFat('');
    setSnf('');
    setPreview(null);
    setTriedSave(false);
    capturedFocus.current = false;
    if (mode === 'AUTO') setArmed(false); // wait for the pan to be emptied
    if (!keepProducer) {
      setRef('');
      setProducer(null);
      setNotFound(false);
    }
    producerInputRef.current?.focus();
  }

  async function save(alsoPrint = false) {
    setTriedSave(true);
    if (!canSave || saving) return;
    setSaving(true);
    const entryMode = mode;
    const payload = {
      clientTransactionId: uuid(),
      producerReference: producer?.producerNumber ?? ref.trim(),
      collectionDate: date,
      shift,
      litres: Number(litres),
      fat: Number(fat),
      snf: Number(snf),
      ...(entryMode === 'AUTO' ? { deviceId: SCALE_ID } : {}),
    };
    try {
      if (!navigator.onLine) throw new Error('offline');
      const { data } = await api.post<Collection>('/collections', payload);
      toast.success(`${data.collectionNumber} · ${fmtL(data.litres)} · ${money(data.breakdown.netAmount)}`);
      setLastSaved(data);
      setEntryModeById((m) => ({ ...m, [data.collectionNumber]: entryMode }));
      qc.invalidateQueries({ queryKey: ['collections'] });
      resetEntry(false);
      if (alsoPrint) print(data);
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

  const region = regions.data?.find((r) => r.id === producer?.regionId);

  // --- day / shift figures ---
  const rows = recent.data?.content ?? [];
  const shiftRows = rows.filter((c) => c.shift === shift);
  const collectedProducers = new Set(shiftRows.map((c) => c.producerId)).size;
  const shiftLitres = shiftRows.reduce((s, c) => s + Number(c.litres), 0);
  const expected = producerTotal.data ?? 0;

  const listRows = useMemo(() => {
    const q = listSearch.trim().toLowerCase();
    return rows
      .filter((c) => listShift === 'ALL' || c.shift === listShift)
      .filter(
        (c) =>
          !q ||
          [c.collectionNumber, c.producerName, c.producerNumber, String(c.producerId)]
            .filter(Boolean)
            .some((s) => String(s).toLowerCase().includes(q)),
      );
  }, [rows, listShift, listSearch]);
  const listActive = listShift !== 'ALL' || !!listSearch.trim();
  const justSaved = !!lastSaved && !litres && !fat && !snf;

  return (
    <>
      <PrintableReceipt collection={receiptFor} centerName={centerName} centerAddress={centerAddress} />

      {/* ---------- compact header ---------- */}
      <Stack
        direction={{ xs: 'column', md: 'row' }}
        alignItems={{ md: 'center' }}
        justifyContent="space-between"
        gap={2}
        sx={{ mb: 2 }}
      >
        <Box>
          <Typography variant="h5" sx={{ fontWeight: 700 }}>
            Milk Collection
          </Typography>
          <Box sx={{ mt: 0.5 }}>
            <DateNav value={date} onChange={setDate} />
          </Box>
        </Box>

        <Stack direction="row" spacing={1.5} alignItems="center" sx={{ flexWrap: 'wrap', rowGap: 1 }}>
          <ToggleButtonGroup
            exclusive
            size="small"
            color="primary"
            value={shift}
            onChange={(_, v: Shift | null) => {
              if (v) {
                shiftPickedManually.current = true;
                setShift(v);
              }
            }}
          >
            <ToggleButton value="MORNING" sx={{ px: 2 }}>
              <WbSunnyOutlinedIcon sx={{ mr: 0.75, fontSize: 18 }} /> Morning
            </ToggleButton>
            <ToggleButton value="EVENING" sx={{ px: 2 }}>
              <Brightness3OutlinedIcon sx={{ mr: 0.75, fontSize: 18 }} /> Evening
            </ToggleButton>
          </ToggleButtonGroup>

          {mode === 'AUTO' && <WeightMachineStatus status={bridge.status} onRetry={bridge.retry} />}

          <Figure label="Collected" value={expected ? `${collectedProducers} / ${expected}` : String(collectedProducers)} />
          <Figure label="Total litres" value={fmtL(shiftLitres)} />
        </Stack>
      </Stack>

      {/* ---------- compact progress ---------- */}
      {expected > 0 && (
        <LinearProgress
          variant="determinate"
          value={Math.min(100, (collectedProducers / expected) * 100)}
          sx={{ height: 6, borderRadius: 3, mb: 2 }}
        />
      )}

      {/* ---------- entry panel ---------- */}
      <Card sx={{ mb: 3 }}>
        <CardContent sx={{ p: { xs: 2, sm: 3 } }}>
          <Stack spacing={2.5}>
            {/* mode selector — always visible, top of the entry area */}
            <Stack direction="row" alignItems="center" justifyContent="space-between" flexWrap="wrap" gap={1}>
              <CollectionModeSelector value={mode} onChange={switchMode} />
              {mode === 'MANUAL' ? (
                <Chip size="small" color="warning" variant="outlined" label="Manual Entry Mode" sx={{ fontWeight: 600 }} />
              ) : (
                <WeightMachineStatus status={bridge.status} onRetry={bridge.retry} />
              )}
            </Stack>

            {/* producer */}
            <Box>
              <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} alignItems="flex-start">
                <Box sx={{ flex: 1, width: '100%' }}>
                  <ProducerSelector
                    value={producer}
                    rawText={ref}
                    recent={recentProducers}
                    error={!!showErr('producer')}
                    helper={
                      showErr('producer') ||
                      (producer
                        ? `${producer.producerName}${producer.regionId ? '' : ' — no region set'}`
                        : notFound
                          ? 'Not found — will be resolved on the server when you save'
                          : 'Scan the QR code or type the producer number / name, then Enter')
                    }
                    helperOk={!!producer}
                    inputRef={producerInputRef}
                    onRawChange={(v) => {
                      setRef(v);
                      setProducer(null);
                      setNotFound(false);
                    }}
                    onResolve={(v) => resolveProducer(v)}
                    onSelect={selectProducer}
                  />
                </Box>
                <Stack direction="row" spacing={1.5} sx={{ pt: { sm: 0.5 } }}>
                  <ReadOut label="Producer No" value={producer?.producerNumber ?? (notFound ? ref : '—')} />
                  <ReadOut
                    label="Region"
                    value={producer ? regionShort(region) : '—'}
                    warn={!!producer && !producer.regionId}
                  />
                </Stack>
              </Stack>
            </Box>

            {/* milk details */}
            <Box>
              <Typography variant="overline" color="text.secondary">
                Milk details
              </Typography>

              {mode === 'AUTO' && (
                <Box sx={{ mt: 1 }}>
                  <WeightDisplay
                    status={bridge.status}
                    weightKg={bridge.weightKg}
                    unit={bridge.unit}
                    stable={bridge.stable}
                    stableHeld={bridge.stableHeld}
                    armed={armed}
                  />
                  <Stack
                    direction={{ xs: 'column', sm: 'row' }}
                    spacing={1.5}
                    alignItems={{ sm: 'center' }}
                    sx={{ mt: 1.5 }}
                  >
                    <Box
                      sx={{
                        px: 2,
                        py: 1,
                        borderRadius: 2,
                        bgcolor: 'primary.light',
                        border: 1,
                        borderColor: 'primary.main',
                      }}
                    >
                      <Typography variant="caption" color="text.secondary">
                        Collection quantity
                      </Typography>
                      <Typography sx={{ fontSize: 20, fontWeight: 800 }} color="primary.dark">
                        {litres ? `${litres} L` : '—'}
                      </Typography>
                    </Box>
                    <Button
                      variant="outlined"
                      onClick={doTare}
                      disabled={bridge.status !== 'connected'}
                      sx={{ whiteSpace: 'nowrap' }}
                    >
                      ZERO / TARE
                    </Button>
                    {bridge.status !== 'connected' && (
                      <Button color="warning" variant="text" onClick={() => switchMode('MANUAL')}>
                        Scale unavailable — use Manual Entry
                      </Button>
                    )}
                  </Stack>
                  {!!showErr('litres') && (
                    <Typography variant="caption" color="error" sx={{ display: 'block', mt: 0.5 }}>
                      {showErr('litres')}
                    </Typography>
                  )}
                </Box>
              )}

              <Grid container spacing={2} sx={{ mt: 0 }}>
                {mode === 'MANUAL' && (
                  <Grid size={{ xs: 12, sm: 6 }}>
                    <TextField
                      label="Milk Quantity"
                      type="number"
                      value={litres}
                      onChange={(e) => setLitres(e.target.value)}
                      onKeyDown={(e) => {
                        if (isEnter(e)) {
                          e.preventDefault();
                          fatRef.current?.focus();
                        }
                      }}
                      error={!!showErr('litres')}
                      helperText={showErr('litres')}
                      inputRef={litresRef}
                      autoFocus={false}
                      inputProps={{ step: '0.1', min: '0', inputMode: 'decimal', sx: { fontSize: 26, fontWeight: 700, py: 1.25 } }}
                      InputProps={{ endAdornment: <InputAdornment position="end">L</InputAdornment> }}
                    />
                  </Grid>
                )}
                <Grid size={{ xs: 6, sm: mode === 'MANUAL' ? 3 : 6 }}>
                  <TextField
                    label="FAT"
                    type="number"
                    value={fat}
                    onChange={(e) => setFat(e.target.value)}
                    onKeyDown={(e) => {
                      if (isEnter(e)) {
                        e.preventDefault();
                        snfRef.current?.focus();
                      }
                    }}
                    error={!!showErr('fat')}
                    helperText={showErr('fat')}
                    inputRef={fatRef}
                    inputProps={{ step: '0.1', min: '0', inputMode: 'decimal', sx: { fontSize: 20, fontWeight: 700, py: 1.25 } }}
                    InputProps={{ endAdornment: <InputAdornment position="end">%</InputAdornment> }}
                  />
                </Grid>
                <Grid size={{ xs: 6, sm: mode === 'MANUAL' ? 3 : 6 }}>
                  <TextField
                    label="SNF"
                    type="number"
                    value={snf}
                    onChange={(e) => setSnf(e.target.value)}
                    onKeyDown={(e) => {
                      if (isEnter(e)) {
                        e.preventDefault();
                        save();
                      }
                    }}
                    error={!!showErr('snf')}
                    helperText={showErr('snf')}
                    inputRef={snfRef}
                    inputProps={{ step: '0.1', min: '0', inputMode: 'decimal', sx: { fontSize: 20, fontWeight: 700, py: 1.25 } }}
                    InputProps={{ endAdornment: <InputAdornment position="end">%</InputAdornment> }}
                  />
                </Grid>
              </Grid>
            </Box>

            {/* calculation summary */}
            {justSaved && lastSaved ? (
              <Box
                sx={{
                  bgcolor: 'primary.light',
                  border: 1,
                  borderColor: 'primary.main',
                  borderRadius: 2,
                  p: 2,
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                  gap: 1.5,
                  flexWrap: 'wrap',
                }}
              >
                <Stack direction="row" spacing={1.25} alignItems="center">
                  <CheckCircleIcon sx={{ color: 'success.main' }} />
                  <Box>
                    <Typography variant="body2" fontWeight={700}>
                      Saved · {lastSaved.collectionNumber}
                    </Typography>
                    <Typography variant="caption" color="text.secondary">
                      {fmtL(lastSaved.litres)} @ {fmtRate(lastSaved.breakdown.ratePerLitre)} · Net{' '}
                      <b>{money(lastSaved.breakdown.netAmount)}</b>
                    </Typography>
                  </Box>
                </Stack>
                <Button size="small" variant="outlined" startIcon={<PrintIcon />} onClick={() => print(lastSaved)}>
                  Print
                </Button>
              </Box>
            ) : (
              <AmountSummary preview={preview} estBase={estBase} />
            )}

            {/* actions */}
            <Stack
              direction={{ xs: 'column', sm: 'row' }}
              spacing={1.5}
              sx={{
                position: { xs: 'sticky', md: 'static' },
                bottom: 8,
                zIndex: 2,
                bgcolor: 'background.paper',
                pt: { xs: 1, md: 0 },
              }}
            >
              <Button variant="contained" size="large" fullWidth disabled={saving} onClick={() => save()}>
                {saving ? 'Saving…' : 'Save Collection'}
              </Button>
              <Button
                variant="outlined"
                size="large"
                startIcon={<PrintIcon />}
                disabled={saving}
                onClick={() => save(true)}
                sx={{ whiteSpace: 'nowrap' }}
              >
                Save &amp; Print
              </Button>
              <Button size="large" color="inherit" onClick={() => resetEntry(false)} disabled={saving}>
                Clear
              </Button>
            </Stack>
          </Stack>
        </CardContent>
      </Card>

      {/* ---------- recent collections ---------- */}
      <Stack
        direction={{ xs: 'column', sm: 'row' }}
        alignItems={{ sm: 'center' }}
        justifyContent="space-between"
        gap={1.5}
        sx={{ mb: 1.5 }}
      >
        <Typography variant="h6" sx={{ fontWeight: 600 }}>
          Recent collections
          <Typography component="span" variant="body2" color="text.secondary" sx={{ ml: 1 }}>
            {listRows.length} {listRows.length === 1 ? 'entry' : 'entries'}
          </Typography>
        </Typography>
        <Stack direction="row" spacing={1} alignItems="center">
          <ToggleButtonGroup
            exclusive
            size="small"
            value={listShift}
            onChange={(_, v: 'ALL' | Shift | null) => v && setListShift(v)}
          >
            <ToggleButton value="ALL" sx={{ px: 1.5 }}>
              All
            </ToggleButton>
            <ToggleButton value="MORNING" sx={{ px: 1.5 }}>
              AM
            </ToggleButton>
            <ToggleButton value="EVENING" sx={{ px: 1.5 }}>
              PM
            </ToggleButton>
          </ToggleButtonGroup>
          <TextField
            size="small"
            placeholder="Search this list"
            value={listSearch}
            onChange={(e) => setListSearch(e.target.value)}
            sx={{ width: { xs: '100%', sm: 200 } }}
          />
        </Stack>
      </Stack>

      <Card>
        <TableContainer sx={{ maxHeight: 520 }}>
          <Table size="small" stickyHeader>
            <TableHead>
              <TableRow>
                <TableCell>Producer No</TableCell>
                <TableCell>Producer</TableCell>
                <TableCell>Shift</TableCell>
                <TableCell align="right">Litres</TableCell>
                <TableCell align="right">FAT</TableCell>
                <TableCell align="right">SNF</TableCell>
                <TableCell align="right">Rate</TableCell>
                <TableCell align="right">Amount</TableCell>
                <TableCell>Mode</TableCell>
                <TableCell>Status</TableCell>
                <TableCell align="right" />
              </TableRow>
            </TableHead>
            <TableBody>
              {listRows.map((c) => (
                <TableRow key={c.id} hover>
                  <TableCell sx={{ fontWeight: 600 }}>{c.producerNumber ?? `#${c.producerId}`}</TableCell>
                  <TableCell>{c.producerName ?? '—'}</TableCell>
                  <TableCell>
                    <Chip size="small" variant="outlined" label={c.shift === 'MORNING' ? 'AM' : 'PM'} />
                  </TableCell>
                  <TableCell align="right">{fmtL(c.litres)}</TableCell>
                  <TableCell align="right">{Number(c.fat).toFixed(1)}</TableCell>
                  <TableCell align="right">{Number(c.snf).toFixed(1)}</TableCell>
                  <TableCell align="right">{fmtRate(c.breakdown.ratePerLitre)}</TableCell>
                  <TableCell align="right" sx={{ fontWeight: 700 }}>
                    {money(c.breakdown.netAmount)}
                  </TableCell>
                  <TableCell>
                    {entryModeById[c.collectionNumber] ? (
                      <Chip
                        size="small"
                        variant="outlined"
                        icon={entryModeById[c.collectionNumber] === 'AUTO' ? <ScaleOutlinedIcon /> : undefined}
                        label={entryModeById[c.collectionNumber]}
                      />
                    ) : (
                      <Typography variant="caption" color="text.secondary">
                        —
                      </Typography>
                    )}
                  </TableCell>
                  <TableCell>
                    <StatusChip status={c.status} />
                  </TableCell>
                  <TableCell align="right" padding="none">
                    <Tooltip title="Print receipt">
                      <IconButton size="small" onClick={() => print(c)}>
                        <PrintIcon fontSize="small" />
                      </IconButton>
                    </Tooltip>
                  </TableCell>
                </TableRow>
              ))}
              {listRows.length === 0 && (
                <TableRow>
                  <TableCell colSpan={11} sx={{ textAlign: 'center', py: 5, color: 'text.secondary' }}>
                    {listActive ? (
                      'No collections match this filter.'
                    ) : (
                      <>
                        No milk collections recorded for this shift yet.
                        <Box sx={{ mt: 1.5 }}>
                          <Button variant="contained" size="small" onClick={() => producerInputRef.current?.focus()}>
                            Start collection
                          </Button>
                        </Box>
                      </>
                    )}
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        </TableContainer>
      </Card>
    </>
  );
}

/* ----------------------------------------------------------------- pieces */

/** Compact, modern date navigator: prev/next day arrows + a click-to-open calendar. */
function DateNav({ value, onChange }: { value: string; onChange: (v: string) => void }) {
  const selected = parseISO(value);
  const isToday = isSameDay(selected, new Date());
  const go = (days: number) => onChange(formatDate(addDays(selected, days), 'yyyy-MM-dd'));

  return (
    <Stack direction="row" alignItems="center" spacing={0.25}>
      <Stack
        direction="row"
        alignItems="center"
        sx={{
          bgcolor: 'action.hover',
          borderRadius: 999,
          pl: 0.5,
          pr: 0.5,
          transition: 'background-color 120ms ease',
          '&:hover': { bgcolor: 'action.selected' },
        }}
      >
        <Tooltip title="Previous day">
          <IconButton size="small" onClick={() => go(-1)} aria-label="Previous day">
            <ChevronLeftIcon fontSize="small" />
          </IconButton>
        </Tooltip>

        <DatePicker
          value={selected}
          maxDate={new Date()}
          onChange={(v) => v && onChange(formatDate(v, 'yyyy-MM-dd'))}
          format="EEE, d MMM yyyy"
          slots={{ openPickerIcon: CalendarMonthOutlinedIcon }}
          slotProps={{
            textField: {
              variant: 'standard',
              size: 'small',
              InputProps: { disableUnderline: true },
              sx: {
                width: 168,
                '& input': {
                  fontSize: 13,
                  fontWeight: 700,
                  textAlign: 'center',
                  cursor: 'pointer',
                  caretColor: 'transparent',
                },
              },
            },
            openPickerButton: { size: 'small', sx: { '& svg': { fontSize: 18 } } },
            popper: { placement: 'bottom-start' },
          }}
        />

        <Tooltip title={isToday ? "You're on today" : 'Next day'}>
          <span>
            <IconButton size="small" onClick={() => go(1)} disabled={isToday} aria-label="Next day">
              <ChevronRightIcon fontSize="small" />
            </IconButton>
          </span>
        </Tooltip>
      </Stack>

      <Tooltip title={isToday ? "You're on today" : 'Jump to today'}>
        <span>
          <Chip
            size="small"
            label="Today"
            disabled={isToday}
            onClick={() => onChange(today())}
            sx={{ fontWeight: 600, cursor: isToday ? 'default' : 'pointer' }}
          />
        </span>
      </Tooltip>
    </Stack>
  );
}

function Figure({ label, value }: { label: string; value: string }) {
  return (
    <Box
      sx={{
        px: 1.5,
        py: 0.5,
        borderRadius: 2,
        bgcolor: 'action.hover',
        minWidth: 92,
        textAlign: 'center',
      }}
    >
      <Typography variant="caption" color="text.secondary" sx={{ display: 'block', lineHeight: 1.2 }}>
        {label}
      </Typography>
      <Typography sx={{ fontWeight: 700, fontSize: 15 }}>{value}</Typography>
    </Box>
  );
}

function ReadOut({ label, value, warn }: { label: string; value: ReactNode; warn?: boolean }) {
  return (
    <Box sx={{ minWidth: 96 }}>
      <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>
        {label}
      </Typography>
      <Typography sx={{ fontWeight: 600, fontSize: 14, color: warn ? 'warning.main' : 'text.primary' }} noWrap>
        {value}
      </Typography>
    </Box>
  );
}

function AmountSummary({ preview, estBase }: { preview: RatePreview | null; estBase: number | null }) {
  return (
    <Box sx={{ bgcolor: 'primary.light', borderRadius: 2, p: 2 }}>
      <Stack
        direction="row"
        spacing={2}
        divider={<Box sx={{ width: '1px', bgcolor: 'primary.main', opacity: 0.25 }} />}
        sx={{ flexWrap: 'wrap', rowGap: 1 }}
      >
        <Mini label="Rate / L" value={preview ? fmtRate(preview.ratePerLitre) : '—'} />
        <Mini label="Milk amount" value={estBase != null ? money(estBase) : '—'} />
        <Mini label="Incentive" value="—" />
        <Mini label="Deduction" value="—" />
      </Stack>
      <Stack direction="row" justifyContent="space-between" alignItems="baseline" sx={{ mt: 1.5 }}>
        <Box>
          <Typography variant="caption" color="text.secondary">
            Estimated net {preview && <Chip label={preview.scope} size="small" variant="outlined" sx={{ ml: 0.5 }} />}
          </Typography>
          <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>
            incentive &amp; deduction are applied on save
          </Typography>
        </Box>
        <Typography sx={{ fontSize: 30, fontWeight: 800 }} color="primary.dark">
          {estBase != null ? money(estBase) : '—'}
        </Typography>
      </Stack>
    </Box>
  );
}

function Mini({ label, value }: { label: string; value: string }) {
  return (
    <Box>
      <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>
        {label}
      </Typography>
      <Typography sx={{ fontWeight: 700, fontSize: 14 }} color="primary.dark">
        {value}
      </Typography>
    </Box>
  );
}

/**
 * Plain, scanner-perfect producer input: type the number or name (or let a
 * QR/barcode scanner type it) then Enter — the parent resolves it against the
 * API and moves focus to Litres. Recently used producers appear as one-tap
 * chips while the field is empty.
 */
function ProducerSelector({
  value,
  rawText,
  recent,
  error,
  helper,
  helperOk,
  inputRef,
  onRawChange,
  onResolve,
  onSelect,
}: {
  value: Producer | null;
  rawText: string;
  recent: Producer[];
  error: boolean;
  helper: string;
  helperOk: boolean;
  inputRef: RefObject<HTMLInputElement>;
  onRawChange: (v: string) => void;
  onResolve: (v: string) => void;
  onSelect: (p: Producer) => void;
}) {
  return (
    <Box>
      <TextField
        inputRef={inputRef}
        autoFocus
        fullWidth
        label="Producer — scan QR or type number / name"
        value={rawText}
        onChange={(e) => onRawChange(e.target.value)}
        onKeyDown={(e) => {
          if (isEnter(e)) {
            e.preventDefault();
            onResolve(rawText);
          }
        }}
        onBlur={() => {
          if (rawText.trim() && !value) onResolve(rawText);
        }}
        error={error}
        helperText={helper}
        FormHelperTextProps={{ sx: { color: helperOk ? 'success.main' : undefined, fontWeight: helperOk ? 600 : 400 } }}
        InputProps={{
          sx: { fontSize: 18, fontWeight: 600 },
          endAdornment: (
            <InputAdornment position="end">
              <Tooltip title="Scan the producer QR code, or type the number / name and press Enter">
                <QrCodeScannerIcon color="action" />
              </Tooltip>
            </InputAdornment>
          ),
        }}
        inputProps={{ autoComplete: 'off' }}
      />
      {!value && recent.length > 0 && (
        <Stack direction="row" spacing={1} sx={{ mt: 1, flexWrap: 'wrap', rowGap: 1, alignItems: 'center' }}>
          <Typography variant="caption" color="text.secondary">
            Recent:
          </Typography>
          {recent.map((p) => (
            <Chip
              key={p.id}
              size="small"
              label={p.producerNumber}
              onClick={() => onSelect(p)}
              sx={{ cursor: 'pointer' }}
            />
          ))}
        </Stack>
      )}
    </Box>
  );
}
