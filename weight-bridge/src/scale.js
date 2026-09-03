'use strict';
const { EventEmitter } = require('events');
const { makeParser, round3 } = require('./parser');

/**
 * Owns the serial connection (or a mock source), turns frames into a rolling
 * weight state and emits `frame` / `status` events. Never throws to the caller;
 * serial problems become status changes and auto-reconnects.
 */
class Scale extends EventEmitter {
  constructor(cfg, log) {
    super();
    this.cfg = cfg;
    this.log = log;
    this.parser = makeParser(cfg);

    this.state = {
      connecting: false,
      open: false,
      port: cfg.serial.path,
      baudRate: cfg.serial.baudRate,
      mock: cfg.mock,
      framesSeen: 0,
      parseFailures: 0,
      lastError: null,
      openedAt: null,
      tareSupported: !!cfg.tareCommand,
    };

    this.weight = { weightKg: null, unit: cfg.parse.defaultUnit, stable: false, raw: '', at: 0 };
    this._history = []; // {kg, t} for client-side stability
    this._sp = null;
    this._reconnectTimer = null;
    this._mockTimer = null;
    this._stopped = false;
  }

  start() {
    if (this.cfg.mock) return this._startMock();
    return this._openSerial();
  }

  stop() {
    this._stopped = true;
    clearTimeout(this._reconnectTimer);
    clearInterval(this._mockTimer);
    if (this._sp && this._sp.isOpen) this._sp.close(() => {});
  }

  status() {
    const fresh = this.weight.at > 0 && Date.now() - this.weight.at < this.cfg.frameStaleMs;
    return { ...this.state, connected: this.state.open && fresh, fresh };
  }

  currentWeight() {
    return { ...this.weight };
  }

  tare() {
    if (!this.cfg.tareCommand) {
      return { ok: false, reason: 'remote_tare_not_configured' };
    }
    if (this.cfg.mock) {
      this._mockTarget = 0;
      return { ok: true, sent: '(mock)' };
    }
    if (!this._sp || !this._sp.isOpen) {
      return { ok: false, reason: 'port_not_open' };
    }
    const buf = parseCommand(this.cfg.tareCommand);
    this._sp.write(buf, (err) => {
      if (err) this.log.warn('tare write failed: ' + err.message);
    });
    return { ok: true, sent: this.cfg.tareCommand };
  }

  // --------------------------------------------------------------- serial
  _openSerial() {
    if (this._stopped) return;
    if (!this.cfg.serial.path) {
      this._setStatus({ connecting: false, open: false, lastError: 'SCALE_PORT not configured' });
      return;
    }
    let SerialPort, ReadlineParser;
    try {
      ({ SerialPort } = require('serialport'));
      ({ ReadlineParser } = require('@serialport/parser-readline'));
    } catch (e) {
      this._setStatus({ open: false, connecting: false, lastError: 'serialport module not installed: ' + e.message });
      return;
    }

    this._setStatus({ connecting: true, open: false, lastError: null });
    this.log.info(`opening ${this.cfg.serial.path} @ ${this.cfg.serial.baudRate}`);

    const sp = new SerialPort({
      path: this.cfg.serial.path,
      baudRate: this.cfg.serial.baudRate,
      dataBits: this.cfg.serial.dataBits,
      stopBits: this.cfg.serial.stopBits,
      parity: this.cfg.serial.parity,
      autoOpen: true,
    });
    this._sp = sp;

    const reader = sp.pipe(new ReadlineParser({ delimiter: this.cfg.serial.delimiter }));
    reader.on('data', (line) => this._onLine(line));

    sp.on('open', () => {
      this._setStatus({ open: true, connecting: false, openedAt: Date.now(), lastError: null });
      this.log.info('serial open');
    });
    sp.on('error', (err) => {
      this._setStatus({ lastError: err.message });
      this.log.warn('serial error: ' + err.message);
    });
    sp.on('close', () => {
      this._setStatus({ open: false, connecting: false });
      this.log.warn('serial closed — reconnecting');
      this._scheduleReconnect();
    });
  }

  _scheduleReconnect() {
    if (this._stopped) return;
    clearTimeout(this._reconnectTimer);
    this._reconnectTimer = setTimeout(() => this._openSerial(), this.cfg.reconnectMs);
  }

  _onLine(line) {
    this.state.framesSeen += 1;
    const parsed = this.parser.parseLine(line);
    if (!parsed) {
      this.state.parseFailures += 1;
      if (this.state.parseFailures <= 5 || this.state.parseFailures % 50 === 0) {
        this.log.debug('unparsed frame: ' + JSON.stringify(String(line)));
      }
      return;
    }
    this._pushWeight(parsed);
  }

  _pushWeight(parsed) {
    const now = Date.now();
    let stable = parsed.stable;
    if (stable === null || stable === undefined) {
      stable = this._computeStability(parsed.weightKg, now);
    } else {
      this._history = []; // device is authoritative — drop the window
    }
    this.weight = { weightKg: parsed.weightKg, unit: parsed.unit, stable: !!stable, raw: parsed.raw, at: now };
    this.emit('frame', this.currentWeight());
  }

  _computeStability(kg, now) {
    this._history.push({ kg, t: now });
    const cutoff = now - Math.max(this.cfg.stability.durationMs, 300) - 200;
    this._history = this._history.filter((h) => h.t >= cutoff);
    const windowStart = now - this.cfg.stability.durationMs;
    const inWindow = this._history.filter((h) => h.t >= windowStart);
    if (inWindow.length < 2) return false;
    const values = inWindow.map((h) => h.kg);
    const spread = Math.max(...values) - Math.min(...values);
    return spread <= this.cfg.stability.thresholdKg && kg > 0.001;
  }

  _setStatus(patch) {
    Object.assign(this.state, patch);
    this.emit('status', this.status());
  }

  // --------------------------------------------------------------- mock
  _startMock() {
    this.log.info('MOCK mode — emitting a simulated weight curve, no serial port opened');
    this._setStatus({ open: true, connecting: false, openedAt: Date.now(), mock: true });
    let current = 0;
    this._mockTarget = 0;
    let holdUntil = 0;
    this._mockTimer = setInterval(() => {
      const now = Date.now();
      if (now > holdUntil && Math.abs(current - this._mockTarget) < 0.02) {
        // pick a new target: mostly a "fill", sometimes back to ~0
        this._mockTarget = Math.random() < 0.35 ? 0 : round3(3 + Math.random() * 15);
        holdUntil = now + 4000 + Math.random() * 4000;
      }
      const step = (this._mockTarget - current) * 0.18 + (Math.random() - 0.5) * 0.01;
      current = Math.max(0, round3(current + step));
      this._pushWeight({ weightKg: current, unit: 'kg', stable: null, raw: `MOCK ${current.toFixed(3)} kg` });
    }, 200);
  }
}

function parseCommand(spec) {
  const s = String(spec).trim();
  if (/^0x/i.test(s) || /^[0-9a-f\s]+$/i.test(s.replace(/0x/gi, ''))) {
    const bytes = s.replace(/0x/gi, '').trim().split(/\s+/).map((h) => parseInt(h, 16)).filter((n) => Number.isFinite(n));
    if (bytes.length) return Buffer.from(bytes);
  }
  return Buffer.from(s.replace(/\\r/g, '\r').replace(/\\n/g, '\n').replace(/\\t/g, '\t'), 'ascii');
}

module.exports = { Scale };
