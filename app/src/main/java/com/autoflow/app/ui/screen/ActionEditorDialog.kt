package com.autoflow.app.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.autoflow.app.data.ActionSpec
import com.autoflow.app.data.ConditionSpec
import com.autoflow.app.ui.catalog.ActionCatalog
import com.autoflow.app.ui.catalog.ConditionCatalog
import com.autoflow.app.util.InstalledApp

/**
 * Edits a single step. Field values live in one string map and are converted back to a typed
 * [ActionSpec] by the catalog on confirm, so there is one code path for every action type.
 */
@Composable
fun ActionEditorDialog(
    action: ActionSpec,
    apps: List<InstalledApp>,
    onDismiss: () -> Unit,
    onConfirm: (ActionSpec) -> Unit,
) {
    val definition = remember(action) { ActionCatalog.defFor(action) }
    val values = remember(action) {
        mutableStateMapOf<String, String>().apply { putAll(definition.read(action)) }
    }

    // The If action carries a nested condition, which needs its own catalog-driven editor.
    var condition by remember(action) {
        mutableStateOf((action as? ActionSpec.If)?.condition ?: ConditionSpec.Text())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(definition.title) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (definition.help.isNotBlank()) {
                    Text(definition.help, style = MaterialTheme.typography.bodySmall)
                }

                definition.fields.forEach { field ->
                    FieldEditor(field = field, values = values, apps = apps)
                }

                if (action is ActionSpec.If) {
                    HorizontalDivider()
                    Text("Condition", style = MaterialTheme.typography.titleSmall)
                    ConditionEditor(
                        condition = condition,
                        apps = apps,
                        onChange = { condition = it },
                    )
                }

                if (definition.fields.isEmpty() && action !is ActionSpec.If) {
                    Text(
                        "This step has nothing to configure.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val updated = if (action is ActionSpec.If) {
                        ActionSpec.If(condition)
                    } else {
                        definition.write(action, values.toMap())
                    }
                    onConfirm(updated)
                },
            ) { Text("Done") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * Inline editor for one condition: a type dropdown plus that type's declared fields.
 * Shared by the If action and the rule-level filter list.
 */
@Composable
fun ConditionEditor(
    condition: ConditionSpec,
    apps: List<InstalledApp>,
    onChange: (ConditionSpec) -> Unit,
) {
    val definition = remember(condition) { ConditionCatalog.defFor(condition) }
    val values = remember(condition) {
        mutableStateMapOf<String, String>().apply { putAll(definition.read(condition)) }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Dropdown(
            label = "Condition type",
            selected = definition.title,
            options = ConditionCatalog.all.map { it.title },
            onSelect = { index -> onChange(ConditionCatalog.all[index].create()) },
        )

        if (definition.help.isNotBlank()) {
            Text(definition.help, style = MaterialTheme.typography.bodySmall)
        }

        definition.fields.forEach { field ->
            FieldEditor(
                field = field,
                values = ChangeTrackingMap(values) { onChange(definition.write(values.toMap())) },
                apps = apps,
            )
        }
    }
}

/**
 * Writes through to the backing snapshot map and notifies after each edit, so the caller
 * always holds a current [ConditionSpec] without a separate Save step.
 */
private class ChangeTrackingMap(
    private val backing: MutableMap<String, String>,
    private val onWrite: () -> Unit,
) : MutableMap<String, String> by backing {

    override fun put(key: String, value: String): String? {
        val previous = backing.put(key, value)
        onWrite()
        return previous
    }
}
