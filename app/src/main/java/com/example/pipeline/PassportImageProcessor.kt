package com.example.pipeline

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.suspendCancellableCoroutine

const val kSheetW = 1200      // 4 in @ 300 DPI
const val kSheetH = 1800      // 6 in @ 300 DPI
const val kCols = 3
const val kRows = 4
const val kTileW = 340        // outer size of one photo incl. border
const val kTileH = 395
const val kGapX = 22
const val kGapY = 13
const val kBorder = 4         // solid black border on every photo

enum class BackgroundOption(val labelResName: String) {
    ORIGINAL("Original BG"),
    WHITE("White BG"),
    BLUE("Blue BG"),
}

data class ProcessedPortrait(
    val width: Int,
    val height: Int,
    val pixels: IntArray,
    val mask: FloatArray?,
    val usedFallbackCrop: Boolean,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ProcessedPortrait) return false
        return width == other.width &&
            height == other.height &&
            usedFallbackCrop == other.usedFallbackCrop &&
            pixels.contentEquals(other.pixels) &&
            (mask?.contentEquals(other.mask) ?: (other.mask == null))
    }

    override fun hashCode(): Int {
        var result = width
        result = 31 * result + height
        result = 31 * result + pixels.contentHashCode()
        result = 31 * result + (mask?.contentHashCode() ?: 0)
        result = 31 * result + usedFallbackCrop.hashCode()
        return result
    }
}

data class InitialProcessingResult(
    val originalTempFilePath: String,
    val autoPortrait: ProcessedPortrait,
)

data class CropPlan(
    val rect: RectF,
    val usedFallbackCrop: Boolean,
)

interface PersonSegmenter {
    suspend fun segment(bitmap: Bitmap): FloatArray?
}

class MlKitPersonSegmenter : PersonSegmenter {
    private val segmenter by lazy {
        val options =
            SelfieSegmenterOptions.Builder()
                .setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE)
                .build()
        Segmentation.getClient(options)
    }

    override suspend fun segment(bitmap: Bitmap): FloatArray? =
        suspendCancellableCoroutine { continuation ->
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            segmenter
                .process(inputImage)
                .addOnSuccessListener { segmentationMask ->
                    if (!continuation.isActive) return@addOnSuccessListener
                    try {
                        val maskWidth = segmentationMask.width
                        val maskHeight = segmentationMask.height
                        val buffer: ByteBuffer = segmentationMask.buffer
                        buffer.rewind()
                        val floatBuffer = buffer.asFloatBuffer()
                        val rawMask = FloatArray(maskWidth * maskHeight)
                        floatBuffer.get(rawMask)

                        val alignedMask =
                            if (maskWidth == bitmap.width && maskHeight == bitmap.height) {
                                rawMask
                            } else {
                                PassportImageProcessor.resampleMaskBilinear(
                                    mask = rawMask,
                                    srcWidth = maskWidth,
                                    srcHeight = maskHeight,
                                    srcRect = RectF(0f, 0f, maskWidth.toFloat(), maskHeight.toFloat()),
                                    dstWidth = bitmap.width,
                                    dstHeight = bitmap.height,
                                )
                            }
                        continuation.resume(alignedMask)
                    } catch (_: Exception) {
                        continuation.resume(null)
                    }
                }
                .addOnFailureListener { e ->
                    if (continuation.isActive) {
                        continuation.resumeWithException(e)
                    }
                }
        }
}

object PassportImageProcessor {
    const val MAX_INPUT_DIMENSION = 1600

    val kMarginX: Int = (kSheetW - kCols * kTileW - (kCols - 1) * kGapX) / 2 // 68 px left AND right
    val kMarginY: Int = (kSheetH - kRows * kTileH - (kRows - 1) * kGapY) / 2 // 90 px top AND bottom
    const val kInnerPhotoW: Int = kTileW - 2 * kBorder // 332 px
    const val kInnerPhotoH: Int = kTileH - 2 * kBorder // 387 px

    const val WHITE_BG_COLOR = 0xFFFFFFFF.toInt()
    const val BLUE_BG_COLOR = 0xFF1E90FF.toInt()
    const val BORDER_BLACK_COLOR = Color.BLACK

