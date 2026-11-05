package com.example.hiremydriver

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises async UI state with controlled tasks; never changes a live user's password. */
@RunWith(AndroidJUnit4::class)
class ChangePasswordFlowTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private fun onMain(action: () -> Unit) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync(action)
        instrumentation.waitForIdleSync()
    }

    @Test fun successWaitsForServerAndDuplicateTapsDoNotSubmitTwice() {
        val completion = TaskCompletionSource<Void>()
        var calls = 0
        val model = ChangePasswordViewModel { _, current, next ->
            calls++
            assertEquals(" Oldpass1 ", current)
            assertEquals(" Newpass2 ", next)
            completion.task
        }
        onMain {
            model.save(context, " Oldpass1 ", " Newpass2 ", " Newpass2 ")
            model.save(context, " Oldpass1 ", " Newpass2 ", " Newpass2 ")
            assertEquals(1, calls)
            assertTrue(model.state.value!!.saving)
            assertFalse(model.state.value!!.saved)
        }
        onMain { completion.setResult(null) }
        onMain {
            assertTrue(model.state.value!!.saved)
            assertFalse(model.state.value!!.saving)
        }
    }

    @Test fun wrongCurrentPasswordDoesNotReportSuccessAndAllowsRetry() {
        val completion = TaskCompletionSource<Void>()
        val retry = TaskCompletionSource<Void>()
        var calls = 0
        val model = ChangePasswordViewModel { _, _, _ ->
            calls++
            if (calls == 1) completion.task else retry.task
        }
        onMain { model.save(context, "Wrongpass1", "Newpass2", "Newpass2") }
        onMain { completion.setException(FirebaseAuthInvalidCredentialsException("ERROR_INVALID_CREDENTIAL", "test failure")) }
        onMain {
            assertFalse(model.state.value!!.saved)
            assertFalse(model.state.value!!.saving)
            assertEquals(PasswordChangeField.CURRENT, model.state.value!!.failure!!.field)
            model.save(context, "Oldpass1", "Newpass2", "Newpass2")
            assertEquals(2, calls)
        }
        onMain { retry.setResult(null) }
        onMain { assertTrue(model.state.value!!.saved) }
    }

    @Test fun updateFailureIsNotMistakenForSuccess() {
        val completion = TaskCompletionSource<Void>()
        val model = ChangePasswordViewModel { _, _, _ -> completion.task }
        onMain { model.save(context, "Oldpass1", "Newpass2", "Newpass2") }
        onMain { completion.setException(FirebaseAuthInvalidCredentialsException("ERROR_WEAK_PASSWORD", "policy rejection")) }
        onMain {
            assertFalse(model.state.value!!.saved)
            assertFalse(model.state.value!!.saving)
            assertEquals(PasswordChangeField.NEW, model.state.value!!.failure!!.field)
        }
    }

    @Test fun mismatchedConfirmationNeverCallsFirebase() {
        val model = ChangePasswordViewModel { _, _, _ -> throw AssertionError("Invalid input must not submit") }
        onMain {
            model.save(context, "Oldpass1", "Newpass2", "Newpass2 ")
            assertFalse(model.state.value!!.saving)
            assertEquals(PasswordChangeField.CONFIRM, model.state.value!!.failure!!.field)
        }
    }
}
