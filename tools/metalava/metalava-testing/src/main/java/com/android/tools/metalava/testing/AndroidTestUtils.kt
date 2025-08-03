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

package com.android.tools.metalava.testing

import java.io.File

private const val API_LEVEL = 31

private fun getAndroidJarFromEnv(apiLevel: Int): File {
    val sdkRoot =
        System.getenv("ANDROID_SDK_ROOT")
            ?: System.getenv("ANDROID_HOME") ?: error("Expected ANDROID_SDK_ROOT to be set")
    val jar = File(sdkRoot, "platforms/android-$apiLevel/android.jar")
    if (!jar.exists()) {
        error("Missing ${jar.absolutePath} file in the SDK")
    }
    return jar
}

/**
 * Check to see if this file is the top level metalava directory by looking for a `metalava-model`
 * directory.
 */
private fun File.isMetalavaRootDir(): Boolean = resolve("metalava-model").isDirectory

/** Get a [File] for the public `android.jar` of the specified [apiLevel]. */
fun getAndroidJar(apiLevel: Int = API_LEVEL): File {
    val metalavaDir = getMetalavaDir()

    val localFile = metalavaDir.resolve("../../prebuilts/sdk/$apiLevel/public/android.jar")
    if (localFile.exists()) {
        return localFile
    } else {
        val androidJar = File("../../prebuilts/sdk/$apiLevel/android.jar")
        if (androidJar.exists()) return androidJar
        return getAndroidJarFromEnv(apiLevel)
    }
}

/** Get a [File] for the [apiSurface] `android.txt` of the specified [apiLevel]. */
fun getAndroidTxt(apiLevel: Int = API_LEVEL, apiSurface: String = "public"): File {
    val metalavaDir = getMetalavaDir()

    val localFile = metalavaDir.resolve("../../prebuilts/sdk/$apiLevel/$apiSurface/api/android.txt")
    if (!localFile.exists()) {
        error("Missing ${localFile.absolutePath} file in the SDK")
    }

    return localFile
}

private fun getMetalavaDir(): File {
    // This is either running in tools/metalava or tools/metalava/subproject-dir and we need to look
    // in prebuilts/sdk, so first find tools/metalava then resolve relative to that.
    val cwd = File("").absoluteFile
    val metalavaDir =
        if (cwd.isMetalavaRootDir()) cwd
        else {
            val parent = cwd.parentFile
            if (parent.isMetalavaRootDir()) parent
            else {
                throw IllegalArgumentException("Could not find metalava-model in $cwd")
            }
        }
    return metalavaDir
}
