# Booking improvements — October 19, 2026

## Included

- Driver profiles display the saved registration address below the service areas. The Android public-profile publisher and the admin approval projection copy only this additional field; registration documents, phone, email and date of birth remain private. Existing drivers publish their address when their registration profile is next synchronized (for example, after signing in).
- The original 1–24 hour picker remains available before choosing a date or time. The date picker rejects days the selected driver has not enabled, and the complete duration must fit the selected working window before continuing. Confirmation and driver acceptance re-read the driver profile in a Firestore transaction and reject schedule overflow or conflicts with reserved bookings. Pending requests do not reserve time; competing requests cannot both be accepted through the app because acceptance reads and updates the same driver reservation document transactionally.
- Customers can cancel Requested, Accepted, On the Way and Arrived bookings with a required reason. In Progress and terminal bookings cannot be cancelled. Cancellation and reservation removal commit together; the reason remains in history.
- The existing customer trip timer shows remaining time, scheduled end and Request more time. Additional durations include 30 minutes, one hour, two hours and custom minutes, within the existing 24-hour total limit.
- Extension requests have a sequence and Pending/Approved/Declined status. Pending requests cannot be duplicated. Approval adds exactly the requested minutes and updates the reservation in the same transaction. The approved total is derived from the original hourly rate and approved minutes; the original fee is retained for the breakdown. Decline leaves time and amount unchanged.
- Both details screens listen for booking changes and show the same approved duration, fee breakdown and end time. Extensions count from the actual service start, preserving the existing service timer basis. Dates/times are Philippine time.
- Extension notifications use server-confirmed receipts and unique event IDs through the existing durable relay. Cancellation uses the existing Cancelled notification event.
- Earlier requested continuous offline skeletons and circular conversation-avatar inset are included in the APK.

## Deployment required before live use

1. Publish the complete `firebase/firestore-combined.txt` in Firebase Firestore Rules. This includes new booking transitions, extension receipts, reservation projections and the registered address field. Do not replace rules with the standalone booking fragment.
2. Deploy the scoped backend changes in `C:/WebProjects/hiremydriver-admin`: `src/lib/notifications/events.ts` and `src/app/api/driver-applications/route.ts`. The extension event regression test is `tests/notification-events.test.mjs`. Other pre-existing backend changes were not included in this work.
3. Install `app/build/outputs/apk/debug/app-debug.apk`. This release updates the busy-slot projection; use the updated app for both accounts.
4. Existing approved profiles without an address need their normal driver profile synchronization before the address appears. Newly approved profiles receive it from the updated approval backend.

No production rules, deployment or registered user records were modified during implementation. Schedule and overlap checks run in the Android transaction flow; the existing Firestore rules enforce participants, legal transitions and reservation consistency, but do not independently reimplement the complete schedule/overlap algorithm against arbitrary custom clients.

## Verification

Debug APK built; 230 Android unit tests pass, including pending-price preservation, repeated approval protection, 30-minute pricing, decline, cancellation eligibility, schedule bounds and extended overlap. Existing Firebase workflow, matching and customer-feature emulator checks pass; additional atomic reservation and extension-receipt checks are included. Notification event tests and backend TypeScript checking pass.

No Android phone was connected. Still verify on two phones: cancellation while the driver views details, extension request/accept/decline, background notification sound, reconnect after an offline request, timer continuity after reopening, large-font sheet scrolling, persistent offline skeletons and circular conversation photos. Google sign-in remains deferred as requested.

## Registered-address follow-up

The Registered Address heading and value now sit directly under About Me. The driver Home approval listener repairs missing or stale public addresses using a transaction that reads the authenticated driver's registration. This also works for an already signed-in driver.

The scoped `firebase/server/sync-registered-addresses.mjs` migration was run against the existing Firebase project after a read-only check: two approved profiles missing a public address were updated from their saved approved registrations. No other profile fields, registrations, rules or deployments changed. This supersedes the earlier note that no production records had been modified. The layout move requires the new APK; the copied address is already in the customer-readable profile.

