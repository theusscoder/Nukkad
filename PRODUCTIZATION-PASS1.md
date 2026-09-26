# Nukkad productization checkpoint — Pass 1

## Inspection and reuse

The project remains the existing Kotlin/Compose application. Reused: HarnessSession, MqttTransport, FakeTransport, CustomerViewModel, SellerViewModel, OrderBook, ShopAgent, Ranker, protocol messages, and JSON ledgers. No networking rewrite, maps, accounts, database, or new business domains.

Modified: NukkadApp entry point; Theme; HarnessViewModel (saved merchant profile and structured request submission); Seeds (bakery-only routing); SpeechInput lifecycle; AndroidManifest speech-service visibility and app label; Gradle flavor/version configuration; MerchantRules and ShopAgent for item automation.

Added: product/ProductApp.kt, CustomerScreens.kt, MerchantScreens.kt, ProductStore.kt, model/AutomationRule.kt, Kotlin AppAudience flavor files, and AutomationRuleTest.

## What this build does

- Distinct Customer and Merchant launcher names/application IDs.
- Cream, charcoal, saffron and sage visual theme.
- Persisted three-page onboarding in each app.
- Customer voice/text entry, three recognition languages, sample requests, animated listening, transcript and editable request review.
- Missing fields remain empty. Quantity/unit are now passed through rather than forcing every request to kg.
- Existing deterministic ranking drives offer cards and existing selection/cancellation drives order cards.
- Merchant home with real counts since connecting, latest request/checklist and catalogue cards.
- Persistent shop name, category, UPI ID, catalogue items, prices, unit, supported option surcharges and maximum quantities.
- Persistent shop floor, lead time, capacity, opening hours and item automation rules.
- Rule actions: AUTO_QUOTE, AUTO_ACCEPT, ASK_OWNER, NO_MATCH. First matching item/price rule wins.
- Thresholds use calculated order value, not customer budget. Existing price/budget/capacity/deadline checks still apply.
- AUTO_ACCEPT pre-authorizes acceptance when the customer selects the offer; broadcast requests do not reserve multiple merchants.
- ASK_OWNER flags the request for attention; a manual owner quote/accept workflow is not included yet.
- Automatic join of the existing configured demo market. Technical details appear only in Developer / Diagnostics.
- User-friendly connection failures and retry controls.
- Settings accurately identifies the fallback interpreter and unavailable Gemma engine.

## Scope and limits

This is the first productization checkpoint, not completion of all four passes.

- Gemma is not bundled/loaded; model integration and strict output grounding are Pass 2.
- Fallback extraction is limited, especially for Hindi/Telugu text and spoken number words. Review all fields.
- Deadline review currently uses hours from now; natural-language deadline parsing is pending.
- Android merchant notifications, sequential check choreography and spoken customer offer summaries are Pass 3.
- Full interface translation is not implemented; speech recognition offers Hindi/Telugu/English.
- App language, response-language and spoken-response preferences will be enabled alongside actual localized/TTS behavior rather than nonfunctional controls.
- Metrics are honestly labelled since connecting; they are not persisted daily analytics.
- Same public MQTT demo market and internet dependency remain. No nearby distance or Bluetooth discovery.
- UPI ID is stored; payments are not implemented or marked paid.
- Physical-phone visual, voice and multi-phone network validation must still be done on the target devices.

## Phone walkthrough

1. Install Customer on one phone, Merchant on each shop phone.
2. Complete onboarding. No session entry is required.
3. Merchant: Profile -> set unique shop name and category; save. Category changes clear old catalogue items.
4. Merchant: Catalogue -> add items and options; Rules -> set capacity, pricing and timings; save.
5. Customer: speak or type, review category/item/quantity/unit/budget/time and options, then Find sellers.
6. For the seeded demo: chocolate cake, 1 kg, budget 800, eggless, deadline 24 hours from now. Expected quote is 750 when shop rules permit it.
7. Leave both apps open. Confirm the merchant's actual checks and quote match the received customer offer.
8. Select the offer, then cancel if repeating the demo. Unpaid reservations still expire.
9. Test disconnect/reconnect: normal screens show a retry card, diagnostics contains technical details.
