package com.example.nonoti.notifications

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ListenerConnectionChangeTest {
    @Test
    fun transientDisconnectWithSystemAccessKeepsFocusActive() {
        assertFalse(
            ListenerConnectionChange.shouldFailOpen(
                listenerConnected = false,
                accessGranted = true,
            ),
        )
    }

    @Test
    fun revokedSystemAccessFailsOpen() {
        assertTrue(
            ListenerConnectionChange.shouldFailOpen(
                listenerConnected = false,
                accessGranted = false,
            ),
        )
    }
}
