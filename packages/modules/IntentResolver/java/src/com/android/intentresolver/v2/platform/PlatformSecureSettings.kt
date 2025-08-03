package com.android.intentresolver.v2.platform

import android.content.ContentResolver
import android.provider.Settings
import javax.inject.Inject

/**
 * Implements [SecureSettings] backed by Settings.Secure and a ContentResolver.
 *
 * These methods make Binder calls and may block, so use on the Main thread should be avoided.
 */
class PlatformSecureSettings @Inject constructor(private val resolver: ContentResolver) :
    SecureSettings {

    override fun getString(name: String): String? {
        return Settings.Secure.getString(resolver, name)
    }

    override fun getInt(name: String): Int? {
        return runCatching { Settings.Secure.getInt(resolver, name) }.getOrNull()
    }

    override fun getLong(name: String): Long? {
        return runCatching { Settings.Secure.getLong(resolver, name) }.getOrNull()
    }

    override fun getFloat(name: String): Float? {
        return runCatching { Settings.Secure.getFloat(resolver, name) }.getOrNull()
    }
}
