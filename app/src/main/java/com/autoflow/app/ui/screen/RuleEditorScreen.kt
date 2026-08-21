package com.autoflow.app.ui.screen

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.autoflow.app.data.ActionSpec
import com.autoflow.app.data.ConditionLogic
import com.autoflow.app.data.ConditionSpec
import com.autoflow.app.data.Rule
import com.autoflow.app.data.TriggerSpec
import com.autoflow.app.data.indentDelta
import com.autoflow.app.data.label
import com.autoflow.app.ui.catalog.ActionCatalog
import com.autoflow.app.ui.catalog.ActionDef
import com.autoflow.app.ui.catalog.TriggerCatalog
import com.autoflow.app.ui.components.AppBrandBadge
import com.autoflow.app.ui.theme.AppColors
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
    val triggerValues = remember(triggerDef.title) {
        mutableStateMapOf<String, String>().apply { putAll(triggerDef.read(trigger)) }
    }

    val conditions = remember {
        mutableStateListOf<ConditionSpec>().apply { addAll(existing?.conditions.orEmpty()) }
    }
    var conditionLogic by remember {
        mutableStateOf(existing?.conditionLogic ?: ConditionLogic.ALL)
    }

    val actions = remember {
        mutableStateListOf<ActionSpec>().apply { addAll(existing?.actions.orEmpty()) }
    }
    var editingIndex by remember { mutableStateOf<Int?>(null) }
    var showActionPicker by remember { mutableStateOf(false) }
    var showTriggerPicker by remember { mutableStateOf(false) }

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
                            if (existing == null) "New Automation Flow" else "Edit Automation",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "Visual Flow Builder",
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
                    FilledTonalButton(
                        enabled = name.isNotBlank() && actions.isNotEmpty(),
                        onClick = { onSave(buildRule()) },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = AppColors.IndigoPrimary,
                            contentColor = Color.White,
                        ),
                        modifier = Modifier.padding(end = 8.dp),
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Save Flow", fontWeight = FontWeight.Bold)
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
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Automation Name Field
            item {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Automation Name (e.g. WhatsApp Auto-Reply)") },
                    placeholder = { Text("Enter a descriptive name...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                    ),
                )
            }

            // ---- NODE 1: TRIGGER (WHEN) -----------------------------------
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                    border = BorderStroke(1.dp, AppColors.IndigoPrimary.copy(alpha = 0.3f)),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = AppColors.IndigoPrimary,
                                ) {
                                    Text(
                                        "STEP 1: WHEN",
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                    )
                                }
                            }

                            TextButton(onClick = { showTriggerPicker = true }) {
                                Text("Change Trigger", fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(Modifier.height(10.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AppBrandBadge(trigger = trigger)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    triggerDef.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                                if (triggerDef.help.isNotBlank()) {
                                    Text(
                                        triggerDef.help,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            items(triggerDef.fields, key = { "trigger_${it.key}" }) { field ->
                FieldEditor(field = field, values = triggerValues, apps = installedApps)
            }

            // Connecting visual connector
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                        modifier = Modifier.size(24.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.ArrowDownward, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }

            // ---- NODE 2: CONDITIONS (ONLY IF) ----------------------------
            item {
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.outlinedCardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                    ),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = AppColors.AmberWarning.copy(alpha = 0.2f),
                                ) {
                                    Text(
                                        "STEP 2: ONLY IF (Optional Filters)",
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = AppColors.AmberWarning,
                                    )
                                }
                            }

                            FilledTonalButton(
                                onClick = { conditions.add(ConditionSpec.Text()) },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Add Filter", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        if (conditions.isEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "No filters added. The flow executes unconditionally every time the trigger fires.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else if (conditions.size > 1) {
                            Spacer(Modifier.height(8.dp))
                            Dropdown(
                                label = "Combine filters using",
                                selected = conditionLogic.name,
                                options = ConditionLogic.entries.map { it.name },
                                onSelect = { conditionLogic = ConditionLogic.entries[it] },
                            )
                        }
                    }
                }
            }

            itemsIndexed(conditions) { index, condition ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.FilterList, contentDescription = null, tint = AppColors.AmberWarning, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Filter #${index + 1}", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                            }
                            IconButton(onClick = { conditions.removeAt(index) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Remove filter", tint = AppColors.RoseError.copy(alpha = 0.8f), modifier = Modifier.size(18.dp))
                            }
                        }
                        ConditionEditor(
                            condition = condition,
                            apps = installedApps,
                            onChange = { conditions[index] = it },
                        )
                    }
                }
            }

            // Connecting visual connector
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                        modifier = Modifier.size(24.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.ArrowDownward, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }

            // ---- NODE 3: ACTIONS (THEN DO) --------------------------------
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = AppColors.WhatsAppEmerald.copy(alpha = 0.15f),
                    ) {
                        Text(
                            "STEP 3: THEN DO (${actions.size} Actions)",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = AppColors.WhatsAppDark,
                        )
                    }

                    FilledTonalButton(
                        onClick = { showActionPicker = true },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = AppColors.IndigoPrimary,
                            contentColor = Color.White,
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Add Step", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (actions.isEmpty()) {
                item {
                    OutlinedCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                "No actions added yet",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                "Tap '+ Add Step' to choose what this rule will do (send WhatsApp message, click button, notify, etc.)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                        }
                    }
                }
            } else {
                itemsIndexed(actions) { index, action ->
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

            // ---- NODE 4: ADVANCED SETTINGS & LIMITS ------------------------
            item {
                Spacer(Modifier.height(8.dp))
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Execution Settings & Limits", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        }

                        Spacer(Modifier.height(12.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = cooldown,
                                onValueChange = { cooldown = it.filter(Char::isDigit) },
                                label = { Text("Cooldown (ms)") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                            )
                            OutlinedTextField(
                                value = dailyLimit,
                                onValueChange = { dailyLimit = it.filter(Char::isDigit) },
                                label = { Text("Daily Limit (0 = max)") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                            )
                        }

                        Spacer(Modifier.height(10.dp))

                        OutlinedTextField(
                            value = notes,
                            onValueChange = { notes = it },
                            label = { Text("Notes / Description") },
                            minLines = 2,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                        )
                    }
                }
            }

            item {
                Spacer(Modifier.height(40.dp))
            }
        }
    }

    if (showTriggerPicker) {
        PickerDialog(
            title = "Select Trigger (When)",
            groups = TriggerCatalog.categories.map { category ->
                category to TriggerCatalog.all.filter { it.category == category }.map { it.title }
            },
            onDismiss = { showTriggerPicker = false },
            onPick = { title ->
                showTriggerPicker = false
                trigger = TriggerCatalog.all.first { it.title == title }.create()
            },
        )
    }

    if (showActionPicker) {
        PickerDialog(
            title = "Add Action Step (Do)",
            groups = ActionCatalog.categories.map { category ->
                category to ActionCatalog.all.filter { it.category == category }.map { it.title }
            },
            onDismiss = { showActionPicker = false },
            onPick = { title ->
                showActionPicker = false
                val definition: ActionDef = ActionCatalog.all.first { it.title == title }
                actions.add(definition.create())
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

private fun indentAt(actions: List<ActionSpec>, index: Int): Int {
    var depth = 0
    for (i in 0 until index) depth += actions[i].indentDelta
    if (actions[index].indentDelta < 0) depth--
    return depth.coerceAtLeast(0)
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
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (indent * 16).dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(AppColors.IndigoPrimary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "${index + 1}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.IndigoPrimary,
                )
            }

            Spacer(Modifier.width(10.dp))

            Text(
                action.label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            IconButton(enabled = canMoveUp, onClick = onMoveUp, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.ArrowUpward, contentDescription = "Move up", modifier = Modifier.size(16.dp))
            }
            IconButton(enabled = canMoveDown, onClick = onMoveDown, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.ArrowDownward, contentDescription = "Move down", modifier = Modifier.size(16.dp))
            }
            IconButton(onClick = onEdit, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Edit, contentDescription = "Edit step", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Delete, contentDescription = "Remove step", modifier = Modifier.size(16.dp), tint = AppColors.RoseError)
            }
        }
    }
}

@Composable
private fun PickerDialog(
    title: String,
    groups: List<Pair<String, List<String>>>,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", fontWeight = FontWeight.Bold) } },
        title = {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        },
        text = {
            LazyColumn(Modifier.heightIn(max = 460.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                groups.forEach { (category, entries) ->
                    item(key = "header_$category") {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp, bottom = 4.dp),
                        ) {
                            Text(
                                category,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                    }
                    items(entries, key = { "$category/$it" }) { entry ->
                        FilledTonalButton(
                            onClick = { onPick(entry) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                contentColor = MaterialTheme.colorScheme.onSurface,
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            Text(
                                entry,
                                modifier = Modifier.fillMaxWidth(),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
            }
        },
    )
}