## Booking actions and customer profile follow-up

- Restored the original 1–24 hour selection without requiring a date first. The date picker accepts only the selected driver's enabled weekdays; the complete booked duration still has to fit that day's hours. Final booking confirmation rechecks the latest profile transactionally. All seven weekday choices are covered by tests.
- Both driver Booking Details and the map use separate confirmation dialogs for I've Arrived and Start Service. Arrival does not start the service timer. Start Service requires confirmation that the customer is on board.
- Customer Information on driver request/accepted details opens a customer profile dialog with the photo, full name, email, phone and address. Private contact fields are stored under the booking and readable only by its participants; they are not added to the public customer photo/name document.
- Customer Edit Profile now labels the address Barangay, Municipality, Province and saves editable contact fields to the owner's private profile. Existing phone-local fields synchronize to booking participants when the customer opens their bookings or saves the profile. A driver cannot retrieve a phone-only address until that customer device synchronizes.
- Emergency Contact uses the shared modern choice dialog for Relationship.

Publish the latest complete firebase/firestore-combined.txt again for the new private profile and booking contact permissions. Install the updated APK for both roles and open the customer's bookings to synchronize existing saved details. This follow-up did not publish production rules or modify the notification backend. Device UI verification remains outstanding.

### 2026-10-19: Customer contact loading and notification routing

- Confirmed that production lacked both private customer settings and booking-contact rules. Prepared a minimal patch against the deployed rules; preserved all unrelated rules.
- Published and read back the two contact-rule additions. Backfilled 2 missing booking contact records from existing customer registrations. The remaining 8 bookings have no registered customer source; no contact details were fabricated. Customer email, phone, and address remain readable only by the booking participants.
- Rule snapshots: `firebase/deployed-profile-rules-before.rules` and `firebase/deployed-profile-rules-after.rules`; prior ruleset reference in `firebase/profile-rules-rollback.json`.
- Customer profile dialog now reserves scrollable content space before loading, retains the same dialog during retry, distinguishes unavailable permissions from connectivity errors, and guards duplicate taps and account changes.
- Notification opening requires the latest server booking and routes by its current phase. Initial Firestore disk snapshots no longer overwrite confirmed local status. Four regression tests cover completed/cancelled cache races, new server updates, and first offline load.
- Removed the driver Messages search icon and handler from the main and unread layouts.
- Validation: 239 Android unit tests passed; 54 Firestore permission checks passed against the exact minimal production patch; debug APK built. No phone connected for visual/device validation.

### Customer profile dialog follow-up

Removed the Try again button and loading copy. Customer contacts now preload with booking details, show prefetched values immediately, and observe Firebase's cached/live profile updates in the same fixed-size dialog. The dialog retains only Close. Empty first-load fields use a neutral dash until actual data arrives; missing server fields show Not provided. Prefetch subscriptions stop with the screen, private memory is scoped to the activity and signed-in driver, and permission errors clear displayed contact values. No additional Firebase deployment is required.
The customer dialog detail rows now use the existing profile, email, phone, and location icons in orange. Each row places its bold label beside its value instead of above it; long values wrap naturally, and the content remains scrollable on smaller screens or larger font settings.

Customer dialog visual cleanup: centered title and 80dp circular photo, consistent detail-row spacing and subtle dividers, hidden empty status area, and full-width orange Close button. Retained the existing cached/live loading behavior and scrollable fixed-size content. Debug build passed; device appearance has not been verified.

### Inline customer contact details

Removed the customer profile dialog and the Customer Information card's tap/ripple behavior. Both driver request and accepted-booking layouts now display a wrapping address row with a location icon directly below email. The existing lifecycle-scoped private booking-contact listener binds name, phone, email, and address directly to those cards, retaining cached values across booking rerenders. No new Firebase rules are needed.

### 2026-10-20: Google sign-in device diagnosis

