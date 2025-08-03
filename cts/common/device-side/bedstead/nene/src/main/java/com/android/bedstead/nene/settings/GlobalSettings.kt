/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.bedstead.nene.settings

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentResolver
import android.os.Build
import android.provider.Settings
import androidx.annotation.RequiresApi
import com.android.bedstead.nene.TestApis
import com.android.bedstead.nene.exceptions.NeneException
import com.android.bedstead.nene.permissions.CommonPermissions.INTERACT_ACROSS_USERS_FULL
import com.android.bedstead.nene.users.UserReference
import com.android.bedstead.nene.utils.Versions

/** APIs related to [Settings.Global].  */
object GlobalSettings {
    /**
     * See [Settings.Global.putInt]
     */
    @RequiresApi(Build.VERSION_CODES.S)
    fun putInt(contentResolver: ContentResolver, key: String, value: Int) {
        Versions.requireMinimumVersion(Build.VERSION_CODES.S)
        TestApis.permissions().withPermission(
                INTERACT_ACROSS_USERS_FULL, Manifest.permission.WRITE_SECURE_SETTINGS).use {
                    Settings.Global.putInt(contentResolver, key, value)
                }
    }

    /**
     * Put int to global settings for the given [UserReference].
     *
     *
     * If the user is not the instrumented user, this will only succeed when running on Android S
     * and above.
     *
     *
     * See [.putInt]
     */
    @SuppressLint("NewApi")
    fun putInt(user: UserReference, key: String, value: Int) {
        if (user == TestApis.users().instrumented()) {
            putInt(key, value)
            return
        }
        putInt(TestApis.context().androidContextAsUser(user).contentResolver, key, value)
    }

    /**
     * Put int to global settings for the instrumented user.
     *
     *
     * See [.putInt]
     */
    fun putInt(key: String, value: Int) {
        TestApis.permissions().withPermission(Manifest.permission.WRITE_SECURE_SETTINGS).use { p ->
            Settings.Global.putInt(
                    TestApis.context().instrumentedContext().contentResolver, key, value)
        }
    }

    /**
     * See [Settings.Global.putString]
     */
    @RequiresApi(Build.VERSION_CODES.S)
    fun putString(contentResolver: ContentResolver, key: String, value: String) {
        Versions.requireMinimumVersion(Build.VERSION_CODES.S)
        TestApis.permissions().withPermission(
                INTERACT_ACROSS_USERS_FULL, Manifest.permission.WRITE_SECURE_SETTINGS).use {
                    Settings.Global.putString(contentResolver, key, value)
                }
    }

    /**
     * Put string to global settings for the given [UserReference].
     *
     *
     * If the user is not the instrumented user, this will only succeed when running on Android S
     * and above.
     *
     *
     * See [.putString]
     */
    @SuppressLint("NewApi")
    fun putString(user: UserReference, key: String, value: String) {
        if (user == TestApis.users().instrumented()) {
            putString(key, value)
            return
        }
        putString(TestApis.context().androidContextAsUser(user).contentResolver, key, value)
    }

    /**
     * Put string to global settings for the instrumented user.
     *
     *
     * See [.putString]
     */
    fun putString(key: String, value: String) {
        TestApis.permissions().withPermission(Manifest.permission.WRITE_SECURE_SETTINGS).use {
            Settings.Global.putString(
                    TestApis.context().instrumentedContext().contentResolver, key, value)
        }
    }

    /**
     * See [Settings.Global.getInt]
     */
    @RequiresApi(Build.VERSION_CODES.S)
    fun getInt(contentResolver: ContentResolver, key: String): Int {
        Versions.requireMinimumVersion(Build.VERSION_CODES.S)
        TestApis.permissions().withPermission(INTERACT_ACROSS_USERS_FULL).use {
            return getIntInner(contentResolver, key)
        }
    }

    /**
     * See [Settings.Global.getInt]
     */
    @RequiresApi(Build.VERSION_CODES.S)
    fun getInt(contentResolver: ContentResolver, key: String, defaultValue: Int): Int {
        Versions.requireMinimumVersion(Build.VERSION_CODES.S)
        TestApis.permissions().withPermission(INTERACT_ACROSS_USERS_FULL).use {
            return getIntInner(contentResolver, key, defaultValue)
        }
    }

    private fun getIntInner(contentResolver: ContentResolver, key: String): Int {
        return try {
            Settings.Global.getInt(contentResolver, key)
        } catch (e: Settings.SettingNotFoundException) {
            throw NeneException("Error getting int setting", e)
        }
    }

