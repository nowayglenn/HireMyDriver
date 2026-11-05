package com.example.hiremydriver

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.hamcrest.Matchers.not
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MessagingPreparedFlowTest {
    @org.junit.Before fun signedOutFixture() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        com.google.firebase.auth.FirebaseAuth.getInstance().signOut()
        com.google.firebase.auth.FirebaseAuth.getInstance(DriverAuth.firebaseApp(context)).signOut()
    }
    @Test fun signedOutInboxesDoNotShowSampleConversations() {
        ActivityScenario.launch(MessagesActivity::class.java).use { scenario ->
            onView(withText("Sign in to view your messages.")).check(matches(isDisplayed()))
            scenario.recreate()
            onView(withText("Sign in to view your messages.")).check(matches(isDisplayed()))
        }
        ActivityScenario.launch(DriverMessageActivity::class.java).use { scenario ->
            onView(withId(R.id.txtUnread)).check(matches(withText("Unread")))
            onView(withId(R.id.tabUnread)).perform(click())
            onView(withText("Sign in to view your messages.")).check(matches(isDisplayed()))
            scenario.recreate()
            onView(withText("Sign in to view your messages.")).check(matches(isDisplayed()))
        }
    }

    @Test fun legacyNameOnlyChatCannotPretendToSendOrShowOnline() {
        val intent = Intent(ApplicationProvider.getApplicationContext(), ChatConversationActivity::class.java)
            .putExtra("CHAT_NAME", "A sample driver")
        ActivityScenario.launch<ChatConversationActivity>(intent).use { scenario ->
            onView(withText(R.string.messaging_no_conversation)).check(matches(isDisplayed()))
            onView(withId(R.id.btnSend)).check(matches(not(isEnabled())))
            onView(withId(R.id.txtDriverName)).check(matches(withText("Conversation")))
            onView(withId(R.id.txtChatStatus)).check(matches(withText("")))
            onView(withContentDescription("Attach photo")).check(androidx.test.espresso.assertion.ViewAssertions.doesNotExist())
            scenario.recreate()
            onView(withId(R.id.btnSend)).check(matches(not(isEnabled())))
        }
    }
}
