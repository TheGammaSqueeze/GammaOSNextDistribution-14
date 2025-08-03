/*
 * Copyright 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 *
 */

package com.android.healthconnect.controller.navigation

import android.content.Intent
import android.content.Intent.EXTRA_PACKAGE_NAME
import android.health.connect.HealthConnectManager
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.fragment.app.FragmentActivity
import com.android.healthconnect.controller.MainActivity
import com.android.healthconnect.controller.data.DataManagementActivity
import com.android.healthconnect.controller.onboarding.OnboardingActivity
import com.android.healthconnect.controller.onboarding.OnboardingActivity.Companion.shouldRedirectToOnboardingActivity
import com.android.healthconnect.controller.permissions.shared.SettingsActivity
import com.android.healthconnect.controller.utils.DeviceInfoUtils
import com.android.healthconnect.controller.utils.activity.EmbeddingUtils.maybeRedirectIntoTwoPaneSettings
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * A proxy activity that starts health connect screens. This activity validates intents and handles
 * errors starting health connect screens.
 */
@AndroidEntryPoint(FragmentActivity::class)
class TrampolineActivity : Hilt_TrampolineActivity() {

    companion object {
        private const val TAG = "TrampolineActivity"
    }

    @Inject lateinit var deviceInfoUtils: DeviceInfoUtils

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // This flag ensures a non system app cannot show an overlay on Health Connect. b/313425281
        window.addSystemFlags(WindowManager.LayoutParams.SYSTEM_FLAG_HIDE_NON_SYSTEM_OVERLAY_WINDOWS)
        // Handles unsupported devices and user profiles.
        if (!deviceInfoUtils.isHealthConnectAvailable(this)) {
            Log.e(TAG, "Health connect is not available for this user or hardware, finishing!")
            finish()
            return
        }

        // Handles large screen support in settings.
        if (maybeRedirectIntoTwoPaneSettings(this)) {
            finish()
            return
        }

        val targetIntent = getTargetIntent()

        // Handles showing Health Connect Onboarding.
        if (shouldRedirectToOnboardingActivity(this)) {
            startActivity(OnboardingActivity.createIntent(this, targetIntent))
            finish()
            return
        }

        startActivity(targetIntent)
        finish()
    }

    private fun getTargetIntent(): Intent {
        return when (intent.action) {
            HealthConnectManager.ACTION_HEALTH_HOME_SETTINGS -> {
                Intent(this, MainActivity::class.java)
            }
            HealthConnectManager.ACTION_MANAGE_HEALTH_DATA -> {
                Intent(this, DataManagementActivity::class.java)
            }
            HealthConnectManager.ACTION_MANAGE_HEALTH_PERMISSIONS -> {
                val extraPackageName: String? = intent.getStringExtra(EXTRA_PACKAGE_NAME)

                Intent(this, SettingsActivity::class.java).apply {
                    if (extraPackageName != null) {
                        putExtra(EXTRA_PACKAGE_NAME, extraPackageName)
                    }
                }
            }

            else -> {
                // Default to open Health Connect MainActivity
                Intent(this, MainActivity::class.java)
            }
        }
    }
}
