# Validation — productization checkpoint

Command:
gradlew.bat :app:assembleCustomerDebug :app:assembleMerchantDebug :app:testCustomerDebugUnitTest :app:testMerchantDebugUnitTest :app:lintCustomerDebug :app:lintMerchantDebug

Result: successful.
Each flavor: 53 discovered tests, 51 passed, 2 opt-in public MQTT tests skipped, 0 failures.
Both lint tasks passed. Subsequent customer-screen layout changes are verified by assemble and lint.
No physical device UI/speech/network test was completed in this environment.
ADB could not start because its Android directory resolved to a filesystem location it could not create.

No global JavaCompile disable remains. Flavor identity is Kotlin; generated Java BuildConfig is disabled intentionally.
Keep the existing debug signing key for upgrades. APK filenames use v0.7 and Android versionCode is 7.

See PRODUCTIZATION-PASS1.md for scope, actual limitations, inspection inventory and phone walkthrough.
