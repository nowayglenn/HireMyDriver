# Customer and driver readiness audit

Checked: 8 October 2026 (Asia/Manila).

Scope: Android source review of registration, sign-in, customer/driver displays, bookings, recommendations, messages, notifications, and profile storage. This is not an end-to-end test of the deployed admin dashboard or cloud rules. No application behavior was changed during this audit.

**Status: not yet ready for real customer-to-driver use.** The existing screen designs can be reused, but completing driver registration alone will not connect the remaining local/demo features.

## Confirmed remaining work

| Area | Current behavior and source | Required connection |
| --- | --- | --- |
| Driver sign-in | `DriverLoginActivity.kt` opens Driver Home directly when Login is tapped, without verifying credentials. | Authenticate the driver; enforce account role and approval status. |
| Registration submission | `ReviewInfoActivity.kt` opens Driver Home with a pending-approval flag. `RegistrationStepActivity.kt` passes a local draft between screens; `DocumentPhotoStore.kt` stores captured files locally. | Create the driver account, upload the selected documents to protected storage, save the application, and expose it to the admin review workflow. Only show submission success after persistence succeeds. |
| Approval | `PendingApprovalDialog.kt` is a dismissible UI message. `DriverHomeActivity.kt` reads a one-time intent flag rather than a server approval record. | Persist pending/approved/rejected status. Only approved drivers may appear in customer results or accept work, with authorization enforced by the backend. |
| Customer driver list | `HomeActivity.java` builds named drivers, prices, ratings, and photos in `initDriverData()` and `setupStaticDrivers()`. | Load approved registered drivers by stable driver UID; show an empty state when none qualify. |
| Nearby recommendations | `recommendation/NearbyRecommendations.kt` calls `DemoDriverSource.drivers(...)`. Real pickup/location handling does not make the candidate drivers real. | Feed the existing ranking logic approved profiles, actual availability, location timestamps, and booking conflicts. |
| Driver dashboard | `DriverHomeActivity.kt` displays fixed upcoming customer/date/route, pending/completed counts, and rating. Some request actions only show a toast. | Derive cards and totals from the signed-in driver's records and confirmed booking changes. |
| Bookings and reviews | `trip/LocalTripStore.kt` and `trip/LocalReviewStore.kt` persist to device preferences. Their stores are not scoped by authenticated customer/driver UID. | Store bookings with customer UID and driver UID; enforce participant access and allowed state transitions; synchronize both phones. Replace or explicitly migrate local test data rather than treating it as real bookings. |
| Chat | `MessagesActivity.kt` has a fixed conversation; `ChatConversationActivity.kt` adds text/photo bubbles to the current view. | Persist conversations/messages and attachments, restrict them to participants, and handle delivery failures and unread counts. |
| Notifications | `NotificationsActivity.kt` and `DriverNotificationsActivity.kt` populate example notification lists. `trip/TripNotifications.kt` does implement a local trip timer notification. | Generate notifications from real booking/message/approval events and deliver them across devices. Preserve the functioning local trip timer. |
| Driver schedule/profile | `DriverScheduleStore.kt` and driver personal settings use local preferences. `CustomerProfilePhoto.kt` saves a customer-specific local image URI. | Store account-owned driver profile/schedule and uploaded image references; connect availability to discovery and booking eligibility. |
| Driver password recovery | `DriverForgotPasswordActivity.kt` displays "link sent" after email-format validation without sending a reset email. | Connect recovery to the authenticated driver account provider and show accurate results. |
| Emergency alert | `EmergencyAlertConfirmActivity.kt` only shows "Emergency Alert Sent Successfully!" and closes. | Implement the agreed delivery/recipient flow and report actual delivery status, or keep this action unavailable until connected. |

## Existing work to preserve

- Customer Firebase sign-up/sign-in, reset-password and reauthenticated password-change implementations are present in `CustomerAuth.kt`.
- The customer's first-name greeting and full profile name use the signed-in account.
- Registration form validation, review screens, photo capture, and OCR provide the frontend workflow. They do not yet submit a driver application to the admin backend.
- Existing map/routes behavior, trip screens, navigation fixes, and screen designs can be reused while their data sources are replaced.
- Some XML examples are editor-only `tools:text`, and some booking/profile views overwrite XML defaults at runtime. Do not remove all named XML strings indiscriminately; inspect what actually remains visible.

## Implementation order

1. Complete driver authentication, document upload, and durable registration submission.
2. Connect admin review and server-enforced approval/role checks.
3. Replace customer driver catalogs and driver profile/dashboard defaults with approved account data; provide loading, empty, and error states.
4. Connect bookings, availability, trip updates, and reviews across customer/driver accounts.
5. Connect chat, event notifications, password recovery, and emergency delivery.
6. Run the acceptance checks below before describing the app as ready for real use.

## Acceptance checks

- A new driver's correct profile and selected documents appear in the admin review queue.
- A pending/rejected driver cannot accept bookings or appear as an approved customer result.
- Approval makes the correct driver discoverable; rejecting/disabling removes eligibility.
- A customer booking reaches the selected driver's separate device. Accept/decline/start/complete changes reach the customer.
- Logging in as another account on the same phone never exposes the previous account's bookings, documents, reviews, or messages.
- No real records produces truthful empty states, not named demo people, made-up ratings, or sample requests.
- Messages and attachments survive reopening and arrive only for the correct participant.
- Failed/offline submissions do not show success or create duplicate registrations/bookings after retry.
- Driver password reset actually arrives and changes access; emergency actions do not claim delivery without sending.

No builds or device tests were run for this documentation-only audit. Earlier navigation tests do not establish readiness of these backend flows.

## Follow-up: messaging preparation

After this audit, the customer/driver message screens were connected to a shared, disabled-by-default text-messaging implementation. Static inbox conversations and fake sent/online labels were removed. See [Customer / driver messaging](CUSTOMER_DRIVER_MESSAGING.md) for the prepared Firestore adapter, rule/index candidates, tests, and activation steps. Driver authentication, trusted conversation provisioning, cloud deployment and two-device delivery verification remain prerequisites; attachments and push notifications remain follow-up work.
