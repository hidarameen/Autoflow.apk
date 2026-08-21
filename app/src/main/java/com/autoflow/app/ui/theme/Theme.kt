package com.autoflow.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ---- Vibrant Brand & Accent Colors ----------------------------------------
object AppColors {
    val IndigoPrimary = Color(0xFF6366F1)
    val IndigoDark = Color(0xFF4F46E5)
    val VioletAccent = Color(0xFF8B5CF6)
    val PurpleGlow = Color(0xFFA855F7)
    
    val WhatsAppEmerald = Color(0xFF10B981)
    val WhatsAppLight = Color(0xFF34D399)
    val WhatsAppDark = Color(0xFF059669)
    
    val TelegramSky = Color(0xFF0EA5E9)
    val TelegramLight = Color(0xFF38BDF8)
    
    val AmberWarning = Color(0xFFF59E0B)
    val RoseError = Color(0xFFF43F5E)
    val GreenSuccess = Color(0xFF22C55E)
    
    val SlateBackgroundDark = Color(0xFF0A0E17)
    val SlateSurfaceDark = Color(0xFF111827)
    val SlateCardDark = Color(0xFF1E293B)
    val SlateBorderDark = Color(0xFF334155)
    val SlateTextMutedDark = Color(0xFF94A3B8)
    
    val SlateBackgroundLight = Color(0xFFF8FAFC)
    val SlateSurfaceLight = Color(0xFFFFFFFF)
    val SlateCardLight = Color(0xFFF1F5F9)
    val SlateBorderLight = Color(0xFFE2E8F0)
    val SlateTextMutedLight = Color(0xFF64748B)

    val PrimaryGradient = Brush.horizontalGradient(listOf(IndigoPrimary, VioletAccent))
    val WhatsAppGradient = Brush.horizontalGradient(listOf(WhatsAppDark, WhatsAppEmerald))
    val TelegramGradient = Brush.horizontalGradient(listOf(TelegramSky, TelegramLight))
    val AmberGradient = Brush.horizontalGradient(listOf(Color(0xFFEA580C), AmberWarning))
    val HeroCardGradientDark = Brush.linearGradient(
        listOf(
            Color(0xFF1E1B4B),
            Color(0xFF0F172A),
            Color(0xFF111827),
        )
    )
    val HeroCardGradientLight = Brush.linearGradient(
        listOf(
            Color(0xFFEEF2FF),
            Color(0xFFE0E7FF),
            Color(0xFFF8FAFC),
        )
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
    onBackground = Color(0xFFF8FAFC),
    surface = AppColors.SlateSurfaceDark,
    onSurface = Color(0xFFF8FAFC),
    surfaceVariant = AppColors.SlateCardDark,
    onSurfaceVariant = Color(0xFFCBD5E1),
    outline = AppColors.SlateBorderDark,
    outlineVariant = Color(0xFF1E293B),
    error = AppColors.RoseError,
    onError = Color.White,
)

private val LightColorScheme = lightColorScheme(
    primary = AppColors.IndigoDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0E7FF),
    onPrimaryContainer = Color(0xFF312E81),
    secondary = AppColors.VioletAccent,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEDE9FE),
    onSecondaryContainer = Color(0xFF4C1D95),
    tertiary = AppColors.WhatsAppDark,
    background = AppColors.SlateBackgroundLight,
    onBackground = Color(0xFF0F172A),
    surface = AppColors.SlateSurfaceLight,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = AppColors.SlateCardLight,
    onSurfaceVariant = Color(0xFF475569),
    outline = AppColors.SlateBorderLight,
    outlineVariant = Color(0xFFCBD5E1),
    error = AppColors.RoseError,
    onError = Color.White,
)

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

val AppTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.5).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.25).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
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
        fontSize = 13.sp,
        lineHeight = 18.sp,
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
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        lineHeight = 12.sp,
    ),
)

@Composable
fun AutoFlowTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        shapes = AppShapes,
        typography = AppTypography,
        content = content,
    )
}
