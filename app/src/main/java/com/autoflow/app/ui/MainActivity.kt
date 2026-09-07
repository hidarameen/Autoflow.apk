package com.autoflow.app.ui

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.autoflow.app.data.Rule
import com.autoflow.app.engine.AutoFlowForegroundService
import com.autoflow.app.engine.RuleEngine
import com.autoflow.app.ui.screen.HomeScreen
import com.autoflow.app.ui.screen.LogScreen
import com.autoflow.app.ui.screen.RuleWizardScreen
import com.autoflow.app.ui.screen.TemplatesScreen
import com.autoflow.app.ui.theme.AppColors
import com.autoflow.app.ui.theme.AutoFlowTheme

private sealed interface Destination {
    data object Home : Destination
    data object Logs : Destination
    data object Templates : Destination
    data class Editor(val rule: Rule?) : Destination
}

class MainActivity : ComponentActivity() {

    private var viewModelRef: AutoFlowViewModel? = null

    private val requestPermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        RuleEngine.init(this)
        AutoFlowForegroundService.start(this)
        requestOptionalPermissions()

        setContent {
            AutoFlowTheme {
                AutoFlowApp()
            }
        }
    }

    private fun requestOptionalPermissions() {
        val wanted = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
            add(Manifest.permission.RECEIVE_SMS)
            add(Manifest.permission.SEND_SMS)
        }
        requestPermissions.launch(wanted.toTypedArray())
    }

    override fun onResume() {
        super.onResume()
        viewModelRef?.refreshSetup()
    }

    @Composable
    private fun AutoFlowApp() {
        val viewModel: AutoFlowViewModel = viewModel()
        viewModelRef = viewModel

        var destination by remember { mutableStateOf<Destination>(Destination.Home) }
        val rules by viewModel.rules.collectAsStateWithLifecycle()
        val logs by viewModel.logs.collectAsStateWithLifecycle()

        when (val current = destination) {
            is Destination.Editor -> RuleWizardScreen(
                existing = current.rule,
                apps = viewModel.apps,
                onSave = {
                    viewModel.save(it)
                    destination = Destination.Home
                },
                onCancel = { destination = Destination.Home },
            )

            else -> {
                Scaffold(
                    bottomBar = {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            tonalElevation = 8.dp,
                            modifier = Modifier.clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)),
                        ) {
                            NavigationBarItem(
                                selected = destination is Destination.Home,
                                onClick = { destination = Destination.Home },
                                icon = {
                                    Icon(Icons.Default.Bolt, contentDescription = "Automations")
                                },
                                label = { Text("Automations", fontWeight = FontWeight.Bold) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Color.White,
                                    indicatorColor = AppColors.IndigoPrimary,
                                    selectedTextColor = AppColors.IndigoPrimary,
                                ),
                            )

                            NavigationBarItem(
                                selected = destination is Destination.Templates,
                                onClick = { destination = Destination.Templates },
                                icon = {
                                    Icon(Icons.Default.Widgets, contentDescription = "Templates")
                                },
                                label = { Text("Templates", fontWeight = FontWeight.Bold) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Color.White,
                                    indicatorColor = AppColors.IndigoPrimary,
                                    selectedTextColor = AppColors.IndigoPrimary,
                                ),
                            )

                            NavigationBarItem(
                                selected = destination is Destination.Logs,
                                onClick = { destination = Destination.Logs },
                                icon = {
                                    Icon(Icons.AutoMirrored.Filled.Article, contentDescription = "Activity")
                                },
                                label = { Text("Activity", fontWeight = FontWeight.Bold) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Color.White,
                                    indicatorColor = AppColors.IndigoPrimary,
                                    selectedTextColor = AppColors.IndigoPrimary,
                                ),
                            )
                        }
                    },
                ) { padding ->
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = padding.calculateBottomPadding()),
                    ) {
                        when (destination) {
                            Destination.Home -> HomeScreen(
                                viewModel = viewModel,
                                onAddRule = { destination = Destination.Editor(null) },
                                onEditRule = { destination = Destination.Editor(it) },
                                onOpenLogs = { destination = Destination.Logs },
                                onOpenTemplates = { destination = Destination.Templates },
                            )

                            Destination.Logs -> LogScreen(
                                viewModel = viewModel,
                                onBack = { destination = Destination.Home },
                            )

                            Destination.Templates -> TemplatesScreen(
                                onUse = { rule ->
                                    destination = Destination.Editor(rule)
                                },
                                onBack = { destination = Destination.Home },
                            )

                            else -> {}
                        }
                    }
                }
            }
        }
    }
}
