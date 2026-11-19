package com.example.export

import android.content.Context

data class PrinterAlignmentOffsets(
    val offsetX: Int = 0,
    val offsetY: Int = 0,
) {
    fun clamped(): PrinterAlignmentOffsets =
        PrinterAlignmentOffsets(
            offsetX = offsetX.coerceIn(-MAX_OFFSET_PX, MAX_OFFSET_PX),
            offsetY = offsetY.coerceIn(-MAX_OFFSET_PX, MAX_OFFSET_PX),
        )

    companion object {
        const val STEP_PX = 4
        const val MAX_OFFSET_PX = 80
    }
}

class PrinterAlignmentStore(context: Context) {
    private val prefs =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): PrinterAlignmentOffsets {
        val x = prefs.getInt(KEY_OFFSET_X, 0)
        val y = prefs.getInt(KEY_OFFSET_Y, 0)
        return PrinterAlignmentOffsets(offsetX = x, offsetY = y).clamped()
    }

    fun save(offsets: PrinterAlignmentOffsets) {
        val clamped = offsets.clamped()
        prefs.edit()
            .putInt(KEY_OFFSET_X, clamped.offsetX)
            .putInt(KEY_OFFSET_Y, clamped.offsetY)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "passport_printer_alignment"
        private const val KEY_OFFSET_X = "offset_x"
        private const val KEY_OFFSET_Y = "offset_y"
    }
}
