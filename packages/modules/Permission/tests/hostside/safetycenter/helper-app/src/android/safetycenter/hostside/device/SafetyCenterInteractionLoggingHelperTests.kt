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

package android.safetycenter.hostside.device

import android.content.Context
import android.os.Bundle
import android.safetycenter.SafetyCenterManager.EXTRA_SAFETY_SOURCES_GROUP_ID
import android.safetycenter.SafetyCenterManager.EXTRA_SAFETY_SOURCE_ID
import android.safetycenter.SafetyCenterManager.EXTRA_SAFETY_SOURCE_ISSUE_ID
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.compatibility.common.util.UiAutomatorUtils2
import com.android.safetycenter.testing.SafetyCenterActivityLauncher.launchSafetyCenterActivity
import com.android.safetycenter.testing.SafetyCenterActivityLauncher.launchSafetyCenterQsActivity
import com.android.safetycenter.testing.SafetyCenterActivityLauncher.openPageAndExit
import com.android.safetycenter.testing.SafetyCenterFlags
import com.android.safetycenter.testing.SafetyCenterTestConfigs
import com.android.safetycenter.testing.SafetyCenterTestConfigs.Companion.SINGLE_SOURCE_ID
import com.android.safetycenter.testing.SafetyCenterTestHelper
import com.android.safetycenter.testing.SafetyCenterTestRule
import com.android.safetycenter.testing.SafetySourceTestData
import com.android.safetycenter.testing.SafetySourceTestData.Companion.INFORMATION_ISSUE_ID
import com.android.safetycenter.testing.UiTestHelper.waitAllTextDisplayed
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Contains "helper tests" that perform on-device setup and interaction code for Safety Center's
 * interaction logging host-side tests. These "helper tests" just perform arrange and act steps and
 * should not contain assertions. Assertions are performed in the host-side tests that run these
 * helper tests.
 *
 * Some context: host-side tests are unable to interact with the device UI in a detailed manner, and
 * must run "helper tests" on the device to perform in-depth interactions or setup that must be
 * performed by code running on the device.
 */
@RunWith(AndroidJUnit4::class)
class SafetyCenterInteractionLoggingHelperTests {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val safetyCenterTestHelper = SafetyCenterTestHelper(context)
    private val safetyCenterTestConfigs = SafetyCenterTestConfigs(context)
    private val safetySourceTestData = SafetySourceTestData(context)

    @get:Rule val safetyCenterTestRule = SafetyCenterTestRule(safetyCenterTestHelper)

    @Before
    fun setUp() {
        SafetyCenterFlags.showSubpages = true
    }

    @After
    fun tearDown() {
        // When an assertion fails, it will end up leaving the previous view open, which screws
        // with the logging assertions made by this test (polluting with view events from whatever
        // view was left open). Here we preemptively clear whatever's open to get back to home
        UiAutomatorUtils2.getUiDevice().pressHome()
    }

    @Test
    fun openSafetyCenter() {
        context.launchSafetyCenterActivity {}
    }

    @Test
    fun openSafetyCenterFullFromQs() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, safetySourceTestData.informationWithIssue)

        context.launchSafetyCenterQsActivity {
            openPageAndExit("Settings") { waitAllTextDisplayed(safetySourceTestData.informationIssue.title) }
        }
    }

    @Test
    fun openSafetyCenterWithIssueIntent() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)

        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, safetySourceTestData.informationWithIssue)

        val extras = Bundle()
        extras.putString(EXTRA_SAFETY_SOURCE_ID, SINGLE_SOURCE_ID)
        extras.putString(EXTRA_SAFETY_SOURCE_ISSUE_ID, INFORMATION_ISSUE_ID)

        context.launchSafetyCenterActivity(extras) {}
    }

    @Test
    fun openSafetyCenterQs() {
        context.launchSafetyCenterQsActivity {}
    }

    @Test
    fun openSubpageFromIntentExtra() {
        val config = safetyCenterTestConfigs.singleSourceConfig
        safetyCenterTestHelper.setConfig(config)
        val sourceGroup = config.safetySourcesGroups.first()!!
        val extras = Bundle()
        extras.putString(EXTRA_SAFETY_SOURCES_GROUP_ID, sourceGroup.id)

        context.launchSafetyCenterActivity(extras) {}
    }

    @Test
    fun openSubpageFromHomepage() {
        val config = safetyCenterTestConfigs.singleSourceConfig
        safetyCenterTestHelper.setConfig(config)
        val sourcesGroup = config.safetySourcesGroups.first()!!

        context.launchSafetyCenterActivity {
            openPageAndExit(context.getString(sourcesGroup.titleResId)) {}
        }
    }

    @Test
    fun openSubpageFromSettingsSearch() {
        val config = safetyCenterTestConfigs.singleSourceConfig
        safetyCenterTestHelper.setConfig(config)
        val sourcesGroup = config.safetySourcesGroups.first()!!
        val extras = Bundle()
        extras.putString(
            EXTRA_SETTINGS_FRAGMENT_ARGS_KEY,
            "${sourcesGroup.safetySources.first().id}_personal"
        )

        context.launchSafetyCenterActivity(extras) {}
    }

    companion object {
        private const val EXTRA_SETTINGS_FRAGMENT_ARGS_KEY = ":settings:fragment_args_key"
    }
}
