package com.dalur.film.pro

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicReference

/**
 * Lightweight luma histogram (64 bins) + zebra over-exposure fraction.
 * Runs on the CameraX analyzer thread; results are polled by Compose.
 */
class HistogramAnalyzer : ImageAnalysis.Analyzer {
    data class Result(val bins: IntArray, val overExposedFraction: Float, val width: Int, val height: Int)

    private val _last = AtomicReference<Result?>(null)
    val last: Result? get() = _last.get()

    override fun analyze(image: ImageProxy) {
        try {
            val yPlane = image.planes.firstOrNull() ?: return
            val buf: ByteBuffer = yPlane.buffer
            val rowStride = yPlane.rowStride
            val w = image.width; val h = image.height
            // Sample every 4th row/col for performance.
            val bins = IntArray(64)
            var total = 0; var over = 0
            val row = ByteArray(rowStride)
            var y = 0
            while (y < h) {
                buf.position(y * rowStride)
                val take = minOf(rowStride, buf.remaining())
                buf.get(row, 0, take)
                var x = 0
                while (x < w && x < take) {
                    val v = row[x].toInt() and 0xFF
                    bins[v / 4]++
                    total++
                    if (v >= 245) over++
                    x += 4
                }
                y += 4
            }
            _last.set(Result(bins, if (total > 0) over.toFloat() / total else 0f, w, h))
        } catch (_: Exception) {
        } finally {
            image.close()
        }
    }
}