Connected phone: Huawei FRL-L22, Android 10. Package inspection found no native com.google.android.gms or com.android.vending; Google-branded apps were GBox packages. The app uses Credential Manager with the Google Play services provider. The local debug certificate SHA-1 matches the Android OAuth entry in app/google-services.json; a web client ID is present. This does not verify sign-in end to end on a supported device.

GoogleCustomerLogin now checks Play services availability before invoking Google authentication and provides specific missing/disabled/update messages with email login as an alternative. Credential errors no longer universally blame the internet, and diagnostics log only the exception class (no token or account data). This improves unsupported-device handling; it does not add a browser-based OAuth flow or install Google services on the Huawei phone.

### Forgot Password visual alignment

Removed the decorative header illustrations from both Forgot Password layouts. Customer recovery now follows the driver screen's title size, spacing, input appearance, orange button with white text, and orange back-to-login link. Kept every activity-bound customer view ID, including the hidden account label. Only the two layout XML files changed; reset requests, validation, status handling, email prefill, and navigation logic remain unchanged.

### 2026-10-20: Extension request permissions and sheet

- Fixed an unintended driver-profile write during customer extension requests. Firestore numbers arrived as Long while model values were Int, making equal busy-slot projections compare unequal. BusySchedule now uses canonical Long values, and customer updates touch availability only when the reservation actually changes. Pending extensions leave booked time unchanged; driver approval still validates the schedule and reserves extra time atomically.
- Read the current production rules into `firebase/deployed-extension-rules.rules`. No Firebase permissions or production booking data changed. Emulator regression reproduces the rejected customer/profile write, then verifies booking-only requests with notification receipts, duplicate rejection, participant restrictions, approval, decline, and reservation consistency against those exact rules.
- Replaced the extension sheet with a rounded white layout, current-booking summary, orange selected time cards, custom-minute validation, proposed end/fee/total-duration summary, and an approval notice. The content scrolls independently of the Cancel/Send footer. Guards prevent duplicate sheets and submissions; errors retain the selection. Success copy waits for server acknowledgement and does not claim push delivery.
- Validation: all 243 Android unit tests passed; both matching and booking Firebase permission suites passed against the live-rules snapshot. Debug APK rebuilt. No phone or emulator was connected, so device appearance and end-to-end submission still need a device check.

### Map controls and booking reference layout

- The standalone LiveTracking header now receives status-bar/cutout spacing, while the map remains edge to edge. Bottom controls also reserve system-navigation space without accumulating margins on inset redispatch.
- Sheet clearance now moves both the driver/picker location button and the customer trip recenter button above the draggable sheet with a 16dp gap.
- Both driver booking details layouts place the Booking ID in a shared full-width rounded section below the date/status row. The actual complete booking identifier remains unchanged, wraps as needed, and can be selected/copied; no shortened or fabricated identifier is stored.
- Debug build/resource compilation checked. No connected phone available for visual verification.

### Booking ID display correction

Restored the Booking ID to its original location directly below the status, beside the calendar, in both driver details layouts. The badge now displays a deterministic compact reference such as `Booking ID: HMD-37894B8D` (first eight UUID hex characters), rather than the full UUID. This is presentation only, not a new globally unique database key: booking reads, updates, navigation, and relationships continue to use the original complete ID.

### Notification bell and booking chat opening

- Replaced the activity-root notification-dot overlay with a drawable in the bell's own overlay. Its bounds are local to the bell, so scrolling, clipping, header movement, and view visibility move the icon and dot together. Stop removes the listener and drawable; stale callbacks cannot update a replacement indicator.
- Booking chat preparation can use a confirmed, account-scoped booking to resolve the participant pair directly. This skips the sequential missing-thread read and repeated booking read on the booking-details path. Existing active inbox threads can open directly; merged pair history and server-authorized chat creation remain unchanged.
- Cached participant hints require the current authenticated account and selected role, complete distinct participant IDs, and a synced booking. Added regression checks for both roles, other accounts, missing/unsynced bookings, and malformed participants. Live Firestore authorization still determines access and message writes.
- No connected phone was detected for scroll animation or real network latency measurements.

