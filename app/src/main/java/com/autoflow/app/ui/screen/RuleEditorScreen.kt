package com.autoflow.app.ui.screen

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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import com.autoflow.app.ui.catalog.ConditionCatalog
import com.autoflow.app.ui.catalog.TriggerCatalog
import com.autoflow.app.ui.components.FlowConnector
import com.autoflow.app.ui.components.Glyphs
import com.autoflow.app.ui.components.IconBadge
import com.autoflow.app.ui.components.PickerItem
import com.autoflow.app.ui.components.SearchablePickerDialog
import com.autoflow.app.ui.components.SectionCard
import com.autoflow.app.ui.components.StepHeader
import com.autoflow.app.ui.theme.AppColors
import com.autoflow.app.ui.theme.LocalTokens
import com.autoflow.app.util.InstalledApp
import kotlinx.coroutines.flow.StateFlow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuleEditorScreen(
    existing: Rule?,
    apps: StateFlow<List<InstalledApp>>,
    onSave: (Rule) -> Unit,
    onCancel: () -> Unit,
) {
    val installedApps by apps.collectAsStateWithLifecycle()

    var name by remember { mutableStateOf(existing?.name ?: "") }
    var notes by remember { mutableStateOf(existing?.notes ?: "") }
    var cooldown by remember { mutableStateOf((existing?.cooldownMs ?: 5_000L).toString()) }
    var dailyLimit by remember { mutableStateOf((existing?.dailyLimit ?: 0).toString()) }

    var trigger by remember { mutableStateOf(existing?.trigger ?: TriggerSpec.Notification()) }
    val triggerDef = remember(trigger) { TriggerCatalog.defFor(trigger) }
    // Keyed on the definition so switching trigger type resets the field values.
    val triggerValues = remember(triggerDef.title) {
        mutableStateMapOf<String, String>().apply { putAll(triggerDef.read(trigger)) }
    }

    val conditions = remember {
        mutableStateListOf<ConditionSpec>().apply { addAll(existing?.conditions.orEmpty()) }
    }
    var conditionLogic by remember { mutableStateOf(existing?.conditionLogic ?: ConditionLogic.ALL) }

    val actions = remember {
        mutableStateListOf<ActionSpec>().apply { addAll(existing?.actions.orEmpty()) }
    }

    var editingIndex by remember { mutableStateOf<Int?>(null) }
    var showActionPicker by remember { mutableStateOf(false) }
    var showTriggerPicker by remember { mutableStateOf(false) }
    var showAdvanced by remember { mutableStateOf(false) }

    val canSave = name.isNotBlank() && actions.isNotEmpty()

    fun buildRule(): Rule {
        val resolvedTrigger = triggerDef.write(triggerValues.toMap())
        val base = existing ?: Rule(name = name, trigger = resolvedTrigger, actions = emptyList())
        return base.copy(
            name = name,
            trigger = resolvedTrigger,
            conditions = conditions.toList(),
            conditionLogic = conditionLogic,
            actions = actions.toList(),
            cooldownMs = cooldown.toLongOrNull() ?: 5_000L,
            dailyLimit = dailyLimit.toIntOrNull() ?: 0,
            notes = notes,
        )
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
                            "When → Only if → Then",
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
                actions = {
                    Button(
                        onClick = { onSave(buildRule()) },
                        enabled = canSave,
                        shape = RoundedCornerShape(11.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AppColors.IndigoPrimary,
                        ),
                        modifier = Modifier.padding(end = 12.dp),
                    ) {
                        Icon(Icons.Default.Check, null, Modifier.size(17.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Save", fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            item {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Automation name") },
                    placeholder = { Text("e.g. Forward Telegram to X") },
                    singleLine = true,
                    isError = name.isBlank(),
                    supportingText = if (name.isBlank()) {
                        { Text("Give it a name so you can find it later") }
                    } else null,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(14.dp))
            }

            // ---- Step 1: WHEN ------------------------------------------------

            item {
                SectionCard(accent = AppColors.StepWhen) {
                    Column(Modifier.padding(14.dp)) {
                        StepHeader(
                            number = 1,
                            label = "When",
                            caption = "What starts this automation",
                            color = AppColors.StepWhen,
                        ) {
                            TextButton(onClick = { showTriggerPicker = true }) {
                                Text("Change", fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(Modifier.height(11.dp))

                        SelectedRow(
                            icon = Glyphs.triggerIcon(triggerDef.category),
                            tint = Glyphs.triggerTint(triggerDef.category),
                            title = triggerDef.title,
                            subtitle = triggerDef.help.ifBlank { triggerDef.category },
                        )

                        if (triggerDef.fields.isNotEmpty()) {
                            Spacer(Modifier.height(12.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                                triggerDef.fields.forEach { field ->
                                    FieldEditor(field, triggerValues, installedApps)
                                }
                            }
                        }
                    }
                }
                FlowConnector(AppColors.StepWhen)
            }

            // ---- Step 2: ONLY IF ---------------------------------------------

            item {
                SectionCard(accent = if (conditions.isEmpty()) null else AppColors.StepIf) {
                    Column(Modifier.padding(14.dp)) {
                        StepHeader(
                            number = 2,
                            label = "Only if",
                            caption = "Optional filters",
                            color = AppColors.StepIf,
                        ) {
                            TextButton(onClick = { conditions.add(ConditionSpec.Text()) }) {
                                Icon(Icons.Default.Add, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Add", fontWeight = FontWeight.Bold)
                            }
                        }

                        if (conditions.isEmpty()) {
                            Spacer(Modifier.height(10.dp))
                            HintRow("No filters — runs every time the trigger fires.")
                        } else {
                            if (conditions.size > 1) {
                                Spacer(Modifier.height(11.dp))
                                LogicToggle(conditionLogic) { conditionLogic = it }
                            }
                            Spacer(Modifier.height(10.dp))
                            conditions.forEachIndexed { index, condition ->
                                FilterCard(
                                    index = index,
                                    condition = condition,
                                    apps = installedApps,
                                    onChange = { conditions[index] = it },
                                    onRemove = { conditions.removeAt(index) },
                                )
                                if (index < conditions.lastIndex) Spacer(Modifier.height(8.dp))
                            }
                        }
                    }
                }
                FlowConnector(AppColors.StepIf)
            }

            // ---- Step 3: THEN ------------------------------------------------

            item {
                SectionCard(accent = if (actions.isEmpty()) null else AppColors.StepThen) {
                    Column(Modifier.padding(14.dp)) {
                        StepHeader(
                            number = 3,
                            label = "Then do",
                            caption = if (actions.isEmpty()) {
                                "At least one step required"
                            } else {
                                "${actions.size} step" + if (actions.size == 1) "" else "s"
                            },
                            color = AppColors.StepThen,
                        ) {
                            TextButton(onClick = { showActionPicker = true }) {
                                Icon(Icons.Default.Add, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Add", fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(Modifier.height(10.dp))

                        if (actions.isEmpty()) {
                            HintRow("Add what should happen — open an app, type text, call an API…")
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                actions.forEachIndexed { index, action ->
                                    ActionRow(
                                        index = index,
                                        action = action,
                                        indent = indentAt(actions, index),
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

                        Spacer(Modifier.height(10.dp))
                        VariableHint()
                    }
                }
            }

            // ---- Advanced ----------------------------------------------------

            item {
                Spacer(Modifier.height(14.dp))
                SectionCard {
                    Column(Modifier.padding(14.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconBadge(Icons.Default.Tune, MaterialTheme.colorScheme.onSurfaceVariant, size = 32)
                            Spacer(Modifier.width(11.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "Limits & notes",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    "Cooldown ${cooldown}ms" +
                                        if ((dailyLimit.toIntOrNull() ?: 0) > 0) ", max $dailyLimit/day" else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            TextButton(onClick = { showAdvanced = !showAdvanced }) {
                                Text(if (showAdvanced) "Hide" else "Edit", fontWeight = FontWeight.Bold)
                            }
                        }

                        if (showAdvanced) {
                            Spacer(Modifier.height(12.dp))
                            OutlinedTextField(
                                value = cooldown,
                                onValueChange = { cooldown = it.filter(Char::isDigit) },
                                label = { Text("Cooldown (ms)") },
                                supportingText = { Text("Ignores repeat triggers inside this window") },
                                singleLine = true,
                                shape = RoundedCornerShape(13.dp),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(Modifier.height(9.dp))
                            OutlinedTextField(
                                value = dailyLimit,
                                onValueChange = { dailyLimit = it.filter(Char::isDigit) },
                                label = { Text("Max runs per day") },
                                supportingText = { Text("0 means unlimited") },
                                singleLine = true,
                                shape = RoundedCornerShape(13.dp),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(Modifier.height(9.dp))
                            OutlinedTextField(
                                value = notes,
                                onValueChange = { notes = it },
                                label = { Text("Notes") },
                                minLines = 2,
                                shape = RoundedCornerShape(13.dp),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }

            if (!canSave) {
                item {
                    Spacer(Modifier.height(12.dp))
                    HintRow(
                        when {
                            name.isBlank() && actions.isEmpty() ->
                                "Add a name and at least one step to save."
                            name.isBlank() -> "Add a name to save."
                            else -> "Add at least one step to save."
                        },
                        color = AppColors.AmberWarning,
                    )
                }
            }
        }
    }

    // ---- Dialogs ---------------------------------------------------------

    if (showTriggerPicker) {
        SearchablePickerDialog(
            title = "Choose a trigger",
            items = TriggerCatalog.all.map {
                PickerItem(
                    title = it.title,
                    subtitle = it.help,
                    group = it.category,
                    icon = Glyphs.triggerIcon(it.category),
                    tint = Glyphs.triggerTint(it.category),
                )
            },
            onDismiss = { showTriggerPicker = false },
            onPick = { picked ->
                showTriggerPicker = false
                trigger = TriggerCatalog.all.first { it.title == picked.title }.create()
            },
        )
    }

    if (showActionPicker) {
        SearchablePickerDialog(
            title = "Add a step",
            items = ActionCatalog.all.map {
                PickerItem(
                    title = it.title,
                    subtitle = it.help,
                    group = it.category,
                    icon = Glyphs.actionIcon(it.category),
                    tint = Glyphs.actionTint(it.category),
                )
            },
            onDismiss = { showActionPicker = false },
            onPick = { picked ->
                showActionPicker = false
                val definition = ActionCatalog.all.first { it.title == picked.title }
                actions.add(definition.create())
                // Steps with nothing to configure do not need the dialog.
                if (definition.fields.isNotEmpty() || definition.create() is ActionSpec.If) {
                    editingIndex = actions.lastIndex
                }
            },
        )
    }

    editingIndex?.let { index ->
        if (index in actions.indices) {
            ActionEditorDialog(
                action = actions[index],
                apps = installedApps,
                onDismiss = { editingIndex = null },
                onConfirm = { updated ->
                    actions[index] = updated
                    editingIndex = null
                },
            )
        }
    }
}

// ---- Pieces --------------------------------------------------------------

@Composable
private fun SelectedRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    title: String,
    subtitle: String,
) {
    val tokens = LocalTokens.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(tokens.subtleSurface)
            .padding(11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(icon, tint, size = 36)
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun HintRow(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = color,
        modifier = Modifier.padding(vertical = 2.dp),
    )
}

@Composable
private fun LogicToggle(current: ConditionLogic, onChange: (ConditionLogic) -> Unit) {
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
                    .clickable(onClick = { onChange(option) })
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
private fun FilterCard(
    index: Int,
    condition: ConditionSpec,
    apps: List<InstalledApp>,
    onChange: (ConditionSpec) -> Unit,
    onRemove: () -> Unit,
) {
    val tokens = LocalTokens.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(tokens.subtleSurface)
            .padding(11.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.FilterAlt, null,
                tint = AppColors.StepIf,
                modifier = Modifier.size(15.dp),
            )
            Spacer(Modifier.width(7.dp))
            Text(
                "Filter ${index + 1}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = AppColors.StepIf,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)) {
                Icon(
                    Icons.Default.Delete, "Remove filter",
                    Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        ConditionEditor(condition = condition, apps = apps, onChange = onChange)
    }
}

@Composable
private fun ActionRow(
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
        modifier = Modifier
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
            Icon(
                Icons.Default.Delete, "Remove step",
                Modifier.size(15.dp),
                tint = AppColors.RoseError,
            )
        }
    }
}

/** Reminds the user that message content can be injected, without opening documentation. */
@Composable
private fun VariableHint() {
    val tokens = LocalTokens.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(11.dp))
            .background(AppColors.IndigoPrimary.copy(alpha = if (tokens.isDark) 0.13f else 0.07f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.Bolt, null,
            tint = AppColors.IndigoPrimary,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "Insert the message with {{text}}, {{title}}, {{app}} — " +
                "trim it with {{text|trim:270}}.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Indentation so If and Repeat blocks read as blocks in a flat list. */
private fun indentAt(actions: List<ActionSpec>, index: Int): Int {
    var depth = 0
    for (i in 0 until index) depth += actions[i].indentDelta
    // A closing marker lines up with the step that opened it.
    if (actions[index].indentDelta < 0) depth--
    return depth.coerceAtLeast(0)
}
