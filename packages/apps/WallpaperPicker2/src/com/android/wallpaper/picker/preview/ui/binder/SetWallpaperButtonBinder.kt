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

package com.android.wallpaper.picker.preview.ui.binder

import android.widget.Button
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.android.wallpaper.picker.preview.ui.viewmodel.WallpaperPreviewViewModel
import kotlinx.coroutines.launch

/** Binds the set wallpaper button on small preview. */
object SetWallpaperButtonBinder {

    fun bind(
        button: Button,
        viewModel: WallpaperPreviewViewModel,
        lifecycleOwner: LifecycleOwner,
        navigate: () -> Unit,
    ) {
        lifecycleOwner.lifecycleScope.launch {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.isSetWallpaperButtonVisible.collect { button.isVisible = it } }

                launch {
                    viewModel.onSetWallpaperButtonClicked.collect { onClicked ->
                        button.setOnClickListener(
                            if (onClicked != null) {
                                { onClicked.invoke() }
                            } else {
                                null
                            }
                        )
                    }
                }

                launch {
                    viewModel.showSetWallpaperDialog.collect {
                        if (it) {
                            navigate.invoke()
                        }
                    }
                }
            }
        }
    }
}
