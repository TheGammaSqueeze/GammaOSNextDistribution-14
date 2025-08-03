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

package com.android.wallpaper.picker.common.button.ui.viewbinder

import android.graphics.Rect
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.TouchDelegate
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.LayoutRes
import com.android.wallpaper.R
import com.android.wallpaper.picker.common.button.ui.viewmodel.ButtonViewModel
import com.android.wallpaper.picker.common.text.ui.viewbinder.TextViewBinder

object ButtonViewBinder {
    /** Returns a newly-created [View] that's already bound to the given [ButtonViewModel]. */
    fun create(
        parent: ViewGroup,
        viewModel: ButtonViewModel,
        @LayoutRes buttonLayoutResourceId: Int = R.layout.dialog_button,
    ): View {
        val view =
            LayoutInflater.from(
                    ContextThemeWrapper(
                        parent.context,
                        viewModel.style.styleResourceId,
                    )
                )
                .inflate(
                    buttonLayoutResourceId,
                    parent,
                    false,
                )

        addTouchPadding(view)

        val text: TextView = view.requireViewById(R.id.text)
        view.setOnClickListener { viewModel.onClicked?.invoke() }

        TextViewBinder.bind(
            view = text,
            viewModel = viewModel.text,
        )

        return view
    }

    /**
     * Adds touch padding to the top and bottom of the view for accessibility.
     */
    private fun addTouchPadding(view: View) {
        view.post {
            val touchPadding =
                view
                    .resources
                    .getDimensionPixelSize(R.dimen.shortcut_error_dialog_button_touch_padding)
            val touchRect = Rect().apply {
                view.getHitRect(this)
                top -= touchPadding
                bottom += touchPadding
            }
            view.touchDelegate = TouchDelegate(touchRect, view)
        }
    }
}
