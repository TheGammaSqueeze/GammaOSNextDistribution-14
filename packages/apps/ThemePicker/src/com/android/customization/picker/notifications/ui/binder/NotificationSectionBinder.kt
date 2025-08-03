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

package com.android.customization.picker.notifications.ui.binder

import android.annotation.SuppressLint
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.android.customization.picker.notifications.ui.viewmodel.NotificationSectionViewModel
import com.android.themepicker.R
import com.google.android.material.materialswitch.MaterialSwitch
import kotlinx.coroutines.launch

/**
 * Binds between view and view-model for a section that lets the user control notification settings.
 */
object NotificationSectionBinder {
    @SuppressLint("UseSwitchCompatOrMaterialCode") // We're using Switch and that's okay for SysUI.
    fun bind(
        view: View,
        viewModel: NotificationSectionViewModel,
        lifecycleOwner: LifecycleOwner,
    ) {
        val switch: MaterialSwitch = view.requireViewById(R.id.switcher)

        view.setOnClickListener { viewModel.onClicked() }

        lifecycleOwner.lifecycleScope.launch {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.isSwitchOn.collect { switch.isChecked = it } }
            }
        }
    }
}
