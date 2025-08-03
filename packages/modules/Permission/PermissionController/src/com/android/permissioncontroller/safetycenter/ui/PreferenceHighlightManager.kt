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

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceScreen
import androidx.recyclerview.widget.RecyclerView
import com.android.permissioncontroller.safetycenter.SafetyCenterConstants.EXTRA_SETTINGS_FRAGMENT_ARGS_KEY
import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity

/** Class used to scroll and highlight preferences for settings search. */
internal class PreferenceHighlightManager(private val fragment: PreferenceFragmentCompat) {
    private var preferenceHighlighted = false
    private var isDataSetObserverRegistered = false

    private var currentRootAdapter: RecyclerView.Adapter<RecyclerView.ViewHolder>? = null
    private var preferenceGroupAdapter: HighlightablePreferenceGroupAdapter? = null
    private val dataSetObserver: RecyclerView.AdapterDataObserver =
        object : RecyclerView.AdapterDataObserver() {
            override fun onChanged() {
                highlightPreferenceIfNeeded()
            }

            override fun onItemRangeChanged(positionStart: Int, itemCount: Int) {
                highlightPreferenceIfNeeded()
            }

            override fun onItemRangeChanged(positionStart: Int, itemCount: Int, payload: Any?) {
                highlightPreferenceIfNeeded()
            }

            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                highlightPreferenceIfNeeded()
            }

            override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) {
                highlightPreferenceIfNeeded()
            }

            override fun onItemRangeMoved(fromPosition: Int, toPosition: Int, itemCount: Int) {
                highlightPreferenceIfNeeded()
            }
        }

    /** Creates a new [HighlightablePreferenceGroupAdapter] instance */
    fun createAdapter(
        preferenceScreen: PreferenceScreen,
    ): RecyclerView.Adapter<RecyclerView.ViewHolder> {
        val intent = fragment.getActivity()?.getIntent()
        preferenceGroupAdapter =
            HighlightablePreferenceGroupAdapter(
                preferenceScreen,
                intent?.getStringExtra(EXTRA_SETTINGS_FRAGMENT_ARGS_KEY),
                preferenceHighlighted
            )
        @Suppress("UNCHECKED_CAST")
        return preferenceGroupAdapter!! as RecyclerView.Adapter<RecyclerView.ViewHolder>
    }

    /** Restore previously saved instance state from [Bundle] */
    fun restoreState(savedInstanceState: Bundle?) {
        if (!SafetyCenterUiFlags.getShowSubpages() || savedInstanceState == null) {
            return
        }

        preferenceHighlighted = savedInstanceState.getBoolean(SAVE_HIGHLIGHTED_KEY, false)
    }

    /** Save current instance state to provided [Bundle] */
    fun saveState(outState: Bundle) {
        if (!SafetyCenterUiFlags.getShowSubpages()) {
            return
        }

        val highlightRequested = preferenceGroupAdapter?.isHighlightRequested
        highlightRequested?.let { outState.putBoolean(SAVE_HIGHLIGHTED_KEY, it) }
    }

    /** Scrolls to a particular preference in the recycler view and highlights it */
    fun highlightPreferenceIfNeeded() {
        if (!SafetyCenterUiFlags.getShowSubpages() || !fragment.isAdded()) {
            return
        }

        val collapsingActivity = fragment.getActivity() as? CollapsingToolbarBaseActivity
        preferenceGroupAdapter?.requestHighlight(
            fragment.getView(),
            fragment.getListView(),
            collapsingActivity?.appBarLayout
        )
    }

    /** Registers the observer while binding preferences */
    fun registerObserverIfNeeded() {
        if (!isDataSetObserverRegistered) {
            currentRootAdapter?.unregisterAdapterDataObserver(dataSetObserver)
            currentRootAdapter = fragment.getListView()?.getAdapter()
            currentRootAdapter?.registerAdapterDataObserver(dataSetObserver)
            isDataSetObserverRegistered = true
            highlightPreferenceIfNeeded()
        }
    }

    /** Unregisters the observer while unbinding preferences */
    fun unregisterObserverIfNeeded() {
        if (isDataSetObserverRegistered) {
            currentRootAdapter?.unregisterAdapterDataObserver(dataSetObserver)
            currentRootAdapter = null
            isDataSetObserverRegistered = false
        }
    }

    companion object {
        private const val SAVE_HIGHLIGHTED_KEY: String = "android:preference_highlighted"
    }
}
