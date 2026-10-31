# Current app update — October 18, 2026

## Latest languages, service-area search and approval update

**Publish the complete current `firebase/firestore-combined.txt`, then install the latest APK.** This update adds optional offline `matching.languages` storage and requires a selected language before accepting bookings. It also enforces approval for public listing, going online, new booking creation and request acceptance. Existing registration, messages, review, notification, active-trip and history rules remain combined in the same file. Earlier no-rules-change notes below refer only to those earlier updates.

- Languages Spoken appears below Preferred Service Area in Driving Information. Drivers explicitly select one or more of Filipino, English and Cebuano/Bisaya. Save Changes persists the selections in the existing `driverProfiles/{uid}.matching` map and the customer profile shows those exact choices. Existing service-area selections remain intact. Existing drivers must select languages and save their information before turning availability on with this version.
- Customer driver profiles display only enabled, configured, valid working days and their saved hours. Off/unconfigured rows are omitted from this display only; the saved weekly schedule and schedule editor are retained. The Response Rate row was removed as requested.
- Location/name search uses saved service areas and requires an approved, online driver with complete information. It no longer invents an immediate one-hour trip while the customer is merely searching. Municipality/province searches such as Sikatuna, Bohol are supported. The dated matching engine still checks saved working hours and conflicting bookings.
- Driver Home retains the original check icon/badge, gray with Pending until approved and blue with Verified Driver afterward. Pending drivers cannot enable either availability switch or appear in customer directory/recommendation/search results. Firebase rejects new requests and acceptance when approval or setup is missing, including requests attempted through older app versions.
- Approved drivers with incomplete information can tap the switch to see Driving information incomplete and the requested explanation, with a shortcut to complete the setup. The switch remains off. Saving setup or becoming approved does not automatically turn availability on.
- Approval uses the existing `driverApplications/{uid}.status` and `driverProfiles/{uid}.status` fields, with `pending`/`approved` values. The future admin connection must set both statuses together using a trusted server transaction/batch; app clients cannot approve themselves. No admin code, live registration status or dashboard deployment was changed in this update.

Verification: all 215 Android unit tests and 143 local Firestore checks passed (48 matching/setup, 44 customer features, 51 booking workflow). Debug APK and instrumentation APK compiled. Tests cover pending/online restrictions, self-approval rejection, approved incomplete setup, booking creation/acceptance guards, saved languages, working-day display and service-area search. No phone was connected; screen rendering and the future admin-to-phone approval interaction still need device testing.

## Latest chat photo, reopening and launch-logo fix

This follow-up needs **only the updated APK** if the notification/status-history rules below are already published. No Firebase rules or backend deployment changed.

- The shared customer/driver chat header now masks the photo with the existing circular image component and preserves its 40dp size and light border. The square photo corners can no longer extend outside the circle.
- Previously loaded conversation details resolve from the existing Firestore cache before network fallback. A bounded, account-scoped memory cache restores confirmed chat history while live listeners refresh it; sending stays disabled until the live chat is ready. Failed/pending sends are not replayed from this cache.
- Identical chat updates no longer redraw the view. Unread inbox rows wait for their read-state evidence instead of briefly appearing read. Inbox rows retain their identity by conversation partner across bookings, and stale screen-opening callbacks are ignored.
- The Android launch theme uses a transparent icon, removing the extra logo before the existing full-screen splash. The existing splash artwork, logo and timing are unchanged. Android still owns the initial launch surface; its appearance must be checked on the target phone.
- Verification: debug APK and instrumentation APK compiled; all 214 unit tests passed, including reopening, live refresh, account isolation, unread loading and cache eviction. No phone was connected for visual verification. Install over the current app and test both message roles, repeated back/open actions and a cold app launch.

## Latest notifications, trip timer and persistent chat update

**Publish the entire current `firebase/firestore-combined.txt` before using this APK for driver booking actions.** This update adds validated `statusEvents` timestamps to existing booking records. The combined file preserves the previous registration, matching, schedule, messaging, review and notification rules. This requirement supersedes the no-rules-change note for the older service-area-only update below.

