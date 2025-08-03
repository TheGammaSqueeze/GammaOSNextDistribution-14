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

import android.net.Uri
import androidx.annotation.VisibleForTesting

class FileInfo private constructor(val uri: Uri, val previewUri: Uri?, val mimeType: String?) {
    @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
    class Builder(val uri: Uri) {
        var previewUri: Uri? = null
            private set
        var mimeType: String? = null
            private set

        @Synchronized fun withPreviewUri(uri: Uri?): Builder = apply { previewUri = uri }

        @Synchronized
        fun withMimeType(mimeType: String?): Builder = apply { this.mimeType = mimeType }

        @Synchronized fun build(): FileInfo = FileInfo(uri, previewUri, mimeType)
    }
}
