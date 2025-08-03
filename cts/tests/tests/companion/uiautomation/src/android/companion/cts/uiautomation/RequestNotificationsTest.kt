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

package android.companion.cts.uiautomation

import android.companion.cts.common.MAC_ADDRESS_A
import android.content.ComponentName
import android.content.pm.PackageManager.FEATURE_AUTOMOTIVE
import android.platform.test.annotations.AppModeFull
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.compatibility.common.util.FeatureUtil
import org.junit.Assume.assumeFalse
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertFailsWith

/**
 * Test RequestNotificationsTest api.
 *
 * Build/Install/Run:
 * atest CtsCompanionDeviceManagerUiAutomationTestCases:RequestNotificationsTest
 */
@AppModeFull(reason = "CompanionDeviceManager APIs are not available to the instant apps.")
@RunWith(AndroidJUnit4::class)
class RequestNotificationsTest : UiAutomationTestBase(null, null) {
    private val isAuto: Boolean by lazy { pm.hasSystemFeature(FEATURE_AUTOMOTIVE) }

    override fun setUp() {
        super.setUp()
        assumeFalse(FeatureUtil.isWatch())
    }

    @Test
    fun test_requestNotifications() {
        // Should throw IllegalStateException without an association.
        assertFailsWith(IllegalStateException::class) {
            cdm.requestNotificationAccess(
                ComponentName(instrumentation.targetContext, NotificationListener::class.java))
        }

        targetApp.associate(MAC_ADDRESS_A)

        cdm.requestNotificationAccess(
            ComponentName(instrumentation.targetContext, NotificationListener::class.java))
        if (isAuto) {
            confirmationUi.waitUntilNotificationVisible(isAuto = true)
        } else {
            confirmationUi.waitUntilNotificationVisible()
        }
    }

    override fun tearDown() {
        uiDevice.pressBack()
        super.tearDown()
    }
}