    private fun getIntInner(contentResolver: ContentResolver, key: String, defaultValue: Int): Int {
        return Settings.Global.getInt(contentResolver, key, defaultValue)
    }

    /**
     * Get int from global settings for the given [UserReference].
     *
     *
     * If the user is not the instrumented user, this will only succeed when running on Android S
     * and above.
     *
     *
     * See [.getInt]
     */
    @SuppressLint("NewApi")
    fun getInt(user: UserReference, key: String): Int {
        return if (user == TestApis.users().instrumented()) {
            getInt(key)
        } else getInt(TestApis.context().androidContextAsUser(user).contentResolver, key)
    }

    /**
     * Get int from global settings for the given [UserReference], or the default value.
     *
     *
     * If the user is not the instrumented user, this will only succeed when running on Android S
     * and above.
     *
     *
     * See [.getInt]
     */
    @SuppressLint("NewApi")
    fun getInt(user: UserReference, key: String, defaultValue: Int): Int {
        return if (user == TestApis.users().instrumented()) {
            getInt(key, defaultValue)
        } else getInt(
                TestApis.context().androidContextAsUser(user).contentResolver,
                key, defaultValue)
    }

    /**
     * Get int from global settings for the instrumented user.
     *
     *
     * See [.getInt]
     */
    fun getInt(key: String): Int {
        return getIntInner(TestApis.context().instrumentedContext().contentResolver, key)
    }

    /**
     * Get int from global settings for the instrumented user, or the default value.
     *
     *
     * See [.getInt]
     */
    fun getInt(key: String, defaultValue: Int): Int {
        return getIntInner(
                TestApis.context().instrumentedContext().contentResolver, key, defaultValue)
    }

    /**
     * See [Settings.Global.getString]
     */
    @RequiresApi(Build.VERSION_CODES.S)
    fun getString(contentResolver: ContentResolver, key: String): String? {
        Versions.requireMinimumVersion(Build.VERSION_CODES.S)
        TestApis.permissions().withPermission(INTERACT_ACROSS_USERS_FULL).use {
            return getStringInner(contentResolver, key)
        }
    }

    private fun getStringInner(contentResolver: ContentResolver, key: String): String? {
        return Settings.Global.getString(contentResolver, key)
    }

    /**
     * Get string from global settings for the given [UserReference].
     *
     *
     * If the user is not the instrumented user, this will only succeed when running on Android S
     * and above.
     *
     *
     * See [.getString]
     */
    @SuppressLint("NewApi")
    fun getString(user: UserReference, key: String): String? {
        return if (user == TestApis.users().instrumented()) {
            getString(key)
        } else getString(TestApis.context().androidContextAsUser(user).contentResolver, key)
    }

    /**
     * Get string from global settings for the instrumented user.
     *
     *
     * See [.getString]
     */
    fun getString(key: String): String? {
        return getStringInner(TestApis.context().instrumentedContext().contentResolver, key)
    }

    /**
     * Reset all global settings set by this package.
     *
     * See [Settings.Global.resetToDefaults].
     *
     *
     * The `tag` argument is always set to null.
     */
    @RequiresApi(Build.VERSION_CODES.S)
    fun reset(contentResolver: ContentResolver?) {
        Versions.requireMinimumVersion(Build.VERSION_CODES.S)
        TestApis.permissions().withPermission(
                Manifest.permission.WRITE_SECURE_SETTINGS, INTERACT_ACROSS_USERS_FULL).use {
                    Settings.Global.resetToDefaults(contentResolver!!,  /* tag= */null)
                }
    }

    /**
     * Reset all global settings set by this package for the given user.
     *
     *
     * If the user is not the instrumented user, this will only succeed when running on Android S
     * and above.
     *
     * See [.reset].
     */
    @SuppressLint("NewApi")
    fun reset(user: UserReference) {
        if (user == TestApis.users().instrumented()) {
            reset()
            return
        }
        reset(TestApis.context().androidContextAsUser(user).contentResolver)
    }

    /**
     * Reset all global settings set by this package for the instrumented user.
     *
     * See [.reset].
     */
    fun reset() {
        TestApis.permissions().withPermission(Manifest.permission.WRITE_SECURE_SETTINGS).use {
            Settings.Global.resetToDefaults(
                    TestApis.context().instrumentedContext().contentResolver,  /* tag= */
                    null)
        }
    }
}
