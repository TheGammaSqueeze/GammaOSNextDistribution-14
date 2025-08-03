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

enum class Rotation(val description: String, val value: Int) {
    ROTATION_0("ROTATION_0", PlatformConsts.ROTATION_0),
    ROTATION_90("ROTATION_90", PlatformConsts.ROTATION_90),
    ROTATION_180("ROTATION_180", PlatformConsts.ROTATION_180),
    ROTATION_270("ROTATION_270", PlatformConsts.ROTATION_270);

    fun isRotated() = this == ROTATION_90 || this == ROTATION_270

    override fun toString(): String {
        return description
    }

    companion object {
        private val VALUES = values()
        fun getByValue(value: Int) = if (value == -1) ROTATION_0 else VALUES[value]
    }
}
