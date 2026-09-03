'use strict';
require('dotenv').config();

function str(name, def) {
  const v = process.env[name];
  return v === undefined || v === '' ? def : v;
}
function num(name, def) {
  const v = Number(process.env[name]);
  return Number.isFinite(v) ? v : def;
}
function unescapeDelim(s) {
  return String(s).replace(/\\r/g, '\r').replace(/\\n/g, '\n').replace(/\\t/g, '\t');
}

const config = {
  httpPort: num('HTTP_PORT', 8787),
  mock: process.env.MOCK === '1' || process.env.MOCK === 'true',

  serial: {
    path: str('SCALE_PORT', ''),
    baudRate: num('BAUD_RATE', 9600),
    dataBits: num('DATA_BITS', 8),
    stopBits: num('STOP_BITS', 1),
    parity: str('PARITY', 'none'),
    delimiter: unescapeDelim(str('LINE_DELIMITER', '\r\n')),
  },

  parse: {
    weightRegex: str('WEIGHT_REGEX', '(?<sign>[+-]?)\\s*(?<weight>\\d+(?:\\.\\d+)?)\\s*(?<unit>kg|KG|g|G|lb|LB)?'),
    stableRegex: str('STABLE_REGEX', ''),
    defaultUnit: str('DEFAULT_UNIT', 'kg'),
  },

  stability: {
    thresholdKg: num('STABLE_THRESHOLD_KG', 0.02),
    durationMs: num('STABLE_DURATION_MS', 700),
  },

  tareCommand: str('TARE_COMMAND', ''),
  frameStaleMs: num('FRAME_STALE_MS', 2500),
  reconnectMs: num('RECONNECT_MS', 2000),
  logLevel: str('LOG_LEVEL', 'info'),
};

module.exports = config;
