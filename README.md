# Nukkad AI

**Nukkad AI is a phone-first local commerce system that connects customer demand with nearby small merchants using on-device AI, deterministic merchant policies, and MQTT-based request routing.**

Small businesses often have the right products or services but limited digital visibility. Customers, meanwhile, struggle to discover nearby merchants who can satisfy a specific request, especially when the merchant has no website, catalogue API, or online storefront.

Nukkad reverses this model:

**The customer describes a requirement → Nukkad routes it to relevant merchants → merchants respond with offers → the customer selects one.**

The system is designed around **merchant-owned phones, on-device inference, deterministic pricing, and lightweight device-to-device communication infrastructure.**

---

## Core Idea

A customer can make a natural-language request such as:

```text
1 kg eggless chocolate cake
Budget: ₹800
Required within: 24 hours
```

Nukkad converts the request into structured intent and routes it to relevant merchants.

Each merchant phone independently evaluates:

- Catalogue availability
- Price
- Minimum price
- Capacity
- Operating hours
- Lead time
- Merchant policy version

The merchant then returns an offer.

The customer can compare multiple offers and select one.

---

## Key Feature: Demand Signal

Nukkad also turns customer requests into a simple demand signal for merchants.

### 🟥 RED

**Demand exists around the merchant, but the merchant does not stock the requested item.**

> Import it?

### 🟩 GREEN

**Demand exists and the merchant already stocks the item.**

> Sell it.

This gives merchants a direct view of **what customers around them are requesting**, allowing them to identify potential inventory opportunities instead of relying entirely on guesswork.

---

# System Architecture

```text
                         CUSTOMER PHONE
                              │
                    Android SpeechRecognizer
                              │
                              ▼
                     Gemma 3 1B / LiteRT-LM
                              │
                              ▼
                       Intent Extraction
                              │
                              ▼
                       Request Review UI
                              │
                              ▼
                    MQTT Request Routing
                              │
             ┌────────────────┴────────────────┐
             ▼                                 ▼
      MERCHANT PHONE A                  MERCHANT PHONE B
             │                                 │
       Merchant Agent                    Merchant Agent
             │                                 │
       Catalogue Check                   Catalogue Check
       Policy Validation                 Policy Validation
       Capacity Check                    Capacity Check
       Deterministic Price               Deterministic Price
             │                                 │
             └──────────────┬──────────────────┘
                            ▼
                     MQTT Offer Channel
                            │
                            ▼
                     CUSTOMER PHONE
                            │
                    Offer Comparison
                            │
                            ▼
                       SELECT OFFER
                            │
                            ▼
                  Merchant Validation
                            │
                            ▼
                  Capacity Reservation
                            │
                            ▼
                  ACCEPTED / REJECTED
                            │
                            ▼
                  Losing Offers CLOSED
```

---

# Technology Stack

| Layer | Technology |
|---|---|
| Platform | Native Android |
| Language | Kotlin |
| UI | Jetpack Compose |
| AI | Gemma 3 1B |
| AI Runtime | LiteRT-LM |
| Speech | Android SpeechRecognizer |
| OCR | CameraX + ML Kit Text Recognition |
| Messaging | MQTT 3.1.1 |
| MQTT Broker | HiveMQ |
| Location / Routing | H3 |
| Persistence | Android AtomicFile + JSON |
| Build | Gradle 8.11.1 |
| Android Gradle Plugin | 8.9.2 |
| Kotlin / Compose Compiler | 2.1.20 |
| Compile / Target SDK | 35 |
| Minimum SDK | 26 |

The current implementation uses a **single native Android project with one app module** and separate customer/merchant application flows.

---

# Request Pipeline

### 1. Voice Input

The customer speaks a request using Android SpeechRecognizer.

Example:

```text
"1 kg eggless chocolate cake under 800 by tomorrow"
```

### 2. On-device Intent Parsing

Gemma 3 1B running through LiteRT-LM extracts structured information:

```json
{
  "item": "chocolate cake",
  "quantity": "1 kg",
  "constraints": {
    "eggless": true
  },
  "budget": 800,
  "deadline": "24h"
}
```

The customer receives an editable representation before the request is transmitted.

A rule-based fallback parser is also available when the model is unavailable.

---

# Merchant Processing

Each merchant phone maintains its own catalogue and business rules.

A request is evaluated locally against:

```text
Catalogue
    ↓
Availability
    ↓
Price / Minimum Price
    ↓
Capacity
    ↓
Operating Hours
    ↓
Lead Time
    ↓
Merchant Policy Version
```

The LLM is **not responsible for pricing**.

Pricing and acceptance decisions are deterministic so that a model cannot invent a price or override merchant-defined constraints.

---

# Catalogue Onboarding

Merchant onboarding is designed to avoid manual catalogue entry.

Two supported inputs are:

### Voice

The merchant describes available products verbally.

### Camera + OCR

The merchant points the camera at an existing handwritten or printed price card.

```text
CameraX
   ↓
ML Kit Text Recognition
   ↓
Structured Catalogue Draft
   ↓
Merchant Review
   ↓
Persisted Catalogue
```

Exact catalogue matching is currently used in the M2 debug build.

Natural-language aliases and automatic category discovery are planned for M3.

---

# MQTT Communication