- Confirmed Accepted, On the Way, Arrived, Trip Started and Completed events use the existing device notification relay. A delayed event can be verified against the booking's saved status history after the booking advances. Existing event IDs prevent duplicate notifications.
- The customer timer uses the saved trip start/completion timestamps. It runs only In Progress and freezes at the completed duration. Completion also cancels the ongoing Android trip notification, including when received in the background.
- Repeat bookings reuse the pair's existing active conversation. Earlier booking conversations are combined into the same chronological view without moving or deleting stored messages. Each source conversation retains its independent read cursor.
- Incoming unread messages and inbox previews are bold until read. Own messages do not count as unread. Failed read acknowledgements can retry; temporary message-loading failures retain already loaded history.
- Home notification bells show a red dot for real unread booking/review/approval records, excluding chat messages. Viewing notification rows or marking all as read updates the saved records and both badges.
- Original screen layouts, icons, colors and navigation remain in place.

Verification: 210 Android unit tests and 120 Firestore emulator checks passed (39 matching, 44 customer features, 37 booking workflow). Debug APK and instrumentation APK compiled. Backend event tests, TypeScript checking and the dashboard production build passed. Backend commit `8e407a5` was pushed; Vercel reported a successful deployment, and the live relay rejects unauthenticated calls with HTTP 401. No paid service or billing change was added.

Install `app/build/outputs/apk/debug/app-debug.apk` over the existing app after publishing the rules. Allow notifications and open each account's Home screen once. Two-phone/background delivery and visual interaction still require device testing; automated tests and the deployment check do not prove end-to-end FCM delivery. See `docs/notifications-chat-timing.md` for the focused checks.

## Latest preferred-service-area update

The driver picker now suggests cities/municipalities from the GPS-detected province, with manual search when location is unavailable. Drivers explicitly choose their service areas; GPS never automatically selects them. Save Changes uses the existing `matching.areas` field. Customer Home search uses saved service areas and existing availability/schedule/conflict checks, not the driver's GPS location. Original main-screen layouts remain unchanged.

This update does **not** change Firebase rules or require another publish if the mandatory-driver-setup rules below are already live. Install the latest APK. See `docs/service-area-selection.md` for data-source details and phone checks. Verification: 204 app unit tests and 39 matching/Firebase checks passed; APK and instrumentation compiled. No phone was connected for GPS/UI testing.

## Latest mandatory driver setup and schedule update

Publish the entire updated `firebase/firestore-combined.txt` before saving schedules with this APK. It combines the existing rules with the driver setup checks and an optional `configured` flag on each weekly day. No new collection, paid service or admin deployment is required.

- Driver Home and Availability & Schedule require server-saved vehicle types, Manual/Automatic capability, numeric experience (0–80 years), service areas and an hourly rate (0–100000, preserving existing zero-rate support). Incomplete setup disables the existing toggle; its existing status label explains how to open Driving Information. Turning availability on is confirmed only after the server save.
- The form validates these fields before saving. Firebase also rejects enabling availability when required information is missing. Customer matching excludes incomplete profiles even if an old record still says availability is on.
- Customer profile transmission badges and the booking vehicle picker use the driver's actual saved capabilities.
- An untouched weekly day is now distinct from an explicitly disabled day. Untouched days display Not set, explicit disabled days display Off, and working days show the saved hours. No default weekday/off-day template is published. Previously saved schedules remain backward compatible; the app does not guess which old saved Off entries were intentional.
- Empty server schedules clear stale local schedule caches. Late reads cannot overwrite newly saved edits, and edits made while the initial server refresh is running are still uploaded before reporting success. The schedule editor waits for the saved schedule to load before enabling Save.
- Verification: 198 unit tests; 35 matching/setup rules checks, 44 customer-feature rules checks, and 29 booking rules checks passed. Android debug build and instrumentation compilation passed. Device rendering and two-account interaction still require phone testing.

## Previous notification, search history and matching update

