package com.nothingai.capture.ui.theme

import android.content.SharedPreferences
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nothingai.capture.ui.settings.SettingsPrefs

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

// "Dark Ink": warm charcoal surfaces, parchment-toned text, same Coral/Sage/Mustard accents.
private val InkSurface = Color(0xFF1A1A18)
private val InkSurfaceVariant = Color(0xFF262622)
private val InkCard = Color(0xFF23231F)
private val InkNote = Color(0xFF20201C)
private val InkField = Color(0xFF2B2B26)
private val ParchmentText = Color(0xFFEDE7DB)
private val ParchmentTextMuted = Color(0xFFB5AEA1)
private val ParchmentTextFaint = Color(0xFF847E74)
private val CoralDeep = Color(0xFF4A2C25)
private val SageDeep = Color(0xFF27352B)
private val MustardDeep = Color(0xFF3E3418)

private val LightNotebookColors = lightColorScheme(
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

private val DarkInkColors = darkColorScheme(
    primary = Coral,
    // Dark ink on the mid-tone accents: white-on-Coral is only ~2.9:1.
    onPrimary = Ink,
    primaryContainer = CoralDeep,
    onPrimaryContainer = ParchmentText,
    secondary = Sage,
    onSecondary = Ink,
    secondaryContainer = SageDeep,
    onSecondaryContainer = ParchmentText,
    tertiary = Mustard,
    onTertiary = Ink,
    tertiaryContainer = MustardDeep,
    onTertiaryContainer = ParchmentText,
    background = InkSurface,
    onBackground = ParchmentText,
    surface = InkSurface,
    onSurface = ParchmentText,
    surfaceVariant = InkSurfaceVariant,
    onSurfaceVariant = ParchmentTextMuted,
    outline = ParchmentTextFaint,
    outlineVariant = Color(0xFF3A3630),
)

/**
 * Colors the screens need that Material's roles do not cover (paper-like card fills, the soft
 * accent washes used for chips/pills, and the two dimmer text tiers).
 */
@Immutable
class NothingSemanticColors(
    val cardSurface: Color,
    val noteSurface: Color,
    val fieldSurface: Color,
    val mutedText: Color,
    val faintText: Color,
    val accentSoft: Color,
    val warningSoft: Color,
    val successSoft: Color,
    val onSoft: Color,
)

private val LightSemanticColors = NothingSemanticColors(
    cardSurface = Color.White,
    noteSurface = Color(0xFFFFFCF5),
    fieldSurface = Color.White,
    mutedText = InkLight,
    faintText = InkFaint,
    accentSoft = CoralLight,
    warningSoft = MustardLight,
    successSoft = SageLight,
    onSoft = Ink,
)

private val DarkSemanticColors = NothingSemanticColors(
    cardSurface = InkCard,
    noteSurface = InkNote,
    fieldSurface = InkField,
    mutedText = ParchmentTextMuted,
    faintText = ParchmentTextFaint,
    accentSoft = CoralDeep,
    warningSoft = MustardDeep,
    successSoft = SageDeep,
    onSoft = ParchmentText,
)

private val LocalNothingSemanticColors = staticCompositionLocalOf { LightSemanticColors }

/** Theme-aware semantic colors: `MaterialTheme.semantic.mutedText`. */
val MaterialTheme.semantic: NothingSemanticColors
    @Composable @ReadOnlyComposable get() = LocalNothingSemanticColors.current

private fun nothingTypography(primaryText: Color, mutedText: Color, faintText: Color) = Typography(
    displayLarge = TextStyle(fontFamily = caveat, fontWeight = FontWeight.Bold, fontSize = 40.sp, lineHeight = 48.sp, color = primaryText),
    displayMedium = TextStyle(fontFamily = caveat, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 40.sp, color = primaryText),
    displaySmall = TextStyle(fontFamily = caveat, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp, color = primaryText),
    headlineLarge = TextStyle(fontFamily = caveat, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp, color = primaryText),
    headlineMedium = TextStyle(fontFamily = caveat, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 30.sp, color = primaryText),
    headlineSmall = TextStyle(fontFamily = caveat, fontWeight = FontWeight.Normal, fontSize = 22.sp, lineHeight = 28.sp, color = primaryText),
    titleLarge = TextStyle(fontFamily = lora, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp, color = primaryText),
    titleMedium = TextStyle(fontFamily = lora, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 22.sp, color = primaryText),
    titleSmall = TextStyle(fontFamily = lora, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, color = primaryText),
    bodyLarge = TextStyle(fontFamily = lora, fontSize = 16.sp, lineHeight = 24.sp, color = primaryText),
    bodyMedium = TextStyle(fontFamily = lora, fontSize = 14.sp, lineHeight = 20.sp, color = primaryText),
    bodySmall = TextStyle(fontFamily = lora, fontSize = 12.sp, lineHeight = 16.sp, color = mutedText),
    labelLarge = TextStyle(fontFamily = lora, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, color = primaryText),
    labelMedium = TextStyle(fontFamily = lora, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, color = mutedText),
    labelSmall = TextStyle(fontFamily = caveat, fontSize = 12.sp, lineHeight = 16.sp, color = faintText),
)

private val LightTypography = nothingTypography(Ink, InkLight, InkFaint)
private val DarkTypography = nothingTypography(ParchmentText, ParchmentTextMuted, ParchmentTextFaint)

private val NothingShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/**
 * Reads the persisted theme choice and keeps it current, so changing the setting re-themes the
 * running app instead of waiting for a restart.
 */
@Composable
fun rememberThemeChoice(): State<ThemeChoice> {
    val context = LocalContext.current
    val prefs = remember(context) { SettingsPrefs.get(context) }
    val choice = remember(prefs) { mutableStateOf(ThemeChoice.fromPreference(prefs.getString(ThemeChoice.PREF_KEY, null))) }
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { changed, key ->
            // key is null when prefs are cleared wholesale.
            if (key == null || key == ThemeChoice.PREF_KEY) {
                choice.value = ThemeChoice.fromPreference(changed.getString(ThemeChoice.PREF_KEY, null))
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        choice.value = ThemeChoice.fromPreference(prefs.getString(ThemeChoice.PREF_KEY, null))
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return choice
}

@Composable
fun NothingTheme(choice: ThemeChoice = ThemeChoice.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (choice) {
        ThemeChoice.SYSTEM -> isSystemInDarkTheme()
        ThemeChoice.LIGHT -> false
        ThemeChoice.DARK -> true
    }
    CompositionLocalProvider(LocalNothingSemanticColors provides if (dark) DarkSemanticColors else LightSemanticColors) {
        MaterialTheme(
            colorScheme = if (dark) DarkInkColors else LightNotebookColors,
            typography = if (dark) DarkTypography else LightTypography,
            shapes = NothingShapes,
            content = content,
        )
    }
}
