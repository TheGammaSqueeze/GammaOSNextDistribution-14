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
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.android.customization.picker.color.ui.viewmodel.ColorOptionIconViewModel
import com.android.customization.picker.color.ui.viewmodel.ColorPickerViewModel
import com.android.themepicker.R
import com.android.wallpaper.picker.common.icon.ui.viewbinder.ContentDescriptionViewBinder
import com.android.wallpaper.picker.option.ui.viewmodel.OptionItemViewModel
import kotlinx.coroutines.launch

object ColorSectionViewBinder {

    /**
     * Binds view with view-model for color picker section. The view should include a linear layout
     * with id [R.id.color_section_option_container]
     */
    @JvmStatic
    fun bind(
        view: View,
        viewModel: ColorPickerViewModel,
        lifecycleOwner: LifecycleOwner,
        navigationOnClick: (View) -> Unit,
        isConnectedHorizontallyToOtherSections: Boolean = false,
    ) {
        val optionContainer: LinearLayout =
            view.requireViewById(R.id.color_section_option_container)
        val moreColorsButton: View = view.requireViewById(R.id.more_colors)
        if (isConnectedHorizontallyToOtherSections) {
            moreColorsButton.isVisible = true
            moreColorsButton.setOnClickListener(navigationOnClick)
        } else {
            moreColorsButton.isVisible = false
        }
        lifecycleOwner.lifecycleScope.launch {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.colorSectionOptions.collect { colorOptions ->
                        setOptions(
                            options = colorOptions,
                            view = optionContainer,
                            lifecycleOwner = lifecycleOwner,
                            addOverflowOption = !isConnectedHorizontallyToOtherSections,
                            overflowOnClick = navigationOnClick,
                        )
                    }
                }
            }
        }
    }

    fun setOptions(
        options: List<OptionItemViewModel<ColorOptionIconViewModel>>,
        view: LinearLayout,
        lifecycleOwner: LifecycleOwner,
        addOverflowOption: Boolean = false,
        overflowOnClick: (View) -> Unit = {},
    ) {
        view.removeAllViews()
        // Color option slot size is the minimum between the color option size and the view column
        // count. When having an overflow option, a slot is reserved for the overflow option.
        val colorOptionSlotSize =
            (if (addOverflowOption) {
                    minOf(view.weightSum.toInt() - 1, options.size)
                } else {
                    minOf(view.weightSum.toInt(), options.size)
                })
                .let { if (it < 0) 0 else it }
        options.subList(0, colorOptionSlotSize).forEach { item ->
            val night =
                (view.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
                    Configuration.UI_MODE_NIGHT_YES)
            val itemView =
                LayoutInflater.from(view.context)
                    .inflate(R.layout.color_option_no_background, view, false)
            item.payload?.let {
                ColorOptionIconBinder.bind(itemView.requireViewById(R.id.option_tile), it, night)
                ContentDescriptionViewBinder.bind(
                    view = itemView.requireViewById(R.id.option_tile),
                    viewModel = item.text,
                )
            }
            val optionSelectedView = itemView.requireViewById<ImageView>(R.id.option_selected)

            lifecycleOwner.lifecycleScope.launch {
                launch {
                    item.isSelected.collect { isSelected ->
                        optionSelectedView.isVisible = isSelected
                    }
                }
                launch {
                    item.onClicked.collect { onClicked ->
                        itemView.setOnClickListener(
                            if (onClicked != null) {
                                View.OnClickListener { onClicked.invoke() }
                            } else {
                                null
                            }
                        )
                    }
                }
            }
            view.addView(itemView)
        }
        // add overflow option
        if (addOverflowOption) {
            val itemView =
                LayoutInflater.from(view.context)
                    .inflate(R.layout.color_option_overflow_no_background, view, false)
            itemView.setOnClickListener(overflowOnClick)
            view.addView(itemView)
        }
    }
}
