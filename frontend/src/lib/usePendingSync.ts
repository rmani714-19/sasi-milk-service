import { useCallback, useEffect, useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { countPending, syncPending } from './offline';

/** Tracks the offline collection queue + online status, and drains it on reconnect. */
export function usePendingSync() {
  const qc = useQueryClient();
  const [pending, setPending] = useState(0);
  const [online, setOnline] = useState(navigator.onLine);
  const [syncing, setSyncing] = useState(false);

  const refresh = useCallback(async () => setPending(await countPending()), []);

  const sync = useCallback(async () => {
    if (syncing) return;
    setSyncing(true);
    try {
      const res = await syncPending();
      if (res.synced > 0) qc.invalidateQueries({ queryKey: ['collections'] });
    } finally {
      setSyncing(false);
      await refresh();
    }
  }, [qc, refresh, syncing]);

  useEffect(() => {
    void refresh();
    const goOnline = () => {
      setOnline(true);
      void sync();
    };
    const goOffline = () => setOnline(false);
    const onQueued = () => void refresh();
    window.addEventListener('online', goOnline);
    window.addEventListener('offline', goOffline);
    window.addEventListener('sasi:queued', onQueued);
    const t = setInterval(refresh, 5000);
    return () => {
      window.removeEventListener('online', goOnline);
      window.removeEventListener('offline', goOffline);
      window.removeEventListener('sasi:queued', onQueued);
      clearInterval(t);
    };
  }, [refresh, sync]);

  return { pending, online, syncing, sync, refresh };
}
