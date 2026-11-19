package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.pipeline.BackgroundOption
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.CardSurface
import com.example.ui.theme.ErrorContainer
import com.example.ui.theme.ErrorContent
import com.example.ui.theme.GalleryGreen
import com.example.ui.theme.InkDark
import com.example.ui.theme.InkMuted
import com.example.ui.theme.PassportBlueSwatch
import com.example.ui.theme.PassportWhiteSwatch
import com.example.ui.theme.SavePrintRed
import com.example.ui.theme.SuccessContainer
import com.example.ui.theme.SuccessContent
import com.example.ui.theme.SurfaceBg
import com.example.ui.theme.TakePhotoBlue
import com.example.ui.theme.TakePhotoBlueContainer
import com.example.ui.theme.WarningContainer
import com.example.ui.theme.WarningContent
import com.example.viewmodel.PassportUiState

@Composable
fun SheetPreviewScreen(
    state: PassportUiState.SheetReady,
    onBackClick: () -> Unit,
    onPrinterAlignmentClick: () -> Unit,
    onCropClick: () -> Unit,
    onResetAutoCropClick: () -> Unit,
    onSelectBackground: (BackgroundOption) -> Unit,
    onToggleAutoBrightness: (Boolean) -> Unit,
    onSaveAndPrintClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBackClick)

    val sheetImageBitmap =
        remember(state.sheetBitmap) { state.sheetBitmap.asImageBitmap() }

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
            // Top navigation bar with big Back button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    onClick = onBackClick,
                    shape = RoundedCornerShape(24.dp),
                    color = CardSurface,
                    border = BorderStroke(1.5.dp, BorderSubtle),
                    modifier = Modifier.heightIn(min = 52.dp).testTag("back_button"),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back_button),
                            tint = InkDark,
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.back_button),
                            style = MaterialTheme.typography.titleMedium,
                            color = InkDark,
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Text(
                    text = stringResource(R.string.sheet_screen_title),
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 19.sp),
                    color = InkDark,
                    modifier = Modifier.weight(1f),
                )

                Spacer(modifier = Modifier.width(8.dp))

                Surface(
                    onClick = onPrinterAlignmentClick,
                    shape = RoundedCornerShape(24.dp),
                    color = CardSurface,
                    border = BorderStroke(1.5.dp, BorderSubtle),
                    modifier =
                        Modifier
                            .size(52.dp)
                            .testTag("printer_alignment_button"),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = stringResource(R.string.printer_alignment_cd),
                            tint = InkDark,
                            modifier = Modifier.size(26.dp),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Success, fallback notice, or error banner
            if (state.successMessage != null) {
                StatusBanner(
                    message = state.successMessage,
                    containerColor = SuccessContainer,
                    contentColor = SuccessContent,
                    icon = Icons.Filled.CheckCircle,
                    testTag = "sheet_success_banner",
                )
                Spacer(modifier = Modifier.height(8.dp))
            } else if (state.errorMessage != null) {
                StatusBanner(
                    message = state.errorMessage,
                    containerColor = ErrorContainer,
                    contentColor = ErrorContent,
                    icon = Icons.Filled.ErrorOutline,
                    testTag = "sheet_error_banner",
                )
                Spacer(modifier = Modifier.height(8.dp))
            } else if (state.noticeMessage != null) {
                StatusBanner(
                    message = state.noticeMessage,
                    containerColor = WarningContainer,
                    contentColor = WarningContent,
                    icon = Icons.Filled.Info,
                    testTag = "sheet_notice_banner",
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // 2:3 Aspect-Ratio 4x6 Sheet Preview with soft shadow
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.White,
                shadowElevation = 10.dp,
                border = BorderStroke(1.dp, BorderSubtle),
                modifier =
                    Modifier
                        .fillMaxWidth(0.54f)
                        .aspectRatio(2f / 3f)
                        .testTag("sheet_preview_image"),
            ) {
                Image(
                    bitmap = sheetImageBitmap,
                    contentDescription = stringResource(R.string.sheet_preview_description),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // CROP button (min height 56dp, scissors icon) + optional RESET TO AUTO CROP
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = onCropClick,
                    shape = RoundedCornerShape(24.dp),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = TakePhotoBlue,
                            contentColor = Color.White,
                        ),
                    modifier =
                        Modifier
                            .weight(1f)
                            .heightIn(min = 56.dp)
                            .testTag("crop_button"),
                ) {
                    Icon(
                        imageVector = Icons.Filled.ContentCut,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.crop_button),
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
                    )
                }

                if (state.isManualCrop) {
                    OutlinedButton(
                        onClick = onResetAutoCropClick,
                        shape = RoundedCornerShape(24.dp),
                        border = BorderStroke(1.5.dp, TakePhotoBlue),
                        modifier =
                            Modifier
                                .weight(1.3f)
                                .heightIn(min = 56.dp)
                                .testTag("reset_auto_crop_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = null,
                            tint = TakePhotoBlue,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.reset_auto_crop_button),
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp),
                            color = TakePhotoBlue,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 3 Big Background Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                BackgroundChip(
                    label = stringResource(R.string.bg_original),
                    selected = state.backgroundOption == BackgroundOption.ORIGINAL,
                    swatchColor = null,
                    onClick = { onSelectBackground(BackgroundOption.ORIGINAL) },
                    modifier = Modifier.weight(1f).testTag("bg_chip_original"),
                )
                BackgroundChip(
                    label = stringResource(R.string.bg_white),
                    selected = state.backgroundOption == BackgroundOption.WHITE,
                    swatchColor = PassportWhiteSwatch,
                    onClick = { onSelectBackground(BackgroundOption.WHITE) },
                    modifier = Modifier.weight(1f).testTag("bg_chip_white"),
                )
                BackgroundChip(
                    label = stringResource(R.string.bg_blue),
                    selected = state.backgroundOption == BackgroundOption.BLUE,
                    swatchColor = PassportBlueSwatch,
                    onClick = { onSelectBackground(BackgroundOption.BLUE) },
                    modifier = Modifier.weight(1f).testTag("bg_chip_blue"),
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Auto brightness toggle (large, ON by default)
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = CardSurface,
                border = BorderStroke(1.5.dp, BorderSubtle),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 64.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .clickable(
                            role = Role.Switch,
                            onClick = { onToggleAutoBrightness(!state.autoBrightnessEnabled) },
                        )
                        .testTag("auto_brightness_toggle"),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.WbSunny,
                            contentDescription = null,
                            tint = TakePhotoBlue,
                            modifier = Modifier.size(26.dp),
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.auto_brightness_label),
                            style = MaterialTheme.typography.titleMedium,
                            color = InkDark,
                        )
                    }

                    Switch(
                        checked = state.autoBrightnessEnabled,
                        onCheckedChange = onToggleAutoBrightness,
                        colors =
                            SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = GalleryGreen,
                                uncheckedThumbColor = Color.White,
                                uncheckedTrackColor = InkMuted,
                            ),
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Huge Red SAVE & PRINT button
            Button(
                onClick = onSaveAndPrintClick,
                enabled = !state.isSaving,
                shape = RoundedCornerShape(24.dp),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = SavePrintRed,
                        contentColor = Color.White,
                        disabledContainerColor = SavePrintRed.copy(alpha = 0.6f),
                        disabledContentColor = Color.White,
                    ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 68.dp)
                        .testTag("save_print_button"),
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(26.dp),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.saving_label),
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 21.sp),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.Print,
                        contentDescription = null,
                        modifier = Modifier.size(30.dp),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.save_and_print_button),
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 22.sp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun BackgroundChip(
    label: String,
    selected: Boolean,
    swatchColor: Color?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val containerColor = if (selected) TakePhotoBlueContainer else CardSurface
    val borderColor = if (selected) TakePhotoBlue else BorderSubtle
    val borderWidth = if (selected) 2.5.dp else 1.5.dp

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        color = containerColor,
        border = BorderStroke(borderWidth, borderColor),
        modifier = modifier.heightIn(min = 64.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (swatchColor != null) {
                Box(
                    modifier =
                        Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(swatchColor)
                            .border(1.5.dp, InkMuted, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (selected) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            tint = if (swatchColor == PassportWhiteSwatch) TakePhotoBlue else Color.White,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            } else {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = if (selected) TakePhotoBlue else InkMuted,
                    modifier = Modifier.size(22.dp),
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
                color = InkDark,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun StatusBanner(
    message: String,
    containerColor: Color,
    contentColor: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    testTag: String,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = containerColor,
        border = BorderStroke(1.dp, contentColor.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth().testTag(testTag),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
                color = contentColor,
            )
        }
    }
}
