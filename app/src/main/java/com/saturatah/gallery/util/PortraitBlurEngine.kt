package com.saturatah.gallery.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference
import java.nio.FloatBuffer
import kotlin.math.max

/**
 * Natural, realistic portrait background blur engine.
 *
 * Designed to look like genuine optical lens depth-of-field:
 * - Does not over-blur or turn the background into abstract mush.
 * - Creates optical depth falloff (near background is gently softened, far background has richer bokeh).
 * - Soft feathered subject edges with zero jagged artifacts or cut-out halos.
 * - Thread-safe with Mutex; completely avoids manual .recycle() calls to eliminate race conditions.
 */
object PortraitBlurEngine {

    private const val TAG = "PortraitBlurEngine"

    // Processing dimension for ML Kit and blur synthesis.
    // At 512px, memory footprint is tiny (~1MB), inference is fast, and upsampling with bilinear
    // filtering produces creamy, natural bokeh.
    private const val PROCESS_MAX_DIM = 512

    private val engineMutex = Mutex()

    // Cache the segmentation mask for the active bitmap to allow instant slider responsiveness
    private var cachedSourceRef: WeakReference<Bitmap>? = null
    private var cachedMaskBitmap: Bitmap? = null

    /**
     * Apply portrait background blur.
     *
     * @param bitmap The source photo to blur (preview or full-resolution save bitmap).
     * @param blurLevel Intensity from 1 to 5.
     * @return A new Bitmap with natural portrait blur applied, or the original on error.
     */
    suspend fun applyPortraitBlur(bitmap: Bitmap, blurLevel: Int): Bitmap =
        withContext(Dispatchers.Default) {
            if (blurLevel <= 0) return@withContext bitmap
            if (bitmap.isRecycled) return@withContext bitmap

            engineMutex.withLock {
                try {
                    val targetW = bitmap.width
                    val targetH = bitmap.height
                    if (targetW <= 0 || targetH <= 0 || bitmap.isRecycled) return@withLock bitmap

                    // 1. Calculate downscaled dimensions for ML Kit and blur processing
                    val maxDim = max(targetW, targetH)
                    val scale = if (maxDim > PROCESS_MAX_DIM) {
                        PROCESS_MAX_DIM.toFloat() / maxDim
                    } else {
                        1f
                    }
                    val procW = (targetW * scale).toInt().coerceAtLeast(64)
                    val procH = (targetH * scale).toInt().coerceAtLeast(64)

                    // 2. Obtain or reuse the feathered subject mask (at procW x procH)
                    val maskBitmap = getOrComputeMask(bitmap, procW, procH) ?: run {
                        Log.w(TAG, "No subject detected or segmentation unavailable; returning original")
                        return@withLock bitmap
                    }

                    if (maskBitmap.isRecycled || bitmap.isRecycled) return@withLock bitmap

                    // 3. Synthesize natural optical bokeh on downscaled background
                    val downscaledBg = Bitmap.createScaledBitmap(bitmap, procW, procH, true)
                    val depthBlurredBg = renderDepthBlurredBackground(downscaledBg, maskBitmap, blurLevel)

                    if (depthBlurredBg.isRecycled || bitmap.isRecycled) return@withLock bitmap

                    // 4. Composite: Sharp full-resolution subject over smoothly upscaled bokeh background
                    val output = compositeFullResolution(bitmap, depthBlurredBg, maskBitmap)
                    output
                } catch (e: Throwable) {
                    Log.e(TAG, "Failed applying portrait blur", e)
                    bitmap
                }
            }
        }

    /**
     * Clear cached mask when photo changes or edit session completes.
     */
    fun clearCache() {
        cachedSourceRef = null
        cachedMaskBitmap = null
    }

    /**
     * Gets cached mask or runs ML Kit segmentation.
     */
    private fun getOrComputeMask(sourceBitmap: Bitmap, procW: Int, procH: Int): Bitmap? {
        val cachedSource = cachedSourceRef?.get()
        val cachedMask = cachedMaskBitmap
        if (cachedSource === sourceBitmap && cachedMask != null && !cachedMask.isRecycled) {
            return cachedMask
        }

        // Downsample bitmap for ML Kit
        val segInput = if (sourceBitmap.width == procW && sourceBitmap.height == procH) {
            sourceBitmap
        } else {
            Bitmap.createScaledBitmap(sourceBitmap, procW, procH, true)
        }

        val rawConfidenceBuffer = runMLKitSegmentation(segInput)
        if (rawConfidenceBuffer == null) return null

        // Convert confidence buffer to an ARGB mask Bitmap with smooth edge curve
        val mask = createMaskBitmap(rawConfidenceBuffer, procW, procH)

        // Feather the mask slightly (radius 2 at 512px) to eliminate hard edges/halos
        StackBlur.blurInPlace(mask, 2)

        // Cache for subsequent slider changes
        cachedMaskBitmap = mask
        cachedSourceRef = WeakReference(sourceBitmap)

        return mask
    }

    /**
     * Runs ML Kit Subject Segmentation synchronously on the background thread.
     */
    private fun runMLKitSegmentation(inputBitmap: Bitmap): FloatBuffer? {
        return try {
            val options = SubjectSegmenterOptions.Builder()
                .enableForegroundConfidenceMask()
                .build()
            val segmenter = SubjectSegmentation.getClient(options)
            val inputImage = InputImage.fromBitmap(inputBitmap, 0)
            val result = Tasks.await(segmenter.process(inputImage))
            result.foregroundConfidenceMask
        } catch (e: Exception) {
            Log.e(TAG, "ML Kit Subject Segmentation processing failed", e)
            null
        }
    }

