package com.loadmate.app.feature.trip.presentation.cargo.components

import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** 적재함 3D 투영. Compose·Android 에 의존하지 않는다. x: 운전석(0)→뒷문, y: 왼쪽→오른쪽, z: 위 */
internal const val CELL_X = 2f
internal const val CELL_Y = 1f
internal const val CELL_Z = 0.8f
internal const val BAY_X = CELL_X * 3
internal const val BAY_Y = CELL_Y * 3
internal const val BAY_Z = CELL_Z * 3
private const val BOX_INSET = 0.05f
private const val CAMERA_DISTANCE = 12f

internal const val DEFAULT_YAW = -14f
internal const val DEFAULT_PITCH = 24f
internal const val MIN_PITCH = 12f
internal const val MAX_PITCH = 52f
internal const val ZOOM_STEP = 1.25f
private const val MIN_ZOOM = 0.6f
private const val MAX_ZOOM = 2.5f

internal fun clampZoom(zoom: Float) = zoom.coerceIn(MIN_ZOOM, MAX_ZOOM)

internal data class P2(val x: Float, val y: Float)

internal class BayProjector(yawDeg: Float, pitchDeg: Float, width: Float, height: Float, zoom: Float = 1f) {
    private val cy = cos(Math.toRadians(yawDeg.toDouble())).toFloat()
    private val sy = sin(Math.toRadians(yawDeg.toDouble())).toFloat()
    private val cp = cos(Math.toRadians(pitchDeg.toDouble())).toFloat()
    private val sp = sin(Math.toRadians(pitchDeg.toDouble())).toFloat()
    private val cx = width / 2
    private val cz = height / 2
    private val scale = min(width / 7.4f, height / 4.5f) * zoom

    private fun rotY(x: Float, y: Float) = (x - BAY_X / 2) * sy + (y - BAY_Y / 2) * cy

    /** 카메라에서 먼 값일수록 크다 */
    fun depth(x: Float, y: Float, z: Float): Float = rotY(x, y) * cp - (z - BAY_Z / 3) * sp

    fun project(x: Float, y: Float, z: Float): P2 {
        val dx = x - BAY_X / 2
        val dy = y - BAY_Y / 2
        val dz = z - BAY_Z / 3
        val rx = dx * cy - dy * sy
        val ry = dx * sy + dy * cy
        val f = CAMERA_DISTANCE / (CAMERA_DISTANCE + ry * cp - dz * sp)
        return P2(cx + scale * f * rx, cz - scale * f * (dz * cp + ry * sp))
    }

    fun face(vararg corners: Triple<Float, Float, Float>): List<P2> = corners.map { (x, y, z) -> project(x, y, z) }
}

internal enum class FaceKind { TOP, SIDE_X, SIDE_Y }

internal data class BoxSpec(val id: Int, val zoneIndex: Int, val lane: Int, val layer: Int, val lift: Float = 0f, val slide: Float = 0f)

internal data class BoxFace(val boxId: Int, val kind: FaceKind, val points: List<P2>)

/** 바깥에서 볼 때 반시계로 둔 면이 화면(y 아래)에서 음의 면적이면 카메라를 향한다 */
internal fun facesCamera(points: List<P2>): Boolean {
    var area = 0f
    for (i in points.indices) {
        val a = points[i]
        val b = points[(i + 1) % points.size]
        area += a.x * b.y - b.x * a.y
    }
    return area < 0f
}

private fun corner(x: Float, y: Float, z: Float) = Triple(x, y, z)

internal fun boxOutlineFaces(p: BayProjector, x0: Float, x1: Float, y0: Float, y1: Float, z0: Float, z1: Float): List<Pair<FaceKind, List<P2>>> = listOf(
    FaceKind.SIDE_X to p.face(corner(x1, y0, z0), corner(x1, y1, z0), corner(x1, y1, z1), corner(x1, y0, z1)),
    FaceKind.SIDE_X to p.face(corner(x0, y1, z0), corner(x0, y0, z0), corner(x0, y0, z1), corner(x0, y1, z1)),
    FaceKind.SIDE_Y to p.face(corner(x1, y1, z0), corner(x0, y1, z0), corner(x0, y1, z1), corner(x1, y1, z1)),
    FaceKind.SIDE_Y to p.face(corner(x0, y0, z0), corner(x1, y0, z0), corner(x1, y0, z1), corner(x0, y0, z1)),
    FaceKind.TOP to p.face(corner(x0, y0, z1), corner(x1, y0, z1), corner(x1, y1, z1), corner(x0, y1, z1)),
)

/** 먼 상자부터 그린 순서. 상자마다 카메라를 향한 면만 담는다 */
internal fun visibleBoxFaces(boxes: List<BoxSpec>, p: BayProjector): List<BoxFace> =
    boxes.sortedByDescending { b ->
        p.depth((b.zoneIndex + 0.5f) * CELL_X + b.slide, (b.lane + 0.5f) * CELL_Y, (b.layer - 0.5f) * CELL_Z + b.lift)
    }.flatMap { b ->
        val x0 = b.zoneIndex * CELL_X + BOX_INSET + b.slide
        val y0 = b.lane * CELL_Y + BOX_INSET
        val z0 = (b.layer - 1) * CELL_Z + b.lift
        val faces = boxOutlineFaces(p, x0, x0 + CELL_X - 2 * BOX_INSET, y0, y0 + CELL_Y - 2 * BOX_INSET, z0, z0 + CELL_Z - BOX_INSET)
        faces.filter { facesCamera(it.second) }.sortedBy { if (it.first == FaceKind.TOP) 1 else 0 }.map { BoxFace(b.id, it.first, it.second) }
    }

internal fun inside(points: List<P2>, q: P2): Boolean {
    var hit = false
    var j = points.lastIndex
    for (i in points.indices) {
        val a = points[i]
        val b = points[j]
        if ((a.y > q.y) != (b.y > q.y) && q.x < (b.x - a.x) * (q.y - a.y) / (b.y - a.y) + a.x) hit = !hit
        j = i
    }
    return hit
}

/** 그려진 순서의 반대로 찾아 맨 앞 상자를 고른다 */
internal fun hitBox(faces: List<BoxFace>, q: P2): Int? = faces.lastOrNull { inside(it.points, q) }?.boxId

internal fun centerOf(points: List<P2>) = P2(points.map { it.x }.average().toFloat(), points.map { it.y }.average().toFloat())
