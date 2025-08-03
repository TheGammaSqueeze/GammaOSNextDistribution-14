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

import android.content.Intent
import android.net.Uri
import android.platform.test.flag.junit.CheckFlagsRule
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import com.android.intentresolver.ContentTypeHint
import com.android.intentresolver.TestPreviewImageLoader
import com.android.intentresolver.contentpreview.ChooserContentPreviewUi.ActionFactory
import com.android.intentresolver.mock
import com.android.intentresolver.whenever
import com.android.intentresolver.widget.ActionRow
import com.android.intentresolver.widget.ImagePreviewView
import com.google.common.truth.Truth.assertThat
import java.util.function.Consumer
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify

class ChooserContentPreviewUiTest {
    private val testScope = TestScope(EmptyCoroutineContext + UnconfinedTestDispatcher())
    private val previewData = mock<PreviewDataProvider>()
    private val headlineGenerator = mock<HeadlineGenerator>()
    private val imageLoader = TestPreviewImageLoader(emptyMap())
    private val testMetadataText: CharSequence = "Test metadata text"
    private val actionFactory =
        object : ActionFactory {
            override fun getCopyButtonRunnable(): Runnable? = null

            override fun getEditButtonRunnable(): Runnable? = null

            override fun createCustomActions(): List<ActionRow.Action> = emptyList()

            override fun getModifyShareAction(): ActionRow.Action? = null

            override fun getExcludeSharedTextAction(): Consumer<Boolean> = Consumer<Boolean> {}
        }
    private val transitionCallback = mock<ImagePreviewView.TransitionElementStatusCallback>()
    @get:Rule val checkFlagsRule: CheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    private fun createContentPreviewUi(
        targetIntent: Intent,
        isPayloadTogglingEnabled: Boolean = false
    ) =
        ChooserContentPreviewUi(
            testScope,
            previewData,
            targetIntent,
            imageLoader,
            actionFactory,
            transitionCallback,
            headlineGenerator,
            ContentTypeHint.NONE,
            testMetadataText,
            isPayloadTogglingEnabled,
        )

    @Test
    fun test_textPreviewType_useTextPreviewUi() {
        whenever(previewData.previewType).thenReturn(ContentPreviewType.CONTENT_PREVIEW_TEXT)
        val testSubject = createContentPreviewUi(targetIntent = Intent(Intent.ACTION_VIEW))

        assertThat(testSubject.preferredContentPreview)
            .isEqualTo(ContentPreviewType.CONTENT_PREVIEW_TEXT)
        assertThat(testSubject.mContentPreviewUi).isInstanceOf(TextContentPreviewUi::class.java)
        verify(transitionCallback, times(1)).onAllTransitionElementsReady()
    }

    @Test
    fun test_filePreviewType_useFilePreviewUi() {
        whenever(previewData.previewType).thenReturn(ContentPreviewType.CONTENT_PREVIEW_FILE)
        val testSubject = createContentPreviewUi(targetIntent = Intent(Intent.ACTION_SEND))
        assertThat(testSubject.preferredContentPreview)
            .isEqualTo(ContentPreviewType.CONTENT_PREVIEW_FILE)
        assertThat(testSubject.mContentPreviewUi).isInstanceOf(FileContentPreviewUi::class.java)
        verify(transitionCallback, times(1)).onAllTransitionElementsReady()
    }

    @Test
    fun test_imagePreviewTypeWithText_useFilePlusTextPreviewUi() {
        val uri = Uri.parse("content://org.pkg.app/img.png")
        whenever(previewData.previewType).thenReturn(ContentPreviewType.CONTENT_PREVIEW_IMAGE)
        whenever(previewData.uriCount).thenReturn(2)
        whenever(previewData.firstFileInfo)
            .thenReturn(FileInfo.Builder(uri).withPreviewUri(uri).withMimeType("image/png").build())
        whenever(previewData.imagePreviewFileInfoFlow).thenReturn(MutableSharedFlow())
        val testSubject =
            createContentPreviewUi(
                targetIntent =
                    Intent(Intent.ACTION_SEND).apply { putExtra(Intent.EXTRA_TEXT, "Shared text") }
            )
        assertThat(testSubject.mContentPreviewUi)
            .isInstanceOf(FilesPlusTextContentPreviewUi::class.java)
        verify(previewData, times(1)).imagePreviewFileInfoFlow
        verify(transitionCallback, times(1)).onAllTransitionElementsReady()
    }

    @Test
    fun test_imagePreviewTypeWithoutText_useImagePreviewUi() {
        val uri = Uri.parse("content://org.pkg.app/img.png")
        whenever(previewData.previewType).thenReturn(ContentPreviewType.CONTENT_PREVIEW_IMAGE)
        whenever(previewData.uriCount).thenReturn(2)
        whenever(previewData.firstFileInfo)
            .thenReturn(FileInfo.Builder(uri).withPreviewUri(uri).withMimeType("image/png").build())
        whenever(previewData.imagePreviewFileInfoFlow).thenReturn(MutableSharedFlow())
        val testSubject = createContentPreviewUi(targetIntent = Intent(Intent.ACTION_SEND))
        assertThat(testSubject.preferredContentPreview)
            .isEqualTo(ContentPreviewType.CONTENT_PREVIEW_IMAGE)
        assertThat(testSubject.mContentPreviewUi).isInstanceOf(UnifiedContentPreviewUi::class.java)
        verify(previewData, times(1)).imagePreviewFileInfoFlow
        verify(transitionCallback, never()).onAllTransitionElementsReady()
    }

    @Test
    fun test_imagePayloadSelectionTypeWithEnabledFlag_usePayloadSelectionPreviewUi() {
        // Event if we returned wrong type due to a bug, we should not use payload selection UI
        val uri = Uri.parse("content://org.pkg.app/img.png")
        whenever(previewData.previewType)
            .thenReturn(ContentPreviewType.CONTENT_PREVIEW_PAYLOAD_SELECTION)
        whenever(previewData.uriCount).thenReturn(2)
        whenever(previewData.firstFileInfo)
            .thenReturn(FileInfo.Builder(uri).withPreviewUri(uri).withMimeType("image/png").build())
        whenever(previewData.imagePreviewFileInfoFlow).thenReturn(MutableSharedFlow())
        val testSubject =
            createContentPreviewUi(
                targetIntent = Intent(Intent.ACTION_SEND),
                isPayloadTogglingEnabled = true
            )
        assertThat(testSubject.mContentPreviewUi)
            .isInstanceOf(ShareouselContentPreviewUi::class.java)
    }
}
