import { openDB, type DBSchema, type IDBPDatabase } from 'idb';
import { api } from './api';

export interface PendingCollection {
  clientTransactionId: string;
  producerReference: string;
  collectionDate: string;
  shift: 'MORNING' | 'EVENING';
  litres: number;
  fat: number;
  snf: number;
  source: 'OFFLINE_SYNC';
  queuedAt: number;
  lastError?: string;
}

interface OfflineDB extends DBSchema {
  'pending-collections': { key: string; value: PendingCollection };
}

let dbp: Promise<IDBPDatabase<OfflineDB>> | null = null;
function db() {
  dbp ??= openDB<OfflineDB>('sasi-offline', 1, {
    upgrade(d) {
      d.createObjectStore('pending-collections', { keyPath: 'clientTransactionId' });
    },
  });
  return dbp;
}

export async function queueCollection(c: Omit<PendingCollection, 'queuedAt' | 'source'>) {
  const d = await db();
  await d.put('pending-collections', { ...c, source: 'OFFLINE_SYNC', queuedAt: Date.now() });
}

export async function listPending(): Promise<PendingCollection[]> {
  const d = await db();
  return (await d.getAll('pending-collections')).sort((a, b) => a.queuedAt - b.queuedAt);
}

export async function countPending(): Promise<number> {
  const d = await db();
  return d.count('pending-collections');
}

/** Push every queued collection; drop those the server accepts (incl. duplicates). */
export async function syncPending(): Promise<{ synced: number; failed: number }> {
  const d = await db();
  const pending = await listPending();
  let synced = 0;
  let failed = 0;
  for (const p of pending) {
    try {
      await api.post('/collections', {
        clientTransactionId: p.clientTransactionId,
        producerReference: p.producerReference,
        collectionDate: p.collectionDate,
        shift: p.shift,
        litres: p.litres,
        fat: p.fat,
        snf: p.snf,
        source: 'OFFLINE_SYNC',
      });
      await d.delete('pending-collections', p.clientTransactionId);
      synced += 1;
    } catch (err) {
      const status = (err as { response?: { status?: number } }).response?.status;
      if (status && status >= 400 && status < 500 && status !== 409) {
        // permanent rejection (e.g. bad producer) — drop it so the queue can drain
        await d.delete('pending-collections', p.clientTransactionId);
      } else {
        await d.put('pending-collections', {
          ...p,
          lastError: (err as { message?: string }).message ?? 'sync failed',
        });
      }
      failed += 1;
    }
  }
  return { synced, failed };
}
