package com.autoflow.app.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.autoflow.app.data.Rule
import com.autoflow.app.data.label
import com.autoflow.app.ui.AutoFlowViewModel
import com.autoflow.app.ui.components.EmptyState
import com.autoflow.app.ui.components.Glyphs
import com.autoflow.app.ui.components.IconBadge
import com.autoflow.app.ui.components.SectionCard
import com.autoflow.app.ui.components.StatTile
import com.autoflow.app.ui.components.TagPill
import com.autoflow.app.ui.theme.AppColors
import com.autoflow.app.ui.theme.LocalTokens
import com.autoflow.app.trigger.CollapsedNotice
import com.autoflow.app.util.AppInfo
import com.autoflow.app.util.Permissions

@Composable
fun HomeScreen(
    viewModel: AutoFlowViewModel,
    onAddRule: () -> Unit,
    onEditRule: (Rule) -> Unit,
    onOpenLogs: () -> Unit,
    onOpenTemplates: () -> Unit,
) {
    val rules by viewModel.rules.collectAsStateWithLifecycle()
    val logs by viewModel.logs.collectAsStateWithLifecycle()
    val setup by viewModel.setup.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var pendingDelete by remember { mutableStateOf<Rule?>(null) }

    val activeCount = rules.count { it.enabled }
    val totalRuns = rules.sumOf { it.runCount }
    val failures = logs.count { !it.success }
    val health = if (logs.isEmpty()) 100 else ((logs.size - failures) * 100) / logs.size

    // Active rules first: a paused rule is reference material, an active one is live state.
    val ordered = remember(rules) { rules.sortedByDescending { it.enabled } }
    val collapsed by CollapsedNotice.collapsed.collectAsStateWithLifecycle()

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddRule,
                containerColor = AppColors.IndigoPrimary,
                contentColor = Color.White,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("New rule", fontWeight = FontWeight.SemiBold) },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Header() }

            item {
                EngineCard(
                    activeCount = activeCount,
                    totalRules = rules.size,
                    totalRuns = totalRuns,
                    health = health,
                )
            }

            // Only occupies space while something still needs fixing.
            item {
                AnimatedVisibility(visible = !setup.ready || !setup.batteryUnrestricted) {
                    PermissionCard(
                        accessibility = setup.accessibility,
                        notifications = setup.notificationAccess,
                        battery = setup.batteryUnrestricted,
                        onFixAccessibility = { Permissions.openAccessibilitySettings(context) },
                        onFixNotifications = { Permissions.openNotificationAccessSettings(context) },
                        onFixBattery = { Permissions.openBatterySettings(context) },
                    )
                }
            }

            // A group summary carries no sender, so per-sender rules silently cannot fire.
            // Say so rather than letting the user think the app is broken.
            if (collapsed.isNotEmpty()) {
                item {
                    SectionCard(accent = AppColors.AmberWarning) {
                        Row(
                            Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconBadge(Icons.Default.Warning, AppColors.AmberWarning, size = 34)
                            Spacer(Modifier.width(11.dp))
                            Column {
                                Text(
                                    "Notifications are being collapsed",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    collapsed.keys.joinToString { AppInfo.label(context, it) } +
                                        " is grouping messages into one summary because of a " +
                                        "large unread backlog. That summary has no sender or " +
                                        "text, so per-sender rules cannot match. Clear the " +
                                        "unread messages to restore them.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            if (rules.isEmpty()) {
                item {
                    SectionCard {
                        EmptyState(
                            icon = Icons.Default.Bolt,
                            title = "No automations yet",
                            message = "Build a flow from scratch, or start from a ready-made " +
                                "template for Telegram, WhatsApp, X, Instagram or YouTube.",
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedButton(
                                    onClick = onOpenTemplates,
                                    shape = RoundedCornerShape(12.dp),
                                ) {
                                    Icon(Icons.Default.Widgets, null, Modifier.size(17.dp))
                                    Spacer(Modifier.width(7.dp))
                                    Text("Templates")
                                }
                                Button(
                                    onClick = onAddRule,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = AppColors.IndigoPrimary,
                                    ),
                                ) {
                                    Icon(Icons.Default.Add, null, Modifier.size(17.dp))
                                    Spacer(Modifier.width(7.dp))
                                    Text("Build one")
                                }
                            }
                        }
                    }
                }
            } else {
                item {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Your automations",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "$activeCount of ${rules.size} active",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                items(ordered, key = { it.id }) { rule ->
                    RuleCard(
                        rule = rule,
                        onToggle = { viewModel.setEnabled(rule, it) },
                        onRunNow = { viewModel.runNow(rule) },
                        onDelete = { pendingDelete = rule },
                        onClick = { onEditRule(rule) },
                    )
                }
            }
        }
    }

    // Deleting a rule is irreversible, so it always asks first.
    pendingDelete?.let { rule ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            shape = RoundedCornerShape(22.dp),
            icon = { Icon(Icons.Default.Delete, null, tint = AppColors.RoseError) },
            title = { Text("Delete \"${rule.name}\"?") },
            text = { Text("This removes the rule and stops it running. It cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.delete(rule)
                        pendingDelete = null
                    },
                ) { Text("Delete", color = AppColors.RoseError, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Keep") }
            },
        )
    }
}

