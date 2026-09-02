import { useQuery } from '@tanstack/react-query';
import { api } from '../lib/api';
import type { Region } from './types';

export function useRegions() {
  return useQuery({
    queryKey: ['regions'],
    queryFn: async () => (await api.get<Region[]>('/regions')).data,
    staleTime: 5 * 60_000,
  });
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
