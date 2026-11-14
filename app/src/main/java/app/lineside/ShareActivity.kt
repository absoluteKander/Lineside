// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Absolute Candor

package app.lineside

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import app.lineside.data.Event
import app.lineside.data.EventSource
import app.lineside.data.LoopFull
import app.lineside.data.NumberInput
import app.lineside.domain.Shared
import app.lineside.ui.Arrived
import app.lineside.ui.LinesideTheme
import app.lineside.ui.ShareScreen
import kotlinx.coroutines.launch

/**
 * "Share → Lineside" from a voicemail or a text in another app. Shows what arrived and files it on a loop.
 * Only text is kept; a shared audio file is named but never opened.
 */
class ShareActivity : ComponentActivity() {
    private val app get() = application as App

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val arrived = read(intent)
        if (BuildConfig.DEBUG) Log.i(TAG, "Shared in: $arrived\nextras: ${dump(intent)}")
        val guess = Shared.guess(arrived.text, arrived.subject, arrived.fileType ?: arrived.type, arrived.fileName)
        enableEdgeToEdge(statusBarStyle = androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        setContent {
            LinesideTheme(app.settings.themeMode) {
                val loops by app.repo.observeAll().collectAsStateWithLifecycle(null as List<LoopFull>?)
                ShareScreen(arrived, guess, loops, onCancel = ::finish) { save ->
                    lifecycleScope.launch {
                        val loopId = save.loopId ?: app.repo.create(save.newName.trim(), "", listOfNotNull(save.number?.let { NumberInput(it) }))
                        if (save.loopId != null && save.addNumber && save.number != null) app.repo.addNumber(loopId, save.number)
                        app.repo.addEvent(
                            Event(loopId = loopId, kind = save.kind, at = save.at, text = save.text.trim(),
                                number = save.number, source = EventSource.SHARED),
                        )
                        val name = app.repo.get(loopId)?.loop?.name.orEmpty()
                        Toast.makeText(this@ShareActivity, "Added to $name", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }
            }
        }
    }

    private fun read(i: Intent): Arrived {
        val stream: Uri? = runCatching {
            if (android.os.Build.VERSION.SDK_INT >= 33) i.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            else @Suppress("DEPRECATION") i.getParcelableExtra(Intent.EXTRA_STREAM)
        }.getOrNull()
        var name: String? = null
        var size: Long? = null
        // Name and size only: the file's contents are never read.
        if (stream != null) runCatching {
            contentResolver.query(stream, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    name = c.getString(0)
                    size = if (c.isNull(1)) null else c.getLong(1)
                }
            }
        }
        val fileType = stream?.let { runCatching { contentResolver.getType(it) }.getOrNull() }
        return Arrived(
            from = referrer?.host ?: callingPackage,
            type = i.type,
            subject = i.getStringExtra(Intent.EXTRA_SUBJECT) ?: i.getStringExtra(Intent.EXTRA_TITLE),
            text = i.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString(),
            fileName = name ?: stream?.lastPathSegment,
            fileType = fileType,
            fileSize = size,
        )
    }

    private fun dump(i: Intent): String = i.extras?.let { b -> b.keySet().joinToString { k -> "$k=${runCatching { @Suppress("DEPRECATION") b.get(k) }.getOrNull()}" } }.orEmpty()

    private companion object { const val TAG = "LinesideShare" }
}
