package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class KineticLabColors(
  val arenaBackground: Color,
  val arenaGrid: Color,
  val cyanGlow: Color,
  val amberGold: Color,
  val emeraldSuccess: Color,
  val crimsonHazard: Color,
  val portalViolet: Color,
  val magentaBoost: Color,
  val elevatedCard: Color,
  val mutedText: Color,
)

val LocalKineticColors = staticCompositionLocalOf {
  AppThemeCatalog.getTheme(AppThemeCatalog.DEFAULT_THEME_ID).kineticColors
}

@Composable
fun KineticLabTheme(
  themeId: String = AppThemeCatalog.DEFAULT_THEME_ID,
  darkTheme: Boolean = true,
  content: @Composable () -> Unit,
) {
  val selected = AppThemeCatalog.getTheme(themeId)
  val colorScheme = selected.colorScheme
  val kineticColors = selected.kineticColors

  CompositionLocalProvider(LocalKineticColors provides kineticColors) {
    MaterialTheme(
      colorScheme = colorScheme,
      typography = KineticTypography,
      content = content,
    )
  }
}
