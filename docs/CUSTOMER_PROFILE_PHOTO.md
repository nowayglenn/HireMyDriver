# Customer photo and search keyboard

- Customer Home hides its bottom navigation while Android reports the keyboard visible, and restores it when hidden. BaseActivity still owns system/keyboard spacing; other screens keep their behavior.
- Customer Profile displays a non-interactive circular photo. The square photo views have an inset stroke so their circular outlines are not clipped at the view edges. The existing no_profile.xml drawable is used until a photo is saved, matching driver avatars without a photo.
- Edit Profile's change-photo button opens the Android document picker. Selection previews the photo; Save commits its URI to local customer preferences. Back without Save preserves the previous photo. The draft URI survives activity recreation after selection.
- The app retains read access to the selected document, decodes a bounded bitmap off the main thread, and applies EXIF orientation. Missing or unreadable documents fall back to the placeholder. Customer Profile reloads the saved photo on resume.
- This is local template storage, not an account upload or backend integration. Production should scope the photo to the authenticated customer.

Phone verification remains deferred. Check opening/closing the keyboard on Home, tapping the display-only profile photo, selecting portrait/landscape photos in Edit Profile, saving and reopening, cancelling changes, rotation, and an unavailable photo document. Confirm the circular border is complete on the target phone.
