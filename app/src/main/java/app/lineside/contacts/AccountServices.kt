// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Absolute Candor

package app.lineside.contacts

import android.accounts.AbstractAccountAuthenticator
import android.accounts.Account
import android.accounts.AccountAuthenticatorResponse
import android.app.Service
import android.content.AbstractThreadedSyncAdapter
import android.content.ContentProviderClient
import android.content.Context
import android.content.Intent
import android.content.SyncResult
import android.os.Bundle
import android.os.IBinder

/** Lineside's local account. No credentials, no server: Android just needs an account to group our contacts under. */
class AuthenticatorService : Service() {
    private lateinit var auth: Authenticator
    override fun onCreate() { auth = Authenticator(this) }
    override fun onBind(intent: Intent?): IBinder = auth.iBinder

    class Authenticator(ctx: Context) : AbstractAccountAuthenticator(ctx) {
        override fun editProperties(r: AccountAuthenticatorResponse?, t: String?): Bundle? = null
        override fun addAccount(r: AccountAuthenticatorResponse?, t: String?, a: String?, f: Array<out String>?, o: Bundle?): Bundle? = null
        override fun confirmCredentials(r: AccountAuthenticatorResponse?, a: Account?, o: Bundle?): Bundle? = null
        override fun getAuthToken(r: AccountAuthenticatorResponse?, a: Account?, t: String?, o: Bundle?): Bundle? = null
        override fun getAuthTokenLabel(t: String?): String? = null
        override fun updateCredentials(r: AccountAuthenticatorResponse?, a: Account?, t: String?, o: Bundle?): Bundle? = null
        override fun hasFeatures(r: AccountAuthenticatorResponse?, a: Account?, f: Array<out String>?): Bundle =
            Bundle().apply { putBoolean(android.accounts.AccountManager.KEY_BOOLEAN_RESULT, false) }
    }
}

/** A sync adapter that never syncs. Contacts apps only treat an account as a contact source if it has one. */
class SyncService : Service() {
    private lateinit var adapter: NoopSyncAdapter
    override fun onCreate() { adapter = NoopSyncAdapter(this) }
    override fun onBind(intent: Intent?): IBinder = adapter.syncAdapterBinder

    class NoopSyncAdapter(ctx: Context) : AbstractThreadedSyncAdapter(ctx, true) {
        override fun onPerformSync(a: Account?, e: Bundle?, auth: String?, p: ContentProviderClient?, r: SyncResult?) = Unit
    }
}
