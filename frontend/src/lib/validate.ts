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

export function accountNumber(v: string, opt = false): string {
  const s = v.trim();
  if (!s) return opt ? '' : 'Account number is required';
  if (!/^\d+$/.test(s)) return 'Digits only — no letters or spaces';
  if (s.length < 9 || s.length > 18) return 'Must be 9–18 digits';
  return '';
}

export function ifsc(v: string, opt = false): string {
  const s = v.trim().toUpperCase();
  if (!s) return opt ? '' : 'IFSC code is required';
  if (!/^[A-Z]{4}0[A-Z0-9]{6}$/.test(s)) return 'Format: 4 letters, 0, then 6 (e.g. HDFC0001234)';
  return '';
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

export function upiId(v: string, opt = true): string {
  const s = v.trim();
  if (!s) return opt ? '' : 'UPI ID is required';
  if (!/^[a-zA-Z0-9.\-_]{2,256}@[a-zA-Z]{2,64}$/.test(s)) return 'Format: name@bank (e.g. ramesh@okhdfcbank)';
  return '';
}

export function ifscExists(v: string): boolean {
  return /^[A-Z]{4}0[A-Z0-9]{6}$/.test(v.trim().toUpperCase());
}

/** true when every error string in the map is empty */
export function allValid(errors: Record<string, string>): boolean {
  return Object.values(errors).every((e) => !e);
}
