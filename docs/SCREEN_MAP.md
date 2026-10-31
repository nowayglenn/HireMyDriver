# Layout and screen guide

New map screens: `activity_location_picker.xml` is shared by customer pickup/destination selection; `activity_driver_trip_map.xml` is the driver pickup/service map. Customer tracking keeps `activity_trip_in_progress.xml` and `activity_live_tracking.xml`. Their shared map controllers are in the `maps` Kotlin package; trip state/local storage is in `trip`. See [the map flow guide](MAP_BOOKING_FLOW.md).

All design XML files are in **app/src/main/res/layout**, visible in Android Studio under **app → res → layout**. The table below identifies the customer, driver or shared area without moving files out of the standard layout folder.

- Customer: login, home, bookings and customer profile.
- Driver: driver login, registration, bookings, profile and service dialogs.
- Shared: welcome, conversations, recovery and reusable settings/policies. Some settings currently have only a customer entry point.
- Shared sizes: `app/src/main/res/values/dimens.xml`.
- Icons, backgrounds and images: `app/src/main/res/drawable` (original files consolidated from custom resource roots for Android Studio Preview).
- Screen controllers: `app/src/main/java/com/example/hiremydriver`.

**Naming exception:** `activity_driver_details.xml` is a **customer** screen showing a driver before booking. `activity_driver_profile.xml` is the **driver's own** profile.

Keep layouts directly inside `res/layout`; do not create nested customer/driver folders there. Use the area column below to trace them.

## Main routes

- Customer: Main → Login → Home → DriverDetails → BookDriver → ConfirmBooking → BookingSuccess.
- Driver: Main → DriverLogin → DriverHome → DriverBookingRequests → DriverRequestDetails.
- Driver upcoming: DriverHome → DriverUpcomingBookings → DriverAcceptedBookingDetails → start/complete dialogs.
- Driver registration: DriverRegistration → PersonalInfo → LicenseInfo → ReviewInfo.
- Driver unread messages use DriverMessageActivity's unread tab. DriverMessageUnreadActivity is a compatibility redirect; its old XML is retained but unused.

## Layout to controller map

