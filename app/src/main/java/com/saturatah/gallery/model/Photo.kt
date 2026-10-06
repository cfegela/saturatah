package com.saturatah.gallery.model

import android.net.Uri

data class Photo(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val dateAdded: Long,
    val size: Long,
    val width: Int = 0,
    val height: Int = 0,
    val filePath: String? = null
)
