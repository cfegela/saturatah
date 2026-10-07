package com.saturatah.gallery.ui

import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.saturatah.gallery.model.Photo
import com.saturatah.gallery.ui.theme.PureBlack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "PhotoDetailScreen"

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PhotoDetailScreen(
    photos: List<Photo>,
    initialIndex: Int,
    onBack: () -> Unit,
    onPhotoDeleted: (Photo) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val hapticFeedback = LocalHapticFeedback.current

    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, (photos.size - 1).coerceAtLeast(0)),
        pageCount = { photos.size }
    )

    var isDeleting by remember { mutableStateOf(false) }
    val currentPhoto = photos.getOrNull(pagerState.currentPage)

    fun handleDelete() {
        val photo = currentPhoto ?: return
        if (isDeleting) return
        isDeleting = true
        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)

        coroutineScope.launch {
            val deleted = withContext(Dispatchers.IO) {
                var fileRemoved = false
                try {
                    if (!photo.filePath.isNullOrEmpty()) {
                        val file = File(photo.filePath)
                        if (file.exists()) {
                            fileRemoved = file.delete()
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Direct file deletion exception", e)
                }

                try {
                    val count = context.contentResolver.delete(photo.uri, null, null)
                    count > 0 || fileRemoved
                } catch (e: Exception) {
                    Log.e(TAG, "ContentResolver deletion exception", e)
                    fileRemoved
                }
            }

            if (deleted) {
                Toast.makeText(context, "Photo deleted", Toast.LENGTH_SHORT).show()
                onPhotoDeleted(photo)
            } else {
                Toast.makeText(context, "Failed to delete photo", Toast.LENGTH_SHORT).show()
            }
            isDeleting = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PureBlack)
    ) {
        // Horizontal swiping pager without zoom
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val photo = photos[page]

            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(photo.uri)
                        .crossfade(true)
                        .build(),
                    contentDescription = photo.displayName,
                    contentScale = ContentScale.Fit,
                    loading = {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Top navigation bar overlay: Back (left) and Delete (right)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(40.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }

            if (currentPhoto != null) {
                val viewConfiguration = LocalViewConfiguration.current
                val customViewConfiguration = remember(viewConfiguration) {
                    object : ViewConfiguration by viewConfiguration {
                        override val longPressTimeoutMillis: Long = 500L
                    }
                }

                CompositionLocalProvider(LocalViewConfiguration provides customViewConfiguration) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                            .clip(CircleShape)
                            .combinedClickable(
                                enabled = !isDeleting,
                                role = Role.Button,
                                onClickLabel = null,
                                onLongClickLabel = "Delete photo",
                                onClick = {},
                                onLongClick = { handleDelete() }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete photo",
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }
}
