package com.example

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.CropEditorScreen
import com.example.ui.HomeScreen
import com.example.ui.PrinterAlignmentScreen
import com.example.ui.ProcessingScreen
import com.example.ui.SheetPreviewScreen
import com.example.ui.theme.PassportPhotoTheme
import com.example.viewmodel.PassportPhotoViewModel
import com.example.viewmodel.PassportUiState
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PassportPhotoTheme {
                PassportPhotoMakerApp()
            }
        }
    }
}

@Composable
fun PassportPhotoMakerApp(
    viewModel: PassportPhotoViewModel = viewModel(),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    LaunchedEffect(Unit) {
        viewModel.loadSavedAlignment(context)
    }

    val takePictureLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.TakePicture(),
        ) { success ->
            val capturedUri = pendingCameraUri
            if (success && capturedUri != null) {
                viewModel.processPhotoUri(context, capturedUri)
            }
        }

    val photoPickerLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.PickVisualMedia(),
        ) { uri ->
            if (uri != null) {
                viewModel.processPhotoUri(context, uri)
            }
        }

    Crossfade(targetState = uiState, label = "screen_transition") { state ->
        when (state) {
            is PassportUiState.Home -> {
                HomeScreen(
                    errorMessage = state.errorMessage,
                    onTakePhotoClick = {
                        try {
                            val uri = createTempPhotoUri(context)
                            pendingCameraUri = uri
                            takePictureLauncher.launch(uri)
                        } catch (_: Exception) {
                            viewModel.showHomeError(
                                context.getString(R.string.error_camera_unavailable),
                            )
                        }
                    },
                    onSelectGalleryClick = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
                )
            }

            is PassportUiState.Processing -> {
                ProcessingScreen()
            }

            is PassportUiState.SheetReady -> {
                SheetPreviewScreen(
                    state = state,
                    onBackClick = { viewModel.returnHome(context) },
                    onPrinterAlignmentClick = { viewModel.openPrinterAlignment() },
                    onCropClick = { viewModel.openCropEditor() },
                    onResetAutoCropClick = { viewModel.resetToAutoCrop() },
                    onSelectBackground = { viewModel.selectBackground(it) },
                    onToggleAutoBrightness = { viewModel.toggleAutoBrightness(it) },
                    onSaveAndPrintClick = { viewModel.saveAndPrint(context) },
                )
            }

            is PassportUiState.Cropping -> {
                CropEditorScreen(
                    originalTempFilePath = state.previousSheet.originalTempFilePath,
                    onCancel = { viewModel.cancelCropEditor() },
                    onDone = { rotationDegrees, cropRect ->
                        viewModel.applyManualCrop(
                            context = context,
                            rotationDegrees = rotationDegrees,
                            cropRectInRotatedBitmap = cropRect,
                        )
                    },
                )
            }

            is PassportUiState.PrinterAlignment -> {
                PrinterAlignmentScreen(
                    state = state,
                    onBackClick = { viewModel.cancelPrinterAlignment() },
                    onNudge = { dx, dy -> viewModel.nudgePrinterAlignment(dx, dy) },
                    onPrintTestPageClick = { viewModel.printAlignmentTestPage(context) },
                    onResetClick = { viewModel.resetDraftAlignment() },
                    onSaveClick = { viewModel.savePrinterAlignment(context) },
                )
            }
        }
    }
}

private fun createTempPhotoUri(context: Context): Uri {
    val dir = File(context.cacheDir, "camera_photos")
    if (!dir.exists()) {
        dir.mkdirs()
    }
    val photoFile = File.createTempFile("capture_", ".jpg", dir)
    return FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        photoFile,
    )
}
