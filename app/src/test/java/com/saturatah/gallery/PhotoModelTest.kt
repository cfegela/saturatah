package com.saturatah.gallery

import android.net.Uri
import com.saturatah.gallery.model.Photo
import com.saturatah.gallery.model.PhotoFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.Mockito

class PhotoModelTest {

    @Test
    fun testPhotoModelDefaults() {
        val uri = Mockito.mock(Uri::class.java)
        val photo = Photo(
            id = 1L,
            uri = uri,
            displayName = "test.jpg",
            dateAdded = 1000L,
            size = 2048L
        )

        assertEquals(1L, photo.id)
        assertEquals(uri, photo.uri)
        assertEquals("test.jpg", photo.displayName)
        assertEquals(1000L, photo.dateAdded)
        assertEquals(0L, photo.dateTaken)
        assertEquals(2048L, photo.size)
        assertFalse(photo.isVideo)
        assertEquals(0L, photo.duration)
        assertNull(photo.filePath)
    }

    @Test
    fun testPhotoSortingDescending() {
        val uri = Mockito.mock(Uri::class.java)
        val p1 = Photo(id = 1L, uri = uri, displayName = "p1.jpg", dateAdded = 1L, dateTaken = 0L, size = 100L)
        val p2 = Photo(id = 2L, uri = uri, displayName = "p2.jpg", dateAdded = 2L, dateTaken = 5000L, size = 100L)
        val p3 = Photo(id = 3L, uri = uri, displayName = "p3.jpg", dateAdded = 3L, dateTaken = 10000L, size = 100L)

        val list = listOf(p1, p2, p3).sortedByDescending {
            if (it.dateTaken > 0) it.dateTaken else it.dateAdded * 1000L
        }

        assertEquals(3L, list[0].id) // dateTaken = 10000
        assertEquals(2L, list[1].id) // dateTaken = 5000
        assertEquals(1L, list[2].id) // dateAdded * 1000 = 1000
    }

    @Test
    fun testStackBlurAlpha() {
        val w = 64
        val h = 64
        val pixels = IntArray(w * h) { i ->
            val alpha = (i % 256)
            (alpha shl 24) or 0x00FFFFFF
        }
        val bitmap = Mockito.mock(android.graphics.Bitmap::class.java)
        Mockito.`when`(bitmap.width).thenReturn(w)
        Mockito.`when`(bitmap.height).thenReturn(h)
        Mockito.doAnswer { invocation ->
            val dest = invocation.getArgument<IntArray>(0)
            System.arraycopy(pixels, 0, dest, 0, w * h)
            null
        }.`when`(bitmap).getPixels(Mockito.any(), Mockito.anyInt(), Mockito.anyInt(), Mockito.anyInt(), Mockito.anyInt(), Mockito.anyInt(), Mockito.anyInt())

        com.saturatah.gallery.util.StackBlur.blurInPlace(bitmap, 2, blurAlpha = true)
    }

    @Test
    fun testPhotoFilterEnumValues() {
        assertEquals("none", PhotoFilter.NONE.id)
        assertEquals("None", PhotoFilter.NONE.displayName)
        assertNull(PhotoFilter.NONE.getColorMatrix())

        assertEquals("warm", PhotoFilter.WARM.id)
        assertNotNull(PhotoFilter.WARM.getColorMatrix())

        assertEquals("cool", PhotoFilter.COOL.id)
        assertNotNull(PhotoFilter.COOL.getColorMatrix())

        assertEquals("noir", PhotoFilter.NOIR.id)
        assertNotNull(PhotoFilter.NOIR.getColorMatrix())

        assertEquals("fade", PhotoFilter.FADE.id)
        assertNotNull(PhotoFilter.FADE.getColorMatrix())
    }

    @Test
    fun testColorAdjustmentHelperSaturation() {
        // Saturation level 0 should be null (no-op)
        assertNull(com.saturatah.gallery.util.ColorAdjustmentHelper.createSaturationMatrix(0))

        // B&W level -1 should produce a matrix
        assertNotNull(com.saturatah.gallery.util.ColorAdjustmentHelper.createSaturationMatrix(-1))

        // Level 5 should produce a matrix
        assertNotNull(com.saturatah.gallery.util.ColorAdjustmentHelper.createSaturationMatrix(5))
    }

    @Test
    fun testColorAdjustmentHelperSkinReduction() {
        // Skin level 0 or positive should be null (only reduces)
        assertNull(com.saturatah.gallery.util.ColorAdjustmentHelper.createSkinReductionMatrix(0))
        assertNull(com.saturatah.gallery.util.ColorAdjustmentHelper.createSkinReductionMatrix(1))

        // Negative skin levels should produce a reduction matrix
        val matrix = com.saturatah.gallery.util.ColorAdjustmentHelper.createSkinReductionMatrix(-5)
        assertNotNull(matrix)
    }

    @Test
    fun testColorAdjustmentHelperCombined() {
        // Both 0 -> null
        assertNull(com.saturatah.gallery.util.ColorAdjustmentHelper.createColorToneMatrix(0, 0))

        // Only sat -> not null
        assertNotNull(com.saturatah.gallery.util.ColorAdjustmentHelper.createColorToneMatrix(3, 0))

        // Only skin -> not null
        assertNotNull(com.saturatah.gallery.util.ColorAdjustmentHelper.createColorToneMatrix(0, -3))

        // Both active -> not null
        assertNotNull(com.saturatah.gallery.util.ColorAdjustmentHelper.createColorToneMatrix(3, -3))

        // B&W active -> pure monochrome matrix returned
        assertNotNull(com.saturatah.gallery.util.ColorAdjustmentHelper.createColorToneMatrix(-1, -3))
    }
}
