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

import android.os.Bundle
import android.view.View
import androidx.navigation.fragment.findNavController
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.shared.preference.HealthPreferenceFragment
import dagger.hilt.android.AndroidEntryPoint

/** Fragment displaying export settings. */
@AndroidEntryPoint(HealthPreferenceFragment::class)
class ExportSettingsFragment : Hilt_ExportSettingsFragment() {

    companion object {
        const val EXPORT_SETUP_BUTTON_PREFERENCE_KEY = "export_setup_button"
    }

    private val setupButtonPreference: ExportSetupButtonPreference? by lazy {
        preferenceScreen.findPreference(EXPORT_SETUP_BUTTON_PREFERENCE_KEY)
    }

    // TODO: b/325914485 - Add proper logging for the export settings fragment.
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)
        setPreferencesFromResource(R.xml.export_settings_screen, rootKey)

        setupButtonPreference?.setOnButtonClickListener {
            findNavController().navigate(R.id.action_exportSettingsFragment_to_exportFrequencyActivity)
        }
    }
}