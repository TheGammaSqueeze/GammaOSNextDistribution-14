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

package com.android.intentresolver.contentpreview

import android.content.ContentInterface
import android.content.Intent
import android.media.MediaMetadata
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document.FLAG_SUPPORTS_THUMBNAIL
import android.provider.Downloads
import android.provider.OpenableColumns
import android.text.TextUtils
import android.util.Log
import androidx.annotation.OpenForTesting
import androidx.annotation.VisibleForTesting
import com.android.intentresolver.contentpreview.ContentPreviewType.CONTENT_PREVIEW_FILE
import com.android.intentresolver.contentpreview.ContentPreviewType.CONTENT_PREVIEW_IMAGE
import com.android.intentresolver.contentpreview.ContentPreviewType.CONTENT_PREVIEW_PAYLOAD_SELECTION
import com.android.intentresolver.contentpreview.ContentPreviewType.CONTENT_PREVIEW_TEXT
import com.android.intentresolver.measurements.runTracing
import com.android.intentresolver.util.ownedByCurrentUser
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Consumer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/**
 * A set of metadata columns we read for a content URI (see
 * [PreviewDataProvider.UriRecord.readQueryResult] method).
 */
@VisibleForTesting
val METADATA_COLUMNS =
    arrayOf(
        DocumentsContract.Document.COLUMN_FLAGS,
        MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI,
        OpenableColumns.DISPLAY_NAME,
        Downloads.Impl.COLUMN_TITLE
    )
private const val TIMEOUT_MS = 1_000L

/**
 * Asynchronously loads and stores shared URI metadata (see [Intent.EXTRA_STREAM]) such as mime
 * type, file name, and a preview thumbnail URI.
 */
