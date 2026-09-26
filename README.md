# Nukkad — M2 build (0.3)

Native Android, Kotlin, Compose, one app module. The two-phone request/quote path is reported working by the user. This update adds the next checkpoint: **two merchant quotes → customer selection → one accepted unpaid reservation → other offer closed**.

See HACKATHON-PLAN.md for the revised roadmap and final product flow. Automatic category discovery without a customer-entered code and catalogue aliases are the next checkpoint, M3. The current UI is still a developer surface.

## Install and run on your three phones

Update ALL THREE phones to the M2 APK; older builds do not understand selection messages.

1. Phone A: customer / MQTT.
2. Phone B: seller / MQTT, Sweet Crumbs.
3. Phone C: seller / MQTT, HomeBake.
4. Use the same demo code, broker/port and TLS setting on all three. Connect B and C first, then A. Keep all apps open.
5. On A, check seller connectivity. Both merchant names should appear after replying.
6. Send 1 kg chocolate cake, eggless, budget 800, deadline 24 hours ahead.
7. A should receive ₹750 and ₹780. Select ₹750.
8. B rechecks policy and reserves capacity. A shows SELLER ACCEPTED. C shows CLOSED; A waits for its closure acknowledgement.
9. Retry/refresh on A. The same order ID must still have just one reservation.
10. Reconnect in the same session and retry/refresh to verify persisted recovery.
11. Cancel the order on A. B releases capacity and acknowledges cancellation.

The Merchant capacity field supports 1–20 orders/day. Set it BEFORE requesting quotes. A policy change after quoting causes selection rejection and requires a fresh request.

Exact catalogue matching remains for this debug build: use `chocolate cake`. Natural-language/alias matching is M3/M5 work, not something already present here.

## Transaction semantics

- A quote does not reserve capacity.
- Quotes expire after five minutes. Advertised readiness includes that five-minute selection window so the seller can honor it even if the customer selects near expiry.
- SELECT identifies an immutable quote and a new order ID.
- Seller validates customer/request/offer identity, expiry, policy version, price, capacity and readiness.
- It atomically persists capacity before replying with ORDER_STATUS = ACCEPTED.
- Only then does the customer send CLOSE to the losing sellers; they reply CLOSED. Late offers are closed too.
- An unpaid reservation expires after up to ten minutes, or earlier cancellation. Payment is not implemented in M2.
- Retrying SELECT uses the same order ID and returns its existing outcome.
- CANCEL arriving before SELECT creates a cancellation tombstone, preventing delayed creation of that order.
- Unknown selection outcomes stay pending; the customer cannot switch sellers or start a new request until the outcome is resolved/cancelled.
- Expired/rejected quotes require a new request or another still-valid offer. No price is silently changed.

## Persistence and reset

AtomicFile JSON stores customer request/offers/selection and seller quotes/orders/closed requests. A persistence failure must not result in an acceptance. Corrupt state fails visibly rather than resetting capacity silently.

Ledgers are scoped to the demo session, with separate fake/MQTT storage. Reconnect to the SAME session to recover an order. A new session starts a fresh isolated demo ledger; it is not a real-order cancellation operation. Cancel outstanding demo reservations before resetting. All three phones must join the new session after reset.

## Transport

HiveMQ MQTT 3.1.1, QoS 1, clean sessions, no retained transactional messages.

- Requests: `nukkad/<session>/<area>/<category>/requests`
- Customer responses: `nukkad/<session>/customers/<customerId>/offers`
- Seller order control: `nukkad/<session>/sellers/<sellerId>/orders`

Events: REQUEST, OFFER, PROBE, RECEIPT, SELECT, ORDER_STATUS, CANCEL, CLOSE, CLOSED. ORDER_STATUS carries ACCEPTED, REJECTED, CANCELLED or EXPIRED. Closure is acknowledged, not inferred from successful publication.

The optional PROBE/RECEIPT diagnostics remain in this build. See CONNECTION-TROUBLESHOOTING.md if phones connect but messages do not arrive. Match the CONNECTED CODE, not just an edited field. Timestamp filtering requires reasonably synchronized phone clocks.

Public-broker demo traffic is not authenticated. Session codes are isolation/convenience, not authorization. Use synthetic data. Keep the foreground demo baseline; background delivery is not guaranteed. Production needs authenticated identities and topic authorization.

## Build

JDK 17, Gradle wrapper 8.11.1, AGP 8.9.2, Kotlin/Compose compiler 2.1.20, compile/target SDK 35, minSdk 26.

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

Opt in to real-broker tests (two-client cycles and a three-client selection/close/cancel transaction):

```powershell
.\gradlew.bat :app:testDebugUnitTest -PmqttSmoke=true
```

Without the flag, network tests are skipped. Fake-broker and pure commerce tests run offline. Network tests depend on the public broker's availability.

APK build output: `app/build/outputs/apk/debug/app-debug.apk`. For Linux/macOS, `chmod +x gradlew` then use `./gradlew`.

## Changes to build configuration and permissions

VersionCode is 3; versionName is 0.3-m2. No new dependencies or Android permissions were added. INTERNET remains the only permission. AtomicFile is provided by Android. No Hilt, Room, AI runtime or payment library is introduced here.

See VALIDATION.md for actual results and remaining physical-device checks. Code on the Go compatibility still needs its explicit device build experiment.
