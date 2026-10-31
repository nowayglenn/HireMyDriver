# Permission settings corrections

Location Settings, used by both customer and driver, now distinguishes precise access, approximate-only access, and denied access. Approximate permission is no longer presented as denied. The precision switch displays the actual permission, resets immediately when tapped, and opens Android app settings; it does not pretend to grant or revoke permissions. It refreshes on resume and does not restore stale switch state after rotation. A separate notice reports when the phone's location service is off.

The unsupported Background Location control has been removed. The screen explains that current location updates stop when leaving the map or minimizing the app. No additional permissions or background service were added. Trip timer notifications are separate and unchanged.

Customer Notification Settings now saves booking-update and chat-message preferences in private local preferences. It restores saved values on reopening, preserves an unsaved draft across rotation, and discards edits on Back. Unsupported promotions and email options are removed. A separate Android notification status and settings button reports and manages the real phone permission, independently of saved category choices.

The screen explicitly states that live booking/message alerts are not yet available. Persisting category preferences does not implement push delivery, filter notification history, or change trip-timer notifications. Future live event publishers must consult CustomerNotificationPreferences before delivery. Storage is per app installation, not an authenticated customer account.

Validation: unit coverage for precise/approximate/denied state selection, and a compiled device scenario for preference saving, reopening, rotation, and cancelling. Device execution remains deferred. When testing resumes, check location denied/approximate/precise states, returning from phone settings, GPS off, Android notification permission changes, and unsupported settings-intent fallback behavior.