@OpenForTesting
open class PreviewDataProvider
@JvmOverloads
constructor(
    private val scope: CoroutineScope,
    private val targetIntent: Intent,
    private val additionalContentUri: Uri?,
    private val contentResolver: ContentInterface,
    // TODO: replace with the ChooserServiceFlags ref when PreviewViewModel dependencies are sorted
    // out
    private val isPayloadTogglingEnabled: Boolean,
    private val typeClassifier: MimeTypeClassifier = DefaultMimeTypeClassifier,
) {

    private val records = targetIntent.contentUris.map { UriRecord(it) }

    private val fileInfoSharedFlow: SharedFlow<FileInfo> by lazy {
        // Alternatively, we could just use [shareIn()] on a [flow] -- and it would be, arguably,
        //  cleaner -- but we'd lost the ability to trace the traverse as [runTracing] does not
        //  generally work over suspend function invocations.
        MutableSharedFlow<FileInfo>(replay = records.size).apply {
            scope.launch {
                runTracing("image-preview-metadata") {
                    for (record in records) {
                        tryEmit(FileInfo.Builder(record.uri).readFromRecord(record).build())
                    }
                }
            }
        }
    }

    /** returns number of shared URIs, see [Intent.EXTRA_STREAM] */
    @get:OpenForTesting
    open val uriCount: Int
        get() = records.size

    val uris: List<Uri>
        get() = records.map { it.uri }

    /**
     * Returns a [Flow] of [FileInfo], for each shared URI in order, with [FileInfo.mimeType] and
     * [FileInfo.previewUri] set (a data projection tailored for the image preview UI).
     */
    @get:OpenForTesting
    open val imagePreviewFileInfoFlow: Flow<FileInfo>
        get() = fileInfoSharedFlow.take(records.size)

    /**
     * Preview type to use. The type is determined asynchronously with a timeout; the fall-back
     * values is [ContentPreviewType.CONTENT_PREVIEW_FILE]
     */
    @get:OpenForTesting
    @get:ContentPreviewType
    open val previewType: Int by lazy {
        runTracing("preview-type") {
            /* In [android.content.Intent#getType], the app may specify a very general mime type
             * that broadly covers all data being shared, such as '*' when sending an image
             * and text. We therefore should inspect each item for the preferred type, in order:
             * IMAGE, FILE, TEXT. */
            if (!targetIntent.isSend || records.isEmpty()) {
                CONTENT_PREVIEW_TEXT
            } else if (isPayloadTogglingEnabled && shouldShowPayloadSelection()) {
                // TODO: replace with the proper flags injection
                CONTENT_PREVIEW_PAYLOAD_SELECTION
            } else {
                try {
                    runBlocking(scope.coroutineContext) {
                        withTimeoutOrNull(TIMEOUT_MS) { scope.async { loadPreviewType() }.await() }
                            ?: CONTENT_PREVIEW_FILE
                    }
                } catch (e: CancellationException) {
                    Log.w(
                        ContentPreviewUi.TAG,
                        "An attempt to read preview type from a cancelled scope",
                        e
                    )
                    CONTENT_PREVIEW_FILE
                }
            }
        }
    }

    private fun shouldShowPayloadSelection(): Boolean {
        val extraContentUri = additionalContentUri ?: return false
        return runCatching {
                val authority = extraContentUri.authority
                records.firstOrNull { authority == it.uri.authority } == null
            }
            .onFailure {
                Log.w(
                    ContentPreviewUi.TAG,
                    "Failed to check URI authorities; no payload toggling",
                    it
                )
            }
            .getOrDefault(false)
    }

    /**
     * The first shared URI's metadata. This call wait's for the data to be loaded and falls back to
     * a crude value if the data is not loaded within a time limit.
     */
    open val firstFileInfo: FileInfo? by lazy {
        runTracing("first-uri-metadata") {
            records.firstOrNull()?.let { record ->
                val builder = FileInfo.Builder(record.uri)
                try {
                    runBlocking(scope.coroutineContext) {
                        withTimeoutOrNull(TIMEOUT_MS) {
                            scope.async { builder.readFromRecord(record) }.await()
                        }
                    }
                } catch (e: CancellationException) {
                    Log.w(
                        ContentPreviewUi.TAG,
                        "An attempt to read first file info from a cancelled scope",
                        e
                    )
                }
                builder.build()
            }
        }
    }

    private fun FileInfo.Builder.readFromRecord(record: UriRecord): FileInfo.Builder {
        withMimeType(record.mimeType)
        val previewUri =
            when {
                record.isImageType || record.supportsImageType || record.supportsThumbnail ->
                    record.uri
                else -> record.iconUri
            }
        withPreviewUri(previewUri)
        return this
    }

    /**
     * Returns a title for the first shared URI which is read from URI metadata or, if the metadata
     * is not provided, derived from the URI.
     */
    @Throws(IndexOutOfBoundsException::class)
    fun getFirstFileName(callerScope: CoroutineScope, callback: Consumer<String>) {
        if (records.isEmpty()) {
            throw IndexOutOfBoundsException("There are no shared URIs")
        }
        callerScope.launch {
            val result = scope.async { getFirstFileName() }.await()
            callback.accept(result)
        }
    }

    @Throws(IndexOutOfBoundsException::class)
    private fun getFirstFileName(): String {
        if (records.isEmpty()) throw IndexOutOfBoundsException("There are no shared URIs")

        val record = records[0]
        return if (TextUtils.isEmpty(record.title)) getFileName(record.uri) else record.title
    }

    @ContentPreviewType
    private suspend fun loadPreviewType(): Int {
        // Execute [ContentResolver#getType()] calls sequentially as the method contains a timeout
        // logic for the actual [ContentProvider#getType] call. Thus it is possible for one getType
        // call's timeout work against other concurrent getType calls e.g. when a two concurrent
        // calls on the caller side are scheduled on the same thread on the callee side.
        records
            .firstOrNull { it.isImageType }
            ?.run {
                return CONTENT_PREVIEW_IMAGE
            }

        val resultDeferred = CompletableDeferred<Int>()
        return coroutineScope {
            val job = launch {
                coroutineScope {
                    val nextIndex = AtomicInteger(0)
                    repeat(4) {
                        launch {
                            while (isActive) {
                                val i = nextIndex.getAndIncrement()
                                if (i >= records.size) break
                                val hasPreview =
                                    with(records[i]) {
                                        supportsImageType || supportsThumbnail || iconUri != null
                                    }
                                if (hasPreview) {
                                    resultDeferred.complete(CONTENT_PREVIEW_IMAGE)
                                    break
                                }
                            }
                        }
                    }
                }
                resultDeferred.complete(CONTENT_PREVIEW_FILE)
            }
            resultDeferred.await().also { job.cancel() }
        }
    }

    /**
     * Provides a lazy evaluation and caches results of [ContentInterface.getType],
     * [ContentInterface.getStreamTypes], and [ContentInterface.query] methods for the given [uri].
     */
    private inner class UriRecord(val uri: Uri) {
        val mimeType: String? by lazy { contentResolver.getTypeSafe(uri) }
        val isImageType: Boolean
            get() = typeClassifier.isImageType(mimeType)
        val supportsImageType: Boolean by lazy {
            contentResolver.getStreamTypesSafe(uri).firstOrNull(typeClassifier::isImageType) != null
        }
        val supportsThumbnail: Boolean
            get() = query.supportsThumbnail
        val title: String
            get() = query.title
        val iconUri: Uri?
            get() = query.iconUri

        private val query by lazy { readQueryResult() }

        private fun readQueryResult(): QueryResult =
            // TODO: rewrite using methods from UiMetadataHelpers.kt
            contentResolver.querySafe(uri, METADATA_COLUMNS)?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null

                var flagColIdx = -1
                var displayIconUriColIdx = -1
                var nameColIndex = -1
                var titleColIndex = -1
                // TODO: double-check why Cursor#getColumnInded didn't work
                cursor.columnNames.forEachIndexed { i, columnName ->
                    when (columnName) {
                        DocumentsContract.Document.COLUMN_FLAGS -> flagColIdx = i
                        MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI -> displayIconUriColIdx = i
                        OpenableColumns.DISPLAY_NAME -> nameColIndex = i
                        Downloads.Impl.COLUMN_TITLE -> titleColIndex = i
                    }
                }

                val supportsThumbnail =
                    flagColIdx >= 0 &&
                        ((cursor.getInt(flagColIdx) and FLAG_SUPPORTS_THUMBNAIL) != 0)

                var title = ""
                if (nameColIndex >= 0) {
                    title = cursor.getString(nameColIndex) ?: ""
                }
                if (TextUtils.isEmpty(title) && titleColIndex >= 0) {
                    title = cursor.getString(titleColIndex) ?: ""
                }

                val iconUri =
                    if (displayIconUriColIdx >= 0) {
                        cursor.getString(displayIconUriColIdx)?.let(Uri::parse)
                    } else {
                        null
                    }

                QueryResult(supportsThumbnail, title, iconUri)
            }
                ?: QueryResult()
    }

    private class QueryResult(
        val supportsThumbnail: Boolean = false,
        val title: String = "",
        val iconUri: Uri? = null
    )
}

private val Intent.isSend: Boolean
    get() =
        action.let { action ->
            Intent.ACTION_SEND == action || Intent.ACTION_SEND_MULTIPLE == action
        }

private val Intent.contentUris: ArrayList<Uri>
    get() =
        ArrayList<Uri>().also { uris ->
            if (Intent.ACTION_SEND == action) {
                getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                    ?.takeIf { it.ownedByCurrentUser }
                    ?.let { uris.add(it) }
            } else {
                getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.fold(uris) { accumulator, uri
                    ->
                    if (uri.ownedByCurrentUser) {
                        accumulator.add(uri)
                    }
                    accumulator
                }
            }
        }

private fun getFileName(uri: Uri): String {
    val fileName = uri.path ?: return ""
    val index = fileName.lastIndexOf('/')
    return if (index < 0) {
        fileName
    } else {
        fileName.substring(index + 1)
    }
}
