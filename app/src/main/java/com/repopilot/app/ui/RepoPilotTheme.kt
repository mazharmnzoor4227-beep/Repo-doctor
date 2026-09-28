package com.repopilot.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object RpColors {
    val Background = Color(0xFF050506)
    val Surface = Color(0xFF0B0C0E)
    val SurfaceRaised = Color(0xFF111317)
    val SurfaceSoft = Color(0xFF17191E)
    val Border = Color(0xFF252830)
    val BorderStrong = Color(0xFF343844)
    val Text = Color(0xFFF5F6F8)
    val TextMuted = Color(0xFF9298A3)
    val TextDim = Color(0xFF656B76)
    val Success = Color(0xFF67D59A)
    val Warning = Color(0xFFF3C76B)
    val Error = Color(0xFFFF7D7D)
    val Info = Color(0xFF8EB7FF)
}

private val RepoPilotColors = darkColorScheme(
    primary = RpColors.Text,
    onPrimary = RpColors.Background,
    secondary = RpColors.TextMuted,
    onSecondary = RpColors.Text,
    background = RpColors.Background,
    onBackground = RpColors.Text,
    surface = RpColors.Surface,
    onSurface = RpColors.Text,
    surfaceVariant = RpColors.SurfaceRaised,
    onSurfaceVariant = RpColors.TextMuted,
    outline = RpColors.Border,
    error = RpColors.Error,
    onError = RpColors.Background,
)

private val RepoPilotTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 30.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.4).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 19.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
)

@Composable
fun RepoPilotTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = RepoPilotColors,
        typography = RepoPilotTypography,
        content = content,
    )
}
