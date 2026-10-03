package com.kvieta.companion.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.Shapes
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Light = lightColorScheme(
    primary = Color(0xFF505D37), onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE2C7), onPrimaryContainer = Color(0xFF283316),
    background = Color(0xFFF1EFE6), onBackground = Color(0xFF24271E),
    surface = Color(0xFFFFFDF5), onSurface = Color(0xFF24271E),
    surfaceVariant = Color(0xFFE7E6DC), onSurfaceVariant = Color(0xFF53574A),
    secondary = Color(0xFF616B4C), onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0E5D0), onSecondaryContainer = Color(0xFF283316),
    outline = Color(0xFF848B76), outlineVariant = Color(0xFFD2D6C6),
    surfaceContainerHigh = Color(0xFFF1EFE6), surfaceContainerHighest = Color(0xFFE7E6DC),
)
private val Dark = darkColorScheme(
    primary = Color(0xFFBEC995), onPrimary = Color(0xFF253016),
    primaryContainer = Color(0xFF343E27), onPrimaryContainer = Color(0xFFDFE8C5),
    background = Color(0xFF171A13), onBackground = Color(0xFFE8EBDD),
    surface = Color(0xFF23271D), onSurface = Color(0xFFE8EBDD),
    surfaceVariant = Color(0xFF303529), onSurfaceVariant = Color(0xFFC0C6B3),
    secondary = Color(0xFFC0CAA7), onSecondary = Color(0xFF29321E),
    secondaryContainer = Color(0xFF3B442F), onSecondaryContainer = Color(0xFFE0E5D0),
    outline = Color(0xFF8B947D), outlineVariant = Color(0xFF454D3C),
    surfaceContainerHigh = Color(0xFF2B3023), surfaceContainerHighest = Color(0xFF353B2C),
)

@Composable
fun KvietaTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) Dark else Light,
        shapes = Shapes(small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(18.dp), large = RoundedCornerShape(26.dp)),
        typography = Typography(
            headlineLarge = TextStyle(fontSize = 32.sp, lineHeight = 38.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-.8).sp),
            titleLarge = TextStyle(fontSize = 21.sp, lineHeight = 27.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-.3).sp),
            bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
            bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
            labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
        ), content = content)
}
