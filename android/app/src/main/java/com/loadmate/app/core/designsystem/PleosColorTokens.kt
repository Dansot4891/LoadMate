package com.loadmate.app.core.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

/**
 * 토큰 출처 구분.
 * OFFICIAL  : 공식 문서(Colors 페이지 이미지)에 hex/참조가 명시된 값
 * MEASURED  : 공식 이미지의 스와치 픽셀을 측정해 확정한 값 (표 텍스트와 불일치 → docs/design-system.md)
 * RESOLVED  : 공식 문서의 참조(예: alpha_dark_50)를 프로젝트가 basic_* 토큰으로 해석한 값
 * PROJECT   : 공식 값이 없어 프로젝트가 정한 값 (공식 아님)
 */
enum class TokenSource { OFFICIAL, MEASURED, RESOLVED, PROJECT }

data class ColorToken(
    val name: String,
    val group: String,
    val light: Color,
    val dark: Color,
    val source: TokenSource,
    /** 공식 표기 (예: "basic_50 / basic_50" 또는 "#02C265 / #00E074") */
    val officialRef: String,
    val note: String? = null,
)

/**
 * Pleos Connect 공식 Colors (https://document.pleos.ai/docs/connect/guide/docs-design/foundations/colors)
 * 이미지에서 2026-11-02 전사. 원본: design-assets/official/foundations 폴더의 PNG
 */
object PleosPalette {
    private fun hex(v: Long) = Color(v)
    private fun alpha(base: Long, pct: Int) = Color(base).copy(alpha = pct / 100f)

    private const val WHITE = 0xFFFFFFFF
    private const val INK = 0xFF131417 // 공식 grayscale의 basic_900(light) / basic_00(dark)

    // ---- Grayscale (basic_*) : light / dark
    val basic = linkedMapOf(
        "basic_00" to (hex(0xFFFFFFFF) to hex(0xFF131417)),
        "basic_50" to (hex(0xFFF7F8FA) to hex(0xFF1D1E21)),
        "basic_100" to (hex(0xFFEDEEF2) to hex(0xFF313236)),
        "basic_200" to (hex(0xFFE0E1E6) to hex(0xFF44464E)),
        "basic_300" to (hex(0xFFC7C9CE) to hex(0xFF686A72)),
        "basic_400" to (hex(0xFFA4A8AE) to hex(0xFFA4A8AE)),
        "basic_500" to (hex(0xFF686A72) to hex(0xFFC7C9CE)),
        "basic_600" to (hex(0xFF44464E) to hex(0xFFE0E1E6)),
        "basic_700" to (hex(0xFF313236) to hex(0xFFEDEEF2)),
        "basic_800" to (hex(0xFF1D1E21) to hex(0xFFF7F8FA)),
        "basic_900" to (hex(0xFF131417) to hex(0xFFFFFFFF)),
    )

    private val alphaSteps = listOf(50 to 5, 100 to 10, 200 to 20, 300 to 30, 400 to 40, 500 to 50)

    // ---- White/Gray Alpha
    // 공식 표의 hex 텍스트는 Light/Dark 열이 뒤바뀐 것으로 보인다. 스와치 픽셀 측정값(scripts/measure_color_swatches.py):
    //   basic_alpha_dark  Light 열 5% → #F3F3F3 (= #131417 5% over white), 50% → #89898B
    //   basic_alpha_light Light 열 5% → #1F2023 (= #FFFFFF 5% over #131417)
    // 시스템 토큰 스와치(switch_off #B8B8B9, button_basic_enabled #F3F3F3)도 이 해석과 일치하므로 측정값을 채택.
    val alphaLight = alphaSteps.associate { (s, p) -> "basic_alpha_light_$s" to (alpha(WHITE, p) to alpha(INK, p)) }
    val alphaDark = alphaSteps.associate { (s, p) -> "basic_alpha_dark_$s" to (alpha(INK, p) to alpha(WHITE, p)) }

    // ---- Static (테마 무관)
    private val staticSteps = listOf(100 to 100, 200 to 84, 300 to 64, 400 to 32)
    val staticLight = staticSteps.associate { (s, p) -> "basic_static_light_$s" to alpha(WHITE, p) }
    val staticDark = staticSteps.associate { (s, p) -> "basic_static_dark_$s" to alpha(INK, p) }

