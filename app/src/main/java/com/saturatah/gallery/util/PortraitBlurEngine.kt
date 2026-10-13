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
 * Uses ML Kit Subject Segmentation to produce pristine subject matting,
 * combined with high-quality optical bokeh background blur.
 * Thread-safe, leak-free, and properly cleans up native ML Kit resources.
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

                    // 2. Obtain or reuse the pristine subject mask (at procW x procH)
                    val maskBitmap = getOrComputeMask(bitmap, procW, procH) ?: run {
                        Log.w(TAG, "No subject detected or segmentation unavailable; returning original")
                        return@withLock bitmap
                    }

                    if (maskBitmap.isRecycled || bitmap.isRecycled) return@withLock bitmap

                    // 3. Synthesize natural optical bokeh on downscaled background
                    val downscaledBg = Bitmap.createScaledBitmap(bitmap, procW, procH, true)
                    val depthBlurredBg = renderDepthBlurredBackground(downscaledBg, blurLevel)

                    if (downscaledBg !== bitmap && !downscaledBg.isRecycled) {
                        downscaledBg.recycle()
                    }

                    if (depthBlurredBg.isRecycled || bitmap.isRecycled) return@withLock bitmap

                    // 4. Composite: Sharp full-resolution subject over smoothly upscaled bokeh background
                    val output = compositeFullResolution(bitmap, depthBlurredBg, maskBitmap)

                    if (depthBlurredBg !== bitmap && !depthBlurredBg.isRecycled) {
                        depthBlurredBg.recycle()
                    }

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
        cachedMaskBitmap?.let {
            if (!it.isRecycled) {
                it.recycle()
            }
        }
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
        if (segInput !== sourceBitmap && !segInput.isRecycled) {
            segInput.recycle()
        }

        if (rawConfidenceBuffer == null) return null

        // Convert confidence buffer to an ARGB mask Bitmap with smooth S-curve
        // Preserves the full, crisp fidelity of ML Kit segmentation without post-blur erosion
        val mask = createMaskBitmap(rawConfidenceBuffer, procW, procH)

        // Clean up previous cached mask if different
        cachedMaskBitmap?.let {
            if (it !== mask && !it.isRecycled) {
                it.recycle()
            }
        }

        // Cache for subsequent slider changes
        cachedMaskBitmap = mask
        cachedSourceRef = WeakReference(sourceBitmap)

        return mask
    }

    /**
     * Runs ML Kit Subject Segmentation synchronously on the background thread and safely closes client.
     */
    private fun runMLKitSegmentation(inputBitmap: Bitmap): FloatBuffer? {
        return try {
            val options = SubjectSegmenterOptions.Builder()
                .enableForegroundConfidenceMask()
                .build()
            val segmenter = SubjectSegmentation.getClient(options)
            try {
                val inputImage = InputImage.fromBitmap(inputBitmap, 0)
                val result = Tasks.await(segmenter.process(inputImage))
                result.foregroundConfidenceMask
            } finally {
                segmenter.close()
            }
        } catch (e: Exception) {
            Log.e(TAG, "ML Kit Subject Segmentation processing failed", e)
            null
        }
    }

    /**
     * Builds an ARGB_8888 mask Bitmap where alpha encodes foreground confidence.
     * Uses a subtle S-curve to clean up noisy low-confidence background pixels
     * while retaining smooth photographic edge transitions.
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
     * Creates rich optical bokeh background blur.
     */
    private fun renderDepthBlurredBackground(
        bgBitmap: Bitmap,
        blurLevel: Int
    ): Bitmap {
        val blurRadius = when (blurLevel) {
            1 -> 4       // Subtle depth softening
            2 -> 7       // Gentle portrait bokeh
            3 -> 11      // Classic portrait bokeh
            4 -> 16      // Shallow depth-of-field
            else -> 22   // Rich, dreamy bokeh
        }

        val blurredBg = bgBitmap.copy(Bitmap.Config.ARGB_8888, true)
        StackBlur.blurInPlace(blurredBg, blurRadius, blurAlpha = false)
        return blurredBg
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
