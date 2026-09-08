package com.example.nonoti.notifications

import android.app.AutomaticZenRule
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.service.notification.ZenPolicy
import android.service.notification.Condition
import android.os.Build
import com.example.nonoti.MainActivity

class ZenRuleController(private val context: Context) {
    private val notificationManager = context.getSystemService(NotificationManager::class.java)
    private val preferences = context.getSharedPreferences("zen_rule", Context.MODE_PRIVATE)
    private var ruleId: String? = null

    fun ensureRule(): String? {
        if (notificationManager?.isNotificationPolicyAccessGranted != true) return null
        return runCatching {
            val matches = matchingRules()
            matches.filter { it.value.owner != owner() }.forEach { notificationManager.removeAutomaticZenRule(it.key) }
            val ownedMatches = matches.filter { it.value.owner == owner() }
            val existing = ownedMatches.firstOrNull { (_, rule) -> rule.isEnabled }
            if (existing == null && ownedMatches.isNotEmpty()) return@runCatching null
            ownedMatches.filter { it.key != existing?.key }.forEach { notificationManager.removeAutomaticZenRule(it.key) }
            ruleId = existing?.key ?: notificationManager.addAutomaticZenRule(
                AutomaticZenRule(
                    RuleName,
                    owner(),
                    ComponentName(context, MainActivity::class.java),
                    ConditionId,
                    policy(allowCalls = false, allowAlarms = false),
                    NotificationManager.INTERRUPTION_FILTER_PRIORITY,
                    true,
                ),
            )
            preferences.edit().putString(RuleIdKey, ruleId).apply()
            ruleId
        }.getOrNull()
    }

    fun setEnabled(enabled: Boolean): Boolean {
        markInactive()
        return runCatching {
            if (enabled && !ZenRuleConfirmation.canVerifyEnable(
                    apiLevel = Build.VERSION.SDK_INT,
                    globalDndActive = notificationManager.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL,
                )
            ) return@runCatching false
            val id = (if (enabled) findEnabledRuleId() ?: ensureRule() else findRuleId())
                ?: return@runCatching !enabled
            val rule = notificationManager?.getAutomaticZenRule(id) ?: return@runCatching !enabled
            if (enabled && !rule.isEnabled) return@runCatching false
            notificationManager.setAutomaticZenRuleState(
                id,
                Condition(rule.conditionId, "NoNoTi Focus", if (enabled) Condition.STATE_TRUE else Condition.STATE_FALSE),
            )
            val confirmed = if (enabled) {
                awaitActive()
            } else {
                ZenRuleConfirmation.disableConfirmed(
                    apiLevel = Build.VERSION.SDK_INT,
                    ownRuleActive = if (Build.VERSION.SDK_INT >= 35) !awaitInactive() else null,
                )
            }
            if (confirmed && enabled) preferences.edit().putBoolean(CommandedActiveKey, true).commit()
            confirmed
        }.getOrDefault(false)
    }

    fun markInactive() {
        preferences.edit().putBoolean(CommandedActiveKey, false).commit()
    }

    fun remove() {
        findRuleId()?.let { notificationManager.removeAutomaticZenRule(it) }
        preferences.edit().clear().apply()
        ruleId = null
    }

    fun updatePolicy(allowCalls: Boolean, allowAlarms: Boolean): Boolean {
        return runCatching {
            val id = findEnabledRuleId() ?: ensureRule() ?: return@runCatching false
            val rule = notificationManager.getAutomaticZenRule(id) ?: return@runCatching false
            rule.zenPolicy = policy(allowCalls, allowAlarms)
            notificationManager.updateAutomaticZenRule(id, rule)
        }.getOrDefault(false)
    }

    fun isActive(): Boolean {
        return runCatching {
            val id = findRuleId() ?: return@runCatching false
            val rule = notificationManager.getAutomaticZenRule(id) ?: return@runCatching false
            ZenRuleActivationEvidence.isActive(
                apiLevel = Build.VERSION.SDK_INT,
                commandedActive = preferences.getBoolean(CommandedActiveKey, false),
                ruleEnabled = rule.isEnabled,
                globalDndActive = notificationManager.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL,
                ownRuleStateActive = if (Build.VERSION.SDK_INT >= 35) {
                    notificationManager.getAutomaticZenRuleState(id) == Condition.STATE_TRUE
                } else {
                    null
                },
            )
        }.getOrDefault(false)
    }