- That notification-only update did not require another rules publication. The newer driver setup/schedule update above does require publishing the current combined file. No new collections, billing plan, keys or indexes were added.
- The notification bell lists booking events only for customers, and booking events, customer reviews and admin approvals for drivers. Existing message events are excluded by the query, so chat activity cannot crowd booking alerts out of the list. Messages and their unread badge remain unchanged.
- Successful review submissions notify the booking's driver once per booking. The server validates the reviewer and completed booking. Admin approval saves the notification atomically with the decision; tapping it opens Driver Profile. Failed push delivery does not roll back an approval, and the saved notification remains visible in the app.
- Backend commit `7203106` was pushed to GitHub and its deployment status reported success. End-to-end phone delivery still needs device testing.
- Customer Home now keeps the latest ten search terms locally per account. Submit the keyboard Search action or open a search result to save a term. Focus the empty search field to see Recent searches; tap to reuse, remove a single term, or Clear all.
- Recommended Drivers uses existing registered Firebase profiles and saved availability. Stale demo descriptions are removed. Fictional driver fixtures now exist only in unit-test sources, not in the APK. Approval eligibility and the existing matching rules are unchanged.
- Final Android build, all 192 unit tests, instrumentation compilation, notification event authorization tests and dashboard production build passed. No phone was connected, so visual interaction and cross-device notification delivery were not run.

## Activate the update

1. Open `firebase/firestore-combined.txt`. Copy the **entire file**. In Firebase project `hiremydriver-e88f7`, open Firestore Database → Rules, select all inside the editor, paste, and Publish. Check that line 1 is `rules_version = '2';` and that the closing braces are included. This is the combined registration, booking, matching, chat, review, photo, emergency-contact and notification rules file, not a separate replacement for customer registration.
2. Install `app/build/outputs/apk/debug/app-debug.apk` over the existing app on the customer and driver phones. Do not uninstall or clear data to update.
3. Allow notifications when Android asks. Open each account's Home screen once to register its device for notifications.
4. On the driver account, save Driving Information and Availability & Schedule. These screens now wait for the server before reporting success. Unsynced edits stay on the phone for retry.

The notification backend was pushed to the existing GitHub `main` branch in commit `99bd318`. The existing Vercel site serves `/api/notifications/relay`; an unauthenticated request correctly returns HTTP 401. It reuses the dashboard's server-side Firebase credentials. Do not put server keys in the Android app. No Blaze upgrade or SMS gateway was added.

## Changes included

- Customer driver-profile schedule now has separate, aligned day/time rows, an icon aligned with the heading, and a separate availability note. It shows saved hours or Off; missing/invalid hours do not invent availability.
- Driving-information saves update editable matching settings separately from the immutable registration record. Availability refresh errors no longer turn a successful information save into a failure.
- Pending schedule/settings edits are preserved, clean caches hydrate from the server, and customer driver details observe updates.
- Nearby recommendations use measured or geocoded saved coordinates, service coverage and saved availability; eligible drivers sort nearest first. Schedule boundaries refresh from the existing snapshot without extra periodic Firestore reads.
- Existing anonymous busy time slots exclude conflicting recommendations. Booking acceptance also rechecks existing slots in its transaction.
- Review submission uses the booking's authoritative driver ID, preserves one review per booking, and reports server errors rather than showing false success.
- Public profile thumbnails use corrected decoding and cached display; incoming conversation bubbles have the peer's circular avatar.
- Profile adjustment keeps drag, pinch and rotate, removes the hint/slider, and suppresses the foreground splash during photo selection.
- The existing first-run onboarding completion flag is respected.
- The specified green emergency-contact control is hidden; number saving and the phone SMS composer remain in place.
- Incoming unread messages drive the red navigation badge. Booking/message delivery uses an authenticated server relay with recipient checks and event deduplication. Notification taps open the corresponding booking/conversation.

## Verification and remaining device checks

Android debug build, unit tests and instrumentation-test compilation pass. Firestore emulator suites passed 21 matching, 44 customer-feature and 29 booking checks. Notification event tests and the dashboard production build passed. The combined `.rules` and `.txt` files are identical.

No phone is connected for this final build. Device rendering, Logcat, fresh-install onboarding, cross-device data hydration and end-to-end push delivery have **not** been verified on the latest APK. The live endpoint authentication check proves deployment, not FCM delivery.

After publishing and installing, use a real customer/driver booking to check messages in both directions, notification taps, unread badges, accepted-trip conflicts, completed-trip reviews, schedule alignment, and photo crop cancellation. Test background/closed-app alerts on two accounts. Android force-stop and notification permissions can prevent delivery; notification relay retries also require connectivity. Do not clear the existing app's data just to test onboarding—use a separate test device or clean test installation.

Emergency messages still open the phone's SMS composer; the customer taps Send. No paid SMS integration was enabled.
