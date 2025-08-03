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
 *
 */

package com.android.customization.picker.color.ui.binder

import android.content.res.Configuration
import android.os.Bundle
import android.os.Parcelable
import android.view.View
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.customization.picker.color.ui.adapter.ColorTypeTabAdapter
import com.android.customization.picker.color.ui.view.ColorOptionIconView
import com.android.customization.picker.color.ui.viewmodel.ColorOptionIconViewModel
import com.android.customization.picker.color.ui.viewmodel.ColorPickerViewModel
import com.android.customization.picker.common.ui.view.ItemSpacing
import com.android.themepicker.R
import com.android.wallpaper.picker.option.ui.adapter.OptionItemAdapter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

object ColorPickerBinder {

    /**
     * Binds view with view-model for a color picker experience. The view should include a Recycler
     * View for color type tabs with id [R.id.color_type_tabs] and a Recycler View for color options
     * with id [R.id.color_options]
     */
    @JvmStatic
    fun bind(
        view: View,
        viewModel: ColorPickerViewModel,
        lifecycleOwner: LifecycleOwner,
    ): Binding {
        val colorTypeTabView: RecyclerView = view.requireViewById(R.id.color_type_tabs)
        val colorTypeTabSubheaderView: TextView = view.requireViewById(R.id.color_type_tab_subhead)
        val colorOptionContainerView: RecyclerView = view.requireViewById(R.id.color_options)

        val colorTypeTabAdapter = ColorTypeTabAdapter()
        colorTypeTabView.adapter = colorTypeTabAdapter
        colorTypeTabView.layoutManager =
            LinearLayoutManager(view.context, RecyclerView.HORIZONTAL, false)
        colorTypeTabView.addItemDecoration(ItemSpacing(ItemSpacing.TAB_ITEM_SPACING_DP))
        val colorOptionAdapter =
            OptionItemAdapter(
                layoutResourceId = R.layout.color_option,
                lifecycleOwner = lifecycleOwner,
                bindIcon = { foregroundView: View, colorIcon: ColorOptionIconViewModel ->
                    val colorOptionIconView = foregroundView as? ColorOptionIconView
                    val night =
                        (view.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
                            Configuration.UI_MODE_NIGHT_YES)
                    colorOptionIconView?.let { ColorOptionIconBinder.bind(it, colorIcon, night) }
                }
            )
        colorOptionContainerView.adapter = colorOptionAdapter
        colorOptionContainerView.layoutManager =
            LinearLayoutManager(view.context, RecyclerView.HORIZONTAL, false)
        colorOptionContainerView.addItemDecoration(ItemSpacing(ItemSpacing.ITEM_SPACING_DP))

        lifecycleOwner.lifecycleScope.launch {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.colorTypeTabs
                        .map { colorTypeById -> colorTypeById.values }
                        .collect { colorTypes -> colorTypeTabAdapter.setItems(colorTypes.toList()) }
                }

                launch {
                    viewModel.colorTypeTabSubheader.collect { subhead ->
                        colorTypeTabSubheaderView.text = subhead
                    }
                }

                launch {
                    viewModel.colorOptions.collect { colorOptions ->
                        // only set or restore instance state on a recycler view once data binding
                        // is complete to ensure scroll position is reflected correctly
                        colorOptionAdapter.setItems(colorOptions) {
                            // the same recycler view is used for different color types tabs
                            // the scroll state of each tab should be independent of others
                            if (layoutManagerSavedState != null) {
                                (colorOptionContainerView.layoutManager as LinearLayoutManager)
                                    .onRestoreInstanceState(layoutManagerSavedState)
                                layoutManagerSavedState = null
                            } else {
                                var indexToFocus = colorOptions.indexOfFirst { it.isSelected.value }
                                indexToFocus = if (indexToFocus < 0) 0 else indexToFocus
                                (colorOptionContainerView.layoutManager as LinearLayoutManager)
                                    .scrollToPositionWithOffset(indexToFocus, 0)
                            }
                        }
                    }
                }
            }
        }
        return object : Binding {
            override fun saveInstanceState(savedState: Bundle) {
                // as a workaround for the picker restarting twice after a config change, if the
                // picker restarts before the saved state was applied and set to null,
                // re-use the same saved state
                savedState.putParcelable(
                    LAYOUT_MANAGER_SAVED_STATE,
                    layoutManagerSavedState
                        ?: colorOptionContainerView.layoutManager?.onSaveInstanceState()
                )
            }

            override fun restoreInstanceState(savedState: Bundle) {
                layoutManagerSavedState = savedState.getParcelable(LAYOUT_MANAGER_SAVED_STATE)
            }
        }
    }

    interface Binding {
        fun saveInstanceState(savedState: Bundle)
        fun restoreInstanceState(savedState: Bundle)
    }

    private val LAYOUT_MANAGER_SAVED_STATE: String = "layout_manager_state"
    private var layoutManagerSavedState: Parcelable? = null
}
