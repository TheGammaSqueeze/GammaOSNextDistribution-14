/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.cts.mockspellchecker

import android.content.ContentProvider
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.util.Base64

private const val PREFS_FILE_NAME = "prefs.xml"
private const val COLUMN_NAME = "value"

/**
 * ContentProvider to access MockSpellChecker's shared preferences.
 *
 * <p>Please use the companion object methods to interact with this ContentProvider. The companion
 * object methods can be used from other processes.
 *
 * <p>This class supports ByteArray value only.
 */
class SharedPrefsProvider : ContentProvider() {

    override fun onCreate(): Boolean = withLog("SharedPrefsProvider.onCreate") { true }

    override fun getType(uri: Uri): String? = null

    override fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?
    ): Cursor? = withLog("SharedPrefsProvider.query: $uri") {
        val context = context ?: return null
        val prefs = getSharedPreferences(context)
        val bytes = Base64.decode(prefs.getString(uri.path, ""), Base64.DEFAULT)
        val cursor = MatrixCursor(arrayOf(COLUMN_NAME))
        cursor.addRow(arrayOf(bytes))
        return cursor
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? =
            withLog("SharedPrefsProvider.insert: $uri") { null }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<String>?
    ): Int = withLog("SharedPrefsProvider.update: $uri") {
        val context = context ?: return 0
        if (values == null) return 0
        val prefs = getSharedPreferences(context)
        val bytes = values.getAsByteArray(COLUMN_NAME)
        val str = Base64.encodeToString(bytes, Base64.DEFAULT)
        prefs.edit().putString(uri.path, str).apply()
        return 1
    }

    override fun delete(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<String>?
    ): Int = withLog("SharedPrefsProvider.delete: $uri") {
        val context = context ?: return 0
        val prefs = getSharedPreferences(context)
        prefs.edit().remove(uri.path).apply()
        return 1
    }

    private fun getSharedPreferences(context: Context) =
        context.getSharedPreferences(PREFS_FILE_NAME, Context.MODE_PRIVATE)

    companion object {
        /** Returns the data for the key. */
        fun get(resolver: ContentResolver, key: String): ByteArray {
            val cursor = resolver.query(uriFor(key), arrayOf(COLUMN_NAME), null, null)
            return if (cursor != null && cursor.moveToNext()) {
                cursor.getBlob(0)
            } else {
                ByteArray(0)
            }
        }

        /** Stores the data for the key. */
        fun put(resolver: ContentResolver, key: String, value: ByteArray) {
            val values = ContentValues()
            values.put(COLUMN_NAME, value)
            resolver.update(uriFor(key), values, null)
        }

        /** Deletes the data for the key. */
        fun delete(resolver: ContentResolver, key: String) {
            resolver.delete(uriFor(key), null)
        }

        private fun uriFor(key: String): Uri = Uri.parse("content://$AUTHORITY/$key")
    }
}
