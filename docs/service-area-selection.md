# Location-based preferred service areas

The existing Driving Information field opens a multi-choice dialog. It requests foreground location permission, uses the existing one-shot location reader (15-second timeout, fresh location only), and resolves its province against a nationwide reference directory. No province is selected in code. GPS determines suggestions only: it never checks boxes or saves an area.

If permission is denied, GPS is off, geocoding fails, the detected place is ambiguous, or the location is outside the Philippines, the same dialog provides manual city/municipality/province search. The directory is packaged with the APK, so manual search does not depend on GPS, internet access, an API key or billing. GPS reverse geocoding still depends on the device's location/geocoder services. Suggestions are alphabetical within the detected province, not a claim of precise distance order. Province-free cities use their region grouping.

## Data and persistence

- Reference source: [PSGC Cloud API](https://psgc.cloud/api-docs), retrieved October 17, 2026. `app/src/main/assets/service_areas.json` includes 1,634 city/municipality entries. Sub-municipalities and SGU clusters are excluded. Names and province links come from the directory, not a hand-maintained Bohol/Cebu list.
- `tools/refresh-service-area-directory.cjs` refreshes the nationwide snapshot, validates completeness/parent consistency, repairs source text encoding and groups province-free cities by region. App suggestions change with the driver's detected province; administrative reference-data updates require refreshing this snapshot in a subsequent build.
- Each chosen area is saved as `Municipality (Province)` in the existing `driverProfiles/{uid}.matching.areas` list through the existing Save Changes transaction. This distinguishes duplicate municipality names and works with the current profile display. The existing 30-area limit is retained.
- Previously saved unqualified city/province names remain readable and selectable. Only explicit selections are changed. Cancel keeps the old field; OK updates the draft field; Save Changes persists it to Firebase.
- No new collection, Firestore rule change, index, secret, paid service or backend deployment is needed for this update. The previous mandatory-driver-setup rules must already be published.

## Customer search

Customer Home searches actual saved area preferences as well as driver names; GPS coordinates are not used to decide whether a searched municipality matches. Matching normalizes accents, case and `City of`/`City` spelling. Province-qualified searches distinguish same-name municipalities. The existing registered-profile listener refreshes results after saved edits. A one-minute local recheck accounts for schedule boundaries.

The home search bar has no selected date or duration, so eligibility uses the existing available-now, one-hour schedule/conflict check: completed driving information, accepting bookings, allowed profile status, confirmed schedule projection, working hours and no conflicting booking. Existing pending/approved eligibility policy is preserved. Future-date matching remains in Find My Best Driver.

## Verification

- 204 unit tests passed, including automatic province resolution, manual fallback, city-name variants, duplicate town names, selected/unselected areas, offline drivers and booking conflicts.
- 39 local Firebase emulator checks passed, including exact selected-area persistence/readback, owner-only changes and the existing selection limit.
- Debug APK and instrumentation test compilation passed.
- No phone was connected. Actual GPS permission prompts, visual dialog sizing, and two-account phone interaction still need device verification.

On a phone: open Driving Information → Preferred Service Area, allow location, select a few areas and Save Changes. Confirm those areas appear on the customer-facing driver profile. With a complete online driver whose saved schedule covers the next hour, search each selected municipality, then an unselected one. Repeat the picker with location permission denied and verify manual search, checkbox preservation while filtering, and Cancel.
