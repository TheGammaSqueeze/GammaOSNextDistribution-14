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

import android.graphics.Bitmap
import android.net.Uri
import java.util.function.Consumer
import kotlinx.coroutines.CoroutineScope

/** A content preview image loader. */
interface ImageLoader : suspend (Uri) -> Bitmap?, suspend (Uri, Boolean) -> Bitmap? {
    /**
     * Load preview image asynchronously; caching is allowed.
     *
     * @param uri content URI
     * @param callback a callback that will be invoked with the loaded image or null if loading has
     *   failed.
     */
    fun loadImage(callerScope: CoroutineScope, uri: Uri, callback: Consumer<Bitmap?>)

    /** Prepopulate the image loader cache. */
    fun prePopulate(uris: List<Uri>)

    /** Load preview image; caching is allowed. */
    override suspend fun invoke(uri: Uri) = invoke(uri, true)

    /**
     * Load preview image.
     *
     * @param uri content URI
     * @param caching indicates if the loaded image could be cached.
     */
    override suspend fun invoke(uri: Uri, caching: Boolean): Bitmap?
}
