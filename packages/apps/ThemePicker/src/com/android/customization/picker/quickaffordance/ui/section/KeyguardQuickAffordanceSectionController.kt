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

package com.android.customization.picker.quickaffordance.ui.section

import android.content.Context
import android.view.LayoutInflater
import androidx.lifecycle.LifecycleOwner
import com.android.customization.picker.quickaffordance.ui.binder.KeyguardQuickAffordanceSectionViewBinder
import com.android.customization.picker.quickaffordance.ui.fragment.KeyguardQuickAffordancePickerFragment
import com.android.customization.picker.quickaffordance.ui.view.KeyguardQuickAffordanceSectionView
import com.android.customization.picker.quickaffordance.ui.viewmodel.KeyguardQuickAffordancePickerViewModel
import com.android.themepicker.R
import com.android.wallpaper.config.BaseFlags
import com.android.wallpaper.model.CustomizationSectionController
import com.android.wallpaper.model.CustomizationSectionController.CustomizationSectionNavigationController as NavigationController

class KeyguardQuickAffordanceSectionController(
    private val navigationController: NavigationController,
    private val viewModel: KeyguardQuickAffordancePickerViewModel,
    private val lifecycleOwner: LifecycleOwner,
) : CustomizationSectionController<KeyguardQuickAffordanceSectionView> {

    override fun isAvailable(context: Context): Boolean {
        return BaseFlags.get().isKeyguardQuickAffordanceEnabled(context)
    }

    override fun createView(context: Context): KeyguardQuickAffordanceSectionView {
        val view =
            LayoutInflater.from(context)
                .inflate(
                    R.layout.keyguard_quick_affordance_section_view,
                    null,
                ) as KeyguardQuickAffordanceSectionView
        KeyguardQuickAffordanceSectionViewBinder.bind(
            view = view,
            viewModel = viewModel,
            lifecycleOwner = lifecycleOwner,
        ) {
            navigationController.navigateTo(KeyguardQuickAffordancePickerFragment.newInstance())
        }
        return view
    }
}
