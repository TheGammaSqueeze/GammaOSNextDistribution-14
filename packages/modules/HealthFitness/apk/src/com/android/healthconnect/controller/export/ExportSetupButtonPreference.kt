/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.healthconnect.controller.export

import android.content.Context
import android.util.AttributeSet
import android.view.View

import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.utils.logging.HealthConnectLogger
import com.android.healthconnect.controller.utils.logging.HealthConnectLoggerEntryPoint
import dagger.hilt.android.EntryPointAccessors

/** Preference for displaying the setup button the save data screen. */
class ExportSetupButtonPreference constructor(context: Context, attrs: AttributeSet? = null): Preference(context, attrs) {

    // TODO: b/325914485 - Add proper logging for this preference.
    private var logger: HealthConnectLogger

    init {
        layoutResource = R.layout.widget_export_setup_button
        val hiltEntryPoint =
                EntryPointAccessors.fromApplication(
                        context.applicationContext, HealthConnectLoggerEntryPoint::class.java)
        logger = hiltEntryPoint.logger()
    }

    private var onButtonClickListener: View.OnClickListener? = null

    fun setOnButtonClickListener(listener: View.OnClickListener) {
        onButtonClickListener = listener
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        val setupButton = holder.findViewById(R.id.setup_button)
        setupButton?.setOnClickListener(onButtonClickListener)
    }
}