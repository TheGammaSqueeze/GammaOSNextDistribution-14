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
import android.safetycenter.SafetySourceData
import android.safetycenter.SafetySourceIssue
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.safetycenter.pendingintents.PendingIntentSender
import com.android.safetycenter.testing.SafetyCenterActivityLauncher
import com.android.safetycenter.testing.SafetyCenterFlags
import com.android.safetycenter.testing.SafetyCenterTestConfigs
import com.android.safetycenter.testing.SafetyCenterTestConfigs.Companion.SINGLE_SOURCE_ID
import com.android.safetycenter.testing.SafetyCenterTestHelper
import com.android.safetycenter.testing.SafetyCenterTestRule
import com.android.safetycenter.testing.SafetySourceTestData
import com.android.safetycenter.testing.StatusBarNotificationWithChannel
import com.android.safetycenter.testing.TestNotificationListener
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
class SafetyCenterNotificationLoggingHelperTests {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val safetyCenterTestHelper = SafetyCenterTestHelper(context)
    private val safetySourceTestData = SafetySourceTestData(context)
    private val safetyCenterTestConfigs = SafetyCenterTestConfigs(context)

    @get:Rule
    val safetyCenterTestRule =
        SafetyCenterTestRule(safetyCenterTestHelper, withNotifications = true)

    @Before
    fun setUp() {
        SafetyCenterFlags.notificationsEnabled = true
        SafetyCenterFlags.notificationsAllowedSources = setOf(SINGLE_SOURCE_ID)
        SafetyCenterFlags.allowStatsdLogging = true
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
    }

    @Test
    fun sendNotification() {
        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, newTestDataWithNotifiableIssue())
    }

    @Test
    fun openSafetyCenterFromNotification() {
        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, newTestDataWithNotifiableIssue())

        sendContentPendingIntent(TestNotificationListener.waitForSingleNotification())
    }

    private fun newTestDataWithNotifiableIssue(): SafetySourceData =
        safetySourceTestData
            .defaultCriticalDataBuilder()
            .addIssue(
                safetySourceTestData
                    .defaultRecommendationIssueBuilder("Notify immediately", "This is urgent!")
                    .setNotificationBehavior(SafetySourceIssue.NOTIFICATION_BEHAVIOR_IMMEDIATELY)
                    .build()
            )
            .build()

    companion object {
        private fun sendContentPendingIntent(
            statusBarNotificationWithChannel: StatusBarNotificationWithChannel
        ) {
            val contentIntent =
                statusBarNotificationWithChannel.statusBarNotification.notification.contentIntent
            SafetyCenterActivityLauncher.executeBlockAndExit(
                launchActivity = { PendingIntentSender.send(contentIntent) },
                block = {} // No action required
            )
        }
    }
}
