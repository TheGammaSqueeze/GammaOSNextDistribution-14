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
package com.android.bedstead.nene.broadcasts

/**
 * Simplified representation of an Activity Intent on Android devices
 * This represents a subset of available field
 *
 * See AdbActivitiesParser33.kt for a sample dumpsys output
 */
object AdbDumpsysActivities {
    class Intent {
        var action: String? = null
        var data: String? = null
        var flags: Long? = null
        var pkg: String? = null
        var cmp: List<String>? = null
    }
}