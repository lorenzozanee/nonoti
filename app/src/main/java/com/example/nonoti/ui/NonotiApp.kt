package com.example.nonoti.ui

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.TimePickerDialog
import android.os.Build
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.foundation.Image
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.core.app.NotificationManagerCompat
import com.example.nonoti.focus.DailyFocusBlock
import com.example.nonoti.focus.FocusBlockError
import com.example.nonoti.focus.FocusBlockRules
import com.example.nonoti.focus.FocusBlockStatus
import com.example.nonoti.focus.FocusBlockStatuses
import com.example.nonoti.notifications.FocusRuntime
import com.example.nonoti.notifications.ActiveFocusSession
import com.example.nonoti.notifications.AndroidFocusPlatform
import com.example.nonoti.notifications.NonotiPlatform
import com.example.nonoti.notifications.CompatibilityStore
import com.example.nonoti.notifications.CompatibilityTestController
import com.example.nonoti.focus.FocusSession
import com.example.nonoti.focus.FocusSessionRules
import com.example.nonoti.focus.StartNowError
import com.example.nonoti.focus.StartNowResult
import com.example.nonoti.focus.FocusViewModel
import com.example.nonoti.box.BoxViewModel
import com.example.nonoti.box.RelativeTimeFormatter
import com.example.nonoti.box.RelativeTimeUnit
import com.example.nonoti.settings.SettingsViewModel
import com.example.nonoti.MainActivity
import com.example.nonoti.R
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import kotlinx.serialization.Serializable

@Serializable
private enum class AppTab(@param:StringRes val labelRes: Int) : NavKey {
    Focus(R.string.tab_focus),
    Box(R.string.tab_box),
    Settings(R.string.tab_settings),
}

