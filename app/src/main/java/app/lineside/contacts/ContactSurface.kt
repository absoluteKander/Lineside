// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Absolute Candor

package app.lineside.contacts

import android.Manifest
import android.accounts.Account
import android.accounts.AccountManager
import android.content.ContentProviderOperation
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.StructuredName
import android.provider.ContactsContract.Data
import android.provider.ContactsContract.PhoneLookup
import android.provider.ContactsContract.RawContacts
import android.util.Log
import app.lineside.R
import app.lineside.data.LoopFull
import androidx.compose.ui.graphics.toArgb
import app.lineside.domain.Initials
import app.lineside.domain.LoopStatus
import app.lineside.domain.photoStatus
import app.lineside.ui.Brand

/**
 * Loops shown as real system contacts, so the phone app displays them on the call screen.
 * They live under Lineside's own local account, which never syncs anywhere. Everything here is
 * optional: without Contacts access every call is a no-op and loops live only inside Lineside.
 */
class ContactSurface(private val ctx: Context, private val discreet: () -> Boolean = { false }) {
    private val accountType = ctx.getString(R.string.account_type)
    private val account = Account(ACCOUNT_NAME, accountType)
    private val cr get() = ctx.contentResolver

    fun canRead() = granted(Manifest.permission.READ_CONTACTS)
    fun canWrite() = granted(Manifest.permission.WRITE_CONTACTS) && canRead()
    private fun granted(p: String) = ctx.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

    private fun ensureAccount() {
        val am = AccountManager.get(ctx)
        if (am.getAccountsByType(accountType).isEmpty()) am.addAccountExplicitly(account, null, null)
        runCatching { ContentResolver.setIsSyncable(account, ContactsContract.AUTHORITY, 0) }
            .onFailure { Log.w(TAG, "couldn't mark account as non-syncing", it) }
        // Show our contacts in Contacts apps even though we use no groups.
        runCatching {
            cr.insert(ContactsContract.Settings.CONTENT_URI, ContentValues().apply {
                put(ContactsContract.Settings.ACCOUNT_NAME, ACCOUNT_NAME)
                put(ContactsContract.Settings.ACCOUNT_TYPE, accountType)
                put(ContactsContract.Settings.UNGROUPED_VISIBLE, 1)
            })
        }
    }

    private fun asSyncAdapter(uri: Uri): Uri = uri.buildUpon()
        .appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true")
        .appendQueryParameter(RawContacts.ACCOUNT_NAME, ACCOUNT_NAME)
        .appendQueryParameter(RawContacts.ACCOUNT_TYPE, accountType)
        .build()

    /**
     * Creates or rewrites the loop's contact: name, and each number labelled with the live status line.
     * Returns the raw contact id, or null when Contacts access isn't allowed.
     */
    fun sync(full: LoopFull, now: Long = System.currentTimeMillis()): Long? {
        if (!canWrite()) return null
        return runCatching {
            val existing = full.loop.rawContactId?.takeIf { exists(it) }
            val rawId = existing ?: create()
            val isPrivate = discreet()
            write(rawId, full, LoopStatus.statusLine(full.events, now, full.loop.topic, full.loop.createdAt, isPrivate), isPrivate)
            writePhoto(rawId, full, now)
            rawId
        }.onFailure { Log.w(TAG, "sync failed", it) }.getOrNull()
    }

    private fun create(): Long {
        ensureAccount()
        val uri = cr.insert(RawContacts.CONTENT_URI, ContentValues().apply {
            put(RawContacts.ACCOUNT_TYPE, accountType); put(RawContacts.ACCOUNT_NAME, ACCOUNT_NAME)
        })!!
        return ContentUris.parseId(uri)
    }

    /** Photos are big writes: only redraw when what's on it changes. */
    private val photoKeys = ctx.getSharedPreferences("photo_keys", Context.MODE_PRIVATE)

    private fun writePhoto(rawId: Long, full: LoopFull, now: Long) {
        val initials = Initials.of(full.loop.name)
        val status = LoopStatus.photoStatus(full.events, now)
        val color = Brand.avatar(full.loop.id).toArgb()
        val key = "$initials|$status|$color"
        if (photoKeys.getString(rawId.toString(), null) == key) return
        val jpeg = PhotoRenderer.render(initials, status, color)
        val uri = Uri.withAppendedPath(ContentUris.withAppendedId(RawContacts.CONTENT_URI, rawId), RawContacts.DisplayPhoto.CONTENT_DIRECTORY)
        runCatching {
            cr.openAssetFileDescriptor(uri, "rw")?.use { fd -> fd.createOutputStream().use { it.write(jpeg) } }
            photoKeys.edit().putString(rawId.toString(), key).apply()
        }.onFailure { Log.w(TAG, "photo write failed", it) }
    }

