package com.example.nonoti.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSettingsTest {
    @Test
    fun defaultsFavorUninterruptedFocusAndDarkAppearance() {
        val settings = AppSettings()

        assertFalse(settings.allowCalls)
        assertFalse(settings.allowAlarms)
        assertTrue(settings.alwaysAllowedPackages.isEmpty())
        assertTrue(settings.darkTheme)
    }
}
