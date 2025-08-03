/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.intentresolver

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri

class TestContentProvider : ContentProvider() {
    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? = null

    override fun getType(uri: Uri): String? =
        runCatching { uri.getQueryParameter(PARAM_MIME_TYPE) }.getOrNull()

    override fun getStreamTypes(uri: Uri, mimeTypeFilter: String): Array<String>? {
        val delay =
            runCatching { uri.getQueryParameter(PARAM_STREAM_TYPE_TIMEOUT)?.toLong() ?: 0L }
                .getOrDefault(0L)
        if (delay > 0) {
            try {
                Thread.sleep(delay)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
        return runCatching { uri.getQueryParameter(PARAM_STREAM_TYPE)?.let { arrayOf(it) } }
            .getOrNull()
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = 0

    override fun onCreate(): Boolean = true

    companion object {
        const val PARAM_MIME_TYPE = "mimeType"
        const val PARAM_STREAM_TYPE = "streamType"
        const val PARAM_STREAM_TYPE_TIMEOUT = "streamTypeTo"
    }
}
