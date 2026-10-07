package com.saturatah.gallery.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.saturatah.gallery.data.PhotoRepository
import com.saturatah.gallery.model.Photo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface GalleryUiState {
    object Loading : GalleryUiState
    object PermissionRequired : GalleryUiState
    object Empty : GalleryUiState
    data class Success(val photos: List<Photo>) : GalleryUiState
}

class GalleryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PhotoRepository(application)

    private val _uiState = MutableStateFlow<GalleryUiState>(GalleryUiState.Loading)
    val uiState: StateFlow<GalleryUiState> = _uiState.asStateFlow()

    private val _selectedPhotoIndex = MutableStateFlow<Int?>(null)
    val selectedPhotoIndex: StateFlow<Int?> = _selectedPhotoIndex.asStateFlow()

    private val _editingPhoto = MutableStateFlow<Photo?>(null)
    val editingPhoto: StateFlow<Photo?> = _editingPhoto.asStateFlow()

    fun onPermissionGranted() {
        loadPhotos()
    }

    fun onPermissionDenied() {
        _uiState.value = GalleryUiState.PermissionRequired
    }

    fun loadPhotos() {
        viewModelScope.launch {
            _uiState.value = GalleryUiState.Loading
            val photos = repository.loadPhotos()
            if (photos.isEmpty()) {
                _uiState.value = GalleryUiState.Empty
            } else {
                _uiState.value = GalleryUiState.Success(photos)
            }
        }
    }

    fun selectPhoto(index: Int) {
        _selectedPhotoIndex.value = index
    }

    fun clearSelection() {
        _selectedPhotoIndex.value = null
    }

    fun startEditing(photo: Photo) {
        _editingPhoto.value = photo
    }

    fun cancelEditing() {
        _editingPhoto.value = null
    }

    suspend fun loadPreviewBitmap(photo: Photo): android.graphics.Bitmap? {
        return repository.loadPreviewBitmap(photo.uri)
    }

    suspend fun saveEditedPhoto(
        photo: Photo,
        rotationDegrees: Int,
        cropLeft: Float,
        cropTop: Float,
        cropRight: Float,
        cropBottom: Float
    ): Boolean {
        val newUri = repository.saveCroppedAndRotatedPhoto(
            sourceUri = photo.uri,
            rotationDegrees = rotationDegrees,
            cropLeft = cropLeft,
            cropTop = cropTop,
            cropRight = cropRight,
            cropBottom = cropBottom
        )
        if (newUri != null) {
            repository.deletePhoto(photo)
            val photos = repository.loadPhotos()
            if (photos.isNotEmpty()) {
                _uiState.value = GalleryUiState.Success(photos)
                _selectedPhotoIndex.value = 0
            }
            _editingPhoto.value = null
            return true
        }
        return false
    }

    fun onPhotoDeleted(photo: Photo) {
        val current = _uiState.value
        if (current is GalleryUiState.Success) {
            val updated = current.photos.filter { it.id != photo.id }
            if (updated.isEmpty()) {
                _uiState.value = GalleryUiState.Empty
                _selectedPhotoIndex.value = null
            } else {
                _uiState.value = GalleryUiState.Success(updated)
                val currentIndex = _selectedPhotoIndex.value ?: 0
                if (currentIndex >= updated.size) {
                    _selectedPhotoIndex.value = updated.size - 1
                }
            }
        }
    }
}
