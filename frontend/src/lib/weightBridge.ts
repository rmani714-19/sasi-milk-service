import { useCallback, useEffect, useRef, useState } from 'react';

/**
 * Talks to the local Weight Bridge (see /weight-bridge) that reads the
 * iScale i-05-RS232 over RS-232. The bridge always runs on the collection PC,
 * so this works even when the internet is down.
 */

export const WEIGHT_BRIDGE_URL: string =
  (import.meta.env.VITE_WEIGHT_BRIDGE_URL as string | undefined)?.replace(/\/+$/, '') ||
  'http://localhost:8787';

const WS_URL = WEIGHT_BRIDGE_URL.replace(/^http/, 'ws');

/** how long a weight frame stays "fresh" on the client */
const FRESH_MS = 3000;
/** stable must hold this long before we treat it as capture-ready */
const STABLE_HOLD_MS = 400;

export type BridgeStatus = 'connecting' | 'connected' | 'no-scale' | 'unavailable';

export interface WeightBridge {
  status: BridgeStatus;
  weightKg: number | null;
  unit: string;
  stable: boolean;
  /** stable AND it has held for STABLE_HOLD_MS — safe to capture */
  stableHeld: boolean;
  raw: string;
  lastFrameAt: number | null;
  tareSupported: boolean;
  /** POST /tare — resolves { ok, instruction? } */
  tare: () => Promise<{ ok: boolean; instruction?: string }>;
  /** force an immediate reconnect */
  retry: () => void;
}

export function useWeightBridge(enabled: boolean): WeightBridge {
  const [weightKg, setWeightKg] = useState<number | null>(null);
  const [unit, setUnit] = useState('kg');
  const [stable, setStable] = useState(false);
  const [stableHeld, setStableHeld] = useState(false);
  const [raw, setRaw] = useState('');
  const [lastFrameAt, setLastFrameAt] = useState<number | null>(null);
  const [tareSupported, setTareSupported] = useState(false);
  const [wsOpen, setWsOpen] = useState(false);
  const [bridgeOpen, setBridgeOpen] = useState(false); // scale/serial open per status frame
  const [nowTick, setNowTick] = useState(0);

  const wsRef = useRef<WebSocket | null>(null);
  const reconnectRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const stableSinceRef = useRef<number | null>(null);
  const enabledRef = useRef(enabled);
  enabledRef.current = enabled;

  const connect = useCallback(() => {
    if (!enabledRef.current) return;
    try {
      const ws = new WebSocket(WS_URL);
      wsRef.current = ws;
      ws.onopen = () => setWsOpen(true);
      ws.onclose = () => {
        setWsOpen(false);
        setBridgeOpen(false);
        if (enabledRef.current) {
          reconnectRef.current = setTimeout(connect, 2000);
        }
      };
      ws.onerror = () => ws.close();
      ws.onmessage = (ev) => {
        let msg: Record<string, unknown>;
        try {
          msg = JSON.parse(ev.data as string);
        } catch {
          return;
        }
        if (msg.type === 'weight') {
          const kg = typeof msg.weightKg === 'number' ? msg.weightKg : null;
          const st = !!msg.stable;
          setWeightKg(kg);
          setUnit(typeof msg.unit === 'string' ? msg.unit : 'kg');
          setRaw(typeof msg.raw === 'string' ? msg.raw : '');
          setLastFrameAt(typeof msg.at === 'number' && msg.at > 0 ? msg.at : Date.now());
          setStable(st);
          if (st) {
            stableSinceRef.current ??= Date.now();
            setStableHeld(Date.now() - (stableSinceRef.current ?? 0) >= STABLE_HOLD_MS);
          } else {
            stableSinceRef.current = null;
            setStableHeld(false);
          }
        } else if (msg.type === 'status') {
          setBridgeOpen(!!msg.open);
          setTareSupported(!!msg.tareSupported);
        }
      };
    } catch {
      reconnectRef.current = setTimeout(connect, 2000);
    }
  }, []);

  useEffect(() => {
    if (!enabled) {
      wsRef.current?.close();
      wsRef.current = null;
      if (reconnectRef.current) clearTimeout(reconnectRef.current);
      setWsOpen(false);
      setBridgeOpen(false);
      setWeightKg(null);
      setStable(false);
      setStableHeld(false);
      setLastFrameAt(null);
      return;
    }
    connect();
    const tick = setInterval(() => setNowTick((n) => (n + 1) % 1_000_000), 1000);
    return () => {
      clearInterval(tick);
      if (reconnectRef.current) clearTimeout(reconnectRef.current);
      wsRef.current?.close();
      wsRef.current = null;
    };
  }, [enabled, connect]);

  // keep stableHeld honest as time passes even without new frames
  useEffect(() => {
    if (stable && stableSinceRef.current) {
      setStableHeld(Date.now() - stableSinceRef.current >= STABLE_HOLD_MS);
    }
  }, [stable, nowTick]);

  const fresh = lastFrameAt != null && Date.now() - lastFrameAt < FRESH_MS;

  let status: BridgeStatus;
  if (!enabled) status = 'connecting';
  else if (!wsOpen) status = 'unavailable';
  else if (bridgeOpen && fresh && weightKg != null) status = 'connected';
  else status = 'no-scale';

  const tare = useCallback(async () => {
    try {
      const res = await fetch(`${WEIGHT_BRIDGE_URL}/tare`, { method: 'POST' });
      if (res.ok) return { ok: true as const };
      const body = await res.json().catch(() => ({}));
      return { ok: false as const, instruction: body.instruction as string | undefined };
    } catch {
      return { ok: false as const, instruction: 'Weight bridge is not reachable.' };
    }
  }, []);

  const retry = useCallback(() => {
    if (reconnectRef.current) clearTimeout(reconnectRef.current);
    wsRef.current?.close();
    connect();
  }, [connect]);

  return {
    status,
    weightKg: status === 'connected' || status === 'no-scale' ? weightKg : null,
    unit,
    stable: status === 'connected' && stable,
    stableHeld: status === 'connected' && stableHeld,
    raw,
    lastFrameAt,
    tareSupported,
    tare,
    retry,
  };
}
