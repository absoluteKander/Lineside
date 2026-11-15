// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Absolute Candor

package app.lineside

import android.app.backup.BackupAgent
import android.app.backup.BackupDataInput
import android.app.backup.BackupDataOutput
import android.app.backup.FullBackupDataOutput
import android.database.sqlite.SQLiteDatabase
import android.os.ParcelFileDescriptor
import android.util.Log

/**
 * Android's backup, on Lineside's terms. Android asks; Lineside hands over its database and settings only when
 * the user switched backup on, and only into a backup encrypted end to end with the phone's screen lock, or a
 * direct phone-to-phone transfer. Otherwise Android gets nothing. `data_extraction_rules.xml` says the same
 * to Android itself (`disableIfNoEncryptionCapabilities`), so either check alone would hold.
 *
 * Android runs this in a bare process without [App], so nothing here may touch App or Room.
 */
class Backup : BackupAgent() {
    override fun onFullBackup(data: FullBackupDataOutput) {
        val settings = Settings(this)
        val flags = data.transportFlags
        val encrypted = flags and FLAG_CLIENT_SIDE_ENCRYPTION_ENABLED != 0
        val transfer = flags and FLAG_DEVICE_TO_DEVICE_TRANSFER != 0
        if (!allowed(settings.backupOn, encrypted, transfer)) {
            if (settings.backupOn) settings.backupRefusedAt = System.currentTimeMillis()
            Log.i(TAG, "backup declined (on=${settings.backupOn}, encrypted=$encrypted, transfer=$transfer)")
            return
        }
        settings.backupRefusedAt = 0L
        // Fold the write-ahead log into the database file so the copy is whole.
        runCatching {
            val f = getDatabasePath("lineside.db")
            if (f.exists()) SQLiteDatabase.openDatabase(f.path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
                db.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { it.moveToFirst() }
            }
        }.onFailure { Log.w(TAG, "checkpoint failed", it) }
        super.onFullBackup(data)
    }

    /** The new phone starts without the old phone's contact ids, message ids and texts switches. See [App]. */
    override fun onRestoreFinished() {
        Settings(this).restoredPending = true
    }

    // Lineside only uses full backups; the key-value kind is never asked for.
    override fun onBackup(oldState: ParcelFileDescriptor?, data: BackupDataOutput?, newState: ParcelFileDescriptor?) = Unit
    override fun onRestore(data: BackupDataInput?, appVersionCode: Int, newState: ParcelFileDescriptor?) = Unit

    companion object {
        private const val TAG = "LinesideBackup"

        fun allowed(on: Boolean, encrypted: Boolean, transfer: Boolean) = on && (encrypted || transfer)
    }
}
