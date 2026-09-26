# Nukkad — Hackathon Build Plan

## Current position

The user has confirmed that the updated two-phone request/quote loop works. That proves the basic M1 path on the available phones. Five repeated device cycles and Code on the Go compatibility remain separate checks unless explicitly recorded.

The hackathon has now started, so challenge-specific development is authorized. The current build moves into **M2: selection, acceptance and local capacity reservations**, using three phones: one customer and two merchants.

## Answers to the product questions

### Will customers need the exact catalogue name?

No. Exact text matching is a temporary debug-harness limitation.

The final matching path will be:

1. Understand the requested category and item.
2. Normalize known aliases to a canonical item/type.
3. Extract quantity, units and constraints separately.
4. Match against each seller's verified catalogue capabilities.
5. Ask for clarification or owner review when the item is ambiguous.
6. Run deterministic merchant rules to calculate and authorize the quote.

For example, “1 kg eggless chocolate birthday cake” should resolve to chocolate cake + 1 kg + eggless, rather than require the customer to know the seller's display name. A seller must actually support those constraints. We will not use unrestricted fuzzy matching to silently substitute a different product.

Aliases and an editable structured review arrive before the full speech/Gemma flow. Bakery is the first working category. Carpentry and pharmacy can use the same routing structure but need their own item, unit and merchant rules. Pharmacy matching must preserve exact product/form/strength requirements and must not guess substitutions.

### Will both phones need a connection ID?

Not in the final customer experience. The current code is a developer/demo isolation tool.

Sellers will set their area, categories and catalogue once, then go live. Customers choose an area and category or describe a need that suggests a category. The coordination layer routes requests to matching registered sellers automatically. Internal IDs still exist, but customers do not type or pair them.

We will use explicit area selection first. “Nearby” will mean the chosen service area until a distance/location feature has actually been implemented.

### Is it like SHAREit?

It should have similarly easy discovery: open the app and find relevant available participants. Our planned transport remains MQTT over an internet connection. Offline Wi-Fi Direct/Bluetooth discovery is a separate project and is not a dependency of this hackathon demo.

### What about notifications?

We will first make arrival unmistakable while merchant phones are live: visible incoming request, sound/haptic cue and quote announcement. We will then verify background notification delivery on the actual target devices before claiming that a closed/background app can always receive and auto-quote. A notification alone does not prove that background commercial evaluation ran.

## Checkpoints and what you can do after each one

| Checkpoint | What we build | What you can demonstrate afterwards |
|---|---|---|
| **M1 — Device loop** | Customer request reaches a seller; seller returns a deterministic quote | Already reported working on your phones |
| **M2 — Safe selection** | Two merchants quote; select one; seller revalidates/reserves; other seller closes; cancellation and expiry | A complete three-phone request → two quotes → one accepted unpaid order |
| **M3 — Automatic discovery and usable matching** | Area/category onboarding, live seller discovery, routing without a customer-entered code, verified aliases and decline/outcome reporting | Open customer app, choose Bakery in the area, and find the two matching merchants automatically |
| **M4 — Round 1 presentation** | Final customer/seller screens, real animated checks, arrival cues, TTS, session/demo controls | “Don't watch my phone. Watch the baker's.” An untouched merchant phone evaluates, quotes and announces |
| **M5 — Language input** | Speech/Gemma candidate extraction, deterministic critical-field validation and editable review | Say a natural request and send a trustworthy structured request |
| **M6 — Payment** | Accepted order → UPI QR/handoff → explicit payment status and tested confirmation fallback | A complete transaction demo with the payment evidence stated accurately |
| **M7 — Stretch** | Deterministic counteroffer requiring customer approval | “I can do ₹720, which is ₹20 over your budget—accept the revised budget?” |

M3 and M4 together complete the intended Round 1 experience. M5 and M6 complete Round 2. Do not start a second stretch until these are stable.

## M2 — what the current update does

