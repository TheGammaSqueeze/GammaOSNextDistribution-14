/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.cts.kernelinfo

import android.app.UiAutomation
import android.os.ParcelFileDescriptor
import android.os.VintfRuntimeInfo
import android.text.TextUtils
import android.util.Pair
import androidx.test.platform.app.InstrumentationRegistry
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.util.regex.Pattern
import java.util.stream.Collectors
import org.junit.Assert.fail

private fun isComment(s: String): Boolean {
    return s.trim().startsWith("#")
}

private fun compareMajorMinorVersion(s1: String, s2: String): Int {
    val v1: Pair<Int, Int> = getVersionFromString(s1)
    val v2: Pair<Int, Int> = getVersionFromString(s2)
    return if (v1.first == v2.first) {
        Integer.compare(v1.second, v2.second)
    } else {
        Integer.compare(v1.first, v2.first)
    }
}

private fun getVersionFromString(version: String): Pair<Int, Int> {
    // Only gets major and minor number of the version string.
    val versionPattern = Pattern.compile("^(\\d+)(\\.(\\d+))?.*")
    val m = versionPattern.matcher(version)
    if (!m.matches()) {
        fail("Cannot parse kernel version: $version")
    }
    val major = m.group(1).toInt()
    val minor = if (TextUtils.isEmpty(m.group(3))) 0 else m.group(3).toInt()
    return Pair(major, minor)
}

/**
 * A class that reads various kernel properties
 */
abstract class KernelInfo {
    companion object {
        /*
         * The kernel configs on this device. The value is cached.
         */
        private val sConfigs: Set<String>

        /**
         * Return true if the specified config is enabled, false otherwise.
         */
        @JvmStatic
        fun hasConfig(config: String): Boolean {
            return sConfigs.contains("$config=y") || sConfigs.contains("$config=m")
        }

        /**
         * Return true if the device's kernel version is newer than the provided version,
         * false otherwise
         */
        @JvmStatic
        fun isKernelVersionGreaterThan(version: String): Boolean {
            val actualVersion = VintfRuntimeInfo.getKernelVersion()
            return compareMajorMinorVersion(actualVersion, version) > 0
        }

        init {
            val automation: UiAutomation = InstrumentationRegistry.getInstrumentation().uiAutomation
            // Access /proc/config.gz from the shell domain, because it cannot be accessed from the
            // app domain
            val stdout: ParcelFileDescriptor =
                    automation.executeShellCommand("zcat /proc/config.gz")
            val inputStream: InputStream = ParcelFileDescriptor.AutoCloseInputStream(stdout)
            inputStream.use {
                val reader = BufferedReader(InputStreamReader(inputStream))
                // Remove any lines that are comments
                sConfigs = reader.lines().filter {!isComment(it)}.collect(Collectors.toSet())
            }
        }
    }
}
