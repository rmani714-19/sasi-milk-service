import { useMemo } from 'react';
import { useQuery } from '@tanstack/react-query';
import { api } from '../lib/api';
import type { Driver, Page, Producer, Region } from './types';

export function useRegions() {
  return useQuery({
    queryKey: ['regions'],
    queryFn: async () => (await api.get<Region[]>('/regions')).data,
    staleTime: 5 * 60_000,
  });
}

/** id → Producer map for pages that list rows keyed only by producerId. */
export function useProducerLookup() {
  const q = useQuery({
    queryKey: ['producers', 'lookup'],
    queryFn: async () =>
      (await api.get<Page<Producer>>('/producers', { params: { size: 500 } })).data.content,
    staleTime: 5 * 60_000,
  });
  const byId = useMemo(() => {
    const m = new Map<number, Producer>();
    (q.data ?? []).forEach((p) => m.set(p.id, p));
    return m;
  }, [q.data]);
  const label = (id: number) => {
    const p = byId.get(id);
    return p ? `${p.producerNumber} · ${p.producerName}` : `#${id}`;
  };
  return { byId, label, isLoading: q.isLoading };
}

/** id → Driver map, for vehicle assignment pickers and status displays. */
export function useDriverLookup() {
  const q = useQuery({
    queryKey: ['drivers', 'lookup'],
    queryFn: async () => (await api.get<Driver[]>('/drivers')).data,
    staleTime: 5 * 60_000,
  });
  const byId = useMemo(() => {
    const m = new Map<number, Driver>();
    (q.data ?? []).forEach((d) => m.set(d.id, d));
    return m;
  }, [q.data]);
  const label = (id?: number | null) => {
    if (id == null) return '—';
    const d = byId.get(id);
    return d ? `${d.code} · ${d.name}` : `#${id}`;
  };
  return { data: q.data, byId, label, isLoading: q.isLoading };
}

/** Fetch a file (report export etc.) with auth and trigger a browser download. */
export async function downloadFile(url: string, filename: string) {
  const res = await api.get(url, { responseType: 'blob' });
  const blob = new Blob([res.data], {
    type: (res.headers as Record<string, string>)['content-type'] || 'application/octet-stream',
  });
  const href = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = href;
  a.download = filename;
  a.click();
  URL.revokeObjectURL(href);
}

/**
 * Fetch a PDF with auth and open it in a new tab for preview/printing. The tab is opened first,
 * synchronously, so the browser does not treat it as a blocked pop-up.
 */
export async function openPdf(url: string) {
  const tab = window.open('', '_blank');
  try {
    const res = await api.get(url, { responseType: 'blob' });
    const href = URL.createObjectURL(new Blob([res.data], { type: 'application/pdf' }));
    if (tab) {
      tab.location.href = href;
    } else {
      window.location.href = href;
    }
    setTimeout(() => URL.revokeObjectURL(href), 60_000);
  } catch (e) {
    tab?.close();
    throw e;
  }
}
