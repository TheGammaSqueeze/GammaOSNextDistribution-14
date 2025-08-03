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

package com.android.wallpaper.picker.common.text.ui.viewbinder

import android.widget.TextView
import com.android.wallpaper.picker.common.text.ui.viewmodel.Text

object TextViewBinder {
    fun bind(
        view: TextView,
        viewModel: Text,
    ) {
        when (viewModel) {
            is Text.Resource -> view.setText(viewModel.res)
            is Text.Loaded -> view.text = viewModel.text
        }
    }
}
