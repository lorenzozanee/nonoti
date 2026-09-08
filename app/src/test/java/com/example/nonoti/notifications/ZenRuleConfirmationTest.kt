package com.example.nonoti.notifications

import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class ZenRuleConfirmationTest {
    @Test
    fun legacyDisableDoesNotDependOnTheGlobalDndFilter() {
        assertTrue(
            ZenRuleConfirmation.disableConfirmed(
                apiLevel = 34,
                ownRuleActive = null,
            ),
        )
    }

    @Test
    fun legacyEnableCannotBeVerifiedWhileAnotherDndRuleIsAlreadyActive() {
        assertFalse(ZenRuleConfirmation.canVerifyEnable(apiLevel = 34, globalDndActive = true))
        assertTrue(ZenRuleConfirmation.canVerifyEnable(apiLevel = 34, globalDndActive = false))
        assertTrue(ZenRuleConfirmation.canVerifyEnable(apiLevel = 35, globalDndActive = true))
    }

    @Test
    fun legacyRuleIsNeverActiveFromGlobalDndAlone() {
        assertFalse(
            ZenRuleActivationEvidence.isActive(
                apiLevel = 34,
                commandedActive = false,
                ruleEnabled = true,
                globalDndActive = true,
                ownRuleStateActive = null,
            ),
        )
    }

    @Test
    fun legacyProcessRestoreCannotProveOwnRuleState() {
        assertFalse(ZenRuleConfirmation.canVerifyRestore(apiLevel = 34))
        assertTrue(ZenRuleConfirmation.canVerifyRestore(apiLevel = 35))
    }
}
