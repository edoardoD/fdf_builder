package manutenzioni.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Colors
import androidx.compose.material.LocalContentColor as LocalM2ContentColor
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Shapes
import androidx.compose.material.Typography
import androidx.compose.material3.LocalContentColor as LocalM3ContentColor
import androidx.compose.material3.MaterialTheme as Material3Theme
import androidx.compose.material3.Shapes as M3Shapes
import androidx.compose.material3.Typography as M3Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Stitch MCP Generated Palette
val PrimaryBlue = Color(0xFF3366FF)
val PrimaryBlueHover = Color(0xFF004BE3)
val SlateBackground = Color(0xFFF8FAFC)
val SlateBorder = Color(0xFFE2E8F0)
val TextPrimary = Color(0xFF191C1E)
val TextSecondary = Color(0xFF64748B)
val TextTertiary = Color(0xFF1E293B)
val LightSurface = Color(0xFFFFFFFF)
val ErrorRed = Color(0xFFBA1A1A)

// Spreadsheet & Grid Semantic Tokens
val GridBorderColor = Color(0xFFCBD5E1)
val GridHeaderBg = Color(0xFFF1F5F9)
val GridErrorBg = Color(0xFFFEF2F2)
val GridErrorBorder = Color(0xFFEF4444)

// Material 2 Colors mapped to the Stitch theme
val ManutenzioniColors = Colors(
    primary = PrimaryBlue,
    primaryVariant = PrimaryBlueHover,
    secondary = TextSecondary,
    secondaryVariant = TextTertiary,
    background = SlateBackground,
    surface = LightSurface,
    error = ErrorRed,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    onError = Color.White,
    isLight = true
)

// Material 3 ColorScheme mapped to the exact same Stitch / Slate palette (zero default M3 purple tint)
val ManutenzioniColorSchemeM3 = lightColorScheme(
    primary = PrimaryBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDBE4FF),
    onPrimaryContainer = PrimaryBlueHover,
    inversePrimary = Color(0xFF9BB4FF),
    secondary = TextSecondary,
    onSecondary = Color.White,
    secondaryContainer = GridHeaderBg,
    onSecondaryContainer = TextTertiary,
    tertiary = TextTertiary,
    onTertiary = Color.White,
    tertiaryContainer = SlateBorder,
    onTertiaryContainer = TextPrimary,
    background = SlateBackground,
    onBackground = TextPrimary,
    surface = LightSurface,
    onSurface = TextPrimary,
    surfaceVariant = GridHeaderBg,
    onSurfaceVariant = TextSecondary,
    surfaceTint = PrimaryBlue,
    inverseSurface = TextTertiary,
    inverseOnSurface = SlateBackground,
    surfaceBright = LightSurface,
    surfaceDim = SlateBorder,
    surfaceContainerLowest = LightSurface,
    surfaceContainerLow = SlateBackground,
    surfaceContainer = GridHeaderBg,
    surfaceContainerHigh = SlateBorder,
    surfaceContainerHighest = GridBorderColor,
    outline = GridBorderColor,
    outlineVariant = SlateBorder,
    scrim = TextPrimary,
    error = ErrorRed,
    onError = Color.White,
    errorContainer = GridErrorBg,
    onErrorContainer = Color(0xFF991B1B)
)

// Stitch Typography (Inter)
val InterFontFamily = FontFamily.Default // In a real app we'd load Inter.ttf here

val ManutenzioniTypography = Typography(
    h5 = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        letterSpacing = (-0.01).sp
    ),
    h6 = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp
    ),
    subtitle1 = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp
    ),
    body1 = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp
    ),
    body2 = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp
    ),
    button = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp
    ),
    caption = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        letterSpacing = 0.05.sp
    )
)

val ManutenzioniTypographyM3 = M3Typography(
    headlineSmall = ManutenzioniTypography.h5,
    titleLarge = ManutenzioniTypography.h6,
    titleMedium = ManutenzioniTypography.subtitle1,
    bodyLarge = ManutenzioniTypography.body1,
    bodyMedium = ManutenzioniTypography.body2,
    bodySmall = ManutenzioniTypography.caption,
    labelLarge = ManutenzioniTypography.button,
    labelMedium = ManutenzioniTypography.caption
)

// Stitch Shapes (ROUND_EIGHT -> 8px)
val ManutenzioniShapes = Shapes(
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(16.dp)
)

val ManutenzioniShapesM3 = M3Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(16.dp)
)

@Composable
fun ManutenzioniTheme(
    content: @Composable () -> Unit
) {
    Material3Theme(
        colorScheme = ManutenzioniColorSchemeM3,
        typography = ManutenzioniTypographyM3,
        shapes = ManutenzioniShapesM3
    ) {
        MaterialTheme(
            colors = ManutenzioniColors,
            typography = ManutenzioniTypography,
            shapes = ManutenzioniShapes
        ) {
            CompositionLocalProvider(
                LocalM3ContentColor provides TextPrimary,
                LocalM2ContentColor provides TextPrimary,
                content = content
            )
        }
    }
}
