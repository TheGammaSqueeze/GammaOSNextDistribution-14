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
import android.media.MediaMetadata
import android.net.Uri
import android.provider.DocumentsContract

class UriMetadataReader(
    private val contentResolver: ContentInterface,
    private val typeClassifier: MimeTypeClassifier,
) : (Uri) -> FileInfo {
    fun getMetadata(uri: Uri): FileInfo {
        val builder = FileInfo.Builder(uri)
        val mimeType = contentResolver.getTypeSafe(uri)
        builder.withMimeType(mimeType)
        if (
            typeClassifier.isImageType(mimeType) ||
                contentResolver.supportsImageType(uri) ||
                contentResolver.supportsThumbnail(uri)
        ) {
            builder.withPreviewUri(uri)
            return builder.build()
        }
        val previewUri = contentResolver.readPreviewUri(uri)
        if (previewUri != null) {
            builder.withPreviewUri(previewUri)
        }
        return builder.build()
    }

    override fun invoke(uri: Uri): FileInfo = getMetadata(uri)

    private fun ContentInterface.supportsImageType(uri: Uri): Boolean =
        getStreamTypesSafe(uri).firstOrNull { typeClassifier.isImageType(it) } != null

    private fun ContentInterface.supportsThumbnail(uri: Uri): Boolean =
        querySafe(uri, arrayOf(DocumentsContract.Document.COLUMN_FLAGS))?.use { cursor ->
            cursor.moveToFirst() && cursor.readSupportsThumbnail()
        }
            ?: false

    private fun ContentInterface.readPreviewUri(uri: Uri): Uri? =
        querySafe(uri, arrayOf(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI))?.use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.readPreviewUri()
            } else {
                null
            }
        }
}
