package com.example.nonoti

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.nonoti.notifications.PlannedFocusOccurrenceStore
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlannedFocusOccurrenceInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @After
    fun clearStore() {
        context.getSharedPreferences("planned_focus_occurrence", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun skippedOccurrencePersistsAcrossStoreInstances() {
        val skipped = "planned-540-660-1"
        PlannedFocusOccurrenceStore(context).skip(skipped)

        val restored = PlannedFocusOccurrenceStore(context)
        assertFalse(restored.shouldStart(skipped))
        assertTrue(restored.shouldStart("planned-540-660-2"))
        assertTrue(restored.shouldStart("manual-1"))
    }
}
