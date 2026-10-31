# Google Maps setup for HireMyDriver

The app now uses Google's native Maps SDK for Android 20.0.0. Existing booking storage, GPS permissions, customer/driver actions, fixed center pin, and full-screen controls remain intact. At the user's request, old map-provider credits, coordinate readout, sample/local-sharing footnotes and duplicate location-status footers were removed. All other existing layout attributes are preserved. Route details retain the last-recorded location timestamp; this is not live cross-phone tracking. Map/Satellite controls are shared across all map screens. Satellite uses Google's Hybrid layer, which keeps road labels over satellite imagery. The selected layer is remembered on the device.

## Google Cloud

1. Open https://console.cloud.google.com/ and select HireMyDriver (`hiremydriver-e88f7`), using the same Google account as Firebase. If absent, check the signed-in account first.
2. Link an active billing account to the project. Review the signup and payment terms in Google Cloud yourself.
3. Search for **Maps SDK for Android**, open it, and click **Enable**.
4. Under **APIs & Services > Credentials**, create a separate API key for this Android app.
5. Edit the key's **Application restrictions**: choose **Android apps**, then add:
   - Package name: `com.example.hiremydriver`
   - Debug signing SHA-1: `4E:1E:B4:5D:70:95:98:A4:C8:B8:91:98:66:F8:49:F3:14:E8:55:19`
6. Under **API restrictions**, choose **Restrict key** and select **Maps SDK for Android**. Save.
7. In the project's `local.properties`, fill the existing blank entry:

```properties
MAPS_API_KEY=YOUR_API_KEY
```

Use the actual key with no surrounding quotes. Keep `sdk.dir` and all other existing entries. This file is already ignored by version control. Gradle can alternatively use the `MAPS_API_KEY` environment variable for CI. Do not paste the key into chat or commit it. Local storage avoids publishing it in source control; the Android key still ships in the APK, so package/SHA-1 and API restrictions remain necessary.

8. Sync and rebuild in Android Studio, then run on a phone or emulator with Google Play services and internet access. Setting the key requires a new build.

The SHA-1 above was read from this machine's debug signing report. A release/Play Store build needs its own app-signing fingerprint in the key restrictions.

## Cost scope

As checked on 2026-09-28, the basic native **Maps SDK** SKU has unlimited free usage. It still requires billing and an API key. This implementation supplies **no Map ID**, uses ordinary bitmap markers, and supports Map/Hybrid layer switching. It does not call Places, Geocoding, Navigation, Street View or Map Tiles APIs. The optional Routes integration described below has its own request billing. Map IDs use different billing. Do not add one merely to enable Satellite.