@Composable
private fun Header() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(AppColors.PrimaryGradient),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Bolt, null, tint = Color.White, modifier = Modifier.size(23.dp))
        }
        Spacer(Modifier.width(11.dp))
        Column {
            Text(
                "AutoFlow",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "Android automation",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EngineCard(
    activeCount: Int,
    totalRules: Int,
    totalRuns: Int,
    health: Int,
) {
    val tokens = LocalTokens.current
    val live = activeCount > 0

    SectionCard(accent = if (live) AppColors.GreenSuccess else null) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(if (live) AppColors.GreenSuccess else MaterialTheme.colorScheme.outline)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (live) "Engine running" else "Engine idle",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (live) AppColors.GreenSuccess else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TagPill(
                    if (live) "$activeCount armed" else "nothing armed",
                    if (live) AppColors.GreenSuccess else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(11.dp))

            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(tokens.subtleSurface)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                StatTile(
                    Icons.Default.Bolt, totalRules.toString(), "Rules",
                    AppColors.IndigoPrimary, Modifier.weight(1f),
                )
                StatTile(
                    Icons.Default.Speed, totalRuns.toString(), "Runs",
                    AppColors.VioletAccent, Modifier.weight(1f),
                )
                StatTile(
                    Icons.Default.HealthAndSafety, "$health%", "Success",
                    if (health >= 90) AppColors.GreenSuccess else AppColors.AmberWarning,
                    Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun PermissionCard(
    accessibility: Boolean,
    notifications: Boolean,
    battery: Boolean,
    onFixAccessibility: () -> Unit,
    onFixNotifications: () -> Unit,
    onFixBattery: () -> Unit,
) {
    val granted = listOf(accessibility, notifications, battery).count { it }

    SectionCard(accent = AppColors.AmberWarning) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(Icons.Default.Warning, AppColors.AmberWarning, size = 34)
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Finish setup",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "$granted of 3 permissions granted",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            PermissionRow(
                "Accessibility service",
                "Lets AutoFlow tap and type inside other apps",
                accessibility,
                onFixAccessibility,
            )
            PermissionRow(
                "Notification access",
                "Lets AutoFlow read incoming messages",
                notifications,
                onFixNotifications,
            )
            PermissionRow(
                "Unrestricted battery",
                "Stops Android killing the engine in the background",
                battery,
                onFixBattery,
            )
        }
    }
}

@Composable
private fun PermissionRow(
    label: String,
    detail: String,
    granted: Boolean,
    onFix: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (granted) Icons.Default.CheckCircle else Icons.Default.Warning,
            contentDescription = null,
            tint = if (granted) AppColors.GreenSuccess else AppColors.AmberWarning,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!granted) {
            TextButton(onClick = onFix) {
                Text("Enable", fontWeight = FontWeight.Bold)
            }
        }
    }
}

/**
 * A rule card reads as the rule itself: what fires it, what filters it, what it does —
 * so the list is scannable without opening anything.
 */
@Composable
private fun RuleCard(
    rule: Rule,
    onToggle: (Boolean) -> Unit,
    onRunNow: () -> Unit,
    onDelete: () -> Unit,
    onClick: () -> Unit,
) {
    val tokens = LocalTokens.current
    val triggerText = rule.trigger.label
    val accent = if (rule.enabled) AppColors.IndigoPrimary else MaterialTheme.colorScheme.outline

    SectionCard(onClick = onClick, accent = if (rule.enabled) accent else null) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(
                    Glyphs.ruleIcon(triggerText),
                    if (rule.enabled) AppColors.IndigoPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        rule.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        if (rule.runCount == 0) "Never run" else "Ran ${rule.runCount} times",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = rule.enabled,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = AppColors.IndigoPrimary,
                    ),
                )
            }

            Spacer(Modifier.height(11.dp))

            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(13.dp))
                    .background(tokens.subtleSurface)
                    .padding(horizontal = 11.dp, vertical = 9.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FlowLine("WHEN", triggerText, AppColors.StepWhen, Icons.Default.Bolt)

                if (rule.conditions.isNotEmpty()) {
                    FlowLine(
                        "ONLY IF",
                        "${rule.conditions.size} filter" +
                            if (rule.conditions.size == 1) "" else "s",
                        AppColors.StepIf,
                        Icons.Default.FilterAlt,
                    )
                }

                FlowLine(
                    "THEN",
                    rule.actions.firstOrNull()?.let { first ->
                        val extra = rule.actions.size - 1
                        first.label + if (extra > 0) "  +$extra more" else ""
                    } ?: "no steps yet",
                    AppColors.StepThen,
                    Icons.AutoMirrored.Filled.CallMade,
                )
            }

            Spacer(Modifier.height(4.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onRunNow) {
                    Icon(Icons.Default.PlayArrow, null, Modifier.size(17.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Run now", fontWeight = FontWeight.SemiBold)
                }
                IconButton(onClick = onClick) {
                    Icon(
                        Icons.Default.Edit, "Edit rule",
                        Modifier.size(19.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete, "Delete rule",
                        Modifier.size(19.dp),
                        tint = AppColors.RoseError,
                    )
                }
            }
        }
    }
}

@Composable
private fun FlowLine(
    stage: String,
    text: String,
    color: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = color, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            stage,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = color,
            modifier = Modifier.width(52.dp),
        )
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
