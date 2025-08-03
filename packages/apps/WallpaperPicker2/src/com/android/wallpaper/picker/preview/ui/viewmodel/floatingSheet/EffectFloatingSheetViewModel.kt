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

package com.android.wallpaper.picker.preview.ui.viewmodel.floatingSheet

import android.view.View
import com.android.wallpaper.effects.EffectsController.EffectEnumInterface
import com.android.wallpaper.widget.floatingsheetcontent.WallpaperEffectsView2

/** This data class represents the view data for the effect floating sheet */
data class EffectFloatingSheetViewModel(
    val myPhotosClickListener: View.OnClickListener,
    val collapseFloatingSheetListener: View.OnClickListener,
    val effectSwitchListener: WallpaperEffectsView2.EffectSwitchListener,
    val effectDownloadClickListener: WallpaperEffectsView2.EffectDownloadClickListener,
    val status: WallpaperEffectsView2.Status,
    val resultCode: Int?,
    val errorMessage: String?,
    val title: String,
    val effectType: EffectEnumInterface,
    val effectTextRes: WallpaperEffectsView2.EffectTextRes,
)
