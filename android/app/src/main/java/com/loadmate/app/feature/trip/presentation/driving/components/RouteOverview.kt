package com.loadmate.app.feature.trip.presentation.driving.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.loadmate.app.core.designsystem.Pleos
import com.loadmate.app.feature.trip.domain.Stop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.tan

/** 온라인 도로 지도 위에 방문지 좌표만 표시한다. Pleos 내비의 경로 geometry는 제공되지 않는다. */
@Composable
internal fun RouteOverview(stops: List<Stop>) {
    if (stops.isEmpty()) return
    val context = LocalContext.current
    val colors = Pleos.colors
    val type = Pleos.type
    Column {
        BasicText("앱 내부 2D 지도 · 온라인", style = type.titleSmall.copy(color = colors.textPrimary))
        BoxWithConstraints(Modifier.fillMaxWidth().height(260.dp).clipToBounds()) {
            val density = LocalDensity.current
            val width = with(density) { maxWidth.toPx() }
            val height = with(density) { maxHeight.toPx() }
            val viewport = remember(stops, width, height) { viewport(stops, width, height) }
            var tiles by remember(viewport) { mutableStateOf<Map<Tile, ImageBitmap>>(emptyMap()) }
            var failed by remember(viewport) { mutableStateOf(false) }
            LaunchedEffect(viewport) {
                withContext(Dispatchers.IO) {
                    val loaded = mutableMapOf<Tile, ImageBitmap>()
                    var missing = false
                    viewport.tiles.forEach { tile ->
                        val image = loadTile(context.cacheDir, tile)
                        if (image == null) missing = true else loaded[tile] = image
                    }
                    withContext(Dispatchers.Main) { tiles = loaded; failed = missing }
                }
            }
            Canvas(Modifier.fillMaxWidth().height(260.dp).clipToBounds()) {
                drawRect(colors.surfaceLow)
                tiles.forEach { (tile, image) ->
                    drawImage(image, topLeft = Offset((tile.x * TILE_SIZE - viewport.left).toFloat(), (tile.y * TILE_SIZE - viewport.top).toFloat()))
                }
                val points = stops.map { stop ->
                    val world = project(stop.latitude, stop.longitude, viewport.zoom)
                    Offset((world.x - viewport.left).toFloat(), (world.y - viewport.top).toFloat())
                }
                points.zipWithNext().forEach { (a, b) -> drawLine(colors.active, a, b, strokeWidth = 4.dp.toPx()) }
                points.forEach { point ->
                    drawCircle(colors.active, 10.dp.toPx(), point)
                    drawCircle(colors.textPrimary, 10.dp.toPx(), point, style = Stroke(2.dp.toPx()))
                }
            }
            if (failed) BasicText("지도 타일을 불러오지 못했습니다", style = type.labelMedium.copy(color = colors.negative))
        }
        BasicText("© OpenStreetMap contributors", style = type.labelMedium.copy(color = colors.textSecondary))
        BasicText(stops.joinToString(" → ") { "${it.label} ${it.name}" }, style = type.bodyMedium.copy(color = colors.textPrimary))
        BasicText("방문지 직선 연결 · 도로 경로·현재 위치·회전 안내 아님", style = type.labelMedium.copy(color = colors.textSecondary))
    }
}

private const val TILE_SIZE = 256
private const val CACHE_MILLIS = 7L * 24 * 60 * 60 * 1000
private data class Point(val x: Double, val y: Double)
private data class Tile(val z: Int, val x: Int, val y: Int)
private data class Viewport(val zoom: Int, val left: Double, val top: Double, val tiles: List<Tile>)

private fun project(latitude: Double, longitude: Double, zoom: Int): Point {
    val lat = Math.toRadians(latitude.coerceIn(-85.0511, 85.0511))
    val scale = TILE_SIZE * 2.0.pow(zoom)
    return Point((longitude + 180) / 360 * scale, (1 - ln(tan(lat) + 1 / cos(lat)) / PI) / 2 * scale)
}

private fun viewport(stops: List<Stop>, width: Float, height: Float): Viewport {
    var zoom = 15
    var points = stops.map { project(it.latitude, it.longitude, zoom) }
    while (zoom > 3 && (points.maxOf { it.x } - points.minOf { it.x } > width - 80 || points.maxOf { it.y } - points.minOf { it.y } > height - 70)) {
        zoom--
        points = stops.map { project(it.latitude, it.longitude, zoom) }
    }
    val left = (points.minOf { it.x } + points.maxOf { it.x }) / 2 - width / 2
    val top = (points.minOf { it.y } + points.maxOf { it.y }) / 2 - height / 2
    val limit = 1 shl zoom
    val tiles = (floor(left / TILE_SIZE).toInt()..floor((left + width) / TILE_SIZE).toInt()).flatMap { x ->
        (max(0, floor(top / TILE_SIZE).toInt())..min(limit - 1, floor((top + height) / TILE_SIZE).toInt())).map { y -> Tile(zoom, x, y) }
    }
    return Viewport(zoom, left, top, tiles)
}

private fun loadTile(cacheDir: File, tile: Tile): ImageBitmap? {
    val file = File(cacheDir, "loadmate-map/${tile.z}/${tile.x}/${tile.y}.png")
    if (!file.isFile || System.currentTimeMillis() - file.lastModified() > CACHE_MILLIS) {
        try {
            file.parentFile?.mkdirs()
            val wrapX = ((tile.x % (1 shl tile.z)) + (1 shl tile.z)) % (1 shl tile.z)
            val connection = URL("https://tile.openstreetmap.org/${tile.z}/$wrapX/${tile.y}.png").openConnection() as HttpURLConnection
            connection.setRequestProperty("User-Agent", "Loadmate/0.1 (Android Automotive; map preview)")
            if (file.isFile) connection.ifModifiedSince = file.lastModified()
            connection.connectTimeout = 5_000
            connection.readTimeout = 5_000
            try {
                if (connection.responseCode == HttpURLConnection.HTTP_NOT_MODIFIED) {
                    file.setLastModified(System.currentTimeMillis())
                    return BitmapFactory.decodeFile(file.path)?.asImageBitmap()
                }
                if (connection.responseCode != 200) return null
                connection.inputStream.use { input -> file.outputStream().use { output -> input.copyTo(output) } }
            } finally { connection.disconnect() }
        } catch (_: Exception) { return null }
    }
    return BitmapFactory.decodeFile(file.path)?.asImageBitmap()
}
