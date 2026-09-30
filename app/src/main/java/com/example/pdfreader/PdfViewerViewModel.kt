package com.example.pdfreader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PdfViewerViewModel(private val savedStateHandle: SavedStateHandle) : ViewModel() {

    companion object {
        private const val KEY_PDF_URI = "pdf_uri"
        private const val KEY_CURRENT_PAGE = "current_page"
    }

    val pdfUri: Uri?
        get() = savedStateHandle.get<String>(KEY_PDF_URI)?.let { Uri.parse(it) }

    var pageCount: Int by mutableIntStateOf(0)
        private set

    var fileName: String? by mutableStateOf(null)
        private set

    var loadError: String? by mutableStateOf(null)
        private set

    var currentPageIndex: Int
        get() = savedStateHandle.get<Int>(KEY_CURRENT_PAGE) ?: 0
        set(value) {
            savedStateHandle[KEY_CURRENT_PAGE] = value
        }

    val pageBitmapCache = mutableStateMapOf<Int, Bitmap>()

    fun loadPdf(context: Context, uri: Uri) {
        savedStateHandle[KEY_PDF_URI] = uri.toString()
        pageBitmapCache.clear()
        loadError = null
        currentPageIndex = 0

        viewModelScope.launch(Dispatchers.IO) {
            var resolvedName: String? = null
            var resolvedCount = 0
            var resolvedError: String? = null

            try {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    PdfRenderer(pfd).use { renderer ->
                        resolvedCount = renderer.pageCount
                        resolvedName = displayNameFor(context, uri)
                    }
                } ?: run {
                    resolvedError = "Couldn't open that file."
                }
            } catch (_: Exception) {
                // If URI permissions expired or file is no longer accessible, clear saved URI
                savedStateHandle.remove<String>(KEY_PDF_URI)
                resolvedError = "Could not access file. Please choose another PDF."
            }

            withContext(Dispatchers.Main) {
                if (resolvedError != null) {
                    fileName = null
                } else {
                    fileName = resolvedName
                }
                pageCount = resolvedCount
                loadError = resolvedError
            }
        }
    }

    private fun displayNameFor(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && nameIndex >= 0) cursor.getString(nameIndex) else uri.lastPathSegment
            } ?: uri.lastPathSegment
        } catch (_: Exception) {
            uri.lastPathSegment
        }
    }
}
