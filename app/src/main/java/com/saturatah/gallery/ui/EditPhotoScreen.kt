package com.saturatah.gallery.ui

import android.graphics.Bitmap
import android.graphics.Matrix
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saturatah.gallery.model.Photo
import com.saturatah.gallery.ui.theme.PureBlack
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.hypot

enum class ActiveEditTool {
    NONE, COLOR, CROP
}

private enum class DragHandle {
    NONE, CENTER, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT, TOP, BOTTOM, LEFT, RIGHT
}

@Composable
fun EditPhotoScreen(
    photo: Photo,
    onCancel: () -> Unit,
    onSave: suspend (
        rotationDegrees: Int,
        cropLeft: Float,
        cropTop: Float,
        cropRight: Float,
        cropBottom: Float,
        saturationLevel: Int
    ) -> Boolean,
    loadBitmap: suspend (Photo) -> Bitmap?,
    modifier: Modifier = Modifier
) {
    var activeTool by remember { mutableStateOf(ActiveEditTool.NONE) }

    BackHandler {
        if (activeTool != ActiveEditTool.NONE) {
            activeTool = ActiveEditTool.NONE
        } else {
            onCancel()
        }
    }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val hapticFeedback = LocalHapticFeedback.current

    var sourceBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoadingBitmap by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }

    var rotationDegrees by remember { mutableIntStateOf(0) }
    var saturationLevel by remember { mutableIntStateOf(0) } // Discrete 0 to 10

    // Normalized freeform crop box (0.0 .. 1.0) relative to rotated bitmap bounds
    var cropLeft by remember { mutableFloatStateOf(0f) }
    var cropTop by remember { mutableFloatStateOf(0f) }
    var cropRight by remember { mutableFloatStateOf(1f) }
    var cropBottom by remember { mutableFloatStateOf(1f) }

    LaunchedEffect(photo.id) {
        isLoadingBitmap = true
        sourceBitmap = loadBitmap(photo)
        isLoadingBitmap = false
    }

    // Helper to calculate rotated bitmap for display
    val displayedBitmap = remember(sourceBitmap, rotationDegrees) {
        val src = sourceBitmap ?: return@remember null
        if (rotationDegrees % 360 == 0) {
            src
        } else {
            val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
            Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
        }
    }

    // Real-time GPU color filter for saturation adjustment
    val saturationColorFilter = remember(saturationLevel) {
        if (saturationLevel > 0) {
            val factor = 1.0f + saturationLevel * 0.15f
            val cm = ColorMatrix().apply { setToSaturation(factor) }
            ColorFilter.colorMatrix(cm)
        } else {
            null
        }
    }

    fun handleRotate() {
        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
        rotationDegrees = (rotationDegrees + 90) % 360
        cropLeft = 0f
        cropTop = 0f
        cropRight = 1f
        cropBottom = 1f
    }

    fun handleReset() {
        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
        rotationDegrees = 0
        saturationLevel = 0
        cropLeft = 0f
        cropTop = 0f
        cropRight = 1f
        cropBottom = 1f
        activeTool = ActiveEditTool.NONE
    }

    fun handleSave() {
        if (isSaving) return
        isSaving = true
        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)

        coroutineScope.launch {
            val success = onSave(rotationDegrees, cropLeft, cropTop, cropRight, cropBottom, saturationLevel)
            if (success) {
                Toast.makeText(context, "Photo saved", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Failed to save photo", Toast.LENGTH_SHORT).show()
                isSaving = false
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PureBlack)
    ) {
        // Top action bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = onCancel,
                enabled = !isSaving,
                modifier = Modifier
                    .size(40.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Cancel",
                    tint = Color.White
                )
            }

            Text(
                text = "Edit Photo",
                color = Color.White,
                fontSize = 18.sp,
                style = MaterialTheme.typography.titleMedium
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { handleReset() },
                    enabled = !isSaving,
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.RestartAlt,
                        contentDescription = "Reset",
                        tint = Color.White
                    )
                }

                IconButton(
                    onClick = { handleSave() },
                    enabled = !isSaving && !isLoadingBitmap,
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = "Save",
                            tint = Color.White
                        )
                    }
                }
            }
        }

        // Center preview and interactive crop overlay
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            if (isLoadingBitmap || displayedBitmap == null) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 2.dp
                )
            } else {
                val bmp = displayedBitmap
                val composeBitmap = remember(bmp) { bmp.asImageBitmap() }

                BoxWithConstraints(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    val density = LocalDensity.current
                    val viewWidthPx = constraints.maxWidth.toFloat()
                    val viewHeightPx = constraints.maxHeight.toFloat()
                    val paddingPx = with(density) { 24.dp.toPx() }

                    val availableWidth = (viewWidthPx - paddingPx * 2).coerceAtLeast(10f)
                    val availableHeight = (viewHeightPx - paddingPx * 2).coerceAtLeast(10f)

                    val bmpW = bmp.width.toFloat()
                    val bmpH = bmp.height.toFloat()

                    val scale = minOf(availableWidth / bmpW, availableHeight / bmpH)
                    val imgDisplayW = bmpW * scale
                    val imgDisplayH = bmpH * scale

                    val imgOffsetX = (viewWidthPx - imgDisplayW) / 2f
                    val imgOffsetY = (viewHeightPx - imgDisplayH) / 2f

                    val touchRadius = with(density) { 36.dp.toPx() }
                    var activeHandle by remember { mutableStateOf(DragHandle.NONE) }

                    val canvasModifier = if (activeTool == ActiveEditTool.CROP) {
                        Modifier
                            .fillMaxSize()
                            .pointerInput(imgDisplayW, imgDisplayH) {
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        val curRectL = imgOffsetX + cropLeft * imgDisplayW
                                        val curRectT = imgOffsetY + cropTop * imgDisplayH
                                        val curRectR = imgOffsetX + cropRight * imgDisplayW
                                        val curRectB = imgOffsetY + cropBottom * imgDisplayH

                                        val x = offset.x
                                        val y = offset.y

                                        fun dist(px: Float, py: Float) = hypot(x - px, y - py)

                                        activeHandle = when {
                                            dist(curRectL, curRectT) <= touchRadius -> DragHandle.TOP_LEFT
                                            dist(curRectR, curRectT) <= touchRadius -> DragHandle.TOP_RIGHT
                                            dist(curRectL, curRectB) <= touchRadius -> DragHandle.BOTTOM_LEFT
                                            dist(curRectR, curRectB) <= touchRadius -> DragHandle.BOTTOM_RIGHT
                                            abs(y - curRectT) <= touchRadius && x in curRectL..curRectR -> DragHandle.TOP
                                            abs(y - curRectB) <= touchRadius && x in curRectL..curRectR -> DragHandle.BOTTOM
                                            abs(x - curRectL) <= touchRadius && y in curRectT..curRectB -> DragHandle.LEFT
                                            abs(x - curRectR) <= touchRadius && y in curRectT..curRectB -> DragHandle.RIGHT
                                            x in curRectL..curRectR && y in curRectT..curRectB -> DragHandle.CENTER
                                            else -> DragHandle.NONE
                                        }
                                    },
                                    onDragEnd = {
                                        activeHandle = DragHandle.NONE
                                    },
                                    onDragCancel = {
                                        activeHandle = DragHandle.NONE
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        if (activeHandle == DragHandle.NONE) return@detectDragGestures

                                        val ndx = dragAmount.x / imgDisplayW
                                        val ndy = dragAmount.y / imgDisplayH
                                        val minNormSize = 0.08f

                                        when (activeHandle) {
                                            DragHandle.CENTER -> {
                                                val w = cropRight - cropLeft
                                                val h = cropBottom - cropTop
                                                val newL = (cropLeft + ndx).coerceIn(0f, 1f - w)
                                                val newT = (cropTop + ndy).coerceIn(0f, 1f - h)
                                                cropLeft = newL
                                                cropRight = newL + w
                                                cropTop = newT
                                                cropBottom = newT + h
                                            }

                                            DragHandle.TOP_LEFT -> {
                                                cropLeft = (cropLeft + ndx).coerceIn(0f, cropRight - minNormSize)
                                                cropTop = (cropTop + ndy).coerceIn(0f, cropBottom - minNormSize)
                                            }

                                            DragHandle.TOP_RIGHT -> {
                                                cropRight = (cropRight + ndx).coerceIn(cropLeft + minNormSize, 1f)
                                                cropTop = (cropTop + ndy).coerceIn(0f, cropBottom - minNormSize)
                                            }

                                            DragHandle.BOTTOM_LEFT -> {
                                                cropLeft = (cropLeft + ndx).coerceIn(0f, cropRight - minNormSize)
                                                cropBottom = (cropBottom + ndy).coerceIn(cropTop + minNormSize, 1f)
                                            }

                                            DragHandle.BOTTOM_RIGHT -> {
                                                cropRight = (cropRight + ndx).coerceIn(cropLeft + minNormSize, 1f)
                                                cropBottom = (cropBottom + ndy).coerceIn(cropTop + minNormSize, 1f)
                                            }

                                            DragHandle.TOP -> {
                                                cropTop = (cropTop + ndy).coerceIn(0f, cropBottom - minNormSize)
                                            }

                                            DragHandle.BOTTOM -> {
                                                cropBottom = (cropBottom + ndy).coerceIn(cropTop + minNormSize, 1f)
                                            }

                                            DragHandle.LEFT -> {
                                                cropLeft = (cropLeft + ndx).coerceIn(0f, cropRight - minNormSize)
                                            }

                                            DragHandle.RIGHT -> {
                                                cropRight = (cropRight + ndx).coerceIn(cropLeft + minNormSize, 1f)
                                            }

                                            else -> Unit
                                        }
                                    }
                                )
                            }
                    } else {
                        Modifier.fillMaxSize()
                    }

                    Canvas(modifier = canvasModifier) {
                        if (activeTool == ActiveEditTool.CROP) {
                            // In CROP mode: draw full image with interactive crop overlay
                            drawImage(
                                image = composeBitmap,
                                dstOffset = IntOffset(imgOffsetX.toInt(), imgOffsetY.toInt()),
                                dstSize = IntSize(imgDisplayW.toInt(), imgDisplayH.toInt()),
                                colorFilter = saturationColorFilter
                            )

                            val cLeft = imgOffsetX + cropLeft * imgDisplayW
                            val cTop = imgOffsetY + cropTop * imgDisplayH
                            val cRight = imgOffsetX + cropRight * imgDisplayW
                            val cBottom = imgOffsetY + cropBottom * imgDisplayH
                            val cWidth = cRight - cLeft
                            val cHeight = cBottom - cTop

                            val dimColor = Color.Black.copy(alpha = 0.65f)

                            // Dimmed overlay outside crop box
                            drawRect(
                                color = dimColor,
                                topLeft = Offset(0f, 0f),
                                size = Size(size.width, cTop)
                            )
                            drawRect(
                                color = dimColor,
                                topLeft = Offset(0f, cBottom),
                                size = Size(size.width, size.height - cBottom)
                            )
                            drawRect(
                                color = dimColor,
                                topLeft = Offset(0f, cTop),
                                size = Size(cLeft, cHeight)
                            )
                            drawRect(
                                color = dimColor,
                                topLeft = Offset(cRight, cTop),
                                size = Size(size.width - cRight, cHeight)
                            )

                            // Crop box outline
                            drawRect(
                                color = Color.White.copy(alpha = 0.85f),
                                topLeft = Offset(cLeft, cTop),
                                size = Size(cWidth, cHeight),
                                style = Stroke(width = 1.5f.dp.toPx())
                            )

                            // 3x3 grid lines
                            val gridStroke = Stroke(width = 1f.dp.toPx())
                            val gridColor = Color.White.copy(alpha = 0.35f)

                            drawLine(
                                color = gridColor,
                                start = Offset(cLeft + cWidth / 3f, cTop),
                                end = Offset(cLeft + cWidth / 3f, cBottom),
                                strokeWidth = gridStroke.width
                            )
                            drawLine(
                                color = gridColor,
                                start = Offset(cLeft + 2 * cWidth / 3f, cTop),
                                end = Offset(cLeft + 2 * cWidth / 3f, cBottom),
                                strokeWidth = gridStroke.width
                            )
                            drawLine(
                                color = gridColor,
                                start = Offset(cLeft, cTop + cHeight / 3f),
                                end = Offset(cRight, cTop + cHeight / 3f),
                                strokeWidth = gridStroke.width
                            )
                            drawLine(
                                color = gridColor,
                                start = Offset(cLeft, cTop + 2 * cHeight / 3f),
                                end = Offset(cRight, cTop + 2 * cHeight / 3f),
                                strokeWidth = gridStroke.width
                            )

                            // Corner L-handles
                            val cornerLen = 18.dp.toPx()
                            val cornerStroke = 3.5f.dp.toPx()
                            val cornerColor = Color.White

                            // Top-Left
                            drawLine(cornerColor, Offset(cLeft, cTop), Offset(cLeft + cornerLen, cTop), cornerStroke)
                            drawLine(cornerColor, Offset(cLeft, cTop), Offset(cLeft, cTop + cornerLen), cornerStroke)

                            // Top-Right
                            drawLine(cornerColor, Offset(cRight, cTop), Offset(cRight - cornerLen, cTop), cornerStroke)
                            drawLine(cornerColor, Offset(cRight, cTop), Offset(cRight, cTop + cornerLen), cornerStroke)

                            // Bottom-Left
                            drawLine(cornerColor, Offset(cLeft, cBottom), Offset(cLeft + cornerLen, cBottom), cornerStroke)
                            drawLine(cornerColor, Offset(cLeft, cBottom), Offset(cLeft, cBottom - cornerLen), cornerStroke)

                            // Bottom-Right
                            drawLine(cornerColor, Offset(cRight, cBottom), Offset(cRight - cornerLen, cBottom), cornerStroke)
                            drawLine(cornerColor, Offset(cRight, cBottom), Offset(cRight, cBottom - cornerLen), cornerStroke)
                        } else {
                            // In NONE or COLOR mode: draw cropped image cleanly fitted
                            val srcL = (cropLeft * bmp.width).toInt().coerceIn(0, bmp.width - 1)
                            val srcT = (cropTop * bmp.height).toInt().coerceIn(0, bmp.height - 1)
                            val srcR = (cropRight * bmp.width).toInt().coerceIn(srcL + 1, bmp.width)
                            val srcB = (cropBottom * bmp.height).toInt().coerceIn(srcT + 1, bmp.height)
                            val srcW = srcR - srcL
                            val srcH = srcB - srcT

                            val croppedScale = minOf(availableWidth / srcW.toFloat(), availableHeight / srcH.toFloat())
                            val dispW = srcW.toFloat() * croppedScale
                            val dispH = srcH.toFloat() * croppedScale
                            val offX = (viewWidthPx - dispW) / 2f
                            val offY = (viewHeightPx - dispH) / 2f

                            drawImage(
                                image = composeBitmap,
                                srcOffset = IntOffset(srcL, srcT),
                                srcSize = IntSize(srcW, srcH),
                                dstOffset = IntOffset(offX.toInt(), offY.toInt()),
                                dstSize = IntSize(dispW.toInt(), dispH.toInt()),
                                colorFilter = saturationColorFilter
                            )
                        }
                    }
                }
            }
        }

        // Bottom editing controls - Menu or Active Tool Controls
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.85f))
                .navigationBarsPadding()
                .padding(vertical = 12.dp, horizontal = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            when (activeTool) {
                ActiveEditTool.NONE -> {
                    // Main Menu Bar: Color and Crop buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            onClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                activeTool = ActiveEditTool.COLOR
                            },
                            shape = RoundedCornerShape(20.dp),
                            color = Color.White.copy(alpha = 0.12f),
                            modifier = Modifier.height(44.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 24.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Palette,
                                    contentDescription = "Color",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "Color",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }

                        Surface(
                            onClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                activeTool = ActiveEditTool.CROP
                            },
                            shape = RoundedCornerShape(20.dp),
                            color = Color.White.copy(alpha = 0.12f),
                            modifier = Modifier.height(44.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 24.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Crop,
                                    contentDescription = "Crop",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "Crop",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    }
                }

                ActiveEditTool.COLOR -> {
                    // Color Stepper with Checkmark button to return to menu
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Spacer(modifier = Modifier.size(40.dp))

                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color.White.copy(alpha = 0.12f),
                            modifier = Modifier.height(42.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.padding(horizontal = 8.dp)
                            ) {
                                IconButton(
                                    onClick = {
                                        if (saturationLevel > 0) {
                                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                            saturationLevel--
                                        }
                                    },
                                    enabled = saturationLevel > 0,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Remove,
                                        contentDescription = "Decrease color",
                                        tint = if (saturationLevel > 0) Color.White else Color.White.copy(alpha = 0.3f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                Text(
                                    text = if (saturationLevel == 0) "Color: 0" else "Color: +$saturationLevel",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                )

                                IconButton(
                                    onClick = {
                                        if (saturationLevel < 10) {
                                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                            saturationLevel++
                                        }
                                    },
                                    enabled = saturationLevel < 10,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Increase color",
                                        tint = if (saturationLevel < 10) Color.White else Color.White.copy(alpha = 0.3f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        IconButton(
                            onClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                activeTool = ActiveEditTool.NONE
                            },
                            modifier = Modifier
                                .size(40.dp)
                                .background(Color.White.copy(alpha = 0.15f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Done",
                                tint = Color.White
                            )
                        }
                    }
                }

                ActiveEditTool.CROP -> {
                    // Rotate 90° button with Checkmark button to return to menu
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Spacer(modifier = Modifier.size(40.dp))

                        Surface(
                            onClick = { handleRotate() },
                            shape = RoundedCornerShape(20.dp),
                            color = Color.White.copy(alpha = 0.12f),
                            modifier = Modifier.height(42.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 20.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.RotateRight,
                                    contentDescription = "Rotate 90°",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "Rotate 90°",
                                    color = Color.White,
                                    fontSize = 14.sp
                                )
                            }
                        }

                        IconButton(
                            onClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                activeTool = ActiveEditTool.NONE
                            },
                            modifier = Modifier
                                .size(40.dp)
                                .background(Color.White.copy(alpha = 0.15f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Done",
                                tint = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}
