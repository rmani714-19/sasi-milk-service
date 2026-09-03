#!/usr/bin/env node
'use strict';
/**
 * Raw serial sniffer. Run this FIRST, before touching WEIGHT_REGEX:
 *
 *   npm run sniff
 *
 * It opens SCALE_PORT with the configured line settings and prints every
 * frame the scale sends — both as text and as hex — so you can see the real
 * iScale i-05-RS232 protocol and build a matching WEIGHT_REGEX.
 */
const config = require('./config');

if (!config.serial.path) {
  console.error('SCALE_PORT is not set. Copy .env.example to .env and set it. Run `npm run ports` to list ports.');
  process.exit(1);
}

let SerialPort;
try {
  ({ SerialPort } = require('serialport'));
} catch (e) {
  console.error('serialport is not installed — run `npm install` in weight-bridge/. ' + e.message);
  process.exit(1);
}

console.log(`sniffing ${config.serial.path} @ ${config.serial.baudRate} ${config.serial.dataBits}${config.serial.parity[0].toUpperCase()}${config.serial.stopBits}`);
console.log('press Ctrl+C to stop\n');

const sp = new SerialPort({
  path: config.serial.path,
  baudRate: config.serial.baudRate,
  dataBits: config.serial.dataBits,
  stopBits: config.serial.stopBits,
  parity: config.serial.parity,
});

let buf = Buffer.alloc(0);
sp.on('data', (chunk) => {
  buf = Buffer.concat([buf, chunk]);
  // flush on CR or LF boundaries; also flush oversized buffers
  let idx;
  while ((idx = buf.findIndex((b, i) => (b === 0x0d || b === 0x0a) && i > 0)) !== -1) {
    const line = buf.subarray(0, idx + 1);
    buf = buf.subarray(idx + 1);
    print(line);
  }
  if (buf.length > 120) {
    print(buf);
    buf = Buffer.alloc(0);
  }
});
sp.on('error', (e) => console.error('serial error:', e.message));

function print(b) {
  const text = b.toString('latin1').replace(/[\x00-\x1F\x7F]/g, (c) => '\\x' + c.charCodeAt(0).toString(16).padStart(2, '0'));
  console.log(`TEXT  "${text}"`);
  console.log(`HEX   ${b.toString('hex').match(/../g).join(' ')}`);
  console.log('');
}
