package com.example.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.export.PrinterAlignmentOffsets
import com.example.export.PrinterAlignmentStore
import com.example.export.SheetExporter
import com.example.pipeline.BackgroundOption
import com.example.pipeline.MlKitPersonSegmenter
import com.example.pipeline.PassportImageProcessor
import com.example.pipeline.PersonSegmenter
import com.example.pipeline.ProcessedPortrait
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface PassportUiState {
    data class Home(val errorMessage: String? = null) : PassportUiState

    data object Processing : PassportUiState

    data class SheetReady(
        val originalTempFilePath: String,
        val autoPortrait: ProcessedPortrait,
        val portrait: ProcessedPortrait,
        val isManualCrop: Boolean = false,
        val backgroundOption: BackgroundOption = BackgroundOption.WHITE,
        val autoBrightnessEnabled: Boolean = true,
        val sheetBitmap: Bitmap,
        val savedOffsets: PrinterAlignmentOffsets = PrinterAlignmentOffsets(),
        val isSaving: Boolean = false,
        val noticeMessage: String? = null,
        val successMessage: String? = null,
        val errorMessage: String? = null,
    ) : PassportUiState

    data class Cropping(
        val previousSheet: SheetReady,
    ) : PassportUiState

    data class PrinterAlignment(
        val previousSheet: SheetReady,
        val draftOffsets: PrinterAlignmentOffsets,
        val previewBitmap: Bitmap,
        val errorMessage: String? = null,
    ) : PassportUiState
}

