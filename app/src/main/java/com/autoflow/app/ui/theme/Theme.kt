package com.autoflow.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Brand palette. Deliberately NOT dynamic-colour: AutoFlow's whole UI encodes meaning in
 * colour (indigo = when, amber = only if, emerald = then), and letting the wallpaper
 * repaint those would destroy the one thing that makes a long rule readable at a glance.
 */
object AppColors {
    val IndigoPrimary = Color(0xFF6366F1)
    val IndigoDark = Color(0xFF4F46E5)
    val IndigoSoft = Color(0xFFEEF2FF)
    val VioletAccent = Color(0xFF8B5CF6)

    // Semantic step colours, used by every screen that shows a rule.
    val StepWhen = Color(0xFF6366F1)
    val StepIf = Color(0xFFF59E0B)
    val StepThen = Color(0xFF10B981)

    val WhatsAppEmerald = Color(0xFF10B981)
    val TelegramSky = Color(0xFF0EA5E9)

    val AmberWarning = Color(0xFFF59E0B)
    val RoseError = Color(0xFFF43F5E)
    val GreenSuccess = Color(0xFF22C55E)

    val SlateBackgroundDark = Color(0xFF0B1120)
    val SlateSurfaceDark = Color(0xFF131C2E)
    val SlateCardDark = Color(0xFF1C2739)
    val SlateBorderDark = Color(0xFF2C3A4F)

    val SlateBackgroundLight = Color(0xFFF7F8FC)
    val SlateSurfaceLight = Color(0xFFFFFFFF)
    val SlateCardLight = Color(0xFFF1F4F9)
    val SlateBorderLight = Color(0xFFE2E8F0)

    val PrimaryGradient = Brush.horizontalGradient(listOf(IndigoPrimary, VioletAccent))
}

/**
 * Tokens Material does not model. Kept in a CompositionLocal so a component can react to
 * the current theme without every call site passing colours down.
 */
data class AutoFlowTokens(
    val isDark: Boolean,
    val cardBorder: Color,
    val subtleSurface: Color,
    val railLine: Color,
)

val LocalTokens = staticCompositionLocalOf {
    AutoFlowTokens(
        isDark = false,
        cardBorder = AppColors.SlateBorderLight,
        subtleSurface = AppColors.SlateCardLight,
        railLine = AppColors.SlateBorderLight,
    )
}

private val DarkColorScheme = darkColorScheme(
    primary = AppColors.IndigoPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF312E81),
    onPrimaryContainer = Color(0xFFE0E7FF),
    secondary = AppColors.VioletAccent,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF4C1D95),
    onSecondaryContainer = Color(0xFFEDE9FE),
    tertiary = AppColors.WhatsAppEmerald,
    background = AppColors.SlateBackgroundDark,
    onBackground = Color(0xFFF1F5F9),
    surface = AppColors.SlateSurfaceDark,
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = AppColors.SlateCardDark,
    onSurfaceVariant = Color(0xFF9FB0C7),
    outline = AppColors.SlateBorderDark,
    outlineVariant = Color(0xFF223047),
    error = AppColors.RoseError,
    onError = Color.White,
)

private val LightColorScheme = lightColorScheme(
    primary = AppColors.IndigoDark,
    onPrimary = Color.White,
    primaryContainer = AppColors.IndigoSoft,
    onPrimaryContainer = Color(0xFF312E81),
    secondary = AppColors.VioletAccent,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEDE9FE),
    onSecondaryContainer = Color(0xFF4C1D95),
    tertiary = Color(0xFF059669),
    background = AppColors.SlateBackgroundLight,
    onBackground = Color(0xFF0F172A),
    surface = AppColors.SlateSurfaceLight,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = AppColors.SlateCardLight,
    onSurfaceVariant = Color(0xFF5A6B85),
    outline = AppColors.SlateBorderLight,
    outlineVariant = Color(0xFFCBD5E1),
    error = AppColors.RoseError,
    onError = Color.White,
)

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

val AppTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.6).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 21.sp,
        lineHeight = 27.sp,
        letterSpacing = (-0.4).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 23.sp,
        letterSpacing = (-0.2).sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 13.5.sp,
        lineHeight = 19.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.4.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 10.5.sp,
        lineHeight = 13.sp,
        letterSpacing = 0.5.sp,
    ),
)

@Composable
fun AutoFlowTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val tokens = AutoFlowTokens(
        isDark = darkTheme,
        cardBorder = if (darkTheme) AppColors.SlateBorderDark else AppColors.SlateBorderLight,
        subtleSurface = if (darkTheme) AppColors.SlateCardDark else AppColors.SlateCardLight,
        railLine = if (darkTheme) AppColors.SlateBorderDark else Color(0xFFDCE3ED),
    )

    CompositionLocalProvider(LocalTokens provides tokens) {
        MaterialTheme(
            colorScheme = colorScheme,
            shapes = AppShapes,
            typography = AppTypography,
            content = content,
        )
    }
}
