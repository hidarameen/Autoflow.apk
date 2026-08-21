package com.autoflow.app.ui.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.autoflow.app.ui.catalog.FieldDef
import com.autoflow.app.ui.catalog.FieldType
import com.autoflow.app.ui.theme.AppColors
import com.autoflow.app.util.AppInfo
import com.autoflow.app.util.InstalledApp

/**
 * Renders one catalog-declared field. Every editable value in the app funnels through here,
 * so a new action gets a working editor for free.
 */
@Composable
fun FieldEditor(
    field: FieldDef,
    values: MutableMap<String, String>,
    apps: List<InstalledApp>,
) {
    Column(Modifier.fillMaxWidth()) {
        when (field.type) {
            FieldType.TEXT -> PlainTextField(field, values, singleLine = true)
            FieldType.MULTILINE -> PlainTextField(field, values, singleLine = false)

            FieldType.NUMBER -> OutlinedTextField(
                value = values[field.key].orEmpty(),
                onValueChange = { input ->
                    values[field.key] = input.filter { it.isDigit() || it == '-' }
                },
                label = { Text(field.label) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            )

            FieldType.BOOL -> Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(field.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Switch(
                    checked = values[field.key]?.toBooleanStrictOrNull() ?: false,
                    onCheckedChange = { values[field.key] = it.toString() },
                )
            }

            FieldType.ENUM -> Dropdown(
                label = field.label,
                selected = values[field.key].orEmpty().ifBlank { field.options.firstOrNull().orEmpty() },
                options = field.options,
                onSelect = { index -> values[field.key] = field.options[index] },
            )

            FieldType.APP -> AppPickerField(
                label = field.label,
                selectedPackage = values[field.key].orEmpty(),
                apps = apps,
                onSelect = { values[field.key] = it },
            )

            FieldType.DAYS -> DayPicker(
                selected = values[field.key].orEmpty(),
                onChange = { values[field.key] = it },
            )

            FieldType.MATCH -> PlainTextField(field, values, singleLine = true)
        }

        if (field.help.isNotBlank()) {
            Text(
                field.help,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun PlainTextField(
    field: FieldDef,
    values: MutableMap<String, String>,
    singleLine: Boolean,
) {
    OutlinedTextField(
        value = values[field.key].orEmpty(),
        onValueChange = { values[field.key] = it },
        label = { Text(field.label) },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 3,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun Dropdown(
    label: String,
    selected: String,
    options: List<String>,
    onSelect: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
            shape = RoundedCornerShape(12.dp),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEachIndexed { index, option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelect(index)
                        expanded = false
                    },
                )
            }
        }
    }
}

/**
 * Rich App Picker with App Icon, App Name, Package Name, and Instant Search.
 */
@Composable
fun AppPickerField(
    label: String,
    selectedPackage: String,
    apps: List<InstalledApp>,
    onSelect: (String) -> Unit,
) {
    val context = LocalContext.current
    var showDialog by remember { mutableStateOf(false) }

    val selectedApp = remember(selectedPackage, apps) {
        apps.firstOrNull { it.packageName == selectedPackage }
    }
    val appIcon = remember(selectedPackage) {
        AppInfo.appIcon(context, selectedPackage)
    }

    OutlinedCard(
        onClick = { showDialog = true },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // App Icon box
            if (appIcon != null) {
                Image(
                    bitmap = appIcon,
                    contentDescription = selectedApp?.label,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp)),
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(AppColors.IndigoPrimary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Apps,
                        contentDescription = null,
                        tint = AppColors.IndigoPrimary,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = selectedApp?.label ?: if (selectedPackage.isBlank()) "Any application" else selectedPackage,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (selectedPackage.isNotBlank()) {
                    Text(
                        text = selectedPackage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = "Select app",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (showDialog) {
        AppPickerDialog(
            title = label,
            selectedPackage = selectedPackage,
            apps = apps,
            onDismiss = { showDialog = false },
            onSelect = {
                onSelect(it)
                showDialog = false
            },
        )
    }
}

@Composable
fun AppPickerDialog(
    title: String,
    selectedPackage: String,
    apps: List<InstalledApp>,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }

    val filteredApps = remember(apps, searchQuery) {
        if (searchQuery.isBlank()) apps
        else apps.filter {
            it.label.contains(searchQuery, ignoreCase = true) ||
                it.packageName.contains(searchQuery, ignoreCase = true)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close", fontWeight = FontWeight.Bold) }
        },
        title = {
            Column {
                Text("Select Application", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Choose target app with icon & name", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search apps (e.g. WhatsApp)...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
                )

                LazyColumn(
                    modifier = Modifier.heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    // Option for "Any app"
                    item {
                        AppPickerRow(
                            icon = null,
                            label = "Any application",
                            packageName = "All apps (Universal match)",
                            selected = selectedPackage.isBlank(),
                            onClick = { onSelect("") },
                        )
                    }

                    items(filteredApps, key = { it.packageName }) { app ->
                        val icon = remember(app.packageName) {
                            AppInfo.appIcon(context, app.packageName)
                        }
                        AppPickerRow(
                            icon = icon,
                            label = app.label,
                            packageName = app.packageName,
                            selected = selectedPackage == app.packageName,
                            onClick = { onSelect(app.packageName) },
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun AppPickerRow(
    icon: androidx.compose.ui.graphics.ImageBitmap?,
    label: String,
    packageName: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Image(
                    bitmap = icon,
                    contentDescription = label,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp)),
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(AppColors.IndigoPrimary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Apps,
                        contentDescription = null,
                        tint = AppColors.IndigoPrimary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (selected) {
                Surface(
                    shape = CircleShape,
                    color = AppColors.IndigoPrimary,
                    modifier = Modifier.size(22.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
        }
    }
}

private val DAY_LABELS = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DayPicker(selected: String, onChange: (String) -> Unit) {
    val chosen = remember(selected) {
        selected.split(',').mapNotNull { it.trim().toIntOrNull() }.toSet()
    }

    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        DAY_LABELS.forEachIndexed { index, label ->
            val day = index + 1
            FilterChip(
                selected = day in chosen,
                onClick = {
                    val updated = if (day in chosen) chosen - day else chosen + day
                    onChange(updated.sorted().joinToString(","))
                },
                label = { Text(label) },
            )
        }
    }
}