| Area | Layout | Controller / inflater |
|---|---|---|
| customer | [activity_ai_recommendation.xml](../app/src/main/res/layout/activity_ai_recommendation.xml) | AiRecommendationActivity.kt |
| customer | [activity_ai_results.xml](../app/src/main/res/layout/activity_ai_results.xml) | AiResultsActivity.kt |
| customer | [activity_book_driver.xml](../app/src/main/res/layout/activity_book_driver.xml) | BookDriverActivity.kt |
| customer | [activity_booking_details.xml](../app/src/main/res/layout/activity_booking_details.xml) | BookingDetailsActivity.kt |
| customer | [activity_booking_success.xml](../app/src/main/res/layout/activity_booking_success.xml) | BookingSuccessActivity.kt |
| customer | [activity_confirm_booking.xml](../app/src/main/res/layout/activity_confirm_booking.xml) | ConfirmBookingActivity.kt |
| customer | [activity_customer_booking.xml](../app/src/main/res/layout/activity_customer_booking.xml) | MyBookingActivity.kt |
| customer | [activity_customer_home.xml](../app/src/main/res/layout/activity_customer_home.xml) | HomeActivity.java |
| customer | [activity_customer_messages.xml](../app/src/main/res/layout/activity_customer_messages.xml) | MessagesActivity.kt |
| customer | [activity_customer_profile.xml](../app/src/main/res/layout/activity_customer_profile.xml) | ProfileActivity.kt |
| customer | [activity_customer_reviews.xml](../app/src/main/res/layout/activity_customer_reviews.xml) | CustomerReviewsActivity.kt |
| customer | [activity_customer_signup.xml](../app/src/main/res/layout/activity_customer_signup.xml) | CustomerSignUpActivity.kt |
| customer | [activity_delete_account.xml](../app/src/main/res/layout/activity_delete_account.xml) | DeleteAccountActivity.kt |
| customer | [activity_driver_details.xml](../app/src/main/res/layout/activity_driver_details.xml) | DriverDetailsActivity.kt |
| customer | [activity_edit_profile.xml](../app/src/main/res/layout/activity_edit_profile.xml) | EditProfileActivity.kt |
| customer | [activity_emergency_alert_confirm.xml](../app/src/main/res/layout/activity_emergency_alert_confirm.xml) | EmergencyAlertConfirmActivity.kt |
| customer | [activity_emergency_contact.xml](../app/src/main/res/layout/activity_emergency_contact.xml) | EmergencyContactActivity.kt |
| customer | [activity_filter.xml](../app/src/main/res/layout/activity_filter.xml) | FilterActivity.kt |
| customer | [activity_login.xml](../app/src/main/res/layout/activity_login.xml) | LoginActivity.kt |
| customer | [activity_popular_drivers.xml](../app/src/main/res/layout/activity_popular_drivers.xml) | PopularDriversActivity.kt |
| customer | [activity_rate_driver.xml](../app/src/main/res/layout/activity_rate_driver.xml) | RateDriverActivity.kt |
| customer | [activity_review_submitted.xml](../app/src/main/res/layout/activity_review_submitted.xml) | ReviewSubmittedActivity.kt |
| customer | [activity_trip_in_progress.xml](../app/src/main/res/layout/activity_trip_in_progress.xml) | TripInProgressActivity.kt |
| customer | [item_ai_driver.xml](../app/src/main/res/layout/item_ai_driver.xml) | activity_customer_home.xml |
| customer | [item_ai_result.xml](../app/src/main/res/layout/item_ai_result.xml) | activity_ai_results.xml |
| customer | [item_customer_review.xml](../app/src/main/res/layout/item_customer_review.xml) | activity_customer_reviews.xml |
| customer | [item_driver.xml](../app/src/main/res/layout/item_driver.xml) | HomeActivity.java, activity_customer_home.xml, activity_popular_drivers.xml |
| driver | [activity_driver_accepted_booking_details.xml](../app/src/main/res/layout/activity_driver_accepted_booking_details.xml) | DriverAcceptedBookingDetailsActivity.kt |
| driver | [activity_driver_availability_schedule.xml](../app/src/main/res/layout/activity_driver_availability_schedule.xml) | DriverAvailabilityScheduleActivity.kt |
| driver | [activity_driver_booking.xml](../app/src/main/res/layout/activity_driver_booking.xml) | DriverBookingActivity.kt, DriverBookingRequestsActivity.kt |
| driver | [activity_driver_booking_requests.xml](../app/src/main/res/layout/activity_driver_booking_requests.xml) | DriverBookingRequestsActivity.kt |
| driver | [activity_driver_driving_info.xml](../app/src/main/res/layout/activity_driver_driving_info.xml) | DriverDrivingInfoActivity.kt |
| driver | [activity_driver_edit_weekly_schedule.xml](../app/src/main/res/layout/activity_driver_edit_weekly_schedule.xml) | DriverEditWeeklyScheduleActivity.kt |
| driver | [activity_driver_forgot_password.xml](../app/src/main/res/layout/activity_driver_forgot_password.xml) | DriverForgotPasswordActivity.kt |
| driver | [activity_driver_home.xml](../app/src/main/res/layout/activity_driver_home.xml) | DriverHomeActivity.kt |
| driver | [activity_driver_login.xml](../app/src/main/res/layout/activity_driver_login.xml) | DriverLoginActivity.kt |
| driver | [activity_driver_message.xml](../app/src/main/res/layout/activity_driver_message.xml) | DriverMessageActivity.kt |
| driver | [activity_driver_message_unread.xml](../app/src/main/res/layout/activity_driver_message_unread.xml) | Retained layout; no current direct reference |
| driver | [activity_driver_profile.xml](../app/src/main/res/layout/activity_driver_profile.xml) | DriverProfileActivity.kt |
| driver | [activity_driver_registration.xml](../app/src/main/res/layout/activity_driver_registration.xml) | DriverRegistrationActivity.java |
| driver | [activity_driver_request_details.xml](../app/src/main/res/layout/activity_driver_request_details.xml) | DriverRequestDetailsActivity.kt |
| driver | [activity_driver_upcoming_bookings.xml](../app/src/main/res/layout/activity_driver_upcoming_bookings.xml) | DriverUpcomingBookingsActivity.kt |
| driver | [activity_driver_upcoming_schedule.xml](../app/src/main/res/layout/activity_driver_upcoming_schedule.xml) | DriverUpcomingScheduleActivity.kt |
| driver | [activity_license_info.xml](../app/src/main/res/layout/activity_license_info.xml) | LicenseInfoActivity.kt |
| driver | [activity_personal_info.xml](../app/src/main/res/layout/activity_personal_info.xml) | PersonalInfoActivity.kt |
| driver | [activity_review_info.xml](../app/src/main/res/layout/activity_review_info.xml) | ReviewInfoActivity.kt |
| driver | [dialog_complete_service.xml](../app/src/main/res/layout/dialog_complete_service.xml) | DriverAcceptedBookingDetailsActivity.kt |
| driver | [dialog_decline_booking.xml](../app/src/main/res/layout/dialog_decline_booking.xml) | DriverBookingActivity.kt, DriverBookingRequestsActivity.kt, DriverHomeActivity.kt, DriverRequestDetailsActivity.kt |
| driver | [dialog_start_service.xml](../app/src/main/res/layout/dialog_start_service.xml) | DriverAcceptedBookingDetailsActivity.kt |
| shared | [activity_about.xml](../app/src/main/res/layout/activity_about.xml) | AboutActivity.kt |
| shared | [activity_chat_conversation.xml](../app/src/main/res/layout/activity_chat_conversation.xml) | ChatConversationActivity.kt |
| shared | [activity_create_password.xml](../app/src/main/res/layout/activity_create_password.xml) | CreatePasswordActivity.kt |
| shared | [activity_forgot_password.xml](../app/src/main/res/layout/activity_forgot_password.xml) | ForgotPasswordActivity.java |
| shared | [activity_language_settings.xml](../app/src/main/res/layout/activity_language_settings.xml) | LanguageSettingsActivity.kt |
| shared | [activity_live_tracking.xml](../app/src/main/res/layout/activity_live_tracking.xml) | LiveTrackingActivity.kt |
| shared | [activity_location_settings.xml](../app/src/main/res/layout/activity_location_settings.xml) | LocationSettingsActivity.kt |
| shared | [activity_main.xml](../app/src/main/res/layout/activity_main.xml) | MainActivity.java |
| shared | [activity_notification_settings.xml](../app/src/main/res/layout/activity_notification_settings.xml) | NotificationSettingsActivity.kt |
| shared | [activity_notifications.xml](../app/src/main/res/layout/activity_notifications.xml) | NotificationsActivity.kt |
| shared | [activity_privacy_policy.xml](../app/src/main/res/layout/activity_privacy_policy.xml) | PrivacyPolicyActivity.kt |
| shared | [activity_settings.xml](../app/src/main/res/layout/activity_settings.xml) | SettingsActivity.kt |
| shared | [activity_terms_conditions.xml](../app/src/main/res/layout/activity_terms_conditions.xml) | TermsConditionsActivity.kt |
| shared | [item_notification.xml](../app/src/main/res/layout/item_notification.xml) | activity_notifications.xml |

