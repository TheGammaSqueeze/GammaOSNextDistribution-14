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
 *
 */

/**
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.android.healthconnect.controller.permissions.shared

import android.content.Intent
import android.os.Bundle
import androidx.navigation.fragment.findNavController
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.shared.preference.HealthPreference
import com.android.healthconnect.controller.shared.preference.HealthPreferenceFragment
import com.android.healthconnect.controller.utils.DeviceInfoUtils
import com.android.healthconnect.controller.utils.logging.AppPermissionsElement
import com.android.healthconnect.controller.utils.logging.PageName
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** Can't see all your apps fragment for Health Connect. */
@AndroidEntryPoint(HealthPreferenceFragment::class)
class HelpAndFeedbackFragment : Hilt_HelpAndFeedbackFragment() {

    companion object {
        const val CHECK_FOR_UPDATES = "check_for_updates"
        private const val SEE_ALL_COMPATIBLE_APPS = "see_all_compatible_apps"
        private const val SEND_FEEDBACK = "send_feedback"
        const val APP_INTEGRATION_REQUEST_BUCKET_ID =
            "com.google.android.healthconnect.controller.APP_INTEGRATION_REQUEST"
        const val USER_INITIATED_FEEDBACK_BUCKET_ID =
            "com.google.android.healthconnect.controller.USER_INITIATED_FEEDBACK_REPORT"
        const val FEEDBACK_INTENT_RESULT_CODE = 0
    }

    init {
        this.setPageName(PageName.HELP_AND_FEEDBACK_PAGE)
    }

    @Inject lateinit var deviceInfoUtils: DeviceInfoUtils

    private val mCheckForUpdates: HealthPreference? by lazy {
        preferenceScreen.findPreference(CHECK_FOR_UPDATES)
    }

    private val mSeeAllCompatibleApps: HealthPreference? by lazy {
        preferenceScreen.findPreference(SEE_ALL_COMPATIBLE_APPS)
    }

    private val mSendFeedback: HealthPreference? by lazy {
        preferenceScreen.findPreference(SEND_FEEDBACK)
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)
        setPreferencesFromResource(R.xml.help_and_feedback_screen, rootKey)

        mCheckForUpdates?.logName = AppPermissionsElement.CHECK_FOR_UPDATES_BUTTON
        mCheckForUpdates?.setOnPreferenceClickListener {
            findNavController().navigate(R.id.action_cant_see_all_apps_to_updated_apps)
            true
        }

        mSeeAllCompatibleApps?.logName = AppPermissionsElement.SEE_ALL_COMPATIBLE_APPS_BUTTON
        mSeeAllCompatibleApps?.setOnPreferenceClickListener {
            findNavController().navigate(R.id.action_cant_see_all_apps_to_play_store)
            true
        }

        mSendFeedback?.logName = AppPermissionsElement.SEND_FEEDBACK_BUTTON
        mSendFeedback?.setOnPreferenceClickListener {
            val intent = Intent(Intent.ACTION_BUG_REPORT)
            intent.putExtra("category_tag", APP_INTEGRATION_REQUEST_BUCKET_ID)
            activity?.startActivityForResult(intent, FEEDBACK_INTENT_RESULT_CODE)
            true
        }

        mSendFeedback?.isVisible = deviceInfoUtils.isSendFeedbackAvailable(requireContext())
        mCheckForUpdates?.isVisible = deviceInfoUtils.isPlayStoreAvailable(requireContext())
        mSeeAllCompatibleApps?.isVisible = deviceInfoUtils.isPlayStoreAvailable(requireContext())
    }
}
