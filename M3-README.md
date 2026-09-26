# Nukkad M3 — domain routing foundation

M2 was accepted on the three-phone setup. M3 adds the architecture needed to remove exact catalogue wording and customer-entered connection pairing from the normal product flow.

## What changed in this source snapshot

- Added a closed `CommerceDomain` enum: Bakery, Food, Pharmacy, Grocery, Tailoring, Carpentry, Plumbing, Electrical, Home Service and Other.
- `Request` now carries a validated domain plus optional original transcript and normalized text fields. The old category string remains for wire/source compatibility during migration.
- `SellerProfile` now carries a set of domains. Sellers can subscribe to more than one domain topic; the existing seeded merchants subscribe to Bakery.
- ShopAgent routing checks area + seller domain membership before touching catalogue or commercial rules.
- Customer publishing uses the request domain to build the MQTT topic.
- Existing M2 selection, reservation, close, cancellation and persistence code remains intact.

## The intended final flow

A seller registers `Kondapur + Bakery`; a pharmacy registers `Kondapur + Pharmacy`. The customer describes a need, the intent layer returns one member of the fixed domain enum, and deterministic routing publishes to only that domain topic:

```text
nukkad/<area>/<domain>/requests
```

The pharmacy does not receive bakery requests because it is not subscribed to the bakery topic. The seller's phone still performs the catalogue, price, capacity, deadline and constraint checks locally.

The current M3 build still uses the M2 demo session code so the multi-phone hackathon environment stays isolated. Removing code entry from the normal customer flow requires the next part of M3: a seller presence/directory handshake. That will advertise seller area/domain availability without moving commercial decisions to a central service.

## Catalogue matching boundary

The debug request editor still uses `chocolate cake`. M3 will add deterministic aliases such as `cake`, `birthday cake` → canonical `chocolate cake`, with customer review for ambiguous matches. Gemma later produces candidates; it never sets price, discount, capacity or acceptance.

For pharmacy, the router may handle named-product requests such as “Dolo 650 one strip”. It must route “I have fever, what medicine?” for clarification; it must not prescribe a product.

## Demonstration target

Use the current three phones with Sweet Crumbs and HomeBake as Bakery sellers. Add a pharmacy seller profile once domain onboarding is implemented. The final demo story is:

1. Customer says or types a natural request.
2. Review card shows domain, canonical item, quantity, budget, deadline and constraints.
3. Customer confirms.
4. Bakery sellers receive it; the pharmacy phone receives nothing.
5. Each bakery's local ShopAgent returns its own quote.
6. Ranker filters and sorts valid offers.
7. Customer selects; seller accepts and reserves; other offers close.
8. UPI handles payment later in the M5 payment milestone.

## Source code

The complete source is this `Nukkad` directory. It includes Gradle wrapper, AndroidManifest, Kotlin production code, tests, README, roadmap and validation report. `Nukkad-source.zip` is the source archive for sharing at the hackathon. Do not present `app/build` or `.gradle` as source; those are generated build outputs.

Build with JDK 17:

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

The APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.
