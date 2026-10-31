# Driver profile settings

- Driver-mode Customer Reviews hides the app's bottom navigation. Customer-mode reviews keep their existing navigation, and the Android system navigation bar remains available.
- Personal Information and Documents open dedicated settings activities with the existing navy/orange palette, rounded cards, Back, and Save Changes. They no longer enter the registration wizard.
- Personal settings save name, email, mobile, birthday, address, and bio locally; the driver's profile name/email refresh after saving. Name/email are required, and email format is checked. Optional fields may remain empty.
- Documents support license and clearance details, date pickers, front/back/clearance photo selection, bounded previews, and replacement. Saving incomplete local document details is allowed; it is not a verification submission.
- Vehicle/transmission selectors retain the original card layout and selection behavior, with a white check on orange for selected options and a gray outline for unchecked options.

The settings stores are local to this app installation, not authenticated driver accounts or an admin/backend service. They do not import registration drafts. Photos are selected using Android's document picker and referenced with read permissions. Back discards edits; activity recreation retains the current draft.

`DriverSettingsFlowTest` covers review navigation visibility, settings navigation from Profile, saving/reopening/canceling, document draft recreation, and vehicle selection synchronization. Device execution remains deferred at the user's request. Manually verify photo picking/replacement and long forms on a phone when testing resumes.
