# Customer profile photos

The Android app keeps Firebase Authentication and existing profile fields. Photo bytes are stored
in the private Supabase `profile-photos` bucket, one object at `<Firebase UID>/profile.jpg`.
The stable path is derived from the signed-in Firebase account on every device, so no Firestore
schema/rule change or public/signed URL stored in Firestore is required.

## Dashboard setup still required

1. Supabase project: `gemtzjexvdgblpwbvdvh` (HireMyDriver Photos).
2. Authentication > Sign In / Providers > Third-Party Auth: enable Firebase project `hiremydriver-e88f7`.
3. Run the complete `supabase/profile-photos.sql` file in the SQL Editor.
   It keeps the bucket private, allows JPEG/PNG up to 2 MB, and installs owner-only read/insert/update policies.
4. Build/run the app, sign in with a customer account, then Edit Profile > choose/crop > Use photo > Save Changes.
5. Verify replacement, logout/login, and a second device with the same Firebase account.
   Verify a different account cannot retrieve/overwrite the first account's photo.

## Authentication and policy design

Supabase verifies Firebase ID token signatures using the configured third-party integration.
Android sends this token in Authorization and only the publishable key in apikey.
Redirects are disabled to keep tokens on the configured Supabase host. Tokens/response bodies are not logged.

The standard Supabase Firebase example adds `role: authenticated` using a trusted server.
This implementation instead supports Firebase's default token without modifying existing claims:
Supabase assigns it the `anon` database role, and the narrowly scoped policies require a VERIFIED
Firebase issuer, audience, subject, non-anonymous provider and exact subject-owned object path.
The same predicate covers the authenticated database role if it is already assigned.
An API key alone, an unsigned token, another Firebase project or another UID cannot satisfy this flow.
Do not replace these predicates with `true` or make the bucket public. The restrictive boundary also
limits broader permissive policies for this bucket. Roles other than anon/authenticated require review.

References:
- https://supabase.com/docs/guides/auth/third-party/firebase-auth (default tokens use anon role)
- https://supabase.com/docs/guides/storage/security/access-control
- https://supabase.com/docs/guides/storage/serving/downloads

## Behavior and limitations

- Existing crop UI is retained. JPEG upload happens only after Save Changes.
- Text-only edits do not reupload/delete the cloud photo. Crop cancellation changes nothing.
- A stable object path and upsert replace the existing photo without accumulating uploads.
- Firebase session is checked before and after requests; cache is separate for each UID.
- Reads refresh from cloud, with an account-specific private offline cache. No-photo is a normal state.
- Existing local-only photos can appear as a fallback, but must be selected/cropped/saved to upload.
- Repeated save taps are suppressed; save state survives rotation. Failed uploads do not report success.
- Photo upload and Firebase name update are separate operations: a later name-update failure does not undo
  an already successful photo upload. A retry uses the same photo object and is safe.
- Only the account owner can read these photos. Future driver/admin access needs a separate explicit policy.
- Cloud persistence lasts while the account/object/project is retained; free-plan limits still apply.

Local build/tests do not verify hosted policies or real authenticated uploads. Dashboard SQL deployment
and two-account/device verification remain required before treating the feature as live.