    fun basicPair(n: String) = basic.getValue(n)
    fun alphaDarkPair(step: Int) = alphaDark.getValue("basic_alpha_dark_$step")
    fun alphaLightPair(step: Int) = alphaLight.getValue("basic_alpha_light_$step")
    fun staticLightC(step: Int) = staticLight.getValue("basic_static_light_$step")
    fun staticDarkC(step: Int) = staticDark.getValue("basic_static_dark_$step")
}

object PleosColorTokens {
    private val P = PleosPalette

    /** 참조 문자열("basic_50", "alpha_dark_100", "static_light_200")을 (light, dark) 쌍으로 해석 */
    private fun ref(light: String, dark: String): Pair<Color, Color> = resolve(light).first to resolve(dark).second

    private fun resolve(r: String): Pair<Color, Color> = when {
        r.startsWith("basic_") && !r.startsWith("basic_alpha") && !r.startsWith("basic_static") -> P.basicPair(r)
        r.startsWith("alpha_dark_") -> P.alphaDarkPair(r.removePrefix("alpha_dark_").toInt())
        r.startsWith("basic_alpha_dark") -> P.alphaDarkPair(r.filter(Char::isDigit).toInt())
        r.startsWith("alpha_light_") -> P.alphaLightPair(r.removePrefix("alpha_light_").toInt())
        r.startsWith("static_light_") -> P.staticLightC(r.removePrefix("static_light_").toInt()).let { it to it }
        r.startsWith("static_dark_") -> P.staticDarkC(r.removePrefix("static_dark_").toInt()).let { it to it }
        r == "informative_active" -> informativeActive
        else -> error("unknown ref $r")
    }

    private val informativeActive = Color(0xFF02C265) to Color(0xFF00E074)

    private fun t(group: String, name: String, light: String, dark: String, note: String? = null): ColorToken {
        val (l, d) = ref(light, dark)
        val direct = light.startsWith("basic_") && !light.startsWith("basic_alpha") && dark.startsWith("basic_") && !dark.startsWith("basic_alpha")
        return ColorToken(name, group, l, d, if (direct) TokenSource.OFFICIAL else TokenSource.RESOLVED, "$light / $dark", note)
    }

    private fun hexT(group: String, name: String, light: Long, dark: Long) =
        ColorToken(name, group, Color(light), Color(dark), TokenSource.OFFICIAL,
            "#%06X / #%06X".format(light and 0xFFFFFFL, dark and 0xFFFFFFL))

