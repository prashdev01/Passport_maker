package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.export.PrinterAlignmentOffsets
import com.example.export.PrinterAlignmentStore
import com.example.pipeline.BackgroundOption
import com.example.pipeline.PassportImageProcessor
import com.example.pipeline.PersonSegmenter
import com.example.pipeline.kBorder
import com.example.pipeline.kSheetH
import com.example.pipeline.kSheetW
import com.example.pipeline.kTileH
import com.example.pipeline.kTileW
import com.example.ui.formatOffsetsReadout
import com.example.ui.theme.PassportPhotoTheme
import com.example.viewmodel.PassportPhotoViewModel
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w380dp-h780dp-xxhdpi")
class PassportPipelineRobolectricTest {

    @get:Rule val composeTestRule = createComposeRule()

    @Test
    fun `sheet constants margins 68 and 90 black border and print offset shift match specification`() =
        runTest {
            assertEquals(1200, kSheetW)
            assertEquals(1800, kSheetH)
            assertEquals(340, kTileW)
            assertEquals(395, kTileH)
            assertEquals(68, PassportImageProcessor.kMarginX)
            assertEquals(90, PassportImageProcessor.kMarginY)
            assertEquals(332, PassportImageProcessor.kInnerPhotoW)
            assertEquals(387, PassportImageProcessor.kInnerPhotoH)

            val context = ApplicationProvider.getApplicationContext<Context>()

            // Create synthetic 2000x1500 portrait photo (> 1600px to verify downscaling)
            val rawBitmap = Bitmap.createBitmap(2000, 1500, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(rawBitmap)
            canvas.drawColor(Color.rgb(40, 120, 40))

            val paint = android.graphics.Paint().apply { color = Color.rgb(90, 75, 65) }
            canvas.drawRect(950f, 300f, 1450f, 1350f, paint)

            val tempFile = File(context.cacheDir, "sample_portrait.png")
            FileOutputStream(tempFile).use { out ->
                rawBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            rawBitmap.recycle()

            val fakeSegmenter =
                object : PersonSegmenter {
                    override suspend fun segment(bitmap: Bitmap): FloatArray {
                        val mask = FloatArray(bitmap.width * bitmap.height)
                        val pxLeft = (950 * 0.8f).toInt()
                        val pxRight = (1450 * 0.8f).toInt()
                        val pxTop = (300 * 0.8f).toInt()
                        val pxBottom = (1350 * 0.8f).toInt()

                        for (y in 0 until bitmap.height) {
                            for (x in 0 until bitmap.width) {
                                mask[y * bitmap.width + x] =
                                    if (x in pxLeft..pxRight && y in pxTop..pxBottom) 0.95f else 0.05f
                            }
                        }
                        return mask
                    }
                }

            val initial =
                PassportImageProcessor.processPhotoUriWithTempOriginal(
                    context = context,
                    uri = Uri.fromFile(tempFile),
                    segmenter = fakeSegmenter,
                )

            val portrait = initial.autoPortrait
            assertFalse(portrait.usedFallbackCrop)
            assertEquals(332, portrait.width)
            assertEquals(387, portrait.height)

            val bluePortrait =
                PassportImageProcessor.compositePortraitBitmap(
                    portrait = portrait,
                    background = BackgroundOption.BLUE,
                    autoBrightness = true,
                )
            assertEquals(Color.rgb(0x1E, 0x90, 0xFF), bluePortrait.getPixel(2, 2))

            // Gallery PNG sheet (offset = 0)
            val sheet = PassportImageProcessor.renderFourBySixSheet(bluePortrait)
            assertEquals(1200, sheet.width)
            assertEquals(1800, sheet.height)

            // Safe top (90px) and bottom (90px+) margins must be pure white
            assertEquals(Color.WHITE, sheet.getPixel(600, 0))
            assertEquals(Color.WHITE, sheet.getPixel(600, 89))
            assertEquals(Color.WHITE, sheet.getPixel(600, 1709))
            assertEquals(Color.WHITE, sheet.getPixel(600, 1799))

            // Safe left (68px) and right (68px) margins must be pure white
            assertEquals(Color.WHITE, sheet.getPixel(0, 500))
            assertEquals(Color.WHITE, sheet.getPixel(67, 500))
            assertEquals(Color.WHITE, sheet.getPixel(1132, 500))
            assertEquals(Color.WHITE, sheet.getPixel(1199, 500))

            // Inter-tile gap (22px X, 13px Y) must be pure white
            assertEquals(Color.WHITE, sheet.getPixel(68 + kTileW + 5, 90 + 100))
            assertEquals(Color.WHITE, sheet.getPixel(68 + 100, 90 + kTileH + 5))

            // Solid BLACK (#000000) 4px border strictly INSIDE tile (68, 90) .. (68+340-1, 90+395-1)
            assertEquals(Color.BLACK, sheet.getPixel(68, 90))
            assertEquals(Color.BLACK, sheet.getPixel(68 + kBorder - 1, 90 + kBorder - 1))
            assertEquals(Color.BLACK, sheet.getPixel(68 + kTileW - 1, 90 + kTileH - 1))
            assertEquals(
                Color.rgb(0x1E, 0x90, 0xFF),
                sheet.getPixel(68 + kBorder, 90 + kBorder),
            )

            // Print offset shift (+12 X moves right, -16 Y moves up)
            val shiftedPrintSheet =
                PassportImageProcessor.renderFourBySixSheet(
                    portraitBitmap = bluePortrait,
                    offsetX = 12,
                    offsetY = -16,
                )
            assertEquals(Color.BLACK, shiftedPrintSheet.getPixel(68 + 12, 90 - 16))
            assertEquals(Color.WHITE, shiftedPrintSheet.getPixel(68, 73))

            // Manual crop re-runs segmentation and produces 332x387 portrait
            var segmentCalls = 0
            val countingSegmenter =
                object : PersonSegmenter {
                    override suspend fun segment(bitmap: Bitmap): FloatArray {
                        segmentCalls++
                        return FloatArray(bitmap.width * bitmap.height) { idx ->
                            val x = idx % bitmap.width
                            if (x > bitmap.width / 4) 0.95f else 0.05f
                        }
                    }
                }
            val manualPortrait =
                PassportImageProcessor.processManualCrop(
                    originalTempFilePath = initial.originalTempFilePath,
                    rotationDegrees = 90,
                    cropRectInRotatedBitmap = RectF(100f, 100f, 100f + 340f, 100f + 395f),
                    segmenter = countingSegmenter,
                )
            assertEquals(1, segmentCalls)
            assertEquals(332, manualPortrait.width)
            assertEquals(387, manualPortrait.height)
            assertFalse(manualPortrait.usedFallbackCrop)

            bluePortrait.recycle()
            sheet.recycle()
            shiftedPrintSheet.recycle()
            File(initial.originalTempFilePath).delete()
            tempFile.delete()
        }

    @Test
    fun `printer alignment store persists offsets and clamps to plus minus 80 px`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = PrinterAlignmentStore(context)
        store.save(PrinterAlignmentOffsets(offsetX = -4, offsetY = 12))

        // Reload via fresh PrinterAlignmentStore instance to simulate app restart
        val reloaded = PrinterAlignmentStore(context).load()
        assertEquals(-4, reloaded.offsetX)
        assertEquals(12, reloaded.offsetY)
        assertEquals("Up/Down: +12   Left/Right: -4", formatOffsetsReadout(reloaded.offsetX, reloaded.offsetY))

        // Clamping beyond +/-80px
        store.save(PrinterAlignmentOffsets(offsetX = 120, offsetY = -120))
        val clamped = PrinterAlignmentStore(context).load()
        assertEquals(80, clamped.offsetX)
        assertEquals(-80, clamped.offsetY)

        // Reset clean state
        store.save(PrinterAlignmentOffsets(0, 0))
    }

    @Test
    fun `auto crop ratio matches kTileW to kTileH and highlight compression caps below 255`() {
        val cropPlan =
            PassportImageProcessor.computePassportCropRect(
                imageWidth = 800,
                imageHeight = 1200,
                mask = null,
            )
        assertTrue(cropPlan.usedFallbackCrop)
        val aspect = cropPlan.rect.width() / cropPlan.rect.height()
        assertTrue(abs(aspect - (kTileW.toFloat() / kTileH.toFloat())) < 0.002f)

        assertEquals(0f, PassportImageProcessor.smoothstepAlpha(0.2f), 0.001f)
        assertEquals(0.5f, PassportImageProcessor.smoothstepAlpha(0.5f), 0.001f)
        assertEquals(1f, PassportImageProcessor.smoothstepAlpha(0.8f), 0.001f)

        val compressed = PassportImageProcessor.compressHighlight(270f)
        assertTrue(compressed in 236..254)
    }

    @Test
    fun `cuj sheet preview printer alignment calibration crop reset and back`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        PrinterAlignmentStore(context).save(PrinterAlignmentOffsets(0, 0))

        val tempFile = File(context.cacheDir, "cuj_portrait.png")
        val bmp = Bitmap.createBitmap(600, 800, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.rgb(120, 110, 100))
        FileOutputStream(tempFile).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()

        val fakeSegmenter =
            object : PersonSegmenter {
                override suspend fun segment(bitmap: Bitmap): FloatArray {
                    return FloatArray(bitmap.width * bitmap.height) { idx ->
                        val x = idx % bitmap.width
                        val y = idx / bitmap.width
                        if (x in (bitmap.width / 5)..(bitmap.width * 4 / 5) &&
                            y in (bitmap.height / 8)..(bitmap.height * 7 / 8)
                        ) {
                            0.9f
                        } else {
                            0.1f
                        }
                    }
                }
            }

        val viewModel = PassportPhotoViewModel(segmenter = fakeSegmenter)

        composeTestRule.setContent {
            PassportPhotoTheme {
                PassportPhotoMakerApp(viewModel = viewModel)
            }
        }

        // Home screen
        composeTestRule.onNodeWithTag("take_photo_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("select_gallery_button").assertIsDisplayed()

        // Process sample portrait -> Sheet Preview
        viewModel.processPhotoUri(context, Uri.fromFile(tempFile))
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodes(hasTestTag("printer_alignment_button"))
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.waitForIdle()

        // Open Printer Alignment via gear icon
        composeTestRule.onNodeWithTag("printer_alignment_button").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodes(hasTestTag("alignment_offset_text"))
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.waitForIdle()

        // Press UP twice (Y = -8) and RIGHT once (X = +4)
        composeTestRule.onNodeWithTag("align_up_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("align_up_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("align_right_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("alignment_offset_text")
            .performScrollTo()
            .assertTextEquals("Up/Down: -8   Left/Right: +4")

        // Tap SAVE -> persists to SharedPreferences and returns to Sheet Preview
        composeTestRule.onNodeWithTag("alignment_save_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        val saved = PrinterAlignmentStore(context).load()
        assertEquals(4, saved.offsetX)
        assertEquals(-8, saved.offsetY)

        // Verify Manual CROP and RESET TO AUTO CROP still work
        composeTestRule.onNodeWithTag("crop_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("crop_done_button").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodes(hasTestTag("reset_auto_crop_button"))
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("reset_auto_crop_button").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodes(hasTestTag("reset_auto_crop_button"))
                .fetchSemanticsNodes().isEmpty()
        }

        // Back to Home
        composeTestRule.onNodeWithTag("back_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("take_photo_button").assertIsDisplayed()

        tempFile.delete()
        PrinterAlignmentStore(context).save(PrinterAlignmentOffsets(0, 0))
    }
}
