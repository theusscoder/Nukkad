# M4-M5 validation

- Build: `:app:testDebugUnitTest :app:assembleDebug :app:lintDebug`
- Result: BUILD SUCCESSFUL
- Unit tests: 50 executed successfully; 2 MQTT tests skipped unless `-PmqttSmoke=true` is supplied.
- APK: `app/build/outputs/apk/debug/app-debug.apk`
- Voice path: Android SpeechRecognizer with runtime microphone permission; Hindi/Telugu/English locale chips.
- AI path: deterministic parser and `LlmEngine` contract; Gemma runtime/model is not bundled yet.
- No API keys are embedded or required.
