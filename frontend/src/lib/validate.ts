/**
 * Small field-validation helpers shared by every form.
 * Each returns an error string, or '' when the value is acceptable.
 * `optional` validators pass on an empty value.
 */

export const onlyDigits = (v: string) => v.replace(/\D/g, '');
export const upper = (v: string) => v.toUpperCase();

export function required(label = 'This field'): (v: string) => string {
  return (v) => (v && v.trim() ? '' : `${label} is required`);
}

export function mobile(v: string, opt = true): string {
  const s = v.trim();
  if (!s) return opt ? '' : 'Mobile number is required';
  if (!/^[6-9]\d{9}$/.test(s)) return 'Enter a 10-digit Indian mobile number';
  return '';
}

export function aadhaar(v: string, opt = true): string {
  const s = onlyDigits(v);
  if (!s) return opt ? '' : 'Aadhaar number is required';
  if (!/^\d{12}$/.test(s)) return 'Aadhaar must be 12 digits';
  return '';
}

export function email(v: string, opt = true): string {
  const s = v.trim();
  if (!s) return opt ? '' : 'Email is required';
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/.test(s)) return 'Enter a valid email address';
  return '';
}

/** true when every error string in the map is empty */
export function allValid(errors: Record<string, string>): boolean {
  return Object.values(errors).every((e) => !e);
}
