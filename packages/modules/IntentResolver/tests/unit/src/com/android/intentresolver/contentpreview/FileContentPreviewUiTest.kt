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
import com.android.intentresolver.R
import com.android.intentresolver.mock
import com.android.intentresolver.whenever
import com.android.intentresolver.widget.ActionRow
import com.google.common.truth.Truth.assertThat
import java.util.function.Consumer
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FileContentPreviewUiTest {
    private val fileCount = 2
    private val text = "Sharing 2 files"
    private val testMetadataText: CharSequence = "Test metadata text"
    private val actionFactory =
        object : ChooserContentPreviewUi.ActionFactory {
            override fun getEditButtonRunnable(): Runnable? = null
            override fun getCopyButtonRunnable(): Runnable? = null
            override fun createCustomActions(): List<ActionRow.Action> = emptyList()
            override fun getModifyShareAction(): ActionRow.Action? = null
            override fun getExcludeSharedTextAction(): Consumer<Boolean> = Consumer<Boolean> {}
        }
    private val headlineGenerator =
        mock<HeadlineGenerator> { whenever(getFilesHeadline(fileCount)).thenReturn(text) }

    private val context
        get() = InstrumentationRegistry.getInstrumentation().context

    private val testSubject =
        FileContentPreviewUi(
            fileCount,
            actionFactory,
            headlineGenerator,
            testMetadataText,
        )

    @Test
    fun test_display_titleAndMetadataIsDisplayed() {
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
        assertThat(metadataView?.text).isEqualTo(testMetadataText)
    }

    @Test
    fun test_displayWithExternalHeaderView() {
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
        assertThat(metadataView?.text).isEqualTo(testMetadataText)
    }
}