private enum class FocusDialog(@param:StringRes val titleRes: Int) {
    StartNow(R.string.start_now),
    NewBlock(R.string.new_focus_block),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NonotiApp(
    focusViewModel: FocusViewModel,
    boxViewModel: BoxViewModel,
    settingsViewModel: SettingsViewModel,
    launchAction: String? = null,
    onLaunchActionConsumed: () -> Unit = {},
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val backStack = rememberNavBackStack(AppTab.Focus)
    val selectedTab = backStack.lastOrNull() as? AppTab ?: AppTab.Focus
    fun navigate(tab: AppTab) {
        if (selectedTab != tab) {
            backStack.clear()
            backStack.add(tab)
        }
    }
    var emergencyPrompt by remember { mutableStateOf(false) }
    var emergencyUnlocked by remember { mutableStateOf(false) }
    var emergencyActivityVersion by remember { mutableIntStateOf(0) }
    var setupDismissed by remember { mutableStateOf(false) }
    var readinessRefresh by remember { mutableIntStateOf(0) }
    val readiness = rememberReadiness(readinessRefresh)
    val firstRunPermissionStore = remember { FirstRunPermissionStore(context) }
    var permissionOnboardingComplete by remember { mutableStateOf(firstRunPermissionStore.isComplete()) }
    var permissionOnboardingAttempted by remember { mutableStateOf(firstRunPermissionStore.attempted()) }
    var permissionOnboardingInFlight by remember { mutableStateOf(false) }
    val notificationPermissionOnboarding = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        permissionOnboardingInFlight = false
        readinessRefresh++
    }
    val specialPermissionOnboarding = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        permissionOnboardingInFlight = false
        readinessRefresh++
    }
    LaunchedEffect(readiness, permissionOnboardingAttempted, permissionOnboardingInFlight, permissionOnboardingComplete) {
        if (permissionOnboardingComplete || permissionOnboardingInFlight) return@LaunchedEffect
        val step = FirstRunPermissionFlow.next(
            PermissionReadiness(
                postNotifications = readiness.postNotifications,
                notificationAccess = readiness.notificationAccess,
                dndAccess = readiness.dndAccess,
                exactAlarms = readiness.exactAlarms,
            ),
            permissionOnboardingAttempted,
        )
        if (step == null) {
            firstRunPermissionStore.markComplete()
            permissionOnboardingComplete = true
            return@LaunchedEffect
        }
        firstRunPermissionStore.markAttempted(step)
        permissionOnboardingAttempted = firstRunPermissionStore.attempted()
        permissionOnboardingInFlight = true
        if (step == PermissionOnboardingStep.PostNotifications) {
            notificationPermissionOnboarding.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        } else {
            runCatching { specialPermissionOnboarding.launch(step.settingsIntent(context)) }
                .onFailure { permissionOnboardingInFlight = false }
        }
    }
    val activeSession by FocusRuntime.state.collectAsState()
    val focusing = activeSession != null
    LaunchedEffect(focusing, launchAction) {
        if (focusing) {
            navigate(AppTab.Focus)
            if (launchAction == MainActivity.ACTION_EMERGENCY_ACCESS) emergencyPrompt = true
        } else if (launchAction == MainActivity.ACTION_EMERGENCY_ACCESS || launchAction == MainActivity.ACTION_OPEN_BOX) {
            navigate(AppTab.Box)
        }
        if (launchAction != null) onLaunchActionConsumed()
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                emergencyUnlocked = false
                setupDismissed = false
            }
            if (event == Lifecycle.Event.ON_RESUME) readinessRefresh++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(emergencyUnlocked, emergencyActivityVersion) {
        if (emergencyUnlocked) {
            delay(5 * 60 * 1000L)
            emergencyUnlocked = false
        }
    }
    LaunchedEffect(focusing) {
        while (focusing) {
            delay(5_000L)
            val session = FocusRuntime.current() ?: break
            if (session.isUnsupported) break
            val platform = AndroidFocusPlatform(context.applicationContext)
            val readiness = platform.readiness().let {
                if (session.id.startsWith("compatibility-")) it.copy(compatibility = true) else it
            }
            if (!readiness.isReady || !platform.isZenActive()) {
                CompatibilityStore(context).invalidate()
                NonotiPlatform.failOpen(context, session.id)
                break
            }
        }
    }
    Scaffold(
        modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars).pointerInput(emergencyUnlocked) {
            if (emergencyUnlocked) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Initial)
                        emergencyActivityVersion++
                    }
                }
            }
        },
        bottomBar = {
            if (!focusing || emergencyUnlocked) NavigationBar {
                AppTab.entries.forEach { tab ->
                    if (focusing && tab == AppTab.Settings) return@forEach
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { navigate(tab) },
                        icon = { Icon(if (tab == AppTab.Focus) Icons.Default.Lock else if (tab == AppTab.Box) Icons.Default.Inbox else Icons.Default.Settings, contentDescription = stringResource(tab.labelRes)) },
                        label = { Text(stringResource(tab.labelRes)) },
                    )
                }
            }
        },
    ) { padding ->
        NavDisplay(
            backStack = backStack,
            modifier = Modifier.padding(padding),
            entryProvider = entryProvider {
                entry<AppTab> { tab ->
                    when (tab) {
                        AppTab.Focus -> FocusScreen(focusViewModel, Modifier, activeSession = activeSession, readiness = readiness, onEmergency = { emergencyPrompt = true })
                        AppTab.Box -> if (focusing && !emergencyUnlocked) LockedBoxScreen(Modifier, onEmergency = { emergencyPrompt = true }) else BoxScreen(boxViewModel, Modifier, onSourceOpened = { emergencyUnlocked = false })
                        AppTab.Settings -> SettingsScreen(settingsViewModel, boxViewModel, Modifier)
                    }
                }
            },
        )
    }
    if (emergencyPrompt) {
        AlertDialog(
            onDismissRequest = { emergencyPrompt = false },
            title = { Text(stringResource(R.string.emergency_access)) },
            text = { Text(stringResource(R.string.emergency_prompt)) },
            confirmButton = { TextButton(onClick = { emergencyPrompt = false; emergencyUnlocked = true; navigate(AppTab.Box) }) { Text(stringResource(R.string.continue_action)) } },
            dismissButton = { TextButton(onClick = { emergencyPrompt = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (!focusing && permissionOnboardingComplete && !readiness.allReady && !setupDismissed) {
        SetupDialog(
            readiness = readiness,
            onReadinessChanged = { readinessRefresh++ },
            onDismiss = { setupDismissed = true },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FocusScreen(focusViewModel: FocusViewModel, modifier: Modifier, activeSession: ActiveFocusSession?, readiness: Readiness, onEmergency: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var confirmEnd by remember { mutableStateOf(false) }
    if (activeSession != null) {
        if (activeSession.isUnsupported) {
            Column(
                modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(Icons.Default.Warning, contentDescription = null)
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.focus_unavailable), style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.focus_recovery_detail), textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                Button(onClick = { Thread { NonotiPlatform.restore(context.applicationContext) }.start() }) {
                    Text(stringResource(R.string.retry_recovery))
                }
            }
            return
        }
        var nowMillis by remember(activeSession.id) { mutableLongStateOf(System.currentTimeMillis()) }
        LaunchedEffect(activeSession.id, activeSession.endAtMillis) {
            while (true) {
                nowMillis = System.currentTimeMillis()
                delay(1_000L)
            }
        }
        Column(modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(64.dp))
            Icon(Icons.Default.Lock, contentDescription = null)
            Text(stringResource(if (activeSession.isReleasing) R.string.restoring_quietly else R.string.focus_active), style = MaterialTheme.typography.headlineSmall)
            val end = Instant.ofEpochMilli(activeSession.endAtMillis).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))
            Text(stringResource(R.string.ends_at, end))
            Text(formatRemaining(activeSession.endAtMillis - nowMillis))
            Text(
                stringResource(R.string.hold_emergency),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.combinedClickable(
                    onClick = { Toast.makeText(context, R.string.hold_emergency_hint, Toast.LENGTH_SHORT).show() },
                    onLongClick = onEmergency,
                ).padding(12.dp),
            )
            if (!activeSession.isReleasing) {
                TextButton(onClick = { confirmEnd = true }) { Text(stringResource(R.string.end_focus_early)) }
                TextButton(onClick = {
                    val currentEnd = Instant.ofEpochMilli(activeSession.endAtMillis).atZone(ZoneId.systemDefault())
                    TimePickerDialog(
                        context,
                        { _, hour, minute ->
                            val now = ZonedDateTime.now()
                            val requested = FocusSessionRules.startNow(now, LocalTime.of(hour, minute))
                            val endInstant = (requested as? StartNowResult.Accepted)?.window?.end
                            if (endInstant == null || !endInstant.isAfter(Instant.ofEpochMilli(activeSession.endAtMillis))) {
                                Toast.makeText(context, R.string.extend_later_hint, Toast.LENGTH_SHORT).show()
                            } else {
                                scope.launch {
                                    val extended = withContext(Dispatchers.IO) { NonotiPlatform.extendTo(context, endInstant) }
                                    Toast.makeText(
                                        context,
                                        if (extended) R.string.focus_extended else R.string.focus_extend_failed,
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                            }
                        },
                        currentEnd.hour,
                        currentEnd.minute,
                        true,
                    ).show()
                }) { Text(stringResource(R.string.extend_end_time)) }
            }
        }
        if (confirmEnd) {
            AlertDialog(
                onDismissRequest = { confirmEnd = false },
                title = { Text(stringResource(R.string.end_focus_question)) },
                text = { Text(stringResource(R.string.end_focus_detail)) },
                confirmButton = { TextButton(onClick = { confirmEnd = false; NonotiPlatform.endEarly(context, activeSession.id) }) { Text(stringResource(R.string.end_focus)) } },
                dismissButton = { TextButton(onClick = { confirmEnd = false }) { Text(stringResource(R.string.cancel)) } },
            )
        }
        return
    }
    var blockPendingDelete by remember { mutableStateOf<DailyFocusBlock?>(null) }
    var dialog by remember { mutableStateOf<FocusDialog?>(null) }
    val blocks by focusViewModel.blocks.collectAsState()
    Box(modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp),
        ) {
            item {
                Text(stringResource(R.string.tab_focus), style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(R.string.focus_intro), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.today), style = MaterialTheme.typography.titleLarge)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { dialog = FocusDialog.StartNow }) { Text(stringResource(R.string.start_now)) }
                        IconButton(onClick = { dialog = FocusDialog.NewBlock }) { Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_focus_block)) }
                    }
                }
            }
            if (blocks.isEmpty()) {
                item { Text(stringResource(R.string.no_daily_blocks), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                items(blocks, key = { "${it.startMinute}-${it.endMinute}" }) { block ->
                    FocusBlockRow(block, onLongClick = { blockPendingDelete = block })
                }
            }
        }
    }
    dialog?.let { activeDialog ->
        var startMinute by remember(activeDialog) { mutableIntStateOf(9 * 60) }
        var endMinute by remember(activeDialog) {
            val defaultEnd = ZonedDateTime.now().plusHours(1)
            mutableIntStateOf(if (activeDialog == FocusDialog.StartNow) defaultEnd.hour * 60 + defaultEnd.minute else 11 * 60 + 30)
        }
        var blockError by remember(activeDialog) { mutableStateOf<Int?>(null) }
        AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(stringResource(activeDialog.titleRes)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(if (activeDialog == FocusDialog.StartNow) R.string.start_now_detail else R.string.daily_block_detail))
                    if (activeDialog == FocusDialog.StartNow) {
                        Text(stringResource(R.string.end_time))
                        OutlinedButton(onClick = {
                            TimePickerDialog(
                                context,
                                { _, hour, minute -> endMinute = hour * 60 + minute; blockError = null },
                                endMinute / 60,
                                endMinute % 60,
                                true,
                            ).show()
                        }) { Text(formatMinute(endMinute)) }
                        blockError?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
                    } else {
                        Text(stringResource(R.string.start_time))
                        OutlinedButton(onClick = {
                            TimePickerDialog(
                                context,
                                { _, hour, minute -> startMinute = hour * 60 + minute; blockError = null },
                                startMinute / 60,
                                startMinute % 60,
                                true,
                            ).show()
                        }) { Text(formatMinute(startMinute)) }
                        Text(stringResource(R.string.end_time))
                        OutlinedButton(onClick = {
                            TimePickerDialog(
                                context,
                                { _, hour, minute -> endMinute = hour * 60 + minute; blockError = null },
                                endMinute / 60,
                                endMinute % 60,
                                true,
                            ).show()
                        }) { Text(formatMinute(endMinute)) }
                        blockError?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (activeDialog == FocusDialog.NewBlock) {
                        val candidate = DailyFocusBlock(startMinute, endMinute)
                        val error = FocusBlockRules.validate(candidate, blocks)
                        if (error == null) {
                            focusViewModel.save(candidate)
                            dialog = null
                        } else {
                            blockError = error.messageRes()
                        }
                    } else if (activeDialog == FocusDialog.StartNow) {
                        val now = ZonedDateTime.now()
                        when (val result = FocusSessionRules.startNow(now, LocalTime.of(endMinute / 60, endMinute % 60))) {
                            is StartNowResult.Accepted -> {
                                val continuous = FocusSessionRules.extendThroughDailyBlocks(result.window, blocks, now)
                                val session = FocusSession("manual-${System.currentTimeMillis()}", continuous.start, continuous.end)
                                if (NonotiPlatform.start(context, session, AndroidFocusPlatform(context).readiness()) == com.example.nonoti.notifications.FocusStartResult.Started) {
                                    dialog = null
                                }
                            }
                            is StartNowResult.Rejected -> {
                                blockError = when (result.reason) {
                                    StartNowError.TooShort -> R.string.start_now_too_short
                                    StartNowError.TooLong -> R.string.start_now_too_long
                                }
                            }
                        }
                    } else {
                        dialog = null
                    }
                }, enabled = when (activeDialog) {
                    FocusDialog.StartNow -> readiness.allReady
                    FocusDialog.NewBlock -> true
                }) {
                    Text(stringResource(when (activeDialog) {
                        FocusDialog.NewBlock -> R.string.save
                        FocusDialog.StartNow -> R.string.start
                    }))
                }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    blockPendingDelete?.let { block ->
        AlertDialog(
            onDismissRequest = { blockPendingDelete = null },
            title = { Text(stringResource(R.string.delete_focus_question)) },
            text = { Text(stringResource(R.string.delete_focus_detail, "${formatMinute(block.startMinute)} → ${formatMinute(block.endMinute)}")) },
            confirmButton = {
                TextButton(onClick = {
                    focusViewModel.delete(block)
                    blockPendingDelete = null
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { blockPendingDelete = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FocusBlockRow(block: DailyFocusBlock, onLongClick: () -> Unit) {
    val status = FocusBlockStatuses.at(block, ZonedDateTime.now())
    val statusLabel = when (status) {
        FocusBlockStatus.Upcoming -> stringResource(R.string.upcoming)
        FocusBlockStatus.Active -> stringResource(R.string.focusing)
        FocusBlockStatus.Finished -> stringResource(R.string.finished)
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (status == FocusBlockStatus.Finished) 0.55f else 1f)
            .combinedClickable(onClick = {}, onLongClick = onLongClick),
        colors = CardDefaults.cardColors(
            containerColor = if (status == FocusBlockStatus.Active) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surface,
        ),
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text("${formatMinute(block.startMinute)} → ${formatMinute(block.endMinute)}", fontWeight = FontWeight.SemiBold)
                Text(statusLabel, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
            }
            Icon(Icons.Default.Lock, contentDescription = stringResource(R.string.focus_block))
        }
    }
}

@StringRes
private fun FocusBlockError.messageRes(): Int = when (this) {
    FocusBlockError.EqualEndpoints -> R.string.block_equal_error
    FocusBlockError.TooShort -> R.string.block_short_error
    FocusBlockError.OverlapsExisting -> R.string.block_overlap_error
    FocusBlockError.TouchesExisting -> R.string.block_touch_error
}

@Composable
private fun SetupDialog(
    readiness: Readiness,
    onReadinessChanged: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onReadinessChanged() }
    var selfTestTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(readiness.preflightReady, selfTestTick) {
        if (!readiness.preflightReady || CompatibilityStore(context).isTestRunning()) {
            delay(1_000L)
            onReadinessChanged()
            selfTestTick++
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.setup_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.setup_detail), color = MaterialTheme.colorScheme.onSurfaceVariant)
                SetupPermissionLine(stringResource(R.string.notification_access), readiness.notificationAccess, Icons.Default.Notifications) {
                    runCatching { context.startActivity(PermissionOnboardingStep.NotificationAccess.settingsIntent(context)) }
                }
                SetupPermissionLine(stringResource(R.string.dnd_access), readiness.dndAccess, Icons.Default.Policy) {
                    runCatching { context.startActivity(android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)) }
                }
                SetupPermissionLine(stringResource(R.string.exact_alarms), readiness.exactAlarms, Icons.Default.Alarm) {
                    runCatching { context.startActivity(android.content.Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))) }
                }
                SetupPermissionLine(stringResource(R.string.summary_notifications), readiness.postNotifications, Icons.Default.Inbox) {
                    notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                }
                SetupPermissionLine(stringResource(R.string.compatibility_self_test), readiness.compatibility, Icons.Default.CheckCircle, enabled = readiness.preflightReady) {
                    if (CompatibilityStore(context).isTestCompleted()) {
                        CompatibilityStore(context).markPassed()
                        onReadinessChanged()
                    } else {
                        CompatibilityTestController.start(context)
                        selfTestTick++
                    }
                }
                Text(stringResource(R.string.notification_privacy_detail), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (CompatibilityStore(context).isTestRunning()) {
                    Text(stringResource(R.string.test_running), fontSize = 13.sp)
                } else if (CompatibilityStore(context).isTestCompleted() && !readiness.compatibility) {
                    Text(stringResource(R.string.test_completed), fontSize = 13.sp)
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.not_now)) } },
    )
}

