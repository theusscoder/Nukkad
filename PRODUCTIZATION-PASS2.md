# Nukkad — Pass 2: on-device request interpretation

## What changed

Pass 2 adds a Gemma 3 1B interpreter to the customer APK using Google's LiteRT-LM Android runtime. The engine is isolated in `ai/GemmaLlmEngine.kt`; Compose and the marketplace's merchant authorization/ranking logic do not call LiteRT-LM directly.

- The customer can import a compatible `.litertlm` model from Settings. Nukkad copies it into app-private storage; no broad storage permission is requested.
- The app initializes LiteRT-LM on a background dispatcher and runs the first implementation on CPU. The model is not bundled, downloaded automatically, or included in the APK.
- Gemma receives the voice-recognition transcript or typed text and the selected language tag, and is asked for structured JSON: category, item, quantity, unit, budget, time phrase, constraints, and confidence.
- Clear category cues in the transcript (including Dolo/medicine, cake/bakery, and cupboard/hinge carpentry examples) correct an inconsistent model category, and known example items are recovered if Gemma leaves the item blank. The review card explicitly labels the detected category.
- Medicine dosage numbers such as the `650` in `Dolo 650` are not treated as order quantity. A quantity is accepted only when a count/amount is paired with an order unit such as strip, tablet, kg, or pieces; common English, Hindi, and Telugu number words from one to ten are recognized for that grounding check.
- The review card still requires the customer to check and confirm all fields before sending. A returned deadline phrase is displayed for confirmation; it is not silently converted to a deadline.
- Model-generated quantity and budget numbers are discarded unless the same number is explicitly present in the transcript. Category/item ambiguity remains editable. Gemma does not set prices, diagnose symptoms, choose merchants, rank offers, or authorize acceptance.
- If no model is imported, the current deterministic parser is used. If Gemma initialization, inference, or JSON parsing fails, the screen explains that it switched back to the offline fallback.
- No API key, server inference, or database is used for interpretation. Speech-to-text remains Android SpeechRecognizer and may still depend on the phone's speech service/network; Gemma is a separate local text-processing step.

## What happens on the phone

1. Install the Customer APK and open Settings → Request interpreter → Choose Gemma `.litertlm` model.
2. Select a compatible Gemma 3 1B instruction-tuned LiteRT-LM model file. The picker copies it into Nukkad's private files and remembers the selection.
3. Return home and speak or type a request. The first Gemma request may take longer while the model initializes; subsequent requests reuse the runtime during that screen session.
4. Nukkad asks Gemma for structured candidate fields, applies basic grounding checks, and displays the original transcript and interpreted fields for customer review.
5. Correct anything uncertain and send. Marketplace transport and merchant-side checks then continue through the existing flow.

Without importing a model, the app works as before with the deterministic fallback. Importing a model is optional. Use only a compatible `.litertlm` file; GGUF, `.task`, and raw `.tflite` files are not interchangeable with this loader.

## Build and validation

- LiteRT-LM dependency pinned to `0.11.0`.
- Kotlin Android, serialization, and Compose plugins upgraded together from `2.1.20` to `2.3.0`, because the LiteRT-LM artifact's Kotlin metadata is 2.3.0. Android Gradle Plugin remains 8.9.2.
- `:app:assembleCustomerDebug :app:assembleMerchantDebug` — passed.
- `:app:lintCustomerDebug :app:lintMerchantDebug` — passed.
- On-device model loading/inference has not yet been physically validated: no model file or connected target-phone inference run was available during this build. Therefore the APK includes the integration, but “Gemma works on the iQOO” is not claimed until the model is imported and the sample requests are checked on the phone.

## Pass 2 acceptance walkthrough

On the iQOO 15, import the compatible model, then try:

```text
I need one kg eggless chocolate cake under 800 rupees by 9 PM
Dolo 650 ka ek strip aaj raat 10 baje tak chahiye
Need someone to fix my cupboard hinge tomorrow morning
```

Confirm the category, item, quantities and units shown in the review card; check that no unsupported numbers are invented; verify the original transcript remains visible; correct deadline fields manually; and confirm each request only sends after tapping Find sellers. Repeat once without a model loaded to confirm fallback behavior. Record first-load time, later inference time, memory/thermal behavior, and any Hindi/Telugu field corrections.

## APK outputs

```text
app/build/outputs/apk/customer/debug/app-customer-debug.apk
app/build/outputs/apk/merchant/debug/app-merchant-debug.apk
```

LiteRT-LM is included only in the Customer flavor; the Merchant APK does not carry unused model-runtime libraries. The large model file itself is not bundled.
