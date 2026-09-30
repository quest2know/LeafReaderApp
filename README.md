# LeafReaderApp
An android app that works with pdfs. no ads or permissions needed.

A minimal, native PDF reader built with Kotlin + Jetpack Compose. It uses
Android's built-in `PdfRenderer` API, so there are **no third-party PDF
libraries** to worry about (no licensing, no size bloat).

## Features
- Pick any PDF from device storage (Storage Access Framework — no storage
  permission needed)
- Swipe left/right between pages (`HorizontalPager`)
- Pinch to zoom and drag to pan on each page
- "Open with" support — you can open a PDF from another app (e.g. a file
  manager or email attachment) and it'll launch straight into this app
- Page counter ("Page 3 of 12")

## Requirements
- Android Studio (Koala/2024.1 or newer recommended)
- Min SDK 24 (Android 7.0), target/compile SDK 34
- JDK 17 (bundled with recent Android Studio)

## How to open and run
1. Unzip this project.
2. In Android Studio: **File → Open** → select the unzipped `PdfReaderApp`
   folder.
3. Let Gradle sync (Android Studio will offer to create the Gradle wrapper
   automatically if it's missing — accept it, or run `gradle wrapper` once
   if you have Gradle installed locally).
4. Run on an emulator or physical device (▶ button, or `Shift+F10`).

## Project layout
```
app/src/main/java/com/example/pdfreader/
  MainActivity.kt        – entry point, handles "Open with" intents
  PdfViewerScreen.kt      – top-level screen: file picker, pager, page counter
  PdfPage.kt              – renders one page to a Bitmap + pinch-zoom/pan
```

## Known limitations / good next steps
- Each page is rendered fresh from the file the first time it's viewed
  (then cached in memory) — fine for typical documents, but very large
  PDFs (100+ MB) could be slow to page through. A next step would be a
  shared `PdfRenderer` instance guarded by a mutex, plus disk-caching of
  bitmaps.
- No text search, bookmarks, or annotation support yet.
- No app icon has been set — use Android Studio's **Image Asset** wizard
  (right-click `res` → New → Image Asset) to add one in a couple of clicks.
- No dark-mode-specific tuning — Compose Material3 defaults are used as-is.
