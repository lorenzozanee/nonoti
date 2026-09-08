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
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test

class NonotiAppInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun focusShellShowsReadinessAndNavigation() {
        composeRule.onNodeWithText("Choose when messages wait for you.").assertIsDisplayed()
        composeRule.onNodeWithText("Today").assertIsDisplayed()
        composeRule.onNodeWithText("Box").assertIsDisplayed()
        composeRule.onNodeWithText("Settings").assertIsDisplayed()
    }

    @Test
    fun addFocusBlockOffersMinutePrecisionTimeControls() {
        composeRule.onNodeWithContentDescription("Add Focus block").performClick()

        composeRule.onNodeWithText("Start time").assertIsDisplayed()
        composeRule.onNodeWithText("End time").assertIsDisplayed()
    }

    @Test
    fun startNowUsesAnExplicitEndTime() {
        composeRule.onNodeWithText("Start now").performClick()

        composeRule.onNodeWithText("End time").assertIsDisplayed()
    }

    @Test
    fun notificationAccessExplainsPrivacyBeforeOpeningSystemSettings() {
        composeRule.onNodeWithText("Notification access").performClick()

        composeRule.onNodeWithText("Notification privacy").assertIsDisplayed()
    }

    @Test
    fun longPressingFocusBlockOffersDeletion() {
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

}
