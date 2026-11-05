package com.example.hiremydriver

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomerFirebaseProjectTest {
    @Test fun acceptsTheSharedHireMyDriverProject() {
        assertTrue(CustomerFirebaseProject.accepts("hiremydriver-e88f7"))
    }

    @Test fun rejectsMissingOrUnrelatedConfiguration() {
        listOf(null, "", "hiremydriver-customer", "some-other-project").forEach {
            assertFalse(CustomerFirebaseProject.accepts(it))
        }
    }
}
