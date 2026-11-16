package com.example.nonoti

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.example.nonoti.data.FocusBlockEntity
import com.example.nonoti.notifications.AndroidFocusPlatform
import com.example.nonoti.notifications.FocusRuntime
import com.example.nonoti.ui.FirstRunPermissionStore
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class NonotiAppInstrumentedTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val application get() = context.applicationContext as NonotiApplication
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun setUp() {
        FirstRunPermissionStore(context).markComplete()
        AndroidFocusPlatform(context).disableZen()
        FocusRuntime.stop()
        runBlocking {
            application.database.dao().clearFocusSession()
            application.database.dao().focusBlocks().forEach { block ->
                application.database.dao().deleteFocusBlock(block.startMinute, block.endMinute)
            }
        }
    }

    @After
    fun tearDown() {
        scenario?.close()
        setNotificationAccess(true)
    }

    @Test
    fun focusShellShowsReadinessAndNavigation() {
        launchActivity()
        dismissSetupIfShown()
        composeRule.onNodeWithText("Choose when messages wait for you.").assertIsDisplayed()
        composeRule.onNodeWithText("Today").assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.app_name)).assertDoesNotExist()
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
        launchActivity()
        dismissSetupIfShown()
        composeRule.onNodeWithContentDescription("Add Focus block").performClick()

        composeRule.onNodeWithText("Start time").assertIsDisplayed()
        composeRule.onNodeWithText("End time").assertIsDisplayed()
    }

    @Test
    fun startNowUsesAnExplicitEndTime() {
        launchActivity()
        dismissSetupIfShown()
        composeRule.onNodeWithText("Start now").performClick()

        composeRule.onNodeWithText("End time").assertIsDisplayed()
    }

    @Test
    fun incompleteSetupExplainsLocalNotificationStorage() {
        launchActivity(notificationAccess = false)
        composeRule.onNodeWithText(context.getString(R.string.setup_title)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.notification_privacy_detail)).assertIsDisplayed()
    }

    @Test
    fun incompleteSetupReappearsWhenAppIsEnteredAgain() {
        val activity = launchActivity(notificationAccess = false)
        val title = context.getString(R.string.setup_title)
        composeRule.onNodeWithText(title).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.not_now)).performClick()
        composeRule.onNodeWithText(title).assertDoesNotExist()

        activity.moveToState(Lifecycle.State.CREATED)
        activity.moveToState(Lifecycle.State.RESUMED)

        composeRule.onNodeWithText(title).assertIsDisplayed()
    }

    @Test
    fun longPressingFocusBlockOffersDeletion() {
        launchActivity()
        dismissSetupIfShown()
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

    private fun launchActivity(notificationAccess: Boolean = true): ActivityScenario<MainActivity> {
        setNotificationAccess(notificationAccess)
        return ActivityScenario.launch(MainActivity::class.java).also { scenario = it }
    }

    private fun setNotificationAccess(enabled: Boolean) {
        val action = if (enabled) "allow_listener" else "disallow_listener"
        UiDevice.getInstance(instrumentation).executeShellCommand(
            "cmd notification $action ${context.packageName}/com.example.nonoti.notifications.NonotiNotificationListenerService",
        )
        composeRule.waitUntil(5_000) { AndroidFocusPlatform(context).readiness().notificationAccess == enabled }
    }

    private fun dismissSetupIfShown() {
        val notNow = context.getString(R.string.not_now)
        if (composeRule.onAllNodesWithText(notNow).fetchSemanticsNodes().isNotEmpty()) {
            composeRule.onNodeWithText(notNow).performClick()
        }
    }
}
