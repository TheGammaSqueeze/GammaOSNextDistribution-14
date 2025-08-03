/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.wallpaper.picker.customization.ui.binder

import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.android.wallpaper.R
import com.android.wallpaper.picker.customization.ui.viewmodel.CustomizationPickerViewModel
import com.android.wallpaper.widget.DuoTabs
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** Binds view to view-model for the customization picker tabs. */
object CustomizationPickerTabsBinder {
    @JvmStatic
    fun bind(
        view: View,
        viewModel: CustomizationPickerViewModel,
        lifecycleOwner: LifecycleOwner,
    ) {
        val tabs: DuoTabs = view.requireViewById(R.id.duo_tabs)
        tabs.setTabText(
            view.context.getString(R.string.lock_screen_tab),
            view.context.getString(R.string.home_screen_tab),
        )

        lifecycleOwner.lifecycleScope.launch {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    combine(viewModel.lockScreenTab, viewModel.homeScreenTab) {
                            lockScreenTabViewModel,
                            homeScreenTabViewModel ->
                            lockScreenTabViewModel to homeScreenTabViewModel
                        }
                        .collect { (lockScreenTabViewModel, homeScreenTabViewModel) ->
                            tabs.setOnTabSelectedListener(null)
                            tabs.selectTab(
                                when {
                                    lockScreenTabViewModel.isSelected -> DuoTabs.TAB_PRIMARY
                                    else -> DuoTabs.TAB_SECONDARY
                                },
                            )
                            tabs.setOnTabSelectedListener { tabId ->
                                when (tabId) {
                                    DuoTabs.TAB_PRIMARY ->
                                        lockScreenTabViewModel.onClicked?.invoke()
                                    DuoTabs.TAB_SECONDARY ->
                                        homeScreenTabViewModel.onClicked?.invoke()
                                }
                            }
                        }
                }
            }
        }
    }
}
