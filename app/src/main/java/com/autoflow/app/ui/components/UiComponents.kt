package com.autoflow.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.SmartButton
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.autoflow.app.data.Rule
import com.autoflow.app.data.TriggerSpec
import com.autoflow.app.data.label
import com.autoflow.app.ui.SetupState
import com.autoflow.app.ui.theme.AppColors

@Composable
fun AppBrandBadge(
    trigger: TriggerSpec,
    modifier: Modifier = Modifier,
) {
    val (icon, bgGradient, tint) = when (trigger) {
        is TriggerSpec.WhatsAppMessage -> Triple(
            Icons.Outlined.Chat,
            Brush.linearGradient(listOf(Color(0xFF065F46), Color(0xFF10B981))),
            Color.White
        )
        is TriggerSpec.TelegramMessage -> Triple(
            Icons.Default.Send,
            Brush.linearGradient(listOf(Color(0xFF0369A1), Color(0xFF0EA5E9))),
            Color.White
        )
        is TriggerSpec.Notification -> {
            when {
                trigger.packageNames.any { it.contains("whatsapp", ignoreCase = true) } -> Triple(
                    Icons.Outlined.Chat,
                    Brush.linearGradient(listOf(Color(0xFF065F46), Color(0xFF10B981))),
                    Color.White
                )
                trigger.packageNames.any { it.contains("telegram", ignoreCase = true) } -> Triple(
                    Icons.Default.Send,
                    Brush.linearGradient(listOf(Color(0xFF0369A1), Color(0xFF0EA5E9))),
                    Color.White
                )
                trigger.packageNames.any { it.contains("instagram", ignoreCase = true) } -> Triple(
                    Icons.Default.SmartButton,
                    Brush.linearGradient(listOf(Color(0xFF9333EA), Color(0xFFEC4899))),
                    Color.White
                )
                trigger.packageNames.any { it.contains("youtube", ignoreCase = true) } -> Triple(
                    Icons.Default.PlayArrow,
                    Brush.linearGradient(listOf(Color(0xFF991B1B), Color(0xFFEF4444))),
                    Color.White
                )
                else -> Triple(
                    Icons.Outlined.Notifications,
                    Brush.linearGradient(listOf(Color(0xFF4338CA), Color(0xFF6366F1))),
                    Color.White
                )
            }
        }
        is TriggerSpec.Schedule, is TriggerSpec.Interval -> Triple(
            Icons.Default.Schedule,
            Brush.linearGradient(listOf(Color(0xFFC2410C), Color(0xFFF59E0B))),
            Color.White
        )
        is TriggerSpec.SmsReceived -> Triple(
            Icons.Default.Send,
            Brush.linearGradient(listOf(Color(0xFF0E7490), Color(0xFF06B6D4))),
            Color.White
        )
        else -> Triple(
            Icons.Default.Bolt,
            Brush.linearGradient(listOf(Color(0xFF4338CA), Color(0xFF8B5CF6))),
            Color.White
        )
    }

    Box(
        modifier = modifier
            .size(44.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(bgGradient),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
fun HeroDashboardCard(
    setupState: SetupState,
    totalRules: Int,
    activeRules: Int,
    totalRuns: Int,
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseScale",
    )

    val isReady = setupState.ready
    val statusColor = if (isReady) AppColors.GreenSuccess else AppColors.AmberWarning
    val statusText = if (isReady) "Engine Active & Ready" else "Setup Needed (Permissions)"

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .scale(if (isReady) pulseScale else 1f)
                            .clip(CircleShape)
                            .background(statusColor),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        statusText,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = statusColor,
                    )
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                ) {
                    Text(
                        "$activeRules / $totalRules Active",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                StatBox(
                    title = "Automations",
                    value = "$totalRules",
                    subtitle = "Rules configured",
                    icon = Icons.Default.Bolt,
                    tint = AppColors.IndigoPrimary,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                StatBox(
                    title = "Executions",
                    value = "$totalRuns",
                    subtitle = "Total runs",
                    icon = Icons.Default.PlayArrow,
                    tint = AppColors.WhatsAppEmerald,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                StatBox(
                    title = "Health",
                    value = if (setupState.accessibility) "100%" else "50%",
                    subtitle = "Service state",
                    icon = Icons.Default.CheckCircle,
                    tint = if (isReady) AppColors.GreenSuccess else AppColors.AmberWarning,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun StatBox(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f))
        }
    }
}

@Composable
fun ModernSetupCard(
    accessibility: Boolean,
    notifications: Boolean,
    battery: Boolean,
    onFixAccessibility: () -> Unit,
    onFixNotifications: () -> Unit,
    onFixBattery: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val grantedCount = listOf(accessibility, notifications, battery).count { it }
    val isComplete = grantedCount == 3
    var expanded by remember { mutableStateOf(!isComplete) }

    OutlinedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(
            1.dp,
            if (isComplete) AppColors.GreenSuccess.copy(alpha = 0.4f)
            else AppColors.AmberWarning.copy(alpha = 0.4f),
        ),
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (isComplete) AppColors.GreenSuccess.copy(alpha = 0.05f)
            else AppColors.AmberWarning.copy(alpha = 0.06f),
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isComplete) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = null,
                        tint = if (isComplete) AppColors.GreenSuccess else AppColors.AmberWarning,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            if (isComplete) "All Permissions Active" else "Permissions Setup Required",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "$grantedCount of 3 services configured",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Text(
                    text = if (expanded) "Hide" else "Details",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { expanded = !expanded }.padding(4.dp),
                )
            }

            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { grantedCount / 3f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = if (isComplete) AppColors.GreenSuccess else AppColors.AmberWarning,
                trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
            )

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    SetupItem(
                        title = "Accessibility Service",
                        desc = "Allows AutoFlow to tap, type, and navigate on screen",
                        granted = accessibility,
                        onEnable = onFixAccessibility,
                    )
                    Spacer(Modifier.height(8.dp))
                    SetupItem(
                        title = "Notification Listener",
                        desc = "Reads incoming notifications to trigger rules instantly",
                        granted = notifications,
                        onEnable = onFixNotifications,
                    )
                    Spacer(Modifier.height(8.dp))
                    SetupItem(
                        title = "Battery Unrestricted",
                        desc = "Ensures background rules trigger without system sleep",
                        granted = battery,
                        onEnable = onFixBattery,
                    )
                }
            }
        }
    }
}

