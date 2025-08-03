/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.bedstead.nene.broadcasts

import android.annotation.SuppressLint
import com.android.bedstead.nene.exceptions.AdbParseException
import com.android.bedstead.nene.exceptions.NeneException
import com.android.bedstead.nene.utils.Versions

interface AdbActivitiesParser {
    companion object {
        @SuppressLint("NewApi")
        operator fun get(sdkVersion: Int): AdbActivitiesParser {
            if (sdkVersion >= Versions.T) {
                return AdbActivitiesParser33()
            }
            // Older APIs may be compatible, but weren't tested
            throw NeneException("Activity Parser only supports API >= 33")
        }
    }

    @Throws(AdbParseException::class)
    fun parseDumpsysActivities(dumpsysActivityManagerOutput: String):
            List<AdbDumpsysActivities.Intent>
}