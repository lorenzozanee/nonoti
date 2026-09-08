package com.example.nonoti.settings

import android.content.Context
import android.content.Intent

class AndroidInstalledApps(private val context: Context) : InstalledAppsPort {
    override fun apps(): List<InstalledApp> {
        val manager = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return manager.queryIntentActivities(launcherIntent, 0)
            .asSequence()
            .map { it.activityInfo.applicationInfo }
            .distinctBy { it.packageName }
            .filter { it.packageName != context.packageName }
            .map { InstalledApp(it.packageName, manager.getApplicationLabel(it).toString()) }
            .sortedBy { it.label.lowercase() }
            .toList()
    }
}
