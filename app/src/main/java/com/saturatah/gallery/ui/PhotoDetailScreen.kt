package com.saturatah.gallery.ui

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Telephony
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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import android.view.ViewGroup
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.saturatah.gallery.model.Photo
import com.saturatah.gallery.ui.theme.PureBlack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
    onEditPhoto: (Photo) -> Unit = {},
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
    var showShareMenu by remember { mutableStateOf(false) }
    val currentPhoto = photos.getOrNull(pagerState.currentPage)

    LaunchedEffect(pagerState.currentPage) {
        showShareMenu = false
    }

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
                Toast.makeText(context, if (photo.isVideo) "Video deleted" else "Photo deleted", Toast.LENGTH_SHORT).show()
                onPhotoDeleted(photo)
            } else {
                Toast.makeText(context, if (photo.isVideo) "Failed to delete video" else "Failed to delete photo", Toast.LENGTH_SHORT).show()
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

            if (photo.isVideo) {
                VideoPlayerItem(
                    photo = photo,
                    isCurrentPage = pagerState.currentPage == page
                )
            } else {
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
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box {
                        IconButton(
                            onClick = { showShareMenu = true },
                            enabled = !isDeleting,
                            modifier = Modifier
                                .size(40.dp)
                                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = if (currentPhoto.isVideo) "Share video" else "Share photo",
                                tint = Color.White
                            )
                        }

                        DropdownMenu(
                            expanded = showShareMenu,
                            onDismissRequest = { showShareMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Text") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Sms,
                                        contentDescription = null
                                    )
                                },
                                onClick = {
                                    showShareMenu = false
                                    shareViaText(context, currentPhoto)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Gmail") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Email,
                                        contentDescription = null
                                    )
                                },
                                onClick = {
                                    showShareMenu = false
                                    shareViaGmail(context, currentPhoto)
                                }
                            )
                        }
                    }

                    if (!currentPhoto.isVideo) {
                        IconButton(
                            onClick = { onEditPhoto(currentPhoto) },
                            enabled = !isDeleting,
                            modifier = Modifier
                                .size(40.dp)
                                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit photo",
                                tint = Color.White
                            )
                        }
                    }

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
                                    onLongClickLabel = if (currentPhoto.isVideo) "Delete video" else "Delete photo",
                                    onClick = {},
                                    onLongClick = { handleDelete() }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = if (currentPhoto.isVideo) "Delete video" else "Delete photo",
                                tint = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoPlayerItem(
    photo: Photo,
    isCurrentPage: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var isPlaying by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(true) }

    val exoPlayer = remember(photo.uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(ExoMediaItem.fromUri(photo.uri))
            prepare()
            repeatMode = Player.REPEAT_MODE_OFF
        }
    }

    DisposableEffect(lifecycleOwner, exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_ENDED) {
                    exoPlayer.seekTo(0)
                    exoPlayer.pause()
                    isPlaying = false
                    showControls = true
                }
            }
        }
        exoPlayer.addListener(listener)

        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                exoPlayer.pause()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            exoPlayer.removeListener(listener)
            lifecycleOwner.lifecycle.removeObserver(observer)
            exoPlayer.release()
        }
    }

    LaunchedEffect(isCurrentPage) {
        if (!isCurrentPage) {
            exoPlayer.pause()
            isPlaying = false
            showControls = true
        }
    }

    LaunchedEffect(isPlaying, showControls) {
        if (isPlaying && showControls) {
            delay(2500)
            showControls = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                showControls = !showControls
            },
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    setOnClickListener {
                        showControls = !showControls
                    }
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        if (isBuffering) {
            CircularProgressIndicator(
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        AnimatedVisibility(
            visible = showControls || !isPlaying,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            IconButton(
                onClick = {
                    if (isPlaying) {
                        exoPlayer.pause()
                    } else {
                        if (exoPlayer.playbackState == Player.STATE_ENDED) {
                            exoPlayer.seekTo(0)
                        }
                        exoPlayer.play()
                    }
                },
                modifier = Modifier
                    .size(72.dp)
                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Pause video" else "Play video",
                    tint = Color.White,
                    modifier = Modifier.size(40.dp)
                )
            }
        }
    }
}

private fun shareViaText(context: Context, photo: Photo) {
    val defaultSmsPackage = Telephony.Sms.getDefaultSmsPackage(context)
    val mimeType = if (photo.isVideo) "video/*" else "image/*"
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, photo.uri)
        clipData = ClipData.newRawUri("", photo.uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        if (!defaultSmsPackage.isNullOrEmpty()) {
            setPackage(defaultSmsPackage)
        }
    }

    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        Log.w(TAG, "Failed to start default SMS app: $defaultSmsPackage, falling back to general send", e)
        val fallbackIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, photo.uri)
            clipData = ClipData.newRawUri("", photo.uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            context.startActivity(fallbackIntent)
        } catch (e2: Exception) {
            Log.e(TAG, "No messaging app available", e2)
            Toast.makeText(context, "No messaging app available", Toast.LENGTH_SHORT).show()
        }
    }
}

private fun shareViaGmail(context: Context, photo: Photo) {
    val mimeType = if (photo.isVideo) "video/*" else "image/*"
    val gmailIntent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, photo.uri)
        clipData = ClipData.newRawUri("", photo.uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        setPackage("com.google.android.gm")
    }

    try {
        context.startActivity(gmailIntent)
    } catch (e: Exception) {
        Log.w(TAG, "Gmail app not found, falling back to default email client", e)
        val fallbackIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, photo.uri)
            clipData = ClipData.newRawUri("", photo.uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            selector = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:")
            }
        }
        try {
            context.startActivity(fallbackIntent)
        } catch (e2: Exception) {
            Log.e(TAG, "No email client available", e2)
            Toast.makeText(context, "No email app found", Toast.LENGTH_SHORT).show()
        }
    }
}

