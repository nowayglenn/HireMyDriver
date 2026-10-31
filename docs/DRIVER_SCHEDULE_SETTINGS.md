# Driver schedule and settings

## Weekly schedule

Profile → Availability & Schedule → Weekly Schedule now edits a real local schedule. Each of the seven days has an availability switch and start/end time pickers. Sunday can be enabled, and all days can be turned off. The original default hours are retained until the driver saves changes.

Time selection follows the phone's 12/24-hour preference. Each enabled day requires an end time after its start time on that same day; overnight windows are not supported. Validation appears beneath the affected day. Disabled days retain their times but do not block saving. Save Schedule writes all seven days together to local preferences. Back discards changes, and rotation restores the unfinished draft.

The Accept New Bookings switch and Driver Home availability switch share the same saved preference. Switching off does not erase weekly hours. Schedule, availability, and Home switches now use the same MaterialSwitch component and track color resource as customer Location Permissions.

These preferences are local to this app installation. There is no authenticated per-driver storage or remote booking-matching integration yet. Saving this schedule does not filter customer bookings, check conflicts, or update the existing sample Upcoming Schedule cards. The Availability & Schedule screen explains the local scope.

## Driver Settings

The existing gear icon in Driver Profile opens a dedicated navy/orange settings screen with rounded cards and no bottom navigation:

- App preferences: Location Permissions and Notifications.
- Privacy and information: Terms & Conditions, Privacy Policy, About HireMyDriver.

Personal Information, Driving Information, Documents, Availability & Schedule, Customer Reviews, Help Center, and Contact Support remain accessible from Driver Profile and are not duplicated in Settings.

Notifications opens Android's actual app notification settings (with an app-settings fallback). The status refreshes on return. The installed version is read from package information. Existing pages retain their previous behavior and persistence limits; no new account, support, or notification backend is implied.

## Verification

Unit coverage checks custom-minute windows, time boundaries, equal/reversed times, disabled-day validation, and editable Sunday/all-days-off schedules.

Instrumented scenarios cover custom Sunday hours persisting on reopen, discarding edits, validation after rotation, and availability synchronization between Home and Schedule. These are compiled, with phone execution deferred at the user's request.

When device testing resumes, also check the Settings gear and each destination, long text/keyboard layouts, the switch appearance, and Back navigation.
