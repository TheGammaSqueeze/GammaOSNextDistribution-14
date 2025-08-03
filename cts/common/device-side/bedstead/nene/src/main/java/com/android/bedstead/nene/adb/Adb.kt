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

package com.android.bedstead.nene.adb;

import com.android.bedstead.nene.annotations.Experimental
import com.android.bedstead.nene.utils.ShellCommandUtils
import com.android.bedstead.nene.TestApis

/** Helper methods related to Adb. */
@Experimental
object Adb {

    private const val ADB_WIFI_ENABLED = "adb_wifi_enabled";

    /**
     * Check if the device can run commands as root.
     */
    fun isRootAvailable(): Boolean = ShellCommandUtils.isRootAvailable();

    /**
     * Check if Adb is enabled over wifi.
     */
    fun isEnabledOverWifi(): Boolean {
        return TestApis.settings().global().getInt(ADB_WIFI_ENABLED) == 1;
    }
}
