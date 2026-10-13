package com.saturatah.gallery.util

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * High-performance, O(n) Stack Blur (Mario Klingemann).
 * Runs safely on any Android version without RenderScript.
 * Supports optional 4-channel ARGB blurring (blurring alpha for masks/cutouts).
 */
object StackBlur {

    /**
     * Blurs [src] with the given [radius].
     * Returns a new blurred Bitmap.
     */
    fun blur(src: Bitmap, radius: Int, blurAlpha: Boolean = false): Bitmap {
        if (radius < 1) return src

        val out = src.copy(src.config ?: Bitmap.Config.ARGB_8888, true)
        blurInPlace(out, radius, blurAlpha)
        return out
    }

    /**
     * In-place Stack Blur on an ARGB_8888 or RGB_565 Bitmap.
     * When [blurAlpha] is true, alpha channel is blurred alongside RGB (essential for masks).
     */
    fun blurInPlace(bitmap: Bitmap, radius: Int, blurAlpha: Boolean = false) {
        if (radius < 1) return

        val w = bitmap.width
        val h = bitmap.height
        val pix = IntArray(w * h)
        bitmap.getPixels(pix, 0, w, 0, 0, w, h)

        val wm = w - 1
        val hm = h - 1
        val wh = w * h
        val div = radius + radius + 1

        val r = IntArray(wh)
        val g = IntArray(wh)
        val b = IntArray(wh)
        val a = if (blurAlpha) IntArray(wh) else null

        var rsum: Int
        var gsum: Int
        var bsum: Int
        var asum = 0
        var p: Int
        var yp: Int
        var yi: Int
        var yw: Int
        val vmin = IntArray(max(w, h))

        var divsum = (div + 1) shr 1
        divsum *= divsum
        val dv = IntArray(256 * divsum)
        for (idx in 0 until 256 * divsum) {
            dv[idx] = idx / divsum
        }

        yi = 0
        yw = 0

        val stackChannels = if (blurAlpha) 4 else 3
        val stack = Array(div) { IntArray(stackChannels) }
        var stackpointer: Int
        var stackstart: Int
        var sir: IntArray
        var rbs: Int
        val r1 = radius + 1
        var routsum: Int
        var goutsum: Int
        var boutsum: Int
        var aoutsum = 0
        var rinsum: Int
        var ginsum: Int
        var binsum: Int
        var ainsum = 0

        // Horizontal pass
        for (row in 0 until h) {
            bsum = 0; gsum = 0; rsum = 0; asum = 0
            boutsum = 0; goutsum = 0; routsum = 0; aoutsum = 0
            binsum = 0; ginsum = 0; rinsum = 0; ainsum = 0

            for (col in -radius..radius) {
                p = pix[yi + min(wm, max(col, 0))]
                sir = stack[col + radius]
                sir[0] = (p shr 16) and 0xff
                sir[1] = (p shr 8) and 0xff
                sir[2] = p and 0xff
                if (blurAlpha) {
                    sir[3] = (p ushr 24) and 0xff
                }
                rbs = r1 - abs(col)
                rsum += sir[0] * rbs
                gsum += sir[1] * rbs
                bsum += sir[2] * rbs
                if (blurAlpha) {
                    asum += sir[3] * rbs
                }
                if (col > 0) {
                    rinsum += sir[0]
                    ginsum += sir[1]
                    binsum += sir[2]
                    if (blurAlpha) ainsum += sir[3]
                } else {
                    routsum += sir[0]
                    goutsum += sir[1]
                    boutsum += sir[2]
                    if (blurAlpha) aoutsum += sir[3]
                }
            }
            stackpointer = radius

            val maxDvIndex = dv.size - 1
            for (col in 0 until w) {
                r[yi] = dv[rsum.coerceIn(0, maxDvIndex)]
                g[yi] = dv[gsum.coerceIn(0, maxDvIndex)]
                b[yi] = dv[bsum.coerceIn(0, maxDvIndex)]
                if (blurAlpha) {
                    a!![yi] = dv[asum.coerceIn(0, maxDvIndex)]
                }

                rsum -= routsum
                gsum -= goutsum
                bsum -= boutsum
                if (blurAlpha) asum -= aoutsum

                stackstart = stackpointer - radius + div
                sir = stack[stackstart % div]

                routsum -= sir[0]
                goutsum -= sir[1]
                boutsum -= sir[2]
                if (blurAlpha) aoutsum -= sir[3]

                if (row == 0) {
                    vmin[col] = min(col + radius + 1, wm)
                }
                p = pix[yw + vmin[col]]

                sir[0] = (p shr 16) and 0xff
                sir[1] = (p shr 8) and 0xff
                sir[2] = p and 0xff
                if (blurAlpha) sir[3] = (p ushr 24) and 0xff

                rinsum += sir[0]
                ginsum += sir[1]
                binsum += sir[2]
                if (blurAlpha) ainsum += sir[3]

                rsum += rinsum
                gsum += ginsum
                bsum += binsum
                if (blurAlpha) asum += ainsum

                stackpointer = (stackpointer + 1) % div
                sir = stack[stackpointer % div]

                routsum += sir[0]
                goutsum += sir[1]
                boutsum += sir[2]
                if (blurAlpha) aoutsum += sir[3]

                rinsum -= sir[0]
                ginsum -= sir[1]
                binsum -= sir[2]
                if (blurAlpha) ainsum -= sir[3]

                yi++
            }
            yw += w
        }

        // Vertical pass
        for (col in 0 until w) {
            bsum = 0; gsum = 0; rsum = 0; asum = 0
            boutsum = 0; goutsum = 0; routsum = 0; aoutsum = 0
            binsum = 0; ginsum = 0; rinsum = 0; ainsum = 0

            yp = -radius * w
            for (row in -radius..radius) {
                yi = max(0, yp) + col

                sir = stack[row + radius]
                sir[0] = r[yi]
                sir[1] = g[yi]
                sir[2] = b[yi]
                if (blurAlpha) sir[3] = a!![yi]

                rbs = r1 - abs(row)
                rsum += r[yi] * rbs
                gsum += g[yi] * rbs
                bsum += b[yi] * rbs
                if (blurAlpha) asum += a!![yi] * rbs

                if (row > 0) {
                    rinsum += sir[0]
                    ginsum += sir[1]
                    binsum += sir[2]
                    if (blurAlpha) ainsum += sir[3]
                } else {
                    routsum += sir[0]
                    goutsum += sir[1]
                    boutsum += sir[2]
                    if (blurAlpha) aoutsum += sir[3]
                }

                if (row < hm) {
                    yp += w
                }
            }
            yi = col
            stackpointer = radius
            val maxDvIndex = dv.size - 1
            for (row in 0 until h) {
                val alphaVal = if (blurAlpha) {
                    dv[asum.coerceIn(0, maxDvIndex)] shl 24
                } else {
                    pix[yi] and -0x1000000
                }
                pix[yi] = alphaVal or (dv[rsum.coerceIn(0, maxDvIndex)] shl 16) or (dv[gsum.coerceIn(0, maxDvIndex)] shl 8) or dv[bsum.coerceIn(0, maxDvIndex)]

                rsum -= routsum
                gsum -= goutsum
                bsum -= boutsum
                if (blurAlpha) asum -= aoutsum

                stackstart = stackpointer - radius + div
                sir = stack[stackstart % div]

                routsum -= sir[0]
                goutsum -= sir[1]
                boutsum -= sir[2]
                if (blurAlpha) aoutsum -= sir[3]

                if (col == 0) {
                    vmin[row] = min(row + r1, hm) * w
                }
                p = col + vmin[row]

                sir[0] = r[p]
                sir[1] = g[p]
                sir[2] = b[p]
                if (blurAlpha) sir[3] = a!![p]

                rinsum += sir[0]
                ginsum += sir[1]
                binsum += sir[2]
                if (blurAlpha) ainsum += sir[3]

                rsum += rinsum
                gsum += ginsum
                bsum += binsum
                if (blurAlpha) asum += ainsum

                stackpointer = (stackpointer + 1) % div
                sir = stack[stackpointer % div]

                routsum += sir[0]
                goutsum += sir[1]
                boutsum += sir[2]
                if (blurAlpha) aoutsum -= sir[3]

                rinsum -= sir[0]
                ginsum -= sir[1]
                binsum -= sir[2]
                if (blurAlpha) ainsum -= sir[3]

                yi += w
            }
        }

        bitmap.setPixels(pix, 0, w, 0, 0, w, h)
    }
}
