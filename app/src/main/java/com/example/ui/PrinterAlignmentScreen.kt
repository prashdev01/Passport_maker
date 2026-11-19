package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.export.PrinterAlignmentOffsets
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.CardSurface
import com.example.ui.theme.ErrorContainer
import com.example.ui.theme.ErrorContent
import com.example.ui.theme.GalleryGreen
import com.example.ui.theme.InkDark
import com.example.ui.theme.InkMuted
import com.example.ui.theme.SurfaceBg
import com.example.ui.theme.TakePhotoBlue
import com.example.ui.theme.TakePhotoBlueContainer
import com.example.viewmodel.PassportUiState

@Composable
fun PrinterAlignmentScreen(
    state: PassportUiState.PrinterAlignment,
    onBackClick: () -> Unit,
    onNudge: (dx: Int, dy: Int) -> Unit,
    onPrintTestPageClick: () -> Unit,
    onResetClick: () -> Unit,
    onSaveClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBackClick)

    val step = PrinterAlignmentOffsets.STEP_PX
    val previewImageBitmap =
        remember(state.previewBitmap) { state.previewBitmap.asImageBitmap() }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(SurfaceBg)
                .windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .widthIn(max = 600.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Top bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    onClick = onBackClick,
                    shape = RoundedCornerShape(24.dp),
                    color = CardSurface,
                    border = BorderStroke(1.5.dp, BorderSubtle),
                    modifier = Modifier.heightIn(min = 52.dp).testTag("alignment_back_button"),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back_button),
                            tint = InkDark,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Text(
                    text = stringResource(R.string.printer_alignment_title),
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp),
                    color = InkDark,
                    modifier = Modifier.weight(1f).testTag("printer_alignment_title"),
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (state.errorMessage != null) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = ErrorContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ErrorOutline,
                            contentDescription = null,
                            tint = ErrorContent,
                            modifier = Modifier.size(22.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = state.errorMessage,
                            style = MaterialTheme.typography.bodyLarge,
                            color = ErrorContent,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Live 2:3 print sheet preview shifting visibly with offsetX / offsetY
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color.White,
                shadowElevation = 8.dp,
                border = BorderStroke(1.dp, BorderSubtle),
                modifier =
                    Modifier
                        .fillMaxWidth(0.42f)
                        .aspectRatio(2f / 3f)
                        .testTag("alignment_preview_image"),
            ) {
                Image(
                    bitmap = previewImageBitmap,
                    contentDescription = stringResource(R.string.sheet_preview_description),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Current offsets readout: "Up/Down: +12   Left/Right: -4"
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = TakePhotoBlueContainer,
                border = BorderStroke(1.5.dp, TakePhotoBlue.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text =
                        formatOffsetsReadout(
                            offsetX = state.draftOffsets.offsetX,
                            offsetY = state.draftOffsets.offsetY,
                        ),
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 19.sp),
                    color = InkDark,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 12.dp)
                            .testTag("alignment_offset_text"),
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // One-line shopkeeper hint
            Text(
                text = stringResource(R.string.alignment_hint),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp),
                color = InkMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 4 very large arrow buttons (UP, DOWN, LEFT, RIGHT)
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AlignmentArrowButton(
                    label = stringResource(R.string.alignment_up),
                    icon = Icons.Filled.KeyboardArrowUp,
                    onClick = { onNudge(0, -step) },
                    modifier = Modifier.fillMaxWidth(0.54f).testTag("align_up_button"),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    AlignmentArrowButton(
                        label = stringResource(R.string.alignment_left),
                        icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        onClick = { onNudge(-step, 0) },
                        modifier = Modifier.weight(1f).testTag("align_left_button"),
                    )
                    AlignmentArrowButton(
                        label = stringResource(R.string.alignment_right),
                        icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        onClick = { onNudge(step, 0) },
                        modifier = Modifier.weight(1f).testTag("align_right_button"),
                    )
                }

                AlignmentArrowButton(
                    label = stringResource(R.string.alignment_down),
                    icon = Icons.Filled.KeyboardArrowDown,
                    onClick = { onNudge(0, step) },
                    modifier = Modifier.fillMaxWidth(0.54f).testTag("align_down_button"),
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // PRINT TEST PAGE button
            Button(
                onClick = onPrintTestPageClick,
                shape = RoundedCornerShape(24.dp),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = InkDark,
                        contentColor = Color.White,
                    ),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 60.dp)
                        .testTag("print_test_page_button"),
            ) {
                Icon(
                    imageVector = Icons.Filled.Print,
                    contentDescription = null,
                    modifier = Modifier.size(26.dp),
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.print_test_page_button),
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 19.sp),
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // RESET & SAVE buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onResetClick,
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(2.dp, BorderSubtle),
                    modifier =
                        Modifier
                            .weight(1f)
                            .heightIn(min = 60.dp)
                            .testTag("alignment_reset_button"),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = null,
                        tint = InkDark,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.alignment_reset_button),
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
                        color = InkDark,
                    )
                }

                Button(
                    onClick = onSaveClick,
                    shape = RoundedCornerShape(24.dp),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = GalleryGreen,
                            contentColor = Color.White,
                        ),
                    modifier =
                        Modifier
                            .weight(1.4f)
                            .heightIn(min = 60.dp)
                            .testTag("alignment_save_button"),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.alignment_save_button),
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 20.sp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun AlignmentArrowButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        colors =
            ButtonDefaults.buttonColors(
                containerColor = TakePhotoBlue,
                contentColor = Color.White,
            ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 3.dp),
        modifier = modifier.heightIn(min = 62.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(30.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 19.sp),
        )
    }
}

fun formatOffsetsReadout(offsetX: Int, offsetY: Int): String {
    fun fmt(value: Int): String = if (value > 0) "+$value" else value.toString()
    return "Up/Down: ${fmt(offsetY)}   Left/Right: ${fmt(offsetX)}"
}
