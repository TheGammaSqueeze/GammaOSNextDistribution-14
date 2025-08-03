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

package com.android.customization.picker.notifications.ui.section

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import androidx.lifecycle.LifecycleOwner
import com.android.customization.picker.notifications.ui.binder.NotificationSectionBinder
import com.android.customization.picker.notifications.ui.view.NotificationSectionView
import com.android.customization.picker.notifications.ui.viewmodel.NotificationSectionViewModel
import com.android.themepicker.R
import com.android.wallpaper.model.CustomizationSectionController

/** Controls a section with UI that lets the user toggle notification settings. */
class NotificationSectionController(
    private val viewModel: NotificationSectionViewModel,
    private val lifecycleOwner: LifecycleOwner,
) : CustomizationSectionController<NotificationSectionView> {

    override fun isAvailable(context: Context): Boolean {
        return true
    }

    @SuppressLint("InflateParams") // We don't care that the parent is null.
    override fun createView(context: Context): NotificationSectionView {
        val view =
            LayoutInflater.from(context)
                .inflate(
                    R.layout.notification_section,
                    /* parent= */ null,
                ) as NotificationSectionView

        NotificationSectionBinder.bind(
            view = view,
            viewModel = viewModel,
            lifecycleOwner = lifecycleOwner,
        )

        return view
    }
}