- Customer can select an offer; this sends SELECT to that seller.
- Seller loads the original quote, checks its expiry and rule version, and revalidates merchant policy/capacity.
- The seller persists the reservation before acknowledging ACCEPTED.
- Only after acceptance does the customer close the other sellers' offers.
- Losing sellers acknowledge CLOSED; pending closure acknowledgements remain visible and retryable.
- Five-minute quote expiry prevents selecting indefinitely stale quotes.
- Unpaid reservations expire after up to ten minutes; cancellation releases the slot earlier.
- Customer selection intent and seller ledgers survive reconnect/restart in the same demo session.
- Retrying uses the same order ID, so a lost acknowledgement does not consume another slot.
- A cancellation received before its selection creates a tombstone: a delayed SELECT cannot recreate the cancelled order.
- A debug capacity setting enables testing the last available slot.

This is **unpaid order acceptance**, not payment confirmation. Session codes are still visible in M2; removal from the normal user flow is M3. Exact catalogue matching remains until the matching checkpoint.

## Three-phone M2 test

Use the new M2 APK on all three phones.

- **Phone A:** customer / MQTT.
- **Phone B:** seller / MQTT → Sweet Crumbs.
- **Phone C:** seller / MQTT → HomeBake.
- For this checkpoint only, use the same demo code and broker settings on all three.

Then:

1. Connect B and C, then A. Keep all apps open.
2. Check seller connectivity; A should receive replies from both merchants.
3. Send the default 1 kg eggless chocolate cake request under ₹800.
4. A receives ₹750 and ₹780 offers. Neither quote itself consumes a capacity slot.
5. Select Sweet Crumbs. A waits for its acceptance; B records an accepted unpaid reservation.
6. C shows CLOSED. A shows that the other offer has closed.
7. Retry/refresh status. B should still have one reservation, not two.
8. Reconnect A and B in the same session and retry/refresh. The order should be recovered.
9. Cancel on A. B confirms cancellation and releases capacity.
10. Send a fresh request to start another transaction.

Do not reset midway through this test. A new demo session is an isolated ledger and is intentionally fresh. It is not a production cancellation mechanism.

A simultaneous last-slot race needs two independent customers; automated tests cover this even though the current physical demo has one customer. The three-phone setup can directly show selection, losing-offer closure, persistence and cancellation.

## M3 — discovery contract

Seller onboarding records seller identity, shop name, service area, supported categories, catalogue and merchant rules. Going live registers availability with a bounded freshness period.

Customer routing uses area + category, not a manually shared room code. The app will distinguish an available seller from one that actually acknowledged a particular request. Stale/offline sellers will not remain labelled live indefinitely.

The hackathon market/environment identifier can remain an internal debug setting for isolation. It must not appear as a pairing task in the normal customer flow.

Start with Bakery and two sellers. Add a second category only after proving that a Bakery request does not reach an unrelated category and that each category has suitable commerce rules.

## How the final experience works

1. The merchant sets up the shop once and goes live.
2. The customer types or speaks what they need and chooses/confirms their area.
3. Nukkad extracts category, item, quantity, budget, deadline and constraints.
4. The customer reviews critical fields and sends the request.
5. Coordination routes it to available matching sellers automatically.
6. Each seller's phone independently checks its own catalogue, prices, capacity, lead time and constraints.
7. It returns a valid quote, an owner-review outcome or a decline reason.
8. The customer sees valid offers and selects one.
9. The selected merchant reserves capacity and acknowledges acceptance; other offers close.
10. The customer pays through UPI; Nukkad shows the actual payment status supported by the chosen confirmation method.
11. The merchant fulfils the order.

The seller phone remains the commercial decision-maker. Discovery and message routing do not move pricing or acceptance into a central AI service.

## Wow features, in order

1. Untouched seller phone checks real rules and speaks its quote.
2. Two sellers respond differently to the same request because their policies differ.
3. A full merchant declines safely; a cancellation releases the slot.
4. A compact explanation shows why the quote is valid and how its price was calculated.
5. One spoken sentence becomes a reviewed request and an accepted order.
6. A deterministic counteroffer, if time remains.

The current public-broker build is still a synthetic-data hackathon demo. Authenticated identity/topic authorization and stronger durable coordination are production work, not something an eight-digit code provides.
