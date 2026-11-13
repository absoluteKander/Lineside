// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Absolute Candor

package app.lineside.contacts

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import java.io.ByteArrayOutputStream

/**
 * The loop's contact photo: a LOOP badge, its initials, and a few characters of status. Shown large on
 * incoming-call and lock screens. Everything stays inside the circle phone apps crop it to.
 */
object PhotoRenderer {
    fun render(initials: String, status: String?, bg: Int): ByteArray {
        val size = 720
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(bg)
        val bold = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        val mid = size / 2f

        val badge = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 44f; typeface = bold; letterSpacing = 0.14f; textAlign = Paint.Align.CENTER }
        val w = badge.measureText("LOOP") + 52f
        c.drawRoundRect(RectF(mid - w / 2, 168f, mid + w / 2, 236f), 16f, 16f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFC46B.toInt() })
        badge.color = 0xFF12181D.toInt()
        c.drawText("LOOP", mid, 218f, badge)

        val big = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = if (initials.length > 1) 210f else 240f; typeface = bold; color = -1; textAlign = Paint.Align.CENTER }
        c.drawText(initials, mid, if (status == null) 500f else 455f, big)

        if (status != null) {
            val small = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 58f; typeface = bold; color = 0xFFDFF3F3.toInt(); textAlign = Paint.Align.CENTER }
            while (small.measureText(status) > 500f && small.textSize > 30f) small.textSize -= 2f
            c.drawText(status, mid, 548f, small)
        }
        return ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray()
    }
}
