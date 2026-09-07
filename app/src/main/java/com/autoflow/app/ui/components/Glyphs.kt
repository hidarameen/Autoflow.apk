package com.autoflow.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Feed
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.autoflow.app.ui.theme.AppColors

/**
 * Maps catalogue categories to an icon and a colour.
 *
 * Categories come from the trigger/action catalogues as free text, so every lookup falls
 * back to a neutral glyph rather than crashing on an unrecognised name.
 */
object Glyphs {

    /** Covers both trigger categories and the template gallery's per-app categories. */
    fun triggerIcon(category: String): ImageVector = when (category.lowercase()) {
        "apps" -> Icons.Default.Notifications
        "time" -> Icons.Default.Schedule
        "messaging" -> Icons.Default.Chat
        "device" -> Icons.Default.PhoneAndroid
        "manual" -> Icons.Default.TouchApp
        "whatsapp" -> Icons.Default.Chat
        "telegram" -> Icons.AutoMirrored.Filled.Send
        "social media" -> Icons.Default.Feed
        "bridges" -> Icons.Default.SwapHoriz
        "x" -> Icons.Default.Tag
        "instagram" -> Icons.Default.PhotoCamera
        "facebook" -> Icons.Default.Groups
        "youtube" -> Icons.Default.SmartDisplay
        "browser" -> Icons.Default.Language
        else -> Icons.Default.Bolt
    }

    fun triggerTint(category: String): Color = when (category.lowercase()) {
        "apps" -> AppColors.IndigoPrimary
        "time" -> AppColors.VioletAccent
        "messaging" -> AppColors.TelegramSky
        "device" -> AppColors.AmberWarning
        "manual" -> AppColors.GreenSuccess
        "whatsapp" -> AppColors.WhatsAppEmerald
        "telegram" -> AppColors.TelegramSky
        "social media" -> AppColors.VioletAccent
        "bridges" -> AppColors.IndigoPrimary
        "x" -> Color(0xFF0F172A)
        "instagram" -> Color(0xFFE1306C)
        "facebook" -> Color(0xFF1877F2)
        "youtube" -> Color(0xFFFF0000)
        "browser" -> AppColors.TelegramSky
        else -> AppColors.IndigoPrimary
    }

    fun actionIcon(category: String): ImageVector = when (category.lowercase()) {
        "launch" -> Icons.Default.Apps
        "screen" -> Icons.Default.TouchApp
        "data" -> Icons.Default.ContentCopy
        "send" -> Icons.AutoMirrored.Filled.Send
        "feedback" -> Icons.AutoMirrored.Filled.VolumeUp
        "device" -> Icons.Default.Tune
        "flow" -> Icons.Default.AccountTree
        else -> Icons.Default.PlayCircle
    }

    fun actionTint(category: String): Color = when (category.lowercase()) {
        "launch" -> AppColors.IndigoPrimary
        "screen" -> AppColors.VioletAccent
        "data" -> AppColors.TelegramSky
        "send" -> AppColors.StepThen
        "feedback" -> AppColors.AmberWarning
        "device" -> Color(0xFF64748B)
        "flow" -> AppColors.RoseError
        else -> AppColors.IndigoPrimary
    }

    /** Icon shown on a rule card, chosen from the trigger's serialized kind. */
    fun ruleIcon(triggerLabel: String): ImageVector = when {
        triggerLabel.contains("notification", true) -> Icons.Default.Notifications
        triggerLabel.contains("daily", true) || triggerLabel.contains("every", true) ->
            Icons.Default.Schedule
        triggerLabel.contains("sms", true) -> Icons.Default.Chat
        triggerLabel.contains("battery", true) -> Icons.Default.BatteryFull
        triggerLabel.contains("wi-fi", true) -> Icons.Default.Language
        triggerLabel.contains("screen", true) -> Icons.Default.Smartphone
        triggerLabel.contains("opens", true) || triggerLabel.contains("closes", true) ->
            Icons.Default.Apps
        triggerLabel.contains("manual", true) -> Icons.Default.TouchApp
        triggerLabel.contains("boot", true) -> Icons.Default.PhoneAndroid
        triggerLabel.contains("clipboard", true) -> Icons.Default.ContentCopy
        else -> Icons.Default.Bolt
    }

    val filterIcon: ImageVector = Icons.Default.FilterAlt
    val actionListIcon: ImageVector = Icons.AutoMirrored.Filled.CallMade
    val storageIcon: ImageVector = Icons.Default.Storage
    val alarmIcon: ImageVector = Icons.Default.Alarm
}
