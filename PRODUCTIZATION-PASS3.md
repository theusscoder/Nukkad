# Nukkad — Pass 3: request alerts and offer voice

## What changed

- Merchant requests post a high-priority Android notification with the shop name, item, and any stated quantity/options. Android 13+ asks for notification permission once after merchant onboarding. The notification opens the merchant app on Home, where the latest request is shown.
- Merchant Home reveals the actual `Decision.checks` one at a time, including each check's real result and detail. The quote/owner/no-match result appears when those checks have been shown. The business decision itself remains the existing ShopAgent decision.
- Customers can turn on **Settings → Language & voice → Speak offer summaries**. Each offer then has a **Hear offer** action that speaks the shop, amount, and ready time in the selected English, Hindi, or Telugu speech language.
- Existing customer confirmation, seller pricing rules, offer ranking, selection, and order lifecycle are unchanged.

## Phone walkthrough

1. Install the Merchant APK over the earlier merchant app and allow notifications when Android asks. If permission is denied, enable Nukkad notifications from Android App info.
2. Keep the merchant app connected. On the customer phone, send a complete request that matches the merchant's area, category, catalogue item, unit, budget, constraints, and deadline.
3. Confirm the merchant phone receives a notification. Tap it and verify Nukkad opens Home to the latest request.
4. Watch checks appear sequentially with their pass/fail details; then confirm the app shows Auto-quoted, Needs owner, or No match from the actual merchant decision.
5. On the customer phone, enable spoken summaries in Settings. When an offer arrives, tap **Hear offer** and check the spoken shop, amount, and ready time. Try English, Hindi, and Telugu speech languages if the device TTS service provides them.
6. Select an offer and complete the existing order flow.

## Limits and validation

- Android can suppress notification banners because of user settings, Do Not Disturb, or denied permission. The latest request still appears inside the open merchant app. The public MQTT demo also still requires internet and foreground-connected apps.
- Check animation is a staged display of checks already evaluated by ShopAgent; it does not slow down or alter the merchant decision.
- TTS depends on installed Android voice data for the selected language. The UI action is available only when spoken summaries are enabled.
- `:app:assembleCustomerDebug :app:assembleMerchantDebug :app:lintCustomerDebug :app:lintMerchantDebug` passed. Phone notification delivery and Hindi/Telugu TTS need a physical-device walkthrough.
