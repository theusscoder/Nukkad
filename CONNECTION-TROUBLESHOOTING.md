# Nukkad — connection and delivery troubleshooting

Install the latest M2 APK on **all participating phones** as an update. The diagnostics introduced in 0.2 remain available. Existing identities and settings are preserved. The user subsequently reported that the 0.2 phone loop worked. Retain this checklist for connection issues in later builds.

## What changed

- New demo sessions use an 8-digit code instead of a UUID. Customer, seller, request and message IDs remain UUIDs.
- Existing saved session IDs remain valid. Tap **New 8-digit code** on ONE phone and copy that code to the other if you want to switch.
- Codes are trimmed and case-normalized on connect.
- The active code is shown separately as **CONNECTED CODE**.
- Editing setup disables sending until **Connect / reconnect** applies the changes.
- **BROKER READY** now explicitly means broker/subscriptions are ready, not that the other phone is reachable.
- **Check seller connection** sends a probe over the actual request topic and waits for a correlated seller reply on the customer's response topic.
- The customer distinguishes broker publication from an actual **Request received by: Sweet Crumbs** acknowledgement.
- Delivery diagnostics show incoming requests, replies and rejected messages, including device-clock mismatch.

## Retry on two phones

1. Update all phones to the same latest version. Keep the apps open on screen.
2. Customer: select **customer / MQTT**. Seller: select **seller / MQTT**, Sweet Crumbs.
3. On ONE phone tap **New 8-digit code**. Enter that exact code on the other phone. Do not independently generate two codes.
4. Use the same broker hostname, port and TLS setting on both phones.
5. Connect seller first, then customer. Compare **CONNECTED CODE**, not just the editable field. Request topics should match exactly.
6. On the customer tap **Check seller connection**. It should say **Seller replied: Sweet Crumbs**.
7. Send the default request. Look for **Request received by: Sweet Crumbs**, then the ₹750 offer. The seller displays actual checks.
8. If the seller connected after the request was sent, tap **Retry same request**. Transactional requests are deliberately not retained or queued for an offline seller.

## Interpret what you see

| Observation | Meaning / next check |
|---|---|
| Broker ready, but no seller reply | Broker connection alone succeeded. Compare active codes, roles, request topics and broker settings; keep seller foreground and check its diagnostics. |
| Seller sees connection check, but customer gets no reply | The request direction works. Inspect seller's publish error and customer's response diagnostics. |
| Seller reports clock ahead / message too old | Enable automatic date/time on both phones and retry. The app rejects messages more than 1 minute in the future or 10 minutes old. |
| Customer sees request acknowledged, but no offer | Request delivery worked. Inspect seller's actual policy decision: NoMatch/NeedsOwner produces no offer. |
| Customer still says waiting for acknowledgement | The broker accepted publication, but no updated seller has acknowledged it. Retry after the seller is connected and verify all apps are on the same latest version. |
| Seller gets request and quote-send error | Inspect network connection; reconnect and retry. |
| Setup changed banner | The edited settings are not active until reconnect. |

The connection check is a point-in-time diagnostic, not continuous seller presence. Short codes are for a synthetic-data demo and are not authentication credentials.

## What remains unverified

The user reported that both original apps connected using the same settings but the request did not arrive. No device logs have established the root cause. After this update, the two delivery panels should distinguish setup/routing, clock rejection, seller receipt and quote failure. The user has now reported physical two-phone success; the new M2 selection flow needs its own three-phone acceptance run.