class PassportPhotoViewModel(
    private val segmenter: PersonSegmenter = MlKitPersonSegmenter(),
) : ViewModel() {

    private val _uiState = MutableStateFlow<PassportUiState>(PassportUiState.Home())
    val uiState: StateFlow<PassportUiState> = _uiState.asStateFlow()

    private var cachedOffsets: PrinterAlignmentOffsets = PrinterAlignmentOffsets()
    private var alignmentRenderJob: Job? = null
    private var sheetRenderJob: Job? = null

    fun loadSavedAlignment(context: Context) {
        cachedOffsets = PrinterAlignmentStore(context).load()
        val current = _uiState.value
        if (current is PassportUiState.SheetReady && current.savedOffsets != cachedOffsets) {
            _uiState.value = current.copy(savedOffsets = cachedOffsets)
        }
    }

    fun processPhotoUri(context: Context, uri: Uri) {
        val appContext = context.applicationContext
        cachedOffsets = PrinterAlignmentStore(appContext).load()
        _uiState.value = PassportUiState.Processing

        viewModelScope.launch {
            try {
                val result =
                    withContext(Dispatchers.Default) {
                        PassportImageProcessor.processPhotoUriWithTempOriginal(
                            context = appContext,
                            uri = uri,
                            segmenter = segmenter,
                        )
                    }

                val defaultBg = BackgroundOption.WHITE
                val defaultBrightness = true
                val sheetBitmap =
                    withContext(Dispatchers.Default) {
                        renderSheet(result.autoPortrait, defaultBg, defaultBrightness, 0, 0)
                    }

                val fallbackNotice =
                    if (result.autoPortrait.usedFallbackCrop) {
                        appContext.getString(R.string.fallback_crop_notice)
                    } else {
                        null
                    }

                _uiState.value =
                    PassportUiState.SheetReady(
                        originalTempFilePath = result.originalTempFilePath,
                        autoPortrait = result.autoPortrait,
                        portrait = result.autoPortrait,
                        isManualCrop = false,
                        backgroundOption = defaultBg,
                        autoBrightnessEnabled = defaultBrightness,
                        sheetBitmap = sheetBitmap,
                        savedOffsets = cachedOffsets,
                        noticeMessage = fallbackNotice,
                    )
            } catch (_: Exception) {
                _uiState.value =
                    PassportUiState.Home(
                        errorMessage = appContext.getString(R.string.error_no_face_or_image),
                    )
            }
        }
    }

    fun openCropEditor() {
        val current = _uiState.value as? PassportUiState.SheetReady ?: return
        _uiState.value = PassportUiState.Cropping(previousSheet = current)
    }

    fun cancelCropEditor() {
        val cropping = _uiState.value as? PassportUiState.Cropping ?: return
        _uiState.value = cropping.previousSheet
    }

    fun applyManualCrop(
        context: Context,
        rotationDegrees: Int,
        cropRectInRotatedBitmap: RectF,
    ) {
        val cropping = _uiState.value as? PassportUiState.Cropping ?: return
        val previous = cropping.previousSheet
        val appContext = context.applicationContext
        _uiState.value = PassportUiState.Processing

        viewModelScope.launch {
            try {
                val manualPortrait =
                    withContext(Dispatchers.Default) {
                        PassportImageProcessor.processManualCrop(
                            originalTempFilePath = previous.originalTempFilePath,
                            rotationDegrees = rotationDegrees,
                            cropRectInRotatedBitmap = cropRectInRotatedBitmap,
                            segmenter = segmenter,
                        )
                    }

                val newSheet =
                    withContext(Dispatchers.Default) {
                        renderSheet(
                            portrait = manualPortrait,
                            backgroundOption = previous.backgroundOption,
                            autoBrightness = previous.autoBrightnessEnabled,
                            offsetX = 0,
                            offsetY = 0,
                        )
                    }

                val notice =
                    if (manualPortrait.usedFallbackCrop) {
                        appContext.getString(R.string.fallback_crop_notice)
                    } else {
                        null
                    }

                _uiState.value =
                    previous.copy(
                        portrait = manualPortrait,
                        isManualCrop = true,
                        sheetBitmap = newSheet,
                        noticeMessage = notice,
                        successMessage = null,
                        errorMessage = null,
                    )
            } catch (_: Exception) {
                _uiState.value =
                    previous.copy(
                        errorMessage = appContext.getString(R.string.error_crop_failed),
                    )
            }
        }
    }

    fun resetToAutoCrop() {
        val current = _uiState.value as? PassportUiState.SheetReady ?: return
        if (!current.isManualCrop) return

        viewModelScope.launch {
            val newSheet =
                withContext(Dispatchers.Default) {
                    renderSheet(
                        portrait = current.autoPortrait,
                        backgroundOption = current.backgroundOption,
                        autoBrightness = current.autoBrightnessEnabled,
                        offsetX = 0,
                        offsetY = 0,
                    )
                }

            val latest = _uiState.value as? PassportUiState.SheetReady ?: return@launch
            _uiState.value =
                latest.copy(
                    portrait = latest.autoPortrait,
                    isManualCrop = false,
                    sheetBitmap = newSheet,
                    successMessage = null,
                    errorMessage = null,
                )
        }
    }

    fun openPrinterAlignment() {
        val current = _uiState.value as? PassportUiState.SheetReady ?: return
        val offsets = current.savedOffsets
        viewModelScope.launch {
            val preview =
                withContext(Dispatchers.Default) {
                    renderSheet(
                        portrait = current.portrait,
                        backgroundOption = current.backgroundOption,
                        autoBrightness = current.autoBrightnessEnabled,
                        offsetX = offsets.offsetX,
                        offsetY = offsets.offsetY,
                    )
                }
            _uiState.value =
                PassportUiState.PrinterAlignment(
                    previousSheet = current,
                    draftOffsets = offsets,
                    previewBitmap = preview,
                )
        }
    }

    fun cancelPrinterAlignment() {
        val alignment = _uiState.value as? PassportUiState.PrinterAlignment ?: return
        _uiState.value = alignment.previousSheet
    }

    fun nudgePrinterAlignment(dx: Int, dy: Int) {
        val current = _uiState.value as? PassportUiState.PrinterAlignment ?: return
        val nextOffsets =
            PrinterAlignmentOffsets(
                offsetX = current.draftOffsets.offsetX + dx,
                offsetY = current.draftOffsets.offsetY + dy,
            ).clamped()
        if (nextOffsets == current.draftOffsets) return
        _uiState.value = current.copy(draftOffsets = nextOffsets, errorMessage = null)
        updateAlignmentPreview(current.previousSheet, nextOffsets)
    }

    fun resetDraftAlignment() {
        val current = _uiState.value as? PassportUiState.PrinterAlignment ?: return
        val zeroOffsets = PrinterAlignmentOffsets(0, 0)
        if (current.draftOffsets == zeroOffsets) return
        _uiState.value = current.copy(draftOffsets = zeroOffsets, errorMessage = null)
        updateAlignmentPreview(current.previousSheet, zeroOffsets)
    }

    private fun updateAlignmentPreview(
        previousSheet: PassportUiState.SheetReady,
        nextOffsets: PrinterAlignmentOffsets,
    ) {
        alignmentRenderJob?.cancel()
        alignmentRenderJob =
            viewModelScope.launch {
                val preview =
                    withContext(Dispatchers.Default) {
                        renderSheet(
                            portrait = previousSheet.portrait,
                            backgroundOption = previousSheet.backgroundOption,
                            autoBrightness = previousSheet.autoBrightnessEnabled,
                            offsetX = nextOffsets.offsetX,
                            offsetY = nextOffsets.offsetY,
                        )
                    }
                val latest = _uiState.value as? PassportUiState.PrinterAlignment ?: return@launch
                if (latest.draftOffsets == nextOffsets) {
                    _uiState.value = latest.copy(previewBitmap = preview)
                }
            }
    }

    fun savePrinterAlignment(context: Context) {
        val current = _uiState.value as? PassportUiState.PrinterAlignment ?: return
        val clamped = current.draftOffsets.clamped()
        PrinterAlignmentStore(context).save(clamped)
        cachedOffsets = clamped
        _uiState.value =
            current.previousSheet.copy(
                savedOffsets = clamped,
                successMessage = context.getString(R.string.success_alignment_saved),
                errorMessage = null,
            )
    }

    fun printAlignmentTestPage(activityContext: Context) {
        val current = _uiState.value as? PassportUiState.PrinterAlignment ?: return
        try {
            SheetExporter.printFourBySixSheet(
                context = activityContext,
                sheetBitmap = current.previewBitmap,
                jobName = activityContext.getString(R.string.print_job_name),
            )
        } catch (_: Exception) {
            _uiState.value =
                current.copy(errorMessage = activityContext.getString(R.string.error_print_failed))
        }
    }

    fun selectBackground(option: BackgroundOption) {
        val current = _uiState.value as? PassportUiState.SheetReady ?: return
        if (current.backgroundOption == option) return
        recomputeSheet(current, newBg = option, newAutoBrightness = current.autoBrightnessEnabled)
    }

    fun toggleAutoBrightness(enabled: Boolean) {
        val current = _uiState.value as? PassportUiState.SheetReady ?: return
        if (current.autoBrightnessEnabled == enabled) return
        recomputeSheet(current, newBg = current.backgroundOption, newAutoBrightness = enabled)
    }

    private fun recomputeSheet(
        current: PassportUiState.SheetReady,
        newBg: BackgroundOption,
        newAutoBrightness: Boolean,
    ) {
        _uiState.value =
            current.copy(
                backgroundOption = newBg,
                autoBrightnessEnabled = newAutoBrightness,
                successMessage = null,
                errorMessage = null,
            )
        sheetRenderJob?.cancel()
        sheetRenderJob =
            viewModelScope.launch {
                val newSheet =
                    withContext(Dispatchers.Default) {
                        renderSheet(current.portrait, newBg, newAutoBrightness, 0, 0)
                    }

                val latest = _uiState.value as? PassportUiState.SheetReady ?: return@launch
                if (latest.backgroundOption == newBg && latest.autoBrightnessEnabled == newAutoBrightness) {
                    _uiState.value = latest.copy(sheetBitmap = newSheet)
                }
            }
    }

    private fun renderSheet(
        portrait: ProcessedPortrait,
        backgroundOption: BackgroundOption,
        autoBrightness: Boolean,
        offsetX: Int,
        offsetY: Int,
    ): Bitmap {
        val composited =
            PassportImageProcessor.compositePortraitBitmap(
                portrait = portrait,
                background = backgroundOption,
                autoBrightness = autoBrightness,
            )
        val sheet =
            PassportImageProcessor.renderFourBySixSheet(
                portraitBitmap = composited,
                offsetX = offsetX,
                offsetY = offsetY,
            )
        composited.recycle()
        return sheet
    }

    fun saveAndPrint(activityContext: Context) {
        val current = _uiState.value as? PassportUiState.SheetReady ?: return
        if (current.isSaving) return

        _uiState.value = current.copy(isSaving = true, errorMessage = null, successMessage = null)
        val appContext = activityContext.applicationContext
        val offsets = current.savedOffsets

        viewModelScope.launch {
            try {
                // Gallery PNG ALWAYS uses offset = 0
                withContext(Dispatchers.IO) {
                    SheetExporter.saveSheetToGallery(appContext, current.sheetBitmap)
                }

                // Print PDF applies printer alignment offsets if non-zero
                val printBitmap =
                    if (offsets.offsetX == 0 && offsets.offsetY == 0) {
                        current.sheetBitmap
                    } else {
                        withContext(Dispatchers.Default) {
                            renderSheet(
                                portrait = current.portrait,
                                backgroundOption = current.backgroundOption,
                                autoBrightness = current.autoBrightnessEnabled,
                                offsetX = offsets.offsetX,
                                offsetY = offsets.offsetY,
                            )
                        }
                    }

                val latest = _uiState.value as? PassportUiState.SheetReady ?: return@launch
                _uiState.value =
                    latest.copy(
                        isSaving = false,
                        successMessage = activityContext.getString(R.string.success_saved_and_printing),
                    )

                SheetExporter.printFourBySixSheet(
                    context = activityContext,
                    sheetBitmap = printBitmap,
                    jobName = activityContext.getString(R.string.print_job_name),
                )
            } catch (_: Exception) {
                val latest = _uiState.value as? PassportUiState.SheetReady ?: return@launch
                _uiState.value =
                    latest.copy(
                        isSaving = false,
                        errorMessage = activityContext.getString(R.string.error_save_failed),
                    )
            }
        }
    }

    fun showHomeError(message: String) {
        _uiState.value = PassportUiState.Home(errorMessage = message)
    }

    fun returnHome(context: Context? = null) {
        context?.let { PassportImageProcessor.cleanupTempOriginals(it.applicationContext) }
        _uiState.value = PassportUiState.Home()
    }
}
