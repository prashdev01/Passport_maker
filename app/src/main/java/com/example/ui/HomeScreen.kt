package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
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

@Composable
fun HomeScreen(
    errorMessage: String?,
    onTakePhotoClick: () -> Unit,
    onSelectGalleryClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(SurfaceBg)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 20.dp, vertical = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().widthIn(max = 600.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // Top title and offline privacy indicator
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = TakePhotoBlueContainer,
                    modifier = Modifier.size(84.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.Badge,
                            contentDescription = null,
                            tint = TakePhotoBlue,
                            modifier = Modifier.size(48.dp),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineLarge,
                    color = InkDark,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag("app_title"),
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = stringResource(R.string.home_subtitle),
                    style = MaterialTheme.typography.bodyLarge,
                    color = InkMuted,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(14.dp))

                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = CardSurface,
                    border = BorderStroke(1.dp, BorderSubtle),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = null,
                            tint = GalleryGreen,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.offline_privacy_badge),
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp),
                            color = InkDark,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            // Friendly error banner if present
            if (errorMessage != null) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = ErrorContainer,
                    border = BorderStroke(1.5.dp, ErrorContent.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth().testTag("home_error_banner"),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ErrorOutline,
                            contentDescription = null,
                            tint = ErrorContent,
                            modifier = Modifier.size(26.dp),
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = errorMessage,
                            style = MaterialTheme.typography.titleMedium,
                            color = ErrorContent,
                        )
                    }
                }
            }

            // Giant high-contrast full-width buttons
            Column(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Button(
                    onClick = onTakePhotoClick,
                    shape = RoundedCornerShape(24.dp),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = TakePhotoBlue,
                            contentColor = Color.White,
                        ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 84.dp)
                            .testTag("take_photo_button"),
                ) {
                    Icon(
                        imageVector = Icons.Filled.PhotoCamera,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Text(
                        text = stringResource(R.string.take_photo_button),
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 21.sp),
                    )
                }

                Button(
                    onClick = onSelectGalleryClick,
                    shape = RoundedCornerShape(24.dp),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = GalleryGreen,
                            contentColor = Color.White,
                        ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 84.dp)
                            .testTag("select_gallery_button"),
                ) {
                    Icon(
                        imageVector = Icons.Filled.PhotoLibrary,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Text(
                        text = stringResource(R.string.select_gallery_button),
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 21.sp),
                    )
                }
            }
        }
    }
}

@Composable
fun ProcessingScreen(modifier: Modifier = Modifier) {
    Surface(
        color = SurfaceBg,
        modifier = modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp).testTag("processing_overlay"),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            CircularProgressIndicator(
                color = TakePhotoBlue,
                strokeWidth = 6.dp,
                modifier = Modifier.size(72.dp),
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.processing_photo),
                style = MaterialTheme.typography.headlineMedium,
                color = InkDark,
                textAlign = TextAlign.Center,
            )
        }
    }
}
