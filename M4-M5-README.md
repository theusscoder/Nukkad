# M4-M5 voice and on-device AI status

## What is in this APK

This build adds the voice-to-request path on top of the M2/M3 marketplace flow:

- Customer chooses Hindi (`hi-IN`), Telugu (`te-IN`), or English (`en-IN`).
- `Speak request` invokes Android `SpeechRecognizer`.
- The transcript is shown for review.
- A deterministic on-device parser extracts a domain, item, quantity, budget, and constraints when those values are explicit.
- The customer edits or confirms the fields before the request is sent, so speech never silently creates an order.
- Domain routing remains active: bakery, pharmacy, carpentry, plumbing, and other categories can be extended through the seller profile.

## Phone setup

No API key is needed for this APK. Android speech recognition may use the phone's installed speech service and can require an internet connection depending on that service. Grant microphone permission when prompted. Hindi and Telugu work when the selected speech service on the phone supports `hi-IN` and `te-IN`; the app cannot add a missing language pack itself.

For a clean three-phone test, install the same APK on all phones, grant microphone permission on the customer phone, select a language, speak, verify the transcript and extracted fields, then edit/confirm and send. Keep the existing M2 connection/session procedure for this debug build.

## Gemma status

The app now has the `LlmEngine` interface and a safe deterministic fallback. Gemma inference is intentionally not claimed as active yet: the exact LiteRT-LM/Gemma Android runtime and model artifact must be selected and tested against the target phone's RAM and storage. Gemma on-device does not require an API key; it requires a compatible model file bundled or copied to the device and the matching runtime. Until that runtime is verified, the fallback keeps the demo offline and deterministic.

When Gemma is wired, it will return the same `IntentDraft` contract, pass through `IntentValidator`, and still require customer confirmation. No network key should be placed in the APK.

## Success check

1. Tap `Speak request` and grant the microphone permission.
2. Say, for example, “one kilo eggless cake under eight hundred rupees”.
3. Confirm that transcript, bakery domain, quantity, budget, and eggless constraint appear.
4. Edit if needed and send; the existing seller quote and selection flow should behave as before.
5. Repeat in Hindi and Telugu and record the phone model, Android version, speech service, and transcript quality.
