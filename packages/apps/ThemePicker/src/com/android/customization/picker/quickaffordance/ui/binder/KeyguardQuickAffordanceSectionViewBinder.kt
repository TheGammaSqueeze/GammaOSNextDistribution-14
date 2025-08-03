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

package com.android.customization.picker.quickaffordance.ui.binder

import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.android.customization.picker.quickaffordance.ui.viewmodel.KeyguardQuickAffordancePickerViewModel
import com.android.themepicker.R
import com.android.wallpaper.picker.common.icon.ui.viewbinder.IconViewBinder
import com.android.wallpaper.picker.common.text.ui.viewbinder.TextViewBinder
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

object KeyguardQuickAffordanceSectionViewBinder {
    fun bind(
        view: View,
        viewModel: KeyguardQuickAffordancePickerViewModel,
        lifecycleOwner: LifecycleOwner,
        onClicked: () -> Unit,
    ) {
        view.setOnClickListener { onClicked() }

        val descriptionView: TextView =
            view.requireViewById(R.id.keyguard_quick_affordance_description)
        val icon1: ImageView = view.requireViewById(R.id.icon_1)
        val icon2: ImageView = view.requireViewById(R.id.icon_2)

        lifecycleOwner.lifecycleScope.launch {
            viewModel.summary
                .flowWithLifecycle(lifecycleOwner.lifecycle, Lifecycle.State.STARTED)
                .collectLatest { summary ->
                    TextViewBinder.bind(
                        view = descriptionView,
                        viewModel = summary.description,
                    )

                    if (summary.icon1 != null) {
                        IconViewBinder.bind(
                            view = icon1,
                            viewModel = summary.icon1,
                        )
                    }
                    icon1.isVisible = summary.icon1 != null

                    if (summary.icon2 != null) {
                        IconViewBinder.bind(
                            view = icon2,
                            viewModel = summary.icon2,
                        )
                    }
                    icon2.isVisible = summary.icon2 != null
                }
        }
    }
}
