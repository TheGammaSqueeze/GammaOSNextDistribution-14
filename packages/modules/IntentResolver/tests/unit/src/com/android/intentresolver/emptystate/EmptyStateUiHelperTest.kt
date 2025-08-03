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

package com.android.intentresolver.emptystate

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test

class EmptyStateUiHelperTest {
    private val context = InstrumentationRegistry.getInstrumentation().getContext()

    lateinit var rootContainer: ViewGroup
    lateinit var emptyStateTitleView: View
    lateinit var emptyStateSubtitleView: View
    lateinit var emptyStateButtonView: View
    lateinit var emptyStateProgressView: View
    lateinit var emptyStateDefaultTextView: View
    lateinit var emptyStateContainerView: View
    lateinit var emptyStateRootView: View
    lateinit var emptyStateUiHelper: EmptyStateUiHelper

    @Before
    fun setup() {
        rootContainer = FrameLayout(context)
        LayoutInflater.from(context)
            .inflate(
                com.android.intentresolver.R.layout.resolver_list_per_profile,
                rootContainer,
                true
            )
        emptyStateRootView =
            rootContainer.requireViewById(com.android.internal.R.id.resolver_empty_state)
        emptyStateTitleView =
            rootContainer.requireViewById(com.android.internal.R.id.resolver_empty_state_title)
        emptyStateSubtitleView = rootContainer.requireViewById(
            com.android.internal.R.id.resolver_empty_state_subtitle)
        emptyStateButtonView = rootContainer.requireViewById(
            com.android.internal.R.id.resolver_empty_state_button)
        emptyStateProgressView = rootContainer.requireViewById(
            com.android.internal.R.id.resolver_empty_state_progress)
        emptyStateDefaultTextView =
            rootContainer.requireViewById(com.android.internal.R.id.empty)
        emptyStateContainerView = rootContainer.requireViewById(
            com.android.internal.R.id.resolver_empty_state_container)
        emptyStateUiHelper = EmptyStateUiHelper(rootContainer)
    }

    @Test
    fun testResetViewVisibilities() {
        // First set each view's visibility to differ from the expected "reset" state so we can then
        // assert that they're all reset afterward.
        // TODO: for historic reasons "reset" doesn't cover `emptyStateContainerView`; should it?
        emptyStateRootView.visibility = View.GONE
        emptyStateTitleView.visibility = View.GONE
        emptyStateSubtitleView.visibility = View.GONE
        emptyStateButtonView.visibility = View.VISIBLE
        emptyStateProgressView.visibility = View.VISIBLE
        emptyStateDefaultTextView.visibility = View.VISIBLE

        emptyStateUiHelper.resetViewVisibilities()

        assertThat(emptyStateRootView.visibility).isEqualTo(View.VISIBLE)
        assertThat(emptyStateTitleView.visibility).isEqualTo(View.VISIBLE)
        assertThat(emptyStateSubtitleView.visibility).isEqualTo(View.VISIBLE)
        assertThat(emptyStateButtonView.visibility).isEqualTo(View.INVISIBLE)
        assertThat(emptyStateProgressView.visibility).isEqualTo(View.GONE)
        assertThat(emptyStateDefaultTextView.visibility).isEqualTo(View.GONE)
    }

    @Test
    fun testShowSpinner() {
        emptyStateTitleView.visibility = View.VISIBLE
        emptyStateButtonView.visibility = View.VISIBLE
        emptyStateProgressView.visibility = View.GONE
        emptyStateDefaultTextView.visibility = View.VISIBLE

        emptyStateUiHelper.showSpinner()

        // TODO: should this cover any other views? Subtitle?
        assertThat(emptyStateTitleView.visibility).isEqualTo(View.INVISIBLE)
        assertThat(emptyStateButtonView.visibility).isEqualTo(View.INVISIBLE)
        assertThat(emptyStateProgressView.visibility).isEqualTo(View.VISIBLE)
        assertThat(emptyStateDefaultTextView.visibility).isEqualTo(View.GONE)
    }

    @Test
    fun testHide() {
        emptyStateRootView.visibility = View.VISIBLE

        emptyStateUiHelper.hide()

        assertThat(emptyStateRootView.visibility).isEqualTo(View.GONE)
    }
}
