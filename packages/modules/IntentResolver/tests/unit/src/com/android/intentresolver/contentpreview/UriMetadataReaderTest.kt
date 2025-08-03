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
import android.database.MatrixCursor
import android.media.MediaMetadata
import android.net.Uri
import android.provider.DocumentsContract
import com.android.intentresolver.any
import com.android.intentresolver.anyOrNull
import com.android.intentresolver.eq
import com.android.intentresolver.mock
import com.android.intentresolver.whenever
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

class UriMetadataReaderTest {
    private val uri = Uri.parse("content://org.pkg.app/item")
    private val contentResolver = mock<ContentInterface>()

    @Test
    fun testImageUri() {
        val mimeType = "image/png"
        whenever(contentResolver.getType(uri)).thenReturn(mimeType)
        val testSubject = UriMetadataReader(contentResolver, DefaultMimeTypeClassifier)

        testSubject.getMetadata(uri).let { fileInfo ->
            assertWithMessage("Wrong uri").that(fileInfo.uri).isEqualTo(uri)
            assertWithMessage("Wrong mime type").that(fileInfo.mimeType).isEqualTo(mimeType)
            assertWithMessage("Wrong preview URI").that(fileInfo.previewUri).isEqualTo(uri)
        }
    }

    @Test
    fun testFileUriWithImageTypeSupport() {
        val mimeType = "application/pdf"
        val imageType = "image/png"
        whenever(contentResolver.getType(uri)).thenReturn(mimeType)
        whenever(contentResolver.getStreamTypes(eq(uri), any())).thenReturn(arrayOf(imageType))
        val testSubject = UriMetadataReader(contentResolver, DefaultMimeTypeClassifier)

        testSubject.getMetadata(uri).let { fileInfo ->
            assertWithMessage("Wrong uri").that(fileInfo.uri).isEqualTo(uri)
            assertWithMessage("Wrong mime type").that(fileInfo.mimeType).isEqualTo(mimeType)
            assertWithMessage("Wrong preview URI").that(fileInfo.previewUri).isEqualTo(uri)
        }
    }

    @Test
    fun testFileUriWithThumbnailSupport() {
        val mimeType = "application/pdf"
        whenever(contentResolver.getType(uri)).thenReturn(mimeType)
        val columns = arrayOf(DocumentsContract.Document.COLUMN_FLAGS)
        whenever(contentResolver.query(eq(uri), eq(columns), anyOrNull(), anyOrNull()))
            .thenReturn(
                MatrixCursor(columns).apply {
                    addRow(arrayOf(DocumentsContract.Document.FLAG_SUPPORTS_THUMBNAIL))
                }
            )
        val testSubject = UriMetadataReader(contentResolver, DefaultMimeTypeClassifier)

        testSubject.getMetadata(uri).let { fileInfo ->
            assertWithMessage("Wrong uri").that(fileInfo.uri).isEqualTo(uri)
            assertWithMessage("Wrong mime type").that(fileInfo.mimeType).isEqualTo(mimeType)
            assertWithMessage("Wrong preview URI").that(fileInfo.previewUri).isEqualTo(uri)
        }
    }

    @Test
    fun testFileUriWithPreviewUri() {
        val mimeType = "application/pdf"
        val previewUri = uri.buildUpon().appendQueryParameter("preview", null).build()
        whenever(contentResolver.getType(uri)).thenReturn(mimeType)
        val columns = arrayOf(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI)
        whenever(contentResolver.query(eq(uri), eq(columns), anyOrNull(), anyOrNull()))
            .thenReturn(MatrixCursor(columns).apply { addRow(arrayOf(previewUri.toString())) })
        val testSubject = UriMetadataReader(contentResolver, DefaultMimeTypeClassifier)

        testSubject.getMetadata(uri).let { fileInfo ->
            assertWithMessage("Wrong uri").that(fileInfo.uri).isEqualTo(uri)
            assertWithMessage("Wrong mime type").that(fileInfo.mimeType).isEqualTo(mimeType)
            assertWithMessage("Wrong preview URI").that(fileInfo.previewUri).isEqualTo(previewUri)
        }
    }
}
