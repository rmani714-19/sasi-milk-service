export const money = (v: number | string | null | undefined): string => {
  const n = typeof v === 'string' ? Number(v) : v ?? 0;
  return `₹${(n || 0).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
};

export const litres = (v: number | string | null | undefined): string => {
  const n = typeof v === 'string' ? Number(v) : v ?? 0;
  return `${(n || 0).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })} L`;
};

export const rate = (v: number | string | null | undefined): string => {
  const n = typeof v === 'string' ? Number(v) : v ?? 0;
  return `₹${(n || 0).toFixed(4)}`;
};

export const today = (): string => new Date().toISOString().slice(0, 10);

export const uuid = (): string =>
  typeof crypto !== 'undefined' && 'randomUUID' in crypto
    ? crypto.randomUUID()
    : `${Date.now()}-${Math.random().toString(16).slice(2)}`;
