package com.example.hiremydriver

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.CheckBox
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.google.android.material.textfield.TextInputLayout
import org.junit.Assert.*
import org.junit.Test
import org.junit.After
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RegistrationFlowTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val photoFixtures = mutableListOf<java.io.File>()

    private fun clearancePhoto(): String {
        val file = java.io.File.createTempFile("clearance-test-", ".png", context.cacheDir)
        photoFixtures.add(file)
        val bitmap = android.graphics.Bitmap.createBitmap(32, 32, android.graphics.Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.WHITE)
        file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return android.net.Uri.fromFile(file).toString()
    }

    private fun awaitClearancePhoto() {
        val deadline = android.os.SystemClock.uptimeMillis() + 5000
        do {
            var loaded = false
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                val screen = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                    .filterIsInstance<LicenseInfoActivity>().firstOrNull()
                loaded = screen?.findViewById<android.widget.ImageView>(R.id.imgPoliceClearance)?.drawable != null
            }
            if (loaded) return
            Thread.sleep(50)
        } while (android.os.SystemClock.uptimeMillis() < deadline)
        fail("Clearance photo did not load")
    }

    private fun capturePreview(name: String) {
        val screenshot = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot() ?: return
        java.io.File(context.externalCacheDir, name).outputStream().use {
            screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        screenshot.recycle()
    }

    @After fun closeRegistrationScreens() {
        // Submit clears the task, so its Home activity is outside the original scenario.
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            Stage.values().flatMap { ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(it).toList() }
                .distinct().filter { it.packageName == context.packageName && !it.isFinishing }
                .forEach { it.finish() }
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        photoFixtures.forEach { it.delete() }
    }

    @Test fun incompleteReviewCannotSubmitOrOpenDriverHome() {
        ActivityScenario.launch(ReviewInfoActivity::class.java).use { screen ->
            onView(withId(R.id.btnSubmitVerification)).perform(click())
            val deadline = android.os.SystemClock.uptimeMillis() + 5000
            var failed = false
            while (!failed && android.os.SystemClock.uptimeMillis() < deadline) {
                screen.onActivity { activity ->
                    val model = androidx.lifecycle.ViewModelProvider(activity)[DriverDocumentSubmissionModel::class.java]
                    failed = model.state.value == DriverDocumentSubmissionModel.State.ERROR
                }
                if (!failed) Thread.sleep(50)
            }
            assertTrue("Incomplete registration must be rejected before account creation", failed)
            onView(withId(R.id.btnSubmitVerification)).check(matches(isEnabled()))
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                assertFalse(ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                    .any { it is DriverHomeActivity })
            }
        }
    }

    @Test fun driverLoginRequiresCredentialsAndPendingNoticeStaysOnLogin() {
        ActivityScenario.launch<DriverLoginActivity>(Intent(context, DriverLoginActivity::class.java)
            .putExtra(DriverHomeActivity.SHOW_PENDING_APPROVAL, true)).use { screen ->
            onView(withId(R.id.txtVerificationPending)).inRoot(isDialog()).check(matches(isDisplayed()))
            onView(withId(R.id.btnVerificationContinue)).inRoot(isDialog()).perform(scrollTo(), click())
            onView(withId(R.id.btnLogin)).perform(scrollTo(), click())
            screen.onActivity {
                assertFalse(it.findViewById<TextInputLayout>(R.id.emailLayout).error.isNullOrBlank())
                assertFalse(it.findViewById<TextInputLayout>(R.id.passwordLayout).error.isNullOrBlank())
            }
        }
    }
    private fun selectDate(boxId: Int, year: Int, month: Int, day: Int) {
        onView(withId(boxId)).perform(scrollTo(), click())
        onView(isAssignableFrom(android.widget.DatePicker::class.java)).perform(object : androidx.test.espresso.ViewAction {
            override fun getConstraints() = isAssignableFrom(android.widget.DatePicker::class.java)
            override fun getDescription() = "Select a manual document date"
            override fun perform(uiController: androidx.test.espresso.UiController, view: android.view.View) {
                (view as android.widget.DatePicker).updateDate(year, month - 1, day)
                uiController.loopMainThreadUntilIdle()
            }
        })
        onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click())
    }

    @Test fun clearanceUploadKeepsManualDatesAndPhotoAfterRecreation() {
        val photo = android.net.Uri.parse(clearancePhoto())
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        var openedCamera = false
        val monitor = object : android.app.Instrumentation.ActivityMonitor() {
            override fun onStartActivity(intent: Intent): android.app.Instrumentation.ActivityResult? {
                if (intent.component?.className == LicenseCameraActivity::class.java.name) openedCamera = true
                return if (intent.action == Intent.ACTION_OPEN_DOCUMENT)
                    android.app.Instrumentation.ActivityResult(android.app.Activity.RESULT_OK, Intent().setData(photo))
                else null
            }
        }
        instrumentation.addMonitor(monitor)
        try {
            val draft = Bundle().apply {
                putString("licenseNumber", "TEST-LICENSE")
                putString("licenseExpiration", "1/1/2030")
            }
            ActivityScenario.launch<LicenseInfoActivity>(Intent(context, LicenseInfoActivity::class.java)
                .putExtra(RegistrationStepActivity.DRAFT, draft)).use { screen ->
                onView(withText("Scan police clearance")).check(doesNotExist())
                onView(withId(R.id.txtIssueDate)).check(matches(withText("Select date of issue")))
                onView(withId(R.id.txtValidityDate)).check(matches(withText("Select validity date")))
                onView(withId(R.id.btnUploadPolice)).perform(scrollTo(), click())
                awaitClearancePhoto()
                // Uploading a photo must not invent either date.
                onView(withId(R.id.txtIssueDate)).check(matches(withText("Select date of issue")))
                onView(withId(R.id.txtValidityDate)).check(matches(withText("Select validity date")))
                onView(withId(R.id.layoutIssueDate)).perform(scrollTo(), click())
                onView(withId(android.R.id.button2)).inRoot(isDialog()).perform(click())
                onView(withId(R.id.txtIssueDate)).check(matches(withText("Select date of issue")))
                selectDate(R.id.layoutIssueDate, 2026, 9, 15)
                selectDate(R.id.layoutValidityDate, 2027, 3, 15)
                onView(withId(R.id.replacePolicePhoto)).perform(scrollTo(), click())
                awaitClearancePhoto()
                screen.recreate()
                awaitClearancePhoto()
                onView(withId(R.id.txtIssueDate)).check(matches(withText("9/15/2026")))
                onView(withId(R.id.txtValidityDate)).check(matches(withText("3/15/2027")))
                onView(withId(R.id.btnLicenseNext)).perform(scrollTo(), click())
                onView(withId(R.id.txtReviewIssueDate)).check(matches(withText("Date of issue: 9/15/2026")))
                onView(withId(R.id.txtReviewValidityDate)).check(matches(withText("Validity date: 3/15/2027")))
                onView(withId(R.id.imgReviewPoliceClearance)).perform(scrollTo()).check(matches(isDisplayed()))
                assertFalse(openedCamera)
            }
        } finally { instrumentation.removeMonitor(monitor) }
    }

    @Test fun pendingDialogRestoresWhileOpenButStaysDismissedAfterContinue() {
        ActivityScenario.launch<DriverHomeActivity>(Intent(context, DriverHomeActivity::class.java)
            .putExtra(DriverHomeActivity.SHOW_PENDING_APPROVAL, true)).use { screen ->
            screen.recreate()
            onView(withId(R.id.txtVerificationPending)).inRoot(isDialog()).check(matches(withText("Pending approval")))
            onView(withId(R.id.btnVerificationContinue)).inRoot(isDialog()).perform(scrollTo(), click())
            screen.recreate()
            onView(withId(R.id.txtVerificationPending)).check(doesNotExist())
            onView(withId(R.id.scrollDriverHome)).check(matches(isDisplayed()))
        }
    }

    @Test fun ordinaryHomeDoesNotShowPendingDialog() {
        ActivityScenario.launch(DriverHomeActivity::class.java).use {
            onView(withId(R.id.txtVerificationPending)).check(doesNotExist())
            onView(withId(R.id.scrollDriverHome)).check(matches(isDisplayed()))
        }
    }

    @Test fun completeAccountRequiresTermsAndShowsSeparateErrorWithoutCheckboxIcon() {
        val draft = Bundle().apply {
            putString("name", "Terms test driver")
            putString("email", "terms@example.com")
            putString("mobile", "09123456789")
            putString("password", "test password")
            putString("confirmPassword", "test password")
        }
        ActivityScenario.launch<DriverRegistrationActivity>(Intent(context, DriverRegistrationActivity::class.java)
            .putExtra(RegistrationStepActivity.DRAFT, draft)).use { screen ->
            onView(withId(R.id.btnNext)).perform(scrollTo(), click())
            onView(withId(R.id.txtTermsError)).perform(scrollTo()).check(matches(isDisplayed()))
            onView(withId(R.id.chkTerms)).check(matches(isNotChecked()))
            screen.onActivity { assertNull(it.findViewById<CheckBox>(R.id.chkTerms).error) }
            onView(withId(R.id.chkTerms)).perform(scrollTo(), click())
            onView(withId(R.id.txtTermsError)).check(matches(withEffectiveVisibility(Visibility.GONE)))
            capturePreview("registration-terms.png")
            onView(withId(R.id.btnNext)).perform(scrollTo(), click())
            onView(withId(R.id.edtAddress)).check(matches(withText("")))
            onView(withId(R.id.btnPersonalBack)).perform(click())
        }
    }

    @Test fun emptyAccountAndPersonalFieldsAreBlockedWithInlineErrors() {
        ActivityScenario.launch(DriverRegistrationActivity::class.java).use { screen ->
            onView(withId(R.id.btnNext)).perform(scrollTo(), click())
            screen.onActivity { activity ->
                listOf(R.id.layoutFullName, R.id.layoutEmail, R.id.layoutMobile,
                    R.id.layoutPassword, R.id.layoutConfirmPassword).forEach {
                    assertFalse(activity.findViewById<TextInputLayout>(it).error.isNullOrBlank())
                }
            }
            onView(withId(R.id.edtFullName)).perform(scrollTo(), replaceText("Test driver"), closeSoftKeyboard())
            screen.onActivity { assertNull(it.findViewById<TextInputLayout>(R.id.layoutFullName).error) }
        }
        ActivityScenario.launch(PersonalInfoActivity::class.java).use { screen ->
            onView(withId(R.id.btnPersonalNext)).perform(scrollTo(), click())
            screen.onActivity { activity ->
                listOf(R.id.layoutDateOfBirth, R.id.layoutAddress, R.id.layoutExperience, R.id.layoutBio).forEach {
                    assertFalse(activity.findViewById<TextInputLayout>(it).error.isNullOrBlank())
                }
            }
        }
    }

    @Test fun emptyLicenseFieldsAndUnselectedDatesAreBlocked() {
        ActivityScenario.launch(LicenseInfoActivity::class.java).use {
            onView(withId(R.id.btnLicenseNext)).perform(scrollTo(), click())
            listOf("License number", "License expiration date", "Date of issue", "Validity date").forEach {
                onView(withText("$it is required")).perform(scrollTo()).check(matches(isDisplayed()))
            }
            onView(withText("Clearance number is required")).check(doesNotExist())
            onView(withId(R.id.policePhotoError)).perform(scrollTo())
                .check(matches(withText(R.string.police_photo_required)))
        }
    }

    @Test fun clearanceNeedsPhotoAndManualDatesAndRemovalBlocksNext() {
        val draft = Bundle().apply {
            putString("licenseNumber", "A01-02-000003")
            putString("licenseExpiration", "1/1/2030")
            putString("issueDate", "10/9/2026")
            putString("validityDate", "4/9/2027")
            putString("policeImage", clearancePhoto())
            // Old saved drafts must no longer surface a clearance number.
            putString("clearanceNumber", "LEGACY-CLEARANCE")
        }
        ActivityScenario.launch<LicenseInfoActivity>(Intent(context, LicenseInfoActivity::class.java)
            .putExtra(RegistrationStepActivity.DRAFT, draft)).use { screen ->
            screen.recreate()
            awaitClearancePhoto()
            onView(withText("Clearance / Transaction Number (optional)")).check(doesNotExist())
            onView(withId(R.id.btnLicenseNext)).perform(scrollTo(), click())
            onView(withId(R.id.imgReviewPoliceClearance)).perform(scrollTo()).check(matches(isDisplayed()))
            onView(withId(R.id.txtReviewIssueDate)).check(matches(withText("Date of issue: 10/9/2026")))
            onView(withId(R.id.txtReviewValidityDate)).check(matches(withText("Validity date: 4/9/2027")))
            onView(withText("Clearance / Transaction number (optional): LEGACY-CLEARANCE")).check(doesNotExist())
            onView(withId(R.id.btnReviewBack)).perform(click())
            awaitClearancePhoto()
            onView(withId(R.id.removePolicePhoto)).perform(scrollTo(), click())
            onView(withId(R.id.btnLicenseNext)).perform(scrollTo(), click())
            onView(withId(R.id.policePhotoError)).perform(scrollTo())
                .check(matches(withText(R.string.police_photo_required)))
            onView(withId(R.id.txtIssueDate)).check(matches(withText("10/9/2026")))
            onView(withId(R.id.txtValidityDate)).check(matches(withText("4/9/2027")))
        }
    }

    @Test fun draftReachesReviewAndSectionEditsSurviveBackAndRecreation() {
        val draft = Bundle().apply {
            putString("name", "Registration test driver")
            putString("email", "registration@example.com")
            putString("mobile", "09123456789")
            putString("password", "test password ")
            putString("confirmPassword", "test password ")
            putBoolean("terms", true)
            putString("birthDate", "1/1/1990")
            putString("address", "Original address")
            putString("experience", "3")
            putString("bio", "Careful driver")
            putString("licenseNumber", "TEST-LICENSE")
            putString("licenseExpiration", "1/1/2030")
            putString("policeImage", clearancePhoto())
            putString("issueDate", "1/1/2026")
            putString("validityDate", "1/1/2030")
        }
        ActivityScenario.launch<DriverRegistrationActivity>(Intent(context, DriverRegistrationActivity::class.java)
            .putExtra(RegistrationStepActivity.DRAFT, draft)).use { account ->
            account.recreate()
            onView(withId(R.id.edtPassword)).check(matches(withText("test password ")))
            onView(withId(R.id.btnNext)).perform(scrollTo(), click())
            onView(withId(R.id.edtAddress)).check(matches(withText("Original address")))
            onView(withId(R.id.btnPersonalNext)).perform(scrollTo(), click())
            awaitClearancePhoto()
            onView(withId(R.id.btnLicenseNext)).perform(scrollTo(), click())
            onView(withId(R.id.txtReviewFullName)).check(matches(withText("Full name: Registration test driver")))

            onView(withId(R.id.btnEditAccount)).perform(scrollTo(), click())
            onView(withId(R.id.edtFullName)).perform(scrollTo(), replaceText("Corrected driver"), closeSoftKeyboard())
            onView(withId(R.id.btnNext)).perform(scrollTo(), click())
            onView(withId(R.id.txtReviewFullName)).check(matches(withText("Full name: Corrected driver")))

            onView(withId(R.id.btnEditPersonal)).perform(scrollTo(), click())
            onView(withId(R.id.edtAddress)).perform(scrollTo(), replaceText("Corrected address"), closeSoftKeyboard())
            onView(withId(R.id.btnPersonalNext)).perform(scrollTo(), click())
            onView(withId(R.id.txtReviewAddress)).check(matches(withText("Address: Corrected address")))

            onView(withId(R.id.btnEditLicense)).perform(scrollTo(), click())
            awaitClearancePhoto()
            onView(withId(R.id.edtLicenseNumber)).perform(scrollTo(), replaceText("CORRECTED-LICENSE"), closeSoftKeyboard())
            onView(withId(R.id.btnLicenseNext)).perform(scrollTo(), click())
            onView(withId(R.id.txtReviewLicenseNumber)).perform(scrollTo())
                .check(matches(withText("License number: CORRECTED-LICENSE")))

            // Canceling a second edit must leave the saved review values alone.
            onView(withId(R.id.btnEditPersonal)).perform(scrollTo(), click())
            onView(withId(R.id.edtAddress)).perform(scrollTo(), replaceText("Discard this change"), closeSoftKeyboard())
            onView(withId(R.id.btnPersonalBack)).perform(click())
            onView(withId(R.id.txtReviewAddress)).check(matches(withText("Address: Corrected address")))

            // Back through the wizard must carry review edits to the original steps.
            onView(withId(R.id.btnReviewBack)).perform(click())
            onView(withId(R.id.edtLicenseNumber)).check(matches(withText("CORRECTED-LICENSE")))
            onView(withId(R.id.btnLicenseBack)).perform(click())
            onView(withId(R.id.edtAddress)).check(matches(withText("Corrected address")))
            onView(withId(R.id.btnPersonalBack)).perform(click())
            account.recreate()
            onView(withId(R.id.edtFullName)).check(matches(withText("Corrected driver")))
        }
    }
}
