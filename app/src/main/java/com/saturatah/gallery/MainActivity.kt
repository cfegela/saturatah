package com.saturatah.gallery

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.saturatah.gallery.ui.GalleryScreen
import com.saturatah.gallery.ui.GalleryUiState
import com.saturatah.gallery.ui.GalleryViewModel
import com.saturatah.gallery.ui.PhotoDetailScreen
import com.saturatah.gallery.ui.theme.PureBlack
import com.saturatah.gallery.ui.theme.SaturatahTheme

class MainActivity : ComponentActivity() {

    private val viewModel: GalleryViewModel by viewModels()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.values.any { it }
        if (granted) {
            viewModel.onPermissionGranted()
        } else {
            viewModel.onPermissionDenied()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        checkAndRequestPermissions()

        setContent {
            SaturatahTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = PureBlack
                ) {
                    GalleryApp(
                        viewModel = viewModel,
                        onRequestPermission = { checkAndRequestPermissions() }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (hasPhotoPermission()) {
            viewModel.onPermissionGranted()
        }
    }

    private fun getRequiredPermissions(): Array<String> {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> {
                arrayOf(
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
                )
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
            }
            else -> {
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
    }

    private fun hasPhotoPermission(): Boolean {
        val permissions = getRequiredPermissions()
        return permissions.any {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun checkAndRequestPermissions() {
        if (hasPhotoPermission()) {
            viewModel.onPermissionGranted()
        } else {
            permissionLauncher.launch(getRequiredPermissions())
        }
    }
}

@Composable
fun GalleryApp(
    viewModel: GalleryViewModel,
    onRequestPermission: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val selectedPhotoIndex by viewModel.selectedPhotoIndex.collectAsState()

    AnimatedContent(
        targetState = selectedPhotoIndex,
        transitionSpec = {
            fadeIn() togetherWith fadeOut()
        },
        label = "ScreenTransition"
    ) { selectedIndex ->
        if (selectedIndex != null && uiState is GalleryUiState.Success) {
            val photos = (uiState as GalleryUiState.Success).photos
            PhotoDetailScreen(
                photos = photos,
                initialIndex = selectedIndex,
                onBack = { viewModel.clearSelection() },
                onPhotoDeleted = { photo -> viewModel.onPhotoDeleted(photo) }
            )
        } else {
            GalleryScreen(
                uiState = uiState,
                onRequestPermission = onRequestPermission,
                onRefresh = { viewModel.loadPhotos() },
                onPhotoClick = { index ->
                    viewModel.selectPhoto(index)
                }
            )
        }
    }
}