@Composable
private fun SetupItem(
    title: String,
    desc: String,
    granted: Boolean,
    onEnable: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
            .padding(10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(desc, style = MaterialTheme.typography.bodySmall, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(8.dp))
        if (granted) {
            Surface(
                shape = CircleShape,
                color = AppColors.GreenSuccess.copy(alpha = 0.15f),
                modifier = Modifier.size(28.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = AppColors.GreenSuccess, modifier = Modifier.size(16.dp))
                }
            }
        } else {
            FilledTonalButton(
                onClick = onEnable,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                shape = RoundedCornerShape(8.dp),
            ) {
                Text("Enable", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun ModernRuleCard(
    rule: Rule,
    onToggle: (Boolean) -> Unit,
    onRunNow: () -> Unit,
    onDelete: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp, pressedElevation = 4.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppBrandBadge(trigger = rule.trigger)
                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            rule.name.ifBlank { "Untitled Automation" },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        rule.trigger.label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Spacer(Modifier.width(8.dp))
                Switch(
                    checked = rule.enabled,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = AppColors.IndigoPrimary,
                    ),
                )
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                ) {
                    Text(
                        "⚡ ${rule.actions.size} ${if (rule.actions.size == 1) "Action" else "Actions"}",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                if (rule.conditions.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = AppColors.AmberWarning.copy(alpha = 0.15f),
                    ) {
                        Text(
                            "🔍 ${rule.conditions.size} Filter",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = AppColors.AmberWarning,
                        )
                    }
                }

                Spacer(Modifier.weight(1f))

                Text(
                    text = if (rule.runCount == 0) "Never run" else "Ran ${rule.runCount}x",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }

            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = onRunNow,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, AppColors.IndigoPrimary.copy(alpha = 0.4f)),
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp), tint = AppColors.IndigoPrimary)
                    Spacer(Modifier.width(4.dp))
                    Text("Test Run", style = MaterialTheme.typography.labelMedium, color = AppColors.IndigoPrimary, fontWeight = FontWeight.Bold)
                }

                Spacer(Modifier.width(6.dp))

                IconButton(
                    onClick = onClick,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = AppColors.RoseError.copy(alpha = 0.8f), modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
fun EmptyAutomationsView(
    onAddRule: () -> Unit,
    onOpenTemplates: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(AppColors.PrimaryGradient),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Bolt, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
            }

            Spacer(Modifier.height(16.dp))

            Text(
                "No Automations Yet",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Create powerful automated flows for WhatsApp, Telegram, YouTube, SMS, and device controls.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )

            Spacer(Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                FilledTonalButton(
                    onClick = onOpenTemplates,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    Icon(Icons.Default.Widgets, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("33+ Templates", fontWeight = FontWeight.Bold)
                }

                Spacer(Modifier.width(10.dp))

                FilledTonalButton(
                    onClick = onAddRule,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = AppColors.IndigoPrimary,
                        contentColor = Color.White,
                    ),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("New Rule", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
