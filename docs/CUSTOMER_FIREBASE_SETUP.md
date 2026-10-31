# HireMyDriver customer Firebase setup

Use the existing **HireMyDriver** Firebase project, **hiremydriver-e88f7**. The Android app and admin dashboard share Firebase Authentication, but being signed in does not grant admin privileges. Do not create a second customer project.

## Connect the Android app

1. Open https://console.firebase.google.com/ and select **HireMyDriver** (`hiremydriver-e88f7`).
2. Open **Project settings → General → Your apps**. Select the Android app with package **com.example.hiremydriver**, or choose **Add app → Android** and register that exact package if it is missing. An optional nickname is **HireMyDriver Android**. SHA certificates are not required for email/password authentication.
3. Download its **google-services.json** into **app/google-services.json** in this Android project. This is an Android client configuration file, not a service-account key. Never put Admin SDK credentials in the Android app.
4. In **Authentication → Sign-in method**, confirm **Email/Password** is enabled. Keep existing admin accounts and provider settings.
5. Sync and run the Android project. The Google services plugin and Firebase Auth dependency are configured already.

Debug builds without the file are allowed for other development; authentication fails closed with a setup message. Release builds require the file. The app rejects a configuration belonging to another project. No configuration values are fabricated.

## Connected authentication

- Customer signup creates an email/password Firebase Auth account and sets its display name.
- Login validates credentials with Firebase before opening Home. Passwords are not trimmed or stored by application code.
- Password recovery sends a Firebase reset email and uses the existing Firebase email template/action handler. This change does not modify that project-wide template.
- Logout signs out and clears the activity stack.
- Profile name and email come from Firebase Auth. Profile photos and extra profile fields (phone, address, birth date) remain local and are stored per UID; phone is not a verified phone-login credential.
- Google/Facebook buttons do not bypass authentication; these providers still need their own setup.
- Driver authentication is not connected by this customer-login change.

## Roles and data access

The inspected local admin dashboard checks both the verified token and current server-side custom claims. It requires `admin: true`; owner-only operations additionally require the owner role. Customer signup does not set either claim. Preserve these server checks.

Authentication users will appear together in Firebase Console. Customer, driver, and administrator permissions must be enforced independently of the screen used to log in. Roles must be assigned only by trusted server code; never accept an admin/owner flag supplied by a mobile client.

**Customer role assignment is still a backend integration step.** Newly registered accounts have no custom role claim. The current admin directory labels those accounts **Unassigned**. A trusted registration backend must assign `role: "customer"` without changing other roles before they can be shown as Customer there. Driver approval and role assignment also need a trusted backend.

This change does not implement or deploy Firestore/Storage rules, customer cloud profiles, bookings, driver approval, or document uploads. Before those features access Firebase data, inspect existing rules and add UID/role-based authorization. Never enable blanket access for all authenticated users. Local admin source inspection is not verification of the deployed server or live Firebase rules.

## Verification after adding configuration

1. Register a customer and confirm it appears in **Authentication → Users** in `hiremydriver-e88f7`.
2. Sign out. Confirm an incorrect password cannot open Home, then sign in with the correct one.
3. Check the displayed name/email and confirm logging out clears the session.
4. Request a reset email, set a new password, and sign in again.
5. Try the customer account on the deployed admin login: it must be denied. Confirm the existing owner can still sign in.
6. After backend role provisioning is added, confirm customers have only their intended role and drivers cannot access unassigned bookings or other users' private documents.

The Android configuration is present. Live signup, login and email delivery still require tests on a device.

Official setup: https://firebase.google.com/docs/android/setup
Email/password: https://firebase.google.com/docs/auth/android/password-auth
Server-assigned roles: https://firebase.google.com/docs/auth/admin/custom-claims

## Signup reports a generic error although Email/Password is enabled

A read-only diagnostic on 2026-10-07, using this Android package and local debug signing certificate, returned HTTP 403 `API_KEY_SERVICE_BLOCKED` for `identitytoolkit.googleapis.com`. Enabling the Email/Password provider does not override API key restrictions.

The Android configuration was using the same key as `ROUTES_API_KEY` in local.properties. That key is restricted to Routes, so its rejection by Firebase Authentication does not indicate a Routes malfunction.

The Firebase client key from the existing dashboard configuration was verified against Firebase's public configuration endpoint, including the Android app ID. Firebase accepted the request and returned the matching project. Only the Android client's `api_key.current_key` in app/google-services.json was replaced. Maps and Routes keys, application restrictions, and map code were unchanged. No Google Cloud restrictions were modified.

The Firebase key must allow Identity Toolkit API and Token Service API; the supplied console screenshots show both. Rebuild and install the app to use the corrected Firebase key. The app displays service/configuration failures as a form error; email-specific errors still appear at Email Address, and password-policy failures at Password. Server responses, tokens, and credentials are not displayed in error messages.

This diagnostic did not create an account, send an email, change cloud settings, or verify a full signup. A connected device is still needed to test the actual customer flow.

## Change Password in customer settings

Change Password verifies the signed-in user's current email/password with Firebase reauthentication, then calls Firebase updatePassword only after verification succeeds and the same user is still signed in. The screen reports success only after the update succeeds. Incorrect-current-password and new-password policy errors appear at their respective fields; setup/network/session failures appear at the bottom of the form.

The request survives activity recreation and ignores duplicate Save taps. Passwords are sent exactly as entered and are never written to saved instance state or local profile preferences. The existing screen requirements (at least eight characters, uppercase/lowercase letters, and a number) are now enforced. Firebase may also apply its configured password policy.

Device acceptance check: enter a wrong current password and confirm no success; use the correct current password with a valid new password; confirm success; sign out and verify the old password fails and the new one signs in. Automated controlled-task tests do not change live account credentials.
