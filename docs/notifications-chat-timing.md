# Notifications, persistent chat and trip timing

Updated October 18, 2026. This change reuses the existing Firebase project, booking documents, conversation/message collections and Vercel notification endpoint. It does not migrate or delete existing booking/chat history.

## Data behavior

- A confirmed booking transition optionally appends a server timestamp under `statusEvents.<phase>`. Rules allow only the newly confirmed phase's entry; previous entries cannot be replaced or removed. Old clients remain supported. The relay accepts a delayed phase only when the current phase or saved receipt confirms it.
- Device alerts use existing deterministic event IDs and delivery deduplication. Android notification permission, connectivity and background/force-stop restrictions still apply.
- Trip elapsed time uses existing `startedAt` and `completedAt` values, not screen lifetime. A completed trip with a missing completion timestamp displays duration unavailable rather than inventing an end time.
- New bookings reuse an existing active conversation for the same customer and driver. Legacy per-booking threads are presented together; their messages keep their original storage locations and per-recipient read cursors. Merged message IDs are namespaced where necessary to avoid collisions.
- Read acknowledgements apply only through rendered messages while the conversation is focused, resumed and at the bottom. Failed writes retry. Badge counts exclude the current user's sent messages.
- Bell badges observe saved eligible notification records. Notification rows are marked read when visible on the focused screen; Mark all as read includes eligible records beyond the first displayed page.

## Activation

1. Publish the complete `firebase/firestore-combined.txt`, replacing the editor contents rather than appending it. The first line must be `rules_version = '2';`.
2. Install the debug APK over the current app on both phones. Keep existing app data.
3. Allow Android notifications and open each signed-in Home screen to register the device token.

The notification backend was deployed through the existing GitHub/Vercel connection in commit `8e407a5`. No additional keys, collections, indexes or paid plan are required for this update.

## Verification

Passed: 210 Android unit tests, 120 Firestore emulator checks, Android debug build and instrumentation compilation, backend notification-event tests, backend TypeScript checking and Next production build. Vercel reported deployment success; the live endpoint returned 401 for an unauthenticated request.

Phone checks still needed:

1. Keep the customer app in the background. On the driver phone confirm Accepted → On the Way → Arrived → In Progress → Completed. Check one appropriate device alert per confirmed event and correct notification destinations.
2. Verify elapsed time starts only In Progress, survives reopening, and freezes on completion both in the map and ongoing Android notification.
3. Exchange messages, finish a booking, then book the same driver again. Open chat from either booking and from Messages; check the full chronological history and that a new message appears once.
4. Send an incoming message while the recipient is away. Check bold text/red unread badge; view the chat and confirm both clear, including on another device using that recipient account. Sending one's own message must not create an unread badge.
5. Verify Home bell dots for new booking/review/approval records; view records and mark all as read. Chat messages must remain outside the notification feed.
6. Briefly interrupt connectivity during chat loading/read acknowledgement, reconnect, and check that history remains and read state catches up.

Instrumentation tests were compiled, not executed on a connected device. A successful server deployment does not by itself verify FCM delivery to a phone.
