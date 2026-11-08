package com.loadmate.app.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.loadmate.app.R

/** 테마별로 해석된 색상. 화면 코드는 hex 대신 이 객체만 사용한다. */
@Immutable
class PleosColors(val isDark: Boolean) {
    private fun c(name: String) = PleosColorTokens[name].let { if (isDark) it.dark else it.light }

    val backgroundPrimary = c("background_primary")
    val backgroundSecondary = c("background_secondary")
    val backgroundPopup = c("background_popup")
    val surfaceBasic = c("surface_basic")
    val surfaceLow = c("surface_low")
    val surfaceHigh = c("surface_high")
    val surfaceAccent = c("surface_accent")
    val textPrimary = c("text_dark_primary")
    val textSecondary = c("text_dark_secondary")
    val textTertiary = c("text_dark_tertiary")
    val textQuaternary = c("text_dark_quaternary")
    val iconPrimary = c("icon_dark_primary")
    val iconSecondary = c("icon_dark_secondary")
    val iconTertiary = c("icon_dark_tertiary")
    val buttonBasicEnabled = c("button_basic_enabled")
    val buttonBasicPressed = c("button_basic_pressed")
    val buttonBasicDisabled = c("button_basic_disabled")
    val buttonSwitchEnabled = c("button_switch_enabled")
    val buttonSwitchPressed = c("button_switch_pressed")
    val buttonSwitchDisabled = c("button_switch_disabled")
    val buttonFilledEnabled = c("button_filled_enabled")
    val buttonFilledPressed = c("button_filled_pressed")
    val buttonFilledDisabled = c("button_filled_disabled")
    val sliderNormal = c("controller_slider_normal")
    val sliderPressed = c("controller_slider_pressed")
    val sliderKnob = c("controller_slider_knob")
    val tabBackground = c("controller_tab_background")
    val stepperBackground = c("controller_stepper_background")
    val fieldBackground = c("field_background")
    val fieldFocused = c("field_focused")
    val switchOn = c("switch_on")
    val switchOff = c("switch_off")
    val switchDisabled = c("switch_disabled")
    val switchKnobOn = c("switch_knob_on")
    val switchKnobOff = c("switch_knob_off")
    val switchKnobDisabled = c("switch_knob_disabled")
    val dropdownNormal = c("dropdown_normal")
    val dropdownPressed = c("dropdown_pressed")
    val dimLightPrimary = c("dim_light_primary")
    val overlayDefault = c("overlay_default")
    val active = c("informative_active")
    val positive = c("informative_positive")
    val negative = c("informative_negative")
    val autonomous = c("informative_autonomous_driving")
    val regulationYellow = c("regulation_yellow")
    val regulationGreen = c("regulation_green")
    val regulationRed = c("regulation_red")
    val regulationOrange = c("regulation_orange")

    /**
     * PROJECT: filled 버튼 위 텍스트. 공식 토큰 미확인.
     * button_filled_enabled 가 라이트 #44464E / 다크 #E0E1E6 으로 반전되므로 basic_00(라이트 #FFFFFF / 다크 #131417)을 사용.
     */
    val onFilled: Color = PleosPalette.basicPair("basic_00").let { if (isDark) it.second else it.first }
    /** PROJECT: 구분선 (공식 divider 토큰 미확인 → surface_accent 재사용) */
    val divider = surfaceAccent
}

/**
 * Typography (공식 Font Hierarchy 이미지 기준 크기·굵기).
 * 단위: 공식 문서는 단위를 표기하지 않음. Pleos_Connect_v2_0 기기 프로파일이 mdpi(160dpi)라
 * 이 기기에서 1px = 1dp = 1sp(fontScale 1.0)이므로 숫자를 sp로 사용한다. (임시 — docs/design-system.md)
 * Strong/Normal의 숫자 굵기는 공식 값 미확인 → Strong=Bold(700), Normal=Regular(400) PROJECT 매핑.
 * 줄 높이(lineHeight) 공식 값 미확인 → 지정하지 않음.
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
    fun s(size: Int, w: FontWeight) = TextStyle(fontFamily = family, fontSize = size.sp, fontWeight = w)
    return PleosTypography(
        headlineLarge = s(60, FontWeight.ExtraBold),
        headlineMedium = s(56, FontWeight.ExtraBold),
        headlineSmall = s(48, FontWeight.ExtraBold),
        titleLarge = s(40, FontWeight.Bold),
        titleMedium = s(36, FontWeight.Bold),
        titleSmall = s(32, FontWeight.Bold),
        bodyLarge = s(30, FontWeight.Normal),
        bodyMedium = s(28, FontWeight.Normal),
        bodySmall = s(26, FontWeight.Normal),
        labelMedium = s(24, FontWeight.Normal),
        labelSmall = s(20, FontWeight.Normal),
    )
}

/** 공식 Shadow (foundations/shadow) — X/Y/Blur/Color. blur→Android shadowLayer radius는 blur/2로 변환(PROJECT 해석). */
data class ShadowLayer(val x: Float, val y: Float, val blur: Float, val color: Color)

object PleosShadows {
    val level1 = listOf(ShadowLayer(0f, 6f, 24f, Color(0x14000000)))
    val level2 = listOf(ShadowLayer(0f, 8f, 32f, Color(0x29000000)), ShadowLayer(0f, 2f, 2f, Color(0x14000000)))
    val level3 = listOf(ShadowLayer(0f, 20f, 40f, Color(0x3D000000)), ShadowLayer(0f, 2f, 4f, Color(0x33000000)))
}

fun Modifier.pleosShadow(layers: List<ShadowLayer>, corner: Dp): Modifier = drawBehind {
    drawIntoCanvas { canvas ->
        layers.forEach { l ->
            val paint = Paint()
            val fp = paint.asFrameworkPaint()
            fp.color = android.graphics.Color.TRANSPARENT
            fp.setShadowLayer(l.blur.dp.toPx() / 2f, l.x.dp.toPx(), l.y.dp.toPx(), l.color.toArgb())
            canvas.drawRoundRect(0f, 0f, size.width, size.height, corner.toPx(), corner.toPx(), paint)
        }
    }
}

/**
 * Spacing / Shape — PROJECT 값. 공식 문서에서 확인한 수치는 Toast의 padding 24, max width 792/712뿐이며
 * 일반 간격·모서리 반경 스케일은 미확인이다.
 */
object PleosSpacing {
    val xs = 4.dp; val s = 8.dp; val m = 12.dp; val l = 16.dp; val xl = 24.dp; val xxl = 32.dp
    /** OFFICIAL: Toast side/bottom padding 24 */
    val toastPadding = 24.dp
    /** OFFICIAL: Toast max width 792 / message 712 */
    val toastMaxWidth = 792.dp
    val toastMessageMaxWidth = 712.dp
}

object PleosShape {
    /** PROJECT: 공식 radius 값 미확인 */
    val small = 8.dp; val medium = 16.dp; val large = 24.dp; val pill = 999.dp
    /** PROJECT: 차량 터치 타깃 최소 높이 (공식 값 미확인) */
    val minTouch = 64.dp
    /** PROJECT: 테두리 두께 */
    val hairline = 2.dp
}

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
