# Driver registration

The existing Account, Personal Info, Requirements, and Review screens now share an in-progress draft. Required text/date fields show inline red errors when Next or Save changes is pressed. Account validation also checks email format, password length, matching passwords, and terms acceptance; experience must be a non-negative whole number.

Review displays the entered values and selected images. Its three existing Edit links open the corresponding prefilled section. Save changes validates and returns to Review; Back cancels a review edit. Normal wizard Back retains draft work. Activity state restoration retains the draft, and no registration preferences/database or credential logging was added. Document selection uses Android's document picker with read access so previews can survive activity recreation.

Submit opens Driver Home directly, clears the registration stack, and shows a rounded Pending approval dialog over Home. Its Continue button dismisses the dialog and leaves Home open. An open dialog restores across activity recreation; a dismissed dialog does not reappear, and ordinary Home visits do not show it. Terms uses an orange outlined unchecked box and a white check on orange when selected, with no theme tint covering the check.

This is a local UI flow; it does not add a registration server, a persisted approval status, or an identity verification service. Photo uploads are not newly made mandatory by the empty-text-field validation.

## Verification

`RegistrationFlowTest` covers empty account/personal fields, missing license fields/date selections, the mandatory terms checkbox with a separate inline message, forward draft propagation, each review Edit link, canceled edits, backward propagation, and activity recreation. The Personal and Requirements Next listeners look up fields from the activity, rather than from the button's view hierarchy. Run it on a connected device with:

```powershell
./gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.hiremydriver.RegistrationFlowTest
```

Manual device checks: select front/back license and clearance images, take a selfie, continue to Review, edit each section, and rotate the phone. Confirm previews/values remain and red errors clear as fields are corrected. Confirm normal Back preserves work while Back during review editing discards unsaved changes.
