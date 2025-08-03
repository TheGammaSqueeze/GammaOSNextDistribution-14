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
package com.android.wallpaper.picker.preview.ui.view

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ToggleButton
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.view.isVisible
import com.android.wallpaper.R
import com.android.wallpaper.picker.preview.ui.viewmodel.Action

/** Custom layout for a group of wallpaper preview actions. */
class PreviewActionGroup(context: Context, attrs: AttributeSet?) : FrameLayout(context, attrs) {

    private val informationButton: ToggleButton
    private val downloadButton: FrameLayout
    private val downloadButtonToggle: ToggleButton
    private val downloadButtonProgress: FrameLayout
    private val deleteButton: ToggleButton
    private val editButton: ToggleButton
    private val customizeButton: ToggleButton
    private val effectsButton: ToggleButton
    private val shareButton: ToggleButton

    init {
        LayoutInflater.from(context).inflate(R.layout.preview_action_group, this, true)
        informationButton = requireViewById(R.id.information_button)
        downloadButton = requireViewById(R.id.download_button)
        downloadButtonToggle = requireViewById(R.id.download_button_toggle)
        downloadButtonProgress = requireViewById(R.id.download_button_progress)
        deleteButton = requireViewById(R.id.delete_button)
        editButton = requireViewById(R.id.edit_button)
        customizeButton = requireViewById(R.id.customize_button)
        effectsButton = requireViewById(R.id.effects_button)
        shareButton = requireViewById(R.id.share_button)
    }

    fun setIsChecked(action: Action, isChecked: Boolean) {
        // Updates only when the view state is different from the input isChecked
        if (getActionToggle(action).isChecked != isChecked) {
            getActionToggle(action).isChecked = isChecked
        }
    }

    fun setIsVisible(action: Action, isVisible: Boolean) {
        getActionView(action).isVisible = isVisible
    }

    fun setClickListener(action: Action, listener: (() -> Unit)?) {
        getActionToggle(action)
            .setOnClickListener(
                if (listener != null) {
                    { listener.invoke() }
                } else null
            )
    }

    fun setIsDownloading(isDownloading: Boolean) {
        downloadButtonProgress.isVisible = isDownloading
        downloadButtonToggle.isVisible = !isDownloading
    }

    private fun getActionView(action: Action): View {
        return when (action) {
            Action.INFORMATION -> informationButton
            Action.DOWNLOAD -> downloadButton
            Action.DELETE -> deleteButton
            Action.EDIT -> editButton
            Action.CUSTOMIZE -> customizeButton
            Action.EFFECTS -> effectsButton
            Action.SHARE -> shareButton
        }
    }

    private fun getActionToggle(action: Action): ToggleButton {
        return when (action) {
            Action.INFORMATION -> informationButton
            Action.DOWNLOAD -> downloadButtonToggle
            Action.DELETE -> deleteButton
            Action.EDIT -> editButton
            Action.CUSTOMIZE -> customizeButton
            Action.EFFECTS -> effectsButton
            Action.SHARE -> shareButton
        }
    }

    /** Update the background color in case the context theme has changed. */
    fun updateBackgroundColor() {
        val context = context ?: return
        informationButton.foreground = null
        downloadButtonToggle.foreground = null
        downloadButtonProgress.background = null
        deleteButton.foreground = null
        editButton.foreground = null
        customizeButton.foreground = null
        effectsButton.foreground = null
        shareButton.foreground = null
        informationButton.foreground =
            AppCompatResources.getDrawable(context, R.drawable.wallpaper_control_button_info)
        downloadButtonToggle.foreground =
            AppCompatResources.getDrawable(context, R.drawable.wallpaper_control_button_download)
        downloadButtonProgress.background =
            AppCompatResources.getDrawable(
                context,
                R.drawable.wallpaper_control_button_off_background
            )
        deleteButton.foreground =
            AppCompatResources.getDrawable(context, R.drawable.wallpaper_control_button_delete)
        editButton.foreground =
            AppCompatResources.getDrawable(context, R.drawable.wallpaper_control_button_edit)
        customizeButton.foreground =
            AppCompatResources.getDrawable(context, R.drawable.wallpaper_control_button_customize)
        effectsButton.foreground =
            AppCompatResources.getDrawable(context, R.drawable.wallpaper_control_button_effect)
        shareButton.foreground =
            AppCompatResources.getDrawable(context, R.drawable.wallpaper_control_button_share)
    }
}
