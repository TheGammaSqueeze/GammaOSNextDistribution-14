/*
 * Copyright (C) 2021 The Android Open Source Project
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

import android.companion.AssociationRequest.DEVICE_PROFILE_APP_STREAMING
import android.companion.AssociationRequest.DEVICE_PROFILE_AUTOMOTIVE_PROJECTION
import android.companion.AssociationRequest.DEVICE_PROFILE_COMPUTER
import android.companion.AssociationRequest.DEVICE_PROFILE_NEARBY_DEVICE_STREAMING
import android.platform.test.annotations.AppModeFull
import com.android.compatibility.common.util.FeatureUtil
import org.junit.Assume.assumeFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Tests the Association Flow end-to-end.
 *
 * Build/Install/Run: atest CtsCompanionDeviceManagerUiAutomationTestCases:AssociationEndToEndTest
 */
@AppModeFull(reason = "CompanionDeviceManager APIs are not available to the instant apps.")
@RunWith(Parameterized::class)
class AssociationEndToEndTest(
    profile: String?,
    profilePermission: String?,
    profileName: String // Used only by the Parameterized test runner for tagging.
) : UiAutomationTestBase(profile, profilePermission) {

    override fun setUp() {
        super.setUp()

        assumeFalse(FeatureUtil.isWatch())
        // Self_managed profiles are not supported for multiple_devices association flow.
        assumeFalse(profile == DEVICE_PROFILE_COMPUTER)
        assumeFalse(profile == DEVICE_PROFILE_APP_STREAMING)
        assumeFalse(profile == DEVICE_PROFILE_AUTOMOTIVE_PROJECTION)
        assumeFalse(profile == DEVICE_PROFILE_NEARBY_DEVICE_STREAMING)
    }

    @Test
    fun test_userRejected() =
            super.test_userRejected(singleDevice = false, selfManaged = false, displayName = null)

    @Test
    fun test_userDismissed() =
            super.test_userDismissed(singleDevice = false, selfManaged = false, displayName = null)

    @Test
    fun test_userConfirmed() = super.test_userConfirmed_foundDevice(singleDevice = false) {
        // Wait until at least one device is found and click on it.
        confirmationUi.waitAndClickOnFirstFoundDevice()
    }

    @Test
    fun test_timeout() = super.test_timeout(singleDevice = false)

    companion object {
        /**
         * List of (profile, permission, name) tuples that represent all supported profiles and
         * null.
         * Each test will be suffixed with "[profile=<NAME>]", e.g.: "[profile=WATCH]".
         */
        @Parameterized.Parameters(name = "profile={2}")
        @JvmStatic
        fun parameters() = supportedProfilesAndNull()
    }
}
