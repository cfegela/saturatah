package com.saturatah.gallery.util

import android.graphics.ColorMatrix

/**
 * Helper to build uniform saturation and skin-tone reduction ColorMatrix transformations.
 */
object ColorAdjustmentHelper {

    /**
     * Builds a uniform saturation ColorMatrix.
     *
     * @param saturationLevel -1 for B&W (0.0 saturation), 0 for unmodified (null),
     *                        or 1..10 for uniform saturation boost (+10% per step, up to 2.0x).
     */
    fun createSaturationMatrix(saturationLevel: Int): ColorMatrix? {
        if (saturationLevel == 0) return null
        val sat = if (saturationLevel == -1) {
            0.0f
        } else {
            1.0f + saturationLevel.coerceIn(1, 10) * 0.10f
        }
        return ColorMatrix().apply { setSaturation(sat) }
    }

    /**
     * Builds a skin-tone reduction ColorMatrix.
     *
     * Only reduces skin tone (warm/red complexion excess) when [skinLevel] < 0.
     * Neutral grays, whites, and blacks are 100% mathematically preserved.
     * Non-skin channels (green foliage, blue skies/water) are fully protected.
     *
     * @param skinLevel Range -10..0 (where 0 is no reduction, -10 is maximum reduction).
     */
    fun createSkinReductionMatrix(skinLevel: Int): ColorMatrix? {
        if (skinLevel >= 0) return null
        val reduction = (-skinLevel).coerceIn(1, 10) * 0.045f // 0.045 to 0.45 max reduction

        val sr = 1.0f - reduction
        val sg = 1.0f - reduction * 0.20f
        val sb = 1.0f
        val wr = 0.2126f
        val wg = 0.7152f
        val wb = 0.0722f

        return ColorMatrix(floatArrayOf(
            (1f - sr) * wr + sr, (1f - sr) * wg,      (1f - sr) * wb,      0f, 0f,
            (1f - sg) * wr,      (1f - sg) * wg + sg, (1f - sg) * wb,      0f, 0f,
            (1f - sb) * wr,      (1f - sb) * wg,      (1f - sb) * wb + sb, 0f, 0f,
            0f,                  0f,                  0f,                  1f, 0f
        ))
    }

    /**
     * Combines uniform saturation and skin tone reduction.
     * Saturation is applied first to boost all hues uniformly, followed by
     * skin reduction to selectively pull back skin tone flush/saturation.
     */
    fun createColorToneMatrix(saturationLevel: Int, skinLevel: Int): ColorMatrix? {
        val satMatrix = createSaturationMatrix(saturationLevel)
        if (saturationLevel == -1) return satMatrix // pure monochrome

        val skinMatrix = createSkinReductionMatrix(skinLevel)
        return when {
            satMatrix != null && skinMatrix != null -> {
                ColorMatrix().apply { setConcat(skinMatrix, satMatrix) }
            }
            satMatrix != null -> satMatrix
            skinMatrix != null -> skinMatrix
            else -> null
        }
    }
}
