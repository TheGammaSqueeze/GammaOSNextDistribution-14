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

package com.android.permissioncontroller.safetycenter.ui

import android.provider.DeviceConfig
import com.android.modules.utils.build.SdkLevel

/** A class to access the Safety Center UI related {@link DeviceConfig} flags. */
object SafetyCenterUiFlags {
    private const val PROPERTY_SHOW_SUBPAGES = "safety_center_show_subpages"

    /**
     * Returns whether to show subpages in the Safety Center UI for Android-U instead of the
     * expand-and-collapse list implementation.
     */
    @JvmStatic
    fun getShowSubpages(): Boolean {
        return SdkLevel.isAtLeastU() &&
            DeviceConfig.getBoolean(DeviceConfig.NAMESPACE_PRIVACY, PROPERTY_SHOW_SUBPAGES, true)
    }
}