### 2026-10-21: Extension approval on the map and notifications

- Inset the customer extension radio indicators inside their rounded option cards. Driver requests now appear in the map's draggable details card above the booked date, with an outlined Decline action and orange Accept extension action. Booking details link to this map review instead of duplicating approval controls.
- Driver consent for an ongoing extension may exceed regular weekly hours. Approval still requires a pending request, a started service, a maximum total duration of 24 hours, available reservation data, and no overlap with another booking during the added interval. Normal new-booking schedule validation remains in place. Reservation updates remain atomic with approval.
- Confirmed extension requests notify the assigned driver; confirmed approval or decline notifies the customer. Notifications have deterministic per-request event IDs, committed receipts, and sender/recipient validation. Tapping an extension notification opens the ongoing trip map after reloading and checking the booking; finished bookings open their current details.
- Published only the notification event implementation and its regression tests in backend commit `8d55694`. GitHub reports the Vercel deployment completed successfully. No Firebase rules or production booking data were changed by this release.
- Verification: debug APK built successfully, all 251 Android unit tests and 6 notification backend tests passed, and backend TypeScript checking passed. No phone was connected, so visual layout and actual push delivery/sound require device verification. No test notifications were sent to real users.

### 2026-10-21: Infinix location-picker ANR

- Connected Infinix X6835B on Android 13 reported three location-picker input timeouts. The app main-thread stacks point to `Marker.setRotation` and `Marker.setIcon` called directly from `PhoneHeading.onSensorChanged` through the map controller's compass callback. This identifies repeated sensor-driven Maps renderer work as the observed blocking path; it does not establish network speed as the cause.
- Compass callbacks now store the latest heading and schedule at most one delayed marker update (120 ms), with lifecycle cleanup. The controller reuses the current icon until the arrow/dot mode changes and skips rotation changes smaller than two degrees, including across north. GPS movement animation uses the same style guard. Map design, route requests, and location permissions remain unchanged.
- All 257 unit tests passed and the debug APK built successfully. Installed the update on the connected Infinix without clearing app data. During the device retest, the updated process opened LocationPickerActivity and returned to BookDriverActivity; the last ANR stayed at the old 17:55:52 incident, with no new ANR or recent fatal/map-authorization log. A subsequent 279-frame sample had a 10 ms median and 40 ms 95th percentile; occasional slow frames remain. This verifies the observed retest, not every device or network condition. No artwork changes were needed for this traced map freeze.

### 2026-10-21: Destination completion and map interaction fixes

- Reached-stop confirmations save each stop in order. Only the final accepted destination triggers completion, even with remaining booked time. Completion follows the reached-stop write as a second confirmed transaction, matching the existing rules. A failed completion remains retryable; success shows Booking completed / Continue and Return to bookings. New stops accepted before completion must also be reached.
- View trip details from the driver map hides the Complete service / Message customer footer. Cancelled request details hide Accept / Decline. Destination selection requires a pinned pickup first.
- Other cancellation reason Continue submits directly without another confirmation; success is shown only after the server acknowledges cancellation.
- The extension request sheet supports dragging and nested scrolling, with rounded top corners and square bottom corners. The timer entry has an arrow. The customer in-progress card connects pickup and every destination using the shared route timeline.
- The map picker retains its geographic selection across keyboard, sheet, and fullscreen viewport changes. GPS initializes the picker once; explicit recenter remains available. Existing compass throttling stays intact.
- Verification: debug build and 260 unit tests passed, including intermediate/final destinations, newly accepted stops, and empty routes. Installed the APK without clearing user data on the connected Huawei FRL-L22. The focused device map test could not initialize Google Maps; this phone has no detectable com.google.android.gms package. Keyboard/fullscreen visual verification remains pending on a Google Play services-capable phone. No live booking was modified and no Firebase rules were changed.
