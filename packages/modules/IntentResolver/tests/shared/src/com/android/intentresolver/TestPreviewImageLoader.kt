/*
 * Copyright (C) 2022 The Android Open Source Project
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

import android.graphics.Bitmap
import android.net.Uri
import com.android.intentresolver.contentpreview.ImageLoader
import java.util.function.Consumer
import kotlinx.coroutines.CoroutineScope

class TestPreviewImageLoader(private val bitmaps: Map<Uri, Bitmap>) : ImageLoader {
    override fun loadImage(callerScope: CoroutineScope, uri: Uri, callback: Consumer<Bitmap?>) {
        callback.accept(bitmaps[uri])
    }

    override suspend fun invoke(uri: Uri, caching: Boolean): Bitmap? = bitmaps[uri]

    override fun prePopulate(uris: List<Uri>) = Unit
}
