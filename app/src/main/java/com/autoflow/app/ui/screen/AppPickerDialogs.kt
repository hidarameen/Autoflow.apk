package com.autoflow.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.autoflow.app.ui.catalog.Platform
import com.autoflow.app.ui.catalog.Platforms
import com.autoflow.app.ui.components.IconBadge
import com.autoflow.app.ui.theme.LocalTokens

/**
 * First stage of the app-shaped pickers: choose which platform you want to automate,
 * then the caller opens that platform's own trigger or action list.
 */
@Composable
fun PlatformPickerDialog(
    title: String,
    countLabel: (Platform) -> String,
    onDismiss: () -> Unit,
    onPick: (Platform) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        title = {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 430.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                items(Platforms.all, key = { it.id }) { platform ->
                    PlatformRow(platform, countLabel(platform)) { onPick(platform) }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun PlatformRow(platform: Platform, subtitle: String, onClick: () -> Unit) {
    val tokens = LocalTokens.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(tokens.subtleSurface)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(platform.icon, platform.color, size = 38)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                platform.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Collects the parameters for a platform preset before it expands into steps.
 *
 * A preset like "send a WhatsApp message" becomes three separate actions, so asking for the
 * number and the body once here is far clearer than making the user edit each generated
 * step afterwards.
 */
@Composable
fun PlatformActionConfigDialog(
    platform: Platform,
    preset: com.autoflow.app.ui.catalog.PlatformAction,
    apps: List<com.autoflow.app.util.InstalledApp>,
    onDismiss: () -> Unit,
    onConfirm: (Map<String, String>) -> Unit,
) {
    val values = androidx.compose.runtime.remember(preset.id) {
        androidx.compose.runtime.mutableStateMapOf<String, String>()
    }
    val scroll = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(platform.icon, platform.color, size = 32)
                Spacer(Modifier.width(10.dp))
                Text(
                    preset.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(scroll),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (preset.description.isNotBlank()) {
                    Text(
                        preset.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                preset.fields.forEach { field ->
                    FieldEditor(field, values, apps)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(values.toMap()) }) {
                Text("Add steps", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
