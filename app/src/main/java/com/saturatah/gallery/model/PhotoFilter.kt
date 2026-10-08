package com.saturatah.gallery.model

import android.graphics.ColorMatrix

enum class PhotoFilter(val id: String, val displayName: String) {
    NONE("none", "None"),
    VIVID("vivid", "Vivid"),
    WARM("warm", "Warm"),
    COOL("cool", "Cool"),
    NOIR("noir", "Noir"),
    FADE("fade", "Fade");

    fun getColorMatrix(): ColorMatrix? {
        return when (this) {
            NONE -> null
            VIVID -> {
                // Saturated and punchy contrast (Velvia style)
                val satMatrix = ColorMatrix().apply { setSaturation(1.35f) }
                val contrast = 1.10f
                val t = (1f - contrast) * 128f * 0.4f
                val contrastMatrix = ColorMatrix(floatArrayOf(
                    contrast, 0f, 0f, 0f, t,
                    0f, contrast, 0f, 0f, t,
                    0f, 0f, contrast, 0f, t,
                    0f, 0f, 0f, 1f, 0f
                ))
                ColorMatrix().apply { setConcat(contrastMatrix, satMatrix) }
            }
            WARM -> {
                // Golden hour / Portra warmth
                ColorMatrix(floatArrayOf(
                    1.10f, 0f,    0f,    0f, 8f,
                    0f,    1.03f, 0f,    0f, 3f,
                    0f,    0f,    0.88f, 0f, -6f,
                    0f,    0f,    0f,    1f, 0f
                ))
            }
            COOL -> {
                // Nordic / Provia clean editorial coolness
                ColorMatrix(floatArrayOf(
                    0.90f, 0f,    0f,    0f, -4f,
                    0f,    1.02f, 0f,    0f, 2f,
                    0f,    0f,    1.14f, 0f, 12f,
                    0f,    0f,    0f,    1f, 0f
                ))
            }
            NOIR -> {
                // High-contrast Leica monochrome
                val c = 1.35f
                val t = -42f
                val rw = 0.299f * c
                val gw = 0.587f * c
                val bw = 0.114f * c
                ColorMatrix(floatArrayOf(
                    rw, gw, bw, 0f, t,
                    rw, gw, bw, 0f, t,
                    rw, gw, bw, 0f, t,
                    0f, 0f, 0f, 1f, 0f
                ))
            }
            FADE -> {
                // Lifted matte shadows with vintage warm roll-off
                val satMatrix = ColorMatrix().apply { setSaturation(0.85f) }
                val fadeMatrix = ColorMatrix(floatArrayOf(
                    0.88f, 0f,    0f,    0f, 30f,
                    0f,    0.86f, 0f,    0f, 25f,
                    0f,    0f,    0.82f, 0f, 20f,
                    0f,    0f,    0f,    1f, 0f
                ))
                ColorMatrix().apply { setConcat(fadeMatrix, satMatrix) }
            }
        }
    }
}
