# Nukkad UI and interaction pass

This pass updates the existing customer and merchant app shells while retaining the current request, Gemma, MQTT, ShopAgent, catalogue, location, notification, speech, offer, and OCR paths.

## Changed experience

- Added a persistent floating bottom navigation bar: Customer Home / Search / Requests and Merchant Home / Shop / Orders. Its cream selection marker springs between tabs and the bar stays outside the scrolling screen content. The merchant camera flow hides the bar.
- Customer Home now has a circular breathing microphone orb, language choices, typed fallback, examples, live partial speech text, Gemma/fallback interpretation, and a compact request summary. If a required field is absent, it asks for one field at a time; Edit details still opens the full editor. Sending opens Search.
- Customer Search shows the existing H3 radar, actual responding merchants and offers, moving connection dots, approximate distances, and an offer detail sheet. Newly received offers produce a haptic. Without a request it offers a path back to Home.
- Requests shows the current request/reply/order state. Historical request storage is not part of this pass.
- Merchant Shop foregrounds Tell Nukkad, Scan Menu, catalogue count, and the existing catalogue editor. Shop profile and rules remain reachable. Orders are grouped into Active, Ready, and Past, with a compact empty state.
- OCR now displays the raw Latin text recognized from the captured image before showing editable product candidates. Capture still uses the bundled ML Kit Latin recognizer and the captured file as InputImage.

## Protected logic and known limits

Gemma and its deterministic fallback still produce the customer draft. Requests and offers continue over the current MQTT transport. Merchant decisions and owner-entered quotes still use the existing ShopAgent and OrderBook. Location is opt-in and distances are straight-line estimates; no shop is fabricated. No Google Maps key, backend, database migration, or new transport is added.

Search only includes merchants that actually acknowledge or quote. The Radar remains the local H3 canvas, not a tiled Google Map. Requests tab currently reflects the active request lifecycle rather than a persisted history. Merchant OCR is bundled Latin text recognition; Hindi/Telugu OCR and handwriting recognition are not promised. Merchant spoken setup remains the existing local parser and requires review. Camera, location, MQTT delivery, and notification behavior still need validation on physical phones.

## Build and verification

Build both audience variants and run their unit tests and lint tasks:

```powershell
.\gradlew.bat :app:assembleCustomerDebug :app:assembleMerchantDebug :app:testCustomerDebugUnitTest :app:testMerchantDebugUnitTest :app:lintCustomerDebug :app:lintMerchantDebug
```

Physical demo check: install the Customer APK on one phone and Merchant APKs on the other two; confirm the bottom tabs, enter a typed and spoken request, inspect Search while the merchants respond, send an offer, select it, and verify the order. On a merchant phone, also test camera permission, a printed English menu, OCR text preview and edits, then save. Deny location once and verify that requests and offers still work.
