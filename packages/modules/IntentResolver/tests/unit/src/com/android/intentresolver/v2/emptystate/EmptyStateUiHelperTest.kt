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

package com.android.intentresolver.v2.emptystate

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import com.android.intentresolver.any
import com.android.intentresolver.emptystate.EmptyState
import com.android.intentresolver.mock
import com.google.common.truth.Truth.assertThat
import java.util.Optional
import java.util.function.Supplier
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.never
import org.mockito.Mockito.verify

class EmptyStateUiHelperTest {
    private val context = InstrumentationRegistry.getInstrumentation().getContext()

    var shouldOverrideContainerPadding = false
    val containerPaddingSupplier =
        Supplier<Optional<Int>> {
            Optional.ofNullable(if (shouldOverrideContainerPadding) 42 else null)
        }

    lateinit var rootContainer: ViewGroup
    lateinit var mainListView: View // Visible when no empty state is showing.
    lateinit var emptyStateTitleView: TextView
    lateinit var emptyStateSubtitleView: TextView
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
        mainListView = rootContainer.requireViewById(com.android.internal.R.id.resolver_list)
        emptyStateRootView =
            rootContainer.requireViewById(com.android.internal.R.id.resolver_empty_state)
        emptyStateTitleView =
            rootContainer.requireViewById(com.android.internal.R.id.resolver_empty_state_title)
        emptyStateSubtitleView =
            rootContainer.requireViewById(com.android.internal.R.id.resolver_empty_state_subtitle)
        emptyStateButtonView =
            rootContainer.requireViewById(com.android.internal.R.id.resolver_empty_state_button)
        emptyStateProgressView =
            rootContainer.requireViewById(com.android.internal.R.id.resolver_empty_state_progress)
        emptyStateDefaultTextView = rootContainer.requireViewById(com.android.internal.R.id.empty)
        emptyStateContainerView =
            rootContainer.requireViewById(com.android.internal.R.id.resolver_empty_state_container)
        emptyStateUiHelper =
            EmptyStateUiHelper(
                rootContainer,
                com.android.internal.R.id.resolver_list,
                containerPaddingSupplier
            )
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
        mainListView.visibility = View.GONE

        emptyStateUiHelper.hide()

        assertThat(emptyStateRootView.visibility).isEqualTo(View.GONE)
        assertThat(mainListView.visibility).isEqualTo(View.VISIBLE)
    }

    @Test
    fun testBottomPaddingDelegate_default() {
        shouldOverrideContainerPadding = false
        emptyStateContainerView.setPadding(1, 2, 3, 4)

        emptyStateUiHelper.setupContainerPadding()

        assertThat(emptyStateContainerView.paddingLeft).isEqualTo(1)
        assertThat(emptyStateContainerView.paddingTop).isEqualTo(2)
        assertThat(emptyStateContainerView.paddingRight).isEqualTo(3)
        assertThat(emptyStateContainerView.paddingBottom).isEqualTo(4)
    }

    @Test
    fun testBottomPaddingDelegate_override() {
        shouldOverrideContainerPadding = true // Set bottom padding to 42.
        emptyStateContainerView.setPadding(1, 2, 3, 4)

        emptyStateUiHelper.setupContainerPadding()

        assertThat(emptyStateContainerView.paddingLeft).isEqualTo(1)
        assertThat(emptyStateContainerView.paddingTop).isEqualTo(2)
        assertThat(emptyStateContainerView.paddingRight).isEqualTo(3)
        assertThat(emptyStateContainerView.paddingBottom).isEqualTo(42)
    }

    @Test
    fun testShowEmptyState_noOnClickHandler() {
        mainListView.visibility = View.VISIBLE

        // Note: an `EmptyState.ClickListener` isn't invoked directly by the UI helper; it has to be
        // built into the "on-click handler" that's injected to implement the button-press. We won't
        // display the button without a click "handler," even if it *does* have a `ClickListener`.
        val clickListener = mock<EmptyState.ClickListener>()

        val emptyState =
            object : EmptyState {
                override fun getTitle() = "Test title"
                override fun getSubtitle() = "Test subtitle"

                override fun getButtonClickListener() = clickListener
            }
        emptyStateUiHelper.showEmptyState(emptyState, null)

        assertThat(mainListView.visibility).isEqualTo(View.GONE)
        assertThat(emptyStateRootView.visibility).isEqualTo(View.VISIBLE)
        assertThat(emptyStateTitleView.visibility).isEqualTo(View.VISIBLE)
        assertThat(emptyStateSubtitleView.visibility).isEqualTo(View.VISIBLE)
        assertThat(emptyStateButtonView.visibility).isEqualTo(View.GONE)
        assertThat(emptyStateProgressView.visibility).isEqualTo(View.GONE)
        assertThat(emptyStateDefaultTextView.visibility).isEqualTo(View.GONE)

        assertThat(emptyStateTitleView.text).isEqualTo("Test title")
        assertThat(emptyStateSubtitleView.text).isEqualTo("Test subtitle")

        verify(clickListener, never()).onClick(any())
    }

    @Test
    fun testShowEmptyState_withOnClickHandlerAndClickListener() {
        mainListView.visibility = View.VISIBLE

        val clickListener = mock<EmptyState.ClickListener>()
        val onClickHandler = mock<View.OnClickListener>()

        val emptyState =
            object : EmptyState {
                override fun getTitle() = "Test title"
                override fun getSubtitle() = "Test subtitle"

                override fun getButtonClickListener() = clickListener
            }
        emptyStateUiHelper.showEmptyState(emptyState, onClickHandler)

        assertThat(mainListView.visibility).isEqualTo(View.GONE)
        assertThat(emptyStateRootView.visibility).isEqualTo(View.VISIBLE)
        assertThat(emptyStateTitleView.visibility).isEqualTo(View.VISIBLE)
        assertThat(emptyStateSubtitleView.visibility).isEqualTo(View.VISIBLE)
        assertThat(emptyStateButtonView.visibility).isEqualTo(View.VISIBLE) // Now shown.
        assertThat(emptyStateProgressView.visibility).isEqualTo(View.GONE)
        assertThat(emptyStateDefaultTextView.visibility).isEqualTo(View.GONE)

        assertThat(emptyStateTitleView.text).isEqualTo("Test title")
        assertThat(emptyStateSubtitleView.text).isEqualTo("Test subtitle")

        emptyStateButtonView.performClick()

        verify(onClickHandler).onClick(emptyStateButtonView)
        // The test didn't explicitly configure its `OnClickListener` to relay the click event on
        // to the `EmptyState.ClickListener`, so it still won't have fired here.
        verify(clickListener, never()).onClick(any())
    }
}
