package com.example.pdfreader

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext

/**
 * Renders one page of the open PDF as a bitmap (cached by page index so it's
 * only rendered once per session) and lets the user pinch-zoom / drag around it.
 */
@Composable
fun PdfPage(
    uri: Uri,
    pageIndex: Int,
    cache: SnapshotStateMap<Int, Bitmap>,
    onZoomChanged: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    var bitmap by remember(pageIndex) { mutableStateOf(cache[pageIndex]) }

    var scale by remember(pageIndex) { mutableStateOf(1f) }
    var offsetX by remember(pageIndex) { mutableStateOf(0f) }
    var offsetY by remember(pageIndex) { mutableStateOf(0f) }

    LaunchedEffect(scale) {
        onZoomChanged(scale > 1.01f)
    }

    LaunchedEffect(pageIndex, uri) {
        if (bitmap == null) {
            val rendered = renderPage(context, uri, pageIndex)
            if (rendered != null) {
                cache[pageIndex] = rendered
                bitmap = rendered
            }
        }
    }

    val currentBitmap = bitmap
    if (currentBitmap != null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(pageIndex) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        var pastTouchSlop = false
                        val touchSlop = viewConfiguration.touchSlop
                        var zoomAcc = 1f
                        var panAcc = Offset.Zero

                        do {
                            val event = awaitPointerEvent()
                            val pointers = event.changes
                            val pointerCount = pointers.size

                            if (scale <= 1f && pointerCount < 2) {
                                continue
                            }

                            val zoomChange = event.calculateZoom()
                            val panChange = event.calculatePan()

                            if (!pastTouchSlop) {
                                zoomAcc *= zoomChange
                                panAcc += panChange
                                if (zoomAcc != 1f || panAcc.getDistance() > touchSlop || pointerCount >= 2) {
                                    pastTouchSlop = true
                                }
                            }

                            if (pastTouchSlop || scale > 1f) {
                                val newScale = (scale * zoomChange).coerceIn(1f, 5f)
                                scale = newScale
                                if (newScale <= 1f) {
                                    offsetX = 0f
                                    offsetY = 0f
                                } else {
                                    offsetX += panChange.x
                                    offsetY += panChange.y
                                }
                                pointers.forEach { it.consume() }
                            }
                        } while (pointers.any { it.pressed })
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Image(
                bitmap = currentBitmap.asImageBitmap(),
                contentDescription = "Page ${pageIndex + 1}",
                modifier = Modifier.graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offsetX,
                    translationY = offsetY
                )
            )
        }
    } else {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    }
}

/**
 * Opens a fresh PdfRenderer for the given page. PdfRenderer/ParcelFileDescriptor
 * are not safe to share across pages concurrently, so each page render opens
 * and closes its own descriptor. This is cheap enough for typical documents;
 * for very large PDFs a single shared renderer with a mutex would be a good
 * next optimization.
 */
private fun renderPage(context: android.content.Context, uri: Uri, pageIndex: Int): Bitmap? {
    return try {
        context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                if (pageIndex >= renderer.pageCount) return null
                renderer.openPage(pageIndex).use { page ->
                    // Render at 2x for reasonable sharpness when zoomed in a bit.
                    val bmp = Bitmap.createBitmap(
                        page.width * 2,
                        page.height * 2,
                        Bitmap.Config.ARGB_8888
                    )
                    bmp.eraseColor(Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bmp
                }
            }
        }
    } catch (_: Exception) {
        null
    }
}
