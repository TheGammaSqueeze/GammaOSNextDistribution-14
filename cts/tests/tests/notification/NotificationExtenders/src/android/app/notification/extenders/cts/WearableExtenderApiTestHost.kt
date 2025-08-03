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

package android.app.notification.extenders.cts

import android.compat.cts.CompatChangeGatingTestCase
import com.google.common.collect.ImmutableSet

class WearableExtenderApiTestHost : CompatChangeGatingTestCase() {

    override fun setUp() {
        super.setUp()
    }

    fun testWearableExtenderBackgroundBlockedEnabled() {
        installPackage(TEST_APK_34, true)
        runDeviceCompatTest(
                TEST_PKG_34,
                ".WearableExtenderApi34Test",
                "wearableBackgroundBlockDisabled_wearableBackgroundSet_valueKeepsBitmap",
                ImmutableSet.of(),
                ImmutableSet.of()
        )
    }

    fun testWearableExtenderBackgroundBlockedDisabled() {
        installPackage(TEST_APK_CURRENT, true)
        runDeviceCompatTest(
                TEST_PKG_CURRENT,
                ".WearableExtenderApiCurrentTest",
                "wearableBackgroundBlockDisabled_wearableBackgroundSet_valueIsNull",
                ImmutableSet.of(),
                ImmutableSet.of()
        )
    }

    companion object {
        const val TEST_PKG_34 = "android.app.notification.extenders.cts.api34"
        const val TEST_APK_34 = "CtsNotificationExtenders34TestApp.apk"
        const val TEST_PKG_CURRENT = "android.app.notification.extenders.cts.current"
        const val TEST_APK_CURRENT = "CtsNotificationExtendersCurrentTestApp.apk"
    }
}
