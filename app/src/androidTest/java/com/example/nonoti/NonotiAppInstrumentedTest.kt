package com.example.nonoti

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.example.nonoti.data.FocusBlockEntity
import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test

class NonotiAppInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun focusShellShowsReadinessAndNavigation() {
        dismissSetupIfShown()
        composeRule.onNodeWithText("Choose when messages wait for you.").assertIsDisplayed()
        composeRule.onNodeWithText("Today").assertIsDisplayed()
        composeRule.onNodeWithText("nonoti").assertDoesNotExist()
        composeRule.onNodeWithText("Ready for Focus").assertDoesNotExist()
        composeRule.onNodeWithText("Finish setup before Focus").assertDoesNotExist()
        composeRule.onNodeWithText("Notification access").assertDoesNotExist()
        composeRule.onNodeWithText("Do Not Disturb access").assertDoesNotExist()
        composeRule.onNodeWithText("Exact alarms").assertDoesNotExist()
        composeRule.onNodeWithText("Summary notifications").assertDoesNotExist()
        composeRule.onNodeWithText("Compatibility self-test").assertDoesNotExist()
        composeRule.onNodeWithText("Box").assertIsDisplayed()
        composeRule.onNodeWithText("Settings").assertIsDisplayed()
    }

    @Test
    fun addFocusBlockOffersMinutePrecisionTimeControls() {
        dismissSetupIfShown()
        composeRule.onNodeWithContentDescription("Add Focus block").performClick()

        composeRule.onNodeWithText("Start time").assertIsDisplayed()
        composeRule.onNodeWithText("End time").assertIsDisplayed()
    }

    @Test
    fun startNowUsesAnExplicitEndTime() {
        dismissSetupIfShown()
        composeRule.onNodeWithText("Start now").performClick()

        composeRule.onNodeWithText("End time").assertIsDisplayed()
    }

    @Test
    fun setupExplainsNotificationPrivacyBeforeOpeningSystemSettings() {
        composeRule.onNodeWithText("Set up nonoti").assertIsDisplayed()
        composeRule.onNodeWithText(
            "nonoti can read notification titles and message text from other apps. Captured content stays on this device and is never sent to a server.",
        ).assertIsDisplayed()
    }

    @Test
    fun incompleteSetupReappearsWhenAppIsEnteredAgain() {
        composeRule.onNodeWithText("Not now").performClick()
        composeRule.onNodeWithText("Set up nonoti").assertDoesNotExist()

        composeRule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)

        composeRule.onNodeWithText("Set up nonoti").assertIsDisplayed()
    }

    @Test
    fun longPressingFocusBlockOffersDeletion() {
        dismissSetupIfShown()
        val application = composeRule.activity.application as NonotiApplication
        runBlocking {
            application.database.dao().upsertFocusBlock(FocusBlockEntity(8 * 60, 9 * 60))
        }
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("08:00 → 09:00").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithText("08:00 → 09:00").performTouchInput { longClick() }

        composeRule.onNodeWithText("Delete Focus block?").assertIsDisplayed()
        composeRule.onNodeWithText("Delete").performClick()
        composeRule.waitUntil(5_000) {
            runBlocking {
                application.database.dao().focusBlocks().none { it.startMinute == 8 * 60 && it.endMinute == 9 * 60 }
            }
        }
    }

    private fun dismissSetupIfShown() {
        if (composeRule.onAllNodesWithText("Not now").fetchSemanticsNodes().isNotEmpty()) {
            composeRule.onNodeWithText("Not now").performClick()
        }
    }

}
