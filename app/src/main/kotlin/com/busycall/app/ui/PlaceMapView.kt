package com.busycall.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.util.Log
import android.view.View
import com.tutpro.baresip.R
import java.io.DataInputStream
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min

/**
 * A small US + Canada map: every state and province as a thin line, the caller's region(s)
 * filled, a dot at the centre. Shapes come from assets/bc_map.bin (tools/area-codes, Natural
 * Earth, public domain). Not focusable: it only illustrates the "Ohio" line next to it.
 */
class PlaceMapView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private var regions: List<String> = emptyList()
    private val drawn = mutableListOf<Pair<String, Path>>()
    private val dots = mutableListOf<Pair<Float, Float>>()
    private val density = resources.displayMetrics.density

    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 0.6f * density
        color = context.getColor(R.color.bc_map_line)
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = context.getColor(R.color.bc_accent)
    }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = context.getColor(R.color.bc_background) }
    private val dotRing = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = context.getColor(R.color.bc_text) }

    /** The region codes to highlight ("OH"); empty hides nothing but highlights nothing. */
    fun show(codes: List<String>) {
        if (codes == regions && drawn.isNotEmpty()) return
        regions = codes
        layoutMap()
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        layoutMap()
    }

    private fun layoutMap() {
        drawn.clear()
        dots.clear()
        if (width == 0 || height == 0) return
        val map = MapData.load(context) ?: return
        // Lower 48 and southern Canada, grown to take in the caller's region (Alaska, the North).
        val box = RectF(MapData.project(-126f, 56f), MapData.projectY(56f), MapData.project(-64f, 24f), MapData.projectY(24f))
        regions.mapNotNull { map[it] }.forEach { box.union(it.bounds) }
        val scale = min(width / box.width(), height / box.height())
        val matrix = Matrix().apply {
            postTranslate(-box.centerX(), -box.centerY())
            postScale(scale, scale)
            postTranslate(width / 2f, height / 2f)
        }
        for ((code, region) in map) {
            val path = Path(region.path)
            path.transform(matrix)
            drawn.add(code to path)
            if (code in regions) {
                val point = floatArrayOf(region.centreX, region.centreY)
                matrix.mapPoints(point)
                dots.add(point[0] to point[1])
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for ((code, path) in drawn) if (code in regions) canvas.drawPath(path, fill)
        for ((_, path) in drawn) canvas.drawPath(path, line)
        for ((x, y) in dots) {
            canvas.drawCircle(x, y, 3.5f * density, dotRing)
            canvas.drawCircle(x, y, 2f * density, dot)
        }
    }

    /** The shapes, read once per process. */
    private object MapData {
        class Region(val path: Path, val bounds: RectF, val centreX: Float, val centreY: Float)

        private val SCALE_X = cos(Math.toRadians(45.0)).toFloat()

        @Volatile
        private var regions: Map<String, Region>? = null

        fun project(lon: Float, @Suppress("UNUSED_PARAMETER") lat: Float) = lon * SCALE_X
        fun projectY(lat: Float) = -lat

        fun load(context: Context): Map<String, Region>? {
            regions?.let { return it }
            return try {
                DataInputStream(context.applicationContext.assets.open("bc_map.bin").buffered()).use { input ->
                    val count = input.readShort().toInt()
                    val result = LinkedHashMap<String, Region>(count)
                    repeat(count) {
                        val code = String(byteArrayOf(input.readByte(), input.readByte()), Charsets.US_ASCII)
                        val cx = input.readShort() / 100f
                        val cy = input.readShort() / 100f
                        val path = Path()
                        val bounds = RectF(Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE)
                        repeat(input.readShort().toInt()) {
                            val points = input.readShort().toInt()
                            for (i in 0 until points) {
                                val x = project(input.readShort() / 100f, 0f)
                                val y = projectY(input.readShort() / 100f)
                                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                                bounds.set(min(bounds.left, x), min(bounds.top, y), max(bounds.right, x), max(bounds.bottom, y))
                            }
                            path.close()
                        }
                        result[code] = Region(path, bounds, project(cx, cy), projectY(cy))
                    }
                    result.also { regions = it }
                }
            } catch (e: Exception) {
                Log.e("BusyCall", "Map failed to load: ${e.javaClass.simpleName}")
                null
            }
        }
    }
}