Driver destination review: `activity_driver_destination_change.xml` → `maps/DriverDestinationChangeActivity.kt`, opened from the driver map's **Review destination request** action. This belongs to the driver area and uses the existing local trip state.

The approved Map-first design is scoped to `activity_location_picker.xml`, `activity_trip_in_progress.xml`, `activity_driver_trip_map.xml`, `activity_driver_destination_change.xml` and `activity_live_tracking.xml`. Shared styling lives in `res/values/map_flow_styles.xml`; map rendering remains in `maps/TripMapController.kt`. Other screen designs and trip state/storage are unchanged by this UI update.

Booking card data binding: `MyBookingActivity.kt` and `DriverBookingActivity.kt` use `trip/BookingCards.kt` and `trip/BookingGroups.kt`. Reusable copies of their original card templates are named `item_customer_booking_*` and `item_driver_booking_*` under `res/layout`. The full-screen XML files retain preview examples; runtime lists replace them with actual saved bookings and empty states.

Booking detail navigation: `trip/BookingNavigation.kt` selects the original customer, driver request, or driver accepted/ongoing/completed details activity. `trip/SavedBookingDetails.kt` fills those existing pages from the saved booking ID; `trip/BookingDetailPage.kt` contains the tested status-to-page mapping. Booking details are full pages, not a replacement text dialog.

Connected booking routes: `trip/BookingRoute.kt` supplies ordered Pickup/Destination captions; `trip/BookingRouteView.kt` and `item_booking_route_point.xml` render connected dots in Review booking and saved detail pages, including completed trips. Customer Home's navy status-bar override lives in `HomeActivity.java`; `BaseActivity` continues to own system-bar insets for every screen.


## Google Maps migration (2026-09-28)

The shared `maps/TripMapController.kt` now uses Google Maps Android SDK with Map/Satellite switching. Earlier MapLibre/OpenFreeMap notes describe the previous implementation. Existing activity layouts and local booking state are preserved. See [GOOGLE_MAPS_SETUP.md](GOOGLE_MAPS_SETUP.md) for API-key setup and device verification.