Nukkad uses **MQTT 3.1.1 with QoS 1** for request and transaction messaging.

### Request Topic

```text
nukkad/<session>/<area>/<category>/requests
```

### Customer Offer Topic

```text
nukkad/<session>/customers/<customerId>/offers
```

### Merchant Order Control

```text
nukkad/<session>/sellers/<sellerId>/orders
```

### Supported Events

```text
REQUEST
OFFER
PROBE
RECEIPT
SELECT
ORDER_STATUS
CANCEL
CLOSE
CLOSED
```

`ORDER_STATUS` supports:

```text
ACCEPTED
REJECTED
CANCELLED
EXPIRED
```

Closure is explicitly acknowledged by the merchant rather than inferred from successful MQTT publication.

---

# Multi-Merchant Transaction Flow

The current M2 build supports:

```text
Customer Request
      ↓
Merchant A → Quote
Merchant B → Quote
      ↓
Customer selects Quote A
      ↓
Merchant A validates:
    - Request identity
    - Offer identity
    - Quote expiry
    - Policy version
    - Price
    - Capacity
    - Readiness
      ↓
Capacity atomically reserved
      ↓
ORDER_STATUS = ACCEPTED
      ↓
Customer closes losing offers
      ↓
Merchant B → CLOSED
```

Important transaction properties:

- Quotes do not reserve capacity.
- Quotes expire after five minutes.
- Selecting a quote creates an immutable order ID.
- Capacity is persisted before `ACCEPTED` is returned.
- Repeated `SELECT` operations reuse the same order ID and return the existing outcome.
- An unpaid reservation expires after up to ten minutes.
- Cancellation releases merchant capacity.
- Late offers are closed.
- Expired or rejected offers require a new request.
- Prices are never silently modified.

---

# Persistence

Nukkad uses **AtomicFile-backed JSON persistence** for the current prototype.

Customer-side state includes:

```text
Requests
Offers
Selections
```

Merchant-side state includes:

```text
Quotes
Orders
Closed Requests
```

Persistence failures do not result in an acceptance.

Corrupt state is surfaced explicitly instead of silently resetting merchant capacity.

Ledgers are scoped to a demo session, allowing the same session to recover state after reconnecting.

---

# Merchant Capacity

Each merchant maintains a configurable daily capacity:

```text
1–20 orders/day
```

Capacity is checked during selection and reserved atomically when an order is accepted.

A policy change after quoting invalidates the previous quote and requires a fresh request.

---

# Current Build

### M2 — `0.3`

Implemented:

- Native Android application
- Kotlin + Jetpack Compose
- Customer request flow
- Merchant request flow
- On-device Gemma 3 1B intent extraction
- LiteRT-LM integration
- Android SpeechRecognizer
- CameraX catalogue capture
- ML Kit OCR
- MQTT request routing
- Multiple merchant quotes
- Customer offer selection
- Merchant policy validation
- Capacity reservation
- Offer closure
- Cancellation
- Persistent transaction state
- Reconnect recovery
- Demand / inventory signal

### Next

**M3**

- Automatic category discovery
- Catalogue aliases
- More natural-language catalogue matching

Planned future work includes:

- Phone-to-phone offline mesh
- NPU-optimized on-device inference
- UPI payment integration
- Automatic payment detection
- Signed transaction receipts

---

# Build Requirements

```text
JDK 17
Gradle 8.11.1
Android Gradle Plugin 8.9.2
compileSdk 35
targetSdk 35
minSdk 26
```

Build and test:

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

Optional MQTT integration tests:

```powershell
.\gradlew.bat :app:testDebugUnitTest -PmqttSmoke=true
```

Without `mqttSmoke=true`, network tests are skipped and offline tests run normally.

APK:

```text
app/build/outputs/apk/debug/app-debug.apk
```

---

# Three-Device Demo

The current transaction flow can be demonstrated using three Android devices:

```text
Phone A
Customer

Phone B
Merchant: Sweet Crumbs

Phone C
Merchant: HomeBake
```

All devices must run the same M2 APK and connect to the same:

```text
Demo Session
MQTT Broker
Port
TLS Configuration
```

Expected flow:

```text
Customer
   ↓
"1 kg chocolate cake, eggless, ₹800"
   ↓
Sweet Crumbs → ₹750
HomeBake     → ₹780
   ↓
Customer selects ₹750
   ↓
Sweet Crumbs → ACCEPTED
HomeBake     → CLOSED
```

The same order ID can then be used to verify transaction recovery after reconnecting.

---

# Security & Production Considerations

The current hackathon build uses a public MQTT broker for demonstration.

Therefore:

- MQTT traffic is not authenticated.
- Session codes provide isolation, not authorization.
- Synthetic data should be used.
- Clean MQTT sessions are used.
- Background delivery is not guaranteed.

A production deployment would require:

- Authenticated merchant/customer identities
- Topic-level authorization
- Secure broker infrastructure
- Stronger transaction authentication
- Protected payment flows
- Production-grade key management
- Reliable background message delivery

---

# Project Status

**Nukkad AI is currently a working native Android prototype demonstrating phone-first local commerce, on-device intent extraction, merchant-side deterministic decision making, MQTT-based discovery, multi-merchant quoting, and transactional offer selection.**
