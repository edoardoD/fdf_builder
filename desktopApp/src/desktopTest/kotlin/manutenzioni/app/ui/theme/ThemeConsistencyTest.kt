package manutenzioni.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class ThemeConsistencyTest {

    @Test
    fun material2AndMaterial3ColorSchemesShareExactBrandAndSlatePalette() {
        assertEquals(PrimaryBlue, ManutenzioniColors.primary)
        assertEquals(PrimaryBlue, ManutenzioniColorSchemeM3.primary)

        assertEquals(TextSecondary, ManutenzioniColors.secondary)
        assertEquals(TextSecondary, ManutenzioniColorSchemeM3.secondary)

        assertEquals(TextTertiary, ManutenzioniColorSchemeM3.tertiary)

        assertEquals(SlateBackground, ManutenzioniColors.background)
        assertEquals(SlateBackground, ManutenzioniColorSchemeM3.background)

        assertEquals(LightSurface, ManutenzioniColors.surface)
        assertEquals(LightSurface, ManutenzioniColorSchemeM3.surface)

        assertEquals(ErrorRed, ManutenzioniColors.error)
        assertEquals(ErrorRed, ManutenzioniColorSchemeM3.error)

        assertEquals(Color.White, ManutenzioniColorSchemeM3.onPrimary)
        assertEquals(Color.White, ManutenzioniColorSchemeM3.onSecondary)
        assertEquals(Color.White, ManutenzioniColorSchemeM3.onTertiary)
        assertEquals(TextPrimary, ManutenzioniColorSchemeM3.onBackground)
        assertEquals(TextPrimary, ManutenzioniColorSchemeM3.onSurface)
        assertEquals(Color.White, ManutenzioniColorSchemeM3.onError)
    }

    @Test
    fun material3SurfaceContainersAndOutlinesHaveZeroDefaultPurpleTint() {
        val defaultM3PurpleAndLavenderColors = setOf(
            Color(0xFFD0BCFF),
            Color(0xFFFEF7FF),
            Color(0xFFDED8E1),
            Color(0xFF322F35),
            Color(0xFFF5EFF7),
            Color(0xFF6750A4),
            Color(0xFFEADDFF),
            Color(0xFF625B71),
            Color(0xFF7D5260),
            Color(0xFFE7E0EC),
            Color(0xFFF7F2FA),
            Color(0xFFF3EDF7),
            Color(0xFF79747E),
            Color(0xFF21005D),
            Color(0xFF4F378B),
            Color(0xFF1D192B),
            Color(0xFFE8DEF8),
            Color(0xFF31111D),
            Color(0xFFFFD8E4),
            Color(0xFF49454F),
            Color(0xFFCAC4D0),
            Color(0xFFECE6F0),
            Color(0xFFE6E0E9),
            Color(0xFF1D1B20)
        )

        assertEquals(Color(0xFF9BB4FF), ManutenzioniColorSchemeM3.inversePrimary)
        assertEquals(LightSurface, ManutenzioniColorSchemeM3.surfaceBright)
        assertEquals(SlateBorder, ManutenzioniColorSchemeM3.surfaceDim)
        assertEquals(TextTertiary, ManutenzioniColorSchemeM3.inverseSurface)
        assertEquals(SlateBackground, ManutenzioniColorSchemeM3.inverseOnSurface)
        assertEquals(TextPrimary, ManutenzioniColorSchemeM3.scrim)

        assertEquals(GridHeaderBg, ManutenzioniColorSchemeM3.surfaceVariant)
        assertEquals(TextSecondary, ManutenzioniColorSchemeM3.onSurfaceVariant)
        assertEquals(LightSurface, ManutenzioniColorSchemeM3.surfaceContainerLowest)
        assertEquals(SlateBackground, ManutenzioniColorSchemeM3.surfaceContainerLow)
        assertEquals(GridHeaderBg, ManutenzioniColorSchemeM3.surfaceContainer)
        assertEquals(SlateBorder, ManutenzioniColorSchemeM3.surfaceContainerHigh)
        assertEquals(GridBorderColor, ManutenzioniColorSchemeM3.surfaceContainerHighest)
        assertEquals(GridBorderColor, ManutenzioniColorSchemeM3.outline)
        assertEquals(SlateBorder, ManutenzioniColorSchemeM3.outlineVariant)
        assertEquals(GridErrorBg, ManutenzioniColorSchemeM3.errorContainer)

        val allColorSchemeProperties = mapOf(
            "primary" to ManutenzioniColorSchemeM3.primary,
            "onPrimary" to ManutenzioniColorSchemeM3.onPrimary,
            "primaryContainer" to ManutenzioniColorSchemeM3.primaryContainer,
            "onPrimaryContainer" to ManutenzioniColorSchemeM3.onPrimaryContainer,
            "inversePrimary" to ManutenzioniColorSchemeM3.inversePrimary,
            "secondary" to ManutenzioniColorSchemeM3.secondary,
            "onSecondary" to ManutenzioniColorSchemeM3.onSecondary,
            "secondaryContainer" to ManutenzioniColorSchemeM3.secondaryContainer,
            "onSecondaryContainer" to ManutenzioniColorSchemeM3.onSecondaryContainer,
            "tertiary" to ManutenzioniColorSchemeM3.tertiary,
            "onTertiary" to ManutenzioniColorSchemeM3.onTertiary,
            "tertiaryContainer" to ManutenzioniColorSchemeM3.tertiaryContainer,
            "onTertiaryContainer" to ManutenzioniColorSchemeM3.onTertiaryContainer,
            "background" to ManutenzioniColorSchemeM3.background,
            "onBackground" to ManutenzioniColorSchemeM3.onBackground,
            "surface" to ManutenzioniColorSchemeM3.surface,
            "onSurface" to ManutenzioniColorSchemeM3.onSurface,
            "surfaceVariant" to ManutenzioniColorSchemeM3.surfaceVariant,
            "onSurfaceVariant" to ManutenzioniColorSchemeM3.onSurfaceVariant,
            "surfaceTint" to ManutenzioniColorSchemeM3.surfaceTint,
            "inverseSurface" to ManutenzioniColorSchemeM3.inverseSurface,
            "inverseOnSurface" to ManutenzioniColorSchemeM3.inverseOnSurface,
            "error" to ManutenzioniColorSchemeM3.error,
            "onError" to ManutenzioniColorSchemeM3.onError,
            "errorContainer" to ManutenzioniColorSchemeM3.errorContainer,
            "onErrorContainer" to ManutenzioniColorSchemeM3.onErrorContainer,
            "outline" to ManutenzioniColorSchemeM3.outline,
            "outlineVariant" to ManutenzioniColorSchemeM3.outlineVariant,
            "scrim" to ManutenzioniColorSchemeM3.scrim,
            "surfaceBright" to ManutenzioniColorSchemeM3.surfaceBright,
            "surfaceDim" to ManutenzioniColorSchemeM3.surfaceDim,
            "surfaceContainer" to ManutenzioniColorSchemeM3.surfaceContainer,
            "surfaceContainerHigh" to ManutenzioniColorSchemeM3.surfaceContainerHigh,
            "surfaceContainerHighest" to ManutenzioniColorSchemeM3.surfaceContainerHighest,
            "surfaceContainerLow" to ManutenzioniColorSchemeM3.surfaceContainerLow,
            "surfaceContainerLowest" to ManutenzioniColorSchemeM3.surfaceContainerLowest
        )

        allColorSchemeProperties.forEach { (tokenName, color) ->
            assertNotEquals(
                true,
                color in defaultM3PurpleAndLavenderColors,
                "Unexpected M3 default purple/lavender tint on token '$tokenName': $color"
            )
        }
    }

    @Test
    fun material2AndMaterial3ShapesAndTypographyMatch() {
        assertEquals(RoundedCornerShape(4.dp), ManutenzioniShapesM3.small)
        assertEquals(RoundedCornerShape(8.dp), ManutenzioniShapesM3.medium)
        assertEquals(RoundedCornerShape(16.dp), ManutenzioniShapesM3.large)

        assertEquals(ManutenzioniTypography.h5, ManutenzioniTypographyM3.headlineSmall)
        assertEquals(ManutenzioniTypography.h6, ManutenzioniTypographyM3.titleLarge)
        assertEquals(ManutenzioniTypography.subtitle1, ManutenzioniTypographyM3.titleMedium)
        assertEquals(ManutenzioniTypography.body1, ManutenzioniTypographyM3.bodyLarge)
        assertEquals(ManutenzioniTypography.body2, ManutenzioniTypographyM3.bodyMedium)
        assertEquals(ManutenzioniTypography.button, ManutenzioniTypographyM3.labelLarge)
        assertEquals(ManutenzioniTypography.caption, ManutenzioniTypographyM3.bodySmall)
    }
}
