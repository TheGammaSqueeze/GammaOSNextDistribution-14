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

package com.android.testutils

import com.android.tradefed.invoker.TestInformation
import com.android.tradefed.targetprep.BaseTargetPreparer

/**
 * A target preparer that disables DeviceConfig sync while running a test.
 *
 * Without this preparer, tests that rely on stable values of DeviceConfig flags, for example to
 * test behavior when setting the flag and resetting it afterwards, may flake as the flags may
 * be synced with remote servers during the test.
 */
class DisableConfigSyncTargetPreparer : BaseTargetPreparer() {
    private var syncDisabledOriginalValue = "none"

    override fun setUp(testInfo: TestInformation) {
        if (isDisabled) return
        syncDisabledOriginalValue = readSyncDisabledOriginalValue(testInfo)

        // The setter is the same in current and legacy S versions
        testInfo.exec("cmd device_config set_sync_disabled_for_tests until_reboot")
    }

    override fun tearDown(testInfo: TestInformation, e: Throwable?) {
        if (isTearDownDisabled) return
        // May fail harmlessly if called before S
        testInfo.exec("cmd device_config set_sync_disabled_for_tests $syncDisabledOriginalValue")
    }

    private fun readSyncDisabledOriginalValue(testInfo: TestInformation): String {
        return when (val reply = testInfo.exec("cmd device_config get_sync_disabled_for_tests")) {
            "until_reboot", "persistent", "none" -> reply
            // Reply does not match known modes, try legacy commands used on S and some T builds
            else -> when (testInfo.exec("cmd device_config is_sync_disabled_for_tests")) {
                // The legacy command just said "true" for "until_reboot" or "persistent". There is
                // no way to know which one was used, so just reset to "until_reboot" to be
                // conservative.
                "true" -> "until_reboot"
                else -> "none"
            }
        }
    }
}

fun TestInformation.exec(cmd: String) = this.device.executeShellCommand(cmd)
