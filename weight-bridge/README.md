# SASI Weight Bridge

A tiny local service that connects the **iScale i-05-RS232** weighing machine to
the SASI MILK SERVICE PWA. The browser cannot open a serial port directly, so the
bridge runs on the collection PC, reads the scale over RS-232, and exposes the
current weight over `http://localhost:8787` (HTTP + WebSocket).

```
 iScale i-05-RS232 ──RS-232──► USB-serial adapter ──► [ weight-bridge (Node) ]
                                                              │  http + ws :8787
                                                              ▼
                                                      SASI MILK SERVICE PWA
                                                      (Milk Collection · Automatic Scale)
```

Manual Entry mode in the PWA does **not** use the bridge and keeps working
offline exactly as before.

---

## 1. Install

Node 18+ on the collection PC.

```bash
cd weight-bridge
npm install
cp .env.example .env
```

`npm install` builds the `serialport` native module (prebuilt binaries exist for
Windows/macOS/Linux x64/arm64).

## 2. Find the serial port

```bash
npm run ports
```

Windows shows `COM3`, `COM4`, …; Linux `/dev/ttyUSB0`; macOS `/dev/tty.usbserial-XXXX`.
Put it in `.env` as `SCALE_PORT`, and set `BAUD_RATE`, `DATA_BITS`, `STOP_BITS`,
`PARITY` to whatever the scale's manual / DIP switches say (a very common default
is `9600 8N1`).

## 3. Confirm the frame format  ⚠️ required

**The iScale i-05-RS232 wire format is not assumed by this bridge.** Before it can
report a weight you must tell it how to read the frames:

```bash
npm run sniff
```

Put some weight on the scale and watch the output, e.g.

```
TEXT  "ST,GS,+  12.450 kg\x0d\x0a"
HEX   53 54 2c 47 53 2c 2b 20 20 31 32 2e 34 35 30 20 6b 67 0d 0a
```

Now set `WEIGHT_REGEX` in `.env` so it captures a named group `weight`
(and `unit` / `sign` / `stable` if the frame has them). For the example above:

```
WEIGHT_REGEX=^(?<stable>ST|US),\w{2},(?<sign>[+-])\s*(?<weight>\d+(?:\.\d+)?)\s*(?<unit>kg|g)
LINE_DELIMITER=\r\n
```

If the scale does not send a stability flag, leave `stable` out — the bridge
derives it from `STABLE_THRESHOLD_KG` / `STABLE_DURATION_MS` (the reading is
"stable" once it stops moving for that long).

## 4. Run

```bash
npm start
```

Health check: <http://localhost:8787/health>

### Develop / demo without hardware

```bash
npm run mock
```

Emits a simulated fill-and-settle curve so the PWA can be built and tested with
no scale attached.

---

## HTTP / WebSocket API

| Method | Path       | Purpose |
|--------|------------|---------|
| GET    | `/health`  | `{ ok, mock, open, connected, lastFrameAgeMs }` |
| GET    | `/weight`  | `{ weightKg, unit, stable, raw, at }` — current reading |
| GET    | `/status`  | full connection + serial + counters snapshot |
| POST   | `/tare`    | sends `TARE_COMMAND` if configured; otherwise `501` with an instruction to use the physical ZERO/TARE button |
| WS     | `/` (any)  | pushes `{type:'weight',…}` per frame and `{type:'status',…}` on connection changes |

CORS is open (`*`) — the bridge only ever listens on `localhost`.

## Tare / Zero

Remote tare is **only** used if `TARE_COMMAND` is set in `.env` to a value taken
from the device manual (ASCII like `T\r`, or hex like `0x54 0x0D`). Otherwise the
bridge returns `501` and the PWA asks the operator to press the scale's own
ZERO/TARE button. The bridge never fakes a zero.

## Run as a service

- **Windows**: [`nssm`](https://nssm.cc/) — `nssm install SasiWeightBridge "C:\Program Files\nodejs\node.exe" "C:\sasi\weight-bridge\src\index.js"`
- **Linux**: a `systemd` unit running `node /opt/sasi/weight-bridge/src/index.js`
- **Anywhere**: `pm2 start src/index.js --name sasi-weight-bridge`

## Configuration reference

Everything is in `.env` (see `.env.example` for the annotated list): HTTP port,
serial port + line settings, `WEIGHT_REGEX` / `STABLE_REGEX` / `DEFAULT_UNIT`,
stability window, `TARE_COMMAND`, `FRAME_STALE_MS`, `RECONNECT_MS`, `LOG_LEVEL`.

## Safety

- Never reports "connected" unless a real, recent frame has been parsed.
- Ignores unparseable frames and keeps listening (logged, counted in `/status`).
- Auto-reconnects after a serial disconnect.
- Sends nothing to the scale except an explicitly configured `TARE_COMMAND`.
