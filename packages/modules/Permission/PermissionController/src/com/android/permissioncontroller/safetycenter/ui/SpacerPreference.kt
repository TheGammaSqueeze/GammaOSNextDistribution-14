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

package com.android.permissioncontroller.safetycenter.ui

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.preference.Preference
import androidx.preference.PreferenceScreen
import androidx.preference.PreferenceViewHolder
import com.android.permissioncontroller.R
import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity
import com.android.settingslib.widget.FooterPreference
import kotlin.math.max

/**
 * A preference that adds an empty space to the bottom of a Safety Center subpage.
 *
 * Due to the logic of [CollapsingToolbarBaseActivity], its content won't be scrollable if it fits
 * the single page. This logic conflicts with the UX of collapsible and expandable items of Safety
 * Center, and with some other use cases (i.e. opening the page from Search might scroll to bottom
 * while the scroll is disabled). In such cases user won't be able to expand the collapsed toolbar
 * by scrolling the screen content.
 *
 * [SpacerPreference] makes the page to be slightly bigger than the screen size to unlock the scroll
 * regardless of the content length and to mitigate this UX problem.
 *
 * If a [FooterPreference] is added to the same [PreferenceScreen], its order should be decreased to
 * keep it with the last visible content above the [SpacerPreference].
 */
internal class SpacerPreference(context: Context, attrs: AttributeSet) :
    Preference(context, attrs) {

    init {
        setLayoutResource(R.layout.preference_spacer)
        isVisible = SafetyCenterUiFlags.getShowSubpages()
        // spacer should be the last item on screen
        setOrder(Int.MAX_VALUE - 1)
    }

    private var maxKnownToolbarHeight = 0
    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        val spacer = holder.itemView

        // we should ensure we won't add multiple listeners to the same view,
        // and Preferences API does not allow to do cleanups when onViewRecycled,
        // so we are keeping a track of the added listener attaching it as a tag to the View
        val listener: View.OnLayoutChangeListener =
            spacer.tag as? View.OnLayoutChangeListener
                ?: object : View.OnLayoutChangeListener {
                        override fun onLayoutChange(
                            v: View?,
                            left: Int,
                            top: Int,
                            right: Int,
                            bottom: Int,
                            oldLeft: Int,
                            oldTop: Int,
                            oldRight: Int,
                            oldBottom: Int
                        ) {
                            adjustHeight(spacer)
                        }
                    }
                    .also { spacer.tag = it }

        spacer.removeOnLayoutChangeListener(listener)
        spacer.addOnLayoutChangeListener(listener)
    }

    private fun adjustHeight(spacer: View) {
        val root = spacer.rootView as? ViewGroup
        if (root == null) {
            return
        }

        val contentParent =
            root.findViewById<ViewGroup>(
                com.android.settingslib.collapsingtoolbar.R.id.content_parent
            )
        if (contentParent == null) {
            return
        }
        // when opening the Subpage from Search the layout pass may be triggered
        // differently due to the auto-scroll to highlight a specific item,
        // and in this case we need to wait the content parent to be measured
        if (contentParent.height == 0) {
            val globalLayoutObserver =
                object : ViewTreeObserver.OnGlobalLayoutListener {
                    override fun onGlobalLayout() {
                        contentParent.viewTreeObserver.removeOnGlobalLayoutListener(this)
                        adjustHeight(spacer)
                    }
                }
            contentParent.viewTreeObserver.addOnGlobalLayoutListener(globalLayoutObserver)
            return
        }

        val collapsingToolbar =
            root.findViewById<View>(
                com.android.settingslib.collapsingtoolbar.R.id.collapsing_toolbar
            )
        maxKnownToolbarHeight = max(maxKnownToolbarHeight, collapsingToolbar!!.height)

        val contentHeight = spacer.top + maxKnownToolbarHeight
        val desiredSpacerHeight =
            if (contentHeight > contentParent.height) {
                // making it 0 height will remove if from recyclerview
                1
            } else {
                // to unlock the scrolling we need spacer to go slightly beyond the screen
                contentParent.height - contentHeight + 1
            }

        val layoutParams = spacer.layoutParams
        if (layoutParams.height != desiredSpacerHeight) {
            layoutParams.height = desiredSpacerHeight
            spacer.layoutParams = layoutParams
            // need to let RecyclerView to update scroll position
            spacer.post(::notifyChanged)
        }
    }
}
