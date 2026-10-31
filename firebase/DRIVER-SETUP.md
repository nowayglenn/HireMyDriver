# Driver registration setup

Firebase project: `hiremydriver-e88f7`. The Android app uses a named Firebase app,
`HireMyDriverDriver`, for the driver session while keeping the customer session separate.
Both use the same Firebase Authentication account directory; use a different email
for a driver registration if the email is already a customer account.

## Console setup

1. Keep Email/Password enabled in Firebase Authentication.
2. Publish `driver-registration.firestore.rules`. This file includes the customer
   rules shown in the setup conversation and the new driver application rules.
3. In Supabase, configure Firebase Third-Party Auth for `hiremydriver-e88f7`.
4. Run `supabase/profile-photos.sql` and `supabase/driver-documents.sql` in
   the existing HireMyDriver Photos project. Both buckets are private.

## Test from the Android app

1. Build and run the latest app. Open driver registration using an unused test email.
2. Complete the fields and accept terms. Select the clearance dates manually.
3. Add license front/back and clearance photos. A profile selfie is optional.
4. Submit once and wait. The app creates the Firebase account, uploads the documents,
   and atomically writes `users/{uid}` and `driverApplications/{uid}`.
5. Check Firebase Authentication for the new account. Check the application in
   Firestore: status must be `pending`, dates must match the manually selected values,
   and no password or local file URI should appear.
6. Check Supabase: three JPEG files should be under
   `driver-documents/{uid}/{submission UUID}/`. A supplied selfie uses
   `profile-photos/{uid}/profile.jpg`.
7. Successful submission opens Driver Home with the existing pending approval notice.
   Dismiss it to use the driver screens. After logout, sign in with the same credentials:
   pending and approved accounts both open Driver Home. Pending login shows the notice.
   Incorrect credentials must not open Driver Home.
8. If submission fails after creating an account, retry from the same registration
   draft with the same credentials. No second account is created; identical document
   uploads are reused after verifying their bytes.
   An existing email is authenticated with the entered password before recovery.
   Existing non-driver profiles and already submitted applications are rejected;
   completed registrations should use Login.
   Both signup screens show an Account already exists popup for duplicate emails.
   Driver recovery requires the local retry marker for an account created by that
   unfinished driver registration, followed by password verification and server
   role checks. Unknown existing accounts are never converted to drivers, including
   legacy customer accounts without a Firestore profile. Successful submission clears
   the retry marker. An unfinished registration on another device must be recovered
   through support rather than converted via signup.
   If that email exists but the supplied password does not match, Review offers
   Reset password and Edit account. The reset email is sent only when the user
   selects Reset password. After following its link, edit both password fields
   and retry submission; the existing registration draft and photos are retained.
   A genuinely new email still creates its password directly during submission.

## Approval and limits

The admin dashboard approval flow is not connected in this Android workspace.
An authorized backend must review the application and set its status to `approved`.
Client rules deliberately deny updates and self-approval. Do not weaken those rules
to make an admin UI work; integrate its trusted server instead.

Private document viewing by the admin also needs a trusted server that verifies the
admin's permissions before fetching files or issuing short-lived signed URLs.
Never embed the Supabase service-role key in Android or browser code.

Pending and approved driver login opens Driver Home and restores the account's
submitted dates, document references, and profile details. Document photos are loaded
privately with that driver's token and remain view-only. Old device-local submissions
are not automatically uploaded or migrated.
Pending accounts remain `pending` in Firestore. Restrictions on unverified driver
features are deferred until the next setup stage; entering Home does not approve them.

Bookings, live messaging, and admin notifications remain separate integrations.
Messaging still uses its existing disabled feature flag; its driver session binding
has been prepared so it cannot accidentally use the customer session when enabled.
Personal Information edits still use the existing local settings flow; they do not
change the submitted cloud application or Firebase account email.

## Validation

## Registered-account displays

Publish the updated `driver-registration.firestore.rules` in the existing project's
Firestore Rules tab. It retains the customer/driver registration rules and adds
`driverProfiles`. Do not make `driverApplications` readable by other users.

After publishing, run the latest app and sign out/sign in once with each existing
driver account. Login copies only name, registered experience, bio, status and a
small profile-photo thumbnail into the directory. New submissions publish these
fields automatically. The customer Home and Drivers list listen to this collection.
Both pending and approved profiles display, with their actual status; rejected or
other statuses are excluded. An admin changing approval later must also refresh
the public profile status as part of that future approval implementation.

The directory never exposes birth dates, home addresses, email, phone, license
numbers or document paths. Profile thumbnails are intentionally visible to signed-in
users; original photos and uploaded documents retain their private storage policies.

Demo notifications, booking cards, recommendation candidates and invented metrics
are no longer displayed. Original XML cards remain the templates. Legacy demo trips
are not copied into the new per-account local trip/review stores. Registration alone
does not create a customer booking, review or chat. Real cross-device booking,
notification and recommendation-location integrations remain separate work. Drivers
without published pricing cannot start the old sample-price booking flow.

Verification steps: register a driver, check `driverProfiles/{uid}`, sign in as a
customer on another device, and check name/photo/status in Home and search. Check a
pending driver's profile never shows a Verified badge. Check empty accounts show no
sample people, ratings, requests or notifications. Test rules with a signed-in
customer: directory reads allowed, application/document reads and directory writes
for another UID denied. No console rules were deployed automatically by this change.

The app and Android test APK compile. Focused JVM tests cover payload field boundaries,
manual date preservation, invalid dates, terms, account-bound file paths, customer auth
error handling, and profile storage responses. Live Firebase/Supabase submission and
device UI tests require a connected device and a test account; they have not been run
against the production services by this setup task.
