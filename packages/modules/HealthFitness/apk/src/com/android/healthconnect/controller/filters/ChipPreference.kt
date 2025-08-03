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
package com.android.healthconnect.controller.filters

import android.content.Context
import android.widget.RadioGroup
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.shared.app.AppMetadata

/**
 * The ChipPreference displays a horizontal list of {@link FilterChip}s.
 *
 * Each FilterChip represents a contributing app to the HealthConnect data. A default `All apps`
 * chip is added to the beginning of the list.
 */
open class ChipPreference
@JvmOverloads
constructor(
    context: Context,
    private val appMetadataList: List<AppMetadata> = listOf(),
    private val addFilterChip: (appMetadata: AppMetadata, chipGroup: RadioGroup) -> Unit,
    private val addAllAppsFilterChip: (chipGroup: RadioGroup) -> Unit
) : Preference(context) {

    init {
        layoutResource = R.layout.widget_horizontal_chip_preference
        isSelectable = false
    }

    var chipGroup: RadioGroup? = null

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)

        chipGroup = holder.findViewById(R.id.chip_group) as RadioGroup

        chipGroup?.removeAllViews()

        addAllAppsFilterChip(chipGroup!!)

        for (appMetadata in appMetadataList) {
            addFilterChip(appMetadata, chipGroup!!)
        }
    }
}
