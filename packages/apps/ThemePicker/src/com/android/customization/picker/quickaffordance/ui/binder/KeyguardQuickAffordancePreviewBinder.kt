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

import android.app.Activity
import android.os.Bundle
import androidx.cardview.widget.CardView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.android.customization.picker.quickaffordance.ui.viewmodel.KeyguardQuickAffordancePickerViewModel
import com.android.systemui.shared.quickaffordance.shared.model.KeyguardPreviewConstants
import com.android.wallpaper.R
import com.android.wallpaper.picker.customization.ui.binder.ScreenPreviewBinder
import kotlinx.coroutines.launch

object KeyguardQuickAffordancePreviewBinder {

    /** Binds view for the preview of the lock screen. */
    @JvmStatic
    fun bind(
        activity: Activity,
        previewView: CardView,
        viewModel: KeyguardQuickAffordancePickerViewModel,
        lifecycleOwner: LifecycleOwner,
        offsetToStart: Boolean,
    ) {
        val binding =
            ScreenPreviewBinder.bind(
                activity = activity,
                previewView = previewView,
                viewModel = viewModel.preview,
                lifecycleOwner = lifecycleOwner,
                offsetToStart = offsetToStart,
                dimWallpaper = true,
                onWallpaperPreviewDirty = { activity.recreate() },
            )

        previewView.contentDescription =
            previewView.context.getString(
                R.string.lockscreen_wallpaper_preview_card_content_description
            )

        lifecycleOwner.lifecycleScope.launch {
            viewModel.selectedSlotId
                .flowWithLifecycle(lifecycleOwner.lifecycle, Lifecycle.State.STARTED)
                .collect { slotId ->
                    binding.sendMessage(
                        KeyguardPreviewConstants.MESSAGE_ID_SLOT_SELECTED,
                        Bundle().apply { putString(KeyguardPreviewConstants.KEY_SLOT_ID, slotId) },
                    )
                }
        }
    }
}
