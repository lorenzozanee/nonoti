package com.example.nonoti.notifications

import android.content.Context
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat

class CompatibilityStore(context: Context) {
    private val preferences = context.getSharedPreferences("compatibility", Context.MODE_PRIVATE)
    private val appVersion = runCatching {
        PackageInfoCompat.getLongVersionCode(context.packageManager.getPackageInfo(context.packageName, 0))
    }.getOrDefault(0L)
    private val deviceKey = "${Build.FINGERPRINT}|${Build.VERSION.SDK_INT}|$appVersion"

    fun isPassed(): Boolean = preferences.getString("passed_device", null) == deviceKey

    fun markPassed() {
        if (!isTestCompleted()) return
        preferences.edit().putString("passed_device", deviceKey).remove("test_state").apply()
    }

    fun beginTest() {
        preferences.edit().putString("test_state", "running").apply()
    }

    fun markTestCompleted() {
        preferences.edit().putString("test_state", "completed").apply()
    }

    fun isTestRunning(): Boolean = preferences.getString("test_state", null) == "running"

    fun isTestCompleted(): Boolean = preferences.getString("test_state", null) == "completed"

    fun cancelTest() {
        preferences.edit().remove("test_state").apply()
    }

    fun invalidate() {
        preferences.edit().remove("passed_device").remove("test_state").apply()
    }

    fun clear() {
        preferences.edit().clear().apply()
    }
}
