package com.kvieta.companion.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kvieta.companion.R
import com.kvieta.companion.connection.*
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun ConnectionScreen(model: ConnectionModel, onBack: () -> Unit, onScanQr: (() -> Unit)? = null,
    showBack: Boolean = true, onEnableNotifications: (() -> Unit)? = null, notificationsEnabled: Boolean = false,
    startWithManualInput: Boolean = false) {
    var manual by rememberSaveable { mutableStateOf(startWithManualInput) }
    var settings by rememberSaveable { mutableStateOf(false) }
    var confirmForget by remember { mutableStateOf(false) }
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(Unit) { while (true) { delay(15000); now = Instant.now() } }
    BackHandler(settings) { settings = false }
    val busy = model.refreshing || model.decisionStatus == DecisionStatus.SENDING
    if (showBack) TextButton(onClick = onBack) { Text(stringResource(R.string.link_back)) }

    if (!model.saved) {
        Text(stringResource(R.string.link_title), style = MaterialTheme.typography.headlineLarge)
        Text(stringResource(R.string.link_instructions), color = MaterialTheme.colorScheme.onSurfaceVariant)
        KvietaPanel {
            Text(stringResource(R.string.pair_step_scan), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.connection_transport_hint), style = MaterialTheme.typography.bodyMedium)
            onScanQr?.let { scan ->
                Button(onClick = scan, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                    Text(stringResource(R.string.link_scan_qr))
                }
            }
        }
        TextButton(onClick = { manual = !manual }, enabled = !busy) { Text(stringResource(R.string.link_manual)) }
        AnimatedVisibility(manual || onScanQr == null) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(model.input, model::edit, enabled = !busy, label = { Text(stringResource(R.string.link_input)) },
                    modifier = Modifier.fillMaxWidth(), maxLines = 4)
                Button(model::connect, enabled = !busy && model.input.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.link_connect))
                }
            }
        }
    }

    if (model.status == LinkStatus.WAITING) {
        KvietaPanel {
            Text(stringResource(if (model.code.isBlank()) R.string.relay_connecting_title else R.string.link_waiting), style = MaterialTheme.typography.titleLarge)
            if (model.code.isNotBlank()) {
                Text(model.code.chunked(4).joinToString(" "), style = MaterialTheme.typography.headlineMedium)
                Text(stringResource(R.string.link_compare))
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(stringResource(R.string.relay_waiting_summary))
            }
            OutlinedButton(onClick = model::forget) { Text(stringResource(R.string.link_cancel)) }
        }
    } else if (model.snapshot != null) {
        if (model.status != LinkStatus.CONNECTED) {
            onScanQr?.let { scan ->
                OutlinedButton(onClick = scan, enabled = model.decisionStatus != DecisionStatus.SENDING,
                    modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.qr_reconnect)) }
            }
        }
        val context = androidx.compose.ui.platform.LocalContext.current
        val activity = context as? android.app.Activity
        val authTitleAction = stringResource(R.string.biometric_prompt_remote_action)
        val authTitleTime = stringResource(R.string.biometric_prompt_grant_time)
        val authTitleRule = stringResource(R.string.biometric_prompt_save_rule)
        val authSubtitle = stringResource(R.string.biometric_prompt_subtitle)

        val safeSessionAction: (String) -> Unit = { cmd ->
            if (activity != null && com.kvieta.companion.security.BiometricHelper.isDeviceSecure(activity)) {
                com.kvieta.companion.security.BiometricHelper.authenticate(
                    activity = activity,
                    title = authTitleAction,
                    subtitle = authSubtitle,
                    onSuccess = { model.submitSessionAction(cmd) }
                )
            } else {
                model.submitSessionAction(cmd)
            }
        }

        val safeDecision: (TimeRequest, Boolean, Int?) -> Unit = { req, approve, minutes ->
            if (activity != null && com.kvieta.companion.security.BiometricHelper.isDeviceSecure(activity)) {
                com.kvieta.companion.security.BiometricHelper.authenticate(
                    activity = activity,
                    title = authTitleTime,
                    subtitle = authSubtitle,
                    onSuccess = { model.decide(req, approve, minutes) }
                )
            } else {
                model.decide(req, approve, minutes)
            }
        }

        val safeAppRuleSave: (String, String, Int) -> Unit = { name, modeStr, limit ->
            if (activity != null && com.kvieta.companion.security.BiometricHelper.isDeviceSecure(activity)) {
                com.kvieta.companion.security.BiometricHelper.authenticate(
                    activity = activity,
                    title = authTitleRule,
                    subtitle = authSubtitle,
                    onSuccess = { model.submitAppRule(name, modeStr, limit) }
                )
            } else {
                model.submitAppRule(name, modeStr, limit)
            }
        }

        DashboardContent(data = model.snapshot!!, connected = model.status == LinkStatus.CONNECTED,
            refreshing = model.refreshing, now = now, canDecide = model.remoteAvailable,
            decisionStatus = model.decisionStatus, onRefresh = model::refresh,
            onConnection = { settings = true }, onDecision = safeDecision,
            planSaving = model.planSaving, planSaved = model.planSaved, onPlanSave = model::submitPlan,
            remoteActionExecuting = model.remoteActionExecuting, remoteActionMessage = model.remoteActionMessage,
            onSessionAction = safeSessionAction, ruleSaving = model.ruleSaving,
            onAppRuleSave = safeAppRuleSave)
    } else if (model.status != LinkStatus.IDLE) {
        KvietaPanel {
            if (busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            if (model.pairingIssue != null) {
                Text(stringResource(when (model.pairingIssue) {
                    PairingFailure.NETWORK -> R.string.pair_network_error
                    PairingFailure.CERTIFICATE -> R.string.pair_certificate_error
                    PairingFailure.REJECTED -> R.string.link_rejected
                    else -> R.string.pair_other_error
                }), color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (model.pairingAddress.isNotEmpty()) Text(stringResource(R.string.pair_address, model.pairingAddress),
                    style = MaterialTheme.typography.bodySmall)
            } else Text(stringResource(when (model.status) {
                LinkStatus.REJECTED -> R.string.link_rejected
                LinkStatus.INVALID -> R.string.link_invalid
                LinkStatus.OFFLINE -> R.string.link_offline
                else -> R.string.link_loading
            }), color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (model.saved) {
                onScanQr?.let { scan ->
                    Button(onClick = scan, enabled = model.decisionStatus != DecisionStatus.SENDING,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(stringResource(R.string.qr_reconnect)) }
                    Text(stringResource(R.string.qr_reconnect_hint), style = MaterialTheme.typography.bodySmall)
                }
                if (model.status != LinkStatus.REJECTED) OutlinedButton(model::refresh, enabled = !busy) { Text(stringResource(R.string.link_refresh)) }
                TextButton(onClick = { settings = true }) { Text(stringResource(R.string.connection_settings)) }
            }
        }
    }

    if (settings) AlertDialog(onDismissRequest = { settings = false },
        title = { Text(stringResource(R.string.connection_settings)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(model.snapshot?.deviceName ?: stringResource(R.string.link_computer), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(if (model.remoteEnabled) R.string.remote_ready else R.string.remote_setup_hint))
                if (model.snapshot?.mode == "Family") {
                    Text(stringResource(if (notificationsEnabled) R.string.notifications_on else R.string.notifications_off),
                        style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(R.string.notification_timing), style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { onEnableNotifications?.invoke() }) {
                        Text(stringResource(if (notificationsEnabled) R.string.notification_settings else R.string.request_enable_notifications))
                    }
                }
                HorizontalDivider()
                onScanQr?.let { scan ->
                    TextButton(onClick = { settings = false; scan() }, enabled = model.decisionStatus != DecisionStatus.SENDING) {
                        Text(stringResource(R.string.qr_reconnect))
                    }
                }
                TextButton(onClick = { confirmForget = true }, enabled = model.decisionStatus != DecisionStatus.SENDING) {
                    Text(stringResource(R.string.link_forget), color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = { TextButton(onClick = { settings = false }) { Text(stringResource(R.string.understood)) } })
    if (confirmForget) AlertDialog(onDismissRequest = { confirmForget = false },
        title = { Text(stringResource(R.string.link_forget)) }, text = { Text(stringResource(R.string.link_forget_hint)) },
        confirmButton = { TextButton(onClick = { model.forget(); confirmForget = false; settings = false }) { Text(stringResource(R.string.link_forget)) } },
        dismissButton = { TextButton(onClick = { confirmForget = false }) { Text(stringResource(R.string.link_cancel)) } })
    if (model.replacementInvite != null) AlertDialog(onDismissRequest = model::cancelReplacement,
        title = { Text(stringResource(R.string.qr_replace_title)) },
        text = { Text(stringResource(R.string.qr_replace_body)) },
        confirmButton = { TextButton(onClick = model::confirmReplacement) { Text(stringResource(R.string.link_connect)) } },
        dismissButton = { TextButton(onClick = model::cancelReplacement) { Text(stringResource(R.string.link_cancel)) } })
}

@Composable
fun DashboardContent(data: DesktopSnapshot, connected: Boolean, refreshing: Boolean, now: Instant,
    canDecide: Boolean, decisionStatus: DecisionStatus, onRefresh: () -> Unit, onConnection: () -> Unit,
    planSaving: Boolean = false, planSaved: Boolean = false, onPlanSave: (List<PlanDay>) -> Unit = {},
    remoteActionExecuting: Boolean = false, remoteActionMessage: String? = null,
    onSessionAction: (String) -> Unit = {}, ruleSaving: Boolean = false,
    onAppRuleSave: (String, String, Int) -> Unit = { _, _, _ -> },
    onDecision: (TimeRequest, Boolean, Int?) -> Unit) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var details by rememberSaveable { mutableStateOf(false) }
    var selectedAppForRule by remember { mutableStateOf<DesktopUsage?>(null) }
    var planDraft by remember(data.schedule) { mutableStateOf(data.schedule) }
    BackHandler(details) { details = false }
    val fresh = connected && !data.isStale(now)
    val family = data.mode == "Family"

    if (selectedAppForRule != null) {
        val app = selectedAppForRule!!
        AppRuleDialog(
            appName = app.name,
            initialMode = app.mode,
            initialLimitMinutes = app.limitMinutes ?: 45,
            saving = ruleSaving,
            onDismiss = { selectedAppForRule = null },
            onSave = { mode, minutes ->
                onAppRuleSave(app.name, mode, minutes)
                selectedAppForRule = null
            }
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(if (family) R.string.dashboard_family else R.string.dashboard_today), style = MaterialTheme.typography.headlineLarge)
                Text(data.deviceName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            TextButton(onClick = onConnection) { Text(stringResource(R.string.connection_settings)) }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(if (fresh) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline))
            Text(stringResource(if (fresh) R.string.snapshot_current else R.string.link_old_data),
                modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = onRefresh, enabled = !refreshing && decisionStatus != DecisionStatus.SENDING) {
                Text(stringResource(if (refreshing) R.string.refreshing_short else R.string.refresh_short))
            }
        }

        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color.Transparent,
            divider = { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)) }
        ) {
            val tabTitles = listOf(
                stringResource(R.string.tab_today),
                stringResource(R.string.tab_apps),
                stringResource(R.string.tab_reports),
                stringResource(R.string.tab_schedule)
            )
            tabTitles.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = {
                        Text(
                            title,
                            fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                )
            }
        }

        when (selectedTab) {
            0 -> {
                val request = data.timeRequest
                if (family && request != null && request.status !in setOf("Applied", "Rejected", "Expired")) {
                    RequestCard(request, canDecide && connected, decisionStatus, now, onDecision)
                }

                RemoteControlsCard(
                    data = data,
                    connected = connected && (canDecide || family),
                    executing = remoteActionExecuting,
                    message = remoteActionMessage,
                    onAction = onSessionAction
                )

                SummaryCard(data, fresh)

                if (family && request?.status in setOf("Applied", "Rejected", "Expired")) {
                    RequestCard(request!!, canDecide, decisionStatus, now, onDecision)
                }

                KvietaPanel {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.usage_title), style = MaterialTheme.typography.titleLarge)
                            Text(stringResource(R.string.usage_description), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = { details = !details }) { Text(stringResource(if (details) R.string.close_details else R.string.open_details)) }
                    }
                    data.apps.take(if (details) data.apps.size else 3).forEachIndexed { index, app ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(14.dp), modifier = Modifier.size(44.dp)) {
                                Box(contentAlignment = Alignment.Center) { Text(app.name.take(1).uppercase(Locale.getDefault()), fontWeight = FontWeight.SemiBold) }
                            }
                            Column(Modifier.weight(1f)) {
                                Text(app.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                                LinearProgressIndicator(
                                    progress = { (app.seconds.toFloat() / data.usedSeconds.coerceAtLeast(1)).coerceIn(0f, 1f) },
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(4.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.outlineVariant,
                                )
                                if (!details && index == 0) Text(stringResource(R.string.most_used), style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(usageDuration(app.seconds), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                    if (data.apps.isEmpty()) Text(stringResource(R.string.link_empty), style = MaterialTheme.typography.bodyMedium)
                    AnimatedVisibility(details) {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            if (data.weeklyUsage.isNotEmpty()) {
                                HorizontalDivider()
                                Text(stringResource(R.string.week_title), style = MaterialTheme.typography.titleMedium)
                                Text(usageDuration(data.weeklyUsage.sumOf { it.seconds }), style = MaterialTheme.typography.headlineSmall)
                                WeekChart(data.weeklyUsage)
                            }
                            Text(stringResource(R.string.measurement_note), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            1 -> {
                KvietaPanel {
                    Column(Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.app_rules_title), style = MaterialTheme.typography.titleLarge)
                        Text(stringResource(R.string.app_rules_desc), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    if (data.apps.isEmpty()) {
                        Text(stringResource(R.string.link_empty), style = MaterialTheme.typography.bodyMedium)
                    }

                    data.apps.forEach { app ->
                        Surface(
                            onClick = { if (connected && (canDecide || family)) selectedAppForRule = app },
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Surface(
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(app.name.take(1).uppercase(Locale.getDefault()), fontWeight = FontWeight.SemiBold)
                                    }
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(app.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                    Text(usageDuration(app.seconds), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }

                                val chipColor = when (app.mode) {
                                    "Blocked" -> MaterialTheme.colorScheme.error
                                    "Limited" -> Color(0xFFFFA000)
                                    else -> MaterialTheme.colorScheme.outline
                                }
                                val chipText = when (app.mode) {
                                    "Blocked" -> stringResource(R.string.app_rule_blocked)
                                    "Limited" -> stringResource(R.string.app_rule_limited, app.limitMinutes ?: 0)
                                    else -> stringResource(R.string.app_rule_unlimited)
                                }
                                Surface(
                                    color = chipColor.copy(alpha = 0.12f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        chipText,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = chipColor,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }
            }
            2 -> {
                KvietaPanel {
                    Text(stringResource(R.string.timeline_24h_title), style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(R.string.timeline_24h_desc), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    DayTimelineChart(data.hourlyUsage)
                }

                if (data.weeklyUsage.isNotEmpty()) {
                    KvietaPanel {
                        Text(stringResource(R.string.week_title), style = MaterialTheme.typography.titleLarge)
                        val totalWeeklySeconds = data.weeklyUsage.sumOf { it.seconds }
                        val avgDailySeconds = totalWeeklySeconds / data.weeklyUsage.size.coerceAtLeast(1)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                            Text(usageDuration(totalWeeklySeconds), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                            Text(stringResource(R.string.avg_daily_usage, usageDuration(avgDailySeconds)),
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.height(8.dp))
                        WeekChart(data.weeklyUsage)
                    }
                }
            }
            3 -> {
                if (data.schedule.isNotEmpty()) {
                    KvietaPanel {
                        Text(stringResource(R.string.plan_title), style = MaterialTheme.typography.titleLarge)
                        Text(stringResource(R.string.plan_description), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        PlanEditor(planDraft, planSaving, planSaved, onChange = { planDraft = it }, onSave = onPlanSave)
                    }
                } else {
                    KvietaPanel {
                        Text(stringResource(R.string.plan_title), style = MaterialTheme.typography.titleLarge)
                        Text(stringResource(R.string.link_empty), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        Text(stringResource(R.string.link_observed, formatInstant(data.observedAt)), style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PlanEditor(
    schedule: List<PlanDay>,
    saving: Boolean,
    saved: Boolean,
    onChange: (List<PlanDay>) -> Unit,
    onSave: (List<PlanDay>) -> Unit
) {
    if (schedule.isEmpty()) return

    var selectedDayIndex by rememberSaveable { mutableIntStateOf(0) }
    val day = schedule.getOrElse(selectedDayIndex) { schedule.first() }

    val dayLabels = listOf("Pzt", "Sal", "Çar", "Per", "Cum", "Cmt", "Paz")
    val fullDayNames = listOf("Pazartesi", "Salı", "Çarşamba", "Perşembe", "Cuma", "Cumartesi", "Pazar")

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // 1. Day Selector Tab Bar / Chips
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            schedule.forEachIndexed { index, item ->
                val isSelected = selectedDayIndex == index
                val label = dayLabels.getOrElse(index) { item.day.take(3) }
                Surface(
                    onClick = { selectedDayIndex = index },
                    modifier = Modifier.weight(1f).height(46.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primary else if (item.enabled) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = if (isSelected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                        )
                        Box(
                            Modifier.size(4.dp).clip(CircleShape).background(
                                if (isSelected) MaterialTheme.colorScheme.onPrimary
                                else if (item.enabled) Color(0xFF4CAF50)
                                else Color.Transparent
                            )
                        )
                    }
                }
            }
        }

        // 2. Active Day Detail Card
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Header with day name and enabled switch
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            fullDayNames.getOrElse(selectedDayIndex) { day.day },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            if (day.enabled) "Kural açık · Kullanım süresi sınırlı" else "Kural kapalı · Serbest kullanım",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = day.enabled,
                        onCheckedChange = { value ->
                            onChange(schedule.toMutableList().also { it[selectedDayIndex] = day.copy(enabled = value) })
                        }
                    )
                }

                if (day.enabled) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // Daily limit presets & input
                    Text("Günlük Süre Limiti", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(60 to "1 Saat", 120 to "2 Saat", 180 to "3 Saat", 240 to "4 Saat").forEach { (mins, text) ->
                            val isPreset = day.dailyLimitMinutes == mins
                            Surface(
                                onClick = {
                                    onChange(schedule.toMutableList().also { it[selectedDayIndex] = day.copy(dailyLimitMinutes = mins) })
                                },
                                shape = RoundedCornerShape(10.dp),
                                color = if (isPreset) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, if (isPreset) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.weight(1f).height(38.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (isPreset) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isPreset) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = day.dailyLimitMinutes.toString(),
                        onValueChange = { value ->
                            val minutes = value.filter(Char::isDigit).take(4).toIntOrNull() ?: 0
                            onChange(schedule.toMutableList().also { it[selectedDayIndex] = day.copy(dailyLimitMinutes = minutes.coerceIn(0, 1440)) })
                        },
                        label = { Text("Özel Limit (Dakika)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )

                    // Time range
                    Text("İzin Verilen Saat Aralığı", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = day.from,
                            onValueChange = { value -> onChange(schedule.toMutableList().also { it[selectedDayIndex] = day.copy(from = value.take(5)) }) },
                            label = { Text(stringResource(R.string.plan_from)) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = day.until,
                            onValueChange = { value -> onChange(schedule.toMutableList().also { it[selectedDayIndex] = day.copy(until = value.take(5)) }) },
                            label = { Text(stringResource(R.string.plan_until)) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Quick Copy Helpers
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                val updated = schedule.map { it.copy(enabled = day.enabled, from = day.from, until = day.until, dailyLimitMinutes = day.dailyLimitMinutes) }
                                onChange(updated)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("⚡ Tüm Günlere Kopyala", style = MaterialTheme.typography.labelSmall)
                        }

                        OutlinedButton(
                            onClick = {
                                val updated = schedule.mapIndexed { idx, item ->
                                    if (idx in 0..4) item.copy(enabled = day.enabled, from = day.from, until = day.until, dailyLimitMinutes = day.dailyLimitMinutes)
                                    else item
                                }
                                onChange(updated)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Pzt-Cum Uygula", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }

        // 3. Save Button
        Button(
            onClick = { onSave(schedule) },
            enabled = !saving,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text(
                stringResource(if (saving) R.string.plan_saving else if (saved) R.string.plan_saved else R.string.plan_save),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleSmall
            )
        }

        Text(
            stringResource(R.string.plan_safety),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun RemoteControlsCard(
    data: DesktopSnapshot,
    connected: Boolean,
    executing: Boolean,
    message: String?,
    onAction: (String) -> Unit
) {
    var showLockConfirm by remember { mutableStateOf(false) }

    if (showLockConfirm) {
        AlertDialog(
            onDismissRequest = { showLockConfirm = false },
            title = { Text(stringResource(R.string.remote_lock_now), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.remote_lock_confirm)) },
            confirmButton = {
                Button(
                    onClick = {
                        showLockConfirm = false
                        onAction("lock")
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.remote_lock_now), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLockConfirm = false }) {
                    Text(stringResource(R.string.link_cancel))
                }
            }
        )
    }

    KvietaPanel {
        Text(stringResource(R.string.remote_controls_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            stringResource(R.string.remote_controls_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Prominent High-Contrast Status Banner
        val (bannerBg, bannerFg, bannerTitle, bannerDesc) = when {
            data.isRemotelyLocked -> Quad(
                MaterialTheme.colorScheme.errorContainer,
                MaterialTheme.colorScheme.onErrorContainer,
                "🔒 Bilgisayar Kilitlendi",
                "Ekran kilitlendi ve karartıldı. PIN veya bu menüden açılabilir."
            )
            data.sessionState == "Paused" -> Quad(
                Color(0xFFFFF3E0),
                Color(0xFFBF360C),
                "☕ Oturuma Mola Verildi",
                "Kullanım süresi sayacı duraklatıldı."
            )
            data.sessionState == "Active" -> Quad(
                Color(0xFFE8F5E9),
                Color(0xFF1B5E20),
                "🟢 Bilgisayar Aktif Kullanılıyor",
                "Oturum açık ve ekran kullanım kuralları devrede."
            )
            else -> Quad(
                MaterialTheme.colorScheme.surfaceVariant,
                MaterialTheme.colorScheme.onSurfaceVariant,
                "🖥️ Oturum Hazır",
                "Bilgisayar bağlı."
            )
        }

        Surface(
            color = bannerBg,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(14.dp)) {
                Text(bannerTitle, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = bannerFg)
                Text(bannerDesc, style = MaterialTheme.typography.bodySmall, color = bannerFg.copy(alpha = 0.85f))
            }
        }

        if (executing) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(4.dp))
            Text(
                stringResource(R.string.remote_action_executing),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
        }

        if (!message.isNullOrBlank()) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    message,
                    modifier = Modifier.padding(10.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // Action Controls - Spacious, clearly readable, never truncated
        Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            if (data.isRemotelyLocked) {
                // When locked: Prominent unlock/resume button
                Button(
                    onClick = { onAction("resume") },
                    enabled = connected && !executing,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                ) {
                    Text("▶️ Kilidi Aç ve Devam Ettir", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, color = Color.White)
                }
            } else if (data.sessionState == "Paused") {
                // When paused: Resume or Lock
                Button(
                    onClick = { onAction("resume") },
                    enabled = connected && !executing,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                ) {
                    Text("▶️ Molayı Bitir ve Devam Et", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, color = Color.White)
                }

                OutlinedButton(
                    onClick = { showLockConfirm = true },
                    enabled = connected && !executing,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("🔒 Bilgisayarı Kilitle", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                // When active: Lock and Pause
                Button(
                    onClick = { showLockConfirm = true },
                    enabled = connected && !executing,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("🔒 Bilgisayarı Hemen Kilitle", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onError)
                }

                OutlinedButton(
                    onClick = { onAction("pause") },
                    enabled = connected && !executing,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("☕ 15 Dakika Mola Ver (Duraklat)", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

@Composable
private fun AppRuleDialog(
    appName: String,
    initialMode: String,
    initialLimitMinutes: Int,
    saving: Boolean,
    onDismiss: () -> Unit,
    onSave: (mode: String, limitMinutes: Int) -> Unit
) {
    var mode by remember { mutableStateOf(initialMode) }
    var limitMinutesText by remember { mutableStateOf(if (initialLimitMinutes > 0) initialLimitMinutes.toString() else "45") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_rule_title, appName)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    RadioButton(selected = mode == "Unlimited", onClick = { mode = "Unlimited" })
                    Text(stringResource(R.string.rule_mode_unlimited), modifier = Modifier.padding(start = 8.dp))
                }

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    RadioButton(selected = mode == "Limited", onClick = { mode = "Limited" })
                    Text(stringResource(R.string.rule_mode_limited), modifier = Modifier.padding(start = 8.dp))
                }

                AnimatedVisibility(visible = mode == "Limited") {
                    Column(Modifier.padding(start = 32.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = limitMinutesText,
                            onValueChange = { limitMinutesText = it.filter(Char::isDigit).take(4) },
                            label = { Text(stringResource(R.string.rule_limit_label)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(15, 30, 45, 60).forEach { mins ->
                                FilterChip(
                                    selected = limitMinutesText == mins.toString(),
                                    onClick = { limitMinutesText = mins.toString() },
                                    label = { Text("$mins dk") }
                                )
                            }
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    RadioButton(selected = mode == "Blocked", onClick = { mode = "Blocked" })
                    Text(stringResource(R.string.rule_mode_blocked), modifier = Modifier.padding(start = 8.dp), color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val minutes = limitMinutesText.toIntOrNull() ?: 45
                    onSave(mode, minutes)
                },
                enabled = !saving
            ) {
                Text(stringResource(if (saving) R.string.rule_saving else R.string.rule_apply))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.link_cancel))
            }
        }
    )
}

@Composable
private fun DayTimelineChart(hourlyUsage: List<HourlyUsage>) {
    val full24 = if (hourlyUsage.isEmpty()) {
        List(24) { HourlyUsage(it, 0L) }
    } else {
        val map = hourlyUsage.associateBy { it.hour }
        List(24) { map[it] ?: HourlyUsage(it, 0L) }
    }
    val maxSeconds = full24.maxOfOrNull { it.seconds }?.coerceAtLeast(60L) ?: 3600L

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.fillMaxWidth().height(100.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            full24.forEach { bucket ->
                val fraction = (bucket.seconds.toFloat() / maxSeconds).coerceIn(0.04f, 1f)
                val isPeak = bucket.seconds > 0 && bucket.seconds == full24.maxOf { it.seconds }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(fraction)
                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                        .background(if (isPeak) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = if (bucket.seconds > 0) 0.65f else 0.15f))
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("00:00", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("06:00", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("12:00", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("18:00", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("23:59", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SummaryCard(data: DesktopSnapshot, fresh: Boolean) {
    val family = data.mode == "Family" && data.remainingSeconds != null
    Surface(color = Color(0xFF34432D), contentColor = Color(0xFFF4F3E7), shape = RoundedCornerShape(32.dp)) {
        Column(Modifier.fillMaxWidth()
            .background(Brush.linearGradient(listOf(Color(0xFF283B31), Color(0xFF52643B))))
            .padding(24.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(if (family) R.string.remaining else R.string.today_usage), modifier = Modifier.weight(1f).padding(end = 8.dp), style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFFDAE2C1))
                Text(formatDay(data.localDay, "d MMM"), style = MaterialTheme.typography.labelMedium, color = Color(0xFFDAE2C1))
            }
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val showRing = maxWidth >= 260.dp && LocalDensity.current.fontScale <= 1.2f
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(usageDuration(if (family) data.remainingSeconds!! else data.usedSeconds),
                            fontSize = 40.sp, lineHeight = 46.sp, letterSpacing = (-1).sp, fontWeight = FontWeight.SemiBold)
                        val secondary = if (family) data.sessionUsedSeconds else data.remainingSeconds
                        secondary?.let {
                            Text(stringResource(if (family) R.string.used_secondary else R.string.remaining_secondary, usageDuration(it)),
                                style = MaterialTheme.typography.bodyMedium, color = Color(0xFFDAE2C1))
                        }
                    }
                    if (showRing) UsageRing(data, Modifier.size(96.dp))
                }
            }
            HorizontalDivider(color = Color.White.copy(alpha = .16f))
            Text(stringResource(when (data.sessionState) {
                "Active" -> R.string.state_active_short
                "Paused" -> R.string.state_paused_short
                "Ready" -> R.string.state_ready_short
                "TimeExpired" -> R.string.state_expired_short
                "OutsideSchedule" -> R.string.state_outside_short
                else -> R.string.link_state_unknown
            }), style = MaterialTheme.typography.bodyMedium)
            if (!fresh) Text(stringResource(R.string.offline_summary_hint), style = MaterialTheme.typography.labelMedium, color = Color(0xFFDAE2C1))
            data.focusRemainingSeconds?.let { Text(stringResource(R.string.focus_remaining, usageDuration(it)), style = MaterialTheme.typography.labelLarge) }
        }
    }
}

@Composable
private fun UsageRing(data: DesktopSnapshot, modifier: Modifier) {
    val total = data.usedSeconds.coerceAtLeast(1)
    val colors = listOf(Color(0xFFDCE5B7), Color(0xFFBFC982), Color(0xFF8FA870))
    val description = stringResource(if (data.mode == "Family" && data.remainingSeconds != null)
        R.string.remaining_ring_description else R.string.usage_ring_description)
    Canvas(modifier.semantics { contentDescription = description }) {
        val stroke = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Butt)
        val inset = 6.dp.toPx()
        val arcSize = androidx.compose.ui.geometry.Size(size.width - 2 * inset, size.height - 2 * inset)
        val top = androidx.compose.ui.geometry.Offset(inset, inset)
        drawArc(Color.White.copy(alpha = .15f), 0f, 360f, false, top, arcSize, style = stroke)
        var start = -90f
        val portions = if (data.mode == "Family" && data.remainingSeconds != null) {
            val remaining = data.remainingSeconds.coerceAtLeast(0)
            val budget = (remaining + (data.sessionUsedSeconds ?: 0).coerceAtLeast(0)).coerceAtLeast(1)
            listOf(remaining.toFloat() / budget)
        } else data.apps.map { it.seconds.toFloat() / total }
        portions.forEachIndexed { index, portion ->
            val sweep = (portion * 360).coerceIn(0f, (360f - (start + 90)).coerceAtLeast(0f))
            if (sweep > 1) drawArc(colors[index % colors.size], start, (sweep - 3).coerceAtLeast(1f), false, top, arcSize, style = stroke)
            start += sweep
        }
    }
}

@Composable
private fun WeekChart(days: List<DesktopDay>) {
    val max = days.maxOfOrNull { it.seconds }?.coerceAtLeast(1) ?: 1L
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        days.forEach { day ->
            val description = formatDay(day.day, "EEEE d MMM") + ": " + usageDuration(day.seconds)
            Column(Modifier.weight(1f).semantics(mergeDescendants = true) { contentDescription = description },
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.fillMaxWidth().height(84.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.BottomCenter) {
                    Box(Modifier.fillMaxWidth().fillMaxHeight((day.seconds.toFloat() / max).coerceIn(0f, 1f))
                        .clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.primary))
                }
                Text(formatDay(day.day, "EE").take(2), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun RequestCard(request: TimeRequest, canDecide: Boolean, decisionStatus: DecisionStatus, now: Instant,
    onDecision: (TimeRequest, Boolean, Int?) -> Unit) {
    var selected by rememberSaveable(request.id) { mutableIntStateOf(request.requestedMinutes) }
    var custom by rememberSaveable(request.id) { mutableStateOf(false) }
    var customText by rememberSaveable(request.id) { mutableStateOf(request.requestedMinutes.toString()) }
    val pending = request.isPending(now)
    val expired = request.status == "Pending" && !pending
    KvietaPanel {
        Text(stringResource(R.string.request_eyebrow), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text(stringResource(R.string.request_live_title, request.requestedMinutes), style = MaterialTheme.typography.titleLarge)
        if (request.note.isNotBlank()) Text("“" + request.note + "”", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(when {
            expired -> R.string.request_live_expired
            request.status == "ApprovalSent" -> R.string.request_approval_sent
            request.status == "RejectionSent" -> R.string.request_rejection_sent
            request.status in setOf("ApprovedAwaitingDevice", "Applying") -> R.string.request_live_waiting_pc
            request.status == "Applied" -> R.string.request_live_applied
            request.status == "Rejected" -> R.string.request_live_rejected
            request.status == "Pending" -> R.string.request_live_pending
            else -> R.string.request_live_expired
        }), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (pending) {
            Text(stringResource(R.string.request_expires, formatInstant(request.expiresAt)), style = MaterialTheme.typography.labelSmall)
            if (!canDecide) Text(stringResource(R.string.request_live_offline), color = MaterialTheme.colorScheme.error)
            else {
                val sending = decisionStatus == DecisionStatus.SENDING
                val retry = decisionStatus == DecisionStatus.FAILED
                if (!retry) {
                    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(15, 30, 60).forEach { minutes ->
                            FilterChip(selected = !custom && selected == minutes, onClick = { selected = minutes; custom = false },
                                enabled = !sending, label = { Text(stringResource(R.string.request_minutes, minutes)) })
                        }
                        FilterChip(selected = custom, onClick = { custom = !custom }, enabled = !sending,
                            label = { Text(stringResource(R.string.request_custom)) })
                    }
                    AnimatedVisibility(custom) {
                        OutlinedTextField(customText, { customText = it.filter(Char::isDigit).take(3) }, modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.request_custom)) }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            supportingText = { Text(stringResource(R.string.request_range)) })
                    }
                }
                val duration = if (custom) customText.toIntOrNull() else selected
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onDecision(request, true, duration) },
                    enabled = !sending && (retry || duration?.let { it in 1..180 } == true),
                    modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(if (retry) R.string.retry_decision else if (sending) R.string.request_sending else R.string.approve_minutes, duration ?: 0))
                }
                if (!retry) TextButton(onClick = { onDecision(request, false, null) }, enabled = !sending,
                    modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.request_reject)) }
                }
                if (retry) Text(stringResource(R.string.request_failed), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
fun KvietaPanel(content: @Composable ColumnScope.() -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
fun usageDuration(seconds: Long): String {
    if (seconds in 1..59) return stringResource(R.string.less_than_minute)
    val minutes = (seconds.coerceAtLeast(0) / 60).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    return if (minutes >= 60) stringResource(R.string.hours_minutes, minutes / 60, minutes % 60)
    else stringResource(R.string.minutes, minutes)
}

private fun formatInstant(value: String): String = runCatching {
    Instant.parse(value).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM · HH:mm", Locale.getDefault()))
}.getOrDefault("—")

private fun formatDay(value: String, pattern: String): String = runCatching {
    LocalDate.parse(value).format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))
}.getOrDefault(value)
