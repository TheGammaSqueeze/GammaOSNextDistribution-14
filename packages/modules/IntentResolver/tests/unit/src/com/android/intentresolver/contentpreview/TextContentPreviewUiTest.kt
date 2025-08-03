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

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.android.intentresolver.ContentTypeHint
import com.android.intentresolver.R
import com.android.intentresolver.mock
import com.android.intentresolver.whenever
import com.android.intentresolver.widget.ActionRow
import com.google.common.truth.Truth.assertThat
import java.util.function.Consumer
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TextContentPreviewUiTest {
    private val text = "Shared Text"
    private val title = "Preview Title"
    private val albumHeadline = "Album headline"
    private val testScope = TestScope(EmptyCoroutineContext + UnconfinedTestDispatcher())
    private val actionFactory =
        object : ChooserContentPreviewUi.ActionFactory {
            override fun getEditButtonRunnable(): Runnable? = null
            override fun getCopyButtonRunnable(): Runnable? = null
            override fun createCustomActions(): List<ActionRow.Action> = emptyList()
            override fun getModifyShareAction(): ActionRow.Action? = null
            override fun getExcludeSharedTextAction(): Consumer<Boolean> = Consumer<Boolean> {}
        }
    private val imageLoader = mock<ImageLoader>()
    private val headlineGenerator =
        mock<HeadlineGenerator> {
            whenever(getTextHeadline(text)).thenReturn(text)
            whenever(getAlbumHeadline()).thenReturn(albumHeadline)
        }
    private val testMetadataText: CharSequence = "Test metadata text"

    private val context
        get() = InstrumentationRegistry.getInstrumentation().context

    private val testSubject =
        TextContentPreviewUi(
            testScope,
            text,
            title,
            testMetadataText,
            /*previewThumbnail=*/ null,
            actionFactory,
            imageLoader,
            headlineGenerator,
            ContentTypeHint.NONE,
        )

    @Test
    fun test_display_headlineIsDisplayed() {
        val layoutInflater = LayoutInflater.from(context)
        val gridLayout = layoutInflater.inflate(R.layout.chooser_grid, null, false) as ViewGroup

        val previewView =
            testSubject.display(
                context.resources,
                layoutInflater,
                gridLayout,
                /*headlineViewParent=*/ null
            )

        assertThat(previewView).isNotNull()
        val headlineView = previewView?.findViewById<TextView>(R.id.headline)
        assertThat(headlineView).isNotNull()
        assertThat(headlineView?.text).isEqualTo(text)
        val metadataView = previewView?.findViewById<TextView>(R.id.metadata)
        assertThat(metadataView).isNotNull()
        assertThat(metadataView?.text).isEqualTo(testMetadataText)
    }

    @Test
    fun test_displayWithExternalHeaderView_externalHeaderIsDisplayed() {
        val layoutInflater = LayoutInflater.from(context)
        val gridLayout =
            layoutInflater.inflate(R.layout.chooser_grid_scrollable_preview, null, false)
                as ViewGroup
        val externalHeaderView =
            gridLayout.requireViewById<View>(R.id.chooser_headline_row_container)

        assertThat(externalHeaderView.findViewById<View>(R.id.headline)).isNull()
        assertThat(externalHeaderView.findViewById<View>(R.id.metadata)).isNull()

        val previewView =
            testSubject.display(context.resources, layoutInflater, gridLayout, externalHeaderView)

        assertThat(previewView).isNotNull()
        assertThat(previewView.findViewById<View>(R.id.headline)).isNull()
        assertThat(previewView.findViewById<View>(R.id.metadata)).isNull()

        val headlineView = externalHeaderView.findViewById<TextView>(R.id.headline)
        assertThat(headlineView).isNotNull()
        assertThat(headlineView?.text).isEqualTo(text)
        val metadataView = externalHeaderView.findViewById<TextView>(R.id.metadata)
        assertThat(metadataView).isNotNull()
        assertThat(metadataView?.text).isEqualTo(testMetadataText)
    }

    @Test
    fun test_display_albumHeadlineOverride() {
        val layoutInflater = LayoutInflater.from(context)
        val gridLayout = layoutInflater.inflate(R.layout.chooser_grid, null, false) as ViewGroup

        val albumSubject =
            TextContentPreviewUi(
                testScope,
                text,
                title,
                testMetadataText,
                /*previewThumbnail=*/ null,
                actionFactory,
                imageLoader,
                headlineGenerator,
                ContentTypeHint.ALBUM,
            )

        val previewView =
            albumSubject.display(
                context.resources,
                layoutInflater,
                gridLayout,
                /*headlineViewParent=*/ null
            )

        assertThat(previewView).isNotNull()
        val headlineView = previewView?.findViewById<TextView>(R.id.headline)
        assertThat(headlineView).isNotNull()
        assertThat(headlineView?.text).isEqualTo(albumHeadline)
        val metadataView = previewView?.findViewById<TextView>(R.id.metadata)
        assertThat(metadataView).isNotNull()
        assertThat(metadataView?.text).isEqualTo(testMetadataText)
    }
}
