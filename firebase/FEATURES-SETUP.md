# Chat, reviews, photos and emergency SMS setup

**Current emergency flow:** the app opens the phone's SMS composer with the saved number and complete message. The customer taps Send there. No gateway account, API key, or backend endpoint is needed. Previously saved contacts cached on this device can be used offline. First-time contact retrieval/saving still needs internet. SMS load or a suitable SMS plan is required to send; the app does not claim delivery. The gateway instructions below describe the earlier, unused implementation and are not required for the current app.

The Android screens retain their existing layouts. Contact/review saves wait for Firebase confirmation. Reviews remain editable as in the existing UI, with one document per booking. Ratings are calculated from those authorized reviews.

## 1. Publish the complete Firestore rules

Open `firestore-combined.txt` in Notepad. Select all and copy. In Firebase project **hiremydriver-e88f7 → Firestore → Rules**, replace the entire editor contents and Publish. The first line must be `rules_version = '2';`. This file preserves registration and booking permissions and adds chat, display thumbnails, reviews and private emergency-contact rules. Do not paste only the new sections.

In Firestore → Indexes, create these **collection** composite indexes for `conversations`:

| Field 1 (Ascending) | Field 2 (Descending) |
| --- | --- |
| customerId | updatedAt |
| driverId | updatedAt |

The same definitions are in `messaging/firestore.indexes.json`. Wait until both indexes are enabled. Use a real saved booking, not a legacy sample booking, to start chat or submit a review.

Small profile-display thumbnails are readable by signed-in users. Full-resolution photos and registration documents retain their existing private Supabase access.

## 2. Configure real SMS (not active yet)

Prepared provider: **Semaphore**, using its priority SMS API for Philippine numbers. Do not purchase credits until the provider approves your application/sender and confirms this emergency-alert use case. Pricing is per SMS segment, and the detailed alert may span several segments. See https://semaphore.co/docs and https://semaphore.co/faq.

1. Create a provider account, request an approved sender name, and obtain its API key. Keep the key on the server only.
2. In `C:\WebProjects\hiremydriver-admin\.env.local`, add:

   ```dotenv
   EMERGENCY_SMS_ENABLED=true
   SEMAPHORE_API_KEY=your_provider_api_key
   SEMAPHORE_SENDERNAME=your_approved_sender_name
   ```

   No `NEXT_PUBLIC_` prefix. Do not paste the secret in chat, Android resources, Firebase rules or source control. Until ready, leave `EMERGENCY_SMS_ENABLED=false` or omit it.
3. Restart the admin backend. For use on actual phones, deploy it to a reachable HTTPS URL and set these variables in that hosting service too. The new endpoint is `/api/emergency-alert`; it verifies the customer's Firebase ID token and reads that customer's saved contact and booking. The admin dashboard UI is unchanged.
4. In the Android project's `local.properties`, add your real deployed endpoint:

   ```properties
   EMERGENCY_API_URL=https://your-admin-domain/api/emergency-alert
   ```

   Replace the example hostname; rebuild and install the app. A localhost URL from your PC will not work on another phone.
5. Save a contact in Customer Settings. With the recipient's consent, test on a real active booking and check the provider dashboard and recipient phone. Do not use an unsuspecting emergency contact for testing.

## Status and reliability

- The confirmation screen asks permission to send to the saved number. Location comes from the **customer phone**, never the driver's tracking coordinates. A location older than two minutes is marked unavailable.
- The existing “Send trip details” switch is respected. With it enabled, the SMS includes pickup, all destinations and driver; customer name, current location or unavailable notice, and Philippine date/time are always included.
- `queued`/`pending` means waiting for the network. `sent` means sent to the network, **not proof the recipient read or received it**. `failed`/`refunded` means failure.
- Tap again to check provider status. The server allows one alert dispatch per booking to prevent accidental repeat sends. A timeout remains **unconfirmed** and is never retried automatically; inspect the provider dashboard before any manual resend. This limitation is deliberate until a separate confirmed re-send flow is designed.
- No real SMS is possible without provider setup, credits, an approved sender and a reachable backend. This integration adds no Android SMS permission and does not send from the user's SIM.

## Verify on two phones

Sign in as the booking's customer and driver. Open Message from the booking; exchange text, leave/reopen, and check history. Upload each profile photo and rapidly switch Home/Profile. Save and reopen the emergency contact. Complete a booking, submit/edit its review, and check the driver's rating and review screen. Reopen both apps to check persisted data.

Local verification uses `test-customer-features.cjs` and `test-booking-rules.cjs` in the Firestore emulator, Android unit tests, and `server/test-emergency.cjs` with a mock provider. These tests do not prove live carrier delivery or device rendering.
