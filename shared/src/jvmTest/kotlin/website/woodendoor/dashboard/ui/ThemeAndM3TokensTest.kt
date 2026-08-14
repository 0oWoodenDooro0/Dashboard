package website.woodendoor.dashboard.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import website.woodendoor.dashboard.ui.theme.ContainerExited
import website.woodendoor.dashboard.ui.theme.ContainerPaused
import website.woodendoor.dashboard.ui.theme.ContainerRestarting
import website.woodendoor.dashboard.ui.theme.ContainerRunning
import website.woodendoor.dashboard.ui.theme.DarkColorScheme
import website.woodendoor.dashboard.ui.theme.DashboardShapes
import website.woodendoor.dashboard.ui.theme.DashboardTypography
import website.woodendoor.dashboard.ui.theme.LightColorScheme
import website.woodendoor.dashboard.ui.theme.StatusClosed
import website.woodendoor.dashboard.ui.theme.StatusHealthy
import website.woodendoor.dashboard.ui.theme.StatusNeutral
import website.woodendoor.dashboard.ui.theme.StatusUnreachable

class ThemeAndM3TokensTest {

    @Test
    fun `DarkColorScheme contains specified colors for all standard M3 roles`() {
        val scheme = DarkColorScheme

        // Primary roles
        assertTrue(scheme.primary.isSpecified, "primary should be specified")
        assertTrue(scheme.onPrimary.isSpecified, "onPrimary should be specified")
        assertTrue(scheme.primaryContainer.isSpecified, "primaryContainer should be specified")
        assertTrue(scheme.onPrimaryContainer.isSpecified, "onPrimaryContainer should be specified")

        // Secondary roles
        assertTrue(scheme.secondary.isSpecified, "secondary should be specified")
        assertTrue(scheme.onSecondary.isSpecified, "onSecondary should be specified")
        assertTrue(scheme.secondaryContainer.isSpecified, "secondaryContainer should be specified")
        assertTrue(scheme.onSecondaryContainer.isSpecified, "onSecondaryContainer should be specified")

        // Tertiary roles
        assertTrue(scheme.tertiary.isSpecified, "tertiary should be specified")
        assertTrue(scheme.onTertiary.isSpecified, "onTertiary should be specified")
        assertTrue(scheme.tertiaryContainer.isSpecified, "tertiaryContainer should be specified")
        assertTrue(scheme.onTertiaryContainer.isSpecified, "onTertiaryContainer should be specified")

        // Background & Surface roles
        assertTrue(scheme.background.isSpecified, "background should be specified")
        assertTrue(scheme.onBackground.isSpecified, "onBackground should be specified")
        assertTrue(scheme.surface.isSpecified, "surface should be specified")
        assertTrue(scheme.onSurface.isSpecified, "onSurface should be specified")
        assertTrue(scheme.surfaceVariant.isSpecified, "surfaceVariant should be specified")
        assertTrue(scheme.onSurfaceVariant.isSpecified, "onSurfaceVariant should be specified")

        // Surface Container hierarchy
        assertTrue(scheme.surfaceContainerLowest.isSpecified, "surfaceContainerLowest should be specified")
        assertTrue(scheme.surfaceContainerLow.isSpecified, "surfaceContainerLow should be specified")
        assertTrue(scheme.surfaceContainer.isSpecified, "surfaceContainer should be specified")
        assertTrue(scheme.surfaceContainerHigh.isSpecified, "surfaceContainerHigh should be specified")
        assertTrue(scheme.surfaceContainerHighest.isSpecified, "surfaceContainerHighest should be specified")

        // Outline roles
        assertTrue(scheme.outline.isSpecified, "outline should be specified")
        assertTrue(scheme.outlineVariant.isSpecified, "outlineVariant should be specified")

        // Error roles
        assertTrue(scheme.error.isSpecified, "error should be specified")
        assertTrue(scheme.onError.isSpecified, "onError should be specified")
        assertTrue(scheme.errorContainer.isSpecified, "errorContainer should be specified")
        assertTrue(scheme.onErrorContainer.isSpecified, "onErrorContainer should be specified")
    }

