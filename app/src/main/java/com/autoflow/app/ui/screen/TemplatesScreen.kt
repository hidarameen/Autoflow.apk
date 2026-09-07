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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Widgets
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.autoflow.app.data.Rule
import com.autoflow.app.ui.components.EmptyState
import com.autoflow.app.ui.components.Glyphs
import com.autoflow.app.ui.components.IconBadge
import com.autoflow.app.ui.components.SectionCard
import com.autoflow.app.ui.components.TagPill
import com.autoflow.app.ui.theme.AppColors
import com.autoflow.app.ui.theme.LocalTokens
import com.autoflow.app.util.AppInfo
import com.autoflow.app.util.Templates

/**
 * Template gallery. Templates whose target app is missing stay visible but are marked,
 * because the fix is usually installing the app rather than picking another template.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplatesScreen(
    onUse: (Rule) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }

    val visible = remember(selectedCategory, query) {
        Templates.all
            .filter { selectedCategory == null || it.category == selectedCategory }
            .filter {
                query.isBlank() ||
                    it.title.contains(query, ignoreCase = true) ||
                    it.summary.contains(query, ignoreCase = true)
            }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Templates",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "${Templates.all.size} ready-made automations",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
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
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search templates…") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Default.Clear, "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    item {
                        CategoryChip("All", selectedCategory == null) { selectedCategory = null }
                    }
                    items(Templates.categories) { category ->
                        CategoryChip(category, selectedCategory == category) {
                            selectedCategory = if (selectedCategory == category) null else category
                        }
                    }
                }
            }

            if (visible.isEmpty()) {
                item {
                    SectionCard {
                        EmptyState(
                            icon = Icons.Default.Widgets,
                            title = "Nothing matches",
                            message = "Try a different search or category.",
                        )
                    }
                }
            }

            items(visible, key = { it.title }) { template ->
                val missing = template.requiresPackage.isNotBlank() &&
                    !AppInfo.isInstalled(context, template.requiresPackage)

                TemplateCard(
                    title = template.title,
                    summary = template.summary,
                    category = template.category,
                    missingPackage = if (missing) template.requiresPackage else null,
                    onUse = { onUse(template.build()) },
                )
            }
        }
    }
}

@Composable
private fun CategoryChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val tokens = LocalTokens.current
    Box(
        Modifier
            .clip(RoundedCornerShape(11.dp))
            .background(if (selected) AppColors.IndigoPrimary else tokens.subtleSurface)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TemplateCard(
    title: String,
    summary: String,
    category: String,
    missingPackage: String?,
    onUse: () -> Unit,
) {
    SectionCard(onClick = onUse) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                IconBadge(
                    Glyphs.triggerIcon(category),
                    Glyphs.triggerTint(category),
                    size = 38,
                )
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TagPill(category, Glyphs.triggerTint(category))

                if (missingPackage != null) {
                    Spacer(Modifier.width(7.dp))
                    TagPill("not installed", AppColors.AmberWarning)
                }

                Spacer(Modifier.weight(1f))

                TextButton(onClick = onUse) {
                    Icon(Icons.Default.Download, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Use", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
