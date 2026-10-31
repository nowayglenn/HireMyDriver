# Booking workflow setup

The Android booking flow now uses `bookings/{bookingId}` in the existing
`hiremydriver-e88f7` Firestore database. It uses the customer's existing default
Firebase Auth session and the driver's existing named Firebase Auth session.
No additional Firebase project or API key is needed.

## Publish the booking rules

1. Open **Firebase Console → HireMyDriver → Firestore Database → Rules**.
2. Keep all the rules already there. Inside `match /databases/{database}/documents {`,
   add the entire contents of **booking-workflow.rules** before its closing brace.
   It is a new sibling of `match /users/{userId}` and `match /driverApplications/{driverId}`;
   do not paste it inside either of those blocks.
3. Click **Publish**. Do not replace customer, driver, admin or messaging rules.
4. Install/run the updated Android build on both phones.

The new block restricts reads to the booking's customer and assigned driver.
Only that driver can advance the status, and only in order. Account IDs and
booking information cannot be changed through a status update.

## Check the complete flow

Create a **new booking** as a customer, selecting a registered driver. The old
records stored only on a phone do not contain authenticated participant IDs;
they are preserved for viewing, but are not uploaded or assigned to accounts by guessing.

Open **Booking** on the assigned driver's account and accept the request.

| Driver action | Saved status on both accounts |
|---|---|
| Accept | Accepted |
| View Map | Accepted |
| Start Going to Pickup → Cancel | Accepted |
| Start Going to Pickup → Yes, I'm On My Way | Driver on the way |
| I've Arrived | Driver has arrived |
| Start Service | In Progress |

Keep the customer booking details open while testing; it should update without
refreshing. Close and reopen the app to verify the saved status. A failed or
offline transaction must leave the old status in place and show a retry error.

Pickup preview uses GPS only on the driver's phone. Confirmed departure enables
saved driver location updates. Permission denial keeps the saved pins visible;
a route from the current driver position needs location permission and a GPS fix.

Status synchronization runs while the relevant booking screens are open and
reconnects when reopened. This change does not add a server push-notification
service for force-closed apps.

## Local verification

- `gradlew.bat --no-configuration-cache :app:assembleDebug :app:testDebugUnitTest`
- `node firebase/prepare-booking-test.cjs`
- From `firebase`, with Java on PATH:
  `npx --yes firebase-tools emulators:exec --only firestore --project demo-hiremydriver-booking --config booking-emulator.json "node test-booking-rules.cjs"`

The emulator config is for local testing only. It intentionally does not contain
the project's unrelated rules and must never be deployed to the live project.
