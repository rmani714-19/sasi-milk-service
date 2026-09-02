import type { Region } from '../api/types';

/**
 * Regions are identified as {area}-{dairy number}-{place} for the local
 * producers who walk their milk straight to the dairy.
 *
 * Storage convention (uses the existing /regions fields, no schema change):
 *   code        = "{AREA}-{DAIRY_NO}"   (unique)
 *   name        = "{Place}"
 *   description = full human label, kept in sync for search/exports
 */

export interface RegionParts {
  area: string;
  dairyNo: string;
  place: string;
}

export function splitRegionCode(code: string): { area: string; dairyNo: string } {
  const i = code.lastIndexOf('-');
  if (i <= 0) return { area: code, dairyNo: '' };
  return { area: code.slice(0, i), dairyNo: code.slice(i + 1) };
}

export function makeRegionCode(area: string, dairyNo: string): string {
  return `${area.trim().toUpperCase()}-${dairyNo.trim()}`;
}

export function regionLabel(r: Region | undefined | null): string {
  if (!r) return '—';
  const { area, dairyNo } = splitRegionCode(r.code);
  if (dairyNo) return `${area}-${dairyNo}-${r.name}`;
  return `${r.code} · ${r.name}`;
}

export function regionDescription(area: string, dairyNo: string, place: string): string {
  return `${area.trim().toUpperCase()} · Dairy ${dairyNo.trim()} · ${place.trim()}`;
}

/** short label for tight table cells */
export function regionShort(r: Region | undefined | null): string {
  if (!r) return '—';
  const { area, dairyNo } = splitRegionCode(r.code);
  return dairyNo ? `${area}-${dairyNo}` : r.code;
}
