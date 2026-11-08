package com.loadmate.app.shared.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.font.FontFamily
import com.loadmate.app.shared.designsystem.color.PleosColors
import com.loadmate.app.shared.designsystem.typography.PleosTypography
import com.loadmate.app.shared.designsystem.typography.pleosTypography

val LocalPleosColors = staticCompositionLocalOf { PleosColors(false) }
val LocalPleosTypography = staticCompositionLocalOf { pleosTypography(FontFamily.Default) }

object Pleos {
    val colors: PleosColors @Composable get() = LocalPleosColors.current
    val type: PleosTypography @Composable get() = LocalPleosTypography.current
}

@Composable
fun PleosTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalPleosColors provides androidx.compose.runtime.remember(dark) { PleosColors(dark) },
        LocalPleosTypography provides androidx.compose.runtime.remember { pleosTypography() },
        content = content,
    )
}