@Composable
private fun SetupPermissionLine(
    label: String,
    ready: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean = !ready,
    onClick: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().clickable(enabled = enabled && !ready, onClick = onClick).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, modifier = Modifier.padding(end = 10.dp))
        Text(label, modifier = Modifier.weight(1f))
        Text(stringResource(if (ready) R.string.ready else R.string.needs_access), color = if (ready) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error, fontSize = 13.sp)
    }
}

@Composable
private fun BoxScreen(viewModel: BoxViewModel, modifier: Modifier, onSourceOpened: () -> Unit) {
    val notifications by viewModel.notifications.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    var sourceOpenFailed by remember { mutableStateOf(false) }
    LazyColumn(modifier.fillMaxSize().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp)) {
        item { Text(stringResource(R.string.tab_box), style = MaterialTheme.typography.headlineSmall) }
        item { Text(stringResource(R.string.box_intro), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (sourceOpenFailed) {
            item { Text(stringResource(R.string.source_unavailable), color = MaterialTheme.colorScheme.error) }
        }
        if (notifications.isEmpty()) {
            item {
                Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
                    Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Inbox, contentDescription = null, modifier = Modifier.padding(bottom = 8.dp))
                        Text(stringResource(R.string.box_empty), fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.box_empty_detail), fontSize = 14.sp)
                    }
                }
            }
        } else {
            items(notifications, key = { "${it.sessionId}:${it.notificationKey}" }) { item ->
                Card(onClick = {
                    sourceOpenFailed = !FocusRuntime.openSource(context, item)
                    if (!sourceOpenFailed) onSourceOpened()
                }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        val appInfo = remember(item.packageName) { runCatching { context.packageManager.getApplicationInfo(item.packageName, 0) }.getOrNull() }
                        val icon = remember(appInfo) { appInfo?.let { context.packageManager.getApplicationIcon(it).toBitmap(96, 96).asImageBitmap() } }
                        if (icon != null) Image(icon, contentDescription = null, modifier = Modifier.size(40.dp)) else Icon(Icons.Default.Notifications, contentDescription = null, modifier = Modifier.size(40.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(appInfo?.let { context.packageManager.getApplicationLabel(it).toString() } ?: item.packageName, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                            Text(item.title ?: stringResource(R.string.notification_fallback_title), fontWeight = FontWeight.SemiBold)
                            item.text?.let { Text(it) }
                            item.subText?.let { Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            val sameAppCount = notifications.count { it.packageName == item.packageName && it.sessionId == item.sessionId }
                            Text(pluralStringResource(R.plurals.notification_metadata, sameAppCount, sameAppCount, relativeTimeText(item.firstCapturedAtMillis), relativeTimeText(item.lastUpdatedAtMillis)), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                buildString {
                                    append(item.packageName)
                                    item.channelId?.let { append(" · "); append(it) }
                                    item.conversationId?.let { append(" · "); append(it) }
                                },
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(stringResource(if (item.isInSystemNotificationBar) R.string.captured else R.string.no_longer_in_shade), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            item { TextButton(onClick = viewModel::clear) { Text(stringResource(R.string.clear_box)) } }
        }
    }
}

@Composable
private fun LockedBoxScreen(modifier: Modifier, onEmergency: () -> Unit) {
    Column(modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(72.dp))
        Icon(Icons.Default.Lock, contentDescription = null)
        Text(stringResource(R.string.box_locked), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.box_locked_detail))
        val emergencyLabel = stringResource(R.string.emergency_access)
        TextButton(onClick = onEmergency, modifier = Modifier.semantics { contentDescription = emergencyLabel }) { Text(emergencyLabel) }
    }
}

@Composable
private fun SettingsScreen(viewModel: SettingsViewModel, boxViewModel: BoxViewModel, modifier: Modifier) {
    val settings by viewModel.settings.collectAsState()
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item { Text(stringResource(R.string.tab_settings), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 16.dp)) }
        item { Text(stringResource(R.string.appearance), style = MaterialTheme.typography.titleLarge) }
        item { ToggleSettingRow(stringResource(R.string.dark_mode), stringResource(R.string.dark_mode_detail), settings.darkTheme, viewModel::setDarkTheme) }
        item { SettingRow(stringResource(R.string.app_language), stringResource(R.string.system_language)) }
        item { HorizontalDivider() }
        item { Text(stringResource(R.string.readiness), style = MaterialTheme.typography.titleLarge) }
        item { SettingRow(stringResource(R.string.notification_access), stringResource(R.string.notification_access_detail)) }
        item { SettingRow(stringResource(R.string.dnd), stringResource(R.string.dnd_detail)) }
        item { SettingRow(stringResource(R.string.emergency_access), stringResource(R.string.emergency_access_detail)) }
        item { HorizontalDivider() }
        item { Text(stringResource(R.string.notification_policy), style = MaterialTheme.typography.titleLarge) }
        item { ToggleSettingRow(stringResource(R.string.allow_calls), stringResource(R.string.allow_calls_detail), settings.allowCalls, viewModel::setAllowCalls) }
        item { ToggleSettingRow(stringResource(R.string.allow_alarms), stringResource(R.string.allow_alarms_detail), settings.allowAlarms, viewModel::setAllowAlarms) }
        item { Text(stringResource(R.string.always_allowed_apps), style = MaterialTheme.typography.titleLarge) }
        if (viewModel.installedApps.isEmpty()) {
            item { Text(stringResource(R.string.no_launchable_apps), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            items(viewModel.installedApps, key = { it.packageName }) { app ->
                ToggleSettingRow(
                    app.label,
                    app.packageName,
                    app.packageName in settings.alwaysAllowedPackages,
                    { checked -> viewModel.setAlwaysAllowed(app.packageName, checked) },
                )
            }
        }
        item { HorizontalDivider() }
        item { Text(stringResource(R.string.box_data), style = MaterialTheme.typography.titleLarge) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(24 to R.string.retention_24_hours, 168 to R.string.retention_7_days, 720 to R.string.retention_30_days).forEach { (hours, labelRes) ->
                    TextButton(onClick = { viewModel.setRetentionHours(hours) }, enabled = settings.retentionHours != hours) { Text(stringResource(labelRes)) }
                }
            }
        }
        item { TextButton(onClick = boxViewModel::clear) { Text(stringResource(R.string.clear_box)) } }
        item { SettingRow(stringResource(R.string.privacy), stringResource(R.string.privacy_detail)) }
    }
}

@Composable
private fun ToggleSettingRow(title: String, detail: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(detail, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingRow(title: String, detail: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Medium); Text(detail, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Icon(Icons.Default.Settings, contentDescription = null)
    }
}

private data class Readiness(val notificationAccess: Boolean, val dndAccess: Boolean, val zenRule: Boolean, val exactAlarms: Boolean, val postNotifications: Boolean, val compatibility: Boolean) {
    val preflightReady: Boolean get() = notificationAccess && dndAccess && zenRule && exactAlarms && postNotifications
    val allReady: Boolean get() = preflightReady
}

@Composable
private fun rememberReadiness(refreshKey: Int): Readiness {
    val context = androidx.compose.ui.platform.LocalContext.current
    return remember(refreshKey) {
        val actual = AndroidFocusPlatform(context).readiness()
        Readiness(actual.notificationAccess, actual.notificationPolicyAccess, actual.zenRule, actual.exactAlarms, actual.postNotifications, actual.compatibility)
    }
}

private fun formatMinute(minute: Int): String = "%02d:%02d".format(minute / 60, minute % 60)

@Composable
private fun relativeTimeText(timestampMillis: Long): String {
    val relative = RelativeTimeFormatter.format(timestampMillis)
    return when (relative.unit) {
        RelativeTimeUnit.Now -> stringResource(R.string.just_now)
        RelativeTimeUnit.Minutes -> pluralStringResource(R.plurals.minutes_ago, relative.value, relative.value)
        RelativeTimeUnit.Hours -> pluralStringResource(R.plurals.hours_ago, relative.value, relative.value)
        RelativeTimeUnit.Days -> pluralStringResource(R.plurals.days_ago, relative.value, relative.value)
    }
}

@Composable
private fun formatRemaining(remainingMillis: Long): String {
    val totalMinutes = ((remainingMillis.coerceAtLeast(0L) + 59_999L) / 60_000L)
    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L
    return if (hours > 0) {
        stringResource(R.string.hours_minutes_remaining, hours, minutes)
    } else {
        pluralStringResource(R.plurals.minutes_remaining, minutes.toInt(), minutes)
    }
}
