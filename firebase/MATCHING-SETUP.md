# Driver matching — existing Firestore / Spark plan

No Cloud Functions, admin-dashboard endpoint, Blaze upgrade or billing setup is used.

## Activate

Publish the entire `firebase/firestore-combined.txt` in Firebase Console → Firestore → Rules. It includes existing registration, booking, messaging and reviews permissions, plus the matching rules. Start with `rules_version` and include the closing braces. Do not replace it with a matching-only fragment.

Install the updated Android app for both roles. Older driver builds cannot change booking status after matching is initialized because they do not update the anonymous busy slot in the same transaction.

## Driver

1. Open Driving Information. Select actual service coverage, vehicle types and transmission. For province-wide coverage, select **Bohol**, **Cebu**, or the province from the saved address. City-only coverage is not silently expanded to the whole province.
2. Save actual working hours under Availability & Schedule → weekly schedule. Untouched form defaults are not advertised as a confirmed schedule.
3. Turn availability on when accepting bookings. Open Driver Home to retry synchronization when reconnecting.

Only matching settings and anonymous busy windows are added to the existing `driverProfiles` records. Private registration documents, customer identities, contact information, notes and trip locations remain in the existing private collections.

## Customer

Matching uses the saved profile address first. Already-permitted GPS is a fallback with a 15-minute area cache. Search checks the selected area, date, duration, vehicle, transmission, confirmed weekly hours and busy slots against a fresh Firestore server response. No demo candidates or invented availability are used. Missing/unconfirmed data excludes a driver until synchronized.

The driver's first complete server booking refresh initializes availability. After initialization, driver booking transitions and their busy slot must be updated atomically; Firestore rejects mismatches. Completed/declined bookings remove their busy slot. Active bookings block overlaps, malformed time slots fail closed, and overdue active services stay unavailable until completed. Other customers cannot read the underlying private booking documents.

Pending and approved driver visibility remains unchanged for now; the requested future approval restrictions have not been activated.

Results are a schedule check at search time, not a reservation. Existing booking acceptance remains the driver's decision.

## Verification after publishing

- Save a driver's service area, vehicles, transmission and working hours; verify synchronization succeeds.
- Search in the same province and matching time window, then a different province/vehicle/time.
- Accept an overlapping request and search again; the busy driver must be excluded.
- Confirm customer and driver booking status still synchronize through the existing workflow.
- Open My Reviews and Customer Reviews and confirm the saved feedback appears.

Spark has free usage quotas. This setup does not enable billing; availability still requires network access and remaining Firestore quota.
