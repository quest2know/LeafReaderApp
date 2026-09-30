package com.example.pdfreader

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * Top-level screen: lets the user pick a PDF (or opens one passed in via
 * an ACTION_VIEW intent) and displays it as a horizontally swipeable,
 * pinch-zoomable set of pages.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PdfViewerScreen(initialUri: Uri? = null) {
    val context = LocalContext.current

    var pdfUri by remember { mutableStateOf(initialUri) }
    var pageCount by remember { mutableStateOf(0) }
    var fileName by remember { mutableStateOf<String?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }

    // Cache rendered bitmaps per page index so re-visiting a page is instant
    // and swiping doesn't re-render from disk every time.
    val pageBitmapCache = remember { mutableStateMapOf<Int, Bitmap>() }

    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) {
                // Some providers (e.g. certain cloud apps) don't support
                // persistable permissions; the current session still works.
            }
            pageBitmapCache.clear()
            loadError = null
            pdfUri = uri
        }
    }

    LaunchedEffect(pdfUri) {
        val uri = pdfUri ?: return@LaunchedEffect
        fileName = displayNameFor(context, uri)
        try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    pageCount = renderer.pageCount
                }
            } ?: run { loadError = "Couldn't open that file." }
        } catch (_: Exception) {
            loadError = "This doesn't look like a readable PDF."
        }
    }

    Scaffold(
        topBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shadowElevation = 4.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = fileName ?: "PDF Reader",
                        style = MaterialTheme.typography.titleLarge
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when {
                pdfUri == null -> EmptyState { pickerLauncher.launch(arrayOf("application/pdf")) }

                loadError != null -> ErrorState(
                    message = loadError!!,
                    onPickAnother = { pickerLauncher.launch(arrayOf("application/pdf")) }
                )

                pageCount == 0 -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }

                else -> {
                    val pagerState = rememberPagerState(pageCount = { pageCount })
                    var isPageZoomed by remember { mutableStateOf(false) }

                    LaunchedEffect(pagerState.currentPage) {
                        isPageZoomed = false
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxSize(),
                            userScrollEnabled = !isPageZoomed
                        ) { pageIndex ->
                            PdfPage(
                                uri = pdfUri!!,
                                pageIndex = pageIndex,
                                cache = pageBitmapCache,
                                onZoomChanged = { zoomed ->
                                    if (pagerState.currentPage == pageIndex) {
                                        isPageZoomed = zoomed
                                    }
                                }
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Page ${pagerState.currentPage + 1} of $pageCount",
                            modifier = Modifier.padding(start = 16.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        TextButton(
                            onClick = { pickerLauncher.launch(arrayOf("application/pdf")) },
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Text("Open another")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(onPick: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "No PDF open",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            Button(onClick = onPick) {
                Text("Open a PDF")
            }
        }
    }
}

@Composable
private fun ErrorState(message: String, onPickAnother: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(message, modifier = Modifier.padding(bottom = 16.dp))
            Button(onClick = onPickAnother) { Text("Choose a different file") }
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
