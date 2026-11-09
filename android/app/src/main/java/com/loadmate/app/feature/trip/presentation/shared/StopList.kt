package com.loadmate.app.feature.trip.presentation.shared

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.loadmate.app.R
import com.loadmate.app.feature.trip.domain.CargoLine
import com.loadmate.app.feature.trip.domain.Stop
import com.loadmate.app.feature.trip.domain.StopStatus
import com.loadmate.app.feature.trip.domain.Trip
import com.loadmate.app.shared.designsystem.components.Field
import com.loadmate.app.shared.designsystem.components.PleosButton
import com.loadmate.app.shared.designsystem.components.PleosButtonStyle
import com.loadmate.app.shared.designsystem.components.PleosIconButton
import com.loadmate.app.shared.designsystem.components.PleosListItem
import com.loadmate.app.shared.designsystem.components.PleosPopupAction
import com.loadmate.app.shared.designsystem.components.PleosStepper
import com.loadmate.app.shared.designsystem.components.PleosSystemPopup
import com.loadmate.app.shared.designsystem.components.PleosSystemPopupSize
import com.loadmate.app.shared.designsystem.dimens.PleosShape
import com.loadmate.app.shared.designsystem.dimens.PleosSpacing
import com.loadmate.app.shared.designsystem.project.PleosCard
import com.loadmate.app.shared.designsystem.theme.Pleos
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

private val HANDLE_SIZE = 72.dp
private val HANDLE_ICON_SIZE = 40.dp
/** 이 폭 안에서 잡으면 길게 누르지 않아도 바로 끌린다 */
private val HANDLE_ZONE_WIDTH = 120.dp
/** 삭제는 서버 기록에 사유가 필요하다. 기사에게 고르게 하지 않는다 */
private const val REMOVED_REASON = "경로에서 삭제"
private val QTY_MIN_WIDTH = 56.dp
private const val ADDRESS_MIN_LENGTH = 2
private const val STOP_QTY_MAX = 99
/** 놓은 뒤 서버 응답을 기다리는 시간. 저장에 실패하면 이 시간 뒤 원래 순서로 돌아간다 */
private const val DROP_SETTLE_MS = 3_000L

/**
 * 방문지 목록 (F04·F18·F29). 남은 방문지는 길게 누르거나 손잡이를 잡아 끌어 순서를 바꾸고, 눌러서 장소를 고치며, 오른쪽에서 수량 변경·삭제를 한다.
 * 계획 수량은 출발 확인 전에만 바꾼다.
 */
@Composable
internal fun StopListCard(trip: Trip, on: (TripUiAction) -> Unit, onDialog: (StopDialog) -> Unit) {
    val c = Pleos.colors
    val currentId = trip.currentStop?.id.takeIf { trip.baseline != null }
    val pending = trip.pendingStops
    val serverOrder = pending.map { it.id }
    val latestServerOrder by rememberUpdatedState(serverOrder)
    val latestOn by rememberUpdatedState(on)
    var order by remember(serverOrder) { mutableStateOf(serverOrder) }
    var dragging by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var dropped by remember { mutableStateOf(false) }
    val heights = remember { mutableStateMapOf<String, Int>() }
    LaunchedEffect(dropped, serverOrder) {
        if (dropped) {
            delay(DROP_SETTLE_MS)
            order = serverOrder
            dropped = false
        }
    }
    val byId = trip.stops.associateBy { it.id }
    val gap = routeLoadGap(trip) != null
    val canManage = !trip.isEnded
    val canDrag = canManage && pending.size > 1
    PleosCard {
        if (canDrag) BasicText("길게 눌러 끌면 순서 변경 · 누르면 장소 수정", style = Pleos.type.labelMedium.copy(color = c.textTertiary))
        trip.stops.filter { it.status != StopStatus.PENDING && it.status != StopStatus.CANCELLED }.forEach { st ->
            StopRow(st, isCurrent = false, onEdit = null, handle = null) {}
        }
        // 움직이는 줄이 아니라 움직이지 않는 목록에서 손가락을 읽어야 끄는 동안 위치 계산이 흔들리지 않는다
        Column(
            Modifier.pointerInput(canDrag, serverOrder) {
                if (!canDrag) return@pointerInput
                val handleZone = HANDLE_ZONE_WIDTH.toPx()
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val id = stopAtY(order, heights, down.position.y) ?: return@awaitEachGesture
                    val started = if (down.position.x < handleZone) awaitTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
                    else awaitLongPressOrCancellation(down.id)
                    if (started == null) return@awaitEachGesture
                    dragging = id
                    dragOffset = 0f
                    trackDrag(started.id) { dy ->
                        val (next, rest) = dragStep(order, id, dragOffset + dy, heights)
                        order = next
                        dragOffset = rest
                    }
                    dragging = null
                    dragOffset = 0f
                    if (order != latestServerOrder) {
                        dropped = true
                        latestOn(TripUiAction.ReorderStops(order))
                    }
                }
            },
        ) {
            order.mapNotNull { byId[it] }.forEach { st ->
                key(st.id) {
                    val isDragging = dragging == st.id
                    StopRow(
                        st,
                        isCurrent = st.id == currentId,
                        modifier = Modifier
                            .onSizeChanged { heights[st.id] = it.height }
                            .then(if (isDragging) Modifier.zIndex(1f).offset { IntOffset(0, dragOffset.roundToInt()) } else Modifier)
                            .then(if (isDragging) Modifier.background(c.surfaceHigh, RoundedCornerShape(PleosShape.medium)) else Modifier),
                        onEdit = if (canManage) ({ onDialog(StopDialog.Edit(st)) }) else null,
                        handle = if (canDrag) ({ DragHandle("${st.label} 끌어서 순서 변경") }) else null,
                    ) {
                        if (canManage && (trip.baseline == null || gap)) {
                            Column(verticalArrangement = Arrangement.spacedBy(PleosSpacing.xs)) {
                                st.cargo.forEach { line -> QtyControl(line) { on(TripUiAction.ChangeQty(st.id, line.id, it)) } }
                            }
                        }
                        if (canManage) PleosIconButton(R.drawable.pleos_ic_trash, "${st.label} 경로에서 삭제") { onDialog(StopDialog.Cancel(st)) }
                    }
                }
            }
        }
        if (canManage) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PleosSpacing.m)) {
                PleosButton("방문지 추가", style = PleosButtonStyle.Basic, leadingIcon = R.drawable.pleos_ic_plus) { onDialog(StopDialog.Add) }
                Box(Modifier.weight(1f))
                if (trip.baseline != null) PleosButton("운행 종료", style = PleosButtonStyle.Basic) { on(TripUiAction.AskEndTrip) }
            }
        }
    }
}

