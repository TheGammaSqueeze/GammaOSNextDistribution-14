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
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.IdRes
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import com.android.intentresolver.R
import com.android.intentresolver.mock
import com.android.intentresolver.whenever
import com.android.intentresolver.widget.ImagePreviewView.TransitionElementStatusCallback
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.times
import org.mockito.Mockito.verify

private const val IMAGE_HEADLINE = "Image Headline"
private const val VIDEO_HEADLINE = "Video Headline"
private const val FILES_HEADLINE = "Files Headline"

@RunWith(AndroidJUnit4::class)
class UnifiedContentPreviewUiTest {
    private val testScope = TestScope(EmptyCoroutineContext + UnconfinedTestDispatcher())
    private val actionFactory =
        mock<ChooserContentPreviewUi.ActionFactory> {
            whenever(createCustomActions()).thenReturn(emptyList())
        }
    private val imageLoader = mock<ImageLoader>()
    private val headlineGenerator =
        mock<HeadlineGenerator> {
            whenever(getImagesHeadline(anyInt())).thenReturn(IMAGE_HEADLINE)
            whenever(getVideosHeadline(anyInt())).thenReturn(VIDEO_HEADLINE)
            whenever(getFilesHeadline(anyInt())).thenReturn(FILES_HEADLINE)
        }
    private val testMetadataText: CharSequence = "Test metadata text"

    private val context
        get() = getInstrumentation().context

    @Test
    fun test_displayImagesWithoutUriMetadata_showImagesHeadline() {
        testLoadingHeadline("image/*", files = null) { previewView ->
            verify(headlineGenerator, times(1)).getImagesHeadline(2)
            verifyPreviewHeadline(previewView, IMAGE_HEADLINE)
            verifyPreviewMetadata(previewView, testMetadataText)
        }
    }

    @Test
    fun test_displayImagesWithoutUriMetadataExternalHeader_showImagesHeadline() {
        testLoadingExternalHeadline("image/*", files = null) { externalHeaderView ->
            verify(headlineGenerator, times(1)).getImagesHeadline(2)
            verifyPreviewHeadline(externalHeaderView, IMAGE_HEADLINE)
            verifyPreviewMetadata(externalHeaderView, testMetadataText)
        }
    }

    @Test
    fun test_displayVideosWithoutUriMetadata_showImagesHeadline() {
        testLoadingHeadline("video/*", files = null) { previewView ->
            verify(headlineGenerator, times(1)).getVideosHeadline(2)
            verifyPreviewHeadline(previewView, VIDEO_HEADLINE)
            verifyPreviewMetadata(previewView, testMetadataText)
        }
    }

    @Test
    fun test_displayVideosWithoutUriMetadataExternalHeader_showImagesHeadline() {
        testLoadingExternalHeadline("video/*", files = null) { externalHeaderView ->
            verify(headlineGenerator, times(1)).getVideosHeadline(2)
            verifyPreviewHeadline(externalHeaderView, VIDEO_HEADLINE)
            verifyPreviewMetadata(externalHeaderView, testMetadataText)
        }
    }

    @Test
    fun test_displayDocumentsWithoutUriMetadata_showImagesHeadline() {
        testLoadingHeadline("application/pdf", files = null) { previewView ->
            verify(headlineGenerator, times(1)).getFilesHeadline(2)
            verifyPreviewHeadline(previewView, FILES_HEADLINE)
            verifyPreviewMetadata(previewView, testMetadataText)
        }
    }

    @Test
    fun test_displayDocumentsWithoutUriMetadataExternalHeader_showImagesHeadline() {
        testLoadingExternalHeadline("application/pdf", files = null) { externalHeaderView ->
            verify(headlineGenerator, times(1)).getFilesHeadline(2)
            verifyPreviewHeadline(externalHeaderView, FILES_HEADLINE)
            verifyPreviewMetadata(externalHeaderView, testMetadataText)
        }
    }

    @Test
    fun test_displayMixedContentWithoutUriMetadata_showImagesHeadline() {
        testLoadingHeadline("*/*", files = null) { previewView ->
            verify(headlineGenerator, times(1)).getFilesHeadline(2)
            verifyPreviewHeadline(previewView, FILES_HEADLINE)
            verifyPreviewMetadata(previewView, testMetadataText)
        }
    }

    @Test
    fun test_displayMixedContentWithoutUriMetadataExternalHeader_showImagesHeadline() {
        testLoadingExternalHeadline("*/*", files = null) { externalHeader ->
            verify(headlineGenerator, times(1)).getFilesHeadline(2)
            verifyPreviewHeadline(externalHeader, FILES_HEADLINE)
            verifyPreviewMetadata(externalHeader, testMetadataText)
        }
    }