    private const val TARGET_LUMINANCE = 140f
    private const val MIN_GAIN = 0.9f
    private const val MAX_GAIN = 1.45f
    private const val HIGHLIGHT_KNEE = 235f
    private const val MIN_PERSON_COVERAGE = 0.015f

    /**
     * Decodes, fixes EXIF orientation, saves the orientation-corrected original to a temp file
     * for lossless repeated manual re-cropping, and computes the initial auto-cropped portrait.
     */
    suspend fun processPhotoUriWithTempOriginal(
        context: Context,
        uri: Uri,
        segmenter: PersonSegmenter = MlKitPersonSegmenter(),
    ): InitialProcessingResult {
        val normalizedBitmap = decodeAndNormalizeBitmap(context, uri)
        val tempFilePath = saveOrientationCorrectedOriginal(context, normalizedBitmap)

        val mask =
            try {
                segmenter.segment(normalizedBitmap)
            } catch (_: Exception) {
                null
            }

        val cropPlan =
            computePassportCropRect(
                imageWidth = normalizedBitmap.width,
                imageHeight = normalizedBitmap.height,
                mask = mask,
            )

        val portrait =
            cropAndResizePortrait(
                source = normalizedBitmap,
                mask = mask,
                cropRect = cropPlan.rect,
                usedFallbackCrop = cropPlan.usedFallbackCrop,
            )
        normalizedBitmap.recycle()

        return InitialProcessingResult(
            originalTempFilePath = tempFilePath,
            autoPortrait = portrait,
        )
    }

    /**
     * Applies a manual crop on the orientation-corrected original temp file, ensures the cropped
     * image is not downscaled below tile size, RE-RUNS person segmentation on the cropped image,
     * skips auto-crop, and resizes to kInnerPhotoW x kInnerPhotoH.
     */
    suspend fun processManualCrop(
        originalTempFilePath: String,
        rotationDegrees: Int,
        cropRectInRotatedBitmap: RectF,
        segmenter: PersonSegmenter = MlKitPersonSegmenter(),
    ): ProcessedPortrait {
        val baseBitmap =
            BitmapFactory.decodeFile(originalTempFilePath)
                ?: throw IllegalStateException("Original photo file is missing.")

        val rotatedBitmap =
            if (rotationDegrees % 360 != 0) {
                val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                Bitmap.createBitmap(baseBitmap, 0, 0, baseBitmap.width, baseBitmap.height, matrix, true)
                    .also { if (it !== baseBitmap) baseBitmap.recycle() }
            } else {
                baseBitmap
            }

        // Extract the cropped region cleanly preserving aspect ratio, ensuring >= kTileW x kTileH
        val clampedLeft = cropRectInRotatedBitmap.left.roundToInt().coerceIn(0, rotatedBitmap.width - 1)
        val clampedTop = cropRectInRotatedBitmap.top.roundToInt().coerceIn(0, rotatedBitmap.height - 1)
        val clampedRight = cropRectInRotatedBitmap.right.roundToInt().coerceIn(clampedLeft + 1, rotatedBitmap.width)
        val clampedBottom = cropRectInRotatedBitmap.bottom.roundToInt().coerceIn(clampedTop + 1, rotatedBitmap.height)

        val srcRect = Rect(clampedLeft, clampedTop, clampedRight, clampedBottom)
        val cropAspect = kTileW.toFloat() / kTileH.toFloat()
        val segHeight = max(kTileH * 2, min(MAX_INPUT_DIMENSION, max(kTileH, srcRect.height())))
        val segWidth = max(kTileW, (segHeight * cropAspect).roundToInt())

        val croppedForSegmentation =
            Bitmap.createBitmap(segWidth, segHeight, Bitmap.Config.ARGB_8888).also { dst ->
                val canvas = Canvas(dst)
                val dstRect = Rect(0, 0, segWidth, segHeight)
                val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
                canvas.drawBitmap(rotatedBitmap, srcRect, dstRect, paint)
            }
        rotatedBitmap.recycle()

        val rawMask =
            try {
                segmenter.segment(croppedForSegmentation)
            } catch (_: Exception) {
                null
            }

        val hasUsableMask =
            rawMask != null && hasSufficientForeground(rawMask, MIN_PERSON_COVERAGE)

        val fullRect =
            RectF(
                0f,
                0f,
                croppedForSegmentation.width.toFloat(),
                croppedForSegmentation.height.toFloat(),
            )
        val portrait =
            cropAndResizePortrait(
                source = croppedForSegmentation,
                mask = if (hasUsableMask) rawMask else null,
                cropRect = fullRect,
                usedFallbackCrop = !hasUsableMask,
            )
        croppedForSegmentation.recycle()
        return portrait
    }

