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

package android.tools

enum class NavBar(val description: String, val value: String) {
    MODE_3BUTTON("3_BUTTON_NAV", PlatformConsts.MODE_3BUTTON),
    MODE_GESTURAL("GESTURAL_NAV", PlatformConsts.MODE_GESTURAL);

    companion object {
        fun getByValue(value: String) {
            when (value) {
                PlatformConsts.MODE_3BUTTON -> MODE_3BUTTON
                PlatformConsts.MODE_GESTURAL -> MODE_GESTURAL
                else -> error("Unknown nav bar mode $value")
            }
        }
    }
}
