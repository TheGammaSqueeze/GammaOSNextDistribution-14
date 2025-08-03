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
package com.android.safetycenter.testing

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED
import android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED
import android.content.pm.PackageManager.DONT_KILL_APP
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider

/** An activity used in tests to assert the redirects. */
class TestActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.test_activity)
        val bundleText: TextView? = findViewById(R.id.bundle_text)
        val isFromSettingsHomepage = getIntent().getBooleanExtra("is_from_settings_homepage", false)
        bundleText?.text = "is_from_settings_homepage $isFromSettingsHomepage"

        val exitButton: View? = findViewById(R.id.button)
        exitButton?.setOnClickListener { finish() }
    }

    companion object {

        /**
         * Enable a higher-priority alias of TestActivity.
         *
         * <p>We have seen flakes where implicit intents for TEST_ACTIVITY fail owing to multiple
         * receivers, perhaps due to an older CTS APK hanging around. This component should be
         * turned on (and off in tidyup) in tests in the hope of only resolving to the actively
         * running test in these cases.
         */
        fun enableHighPriorityAlias() {
            setAliasEnabledState(COMPONENT_ENABLED_STATE_ENABLED)
        }
        /** @see [enableHighPriorityAlias] */
        fun disableHighPriorityAlias() {
            setAliasEnabledState(COMPONENT_ENABLED_STATE_DISABLED)
        }
        private fun setAliasEnabledState(state: Int) {
            val name =
                ComponentName(getApplicationContext(), TestActivity::class.java.name + "Priority")
            getApplicationContext()
                .packageManager
                .setComponentEnabledSetting(name, state, DONT_KILL_APP)
        }

        private fun getApplicationContext(): Context = ApplicationProvider.getApplicationContext()
    }
}
