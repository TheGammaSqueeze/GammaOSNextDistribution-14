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

package com.android.customization.picker.color.ui.section

import android.content.Context
import android.view.LayoutInflater
import androidx.lifecycle.LifecycleOwner
import com.android.customization.picker.color.ui.binder.ColorSectionViewBinder
import com.android.customization.picker.color.ui.fragment.ColorPickerFragment
import com.android.customization.picker.color.ui.view.ColorSectionView
import com.android.customization.picker.color.ui.viewmodel.ColorPickerViewModel
import com.android.themepicker.R
import com.android.wallpaper.model.CustomizationSectionController
import com.android.wallpaper.model.CustomizationSectionController.CustomizationSectionNavigationController as NavigationController

class ColorSectionController(
    private val navigationController: NavigationController,
    private val viewModel: ColorPickerViewModel,
    private val lifecycleOwner: LifecycleOwner
) : CustomizationSectionController<ColorSectionView> {

    override fun isAvailable(context: Context): Boolean {
        return true
    }

    override fun createView(context: Context): ColorSectionView {
        return createView(context, CustomizationSectionController.ViewCreationParams())
    }

    override fun createView(
        context: Context,
        params: CustomizationSectionController.ViewCreationParams
    ): ColorSectionView {
        @SuppressWarnings("It is fine to inflate with null parent for our need.")
        val view =
            LayoutInflater.from(context)
                .inflate(
                    R.layout.color_section_view,
                    null,
                ) as ColorSectionView
        ColorSectionViewBinder.bind(
            view = view,
            viewModel = viewModel,
            lifecycleOwner = lifecycleOwner,
            navigationOnClick = {
                navigationController.navigateTo(ColorPickerFragment.newInstance())
            },
            isConnectedHorizontallyToOtherSections = params.isConnectedHorizontallyToOtherSections,
        )
        return view
    }
}
