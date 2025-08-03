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
package com.android.customization.picker.clock.ui.view

import android.view.View
import androidx.annotation.ColorInt
import androidx.lifecycle.LifecycleOwner
import com.android.systemui.plugins.clocks.ClockController

interface ClockViewFactory {

    fun getController(clockId: String): ClockController

    /**
     * Reset the large view to its initial state when getting the view. This is because some view
     * configs, e.g. animation state, might change during the reuse of the clock view in the app.
     */
    fun getLargeView(clockId: String): View

    /**
     * Reset the small view to its initial state when getting the view. This is because some view
     * configs, e.g. translation X, might change during the reuse of the clock view in the app.
     */
    fun getSmallView(clockId: String): View

    fun updateColorForAllClocks(@ColorInt seedColor: Int?)

    fun updateColor(clockId: String, @ColorInt seedColor: Int?)

    fun updateRegionDarkness()

    fun updateTimeFormat(clockId: String)

    fun registerTimeTicker(owner: LifecycleOwner)

    fun onDestroy()

    fun unregisterTimeTicker(owner: LifecycleOwner)
}
