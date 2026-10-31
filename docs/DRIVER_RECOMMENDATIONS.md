# Driver recommendations

Home Recommended for You, its See all screen, and Find My Best Driver share the local `recommendation/DriverRecommendationEngine.kt`. They use explicitly labelled fictional demo drivers, not registered accounts or live availability. Existing card styling is retained, with kilometres replacing fixed match percentages. Home shows the nearest eligible driver; See all opens the original card screen using BROWSE_RECOMMENDED and displays up to five nearby matches, without redirecting to the filter form. A location status/retry row explains loading, missing location, or empty results. Other existing home sample cards are unchanged.

Home and See all use `rankNearby`: approved drivers accepting bookings, matching the detected service area, with usable positions within 30 km. They do not invent a trip date or vehicle preference. Find My Best Driver additionally applies the customer's selected trip filters using `rank`. All three require actual current phone coordinates and a detected service area, with no destination allow-list. Recommendation cards and View Profile buttons open the existing customer-facing DriverDetailsActivity with the selected candidate information. Demo profiles retain their sample-data label and cannot send bookings or messages; the original profile layout is unchanged.

## Customer flow

- Recommended for You > See all also supports pulling down at the top of its list. Its orange refresh circle sits beneath the existing header and uses the same nearby refresh operation as Home. Existing cards remain during loading, and completion, errors, or leaving the screen stop the circle. This is enabled only for See all browsing; the separate filtered trip results flow keeps its existing behavior.
- Customer Home supports pulling down when its driver list is at the top. An AndroidX SwipeRefreshLayout circle appears directly below the fixed search bar for the requested refresh. It reloads the existing local driver source, reapplies the current search text, and refreshes nearby recommendations through the existing location/algorithm flow. Existing cards remain visible during loading. Success, location failure/timeout, and leaving the screen stop the indicator; concurrent location refreshes are ignored. Static sample drivers remain samples, with no backend or ranking changes.
- No pickup pin or destination entry on the recommendation form. These remain part of the separate booking flow.
- On opening, request foreground coarse/fine location permission as needed. Android LocationManager supplies GPS/network location, without a Google Play services dependency. A usable fix must be at most two minutes old and have reported accuracy of at most 5 km.
- Resolve the service area using Android Geocoder off the main thread. Accept any named area: prefer sub-admin area (usually province), then admin area, then locality. Existing Philippine aliases group cities with the Bohol/Cebu demo service areas; they do not restrict other destinations. Country information must be present, but there is no country allow-list. Distance or a bounding rectangle is never used to guess an island.
- Location/provider/permission/geocoder failure or the 15-second timeout leaves a visible retry action. There is no manual province selector and no results until a current location and named service area are detected. Retry can request permission or open phone location settings. No area or coordinates are fabricated. A valid location with no nearby candidates shows an empty result, rather than falling back to drivers from another province.
- With current location, filter the driver's declared service area first, then rank eligible drivers within 30 km by straight-line distance. This is not road distance, travel time, or a prediction of the driver's location on a future date.
- Date/time, duration, vehicle and transmission remain on the original form. Defaults are tomorrow at 9 AM, four hours, SUV, Any transmission. Times use Asia/Manila. Form selections survive rotation; the screen refreshes GPS rather than restoring an old fix. The results screen also validates the timestamp and accuracy of the received fix, preventing old area-only requests from displaying matches. Location updates and pending callbacks stop when the activity stops; late geocoder results are ignored.
- Results show up to five matches with a kilometres badge. Empty results explain the filters. Demo profiles use the original Driver Profile screen, are clearly labelled, and cannot send bookings or messages.

## Engine rules

1. Require an approved driver accepting bookings in the selected service area.
2. Match vehicle and transmission; Any requires at least one known supported skill.
3. One availability interval must contain the entire requested trip.
4. Reject any overlapping booking. Half-open intervals permit adjacent trips. Absolute time windows also support overnight shifts if supplied by the data source.
5. For current-location ranking, reject missing, future-dated, or older-than-15-minute driver locations. Haversine uses Earth radius 6371.0088 km. Exact-distance ties use rating, completed trips, then stable ID.
6. Current coordinates are required by the engine. Unrated drivers remain eligible, after rated drivers when the preceding sort keys tie.

Demo shifts are 7 AM–8 PM on the requested date. A–H serve Bohol; I–K serve Cebu. These are the current fixture locations, not the supported destination list. Other areas are accepted but can have no results until candidates are supplied there. B has a noon–1 PM booking, F is unapproved, G is unavailable, H has a stale GPS fix (excluded from distance ranking). D and K are unrated. All positions, ratings, trips and fees are fixtures.

## Validation

Recommendation tests cover Haversine/antimeridian distances, invalid and stale fixes, permission-independent area resolution, matching, schedule/booking boundaries, overnight intervals, deterministic ties, unrated drivers, radius/no-match behavior, service-area separation, and current-area searches retaining all trip filters. Nearby browsing is tested separately to ensure distance takes priority over rating and that no trip filters are invented. Additional cases resolve Davao, Negros Oriental, Metro Manila, foreign and previously unknown areas, rank supplied Davao candidates, and verify that Manila does not fall back to Bohol/Cebu fixtures.

Phone testing is deferred. Manual checklist:

- In Recommended for You > See all, pull down at the top, verify location/ranking refresh and spinner dismissal, scroll down normally without triggering refresh, and confirm View Profile still opens the original screen. Try denied permission/location off and leaving during refresh. The filtered Find My Best Driver results should not gain a pull gesture.
- Pull down below the Home search bar at the top of the list: check the orange circular indicator, completion/error dismissal, retained search text, unchanged card/profile navigation, and normal scrolling when the list is not at the top. Leave while refreshing and return; the circle must not remain stuck. Check denied permission and phone Location off.
- Home shows the nearest eligible demo driver and See all stays on the original card screen with up to five drivers sorted by distance. Both show the detected area and demo label. They refresh location when resumed; their status row retries detection or opens permission/location settings.
- With allowed location and GPS on, the form detects the current area without a map screen or pin. Try an area beyond Bohol/Cebu: show its detected name and an empty result if no fixtures exist there. Only nearby candidates serving the detected area appear.
- Deny permission, turn off location, go offline or use a device without geocoder support; verify that no recommendations appear and that retry or phone location settings are available; no manual area choice should appear.
- Rotate the screen and return from results; trip filter fields persist and GPS refreshes automatically. Changing the real/test device location from Bohol to Cebu changes the service-area filter; tapping the location row only retries. Leaving the screen stops location work.
- Choose Luxury or a 24-hour trip for no matches. A four-hour trip starting at 9 AM excludes B. Neither a nearer Cebu driver nor a higher-rated Cebu driver can appear in a Bohol search.

## Backend later

Replace DemoDriverSource with authenticated driver records: approved status, service areas, supported skills, timestamped locations, schedules and bookings. Normalize driver service areas consistently with the customer geocoder result; production should use canonical geographic identifiers instead of relying on display-name equality. Authoritative availability must be checked atomically on the server during booking; local rankings do not reserve a slot. Real account integration and push messages are not implemented by this feature.
