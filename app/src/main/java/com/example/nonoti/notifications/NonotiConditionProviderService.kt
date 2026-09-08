package com.example.nonoti.notifications

import android.net.Uri
import android.service.notification.ConditionProviderService

class NonotiConditionProviderService : ConditionProviderService() {
    override fun onConnected() = Unit

    override fun onSubscribe(conditionId: Uri) = Unit

    override fun onUnsubscribe(conditionId: Uri) {
        if (conditionId != ZenRuleController.ConditionId) return
        ZenRuleController(this).markInactive()
        FocusRuntime.current()?.let { session ->
            CompatibilityStore(this).invalidate()
            Thread { NonotiPlatform.failOpen(applicationContext, session.id) }.start()
        }
    }
}
