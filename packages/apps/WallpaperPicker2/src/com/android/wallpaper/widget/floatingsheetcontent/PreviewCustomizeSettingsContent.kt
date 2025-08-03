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
 */
package com.android.wallpaper.widget.floatingsheetcontent

import android.content.Context
import android.net.Uri
import android.view.View
import androidx.lifecycle.LiveData
import androidx.slice.Slice
import androidx.slice.widget.SliceLiveData
import androidx.slice.widget.SliceView
import com.android.wallpaper.R

class PreviewCustomizeSettingsContent(
    private val context: Context,
    private val uriSettingsSlice: Uri?
) : FloatingSheetContent<View>(context) {

    private lateinit var settingsSliceView: SliceView
    private var settingsLiveData: LiveData<Slice>? = null
    override val viewId: Int
        get() = R.layout.preview_customize_settings

    override fun onViewCreated(previewPage: View) {
        settingsSliceView = previewPage.requireViewById(R.id.settings_slice)
        settingsSliceView.mode = SliceView.MODE_LARGE
        settingsSliceView.isScrollable = false
        if (uriSettingsSlice != null) {
            settingsLiveData = SliceLiveData.fromUri(context, uriSettingsSlice)
        }
        settingsLiveData?.observeForever(settingsSliceView)
    }

    override fun onRecreateView(oldPreviewPage: View) {
        if (settingsLiveData != null && settingsLiveData!!.hasObservers()) {
            settingsLiveData!!.removeObserver(settingsSliceView)
        }
    }
}
