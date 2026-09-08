package com.example.nonoti.notifications

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusSessionIdentityTest {
    @Test
    fun requestedSessionMustMatchPersistedOrRuntimeSession() {
        assertTrue(FocusSessionIdentity.isCurrent("current", "current", null))
        assertTrue(FocusSessionIdentity.isCurrent("current", null, "current"))
        assertFalse(FocusSessionIdentity.isCurrent("old", "current", "current"))
        assertFalse(FocusSessionIdentity.isCurrent("unknown", null, null))
    }
}
