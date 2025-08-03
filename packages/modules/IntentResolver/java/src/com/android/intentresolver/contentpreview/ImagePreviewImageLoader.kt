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

import android.content.ContentResolver
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import android.util.Size
import androidx.annotation.GuardedBy
import androidx.annotation.VisibleForTesting
import androidx.collection.LruCache
import java.util.function.Consumer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore

private const val TAG = "ImagePreviewImageLoader"

/**
 * Implements preview image loading for the content preview UI. Provides requests deduplication,
 * image caching, and a limit on the number of parallel loadings.
 */
@VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
class ImagePreviewImageLoader
@VisibleForTesting
constructor(
    private val scope: CoroutineScope,
    thumbnailSize: Int,
    private val contentResolver: ContentResolver,
    cacheSize: Int,
    // TODO: consider providing a scope with the dispatcher configured with
    //  [CoroutineDispatcher#limitedParallelism] instead
    private val contentResolverSemaphore: Semaphore,
) : ImageLoader {

    constructor(
        scope: CoroutineScope,
        thumbnailSize: Int,
        contentResolver: ContentResolver,
        cacheSize: Int,
        maxSimultaneousRequests: Int = 4
    ) : this(scope, thumbnailSize, contentResolver, cacheSize, Semaphore(maxSimultaneousRequests))

    private val thumbnailSize: Size = Size(thumbnailSize, thumbnailSize)

    private val lock = Any()
    @GuardedBy("lock") private val cache = LruCache<Uri, RequestRecord>(cacheSize)
    @GuardedBy("lock") private val runningRequests = HashMap<Uri, RequestRecord>()

    override suspend fun invoke(uri: Uri, caching: Boolean): Bitmap? = loadImageAsync(uri, caching)

    override fun loadImage(callerScope: CoroutineScope, uri: Uri, callback: Consumer<Bitmap?>) {
        callerScope.launch {
            val image = loadImageAsync(uri, caching = true)
            if (isActive) {
                callback.accept(image)
            }
        }
    }

    override fun prePopulate(uris: List<Uri>) {
        uris.asSequence().take(cache.maxSize()).forEach { uri ->
            scope.launch { loadImageAsync(uri, caching = true) }
        }
    }

    private suspend fun loadImageAsync(uri: Uri, caching: Boolean): Bitmap? {
        return getRequestDeferred(uri, caching).await()
    }

    private fun getRequestDeferred(uri: Uri, caching: Boolean): Deferred<Bitmap?> {
        var shouldLaunchImageLoading = false
        val request =
            synchronized(lock) {
                cache[uri]
                    ?: runningRequests
                        .getOrPut(uri) {
                            shouldLaunchImageLoading = true
                            RequestRecord(uri, CompletableDeferred(), caching)
                        }
                        .apply { this.caching = this.caching || caching }
            }
        if (shouldLaunchImageLoading) {
            request.loadBitmapAsync()
        }
        return request.deferred
    }

    private fun RequestRecord.loadBitmapAsync() {
        scope
            .launch { loadBitmap() }
            .invokeOnCompletion { cause ->
                if (cause is CancellationException) {
                    cancel()
                }
            }
    }

    private suspend fun RequestRecord.loadBitmap() {
        contentResolverSemaphore.acquire()
        val bitmap =
            try {
                contentResolver.loadThumbnail(uri, thumbnailSize, null)
            } catch (t: Throwable) {
                Log.d(TAG, "failed to load $uri preview", t)
                null
            } finally {
                contentResolverSemaphore.release()
            }
        complete(bitmap)
    }

    private fun RequestRecord.cancel() {
        synchronized(lock) {
            runningRequests.remove(uri)
            deferred.cancel()
        }
    }

    private fun RequestRecord.complete(bitmap: Bitmap?) {
        deferred.complete(bitmap)
        synchronized(lock) {
            runningRequests.remove(uri)
            if (bitmap != null && caching) {
                cache.put(uri, this)
            }
        }
    }

    private class RequestRecord(
        val uri: Uri,
        val deferred: CompletableDeferred<Bitmap?>,
        @GuardedBy("lock") var caching: Boolean
    )
}