    /**
     * Builds an ARGB_8888 mask Bitmap where alpha encodes foreground confidence.
     * Uses a subtle S-curve to clean up noisy low-confidence background pixels
     * while retaining smooth feathered edge transitions.
     */
    private fun createMaskBitmap(buffer: FloatBuffer, width: Int, height: Int): Bitmap {
        buffer.rewind()
        val totalPixels = width * height
        val pixels = IntArray(totalPixels)

        var idx = 0
        while (buffer.hasRemaining() && idx < totalPixels) {
            val raw = buffer.get()

            // S-curve smoothstep for natural photographic subject boundary
            val alphaNorm = smoothStep(0.08f, 0.92f, raw)
            val alphaByte = (alphaNorm * 255f).toInt().coerceIn(0, 255)

            // Store alpha in both alpha channel and RGB for PorterDuff compatibility
            pixels[idx] = (alphaByte shl 24) or 0x00FFFFFF
            idx++
        }

        val maskBmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        maskBmp.setPixels(pixels, 0, width, 0, 0, width, height)
        return maskBmp
    }

    private fun smoothStep(edge0: Float, edge1: Float, x: Float): Float {
        val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    /**
     * Creates an optical bokeh background with depth falloff.
     *
     * Near background (close to subject) receives lighter blur.
     * Far background receives full bokeh blur.
     */
    private fun renderDepthBlurredBackground(
        bgBitmap: Bitmap,
        maskBitmap: Bitmap,
        blurLevel: Int
    ): Bitmap {
        // Blur radii calibrated for natural photographic appearance at ~512px
        val (nearRadius, farRadius) = when (blurLevel) {
            1 -> Pair(2, 4)      // Equivalent to subtle lens falloff (~f/4)
            2 -> Pair(3, 7)      // Gentle portrait bokeh (~f/2.8)
            3 -> Pair(5, 11)     // Classic portrait bokeh (~f/2.0)
            4 -> Pair(7, 16)     // Shallow depth-of-field (~f/1.4)
            else -> Pair(9, 22)  // Rich, dreamy bokeh
        }

        val w = bgBitmap.width
        val h = bgBitmap.height

        // 1. Far blur (full bokeh)
        val farBg = bgBitmap.copy(Bitmap.Config.ARGB_8888, true)
        StackBlur.blurInPlace(farBg, farRadius)

        // 2. Near blur (subtle softening)
        val nearBg = bgBitmap.copy(Bitmap.Config.ARGB_8888, true)
        StackBlur.blurInPlace(nearBg, nearRadius)

        // 3. Proximity gradient: blur the mask heavily to identify pixels adjacent to subject
        val proximityMask = maskBitmap.copy(Bitmap.Config.ARGB_8888, true)
        StackBlur.blurInPlace(proximityMask, 16)

        // 4. Blend nearBg and farBg based on proximity to the subject
        val farPix = IntArray(w * h)
        val nearPix = IntArray(w * h)
        val proxPix = IntArray(w * h)

        farBg.getPixels(farPix, 0, w, 0, 0, w, h)
        nearBg.getPixels(nearPix, 0, w, 0, 0, w, h)
        proximityMask.getPixels(proxPix, 0, w, 0, 0, w, h)

        val blendedPix = IntArray(w * h)
        for (i in blendedPix.indices) {
            val proxAlpha = (proxPix[i] ushr 24) and 0xff
            val t = (proxAlpha / 255f).coerceIn(0f, 1f)

            if (t <= 0.01f) {
                blendedPix[i] = farPix[i]
            } else if (t >= 0.99f) {
                blendedPix[i] = nearPix[i]
            } else {
                val fP = farPix[i]
                val nP = nearPix[i]

                val fR = (fP shr 16) and 0xff
                val fG = (fP shr 8) and 0xff
                val fB = fP and 0xff

                val nR = (nP shr 16) and 0xff
                val nG = (nP shr 8) and 0xff
                val nB = nP and 0xff

                val r = (fR + (nR - fR) * t).toInt().coerceIn(0, 255)
                val g = (fG + (nG - fG) * t).toInt().coerceIn(0, 255)
                val b = (fB + (nB - fB) * t).toInt().coerceIn(0, 255)

                blendedPix[i] = -0x1000000 or (r shl 16) or (g shl 8) or b
            }
        }

        val resultBg = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        resultBg.setPixels(blendedPix, 0, w, 0, 0, w, h)
        return resultBg
    }

    /**
     * Composites the sharp, full-resolution subject over the blurred background using
     * Android's native GPU/Skia pipeline with PorterDuff.Mode.DST_IN.
     */
    private fun compositeFullResolution(
        source: Bitmap,
        blurredBackground: Bitmap,
        mask: Bitmap
    ): Bitmap {
        val targetW = source.width
        val targetH = source.height

        val result = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)

        val bilinearPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)

        // 1. Draw blurred background stretched to full size with bilinear interpolation
        canvas.drawBitmap(
            blurredBackground,
            null,
            Rect(0, 0, targetW, targetH),
            bilinearPaint
        )

        // 2. Draw sharp subject on top masked by the feathered mask
        val fullRect = RectF(0f, 0f, targetW.toFloat(), targetH.toFloat())
        val saveCount = canvas.saveLayer(fullRect, null)

        // Draw original sharp source photo into layer
        canvas.drawBitmap(source, 0f, 0f, bilinearPaint)

        // Mask out background using DST_IN (keeps only foreground pixels)
        val maskPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        }
        canvas.drawBitmap(mask, null, fullRect, maskPaint)

        canvas.restoreToCount(saveCount)

        return result
    }
}
