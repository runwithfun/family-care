package com.runwithfun.familycare.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.runwithfun.familycare.resources.Res
import com.runwithfun.familycare.resources.bricolage_grotesque
import com.runwithfun.familycare.resources.figtree
import org.jetbrains.compose.resources.Font

private val Ink = Color(0xFF161616)
private val SoftPink = Color(0xFFF3D6DE)
private val Rose = Color(0xFF9C566C)

private val PaperColors = lightColorScheme(
    primary = Ink,
    onPrimary = Color.White,
    primaryContainer = SoftPink,
    onPrimaryContainer = Ink,
    secondary = Rose,
    secondaryContainer = SoftPink,
    onSecondaryContainer = Ink,
    background = Color.White,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = Color(0xFFF3F2EE),
    onSurfaceVariant = Color(0xFF5F5B51),
    outline = Color(0xFF9C566C),
    outlineVariant = Color(0xFFE8E6E0),
)

private val DarkPaperColors = darkColorScheme(
    primary = Color(0xFFFFF8FA),
    onPrimary = Ink,
    primaryContainer = SoftPink,
    onPrimaryContainer = Ink,
    secondary = SoftPink,
    secondaryContainer = SoftPink,
    onSecondaryContainer = Ink,
    background = Color(0xFF221E20),
    onBackground = Color(0xFFFFF8FA),
    surface = Color(0xFF221E20),
    onSurface = Color(0xFFFFF8FA),
    surfaceVariant = Color(0xFF382F33),
    onSurfaceVariant = Color(0xFFD4C8CC),
    outline = Rose,
    outlineVariant = Color(0xFF53494D),
)

@Composable
internal fun FamilyCareTheme(content: @Composable () -> Unit) {
    val display = FontFamily(Font(Res.font.bricolage_grotesque, weight = FontWeight.Bold))
    val body = FontFamily(
        Font(Res.font.figtree, weight = FontWeight.Normal),
        Font(Res.font.figtree, weight = FontWeight.SemiBold),
    )
    val defaults = Typography()
    val type = defaults.copy(
        displaySmall = defaults.displaySmall.copy(fontFamily = display, fontWeight = FontWeight.Bold, fontSize = 40.sp),
        headlineLarge = defaults.headlineLarge.copy(fontFamily = display, fontWeight = FontWeight.Bold),
        headlineMedium = defaults.headlineMedium.copy(fontFamily = display, fontWeight = FontWeight.Bold),
        headlineSmall = defaults.headlineSmall.copy(fontFamily = display, fontWeight = FontWeight.Bold),
        titleLarge = defaults.titleLarge.copy(fontFamily = display, fontWeight = FontWeight.Bold),
        titleMedium = defaults.titleMedium.copy(fontFamily = body, fontWeight = FontWeight.SemiBold),
        bodyLarge = defaults.bodyLarge.copy(fontFamily = body),
        bodyMedium = defaults.bodyMedium.copy(fontFamily = body),
        bodySmall = defaults.bodySmall.copy(fontFamily = body),
        labelLarge = defaults.labelLarge.copy(fontFamily = body, fontWeight = FontWeight.SemiBold),
        labelMedium = defaults.labelMedium.copy(fontFamily = body),
        labelSmall = defaults.labelSmall.copy(fontFamily = body),
    )
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkPaperColors else PaperColors,
        typography = type,
        content = content,
    )
}
