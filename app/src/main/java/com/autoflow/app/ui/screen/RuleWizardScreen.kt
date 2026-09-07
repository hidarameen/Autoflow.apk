package com.autoflow.app.ui.screen

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.autoflow.app.data.ActionSpec
import com.autoflow.app.data.ConditionLogic
import com.autoflow.app.data.ConditionSpec
import com.autoflow.app.data.Rule
import com.autoflow.app.data.TriggerSpec
import com.autoflow.app.data.indentDelta
import com.autoflow.app.data.label
import com.autoflow.app.ui.catalog.ActionCatalog
import com.autoflow.app.ui.catalog.FieldDef
import com.autoflow.app.ui.catalog.Platform
import com.autoflow.app.ui.catalog.Platforms
import com.autoflow.app.ui.catalog.TriggerCatalog
import com.autoflow.app.ui.components.Glyphs
import com.autoflow.app.ui.components.IconBadge
import com.autoflow.app.ui.components.PickerItem
import com.autoflow.app.ui.components.SearchablePickerDialog
import com.autoflow.app.ui.components.SectionCard
import com.autoflow.app.ui.theme.AppColors
import com.autoflow.app.ui.theme.LocalTokens
import com.autoflow.app.util.InstalledApp
import kotlinx.coroutines.flow.StateFlow

private enum class WizardStep(val title: String, val caption: String) {
    TRIGGER("When", "Pick what starts this automation"),
    FILTERS("Only if", "Narrow down when it should run"),
    ACTIONS("Then do", "Choose what happens"),
    REVIEW("Review", "Name it and save"),
}

/** Which source the user is choosing a trigger or action from. */
private enum class SourceMode { NONE, GENERIC, PLATFORM }

