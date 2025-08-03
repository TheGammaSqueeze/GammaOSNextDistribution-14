package com.android.intentresolver.v2.platform

import android.provider.Settings.SettingNotFoundException

/**
 * A component which provides access to values from [android.provider.Settings.Secure].
 *
 * All methods return nullable types instead of throwing [SettingNotFoundException] which yields
 * cleaner, more idiomatic Kotlin code:
 *
 * // apply a default: val foo = settings.getInt(FOO) ?: DEFAULT_FOO
 *
 * // assert if missing: val required = settings.getInt(REQUIRED_VALUE) ?: error("required value
 * missing")
 */
interface SecureSettings {

    fun getString(name: String): String?

    fun getInt(name: String): Int?

    fun getLong(name: String): Long?

    fun getFloat(name: String): Float?
}