Sources: [Android setup](https://developers.google.com/maps/documentation/android-sdk/config), [billing requirements](https://developers.google.com/maps/documentation/android-sdk/usage-and-billing), [SKU details](https://developers.google.com/maps/billing-and-pricing/sku-details#maps-sdk), [current pricing](https://developers.google.com/maps/billing-and-pricing/pricing).

## Verify on the phone after setup

- Open customer pickup and destination pickers; drag the map and confirm that the fixed pin matches the saved location, including full-screen mode.
- Switch Map/Satellite, leave and reopen the map, and rotate the phone. Confirm pins and the selected layer remain correct.
- Try Use my location with permission granted, denied and GPS disabled.
- Review the pickup/destination pins on customer trip, driver pickup/trip, destination-change and full-screen tracking screens.
- Confirm marker bounds are visible below status cards, and Google's logo/attribution stays visible above the rounded sheet.
- Existing local booking/service actions should behave as before. Google Maps does not itself add cross-phone live tracking.

Without a configured key the map shows a setup message and the pin cannot be confirmed. A build passing does not establish that the Cloud key/billing/restrictions work; that requires an actual map load on a device.


## Road-route integration (2026-09-28)

### Location, compass and map controls

The driver's active pickup/trip screens follow foreground GPS. Customer trip/full-screen maps and location pickers display the customer's own position without saving it as the driver's position. The blue dot has a translucent directional beam driven by the phone's rotation-vector compass (or accelerometer/magnetometer fallback), corrected to true north and smoothed across north. It responds when turning the phone in place. If a reliable compass is unavailable, movement bearing is used; without either, only the dot is shown. Sensors and GPS stop in the background. Driver publishing also stops when service completes.

New pickup selection can initially follow current location. Existing/destination pins keep their position until the customer taps the location control. Dragging or rotating the map pauses camera following while the dot keeps updating; the location button resumes following. GPS fixes older than 15 seconds, out of order, invalid, or with accuracy worse than 75 metres are ignored. Foreground updates request a 1-second/1-metre interval; actual frequency depends on the device and reception. Compass events rotate only the beam, avoiding constant camera rotation while standing still.

A round layers button opens a rounded Map type chooser with exactly two illustrated choices: Map and Satellite (with labels). The selected layer is saved. Curved detail cards in pickup/destination selection and customer/driver trip maps now use Material bottom sheets with collapsed, half and expanded positions. Their content remains scrollable. Map viewport height tracks the visible card edge, preserving center-pin alignment, attribution and controls. The picker full-screen button hides/restores the card; focusing its place-name field expands it for keyboard use.

Validation: unit tests cover stale/inaccurate fixes, stationary GPS jitter, displacement heading, and shortest rotation across north. Build and lint checks are run after implementation. Real sensor/movement and sheet interaction still need device verification: allow precise location, rotate the phone in place, walk outdoors, drag/rotate the map, tap location, switch layers, drag each card, open the keyboard, and check full-screen and landscape. No phone/emulator was connected during these changes. Other screen layouts and booking/backend data formats are unchanged. GPS updates do not issue additional Routes calls or add voice navigation/cross-device sharing.

A separate `ROUTES_API_KEY` in `local.properties` (or the environment) is now wired into the app manifest. Keep the existing `MAPS_API_KEY`. Restrict the new key to Routes API and the same Android package/signing certificate. The HTTP client supplies the actual running package name and signing-certificate SHA-1, without colons. Keys and raw API error bodies are never logged.

Credential checks performed: a basic public Sikatuna-to-Tagbilaran request returned HTTP 200 with route geometry; a request using an incorrect package/certificate returned HTTP 403. Neither check used personal GPS coordinates.

Customer trip, driver trip and full-screen maps display Google's computed driving route through the pickup and confirmed destinations in the customer's order. The blue road line has a white border for visibility over both standard and satellite maps. Pending destinations are excluded until accepted. Completed bookings retain their full planned itinerary. Unknown/unpinned or declined bookings do not generate fabricated lines.

This is a planned driving-route display, not voice navigation or automatic off-route rerouting. Driver-position markers remain separate. The app does not change the confirmed route when a GPS marker moves or the service timer refreshes. Both roles on this installation reuse the same route result. Real cross-phone booking/location sharing still requires the Firebase/authenticated backend integration; it is not provided by enabling Routes API.

Routes use DRIVE, TRAFFIC_UNAWARE, no alternative routes, high-quality encoded polylines and a polyline-only response field mask. Large itineraries are split into overlapping ordered batches with at most 10 intermediate waypoints each to stay within Essentials features. Each batch is a separate API request. Identical itineraries are coalesced while in flight and reused in memory for 30 minutes across screens, with a maximum of 20 cached entries. Route geometry is not persisted to disk. A process restart or expired cache can require another request.

If routing fails, pins remain visible without a fake straight-line fallback. The recenter control retries the route; the driver location control also recenters/retries. Map/Satellite switching and panning do not make route requests. Cost limits still need to be configured in Google Cloud; no cloud quotas were changed by this code.

Relevant code: `maps/RoadRoute.kt` (itinerary, batches, decoder), `maps/GoogleRoadRoutes.kt` (restricted HTTP client and in-memory reuse), `maps/TripMapController.kt` (polyline rendering).

Sources: [Routes usage and billing](https://developers.google.com/maps/documentation/routes/usage-and-billing), [Google mobile web-service security guidance](https://developers.google.com/maps/api-security-best-practices#secure-client-side-web-service-calls).


## Trip timer notification

The floating timer/status card was removed from customer and driver trip maps. Starting service posts one high-importance trip notification (subject to the phone's notification settings). Android 13+ asks for notification permission when starting the service. Denial does not block the booking and does not leave a floating map banner.

The ongoing notification uses Android's elapsed chronometer anchored to the persisted `startedAt`, with the booked duration alongside it. Android renders the clock outside the app; no foreground service, wake lock, exact alarm, or background GPS permission is needed for this timer. It keeps counting beyond the booked duration until service is explicitly completed, matching the existing manual completion flow. Completion through either booking details or the map cancels the notification. GPS/map refreshes do not repost or alert it. Tapping opens the matching trip. Returning to a trip restores its notification quietly using the original start time.

System notification permission, channel settings and Do Not Disturb control whether the start alert appears as a pop-up. This local notification does not add cross-phone delivery, background GPS, or reboot recovery. Device testing remains needed for the start alert, permission denial, Home-screen chronometer, tap-back, and both completion paths.

## Customer completed-booking map history

Open My Bookings > Completed > View Details > Trip Details > View Map. Saved completed bookings show a rounded white Trip Details card with connected orange route dots on the left and a compact pale-blue View Map tile on the right, using the existing location icon. Every confirmed destination gets its own dot and Destination 1, Destination 2, etc. caption. Other booking states, booking-list cards, Book Again and Rate Driver actions remain unchanged.

The existing full-screen map displays the completed booking's saved pickup and all confirmed destinations, including visited stops and accepted added destinations. Route details show the completion date/time when recorded. Map/Satellite selection, pan, zoom and route fitting remain available. History does not request current GPS, show the last driver-position marker as a live location, publish location, or restart the service timer. Missing saved coordinates are stated in the details, without inventing pins.

This is saved itinerary history, not a recorded GPS trail. The road line is recomputed using Routes API when it is no longer cached, so it can differ from the historical road path and still requires internet and a supported map device. No storage format or backend change was made. Device checks: open a completed multi-stop booking, verify every confirmed stop, toggle details and layers, rotate/reopen, and verify Book Again/Rate Driver remain available. Huawei without Google Play services still awaits the separate Huawei Map Kit integration.
