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
package com.android.bedstead.nene.wifi

import android.net.wifi.WifiManager
import com.android.bedstead.nene.TestApis
import com.android.bedstead.nene.permissions.CommonPermissions
import com.android.bedstead.nene.utils.Poll
import com.android.bedstead.nene.utils.ShellCommand

/** Test APIs related to wifi.  */
object Wifi {

    private val wifiManager = TestApis.context().instrumentedContext()
            .getSystemService(WifiManager::class.java)!!

    /**
     * true if wifi is enabled.
     */
    var isEnabled: Boolean
        get() {
            TestApis.permissions().withPermission(CommonPermissions.ACCESS_WIFI_STATE).use {
                return wifiManager.isWifiEnabled
            }
        }
        set(enabled) {
            if (isEnabled == enabled) {
                return
            }
            ShellCommand.builder("svc wifi")
                    .addOperand(if (enabled) "enable" else "disable")
                    .validate { obj -> obj.isEmpty() }
                    .executeOrThrowNeneException("Error switching wifi state")
            Poll.forValue("Wifi enabled") { isEnabled }
                    .toBeEqualTo(enabled)
                    .errorOnFail()
                    .await()
        }
}
