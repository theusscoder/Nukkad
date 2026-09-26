# Validation — M3 / 0.4

- `:app:assembleDebug`: PASS. APK versionName `0.4-m3-routing`, versionCode 4.
- `:app:testDebugUnitTest`: PASS — 49 tests in the normal run, zero failures. The optional public-broker tests are included in the suite but require `-PmqttSmoke=true`; the broker-dependent three-client test may be skipped or fail on external broker availability.
- `:app:lintDebug`: PASS with no lint errors. Existing non-blocking dependency/target warnings remain.
- New `CommerceDomain` closed enum and `SellerProfile.domains` routing foundation are covered by `DomainRoutingTest`: a Bakery request reaches a Bakery seller and leaves a Pharmacy seller in Live state.
- Request now carries validated domain plus optional `originalTranscript` and `normalizedText` fields, with defaults preserving existing M0/M2 source and wire construction.
- Existing M2 selection, reservation, cancellation, expiry and persistence tests remain in the source.

## What M3 means here

This is the domain-routing foundation, not the complete no-code onboarding experience. The demo still uses an M2 session code for isolation. The next M3 increment is seller presence/directory discovery and the customer category/area screen, followed by deterministic catalogue aliases. SpeechRecognizer, Gemma/LiteRT-LM and UPI remain later checkpoints.

## Source

`Nukkad-source.zip` contains the complete source project without generated Gradle/build directories. `Nukkad-SOURCE-HANDOFF.md` explains the package layout and build command. `Nukkad-v0.4-M3-routing.apk` is the shareable APK.