    @Test
    fun `LightColorScheme contains specified colors for standard M3 roles`() {
        val scheme = LightColorScheme

        assertTrue(scheme.primary.isSpecified, "primary should be specified")
        assertTrue(scheme.onPrimary.isSpecified, "onPrimary should be specified")
        assertTrue(scheme.background.isSpecified, "background should be specified")
        assertTrue(scheme.surface.isSpecified, "surface should be specified")
        assertTrue(scheme.onSurface.isSpecified, "onSurface should be specified")
        assertTrue(scheme.outline.isSpecified, "outline should be specified")
        assertTrue(scheme.error.isSpecified, "error should be specified")
    }

    @Test
    fun `DashboardShapes complies with official Material 3 corner scale`() {
        assertEquals(RoundedCornerShape(4.dp), DashboardShapes.extraSmall)
        assertEquals(RoundedCornerShape(8.dp), DashboardShapes.small)
        assertEquals(RoundedCornerShape(12.dp), DashboardShapes.medium)
        assertEquals(RoundedCornerShape(16.dp), DashboardShapes.large)
        assertEquals(RoundedCornerShape(28.dp), DashboardShapes.extraLarge)
    }

    @Test
    fun `DashboardTypography defines all standard M3 text styles with proper scaling`() {
        val typo = DashboardTypography

        // Headlines
        assertEquals(FontWeight.Bold, typo.headlineLarge.fontWeight)
        assertEquals(24.sp, typo.headlineLarge.fontSize)
        assertEquals(32.sp, typo.headlineLarge.lineHeight)

        assertEquals(FontWeight.SemiBold, typo.headlineMedium.fontWeight)
        assertEquals(20.sp, typo.headlineMedium.fontSize)
        assertEquals(28.sp, typo.headlineMedium.lineHeight)

        assertEquals(FontWeight.SemiBold, typo.headlineSmall.fontWeight)
        assertEquals(18.sp, typo.headlineSmall.fontSize)

        // Titles
        assertEquals(FontWeight.SemiBold, typo.titleLarge.fontWeight)
        assertEquals(16.sp, typo.titleLarge.fontSize)
        assertEquals(24.sp, typo.titleLarge.lineHeight)

        assertEquals(FontWeight.Medium, typo.titleMedium.fontWeight)
        assertEquals(14.sp, typo.titleMedium.fontSize)
        assertEquals(20.sp, typo.titleMedium.lineHeight)

        assertEquals(FontWeight.Medium, typo.titleSmall.fontWeight)
        assertEquals(12.sp, typo.titleSmall.fontSize)
        assertEquals(16.sp, typo.titleSmall.lineHeight)

        // Body
        assertEquals(14.sp, typo.bodyLarge.fontSize)
        assertEquals(20.sp, typo.bodyLarge.lineHeight)

        assertEquals(13.sp, typo.bodyMedium.fontSize)
        assertEquals(18.sp, typo.bodyMedium.lineHeight)

        assertEquals(11.sp, typo.bodySmall.fontSize)
        assertEquals(14.sp, typo.bodySmall.lineHeight)

        // Labels
        assertEquals(FontWeight.Medium, typo.labelLarge.fontWeight)
        assertEquals(13.sp, typo.labelLarge.fontSize)

        assertEquals(FontWeight.Medium, typo.labelMedium.fontWeight)
        assertEquals(11.sp, typo.labelMedium.fontSize)

        assertEquals(FontWeight.Normal, typo.labelSmall.fontWeight)
        assertEquals(10.sp, typo.labelSmall.fontSize)
    }

    @Test
    fun `Status and container state colors are non-transparent and distinguishable`() {
        assertTrue(StatusHealthy.isSpecified)
        assertTrue(StatusClosed.isSpecified)
        assertTrue(StatusUnreachable.isSpecified)
        assertTrue(StatusNeutral.isSpecified)

        assertNotEquals(StatusHealthy, StatusClosed)
        assertNotEquals(StatusHealthy, StatusUnreachable)
        assertNotEquals(StatusClosed, StatusUnreachable)

        assertTrue(ContainerRunning.isSpecified)
        assertTrue(ContainerPaused.isSpecified)
        assertTrue(ContainerExited.isSpecified)
        assertTrue(ContainerRestarting.isSpecified)
    }
}