/**
 * Step-by-step rule builder.
 *
 * Splitting the four stages across pages keeps each screen short enough to complete on a
 * phone, and lets the "Apps" path drill from a platform to that platform's own presets
 * without burying the generic catalogue.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuleWizardScreen(
    existing: Rule?,
    apps: StateFlow<List<InstalledApp>>,
    onSave: (Rule) -> Unit,
    onCancel: () -> Unit,
) {
    val installedApps by apps.collectAsStateWithLifecycle()

    var step by remember { mutableStateOf(WizardStep.TRIGGER) }

    var name by remember { mutableStateOf(existing?.name ?: "") }
    var cooldown by remember { mutableStateOf((existing?.cooldownMs ?: 5_000L).toString()) }
    var dailyLimit by remember { mutableStateOf((existing?.dailyLimit ?: 0).toString()) }

    var trigger by remember { mutableStateOf(existing?.trigger ?: TriggerSpec.Notification()) }
    val triggerDef = remember(trigger) { TriggerCatalog.defFor(trigger) }
    val triggerValues = remember(triggerDef.title) {
        mutableStateMapOf<String, String>().apply { putAll(triggerDef.read(trigger)) }
    }

    // When a platform preset supplies the trigger, its own fields drive it instead of the
    // generic catalogue's, so both are tracked separately.
    var platformTriggerId by remember { mutableStateOf<String?>(null) }
    var platformTriggerPlatform by remember { mutableStateOf<Platform?>(null) }
    val platformTriggerValues = remember { mutableStateMapOf<String, String>() }

    val conditions = remember {
        mutableStateListOf<ConditionSpec>().apply { addAll(existing?.conditions.orEmpty()) }
    }
    var conditionLogic by remember { mutableStateOf(existing?.conditionLogic ?: ConditionLogic.ALL) }

    val actions = remember {
        mutableStateListOf<ActionSpec>().apply { addAll(existing?.actions.orEmpty()) }
    }

    var editingIndex by remember { mutableStateOf<Int?>(null) }
    var triggerSource by remember { mutableStateOf(SourceMode.NONE) }
    var actionSource by remember { mutableStateOf(SourceMode.NONE) }
    var triggerPlatformSheet by remember { mutableStateOf<Platform?>(null) }
    var actionPlatformSheet by remember { mutableStateOf<Platform?>(null) }
    var pendingPlatformAction by remember {
        mutableStateOf<Pair<Platform, com.autoflow.app.ui.catalog.PlatformAction>?>(null)
    }

    fun resolvedTrigger(): TriggerSpec {
        val platform = platformTriggerPlatform
        val presetId = platformTriggerId
        if (platform != null && presetId != null) {
            val preset = platform.triggers.firstOrNull { it.id == presetId }
            if (preset != null) return preset.build(platformTriggerValues.toMap())
        }
        return triggerDef.write(triggerValues.toMap())
    }

    fun buildRule(): Rule {
        val resolved = resolvedTrigger()
        val base = existing ?: Rule(name = name, trigger = resolved, actions = emptyList())
        return base.copy(
            name = name,
            trigger = resolved,
            conditions = conditions.toList(),
            conditionLogic = conditionLogic,
            actions = actions.toList(),
            cooldownMs = cooldown.toLongOrNull() ?: 5_000L,
            dailyLimit = dailyLimit.toIntOrNull() ?: 0,
        )
    }

    val canAdvance = when (step) {
        WizardStep.TRIGGER -> true
        WizardStep.FILTERS -> true
        WizardStep.ACTIONS -> actions.isNotEmpty()
        WizardStep.REVIEW -> name.isNotBlank() && actions.isNotEmpty()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            if (existing == null) "New automation" else "Edit automation",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "Step ${step.ordinal + 1} of 4 · ${step.title}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Cancel")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        bottomBar = {
            WizardFooter(
                step = step,
                canAdvance = canAdvance,
                blockedReason = when {
                    step == WizardStep.ACTIONS && actions.isEmpty() -> "Add at least one step"
                    step == WizardStep.REVIEW && name.isBlank() -> "Give the automation a name"
                    else -> null
                },
                onBack = {
                    if (step.ordinal == 0) onCancel()
                    else step = WizardStep.entries[step.ordinal - 1]
                },
                onNext = {
                    if (step == WizardStep.REVIEW) onSave(buildRule())
                    else step = WizardStep.entries[step.ordinal + 1]
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            StepIndicator(step)

            AnimatedContent(
                targetState = step,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "wizard-step",
            ) { current ->
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    when (current) {
                        WizardStep.TRIGGER -> {
                            item {
                                ChooserButtons(
                                    genericLabel = "Browse all triggers",
                                    appsLabel = "Choose by app",
                                    onGeneric = { triggerSource = SourceMode.GENERIC },
                                    onApps = { triggerSource = SourceMode.PLATFORM },
                                )
                            }

                            item {
                                val platform = platformTriggerPlatform
                                val preset = platform?.triggers?.firstOrNull { it.id == platformTriggerId }

                                SectionCard(accent = AppColors.StepWhen) {
                                    Column(Modifier.padding(14.dp)) {
                                        Text(
                                            "Selected trigger",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = AppColors.StepWhen,
                                        )
                                        Spacer(Modifier.height(9.dp))
                                        SelectedSummary(
                                            icon = if (preset != null) platform.icon
                                            else Glyphs.triggerIcon(triggerDef.category),
                                            tint = if (preset != null) platform.color
                                            else Glyphs.triggerTint(triggerDef.category),
                                            title = preset?.title ?: triggerDef.title,
                                            subtitle = preset?.description?.ifBlank { platform.name }
                                                ?: triggerDef.help.ifBlank { triggerDef.category },
                                        )
                                    }
                                }
                            }

                            val platform = platformTriggerPlatform
                            val preset = platform?.triggers?.firstOrNull { it.id == platformTriggerId }
                            val fields: List<FieldDef> = preset?.fields ?: triggerDef.fields
                            val values = if (preset != null) platformTriggerValues else triggerValues

                            if (fields.isNotEmpty()) {
                                item {
                                    Text(
                                        "Settings",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                                items(fields, key = { "t_${it.key}" }) { field ->
                                    FieldEditor(field, values, installedApps)
                                }
                            }
                        }

                        WizardStep.FILTERS -> {
                            item {
                                SectionCard(accent = if (conditions.isEmpty()) null else AppColors.StepIf) {
                                    Column(Modifier.padding(14.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            IconBadge(Icons.Default.FilterAlt, AppColors.StepIf, size = 34)
                                            Spacer(Modifier.width(11.dp))
                                            Column(Modifier.weight(1f)) {
                                                Text(
                                                    "Filters",
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Bold,
                                                )
                                                Text(
                                                    if (conditions.isEmpty()) {
                                                        "Optional — runs every time without them"
                                                    } else {
                                                        "${conditions.size} active"
                                                    },
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                            TextButton(onClick = { conditions.add(ConditionSpec.Text()) }) {
                                                Icon(Icons.Default.Add, null, Modifier.size(16.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text("Add", fontWeight = FontWeight.Bold)
                                            }
                                        }

                                        if (conditions.size > 1) {
                                            Spacer(Modifier.height(11.dp))
                                            LogicToggleRow(conditionLogic) { conditionLogic = it }
                                        }
                                    }
                                }
                            }

                            items(conditions.size, key = { "c_$it" }) { index ->
                                if (index < conditions.size) {
                                    SectionCard {
                                        Column(Modifier.padding(12.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    "Filter ${index + 1}",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = AppColors.StepIf,
                                                    modifier = Modifier.weight(1f),
                                                )
                                                IconButton(
                                                    onClick = { conditions.removeAt(index) },
                                                    modifier = Modifier.size(28.dp),
                                                ) {
                                                    Icon(Icons.Default.Delete, "Remove", Modifier.size(16.dp))
                                                }
                                            }
                                            Spacer(Modifier.height(6.dp))
                                            ConditionEditor(
                                                condition = conditions[index],
                                                apps = installedApps,
                                                onChange = { conditions[index] = it },
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        WizardStep.ACTIONS -> {
                            item {
                                ChooserButtons(
                                    genericLabel = "Browse all actions",
                                    appsLabel = "Choose by app",
                                    onGeneric = { actionSource = SourceMode.GENERIC },
                                    onApps = { actionSource = SourceMode.PLATFORM },
                                )
                            }

                            if (actions.isEmpty()) {
                                item {
                                    SectionCard {
                                        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                            IconBadge(Icons.Default.Bolt, AppColors.StepThen, size = 52)
                                            Spacer(Modifier.height(12.dp))
                                            Text(
                                                "No steps yet",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                            )
                                            Text(
                                                "Add what should happen when the trigger fires.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                            }

                            items(actions.size, key = { "a_$it" }) { index ->
                                if (index < actions.size) {
                                    WizardActionRow(
                                        index = index,
                                        action = actions[index],
                                        indent = indentDepth(actions, index),
                                        canMoveUp = index > 0,
                                        canMoveDown = index < actions.lastIndex,
                                        onMoveUp = { actions.add(index - 1, actions.removeAt(index)) },
                                        onMoveDown = { actions.add(index + 1, actions.removeAt(index)) },
                                        onEdit = { editingIndex = index },
                                        onDelete = { actions.removeAt(index) },
                                    )
                                }
                            }
                        }

                        WizardStep.REVIEW -> {
                            item {
                                OutlinedTextField(
                                    value = name,
                                    onValueChange = { name = it },
                                    label = { Text("Automation name") },
                                    placeholder = { Text("e.g. Forward Telegram to X") },
                                    singleLine = true,
                                    isError = name.isBlank(),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }

                            item {
                                SectionCard(accent = AppColors.StepWhen) {
                                    Column(Modifier.padding(14.dp)) {
                                        SummaryLine("WHEN", resolvedTrigger().label, AppColors.StepWhen)
                                        if (conditions.isNotEmpty()) {
                                            Spacer(Modifier.height(8.dp))
                                            SummaryLine(
                                                "ONLY IF",
                                                conditions.joinToString(
                                                    if (conditionLogic == ConditionLogic.ALL) " and " else " or "
                                                ) { it.label },
                                                AppColors.StepIf,
                                            )
                                        }
                                        Spacer(Modifier.height(8.dp))
                                        SummaryLine(
                                            "THEN",
                                            actions.joinToString(" → ") { it.label },
                                            AppColors.StepThen,
                                        )
                                    }
                                }
                            }

                            item {
                                SectionCard {
                                    Column(Modifier.padding(14.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            IconBadge(Icons.Default.Tune, MaterialTheme.colorScheme.onSurfaceVariant, size = 32)
                                            Spacer(Modifier.width(11.dp))
                                            Text(
                                                "Limits",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                            )
                                        }
                                        Spacer(Modifier.height(11.dp))
                                        OutlinedTextField(
                                            value = cooldown,
                                            onValueChange = { cooldown = it.filter(Char::isDigit) },
                                            label = { Text("Cooldown (ms)") },
                                            singleLine = true,
                                            shape = RoundedCornerShape(13.dp),
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                        Spacer(Modifier.height(9.dp))
                                        OutlinedTextField(
                                            value = dailyLimit,
                                            onValueChange = { dailyLimit = it.filter(Char::isDigit) },
                                            label = { Text("Max runs per day (0 = unlimited)") },
                                            singleLine = true,
                                            shape = RoundedCornerShape(13.dp),
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ---- Trigger pickers -------------------------------------------------

    if (triggerSource == SourceMode.GENERIC) {
        SearchablePickerDialog(
            title = "Choose a trigger",
            items = TriggerCatalog.all.map {
                PickerItem(it.title, it.help, it.category, Glyphs.triggerIcon(it.category), Glyphs.triggerTint(it.category))
            },
            onDismiss = { triggerSource = SourceMode.NONE },
            onPick = { picked ->
                triggerSource = SourceMode.NONE
                platformTriggerId = null
                platformTriggerPlatform = null
                trigger = TriggerCatalog.all.first { it.title == picked.title }.create()
            },
        )
    }

    if (triggerSource == SourceMode.PLATFORM) {
        PlatformPickerDialog(
            title = "Which app?",
            countLabel = { "${it.triggers.size} triggers" },
            onDismiss = { triggerSource = SourceMode.NONE },
            onPick = {
                triggerSource = SourceMode.NONE
                triggerPlatformSheet = it
            },
        )
    }

    triggerPlatformSheet?.let { platform ->
        SearchablePickerDialog(
            title = "${platform.name} triggers",
            items = platform.triggers.map {
                PickerItem(it.title, it.description, platform.name, platform.icon, platform.color)
            },
            onDismiss = { triggerPlatformSheet = null },
            onPick = { picked ->
                triggerPlatformSheet = null
                val preset = platform.triggers.first { it.title == picked.title }
                platformTriggerPlatform = platform
                platformTriggerId = preset.id
                platformTriggerValues.clear()
                trigger = preset.build(emptyMap())
            },
        )
    }

    // ---- Action pickers --------------------------------------------------

    if (actionSource == SourceMode.GENERIC) {
        SearchablePickerDialog(
            title = "Add a step",
            items = ActionCatalog.all.map {
                PickerItem(it.title, it.help, it.category, Glyphs.actionIcon(it.category), Glyphs.actionTint(it.category))
            },
            onDismiss = { actionSource = SourceMode.NONE },
            onPick = { picked ->
                actionSource = SourceMode.NONE
                val definition = ActionCatalog.all.first { it.title == picked.title }
                actions.add(definition.create())
                if (definition.fields.isNotEmpty()) editingIndex = actions.lastIndex
            },
        )
    }

    if (actionSource == SourceMode.PLATFORM) {
        PlatformPickerDialog(
            title = "Which app?",
            countLabel = { "${it.actions.size} actions" },
            onDismiss = { actionSource = SourceMode.NONE },
            onPick = {
                actionSource = SourceMode.NONE
                actionPlatformSheet = it
            },
        )
    }

    actionPlatformSheet?.let { platform ->
        SearchablePickerDialog(
            title = "${platform.name} actions",
            items = platform.actions.map {
                PickerItem(it.title, it.description, platform.name, platform.icon, platform.color)
            },
            onDismiss = { actionPlatformSheet = null },
            onPick = { picked ->
                actionPlatformSheet = null
                val preset = platform.actions.first { it.title == picked.title }
                if (preset.fields.isEmpty()) {
                    actions.addAll(preset.build(emptyMap()))
                } else {
                    pendingPlatformAction = platform to preset
                }
            },
        )
    }

    // A platform action expands into several steps, so its parameters are collected once
    // up front rather than by editing each generated step.
    pendingPlatformAction?.let { (platform, preset) ->
        PlatformActionConfigDialog(
            platform = platform,
            preset = preset,
            apps = installedApps,
            onDismiss = { pendingPlatformAction = null },
            onConfirm = { values ->
                actions.addAll(preset.build(values))
                pendingPlatformAction = null
            },
        )
    }

    editingIndex?.let { index ->
        if (index in actions.indices) {
            ActionEditorDialog(
                action = actions[index],
                apps = installedApps,
                onDismiss = { editingIndex = null },
                onConfirm = {
                    actions[index] = it
                    editingIndex = null
                },
            )
        }
    }
}

// ---- Pieces --------------------------------------------------------------

@Composable
private fun StepIndicator(current: WizardStep) {
    Column(Modifier.padding(horizontal = 16.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            WizardStep.entries.forEach { entry ->
                val done = entry.ordinal <= current.ordinal
                Box(
                    Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            if (done) stepColor(entry)
                            else MaterialTheme.colorScheme.outlineVariant
                        )
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            current.caption,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun stepColor(step: WizardStep): Color = when (step) {
    WizardStep.TRIGGER -> AppColors.StepWhen
    WizardStep.FILTERS -> AppColors.StepIf
    WizardStep.ACTIONS -> AppColors.StepThen
    WizardStep.REVIEW -> AppColors.VioletAccent
}

@Composable
private fun WizardFooter(
    step: WizardStep,
    canAdvance: Boolean,
    blockedReason: String?,
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp,
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            if (blockedReason != null) {
                Text(
                    blockedReason,
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.AmberWarning,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = onBack,
                    shape = RoundedCornerShape(13.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, Modifier.size(17.dp))
                    Spacer(Modifier.width(7.dp))
                    Text(if (step.ordinal == 0) "Cancel" else "Back")
                }
                Button(
                    onClick = onNext,
                    enabled = canAdvance,
                    shape = RoundedCornerShape(13.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.IndigoPrimary),
                    modifier = Modifier.weight(1.4f),
                ) {
                    Text(
                        if (step == WizardStep.REVIEW) "Save automation" else "Next",
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.width(7.dp))
                    Icon(
                        if (step == WizardStep.REVIEW) Icons.Default.Check
                        else Icons.AutoMirrored.Filled.ArrowForward,
                        null,
                        Modifier.size(17.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ChooserButtons(
    genericLabel: String,
    appsLabel: String,
    onGeneric: () -> Unit,
    onApps: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(
            onClick = onGeneric,
            shape = RoundedCornerShape(13.dp),
            modifier = Modifier.weight(1f),
        ) {
            Icon(Icons.Default.Bolt, null, Modifier.size(17.dp))
            Spacer(Modifier.width(6.dp))
            Text(genericLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Button(
            onClick = onApps,
            shape = RoundedCornerShape(13.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AppColors.IndigoPrimary),
            modifier = Modifier.weight(1f),
        ) {
            Icon(Icons.Default.Apps, null, Modifier.size(17.dp))
            Spacer(Modifier.width(6.dp))
            Text(appsLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun SelectedSummary(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    title: String,
    subtitle: String,
) {
    val tokens = LocalTokens.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(tokens.subtleSurface)
            .padding(11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(icon, tint, size = 36)
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            if (subtitle.isNotBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SummaryLine(stage: String, text: String, color: Color) {
    Row(verticalAlignment = Alignment.Top) {
        Text(
            stage,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = color,
            modifier = Modifier.width(60.dp),
        )
        Text(text, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun LogicToggleRow(current: ConditionLogic, onChange: (ConditionLogic) -> Unit) {
    val tokens = LocalTokens.current
    Row(
        Modifier
            .clip(RoundedCornerShape(11.dp))
            .background(tokens.subtleSurface)
            .padding(3.dp),
    ) {
        ConditionLogic.entries.forEach { option ->
            val selected = option == current
            Box(
                Modifier
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (selected) AppColors.StepIf else Color.Transparent)
                    .clickable { onChange(option) }
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            ) {
                Text(
                    if (option == ConditionLogic.ALL) "Match all" else "Match any",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun WizardActionRow(
    index: Int,
    action: ActionSpec,
    indent: Int,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val tokens = LocalTokens.current
    val definition = remember(action) { ActionCatalog.defFor(action) }

    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = (indent * 14).dp)
            .clip(RoundedCornerShape(13.dp))
            .background(tokens.subtleSurface)
            .clickable(onClick = onEdit)
            .padding(start = 10.dp, end = 4.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "${index + 1}",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(16.dp),
        )
        IconBadge(
            Glyphs.actionIcon(definition.category),
            Glyphs.actionTint(definition.category),
            size = 28,
        )
        Spacer(Modifier.width(9.dp))
        Text(
            action.label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        IconButton(enabled = canMoveUp, onClick = onMoveUp, modifier = Modifier.size(30.dp)) {
            Icon(Icons.Default.ArrowUpward, "Move up", Modifier.size(15.dp))
        }
        IconButton(enabled = canMoveDown, onClick = onMoveDown, modifier = Modifier.size(30.dp)) {
            Icon(Icons.Default.ArrowDownward, "Move down", Modifier.size(15.dp))
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(30.dp)) {
            Icon(Icons.Default.Delete, "Remove step", Modifier.size(15.dp), tint = AppColors.RoseError)
        }
    }
}

private fun indentDepth(actions: List<ActionSpec>, index: Int): Int {
    var depth = 0
    for (i in 0 until index) depth += actions[i].indentDelta
    if (actions[index].indentDelta < 0) depth--
    return depth.coerceAtLeast(0)
}
