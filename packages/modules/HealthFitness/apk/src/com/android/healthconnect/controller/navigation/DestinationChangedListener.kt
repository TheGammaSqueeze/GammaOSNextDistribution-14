/**
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * ```
 *      http://www.apache.org/licenses/LICENSE-2.0
 * ```
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.android.healthconnect.controller.navigation

import android.os.Bundle
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity

class DestinationChangedListener(private val activity: CollapsingToolbarBaseActivity) :
    NavController.OnDestinationChangedListener {
    override fun onDestinationChanged(
        controller: NavController,
        destination: NavDestination,
        arguments: Bundle?
    ) {
        // Prevent header from being collapsed between fragments.
        activity.appBarLayout?.setExpanded(true)
        activity.appBarLayout?.let {
            activity.collapsingToolbarLayout?.setContentScrimColor(it.solidColor)
        }
        activity.setTitle(destination.label)
    }
}
