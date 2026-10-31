# UI and navigation audit — 25 September 2026

## Later map-flow update

The map/booking pass now adds MapLibre networking and booking-ID-scoped local persistence for new customer bookings, driver pickup/service steps and destination requests. See [MAP_BOOKING_FLOW.md](MAP_BOOKING_FLOW.md) for ownership, a same-device walkthrough and the boundary before Firebase is configured. Statements below about sample tracking and the absence of network dependencies describe the earlier UI-only pass. Existing sample modules are still not a real backend.

## Fixed in this pass

- Customer Forgot password now opens the email recovery form instead of skipping directly to Create password.
- Driver registration's Login link returns to DriverLoginActivity, not customer login.
- Driver login and registration completion clear the authentication/onboarding stack.
- The legacy unread message route forwards to the working unread tab; it no longer accesses an uninitialized button.
- Switching between driver Booking, Messages and Profile no longer repeatedly piles those screens on the back stack.
- Customer reviews' Profile navigation now works.
- Review Information now uses BaseActivity's system-bar/keyboard spacing and its header back arrow works.
- Chat accepts the existing customer/driver image extras, so opening a driver conversation does not show an unrelated default photo.
- Driver booking details no longer route View Customer Review into the customer's account/review navigation. The sample currently has no review to display.
- Completed Book Again and cancelled Rebook Driver open the booking form; Back to Home opens the home screen.
- Track Trip carries the displayed pickup/destination instead of losing them.
- Driver trip progress and selected booking/message tabs survive activity recreation.
- Saving the current language without changing the radio selection no longer silently resets it to English.
- Driver vehicle checkboxes and their cards share selection state; All reflects individual vehicle selections. The service-area chooser starts from the currently displayed areas.
- Customer booking requires a future date/time instead of silently submitting the old sample date. Duration, selected date/time and vehicle survive rotation. Decimal hourly rates no longer lose the decimal point.

## Layout changes

- All 63 layouts remain in the standard `app/src/main/res/layout` folder. Customer, driver and shared ownership is documented in the screen map. The earlier separate-resource-folder move was reverted to keep files visible in the familiar Android Studio view; UI fixes and existing IDs were retained.
- 75 MaterialButtons use the same 48dp minimum height, 16sp text and padding. Adjacent action buttons have equal heights; long labels can grow rather than clip.
- Two new headers now use the existing shared header metrics and status-bar handling.
- Text-bearing rows and inputs can grow. Label/value rows share width so a long address cannot push its label off-screen.
- Weekly schedule controls wrap onto another line when the available width is too small.
- Service/decline dialogs scroll on short screens and larger font settings.
- Compared against the pre-change XML: all original static labels, colors, backgrounds, images, corner radii and existing view IDs were preserved.

## Validation

- `:app:assembleDebug`, `:app:testDebugUnitTest`, `:app:lintDebug` passed.
- Layout XML parsing and resource mapping checked for all 63 layouts.
- New unit tests cover decimal/thousands price parsing and missing/past/future booking dates. Existing inset tests also pass.
- No phone/emulator was connected, so actual screen rendering, touch behavior and rotation still need device verification. Lint still reports existing warnings; successful lint does not establish that every layout fits every device.

## Existing demo behavior that is not a backend integration

This Android module currently has no Firebase/network authentication or booking repository in its dependencies. These items remain outside this UI-only pass:

- Login/social login navigate to sample screens; they do not authenticate a real account.
- Customer recovery reports unavailable. Driver recovery/Create Password contain demo confirmation behavior; no reset email is sent and no real password is changed.
- Booking requests, upcoming/completed cards, chats, tracking and reviews are sample data. Accept/decline/complete actions do not sync a real booking across customer/driver accounts, and navigating back to a fresh sample list can restore its default cards.
- Profile, driving-information and weekly-schedule Save actions are placeholders rather than server persistence. Driver profile Personal Information/Documents reuse onboarding forms.
- Driver home includes repeated sample request cards whose details/counts need one shared data source when real bookings are connected.
- Registration review shows sample content; submission does not establish verified-driver authorization.
- Several support, emergency, deletion and notification actions are placeholders. They must be connected before being presented as completed real operations.

## Phone verification checklist

Test a narrow phone (around 320–360dp width), a larger phone, and landscape. Repeat at default font size and larger accessibility font sizes.

1. Open customer and driver login/registration; verify the correct Login and Forgot password destinations.
2. Cycle through Home, Booking, Messages and Profile repeatedly; check Back behavior.
3. Open new driver Driving Information, accepted booking details, and start/complete/decline dialogs. Check buttons, long labels, status bar and bottom navigation.
4. Rotate the booking form after changing date/time/duration/vehicle; verify the same values and price. Try a missing date or past time.
5. Rotate driver trip details after Start Service; verify Complete Service remains available.
6. Open weekly schedule and scroll dialogs with larger font sizes; ensure every control is reachable.
7. Open conversations from each role and confirm name and photo match the selected card.

Use [SCREEN_MAP.md](SCREEN_MAP.md) to locate each layout and its controller.
