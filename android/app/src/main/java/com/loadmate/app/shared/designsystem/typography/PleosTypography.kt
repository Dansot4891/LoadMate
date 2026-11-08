package com.loadmate.app.shared.designsystem.typography

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.loadmate.app.R

/**
 * Typography (공식 Figma 2.1.5 텍스트 스타일의 크기·굵기·줄 높이, Connect-S 기준).
 * 단위: Figma는 px. 에뮬레이터 Connect-S(2560×1440, 160dpi)에서 1px = 1dp = 1sp 이므로 숫자를 sp로 쓴다.
 */
@Immutable
data class PleosTypography(
    val headlineLarge: TextStyle,
    val headlineMedium: TextStyle,
    val headlineSmall: TextStyle,
    val titleLarge: TextStyle,
    val titleMedium: TextStyle,
    val titleSmall: TextStyle,
    val bodyLarge: TextStyle,
    val bodyMedium: TextStyle,
    val bodySmall: TextStyle,
    val labelMedium: TextStyle,
    val labelSmall: TextStyle,
    val headlineLargeStrong: TextStyle,
    val headlineSmallStrong: TextStyle,
    val titleLargeExtraStrong: TextStyle,
    val titleLargeNormal: TextStyle,
    val titleMediumExtraStrong: TextStyle,
    val titleMediumNormal: TextStyle,
    val bodyLargeStrong: TextStyle,
    val bodyMediumStrong: TextStyle,
    val bodySmallStrong: TextStyle,
    val drivingSpeedKmh: TextStyle,
    val drivingTrafficSign: TextStyle,
    val gleoListNumber: TextStyle,
) {
    fun all(): List<Pair<String, TextStyle>> = listOf(
        "Headline Large · 60 · ExtraBold" to headlineLarge,
        "Headline Medium · 56 · ExtraBold" to headlineMedium,
        "Headline Small · 48 · ExtraBold" to headlineSmall,
        "Title Large · 40 · Bold" to titleLarge,
        "Title Medium · 36 · Bold" to titleMedium,
        "Title Small · 32 · Bold" to titleSmall,
        "Body Large · 30 · Normal" to bodyLarge,
        "Body Medium · 28 · Normal" to bodyMedium,
        "Body Small · 26 · Normal" to bodySmall,
        "Label Medium · 24 · Normal" to labelMedium,
        "Label Small · 20 · Normal" to labelSmall,
    )
}

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun astaFont(weight: Int) = Font(
    R.font.asta_sans,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

/** Asta Sans (공식 Typography 페이지가 지정한 서체, OFL 1.1). 가변 폰트 wght 300–800 */
val AstaSans = FontFamily(astaFont(400), astaFont(700), astaFont(800))

fun pleosTypography(family: FontFamily = AstaSans): PleosTypography {
    fun s(size: Int, line: Int, w: FontWeight) = TextStyle(fontFamily = family, fontSize = size.sp, lineHeight = line.sp, fontWeight = w)
    return PleosTypography(
        headlineLarge = s(60, 80, FontWeight.ExtraBold),
        headlineMedium = s(56, 72, FontWeight.ExtraBold),
        headlineSmall = s(48, 64, FontWeight.ExtraBold),
        titleLarge = s(40, 52, FontWeight.Bold),
        titleMedium = s(36, 44, FontWeight.Bold),
        titleSmall = s(32, 40, FontWeight.Bold),
        bodyLarge = s(30, 38, FontWeight.Normal),
        bodyMedium = s(28, 36, FontWeight.Normal),
        bodySmall = s(26, 34, FontWeight.Normal),
        labelMedium = s(24, 28, FontWeight.Normal),
        labelSmall = s(20, 24, FontWeight.Normal),
        headlineLargeStrong = s(60, 80, FontWeight.Bold),
        headlineSmallStrong = s(48, 64, FontWeight.Bold),
        titleLargeExtraStrong = s(40, 52, FontWeight.ExtraBold),
        titleLargeNormal = s(40, 52, FontWeight.Normal),
        titleMediumExtraStrong = s(36, 44, FontWeight.ExtraBold),
        titleMediumNormal = s(36, 44, FontWeight.Normal),
        bodyLargeStrong = s(30, 38, FontWeight.Bold),
        bodyMediumStrong = s(28, 36, FontWeight.Bold),
        bodySmallStrong = s(26, 34, FontWeight.Bold),
        drivingSpeedKmh = s(112, 134, FontWeight.Bold),
        drivingTrafficSign = s(40, 48, FontWeight.ExtraBold),
        gleoListNumber = s(28, 36, FontWeight.ExtraBold),
    )
}