    val all: List<ColorToken> = buildList {
        P.basic.forEach { (n, v) -> add(ColorToken(n, "Grayscale", v.first, v.second, TokenSource.OFFICIAL,
            "#%06X / #%06X".format(v.first.toArgb() and 0xFFFFFF, v.second.toArgb() and 0xFFFFFF))) }
        P.alphaLight.forEach { (n, v) -> add(ColorToken(n, "Alpha", v.first, v.second, TokenSource.MEASURED,
            "표 텍스트: #131417 / #FFFFFF", "스와치 측정: Light=#FFFFFF·α, Dark=#131417·α (표 텍스트와 열 반대)")) }
        P.alphaDark.forEach { (n, v) -> add(ColorToken(n, "Alpha", v.first, v.second, TokenSource.MEASURED,
            "표 텍스트: #FFFFFF / #131417", "스와치 측정: Light=#131417·α, Dark=#FFFFFF·α (표 텍스트와 열 반대)")) }
        (P.staticLight + P.staticDark).forEach { (n, c) -> add(ColorToken(n, "Static", c, c, TokenSource.OFFICIAL, n)) }

        add(t("Background", "background_primary", "basic_50", "basic_50"))
        add(t("Background", "background_secondary", "basic_00", "basic_00"))
        add(t("Background", "background_popup", "basic_00", "basic_100"))

        add(t("Surface", "surface_basic", "basic_00", "basic_50"))
        add(t("Surface", "surface_low", "basic_50", "basic_00"))
        add(t("Surface", "surface_high", "basic_100", "basic_100"))
        add(t("Surface", "surface_accent", "basic_200", "basic_200"))

        listOf("primary" to 100, "secondary" to 200, "tertiary" to 300, "quaternary" to 400).forEach { (n, s) ->
            add(t("Text", "text_dark_$n", "static_dark_$s", "static_light_$s",
                note = "공식 표 5~8행에 같은 이름(text_dark_*)이 반대 매핑으로 중복 — 해당 행은 미반영"))
        }
        listOf("primary" to 100, "secondary" to 200, "tertiary" to 300, "quaternary" to 400).forEach { (n, s) ->
            add(t("Icon", "icon_dark_$n", "static_dark_$s", "static_light_$s",
                note = "공식 표 5~8행 중복 이름 미반영"))
        }

        add(t("Button", "button_basic_enabled", "alpha_dark_50", "alpha_dark_50"))
        add(t("Button", "button_basic_pressed", "alpha_dark_100", "alpha_dark_100"))
        add(t("Button", "button_basic_disabled", "alpha_light_200", "alpha_light_200"))
        add(t("Button", "button_switch_enabled", "basic_00", "alpha_dark_200"))
        add(t("Button", "button_switch_pressed", "alpha_light_500", "alpha_dark_200"))
        add(t("Button", "button_switch_disabled", "alpha_light_300", "alpha_dark_300"))
        add(t("Button", "button_filled_enabled", "basic_600", "basic_600"))
        add(t("Button", "button_filled_pressed", "basic_700", "basic_500"))
        add(t("Button", "button_filled_disabled", "alpha_dark_50", "alpha_dark_50"))

        add(t("Controller", "controller_slider_normal", "alpha_dark_200", "alpha_dark_100"))
        add(t("Controller", "controller_slider_pressed", "alpha_dark_300", "alpha_dark_200"))
        add(t("Controller", "controller_slider_knob", "basic_00", "alpha_dark_300"))
        add(t("Controller", "controller_tab_background", "alpha_dark_50", "alpha_dark_50"))
        add(t("Controller", "controller_stepper_background", "basic_00", "alpha_light_100"))

        add(t("Fields", "field_background", "basic_00", "basic_00"))
        add(t("Fields", "field_focused", "basic_700", "basic_700"))

        add(t("Switch", "switch_on", "informative_active", "informative_active"))
        add(t("Switch", "switch_off", "alpha_dark_300", "alpha_dark_100"))
        add(t("Switch", "switch_disabled", "alpha_dark_100", "alpha_dark_100"))
        add(t("Switch", "switch_knob_on", "basic_00", "basic_900"))
        add(t("Switch", "switch_knob_off", "basic_00", "basic_400"))
        add(t("Switch", "switch_knob_disabled", "basic_00", "alpha_dark_200"))

        add(t("Dropdown", "dropdown_normal", "basic_00", "basic_alpha_dark100"))
        add(t("Dropdown", "dropdown_pressed", "basic_100", "basic_alpha_dark300"))

        add(t("Dim", "dim_dark_primary", "alpha_dark_50", "alpha_dark_50"))
        add(t("Dim", "dim_dark_secondary", "alpha_dark_100", "alpha_dark_100"))
        add(t("Dim", "dim_dark_tertiary", "alpha_dark_200", "alpha_dark_200"))
        add(t("Dim", "dim_light_primary", "alpha_light_500", "alpha_light_500"))
        add(t("Dim", "dim_light_secondary", "alpha_light_400", "alpha_light_400"))
        add(t("Dim", "dim_light_tertiary", "alpha_light_300", "alpha_light_300"))

        add(t("Overlay", "overlay_default", "alpha_dark_200", "alpha_light_500"))

        add(hexT("Informative", "informative_active", 0xFF02C265, 0xFF00E074))
        add(hexT("Informative", "informative_positive", 0xFF0064FF, 0xFF0082FF))
        add(hexT("Informative", "informative_negative", 0xFFFE3D16, 0xFFFF4C28))
        add(hexT("Informative", "informative_autonomous_driving", 0xFF5A46FA, 0xFF6A57FF))

        add(hexT("Regulation", "regulation_blue", 0xFF0064FF, 0xFF0082FF))
        add(hexT("Regulation", "regulation_green", 0xFF00BA13, 0xFF55F165))
        add(hexT("Regulation", "regulation_yellow", 0xFFFFC224, 0xFFFFC224))
        add(hexT("Regulation", "regulation_orange", 0xFFFF8A00, 0xFFFF8A00))
        add(hexT("Regulation", "regulation_red", 0xFFF62E24, 0xFFFF4339))
        add(hexT("Regulation", "regulation_bluetooth", 0xFF0082FC, 0xFF0082FC))
    }

    private val byName = all.associateBy { it.name }
    operator fun get(name: String): ColorToken = byName.getValue(name)
}
