package com.saturatah.gallery.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.saturatah.gallery.model.Photo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PhotoRepository(private val context: Context) {

    @Suppress("DEPRECATION")
    suspend fun loadPhotos(): List<Photo> = withContext(Dispatchers.IO) {
        val photos = mutableListOf<Photo>()
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.DATE_MODIFIED,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.DATA
        )

        val sortOrder = "${MediaStore.Images.Media.DATE_TAKEN} DESC, ${MediaStore.Images.Media.DATE_ADDED} DESC"

        try {
            context.contentResolver.query(
                collection,
                projection,
                null,
                null,
                sortOrder
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndex(MediaStore.Images.Media._ID)
                val nameColumn = cursor.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)
                val dateAddedColumn = cursor.getColumnIndex(MediaStore.Images.Media.DATE_ADDED)
                val dateModifiedColumn = cursor.getColumnIndex(MediaStore.Images.Media.DATE_MODIFIED)
                val dateTakenColumn = cursor.getColumnIndex(MediaStore.Images.Media.DATE_TAKEN)
                val sizeColumn = cursor.getColumnIndex(MediaStore.Images.Media.SIZE)
                val widthColumn = cursor.getColumnIndex(MediaStore.Images.Media.WIDTH)
                val heightColumn = cursor.getColumnIndex(MediaStore.Images.Media.HEIGHT)
                val dataColumn = cursor.getColumnIndex(MediaStore.Images.Media.DATA)

                while (cursor.moveToNext()) {
                    val id = if (idColumn >= 0) cursor.getLong(idColumn) else continue
                    val name = if (nameColumn >= 0) cursor.getString(nameColumn) ?: "Photo" else "Photo"
                    val dateAdded = if (dateAddedColumn >= 0) cursor.getLong(dateAddedColumn) else 0L
                    val dateModified = if (dateModifiedColumn >= 0) cursor.getLong(dateModifiedColumn) else 0L
                    val dateTaken = if (dateTakenColumn >= 0) cursor.getLong(dateTakenColumn) else 0L
                    val size = if (sizeColumn >= 0) cursor.getLong(sizeColumn) else 0L
                    val width = if (widthColumn >= 0) cursor.getInt(widthColumn) else 0
                    val height = if (heightColumn >= 0) cursor.getInt(heightColumn) else 0
                    val filePath = if (dataColumn >= 0) cursor.getString(dataColumn) else null

                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        id
                    )

                    photos.add(
                        Photo(
                            id = id,
                            uri = contentUri,
                            displayName = name,
                            dateAdded = dateAdded,
                            dateTaken = dateTaken,
                            dateModified = dateModified,
                            size = size,
                            width = width,
                            height = height,
                            filePath = filePath
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        photos.sortByDescending {
            if (it.dateTaken > 0) it.dateTaken else it.dateAdded * 1000L
        }

        photos
    }

    suspend fun loadPreviewBitmap(uri: Uri, maxDimension: Int = 2048): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }

            var inSampleSize = 1
            val maxSide = maxOf(options.outWidth, options.outHeight)
            if (maxSide > maxDimension) {
                while (maxSide / (inSampleSize * 2) >= maxDimension) {
                    inSampleSize *= 2
                }
            }

            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val bitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            } ?: return@withContext null

            val exifOrientation = try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val exif = ExifInterface(stream)
                    exif.getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL
                    )
                } ?: ExifInterface.ORIENTATION_NORMAL
            } catch (e: Exception) {
                ExifInterface.ORIENTATION_NORMAL
            }

            val degrees = when (exifOrientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }

            if (degrees != 0f) {
                val matrix = Matrix().apply { postRotate(degrees) }
                val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                if (rotated != bitmap) {
                    bitmap.recycle()
                }
                rotated
            } else {
                bitmap
            }
        } catch (e: Exception) {
            Log.e("PhotoRepository", "Failed to load preview bitmap", e)
            null
        }
    }

    suspend fun saveCroppedAndRotatedPhoto(
        sourceUri: Uri,
        rotationDegrees: Int,
        cropLeft: Float,
        cropTop: Float,
        cropRight: Float,
        cropBottom: Float,
        saturationLevel: Int = 0,
        lightLevel: Int = 0,
        darkLevel: Int = 0,
        originalPhoto: Photo? = null,
        saveAsCopy: Boolean = false
    ): Uri? = withContext(Dispatchers.IO) {
        try {
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(sourceUri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOptions)
            }

            var inSampleSize = 1
            val maxSide = maxOf(boundsOptions.outWidth, boundsOptions.outHeight)
            while (maxSide / inSampleSize > 8192) {
                inSampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }

            val baseBitmap = context.contentResolver.openInputStream(sourceUri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            } ?: return@withContext null

            val exifOrientation = try {
                context.contentResolver.openInputStream(sourceUri)?.use { stream ->
                    val exif = ExifInterface(stream)
                    exif.getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL
                    )
                } ?: ExifInterface.ORIENTATION_NORMAL
            } catch (e: Exception) {
                ExifInterface.ORIENTATION_NORMAL
            }

            val exifDegrees = when (exifOrientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }

            val totalDegrees = (exifDegrees + rotationDegrees) % 360

            val orientedBitmap = if (totalDegrees != 0) {
                val matrix = Matrix().apply { postRotate(totalDegrees.toFloat()) }
                val rotated = Bitmap.createBitmap(baseBitmap, 0, 0, baseBitmap.width, baseBitmap.height, matrix, true)
                if (rotated != baseBitmap) {
                    baseBitmap.recycle()
                }
                rotated
            } else {
                baseBitmap
            }

            val clampedL = cropLeft.coerceIn(0f, 1f)
            val clampedT = cropTop.coerceIn(0f, 1f)
            val clampedR = cropRight.coerceIn(clampedL + 0.001f, 1f)
            val clampedB = cropBottom.coerceIn(clampedT + 0.001f, 1f)

            val pxLeft = (clampedL * orientedBitmap.width).toInt().coerceIn(0, orientedBitmap.width - 1)
            val pxTop = (clampedT * orientedBitmap.height).toInt().coerceIn(0, orientedBitmap.height - 1)
            val pxWidth = ((clampedR - clampedL) * orientedBitmap.width).toInt().coerceIn(1, orientedBitmap.width - pxLeft)
            val pxHeight = ((clampedB - clampedT) * orientedBitmap.height).toInt().coerceIn(1, orientedBitmap.height - pxTop)

            val croppedBitmap = Bitmap.createBitmap(orientedBitmap, pxLeft, pxTop, pxWidth, pxHeight)
            if (croppedBitmap != orientedBitmap) {
                orientedBitmap.recycle()
            }

            val finalBitmap = if (saturationLevel == -1 || saturationLevel > 0 || lightLevel > 0 || darkLevel > 0) {
                val satMatrix = if (saturationLevel == -1 || saturationLevel > 0) {
                    if (saturationLevel == -1) {
                        ColorMatrix().apply { setSaturation(0.0f) }
                    } else {
                        val sr = 1.0f + saturationLevel * 0.05f
                        val sg = 1.0f + saturationLevel * 0.20f
                        val sb = 1.0f + saturationLevel * 0.20f
                        val wr = 0.2126f
                        val wg = 0.7152f
                        val wb = 0.0722f
                        ColorMatrix(floatArrayOf(
                            (1f - sr) * wr + sr, (1f - sr) * wg,      (1f - sr) * wb,      0f, 0f,
                            (1f - sg) * wr,      (1f - sg) * wg + sg, (1f - sg) * wb,      0f, 0f,
                            (1f - sb) * wr,      (1f - sb) * wg,      (1f - sb) * wb + sb, 0f, 0f,
                            0f,                  0f,                  0f,                  1f, 0f
                        ))
                    }
                } else null

                val lightMatrix = if (lightLevel > 0) {
                    val c = 1.0f + lightLevel * 0.05f
                    val t = (1.0f - c) * 35.0f
                    ColorMatrix(floatArrayOf(
                        c, 0f, 0f, 0f, t,
                        0f, c, 0f, 0f, t,
                        0f, 0f, c, 0f, t,
                        0f, 0f, 0f, 1f, 0f
                    ))
                } else null

                val darksMatrix = if (darkLevel > 0) {
                    val b = darkLevel * 3.5f
                    val s = 255.0f / (255.0f - b)
                    val t = -s * b
                    ColorMatrix(floatArrayOf(
                        s, 0f, 0f, 0f, t,
                        0f, s, 0f, 0f, t,
                        0f, 0f, s, 0f, t,
                        0f, 0f, 0f, 1f, 0f
                    ))
                } else null

                val toneMatrix = when {
                    lightMatrix != null && darksMatrix != null -> {
                        ColorMatrix().apply { setConcat(lightMatrix, darksMatrix) }
                    }
                    lightMatrix != null -> lightMatrix
                    darksMatrix != null -> darksMatrix
                    else -> null
                }

                val combinedMatrix = when {
                    satMatrix != null && toneMatrix != null -> {
                        ColorMatrix().apply { setConcat(toneMatrix, satMatrix) }
                    }
                    satMatrix != null -> satMatrix
                    toneMatrix != null -> toneMatrix
                    else -> null
                }

                if (combinedMatrix != null) {
                    val adjBitmap = Bitmap.createBitmap(
                        croppedBitmap.width,
                        croppedBitmap.height,
                        croppedBitmap.config ?: Bitmap.Config.ARGB_8888
                    )
                    val canvas = Canvas(adjBitmap)
                    val paint = Paint().apply {
                        colorFilter = ColorMatrixColorFilter(combinedMatrix)
                    }
                    canvas.drawBitmap(croppedBitmap, 0f, 0f, paint)
                    if (adjBitmap != croppedBitmap) {
                        croppedBitmap.recycle()
                    }
                    adjBitmap
                } else {
                    croppedBitmap
                }
            } else {
                croppedBitmap
            }

            val targetDateTaken = when {
                originalPhoto != null && originalPhoto.dateTaken > 0 -> originalPhoto.dateTaken
                originalPhoto != null && originalPhoto.dateAdded > 0 -> originalPhoto.dateAdded * 1000L
                else -> System.currentTimeMillis()
            }
            val targetDateModified = when {
                originalPhoto != null && originalPhoto.dateModified > 0 -> originalPhoto.dateModified
                originalPhoto != null && originalPhoto.dateTaken > 0 -> originalPhoto.dateTaken / 1000L
                originalPhoto != null && originalPhoto.dateAdded > 0 -> originalPhoto.dateAdded
                else -> System.currentTimeMillis() / 1000L
            }

            val filename = if (originalPhoto != null) {
                if (saveAsCopy) {
                    val originalName = originalPhoto.displayName
                    val dotIndex = originalName.lastIndexOf('.')
                    if (dotIndex > 0) {
                        val base = originalName.substring(0, dotIndex)
                        val ext = originalName.substring(dotIndex)
                        "${base}_edit$ext"
                    } else {
                        "${originalName}_edit.jpg"
                    }
                } else {
                    originalPhoto.displayName.ifBlank { "IMG_${System.currentTimeMillis()}_edit.jpg" }
                }
            } else {
                "IMG_${System.currentTimeMillis()}_edit.jpg"
            }

            val tempFile = File.createTempFile("edit_", ".jpg", context.cacheDir)
            val newUri = try {
                tempFile.outputStream().use { out ->
                    finalBitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                }
                if (!finalBitmap.isRecycled) {
                    finalBitmap.recycle()
                }

                // Preserve EXIF metadata and capture timestamps from source
                try {
                    val srcStream = context.contentResolver.openInputStream(sourceUri)
                    if (srcStream != null) {
                        val srcExif = srcStream.use { ExifInterface(it) }
                        val destExif = ExifInterface(tempFile.absolutePath)

                        val exifTagsToCopy = listOf(
                            ExifInterface.TAG_DATETIME,
                            ExifInterface.TAG_DATETIME_ORIGINAL,
                            ExifInterface.TAG_DATETIME_DIGITIZED,
                            ExifInterface.TAG_SUBSEC_TIME,
                            ExifInterface.TAG_SUBSEC_TIME_ORIGINAL,
                            ExifInterface.TAG_SUBSEC_TIME_DIGITIZED,
                            ExifInterface.TAG_OFFSET_TIME,
                            ExifInterface.TAG_OFFSET_TIME_ORIGINAL,
                            ExifInterface.TAG_OFFSET_TIME_DIGITIZED,
                            ExifInterface.TAG_GPS_LATITUDE,
                            ExifInterface.TAG_GPS_LATITUDE_REF,
                            ExifInterface.TAG_GPS_LONGITUDE,
                            ExifInterface.TAG_GPS_LONGITUDE_REF,
                            ExifInterface.TAG_GPS_ALTITUDE,
                            ExifInterface.TAG_GPS_ALTITUDE_REF,
                            ExifInterface.TAG_GPS_DATESTAMP,
                            ExifInterface.TAG_GPS_TIMESTAMP,
                            ExifInterface.TAG_MAKE,
                            ExifInterface.TAG_MODEL,
                            ExifInterface.TAG_FOCAL_LENGTH,
                            ExifInterface.TAG_F_NUMBER,
                            ExifInterface.TAG_EXPOSURE_TIME,
                            ExifInterface.TAG_ISO_SPEED_RATINGS,
                            ExifInterface.TAG_FLASH,
                            ExifInterface.TAG_WHITE_BALANCE
                        )

                        for (tag in exifTagsToCopy) {
                            val value = srcExif.getAttribute(tag)
                            if (value != null) {
                                destExif.setAttribute(tag, value)
                            }
                        }

                        if (destExif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL) == null) {
                            val sdf = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US)
                            val dateStr = sdf.format(Date(targetDateTaken))
                            destExif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, dateStr)
                            destExif.setAttribute(ExifInterface.TAG_DATETIME, dateStr)
                            destExif.setAttribute(ExifInterface.TAG_DATETIME_DIGITIZED, dateStr)
                        }

                        destExif.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
                        destExif.saveAttributes()
                    }
                } catch (e: Exception) {
                    Log.e("PhotoRepository", "Failed preserving EXIF metadata", e)
                }

                tempFile.setLastModified(targetDateModified * 1000L)

                if (!saveAsCopy && originalPhoto != null) {
                    deletePhoto(originalPhoto)
                }

                val contentValues = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.DATE_TAKEN, targetDateTaken)
                    put(MediaStore.Images.Media.DATE_MODIFIED, targetDateModified)
                    if (originalPhoto != null && originalPhoto.dateAdded > 0) {
                        put(MediaStore.Images.Media.DATE_ADDED, originalPhoto.dateAdded)
                    } else {
                        put(MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/simplah")
                        put(MediaStore.Images.Media.IS_PENDING, 1)
                    }
                }

                val insertedUri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                if (insertedUri != null) {
                    context.contentResolver.openOutputStream(insertedUri)?.use { out ->
                        tempFile.inputStream().use { input ->
                            input.copyTo(out)
                        }
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        contentValues.clear()
                        contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                        context.contentResolver.update(insertedUri, contentValues, null, null)
                    }
                }
                insertedUri
            } finally {
                tempFile.delete()
                if (!finalBitmap.isRecycled) {
                    finalBitmap.recycle()
                }
            }
            newUri
        } catch (e: Exception) {
            Log.e("PhotoRepository", "Failed to save edited photo", e)
            null
        }
    }

    suspend fun deletePhoto(photo: Photo): Boolean = withContext(Dispatchers.IO) {
        var fileRemoved = false
        try {
            if (!photo.filePath.isNullOrEmpty()) {
                val file = File(photo.filePath)
                if (file.exists()) {
                    fileRemoved = file.delete()
                }
            }
        } catch (e: Exception) {
            Log.e("PhotoRepository", "Direct file deletion exception", e)
        }

        try {
            val count = context.contentResolver.delete(photo.uri, null, null)
            count > 0 || fileRemoved
        } catch (e: Exception) {
            Log.e("PhotoRepository", "ContentResolver deletion exception", e)
            fileRemoved
        }
    }
}