/** 목록에서 여는 팝업. 스크롤 영역 안에서 띄우면 화면 전체를 덮지 못하므로 화면 최상단에서 그린다 */
internal sealed interface StopDialog {
    data object Add : StopDialog
    data class Edit(val stop: Stop) : StopDialog
    data class Cancel(val stop: Stop) : StopDialog
}

@Composable
internal fun StopDialogHost(dialog: StopDialog, trip: Trip, check: AddressCheckState, on: (TripUiAction) -> Unit, onClose: () -> Unit) {
    when (dialog) {
        StopDialog.Add -> StopEntryDialog(
            title = "방문지 추가", initialName = "", initialAddress = "", showQty = true, confirmLabel = "추가",
            note = "실린 짐은 바뀌지 않습니다.",
            check = check, on = on, onClose = onClose,
        ) { name, address, qty -> on(TripUiAction.AddStop(name, address, qty)) }
        is StopDialog.Edit -> StopEntryDialog(
            title = "${dialog.stop.label} 방문지 수정", initialName = dialog.stop.name, initialAddress = dialog.stop.address, showQty = false, confirmLabel = "저장",
            note = "짐은 그대로이고 장소만 바뀝니다.",
            check = check, on = on, onClose = onClose,
        ) { name, address, _ -> on(TripUiAction.EditStopPlace(dialog.stop.id, name, address)) }
        is StopDialog.Cancel -> RemoveStopDialog(dialog.stop, on, onClose)
    }
}

/** 손을 뗄 때까지 세로 이동량을 넘긴다. 뗄 때의 이벤트까지 소비해 끌기 끝에 줄 누르기가 함께 실행되지 않게 한다 */
private suspend fun AwaitPointerEventScope.trackDrag(pointer: PointerId, onMove: (Float) -> Unit) {
    while (true) {
        val change = awaitPointerEvent().changes.firstOrNull { it.id == pointer } ?: return
        if (!change.pressed) {
            change.consume()
            return
        }
        onMove(change.positionChange().y)
        change.consume()
    }
}

@Composable
private fun DragHandle(description: String) {
    Box(Modifier.size(HANDLE_SIZE), contentAlignment = Alignment.Center) {
        Image(painterResource(R.drawable.pleos_ic_menu_3), description, Modifier.size(HANDLE_ICON_SIZE), colorFilter = ColorFilter.tint(Pleos.colors.textSecondary))
    }
}

@Composable
private fun QtyControl(line: CargoLine, onChange: (Int) -> Unit) {
    val c = Pleos.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PleosSpacing.s)) {
        PleosIconButton(R.drawable.pleos_ic_minus, "${line.itemName} 줄이기", enabled = line.plannedQty > 1) { onChange(line.plannedQty - 1) }
        BasicText("${line.plannedQty}", Modifier.widthIn(min = QTY_MIN_WIDTH), style = Pleos.type.titleSmall.copy(color = c.textPrimary, textAlign = TextAlign.Center))
        PleosIconButton(R.drawable.pleos_ic_plus, "${line.itemName} 늘리기", enabled = line.plannedQty < STOP_QTY_MAX) { onChange(line.plannedQty + 1) }
    }
}

