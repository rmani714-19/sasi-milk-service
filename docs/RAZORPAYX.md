# RazorpayX Payouts — setup & operations

SASI MILK SERVICE disburses producer settlements and driver salaries through the
`PayoutProvider` abstraction (`backend/.../payout/provider`). Two implementations:

| Provider  | When active                                   | Behaviour |
|-----------|-----------------------------------------------|-----------|
| `MANUAL`  | default (RazorpayX disabled)                  | records the disbursement intent, leaves the payout `PROCESSING`; an operator finishes it with `mark-paid` / `mark-failed` |
| `RAZORPAYX` | `sasi.payout.razorpayx.enabled=true`        | real payouts via the RazorpayX Payouts API + webhooks |

Everything upstream of the provider — batch calculation, per-recipient
verification, bank-balance verification, pre-validation, owner approval — is
unchanged and provider-agnostic.

---

## 1. Configuration

All values come from the environment (or a secret manager). Nothing real is
committed. `application.yml` binds:

```yaml
sasi:
  payout:
    razorpayx:
      enabled:        ${RAZORPAYX_ENABLED:false}
      key-id:         ${RAZORPAYX_KEY_ID:}
      key-secret:     ${RAZORPAYX_KEY_SECRET:}      # never logged
      account-number: ${RAZORPAYX_ACCOUNT_NUMBER:}  # the RazorpayX owner account payouts draw from
      webhook-secret: ${RAZORPAYX_WEBHOOK_SECRET:}  # never logged
      base-url:       ${RAZORPAYX_BASE_URL:https://api.razorpay.com/v1}
```

For local dev copy the example and run with the `local` profile:

```bash
cp backend/src/main/resources/application-local.yml.example \
   backend/src/main/resources/application-local.yml
# fill in your rzp_test_... key / secret / account number / webhook secret
cd backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) \
  mvn spring-boot:run -Dspring-boot.run.profiles=local
```

`application-local.yml` is git-ignored.

---

## 2. RazorpayX TEST setup

1. RazorpayX dashboard → switch to **Test Mode**.
2. **Settings → API Keys** → generate a test key. Use the `rzp_test_...` key id +
   secret.
3. **Account Statement / Banking** → note the **RazorpayX account number**
   (`account-number` above) — payouts are drawn from it.
4. **Settings → Webhooks → Add New Webhook**
   - URL: `https://<your-host>/api/v1/payments/webhooks/razorpayx`
   - Secret: any strong string → set it as `RAZORPAYX_WEBHOOK_SECRET`
   - Events: `payout.processed`, `payout.failed`, `payout.rejected`,
     `payout.reversed`, `payout.updated`
5. Start the app with `RAZORPAYX_ENABLED=true` and the four secrets set.
6. Run a small test batch end to end (calculate → verify → bank balance →
   pre-validate → approve → **Initiate payouts**). In test mode RazorpayX
   auto-progresses payouts; the webhook drives the final status.

### How it behaves

- **Contacts / fund accounts** are created once per bank account and cached in
  `payment_provider_accounts` (keyed by a blind-index hash of the account number,
  never the number itself). Re-runs reuse them.
- **Idempotency:** payout creation sends `X-Payout-Idempotency: po-<settlementItemId>`
  (deterministic). A timeout or retry re-sends the **same** key, so RazorpayX
  returns the same payout — never a duplicate.
- **Never PAID on acceptance:** a `202/200` from `POST /payouts` only moves the
  payout to `INITIATED`/`PROCESSING`. `SUCCESS` (and the settlement line →
  `PAID`) happens **only** from a signature-verified `payout.processed` webhook
  (or an explicit manual `mark-paid`).
- **Errors:**
  - retryable (HTTP 429/5xx, `SERVER_ERROR`, `GATEWAY_ERROR`) → propagated;
    the deterministic key makes the automatic retry safe.
  - non-retryable (`INVALID_IFSC`, `INVALID_ACCOUNT_NUMBER`, `INVALID_BENEFICIARY`,
    `INSUFFICIENT_BALANCE`, …) → payout recorded `FAILED` with the mapped
    reason for operator attention (no pointless auto-retry against a bad account).
- **Webhooks:**
  - HMAC-SHA256 of the raw body vs `X-Razorpay-Signature` (constant-time). Bad
    signature → `422`, nothing persisted.
  - Deduplicated on `X-Razorpay-Event-Id` in `payment_webhook_events` **before**
    the payout is resolved, plus a second guard on `(payout, provider_ref)`.
    A redelivered event is a no-op — no duplicate status change, no duplicate
    notification.
  - `payout.reversed` → payout `REVERSED`, settlement line back to `CANCELLED`,
    `PAYMENT_REVERSED` notification.

### Balance

RazorpayX has no stable public "available balance" endpoint on the Payouts API,
so `getBalance()` returns empty and the owner **bank-balance verification stays
manual** (`POST /settlements/{id}/bank-balance`). Balance is never estimated.

---

## 3. Production checklist

- [ ] UAT completed in Test Mode: success, failed, reversed, duplicate webhook,
      timeout-after-create, insufficient balance, missing bank details.
- [ ] Production `rzp_live_...` key + secret injected from the secret manager
      (not a file, not source control).
- [ ] Production RazorpayX account number set.
- [ ] Production webhook registered at the public HTTPS URL with a fresh secret;
      `RAZORPAYX_WEBHOOK_SECRET` matches.
- [ ] `/api/v1/payments/webhooks/razorpayx` reachable from RazorpayX and
      **not** behind auth (it authenticates by signature).
- [ ] Outbound egress to `api.razorpay.com:443` allowed from the app.
- [ ] Logging: confirm no full account numbers / secrets in logs (only last-4,
      masked, hashes).
- [ ] Owner approval required before `Initiate payouts` (already enforced).
- [ ] Alerting on `payment_webhook_events.processing_status = 'FAILED'` and on
      payouts stuck in `PROCESSING` beyond SLA (reconcile via `fetchStatus`).
- [ ] `RAZORPAYX_ENABLED=true` only after all of the above.
