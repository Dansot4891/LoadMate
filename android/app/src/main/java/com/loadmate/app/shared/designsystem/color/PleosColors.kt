package com.loadmate.app.shared.designsystem.color

import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Immutable

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
    val dimDarkPrimary = c("dim_dark_primary")
    val dimDarkSecondary = c("dim_dark_secondary")
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

    val regulationBlue = c("regulation_blue")
    val basic700 = PleosPalette.basicPair("basic_700").let { if (isDark) it.second else it.first }
    val alphaDark200 = PleosPalette.alphaDarkPair(200).let { if (isDark) it.second else it.first }
    val alphaDark500 = PleosPalette.alphaDarkPair(500).let { if (isDark) it.second else it.first }
    /** 테마와 무관한 흰 글자 (Figma static light) */
    val onStatic = PleosPalette.staticLightC(100)
    val climateButtonBorder = c("climate_button_border_enabled")
    val keyboardNormalAccentsNormal = c("keyboard_normal_accents_normal")
    val keyboardNormalAccentsPressed = c("keyboard_normal_accents_pressed")
    val keyboardNormalActiveNormal = c("keyboard_normal_active_normal")
    val keyboardNormalActivePressed = c("keyboard_normal_active_pressed")
    val keyboardNormalBackground = c("keyboard_normal_background")
    val keyboardPrimaryNormal = c("keyboard_normal_primary_normal")
    val keyboardPrimaryPressed = c("keyboard_normal_primary_pressed")
    val keyboardNormalSecondaryNormal = c("keyboard_normal_secondary_normal")
    val keyboardNormalSecondaryPressed = c("keyboard_normal_secondary_pressed")
    val keyboardStaticAccentsNormal = c("keyboard_static_accents_normal")
    val keyboardStaticAccentsPressed = c("keyboard_static_accents_pressed")
    val keyboardStaticActiveNormal = c("keyboard_static_active_normal")
    val keyboardStaticActivePressed = c("keyboard_static_active_pressed")
    val keyboardStaticBackground = c("keyboard_static_background")
    val keyboardStaticIconDark = c("keyboard_static_icon_dark")
    val keyboardStaticPrimaryNormal = c("keyboard_static_primary_normal")
    val keyboardStaticPrimaryPressed = c("keyboard_static_primary_pressed")
    val keyboardStaticSecondaryNormal = c("keyboard_static_secondary_normal")
    val keyboardStaticSecondaryPressed = c("keyboard_static_secondary_pressed")
    val keyboardStaticText = c("keyboard_static_text")
    val climateSliderBackground = c("climate_slider_background")
    val callAcceptNormal = c("button_call_accept_normal")
    val callAcceptPressed = c("button_call_accept_pressed")
    val callEndNormal = c("button_call_end_normal")
    val callEndPressed = c("button_call_end_pressed")

    /** filled 버튼 위 텍스트 (공식 Figma: text/light/primary) */
    val onFilled = c("text_light_primary")
    val dividerPrimary = c("divider_primary")
    val divider = c("divider_secondary")
    val dividerTertiary = c("divider_tertiary")

    /** 공식 Figma 키보드 키 배경 */
    fun keyColor(kind: com.loadmate.app.shared.designsystem.components.PleosKeyKind, static: Boolean, active: Boolean, pressed: Boolean): Color {
        val k = kind.name
        val role = when {
            k == "Enter" -> "accents"
            active -> "active"
            k == "Primary" || k == "Space" -> "primary"
            else -> "secondary"
        }
        val token = "keyboard_${if (static) "static" else "normal"}_${role}_${if (pressed) "pressed" else "normal"}"
        return c(token)
    }
}
