/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.ndkports

import java.util.Collections.max

enum class Abi(
    val archName: String,
    val abiName: String,
    val triple: String,
    val minSupportedVersion: Int
) {
    Arm("arm", "armeabi-v7a", "arm-linux-androideabi", 21),
    Arm64("arm64", "arm64-v8a", "aarch64-linux-android", 21),
    X86("x86", "x86", "i686-linux-android", 21),
    X86_64("x86_64", "x86_64", "x86_64-linux-android", 21);

    fun adjustMinSdkVersion(minSdkVersion: Int) =
        max(listOf(minSdkVersion, minSupportedVersion))

    companion object {
        fun fromAbiName(name: String) = values().find { it.abiName == name }
    }
}
