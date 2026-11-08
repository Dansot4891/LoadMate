package com.loadmate.app.shared.designsystem.dimens

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.dp

/** Spacing (공식 Figma: Spacing 8·12·16·32, 컴포넌트 gap 4·24, Connect-S 기준) */
object PleosSpacing {
    val xs = 4.dp; val s = 8.dp; val m = 12.dp; val l = 16.dp; val xl = 24.dp; val xxl = 32.dp
    /** 시스템 팝업 안쪽 여백 */
    val popupPadding = 48.dp
    /** Toast side/bottom padding 24 */
    val toastPadding = 24.dp
    /** Toast max width 792 / message 712 */
    val toastMaxWidth = 792.dp
    val toastMessageMaxWidth = 712.dp
}

/** Shape (공식 Figma 2.1.5, Connect-S 기준) */
object PleosShape {
    /** Radius level3·5·7·max */
    val small = 8.dp; val medium = 16.dp; val large = 24.dp; val pill = 999.dp
    /** 시스템 팝업 모서리 */
    val popup = 48.dp
    /** 터치 대상 최소 높이 = Small 버튼·아이콘 버튼 높이 */
    val minTouch = 64.dp
    /** 기본 테두리 */
    val hairline = 2.dp
    /** 입력창 포커스 테두리 */
    val focusBorder = 3.dp
    /** 선택된 탭의 밑줄 */
    val tabUnderline = 4.dp
}
