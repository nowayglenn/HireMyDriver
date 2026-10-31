# Prepared customer / driver text messaging

Prepared 8 October 2026. **Live messaging remains disabled.** No production messages were sent and no cloud rules or indexes were deployed by this work.

## Implemented

- The customer and driver inboxes use the shared `messaging/ChatInbox.kt` binding and reusable `item_chat_conversation.xml` layout. Original headers, navigation, and chat bubble styles remain. Sample people/messages and false online/delivered labels have been removed from these screens.
- Driver Main/Unread filtering, unread conversation counts, and search operate on the loaded inbox.
- `ChatRepository` separates screens from storage. `FirebaseChatRepository` implements Firestore listeners, authenticated participant checks, text sends, atomic inbox-summary updates, and read cursors.
- `ChatSession` handles sending/sent/failed states, retry with the same message ID, snapshot deduplication, stale listener rejection, and account changes. A retained `ChatViewModel` keeps requests and the current draft across rotation. Signing out or changing account clears the prior account's state.
- Text is limited to 2,000 characters. A pending send blocks duplicate taps. The retry transaction checks for an existing matching message instead of creating another copy.
- `Sent` means the backend acknowledged the write. There is no claim of recipient delivery or online presence.
- Read cursors advance only through displayed server messages when the chat is focused and scrolled to the bottom. Listeners detach when the screen stops.
- The old attachment button is hidden. Photos previously only added a local bubble and falsely reported success; photo transfer is not implemented in this text-messaging preparation.

## Activation prerequisites

1. Connect real driver sign-in, registration, and approval. Customer and driver must have distinct authenticated UIDs in the intended Firebase project.
2. Have the trusted booking backend create each conversation after verifying the booking's participants and the driver's approval. The app never creates a conversation based on a supplied name or client-selected role.
3. Merge the messaging matches in `firebase/messaging/firestore.rules` with the existing reviewed user/profile rules. Do not replace all current project rules with this messaging-only file. Check that no broader permissive match bypasses participant restrictions.
4. Merge `firebase/messaging/firestore.indexes.json` with existing indexes and deploy them. Test the rules in the emulator before enabling the feature; the local rules file is a prepared candidate, not a deployed or emulator-certified policy.
5. Connect real booking responses to `Messaging.CONVERSATION_ID`. Existing demo/local booking buttons only pass names, so they deliberately cannot send. Review `SavedBookingDetails`, `BookingDetailsActivity`, `DriverAcceptedBookingDetailsActivity`, `DriverUpcomingBookingsActivity`, `TripInProgressActivity`, and `DriverTripMapActivity` when the booking backend is added.
6. Set `messaging_enabled` in `app/src/main/res/values/messaging.xml` to `true`, rebuild, and test two authenticated participants on separate devices.

## Firestore contract

The trusted backend provisions `conversations/{conversationId}` with these fields:

| Field | Type / meaning |
| --- | --- |
| `customerId`, `driverId` | Distinct Firebase Auth UIDs; immutable from the app |
| `customerName`, `driverName` | Display labels from the verified account records |
| `bookingId` | Stable real booking identifier; immutable from the app |
| `active` | Boolean controlled by the backend; false disables further sends |
| `updatedAt` | Server timestamp, initialized when creating the conversation |
| `lastMessageId`, `lastText`, `lastSenderId` | Empty strings initially; updated atomically with each new message |
| `readAt` | Empty map initially; later UID-to-timestamp read cursors |

Messages live at `conversations/{conversationId}/messages/{messageId}` and contain `senderId`, `text`, and server timestamp `createdAt`. Clients cannot edit/delete a message or change a conversation's participants, booking, names, or active status. Each new message and its inbox summary must commit together.

Open a prepared conversation using:

```kotlin
startActivity(Intent(this, ChatConversationActivity::class.java)
    .putExtra(Messaging.CONVERSATION_ID, conversationIdFromBackend))
```

Current bounds: latest 100 inbox conversations and latest 100 messages per conversation. Older-history pagination, photo storage/upload, push notifications, recipient-delivery receipts, presence, blocking/reporting, and retention policy are follow-up work. Offline reads can use Firestore cache; sends require a successful transaction and failures offer manual retry. Pending sends/drafts survive rotation in memory, not process death.

## Validation and later acceptance checks

- Final debug build and Android test APK build passed. All 8 messaging session tests and 3 customer navigation gate tests passed.
- Device screen tests were compiled but could not run: no Android device was connected at the final install/test attempt. That attempt did not install the new APK. Live Firebase delivery and the candidate security rules have not been exercised against production or an emulator.
- Unit regression tests: failure/retry IDs, duplicate taps, echo deduplication, account switching, stale callbacks after access revocation, stop/resume, visibility-based read cursors, disabled/closed chats, validation, and unread ownership.
- Device tests cover disabled customer/driver inboxes and unread tab, rotation, and blocked legacy name-only chat entry. These do not prove production Firestore delivery.
- Before activation, emulator tests must reject outsiders, spoofed sender IDs, participant/role changes, client-created conversations, editing messages, summary-only writes, writes to closed conversations, and another user's read cursor. Confirm authenticated inbox queries and atomic message/summary writes succeed.
- Then test customer-to-driver and driver-to-customer delivery, retries after network loss, account changes, rotation, unread updates, and revoked conversation access on separate devices.

Firebase API references: [transactions](https://firebase.google.com/docs/firestore/manage-data/transactions), [realtime listeners and listener removal](https://firebase.google.com/docs/firestore/query-data/listen), [snapshot metadata](https://firebase.google.com/docs/reference/kotlin/com/google/firebase/firestore/SnapshotMetadata).
