/*
 * Copyright 2024 The Android Open Source Project
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

package com.android.intentresolver.contentpreview

import android.content.ContentInterface
import android.content.Intent
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.service.chooser.AdditionalContentContract.Columns
import android.service.chooser.AdditionalContentContract.CursorExtraKeys
import android.util.Log
import android.util.SparseArray
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope

private const val TAG = ContentPreviewUi.TAG

/**
 * A bi-directional cursor reader. Reads URI from the [cursor] starting from the given [startPos],
 * filters items by [predicate].
 */
class CursorUriReader(
    private val cursor: Cursor,
    startPos: Int,
    private val pageSize: Int,
    private val predicate: (Uri) -> Boolean,
) : PayloadToggleInteractor.CursorReader {
    override val count = cursor.count
    // Unread ranges are:
    // - left: [0, leftPos);
    // - right: [rightPos, count)
    // i.e. read range is: [leftPos, rightPos)
    private var rightPos = startPos.coerceIn(0, count)
    private var leftPos = rightPos

    override val hasMoreBefore
        get() = leftPos > 0

    override val hasMoreAfter
        get() = rightPos < count

    override fun readPageAfter(): SparseArray<Uri> {
        if (!hasMoreAfter) return SparseArray()
        if (!cursor.moveToPosition(rightPos)) {
            rightPos = count
            Log.w(TAG, "Failed to move the cursor to position $rightPos, stop reading the cursor")
            return SparseArray()
        }
        val result = SparseArray<Uri>(pageSize)
        do {
            cursor
                .getString(0)
                ?.let(Uri::parse)
                ?.takeIf { predicate(it) }
                ?.let { uri -> result.append(rightPos, uri) }
            rightPos++
        } while (result.size() < pageSize && cursor.moveToNext())
        maybeCloseCursor()
        return result
    }

    override fun readPageBefore(): SparseArray<Uri> {
        if (!hasMoreBefore) return SparseArray()
        val startPos = maxOf(0, leftPos - pageSize)
        if (!cursor.moveToPosition(startPos)) {
            leftPos = 0
            Log.w(TAG, "Failed to move the cursor to position $startPos, stop reading cursor")
            return SparseArray()
        }
        val result = SparseArray<Uri>(leftPos - startPos)
        for (pos in startPos until leftPos) {
            cursor
                .getString(0)
                ?.let(Uri::parse)
                ?.takeIf { predicate(it) }
                ?.let { uri -> result.append(pos, uri) }
            if (!cursor.moveToNext()) break
        }
        leftPos = startPos
        maybeCloseCursor()
        return result
    }

    private fun maybeCloseCursor() {
        if (!hasMoreBefore && !hasMoreAfter) {
            close()
        }
    }

    override fun close() {
        cursor.close()
    }

    companion object {
        suspend fun createCursorReader(
            contentResolver: ContentInterface,
            uri: Uri,
            chooserIntent: Intent
        ): CursorUriReader {
            val cancellationSignal = CancellationSignal()
            val cursor =
                try {
                    coroutineScope {
                        runCatching {
                                contentResolver.query(
                                    uri,
                                    arrayOf(Columns.URI),
                                    Bundle().apply {
                                        putParcelable(Intent.EXTRA_INTENT, chooserIntent)
                                    },
                                    cancellationSignal
                                )
                            }
                            .getOrNull()
                            ?: MatrixCursor(arrayOf(Columns.URI))
                    }
                } catch (e: CancellationException) {
                    cancellationSignal.cancel()
                    throw e
                }
            return CursorUriReader(
                cursor,
                cursor.extras?.getInt(CursorExtraKeys.POSITION, 0) ?: 0,
                128,
            ) {
                it.authority != uri.authority
            }
        }
    }
}
