package com.example.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.RectF
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.RotateLeft
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.pipeline.kTileH
import com.example.pipeline.kTileW
import com.example.ui.theme.GalleryGreen
import com.example.ui.theme.InkDark
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private class CropSubmitState {
    var computeCropRect: (() -> RectF)? = null
}

@Composable
fun CropEditorScreen(
    originalTempFilePath: String,
    onCancel: () -> Unit,
    onDone: (rotationDegrees: Int, cropRectInRotatedBitmap: RectF) -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onCancel)

    val originalBitmap =
        remember(originalTempFilePath) {
            BitmapFactory.decodeFile(originalTempFilePath)
        }

    if (originalBitmap == null) {
        onCancel()
        return
    }

    var rotationDegrees by remember { mutableIntStateOf(0) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    val submitState = remember { CropSubmitState() }

    val rotatedBitmap: Bitmap =
        remember(originalBitmap, rotationDegrees) {
            if (rotationDegrees % 360 == 0) {
                originalBitmap
            } else {
                val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                Bitmap.createBitmap(
                    originalBitmap,
                    0,
                    0,
                    originalBitmap.width,
                    originalBitmap.height,
                    matrix,
                    true,
                )
            }
        }
    val rotatedImageBitmap = remember(rotatedBitmap) { rotatedBitmap.asImageBitmap() }

    val cropAspect = kTileW.toFloat() / kTileH.toFloat()

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(Color(0xFF0E141B))
                .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        // High-contrast toolbar titled "Adjust photo" with Cancel & large DONE button
        Surface(
            color = InkDark,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Surface(
                    onClick = onCancel,
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFF283446),
                    modifier = Modifier.heightIn(min = 52.dp).testTag("crop_cancel_button"),
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.crop_cancel_button),
                            tint = Color.White,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }

                Text(
                    text = stringResource(R.string.crop_editor_title),
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 21.sp),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    textAlign = TextAlign.Center,
                )

                Button(
                    onClick = {
                        val rect =
                            submitState.computeCropRect?.invoke()
                                ?: RectF(
                                    0f,
                                    0f,
                                    rotatedBitmap.width.toFloat(),
                                    rotatedBitmap.height.toFloat(),
                                )
                        onDone(rotationDegrees, rect)
                    },
                    shape = RoundedCornerShape(20.dp),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = GalleryGreen,
                            contentColor = Color.White,
                        ),
                    modifier = Modifier.heightIn(min = 52.dp).testTag("crop_done_button"),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.crop_done_button),
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 18.sp),
                    )
                }
            }
        }

        // Interactive Crop Canvas (locked to kTileW : kTileH, drag + pinch-to-zoom)
        BoxWithConstraints(
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            val density = LocalDensity.current
            val canvasWidthPx = with(density) { maxWidth.toPx() }
            val canvasHeightPx = with(density) { maxHeight.toPx() }

            val maxCropW = canvasWidthPx * 0.84f
            val maxCropH = canvasHeightPx * 0.86f
            val cropW: Float
            val cropH: Float
            if (maxCropW / cropAspect <= maxCropH) {
                cropW = maxCropW
                cropH = cropW / cropAspect
            } else {
                cropH = maxCropH
                cropW = cropH * cropAspect
            }
            val cropLeft = (canvasWidthPx - cropW) / 2f
            val cropTop = (canvasHeightPx - cropH) / 2f

            val imgW = rotatedBitmap.width.toFloat()
            val imgH = rotatedBitmap.height.toFloat()
            val baseScale = max(cropW / imgW, cropH / imgH)

            fun clampOffsets(z: Float, dx: Float, dy: Float): Pair<Float, Float> {
                val drawnW = imgW * baseScale * z
                val drawnH = imgH * baseScale * z
                val maxDx = max(0f, (drawnW - cropW) / 2f)
                val maxDy = max(0f, (drawnH - cropH) / 2f)
                return dx.coerceIn(-maxDx, maxDx) to dy.coerceIn(-maxDy, maxDy)
            }

            val (clampedDx, clampedDy) = clampOffsets(zoom, offsetX, offsetY)

            submitState.computeCropRect = {
                val totalScale = baseScale * zoom
                val drawnW = imgW * totalScale
                val drawnH = imgH * totalScale
                val imgLeft = (canvasWidthPx - drawnW) / 2f + clampedDx
                val imgTop = (canvasHeightPx - drawnH) / 2f + clampedDy

                val srcLeft = ((cropLeft - imgLeft) / totalScale).coerceIn(0f, imgW - 1f)
                val srcTop = ((cropTop - imgTop) / totalScale).coerceIn(0f, imgH - 1f)
                val srcW = min(imgW - srcLeft, cropW / totalScale)
                val srcH = min(imgH - srcTop, cropH / totalScale)

                RectF(srcLeft, srcTop, srcLeft + srcW, srcTop + srcH)
            }

            Canvas(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .pointerInput(rotationDegrees, cropW, cropH) {
                            detectTransformGestures { _, pan, gestureZoom, _ ->
                                val newZoom = (zoom * gestureZoom).coerceIn(1f, 5f)
                                val (nx, ny) =
                                    clampOffsets(
                                        newZoom,
                                        offsetX + pan.x,
                                        offsetY + pan.y,
                                    )
                                zoom = newZoom
                                offsetX = nx
                                offsetY = ny
                            }
                        }
                        .testTag("crop_canvas"),
            ) {
                val totalScale = baseScale * zoom
                val drawnW = imgW * totalScale
                val drawnH = imgH * totalScale
                val imgLeft = (size.width - drawnW) / 2f + clampedDx
                val imgTop = (size.height - drawnH) / 2f + clampedDy

                drawImage(
                    image = rotatedImageBitmap,
                    dstOffset = IntOffset(imgLeft.roundToInt(), imgTop.roundToInt()),
                    dstSize = IntSize(drawnW.roundToInt(), drawnH.roundToInt()),
                )

                // Dark translucent scrim outside the locked kTileW:kTileH crop frame
                val scrimPath =
                    Path().apply {
                        fillType = PathFillType.EvenOdd
                        addRect(androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height))
                        addRect(
                            androidx.compose.ui.geometry.Rect(
                                cropLeft,
                                cropTop,
                                cropLeft + cropW,
                                cropTop + cropH,
                            ),
                        )
                    }
                drawPath(path = scrimPath, color = Color.Black.copy(alpha = 0.62f))

                // Crisp white crop rectangle
                drawRect(
                    color = Color.White,
                    topLeft = Offset(cropLeft, cropTop),
                    size = Size(cropW, cropH),
                    style = Stroke(width = 3.dp.toPx()),
                )

                // Subtle rule-of-thirds guides
                val thirdW = cropW / 3f
                val thirdH = cropH / 3f
                val guideColor = Color.White.copy(alpha = 0.35f)
                for (i in 1..2) {
                    drawLine(
                        color = guideColor,
                        start = Offset(cropLeft + thirdW * i, cropTop),
                        end = Offset(cropLeft + thirdW * i, cropTop + cropH),
                        strokeWidth = 1.dp.toPx(),
                    )
                    drawLine(
                        color = guideColor,
                        start = Offset(cropLeft, cropTop + thirdH * i),
                        end = Offset(cropLeft + cropW, cropTop + thirdH * i),
                        strokeWidth = 1.dp.toPx(),
                    )
                }
            }
        }

        // Bottom control bar with instructions and Rotate Left / Rotate Right buttons
        Surface(
            color = InkDark,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.crop_instructions),
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 16.sp),
                    color = Color(0xFFD0D5DD),
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(
                        onClick = {
                            rotationDegrees = (rotationDegrees + 270) % 360
                            zoom = 1f
                            offsetX = 0f
                            offsetY = 0f
                        },
                        shape = RoundedCornerShape(24.dp),
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF283446),
                                contentColor = Color.White,
                            ),
                        modifier =
                            Modifier
                                .weight(1f)
                                .heightIn(min = 56.dp)
                                .testTag("rotate_left_button"),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.RotateLeft,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.crop_rotate_left),
                            style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp),
                        )
                    }

                    Button(
                        onClick = {
                            rotationDegrees = (rotationDegrees + 90) % 360
                            zoom = 1f
                            offsetX = 0f
                            offsetY = 0f
                        },
                        shape = RoundedCornerShape(24.dp),
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF283446),
                                contentColor = Color.White,
                            ),
                        modifier =
                            Modifier
                                .weight(1f)
                                .heightIn(min = 56.dp)
                                .testTag("rotate_right_button"),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.RotateRight,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.crop_rotate_right),
                            style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp),
                        )
                    }
                }
            }
        }
    }
}
