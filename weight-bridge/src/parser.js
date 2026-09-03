'use strict';

/**
 * Configurable frame parser.
 *
 * The iScale i-05-RS232 wire format is NOT hard-coded. `weightRegex` (from the
 * WEIGHT_REGEX env var) must be tuned to the frames the real device sends —
 * see `npm run sniff`. It only needs a named capture group `weight`; `unit`,
 * `sign` and `stable` are honoured if present.
 */
function makeParser(cfg) {
  let weightRe;
  try {
    weightRe = new RegExp(cfg.parse.weightRegex);
  } catch (e) {
    throw new Error('Invalid WEIGHT_REGEX: ' + e.message);
  }
  const stableRe = cfg.parse.stableRegex ? new RegExp(cfg.parse.stableRegex) : null;
  // strip NUL and C0/C1 control bytes, keep printable frame content
  const CONTROL = new RegExp("[\\x00-\\x1F\\x7F-\\x9F]", "g");

  function toKg(value, unit) {
    const u = (unit || cfg.parse.defaultUnit || 'kg').toLowerCase();
    if (u === 'g') return value / 1000;
    if (u === 'lb') return value * 0.45359237;
    return value; // kg
  }

  /** @returns {{weightKg:number, unit:string, stable:(boolean|null), raw:string}|null} */
  function parseLine(line) {
    const raw = String(line).replace(CONTROL, '').trim();
    if (!raw) return null;
    const m = weightRe.exec(raw);
    if (!m || !m.groups || m.groups.weight === undefined) return null;

    let value = Number(m.groups.weight);
    if (!Number.isFinite(value)) return null;
    if (m.groups.sign === '-') value = -value;

    const unit = m.groups.unit || cfg.parse.defaultUnit || 'kg';

    let stable = null;
    if (m.groups.stable !== undefined) {
      stable = /^(st|stable|s)$/i.test(String(m.groups.stable));
    } else if (stableRe) {
      stable = stableRe.test(raw);
    }

    return { weightKg: round3(toKg(value, unit)), unit: String(unit).toLowerCase(), stable, raw };
  }

  return { parseLine };
}

function round3(n) {
  return Math.round(n * 1000) / 1000;
}

module.exports = { makeParser, round3 };