    @Test
    fun test_displayImagesWithUriMetadataSet_showImagesHeadline() {
        val uri = Uri.parse("content://pkg.app/image.png")
        val files =
            listOf(
                FileInfo.Builder(uri).withMimeType("image/png").build(),
                FileInfo.Builder(uri).withMimeType("image/jpeg").build(),
            )
        testLoadingHeadline("image/*", files) { preivewView ->
            verify(headlineGenerator, times(1)).getImagesHeadline(2)
            verifyPreviewHeadline(preivewView, IMAGE_HEADLINE)
        }
    }

    @Test
    fun test_displayImagesWithUriMetadataSetExternalHeader_showImagesHeadline() {
        val uri = Uri.parse("content://pkg.app/image.png")
        val files =
            listOf(
                FileInfo.Builder(uri).withMimeType("image/png").build(),
                FileInfo.Builder(uri).withMimeType("image/jpeg").build(),
            )
        testLoadingExternalHeadline("image/*", files) { externalHeader ->
            verify(headlineGenerator, times(1)).getImagesHeadline(2)
            verifyPreviewHeadline(externalHeader, IMAGE_HEADLINE)
        }
    }

    @Test
    fun test_displayVideosWithUriMetadataSet_showImagesHeadline() {
        val uri = Uri.parse("content://pkg.app/image.png")
        val files =
            listOf(
                FileInfo.Builder(uri).withMimeType("video/mp4").build(),
                FileInfo.Builder(uri).withMimeType("video/mp4").build(),
            )
        testLoadingHeadline("video/*", files) { previewView ->
            verify(headlineGenerator, times(1)).getVideosHeadline(2)
            verifyPreviewHeadline(previewView, VIDEO_HEADLINE)
        }
    }

    @Test
    fun test_displayVideosWithUriMetadataSetExternalHeader_showImagesHeadline() {
        val uri = Uri.parse("content://pkg.app/image.png")
        val files =
            listOf(
                FileInfo.Builder(uri).withMimeType("video/mp4").build(),
                FileInfo.Builder(uri).withMimeType("video/mp4").build(),
            )
        testLoadingExternalHeadline("video/*", files) { externalHeader ->
            verify(headlineGenerator, times(1)).getVideosHeadline(2)
            verifyPreviewHeadline(externalHeader, VIDEO_HEADLINE)
        }
    }

    @Test
    fun test_displayImagesAndVideosWithUriMetadataSet_showImagesHeadline() {
        val uri = Uri.parse("content://pkg.app/image.png")
        val files =
            listOf(
                FileInfo.Builder(uri).withMimeType("image/png").build(),
                FileInfo.Builder(uri).withMimeType("video/mp4").build(),
            )
        testLoadingHeadline("*/*", files) { previewView ->
            verify(headlineGenerator, times(1)).getFilesHeadline(2)
            verifyPreviewHeadline(previewView, FILES_HEADLINE)
        }
    }

    @Test
    fun test_displayImagesAndVideosWithUriMetadataSetExternalHeader_showImagesHeadline() {
        val uri = Uri.parse("content://pkg.app/image.png")
        val files =
            listOf(
                FileInfo.Builder(uri).withMimeType("image/png").build(),
                FileInfo.Builder(uri).withMimeType("video/mp4").build(),
            )
        testLoadingExternalHeadline("*/*", files) { externalHeader ->
            verify(headlineGenerator, times(1)).getFilesHeadline(2)
            verifyPreviewHeadline(externalHeader, FILES_HEADLINE)
        }
    }

    @Test
    fun test_displayDocumentsWithUriMetadataSet_showImagesHeadline() {
        val uri = Uri.parse("content://pkg.app/image.png")
        val files =
            listOf(
                FileInfo.Builder(uri).withMimeType("application/pdf").build(),
                FileInfo.Builder(uri).withMimeType("application/pdf").build(),
            )
        testLoadingHeadline("application/pdf", files) { previewView ->
            verify(headlineGenerator, times(1)).getFilesHeadline(2)
            verifyPreviewHeadline(previewView, FILES_HEADLINE)
        }
    }

    @Test
    fun test_displayDocumentsWithUriMetadataSetExternalHeader_showImagesHeadline() {
        val uri = Uri.parse("content://pkg.app/image.png")
        val files =
            listOf(
                FileInfo.Builder(uri).withMimeType("application/pdf").build(),
                FileInfo.Builder(uri).withMimeType("application/pdf").build(),
            )
        testLoadingExternalHeadline("application/pdf", files) { externalHeader ->
            verify(headlineGenerator, times(1)).getFilesHeadline(2)
            verifyPreviewHeadline(externalHeader, FILES_HEADLINE)
        }
    }

