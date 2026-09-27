# Nukkad productization — Passes 4–6

## What changed

This is an additive product pass over the existing customer/merchant APKs. It keeps the existing request protocol, MQTT topics, customer Gemma interpreter and fallback parser, offer ranking, notifications, and ShopAgent authority.

### Merchant experience

- Home, Shop, and Orders are the primary tabs. Shop profile and rule editing remain reachable from the existing screens.
- Merchants can choose **EXACT — What I sell**, **FLEX — Similar work**, or **OPEN — My category**. The setting is saved with the local shop profile.
- FLEX quotes only names the merchant explicitly entered as aliases for a catalogue item. Its saved catalogue price and existing quantity, option, budget, capacity, and deadline checks still apply.
- OPEN unknown items reach **Needs you**. The agent does not invent an item price. The merchant can enter a price and ready time and send an owner-approved offer. Budget, deadline, capacity, and duplicate-request checks run before it is sent; selection continues through the existing order flow.
- **Tell Nukkad** uses Android SpeechRecognizer in the selected speech language. A small local extractor proposes an item, price, unit, option surcharge, and capacity. The merchant can correct the fields and must press **Looks good** before the saved catalogue/rules change. Manual catalogue editing remains available.
- New requests reveal their real item, quantity, options, budget, and deadline with a staged visual treatment. Optional merchant guidance reads the request in the selected English, Hindi, or Telugu TTS voice. Existing notification vibration remains enabled.
- The ShopAgent animation reveals only the actual checks returned by ShopAgent. A real automatic offer or manually approved owner offer is shown after those checks.

### Shop scanning and GPS discovery

- **Scan my shop** opens a CameraX still-image capture. Bundled ML Kit OCR turns legible printed text into editable item/price/unit candidates. **Add** appends new items; items with duplicate names are left unchanged. The camera/OCR path does not alter MQTT.
- Merchants can save or refresh their current shop location. The exact location stays on the merchant phone. Request receipts and offers share only a coarsened location; customers see an approximate straight-line distance and approximate area.
- Customers can request their current location after sending a request. If they grant permission, actual merchant receipts appear as “Checking” markers, and received offers appear as price markers in the H3 hex view. Only real broker responses/offers are counted. Without a customer or shop location, the existing offer list remains available.
- The hex view is a native Compose canvas built from H3 cell boundaries; it uses no Maps API key or map tiles. It shows the customer point, real response/offer locations, price markers, and an offer detail card. Distances are not derived from H3 ring counts.

## Protected request/offer flow

Customer `SpeechInput` / typed text → `GemmaLlmEngine` or `DeterministicIntentParser` → editable `IntentDraft` review → `HarnessViewModel.sendDraft` → `CustomerViewModel.send` → existing `Protocol.requests(...)` MQTT topic → `HarnessSession` receives and acknowledges → `SellerViewModel` → `OrderBook` / real `ShopAgent` checks → existing offer topic → `CustomerViewModel` / `Ranker` → offer list and optional `OfferSpeaker`.

The new merchant setup parser only creates editable candidates; it does not replace customer Gemma or ShopAgent. New profile, item, receipt, and offer fields have defaults so older persisted records/messages continue to decode with the existing `ignoreUnknownKeys` behavior.

## Files added or extended

- `product/MerchantSetup.kt`: editable voice/OCR candidate extraction.
- `product/DeviceLocation.kt`: permission-aware fused location and approximate distance helpers.
- `product/MerchantScanner.kt` (merchant flavor): CameraX capture and ML Kit OCR.
- `product/HexDiscovery.kt` (customer flavor): H3 cells, real response/offer markers, and offer details.
- `model/GeoPoint.kt`: validated coordinate model and public coarsening.
- Existing profile/item/offer/receipt, ShopAgent/OrderBook, speech, product screens, manifest, Gradle dependencies, and theme were extended in place.

## Known limits

- Merchant voice setup currently uses a bounded local extractor, not Gemma. Merchant builds intentionally did not include the customer-only LiteRT-LM engine, and the current Gemma extraction contract is for customer requests. Every candidate is editable; unclear items/prices must be corrected manually.
- ML Kit uses its Latin text model. Printed English/Latin menus are the intended scan path; handwriting, glare, low resolution, and non-Latin menus can fail. Failure returns to voice/manual entry.
- Location requires Google Play Services and a runtime location grant. Android may provide approximate-only permission. Offer coordinates are rounded to protect exact shop addresses, so displayed distances are approximate (and straight-line, not walking/driving).
- The discovery view is a hex map canvas without street tiles, routing, or a Maps API key. It only knows merchants that send a real acknowledgement or offer through the current MQTT flow; it cannot discover silent or offline merchants.
- MQTT and current foreground/connection constraints remain. No background delivery guarantee, payment, Firebase, new backend, or local Bluetooth/Wi-Fi discovery was added.
- Android TTS and SpeechRecognizer quality depends on the language services/voices installed on each phone.

## Phone verification

1. Install the customer APK on the customer phone and the merchant APK on each merchant phone; allow merchant notifications.
2. In Shop, select EXACT, FLEX, or OPEN, leave and reopen the app, and verify the choice persists.
3. Try FLEX with an exact alias entered on an item; try an unlisted name in OPEN and verify no automatic price appears.
4. Use Tell Nukkad with a spoken or typed shop description, correct the draft, and press Looks good. Verify the item/price/option are saved. Cancel before Looks good and verify nothing changes.
5. Scan a clear printed Latin menu. Correct a candidate and Add it; verify existing same-name catalogue items remain unchanged. Deny camera access and verify manual/voice setup still works.
6. Save shop location on a merchant phone. Send a customer request, allow customer location, and verify only responding/quoting merchants appear in the hex view. Compare an offer’s approximate distance with the phone’s actual distance; do not expect road distance.
7. Test an OPEN unknown item: send a merchant-set price and ready time within the shown budget/deadline, receive it on the customer phone, select it, and confirm the order. Try an over-budget price and verify it is rejected.
8. Run the original cake voice/type → review → MQTT → merchant notification → real checks → offer → customer ranking/TTS flow. Deny location and confirm the ordinary offer list continues working.

The build can verify compilation/unit behavior; physical CameraX, GPS permissions, speech, MQTT, and TTS still require the phones for final confirmation.