@Composable
private fun StopRow(
    st: Stop,
    isCurrent: Boolean,
    modifier: Modifier = Modifier,
    onEdit: (() -> Unit)?,
    handle: (@Composable () -> Unit)?,
    actions: @Composable () -> Unit,
) {
    val c = Pleos.colors
    val t = Pleos.type
    val (label, tone) = when (st.status) {
        StopStatus.PENDING -> (if (isCurrent) "현재" else "예정") to c.textSecondary
        StopStatus.COMPLETED -> "완료" to c.positive
        StopStatus.PARTIAL -> "부분 납품" to c.regulationOrange
        StopStatus.CANCELLED -> "취소" to c.textTertiary
        StopStatus.UNKNOWN -> "확인 필요" to c.textSecondary
    }
    val cargo = st.cargo.joinToString(" · ") { "${it.itemName} ${it.deliveredQty}/${it.plannedQty}" }
    PleosListItem(
        title = st.name,
        modifier = modifier,
        subtitle = "하차 $cargo" + st.cancelReason?.let { "\n취소 사유: $it" }.orEmpty(),
        selected = isCurrent,
        onClick = onEdit,
        leading = {
            handle?.invoke()
            BasicText(st.label, style = t.titleSmall.copy(color = c.textPrimary))
        },
        trailing = {
            BasicText(label, style = t.bodyMedium.copy(color = tone))
            actions()
        },
    )
}

/** 기사가 쓴 이름·주소를 저장한다. 주소는 VWorld 로 위치가 잡히는지만 확인하고 그 결과는 저장하지 않는다 */
@Composable
private fun StopEntryDialog(
    title: String,
    initialName: String,
    initialAddress: String,
    showQty: Boolean,
    confirmLabel: String,
    note: String,
    check: AddressCheckState,
    on: (TripUiAction) -> Unit,
    onClose: () -> Unit,
    onSubmit: (name: String, address: String, qty: Int) -> Unit,
) {
    val c = Pleos.colors
    val t = Pleos.type
    var name by rememberSaveable { mutableStateOf(initialName) }
    var address by rememberSaveable { mutableStateOf(initialAddress) }
    var qty by rememberSaveable { mutableStateOf(1) }
    val typed = address.trim()
    val located = typed.isNotEmpty() && (typed == initialAddress.trim() || check.foundAddress == typed)
    val close = { on(TripUiAction.ClearAddressCheck); onClose() }
    PleosSystemPopup(
        title = title,
        onDismiss = close,
        size = PleosSystemPopupSize.Large,
        actions = listOf(
            PleosPopupAction(confirmLabel, enabled = name.isNotBlank() && located) { onSubmit(name.trim(), typed, qty); close() },
            PleosPopupAction("닫기", PleosButtonStyle.Basic, onClick = close),
        ),
    ) {
        Field(name, "받는 곳 이름", Modifier.fillMaxWidth(), singleLine = true) { name = it }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PleosSpacing.m)) {
            Field(address, "주소 (시·구와 건물번호까지)", Modifier.weight(1f), singleLine = true, isError = check.notFoundAddress == typed && typed.isNotEmpty()) { address = it }
            PleosButton(if (check.checking) "확인 중" else "주소 확인", enabled = !check.checking && !located && typed.length >= ADDRESS_MIN_LENGTH) {
                on(TripUiAction.CheckAddress(typed))
            }
        }
        when {
            check.error != null -> BasicText(check.error, style = t.bodyMedium.copy(color = c.negative))
            check.notFoundAddress == typed && typed.isNotEmpty() -> BasicText("주소를 찾지 못했습니다", style = t.bodyMedium.copy(color = c.negative))
            check.foundAddress == typed && typed.isNotEmpty() -> BasicText("주소 확인됨", style = t.bodyMedium.copy(color = c.positive))
        }
        if (showQty) PleosStepper("박스 수량", qty, { qty = it }, min = 1, max = STOP_QTY_MAX)
        BasicText(note, style = t.bodyMedium.copy(color = c.textSecondary))
    }
}

@Composable
private fun RemoveStopDialog(st: Stop, on: (TripUiAction) -> Unit, onClose: () -> Unit) {
    PleosSystemPopup(
        title = "경로에서 삭제할까요?",
        onDismiss = onClose,
        actions = listOf(
            PleosPopupAction("확인") { on(TripUiAction.CancelStop(st.id, REMOVED_REASON)); onClose() },
            PleosPopupAction("취소", PleosButtonStyle.Basic, onClick = onClose),
        ),
    )
}
