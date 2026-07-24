package com.nothingai.capture.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val caveat = FontFamily.Cursive
private val lora = FontFamily.Serif

val Parchment = Color(0xFFF5F0E8)
val ParchmentDark = Color(0xFFEDE7DB)
val Ink = Color(0xFF2C2C2C)
val InkLight = Color(0xFF5A5A5A)
val InkFaint = Color(0xFF9E9E9E)
val Coral = Color(0xFFD4735E)
val CoralLight = Color(0xFFF2D4CC)
val Sage = Color(0xFF7BA487)
val SageLight = Color(0xFFD5E8D4)
val Mustard = Color(0xFFD4A84B)
val MustardLight = Color(0xFFF5E6C4)

private val NothingColors = lightColorScheme(
    primary = Coral,
    onPrimary = Color.White,
    primaryContainer = CoralLight,
    onPrimaryContainer = Ink,
    secondary = Sage,
    onSecondary = Color.White,
    secondaryContainer = SageLight,
    onSecondaryContainer = Ink,
    tertiary = Mustard,
    onTertiary = Color.White,
    tertiaryContainer = MustardLight,
    onTertiaryContainer = Ink,
    background = Parchment,
    onBackground = Ink,
    surface = Parchment,
    onSurface = Ink,
    surfaceVariant = ParchmentDark,
    onSurfaceVariant = InkLight,
    outline = InkFaint,
    outlineVariant = Color(0xFFD5CFC3),
)

private val NothingTypography = Typography(
    displayLarge = TextStyle(fontFamily = caveat, fontWeight = FontWeight.Bold, fontSize = 40.sp, lineHeight = 48.sp, color = Ink),
    displayMedium = TextStyle(fontFamily = caveat, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 40.sp, color = Ink),
    displaySmall = TextStyle(fontFamily = caveat, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp, color = Ink),
    headlineLarge = TextStyle(fontFamily = caveat, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp, color = Ink),
    headlineMedium = TextStyle(fontFamily = caveat, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 30.sp, color = Ink),
    headlineSmall = TextStyle(fontFamily = caveat, fontWeight = FontWeight.Normal, fontSize = 22.sp, lineHeight = 28.sp, color = Ink),
    titleLarge = TextStyle(fontFamily = lora, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp, color = Ink),
    titleMedium = TextStyle(fontFamily = lora, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 22.sp, color = Ink),
    titleSmall = TextStyle(fontFamily = lora, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, color = Ink),
    bodyLarge = TextStyle(fontFamily = lora, fontSize = 16.sp, lineHeight = 24.sp, color = Ink),
    bodyMedium = TextStyle(fontFamily = lora, fontSize = 14.sp, lineHeight = 20.sp, color = Ink),
    bodySmall = TextStyle(fontFamily = lora, fontSize = 12.sp, lineHeight = 16.sp, color = InkLight),
    labelLarge = TextStyle(fontFamily = lora, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, color = Ink),
    labelMedium = TextStyle(fontFamily = lora, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, color = InkLight),
    labelSmall = TextStyle(fontFamily = caveat, fontSize = 12.sp, lineHeight = 16.sp, color = InkFaint),
)

private val NothingShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun NothingTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = NothingColors,
        typography = NothingTypography,
        shapes = NothingShapes,
        content = content,
    )
}