    private fun saveOrientationCorrectedOriginal(context: Context, bitmap: Bitmap): String {
        val dir = File(context.cacheDir, "passport_originals")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        // Keep cache clean by removing older temporary originals
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "original_${System.currentTimeMillis()}.jpg")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
        }
        return file.absolutePath
    }

    fun cleanupTempOriginals(context: Context) {
        val dir = File(context.cacheDir, "passport_originals")
        if (dir.exists()) {
            dir.listFiles()?.forEach { it.delete() }
        }
    }

    /**
     * Decodes a URI, applies EXIF orientation, and downscales so longest side <= 1600px.
     */
    fun decodeAndNormalizeBitmap(context: Context, uri: Uri): Bitmap {
        val resolver = context.contentResolver

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val streamOpened =
            resolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, bounds)
                true
            } ?: false
        if (!streamOpened) {
            throw IllegalStateException("Unable to open selected photo.")
        }

        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw IllegalStateException("Selected file is not a valid image.")
        }

        val sampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, MAX_INPUT_DIMENSION)
        val decodeOptions =
            BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }

        val decoded =
            resolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            } ?: throw IllegalStateException("Could not decode photo.")

        val orientation =
            resolver.openInputStream(uri)?.use { stream ->
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            } ?: ExifInterface.ORIENTATION_NORMAL

        val oriented = applyExifOrientation(decoded, orientation)
        if (oriented !== decoded) {
            decoded.recycle()
        }

        val longestSide = max(oriented.width, oriented.height)
        if (longestSide <= MAX_INPUT_DIMENSION) {
            return if (oriented.config == Bitmap.Config.ARGB_8888) {
                oriented
            } else {
                oriented.copy(Bitmap.Config.ARGB_8888, false).also { oriented.recycle() }
            }
        }

        val scale = MAX_INPUT_DIMENSION.toFloat() / longestSide.toFloat()
        val targetWidth = max(1, (oriented.width * scale).roundToInt())
        val targetHeight = max(1, (oriented.height * scale).roundToInt())
        val scaled = Bitmap.createScaledBitmap(oriented, targetWidth, targetHeight, true)
        if (scaled !== oriented) {
            oriented.recycle()
        }
        return if (scaled.config == Bitmap.Config.ARGB_8888) {
            scaled
        } else {
            scaled.copy(Bitmap.Config.ARGB_8888, false).also { scaled.recycle() }
        }
    }

    private fun calculateInSampleSize(width: Int, height: Int, maxDim: Int): Int {
        var inSampleSize = 1
        var longest = max(width, height)
        while (longest / 2 >= maxDim) {
            inSampleSize *= 2
            longest /= 2
        }
        return inSampleSize
    }

    private fun applyExifOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun hasSufficientForeground(mask: FloatArray, minCoverage: Float): Boolean {
        var count = 0
        for (value in mask) {
            if (value >= 0.5f) count++
        }
        return count.toFloat() / mask.size.toFloat() >= minCoverage
    }

    /**
     * Computes a kTileW : kTileH crop rectangle centered horizontally on the head
     * (top 25% of person mask) with ~8% headroom and ~115% person height,
     * strictly preserving aspect ratio without distortion.
     */
    fun computePassportCropRect(
        imageWidth: Int,
        imageHeight: Int,
        mask: FloatArray?,
    ): CropPlan {
        val targetAspect = kTileW.toFloat() / kTileH.toFloat()
        if (mask == null || mask.size != imageWidth * imageHeight) {
            return CropPlan(
                rect = computeCenteredAspectRect(imageWidth, imageHeight, targetAspect),
                usedFallbackCrop = true,
            )
        }

        var minX = imageWidth
        var minY = imageHeight
        var maxX = -1
        var maxY = -1
        var foregroundCount = 0

        for (y in 0 until imageHeight) {
            val rowOffset = y * imageWidth
            for (x in 0 until imageWidth) {
                if (mask[rowOffset + x] >= 0.5f) {
                    foregroundCount++
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }

        val coverage = foregroundCount.toFloat() / (imageWidth * imageHeight).toFloat()
        if (coverage < MIN_PERSON_COVERAGE || maxX <= minX || maxY <= minY) {
            return CropPlan(
                rect = computeCenteredAspectRect(imageWidth, imageHeight, targetAspect),
                usedFallbackCrop = true,
            )
        }

        val personHeight = (maxY - minY + 1).toFloat()
        val headZoneBottomY = min(maxY, minY + (personHeight * 0.25f).roundToInt())

        var headSumX = 0.0
        var headPixelCount = 0
        for (y in minY..headZoneBottomY) {
            val rowOffset = y * imageWidth
            for (x in minX..maxX) {
                if (mask[rowOffset + x] >= 0.5f) {
                    headSumX += x
                    headPixelCount++
                }
            }
        }

        val headCenterX =
            if (headPixelCount > 0) {
                (headSumX / headPixelCount).toFloat()
            } else {
                (minX + maxX) / 2f
            }

        var cropHeight = personHeight * 1.15f
        var cropWidth = cropHeight * targetAspect
        val desiredTop = minY - personHeight * 0.08f

        // Never exceed image bounds and never distort the kTileW : kTileH aspect ratio.
        if (cropWidth > imageWidth || cropHeight > imageHeight) {
            val fitScale = min(imageWidth / cropWidth, imageHeight / cropHeight)
            cropWidth *= fitScale
            cropHeight *= fitScale
        }

        val clampedLeft = (headCenterX - cropWidth / 2f).coerceIn(0f, max(0f, imageWidth - cropWidth))
        val clampedTop = desiredTop.coerceIn(0f, max(0f, imageHeight - cropHeight))

        return CropPlan(
            rect = RectF(clampedLeft, clampedTop, clampedLeft + cropWidth, clampedTop + cropHeight),
            usedFallbackCrop = false,
        )
    }

    private fun computeCenteredAspectRect(
        imageWidth: Int,
        imageHeight: Int,
        targetAspect: Float,
    ): RectF {
        val imageAspect = imageWidth.toFloat() / imageHeight.toFloat()
        val cropWidth: Float
        val cropHeight: Float
        if (imageAspect > targetAspect) {
            cropHeight = imageHeight.toFloat()
            cropWidth = cropHeight * targetAspect
        } else {
            cropWidth = imageWidth.toFloat()
            cropHeight = cropWidth / targetAspect
        }
        val left = (imageWidth - cropWidth) / 2f
        val top = (imageHeight - cropHeight) / 2f
        return RectF(left, top, left + cropWidth, top + cropHeight)
    }

    /**
     * Crops the source bitmap and resizes to exactly kInnerPhotoW x kInnerPhotoH (340 x 397 px),
     * along with its mask.
     */
    fun cropAndResizePortrait(
        source: Bitmap,
        mask: FloatArray?,
        cropRect: RectF,
        usedFallbackCrop: Boolean,
    ): ProcessedPortrait {
        val portraitBitmap =
            Bitmap.createBitmap(kInnerPhotoW, kInnerPhotoH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(portraitBitmap)
        val srcRect =
            Rect(
                cropRect.left.roundToInt().coerceIn(0, source.width - 1),
                cropRect.top.roundToInt().coerceIn(0, source.height - 1),
                cropRect.right.roundToInt().coerceIn(1, source.width),
                cropRect.bottom.roundToInt().coerceIn(1, source.height),
            )
        val dstRect = Rect(0, 0, kInnerPhotoW, kInnerPhotoH)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
        canvas.drawBitmap(source, srcRect, dstRect, paint)

        val pixels = IntArray(kInnerPhotoW * kInnerPhotoH)
        portraitBitmap.getPixels(pixels, 0, kInnerPhotoW, 0, 0, kInnerPhotoW, kInnerPhotoH)
        portraitBitmap.recycle()

        val croppedMask =
            if (!usedFallbackCrop && mask != null) {
                resampleMaskBilinear(
                    mask = mask,
                    srcWidth = source.width,
                    srcHeight = source.height,
                    srcRect = cropRect,
                    dstWidth = kInnerPhotoW,
                    dstHeight = kInnerPhotoH,
                )
            } else {
                null
            }

        return ProcessedPortrait(
            width = kInnerPhotoW,
            height = kInnerPhotoH,
            pixels = pixels,
            mask = croppedMask,
            usedFallbackCrop = usedFallbackCrop,
        )
    }

    internal fun resampleMaskBilinear(
        mask: FloatArray,
        srcWidth: Int,
        srcHeight: Int,
        srcRect: RectF,
        dstWidth: Int,
        dstHeight: Int,
    ): FloatArray {
        val result = FloatArray(dstWidth * dstHeight)
        val rectW = srcRect.width()
        val rectH = srcRect.height()

        for (dy in 0 until dstHeight) {
            val v = (dy + 0.5f) / dstHeight.toFloat()
            val sy = (srcRect.top + v * rectH - 0.5f).coerceIn(0f, (srcHeight - 1).toFloat())
            val y0 = sy.toInt()
            val y1 = min(y0 + 1, srcHeight - 1)
            val wy = sy - y0

            val rowOffset0 = y0 * srcWidth
            val rowOffset1 = y1 * srcWidth
            val dstRowOffset = dy * dstWidth

            for (dx in 0 until dstWidth) {
                val u = (dx + 0.5f) / dstWidth.toFloat()
                val sx = (srcRect.left + u * rectW - 0.5f).coerceIn(0f, (srcWidth - 1).toFloat())
                val x0 = sx.toInt()
                val x1 = min(x0 + 1, srcWidth - 1)
                val wx = sx - x0

                val c00 = mask[rowOffset0 + x0]
                val c10 = mask[rowOffset0 + x1]
                val c01 = mask[rowOffset1 + x0]
                val c11 = mask[rowOffset1 + x1]

                val top = c00 + (c10 - c00) * wx
                val bottom = c01 + (c11 - c01) * wx
                result[dstRowOffset + dx] = (top + (bottom - top) * wy).coerceIn(0f, 1f)
            }
        }
        return result
    }

    /**
     * Measures average luminance of person pixels only and applies gain toward 140
     * (clamped [0.9, 1.45]) with gentle highlight compression above 235.
     */
    fun applyAutoBrightness(pixels: IntArray, mask: FloatArray?): IntArray {
        var lumSum = 0.0
        var lumCount = 0

        for (i in pixels.indices) {
            val isPerson = mask == null || mask[i] >= 0.5f
            if (isPerson) {
                val argb = pixels[i]
                val r = (argb ushr 16) and 0xFF
                val g = (argb ushr 8) and 0xFF
                val b = argb and 0xFF
                lumSum += 0.299 * r + 0.587 * g + 0.114 * b
                lumCount++
            }
        }

        if (lumCount == 0) return pixels.copyOf()
        val avgLum = (lumSum / lumCount).toFloat()
        if (avgLum < 1f) return pixels.copyOf()

        val gain = (TARGET_LUMINANCE / avgLum).coerceIn(MIN_GAIN, MAX_GAIN)
        val output = IntArray(pixels.size)
        for (i in pixels.indices) {
            val argb = pixels[i]
            val a = (argb ushr 24) and 0xFF
            val r = compressHighlight(((argb ushr 16) and 0xFF) * gain)
            val g = compressHighlight(((argb ushr 8) and 0xFF) * gain)
            val b = compressHighlight((argb and 0xFF) * gain)
            output[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
        }
        return output
    }

    fun compressHighlight(value: Float): Int {
        val compressed =
            if (value <= HIGHLIGHT_KNEE) {
                value
            } else {
                val excess = value - HIGHLIGHT_KNEE
                val headroom = 255f - HIGHLIGHT_KNEE // 20f
                HIGHLIGHT_KNEE + headroom * (excess / (excess + headroom))
            }
        return compressed.roundToInt().coerceIn(0, 255)
    }

    fun smoothstepAlpha(confidence: Float): Float {
        val t = ((confidence - 0.3f) / 0.4f).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    /**
     * Composites the kInnerPhotoW x kInnerPhotoH portrait onto Original / White / Blue
     * using soft-edge smoothstep alpha between 0.3 and 0.7 mask confidence.
     */
    fun compositePortraitBitmap(
        portrait: ProcessedPortrait,
        background: BackgroundOption,
        autoBrightness: Boolean,
    ): Bitmap {
        val adjustedPixels =
            if (autoBrightness) {
                applyAutoBrightness(portrait.pixels, portrait.mask)
            } else {
                portrait.pixels
            }

        val mask = portrait.mask
        val finalPixels =
            if (background == BackgroundOption.ORIGINAL || mask == null) {
                adjustedPixels
            } else {
                val bgArgb =
                    when (background) {
                        BackgroundOption.WHITE -> WHITE_BG_COLOR
                        BackgroundOption.BLUE -> BLUE_BG_COLOR
                        BackgroundOption.ORIGINAL -> WHITE_BG_COLOR
                    }
                val bgR = (bgArgb ushr 16) and 0xFF
                val bgG = (bgArgb ushr 8) and 0xFF
                val bgB = bgArgb and 0xFF

                IntArray(adjustedPixels.size) { i ->
                    val alpha = smoothstepAlpha(mask[i])
                    val invAlpha = 1f - alpha
                    val fg = adjustedPixels[i]
                    val fgR = (fg ushr 16) and 0xFF
                    val fgG = (fg ushr 8) and 0xFF
                    val fgB = fg and 0xFF

                    val outR = (fgR * alpha + bgR * invAlpha).roundToInt().coerceIn(0, 255)
                    val outG = (fgG * alpha + bgG * invAlpha).roundToInt().coerceIn(0, 255)
                    val outB = (fgB * alpha + bgB * invAlpha).roundToInt().coerceIn(0, 255)
                    (0xFF shl 24) or (outR shl 16) or (outG shl 8) or outB
                }
            }

        return Bitmap.createBitmap(
            finalPixels,
            portrait.width,
            portrait.height,
            Bitmap.Config.ARGB_8888,
        )
    }

    /**
     * Builds a 1200 x 1800 px (4x6 inch at 300 DPI) white sheet with 12 photos (3x4 grid),
     * equal left/right (68px) and top/bottom (90px) margins, 22px/13px gaps, and a solid
     * BLACK (#000000) kBorder (4px) rectangular border drawn INSIDE each kTileW x kTileH tile.
     *
     * Optional printer alignment offsets (offsetX, offsetY) shift the printed grid;
     * saved gallery PNGs always use offsetX = 0, offsetY = 0.
     */
    fun renderFourBySixSheet(
        portraitBitmap: Bitmap,
        offsetX: Int = 0,
        offsetY: Int = 0,
    ): Bitmap {
        val sheet = Bitmap.createBitmap(kSheetW, kSheetH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(sheet)
        canvas.drawColor(WHITE_BG_COLOR)

        val borderFillPaint =
            Paint().apply {
                color = BORDER_BLACK_COLOR
                style = Paint.Style.FILL
                isAntiAlias = false
            }
        val photoPaint = Paint(Paint.FILTER_BITMAP_FLAG)

        for (row in 0 until kRows) {
            val tileTop = kMarginY + offsetY + row * (kTileH + kGapY)
            for (col in 0 until kCols) {
                val tileLeft = kMarginX + offsetX + col * (kTileW + kGapX)

                // Solid BLACK (#000000) border drawn strictly INSIDE the tile's kTileW x kTileH outer bounds
                canvas.drawRect(
                    tileLeft.toFloat(),
                    tileTop.toFloat(),
                    (tileLeft + kTileW).toFloat(),
                    (tileTop + kTileH).toFloat(),
                    borderFillPaint,
                )

                val photoRect =
                    Rect(
                        tileLeft + kBorder,
                        tileTop + kBorder,
                        tileLeft + kTileW - kBorder,
                        tileTop + kTileH - kBorder,
                    )
                canvas.drawBitmap(portraitBitmap, null, photoRect, photoPaint)
            }
        }

        return sheet
    }
}