    private fun testLoadingHeadline(
        intentMimeType: String,
        files: List<FileInfo>?,
        verificationBlock: (ViewGroup?) -> Unit,
    ) {
        testScope.runTest {
            val endMarker = FileInfo.Builder(Uri.EMPTY).build()
            val emptySourceFlow = MutableSharedFlow<FileInfo>(replay = 1)
            val testSubject =
                UnifiedContentPreviewUi(
                    testScope,
                    /*isSingleImage=*/ false,
                    intentMimeType,
                    actionFactory,
                    imageLoader,
                    DefaultMimeTypeClassifier,
                    object : TransitionElementStatusCallback {
                        override fun onTransitionElementReady(name: String) = Unit
                        override fun onAllTransitionElementsReady() = Unit
                    },
                    files?.let { it.asFlow() } ?: emptySourceFlow.takeWhile { it !== endMarker },
                    /*itemCount=*/ 2,
                    headlineGenerator,
                    testMetadataText,
                )
            val layoutInflater = LayoutInflater.from(context)
            val gridLayout = layoutInflater.inflate(R.layout.chooser_grid, null, false) as ViewGroup

            val previewView =
                testSubject.display(
                    context.resources,
                    LayoutInflater.from(context),
                    gridLayout,
                    /*headlineViewParent=*/ null
                )
            emptySourceFlow.tryEmit(endMarker)

            verificationBlock(previewView)
        }
    }

    private fun testLoadingExternalHeadline(
        intentMimeType: String,
        files: List<FileInfo>?,
        verificationBlock: (View?) -> Unit,
    ) {
        testScope.runTest {
            val endMarker = FileInfo.Builder(Uri.EMPTY).build()
            val emptySourceFlow = MutableSharedFlow<FileInfo>(replay = 1)
            val testSubject =
                UnifiedContentPreviewUi(
                    testScope,
                    /*isSingleImage=*/ false,
                    intentMimeType,
                    actionFactory,
                    imageLoader,
                    DefaultMimeTypeClassifier,
                    object : TransitionElementStatusCallback {
                        override fun onTransitionElementReady(name: String) = Unit
                        override fun onAllTransitionElementsReady() = Unit
                    },
                    files?.let { it.asFlow() } ?: emptySourceFlow.takeWhile { it !== endMarker },
                    /*itemCount=*/ 2,
                    headlineGenerator,
                    testMetadataText,
                )
            val layoutInflater = LayoutInflater.from(context)
            val gridLayout =
                layoutInflater.inflate(R.layout.chooser_grid_scrollable_preview, null, false)
                    as ViewGroup
            val externalHeaderView =
                gridLayout.requireViewById<View>(R.id.chooser_headline_row_container)

            assertWithMessage("External headline should not be inflated by default")
                .that(externalHeaderView.findViewById<View>(R.id.headline))
                .isNull()

            val previewView =
                testSubject.display(
                    context.resources,
                    LayoutInflater.from(context),
                    gridLayout,
                    externalHeaderView,
                )

            emptySourceFlow.tryEmit(endMarker)

            verifyInternalHeadlineAbsence(previewView)
            verifyInternalMetadataAbsence(previewView)
            verificationBlock(externalHeaderView)
        }
    }

    private fun verifyTextViewText(
        viewParent: View?,
        @IdRes textViewResId: Int,
        expectedText: CharSequence,
    ) {
        assertThat(viewParent).isNotNull()
        val textView = viewParent?.findViewById<TextView>(textViewResId)
        assertThat(textView).isNotNull()
        assertThat(textView?.text).isEqualTo(expectedText)
    }

    private fun verifyPreviewHeadline(headerViewParent: View?, expectedText: String) {
        verifyTextViewText(headerViewParent, R.id.headline, expectedText)
    }

    private fun verifyPreviewMetadata(headerViewParent: View?, expectedText: CharSequence) {
        verifyTextViewText(headerViewParent, R.id.metadata, expectedText)
    }

    private fun verifyInternalHeadlineAbsence(previewView: ViewGroup?) {
        assertWithMessage("Preview parent should not be null").that(previewView).isNotNull()
        assertWithMessage(
                "Preview headline should not be inflated when an external headline is used"
            )
            .that(previewView?.findViewById<View>(R.id.headline))
            .isNull()
    }
    private fun verifyInternalMetadataAbsence(previewView: ViewGroup?) {
        assertWithMessage("Preview parent should not be null").that(previewView).isNotNull()
        assertWithMessage(
                "Preview metadata should not be inflated when an external metadata is used"
            )
            .that(previewView?.findViewById<View>(R.id.metadata))
            .isNull()
    }
}
