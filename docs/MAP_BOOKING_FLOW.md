# Customer and driver map flow

This pass adds native MapLibre maps and local booking state. It keeps the original navy/orange theme and the booking form's vehicle type, additional notes, duration and calculated driver fee. No distance, ETA, geocoding, route calculation or paid map API is used.

## Try it on one device

1. Customer: open Book Driver, select a future date/time and duration. Type a pickup name and use **Choose pickup on map**. Swipe the map beneath the fixed center pin to choose the location, enter a landmark and confirm. Repeat for the first destination. Optional additional destinations can be pinned before booking; tap their list to remove one.
2. Keep the vehicle, notes and fee fields as usual. Confirm the booking. Open **My Bookings → Pending → View Details → View Map**.
3. Switch to the driver flow on the **same installation**. In driver **Booking → Requests**, open the original Request Details page and accept that booking. It moves to **Upcoming**; its card opens the original Booking Details page. Choose **View Map** or **Open pickup map**. Use **Use my location** if desired. Permission is optional; manual pins still work when location is denied.
4. Confirm **I've arrived**, then **Start service** and confirm that the customer is aboard. Only this starts the booked duration. The timestamp survives screen changes and process recreation.
5. Customer: return to the saved trip and choose **Add destination**. The driver reviews and accepts/declines the request. Pending requests do not change the confirmed destination list or restart the timer.
6. Driver: **Reached this destination** advances the visited-stop list. Reaching the last stop or using up the booked hours does not automatically complete the service. Open **View trip details → Complete service** on the original details page; completing service still requires confirmation.

Use internet for map tiles. A saved map pin is a coordinate, not an automatically resolved street address. Editing an address field after pinning clears that pin so the customer confirms the updated location. GPS uses the foreground screen only and stops when it is paused. The picker uses one location fix; manual adjustments must not be overwritten by subsequent GPS updates.

## Code ownership

- `app/src/main/java/com/example/hiremydriver/maps/LocationPickerActivity.kt`: shared pickup/destination picker.
- `app/src/main/java/com/example/hiremydriver/maps/DriverTripMapActivity.kt`: driver pickup, acceptance, arrival, service and destination confirmation.
- `app/src/main/java/com/example/hiremydriver/TripInProgressActivity.kt`: customer trip map, Message, added-destination requests and existing emergency entry.
- `app/src/main/java/com/example/hiremydriver/LiveTrackingActivity.kt`: full-screen map for customer and driver. Route details can be toggled; only driver mode offers foreground GPS recording.
- `app/src/main/java/com/example/hiremydriver/maps/TripMapController.kt`: shared map lifecycle, attribution, markers and map gestures.
- `app/src/main/java/com/example/hiremydriver/maps/MapScreenActivity.kt`: common foreground location permission and lifecycle handling.
- `app/src/main/java/com/example/hiremydriver/trip/TripSession.kt`: trip states and timer rules; covered by unit tests.
- `app/src/main/java/com/example/hiremydriver/trip/LocalTripStore.kt`: booking-ID-scoped local storage.
- `app/src/main/java/com/example/hiremydriver/trip/TripUi.kt`: common labels, pins and locally saved booking cards.
- All XML designs remain in `app/src/main/res/layout`. Existing layout files were not moved.

## Explicit integration boundary

The user selected **map and booking flow first**, without Android Firebase configuration. These records are only saved in this installation. They are shared between the two local demo roles to let the class project flow be tested. They are not authenticated accounts, server-enforced roles, delivered requests or cross-phone tracking. The customer map shows only an explicitly captured driver location and labels it as last recorded on this device; no fabricated moving driver is shown. Existing sample bookings without coordinates are labelled as unpinned instead of inventing pickup locations.

Existing messaging and emergency modules are preserved; this change does not send messages or emergency alerts. Firebase login, authorized booking access, foreground/background location sharing between real devices, notification delivery and secure database rules are a separate integration step. Do not present the local prototype as a production safety service.

## Map hosting

