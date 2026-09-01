# SASI MILK SERVICE

Milk collection, producer settlement and payout platform. Modular-monolith
backend (Java 21 / Spring Boot) + PostgreSQL; React PWA frontend to follow.

See [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md),
[`docs/DATA_MODEL.md`](docs/DATA_MODEL.md) and
[`docs/OPEN_QUESTIONS.md`](docs/OPEN_QUESTIONS.md).

## Status

| Area | State |
|---|---|
| DB schema (Flyway `V1`–`V12`) | done — 33 tables, validated on PostgreSQL 17 |
| Calculation contracts + `RateFormula` / rounding | done + unit-tested |
| Auth (JWT + refresh rotation), RBAC | scaffolded |
| Modules: center, region, producer, rate, incentive, collection | scaffolded (entity→repo→service→controller) |
| Modules: deduction, advance, settlement | implemented — weekly settlement lifecycle (DRAFT→CALCULATED→REVIEWED→APPROVED), advance ledger, cancel/unwind |
| Modules: payout, notification, report, vehicle | SPI only — not yet implemented |
| Frontend, Docker, CI, AWS | not started |

## Run locally

Requires JDK 21 and a PostgreSQL database.

```bash
createdb sasi_milk_service
cd backend
DB_URL=jdbc:postgresql://localhost:5433/sasi_milk_service \
DB_USERNAME=youruser DB_PASSWORD=yourpass \
./mvnw spring-boot:run          # or: mvn spring-boot:run
```

- API base: `http://localhost:8080/api/v1`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- Health: `http://localhost:8080/actuator/health`

Bootstrap admin (seeded by `V11`, **change immediately**): `admin` / `admin123`.

```bash
curl -s localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}'
```

## Test

```bash
cd backend
mvn test
```

Pure calculation unit tests run everywhere. The Testcontainers integration tests
(`*IntegrationTest`, `SasiMilkServiceApplicationTests`) need a Docker daemon and
are skipped automatically when one is not available.

## Milk rate (client-confirmed)

```
rate_per_litre = (FAT% + SNF%) * ts_value / 100
```

`ts_value` is an effective-dated coefficient on `rate_charts` (one `COMMON`, with
optional per-`REGION` overrides). Incentive is a flat ₹/L, common + individual,
capped at `center.incentive_max_per_litre` (₹2). Deductions are fixed amounts.
Settlement rounding is banker's rounding to the whole rupee.
