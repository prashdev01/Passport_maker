package com.example.export

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.print.pdf.PrintedPdfDocument
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object SheetExporter {

    /**
     * Saves the full 1200x1800 PNG sheet into the phone's Gallery (MediaStore Pictures).
     */
    fun saveSheetToGallery(context: Context, sheetBitmap: Bitmap): Uri {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = "passport_sheet_4x6_$timestamp.png"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values =
                ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(
                        MediaStore.Images.Media.RELATIVE_PATH,
                        "${Environment.DIRECTORY_PICTURES}/PassportPhotoMaker",
                    )
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }

            val resolver = context.contentResolver
            val uri =
                resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: throw IOException("Failed to create MediaStore entry.")

            try {
                resolver.openOutputStream(uri)?.use { out ->
                    if (!sheetBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                        throw IOException("Failed to encode PNG.")
                    }
                } ?: throw IOException("Failed to open output stream.")

                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                return uri
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                throw e
            }
        } else {
            val dir =
                File(
                    context.getExternalFilesDir(Environment.DIRECTORY_PICTURES),
                    "PassportPhotoMaker",
                )
            if (!dir.exists()) {
                dir.mkdirs()
            }
            val file = File(dir, fileName)
            FileOutputStream(file).use { out ->
                if (!sheetBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                    throw IOException("Failed to encode PNG.")
                }
            }
            return Uri.fromFile(file)
        }
    }

    /**
     * Launches the Android system print dialog sized to 4in x 6in with zero margins.
     */
    fun printFourBySixSheet(context: Context, sheetBitmap: Bitmap, jobName: String) {
        val printManager =
            context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
                ?: throw IllegalStateException("Print service is unavailable on this device.")

        val mediaSize4x6 = PrintAttributes.MediaSize("4x6_photo", "4 x 6 Photo", 4000, 6000)
        val printAttributes =
            PrintAttributes.Builder()
                .setMediaSize(mediaSize4x6)
                .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
                .build()

        val adapter = PhotoSheetPrintAdapter(context, sheetBitmap)
        printManager.print(jobName, adapter, printAttributes)
    }
}

private class PhotoSheetPrintAdapter(
    private val context: Context,
    private val sheetBitmap: Bitmap,
) : PrintDocumentAdapter() {

    private var pdfDocument: PrintedPdfDocument? = null

    override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes,
        cancellationSignal: CancellationSignal?,
        callback: LayoutResultCallback,
        extras: Bundle?,
    ) {
        pdfDocument = PrintedPdfDocument(context, newAttributes)

        if (cancellationSignal?.isCanceled == true) {
            pdfDocument?.close()
            pdfDocument = null
            callback.onLayoutCancelled()
            return
        }

        val info =
            PrintDocumentInfo.Builder("passport_sheet_4x6.pdf")
                .setContentType(PrintDocumentInfo.CONTENT_TYPE_PHOTO)
                .setPageCount(1)
                .build()

        callback.onLayoutFinished(info, true)
    }

    override fun onWrite(
        pages: Array<out PageRange>?,
        destination: ParcelFileDescriptor,
        cancellationSignal: CancellationSignal?,
        callback: WriteResultCallback,
    ) {
        val doc = pdfDocument
        if (doc == null) {
            callback.onWriteFailed("Print document was not initialized.")
            return
        }

        val page = doc.startPage(0)
        if (cancellationSignal?.isCanceled == true) {
            doc.close()
            pdfDocument = null
            callback.onWriteCancelled()
            return
        }

        val canvas = page.canvas
        val destRect = Rect(0, 0, canvas.width, canvas.height)
        canvas.drawBitmap(sheetBitmap, null, destRect, null)
        doc.finishPage(page)

        try {
            FileOutputStream(destination.fileDescriptor).use { out ->
                doc.writeTo(out)
            }
            callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
        } catch (e: IOException) {
            callback.onWriteFailed(e.localizedMessage ?: "Failed to write print PDF.")
        } finally {
            doc.close()
            pdfDocument = null
        }
    }
}