MapLibre Native 13.6.1 renders the [OpenFreeMap Liberty vector style](https://openfreemap.org/quick_start/) at `https://tiles.openfreemap.org/styles/liberty`. This replaces the older OSM standard raster layer. Labels and road geometry remain sharp when zoomed. OpenFreeMap supports MapLibre Native, needs no account/API key for its public instance, and hosts OSM-derived data. The map keeps visible linked attribution for OpenFreeMap, OpenMapTiles and OpenStreetMap. Internet and provider availability are still needed; this is not Google Maps.

The picker keeps a fixed pin whose tip matches the camera center. Confirmation reads the current camera coordinate, not an earlier gesture result. Saved labels/pins survive rotation; manual movement cancels a pending GPS selection. **Full screen** hides the form; **Use this pin** restores it. Both customer **Expand map** and driver **Full-screen map** open an adaptive full-screen map with optional route details. Rounded white review cards show the route first, followed by date/time/duration/vehicle/notes, driver and fee.

## Device checks still required

Validation on 25 September 2026: `:app:assembleDebug`, `:app:testDebugUnitTest` and `:app:lintDebug` passed. All 11 unit tests passed, including five trip-state tests for pickup/start gating, pending/accepted/declined destinations, unchanged timers, visited stops and completion. Lint has no errors; existing and new non-blocking warnings remain. A structural comparison against the pre-change XML confirmed that the vehicle selector, notes input, fee amount and fee calculation controls were unchanged.

No emulator or phone was connected during implementation. Verify map rendering online/offline, GPS denied/approximate/precise, Location disabled, rotation and activity recreation, long labels, 320–360dp width and large text. Check that pins are not obscured by system bars or the attribution, and that service state survives switching between customer and driver on the same installation.

## Map UI update — 26 September 2026

The final `assembleDebug`, `testDebugUnitTest`, and `lintDebug` checks passed (11 tests; no lint errors). Non-blocking lint warnings remain. The provider style, vector source and a Bohol-area vector tile returned successfully. Android rendering and gestures still require a phone/emulator check; neither was connected.

The 141 original drawable resources were moved byte-for-byte into the standard `res/drawable` folder and their custom Gradle resource roots removed to fix Android Studio Preview indexing. Every original resource hash matches the backup. Home and unrelated module source/layout files are unchanged. The booking vehicle selector, notes input, fee amount and calculation are structurally identical to the pre-update version. Changes to layouts/controllers are restricted to booking/map screens. The obsolete raster style asset was removed.

On a device, test: swipe/zoom the picker, switch full screen and back, confirm a named pin, edit it, rotate, deny GPS, and manually move while GPS is pending. Check Review booking with long place names and multiple stops. Open the full-screen map from both roles; toggle route details. Verify the foreground driver's GPS is never used in customer mode. Cross-phone sharing remains unconnected.

## Driver screen reference update — 26 September 2026

Driver pickup and in-progress screens now use the provided reference hierarchy: navy header with driver badge, map and floating status card, then a white details panel with customer initials, Message, notes, schedule, duration and rounded actions. No distance/ETA or calling controls were added. Full-screen map remains available. In-progress **View trip details** shows the existing route, vehicle, notes and fee, with the existing explicit Complete service confirmation.

**Review destination request** now opens `maps/DriverDestinationChangeActivity.kt` using `res/layout/activity_driver_destination_change.xml`: requested stop, actual map pin, remaining/booked time and accept/decline controls. It reuses `TripSession.resolveStop`, keeps the service timer, and rechecks the current pending request before resolving it. A resolved/ended request disables the action buttons. Back leaves the request pending.

Scope verification: the driver map layout/controller, manifest and an optional driver-only map inset in the shared renderer were modified; the new destination screen and four scoped background drawables were added. All other source/resources, trip state and storage are unchanged from the pre-update backup. The map inset keeps fitted pins below the status card; other map screens retain their existing camera padding. Device checks: accepted → arrived → customer aboard → started; full-screen map; trip details/completion; request accept/decline/back; request becoming stale; rotation, long text and small displays.

Validation for this driver reference update: debug build and lint passed after the final map inset adjustment. All 11 existing unit tests passed. Lint reports no errors (non-blocking warnings remain). No phone or emulator was connected, so visual/gesture verification on a device remains required.

## Approved Map-first UI — 27 September 2026

The shared pickup/destination picker, customer trip, driver trip, driver destination review and full-screen map now follow the approved Map-first preview: floating white controls, rounded white details sheets, compact Message/Emergency actions, and orange primary buttons. Map-area height accommodates enlarged status text; details scroll on smaller screens. The picker keeps its fixed center pin, landmark field, GPS selection and full-screen toggle. Actual booking data populates the screen; preview sample data was not imported.

These screens use `res/values/map_flow_styles.xml` and `bg_map_flow_*` backgrounds. Existing navigation, location, message, shield and profile icons are reused; only expand/collapse icons were added. Styles are scoped to these layouts. Original resources, other modules, the booking form, manifest, permissions, global theme, trip state and storage match the pre-change backup. Google Maps and cross-phone sharing remain separate future work.

Validation: debug build and lint passed after final XML adjustments; lint has no errors (non-blocking warnings remain). All 11 existing unit tests passed. A source/resource comparison verified the stated scope, preserved original icons, and checked layout IDs and controller view types.

No phone or emulator was connected. Check the native appearance, long location names, large system text, map dragging/full-screen transitions and keyboard behavior on a device before release.

## Booking card integration — 27 September 2026

Customer My Bookings and driver Booking now bind saved records to copies of the original XML card templates in `item_customer_booking_*` and `item_driver_booking_*`. `BookingCards.kt` binds fields, details and booking-ID-specific actions; `BookingGroups.kt` maps states to the original tabs. The separate local-record cards and runtime sample bookings are replaced. The original screen XML remains available for Design Preview. Header/navigation layouts, map layouts, button dimensions, colors and original drawable files are unchanged.

Requested bookings appear under Pending/Requests. Accepted and arrived bookings appear under customer Accepted and driver Upcoming. Only a started service appears in the driver's Ongoing card. Completed records appear under Completed. Counts, empty states and the selected filter update on return; multiple bookings are rendered independently without an extra duplicate list. Card details show the actual route, schedule, vehicle, notes and fee and link to the existing map flow.

The original driver Decline dialog now requires a reason and persists a guarded REQUESTED → DECLINED transition. Declined requests appear in customer Cancelled with a Declined badge and reason; they cannot be accepted or started later. A stale decline cannot cancel an already accepted booking. The existing serializer stores this enum and reason without deleting existing records or migrating storage. These records still live on one installation; this change does not add delivery or cross-phone synchronization.

Validation: debug build, all 15 unit tests and lint passed (no lint errors; warnings remain). Four new tests cover template categories, independent bookings, terminal decline and stale decline guards. Structural checks confirmed all copied button dimensions/styles match the originals and all unrelated original source/resources match the backup. Native visual/interaction checks still require a connected phone or emulator.

## Original full-page booking details restored

Card taps and View Details now use `BookingNavigation.kt` to open the original customer `BookingDetailsActivity`, driver `DriverRequestDetailsActivity`, or `DriverAcceptedBookingDetailsActivity`, according to the current stored phase. The replacement booking-details AlertDialog was removed. Driver map View trip details also opens the original full page; its primary action completes active service only after confirmation.

`SavedBookingDetails.kt` binds saved records to these layouts and refreshes on resume. Map, message and request actions keep the exact booking ID. The original layout structure, buttons, spacing and icons are preserved; matching vehicle/notes rows were added where absent. Unknown contact information is labelled as not provided instead of showing sample information. Payment method is Cash for every booking, as requested. Rebook starts a new form without passing the previous booking ID. Legacy sample entry points retain their existing behavior.

Validation: debug build, all 17 unit tests and lint passed with no lint errors. Two new tests cover detail-page selection for every phase and role. A structural comparison confirms the original three detail layouts are unchanged apart from the matching vehicle/notes rows. No native phone/emulator visual check was performed.


## Rounded booking confirmations and Cash payment

Booking acceptance, pickup arrival, service start/completion, reached-stop confirmation and added-destination requests share `trip/BookingDialogs.kt` and `res/layout/dialog_booking_confirmation.xml`. The dialog uses a white 24dp rounded panel, regular body text, reused icons and full-width 50dp orange/outlined buttons. Its width is capped on tablets and scrollable content accommodates small screens/large text. Starting service still requires the customer-on-board checkbox; dismissing a dialog does not run its action. Existing trip transition and timer rules are unchanged.

Customer and driver detail binders now display **Cash** instead of **Not specified** for payment method, including existing saved bookings. This sets the method label; it does not mark a booking as paid. Page layouts, other modules, state and storage remain unchanged.

Validation: debug build, all 17 existing tests and lint passed (no lint errors; warnings remain). Scope comparison confirmed the original page layouts, themes, icons, trip model and storage are unchanged. Native appearance still needs a phone/emulator check.

## Booking selectors and review route rows

Duration and Vehicle Type use the shared rounded `BookingDialogs.choose` panel. The selected value is highlighted; the options scroll inside a bounded list and Cancel leaves the form unchanged. All 24 durations and the original five vehicle types remain available. Tapping a duration runs the original fee recalculation immediately.

Review booking renders each additional destination with `item_review_destination.xml`: the same orange dot, location-name styling, spacing and secondary caption as the first destination. Extra captions start at Stop 2 and retain destination order. Pickup, first destination, other review sections and the Confirm button are unchanged; no additional-stop container is shown when none exist.

## Connected route timeline and Customer Home status bar

The review route and saved customer/driver detail routes now use `BookingRouteView` and `item_booking_route_point.xml`. Every place has a separate dot and caption: Pickup, Destination 1, Destination 2, and so on. The line connects each dot to the next and grows with wrapped text; it stops at the last dot. Place names do not have numerical prefixes. The saved route includes all confirmed destinations after service completion, including visited stops; pending/unaccepted destination changes are not included. The driver View Map box and original action buttons retain their sizing and styles.

Customer Home supplies a navy status-bar color through `BaseActivity.statusBarBackgroundColor()`. This fixes the white bar caused by deriving a color from the white root behind an image header. Existing safe-area padding, home layout and artwork remain unchanged; status icons use the light appearance against navy. Other screens keep their previous status-bar behavior.

Validation: final debug build, all 20 tests and lint passed (no lint errors; warnings remain). New route tests cover captions/order, completed history, unconfirmed stops and legacy single-destination records. Structural checks confirm original button sizing/styles, Home XML/artwork, pricing and state/storage remain unchanged. Visual verification on a phone/emulator is still required.


## Google Maps migration (2026-09-28)

The shared `maps/TripMapController.kt` now uses Google Maps Android SDK with Map/Satellite switching. Earlier MapLibre/OpenFreeMap notes describe the previous implementation. Existing activity layouts and local booking state are preserved. See [GOOGLE_MAPS_SETUP.md](GOOGLE_MAPS_SETUP.md) for API-key setup and device verification.
