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

package com.android.healthconnect.controller.onboarding

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.widget.Button
import androidx.fragment.app.FragmentActivity
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.shared.Constants.ONBOARDING_SHOWN_PREF_KEY
import com.android.healthconnect.controller.shared.Constants.USER_ACTIVITY_TRACKER
import com.android.healthconnect.controller.utils.logging.HealthConnectLogger
import com.android.healthconnect.controller.utils.logging.OnboardingElement
import com.android.healthconnect.controller.utils.logging.PageName
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** Onboarding activity for Health Connect. */
@AndroidEntryPoint(FragmentActivity::class)
class OnboardingActivity : Hilt_OnboardingActivity() {

    /** Companion object for OnboardingActivity. */
    companion object {
        private const val TARGET_ACTIVITY_INTENT = "ONBOARDING_TARGET_ACTIVITY_INTENT"

        fun shouldRedirectToOnboardingActivity(activity: Activity): Boolean {
            val sharedPreference =
                activity.getSharedPreferences(USER_ACTIVITY_TRACKER, Context.MODE_PRIVATE)
            val previouslyOpened = sharedPreference.getBoolean(ONBOARDING_SHOWN_PREF_KEY, false)
            if (!previouslyOpened) {
                return true
            }
            return false
        }

        fun createIntent(context: Context, targetIntent: Intent? = null): Intent {
            val flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NO_ANIMATION

            return Intent(context, OnboardingActivity::class.java).apply {
                addFlags(flags)
                if (targetIntent != null) {
                    putExtra(TARGET_ACTIVITY_INTENT, targetIntent)
                }
            }
        }
    }

    @Inject lateinit var logger: HealthConnectLogger

    private var targetIntent: Intent? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // This flag ensures a non system app cannot show an overlay on Health Connect. b/313425281
        window.addSystemFlags(WindowManager.LayoutParams.SYSTEM_FLAG_HIDE_NON_SYSTEM_OVERLAY_WINDOWS)
        setContentView(R.layout.onboarding_screen)

        if (intent.hasExtra(TARGET_ACTIVITY_INTENT)) {
            targetIntent =
                intent.getParcelableExtra(
                    TARGET_ACTIVITY_INTENT,
                )
        }

        logger.setPageId(PageName.ONBOARDING_PAGE)

        val sharedPreference = getSharedPreferences(USER_ACTIVITY_TRACKER, Context.MODE_PRIVATE)
        val goBackButton = findViewById<Button>(R.id.go_back_button)
        val getStartedButton = findViewById<Button>(R.id.get_started_button)
        logger.logImpression(OnboardingElement.ONBOARDING_GO_BACK_BUTTON)
        logger.logImpression(OnboardingElement.ONBOARDING_COMPLETED_BUTTON)

        goBackButton.setOnClickListener {
            logger.logInteraction(OnboardingElement.ONBOARDING_GO_BACK_BUTTON)
            setResult(Activity.RESULT_CANCELED)
            finish()
        }
        getStartedButton.setOnClickListener {
            logger.logInteraction(OnboardingElement.ONBOARDING_COMPLETED_BUTTON)
            val editor = sharedPreference.edit()
            editor.putBoolean(ONBOARDING_SHOWN_PREF_KEY, true)
            editor.apply()
            if (targetIntent == null) {
                setResult(Activity.RESULT_OK)
            } else {
                startActivity(targetIntent)
            }
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        logger.logPageImpression()
    }
}