    private fun write(rawId: Long, full: LoopFull, status: String, isPrivate: Boolean) {
        val sel = "${Data.RAW_CONTACT_ID}=? AND ${Data.MIMETYPE}=?"
        val ops = arrayListOf(
            ContentProviderOperation.newDelete(Data.CONTENT_URI)
                .withSelection(sel, arrayOf(rawId.toString(), StructuredName.CONTENT_ITEM_TYPE)).build(),
            ContentProviderOperation.newDelete(Data.CONTENT_URI)
                .withSelection(sel, arrayOf(rawId.toString(), Phone.CONTENT_ITEM_TYPE)).build(),
            ContentProviderOperation.newInsert(Data.CONTENT_URI)
                .withValue(Data.RAW_CONTACT_ID, rawId)
                .withValue(Data.MIMETYPE, StructuredName.CONTENT_ITEM_TYPE)
                .withValue(StructuredName.DISPLAY_NAME, full.loop.name).build(),
        )
        // The phone app shows the number's custom label under the name: that's our status line.
        // With several parties in one loop, each number's label says which party is calling: "Radiology · Waiting · …".
        val multi = full.numbers.size > 1
        for (n in full.numbers) ops += ContentProviderOperation.newInsert(Data.CONTENT_URI)
            .withValue(Data.RAW_CONTACT_ID, rawId)
            .withValue(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE)
            .withValue(Phone.NUMBER, n.number)
            .withValue(Phone.TYPE, Phone.TYPE_CUSTOM)
            .withValue(Phone.LABEL, if (multi && !isPrivate && n.label.isNotBlank()) "${n.label.take(16)} · $status" else status).build()
        cr.applyBatch(ContactsContract.AUTHORITY, ops)
    }

    fun delete(rawId: Long?) {
        if (rawId == null || !canWrite()) return
        runCatching { cr.delete(asSyncAdapter(ContentUris.withAppendedId(RawContacts.CONTENT_URI, rawId)), null, null) }
            .onFailure { Log.w(TAG, "delete failed", it) }
    }

    /** Every raw contact under Lineside's account. */
    fun ourRawContactIds(): List<Long> {
        if (!canRead()) return emptyList()
        val ids = mutableListOf<Long>()
        cr.query(RawContacts.CONTENT_URI, arrayOf(RawContacts._ID),
            "${RawContacts.ACCOUNT_TYPE}=? AND ${RawContacts.DELETED}=0", arrayOf(accountType), null)
            ?.use { while (it.moveToNext()) ids += it.getLong(0) }
        return ids
    }

    private fun exists(rawId: Long): Boolean =
        cr.query(ContentUris.withAppendedId(RawContacts.CONTENT_URI, rawId), arrayOf(RawContacts.DELETED), null, null, null)
            ?.use { it.moveToFirst() && it.getInt(0) == 0 } ?: false

    /**
     * True if this number belongs to one of the user's regular (permanent) contacts, i.e. any contact
     * that isn't one of our loops. Lineside stays out of the way for those.
     */
    fun isPermanentContact(number: String): Boolean {
        if (!canRead() || number.isBlank()) return false
        val ids = mutableListOf<Long>()
        cr.query(Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number)), arrayOf(PhoneLookup._ID), null, null, null)
            ?.use { while (it.moveToNext()) ids += it.getLong(0) }
        return ids.any { cid ->
            cr.query(RawContacts.CONTENT_URI, arrayOf(RawContacts._ID),
                "${RawContacts.CONTACT_ID}=? AND (${RawContacts.ACCOUNT_TYPE} IS NULL OR ${RawContacts.ACCOUNT_TYPE}!=?) AND ${RawContacts.DELETED}=0",
                arrayOf(cid.toString(), accountType), null)?.use { it.count > 0 } ?: false
        }
    }

    companion object {
        /** Shown under Settings → Accounts. It's not a login; onboarding explains that. */
        const val ACCOUNT_NAME = "Lineside"
        private const val TAG = "Lineside"
    }
}