    private fun awaitActive(): Boolean {
        val deadline = System.currentTimeMillis() + 1_000L
        while (System.currentTimeMillis() < deadline) {
            if (isRuleActive()) return true
            Thread.sleep(50L)
        }
        return isRuleActive()
    }

    private fun awaitInactive(): Boolean {
        val deadline = System.currentTimeMillis() + 1_000L
        while (System.currentTimeMillis() < deadline) {
            if (Build.VERSION.SDK_INT >= 35) {
                if (!isRuleActive()) return true
            } else if (notificationManager.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_ALL) {
                return true
            }
            Thread.sleep(50L)
        }
        return if (Build.VERSION.SDK_INT >= 35) !isRuleActive() else
            notificationManager.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_ALL
    }

    private fun isRuleActive(): Boolean {
        val id = findEnabledRuleId() ?: return false
        return if (Build.VERSION.SDK_INT >= 35) {
            notificationManager.getAutomaticZenRuleState(id) == Condition.STATE_TRUE
        } else {
            notificationManager.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
        }
    }

    private fun owner() = ComponentName(context, NonotiConditionProviderService::class.java)

    private fun findRuleId(): String? {
        ruleId?.let { return it }
        preferences.getString(RuleIdKey, null)?.let { saved ->
            ruleId = saved
            return saved
        }
        return runCatching { matchingRules().firstOrNull { it.value.isEnabled }?.key ?: matchingRules().firstOrNull()?.key }
            .getOrNull()
            ?.also { ruleId = it }
    }

    private fun findEnabledRuleId(): String? =
        runCatching { matchingRules().firstOrNull { it.value.isEnabled }?.key }
            .getOrNull()
            ?.also { ruleId = it }

    fun isOwnRuleId(id: String?): Boolean =
        id != null && (id == ruleId || id == preferences.getString(RuleIdKey, null) ||
            runCatching { matchingRules().any { it.key == id } }.getOrDefault(false))

    private fun matchingRules() = notificationManager.automaticZenRules.entries.filter {
        it.value.name == RuleName && it.value.conditionId == ConditionId
    }

    private fun policy(allowCalls: Boolean, allowAlarms: Boolean): ZenPolicy {
        val builder = ZenPolicy.Builder()
            .disallowAllSounds()
            .allowAlarms(allowAlarms)
            .allowCalls(if (allowCalls) ZenPolicy.PEOPLE_TYPE_ANYONE else ZenPolicy.PEOPLE_TYPE_NONE)
            .allowSystem(true)
            .hideAllVisualEffects()
        if (Build.VERSION.SDK_INT >= 35) builder.allowPriorityChannels(false)
        return builder.build()
    }

    companion object {
        const val RuleName = "NoNoTi Focus"
        val ConditionId: Uri = Uri.parse("nonoti://focus")
        private const val RuleIdKey = "rule_id"
        private const val CommandedActiveKey = "commanded_active"
    }
}

object ZenRuleConfirmation {
    fun disableConfirmed(apiLevel: Int, ownRuleActive: Boolean?): Boolean =
        apiLevel < 35 || ownRuleActive == false

    fun canVerifyEnable(apiLevel: Int, globalDndActive: Boolean): Boolean =
        apiLevel >= 35 || !globalDndActive

    fun canVerifyRestore(apiLevel: Int): Boolean = apiLevel >= 35
}

object ZenRuleActivationEvidence {
    fun isActive(
        apiLevel: Int,
        commandedActive: Boolean,
        ruleEnabled: Boolean,
        globalDndActive: Boolean,
        ownRuleStateActive: Boolean?,
    ): Boolean =
        commandedActive && ruleEnabled && globalDndActive && (apiLevel < 35 || ownRuleStateActive == true)
}
