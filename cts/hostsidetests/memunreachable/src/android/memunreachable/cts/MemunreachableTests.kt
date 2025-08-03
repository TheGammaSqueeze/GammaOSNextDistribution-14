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
package android.memunreachable.cts

import com.android.tradefed.device.DeviceNotAvailableException
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test
import com.google.common.truth.Truth
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs the host side tests for Environment.java
 */
@RunWith(DeviceJUnit4ClassRunner::class)
class MemunreachableTests : BaseHostJUnit4Test() {
    @Throws(DeviceNotAvailableException::class)
    private fun dumpsysMemunreachable(pkg: String, activity: String): String? {
        device.executeShellCommand("am start -W -n $pkg/$activity")
        val meminfo = device.executeShellCommand("dumpsys -t120 meminfo --unreachable $pkg")
        val matcher = UNREACHABLE_MEMORY_PATTERN.find(meminfo)
        return matcher?.groupValues?.get(1)
    }

    @Test
    @Throws(DeviceNotAvailableException::class)
    fun testDumpsysMeminfoUnreachableDebuggableApp() {
        // Test that an app marked android:debuggable can have dumpsys meminfo --unreachable run on it.
        val meminfo = dumpsysMemunreachable(DEBUGGABLE_TEST_PACKAGE, TEST_ACTIVITY)
        Truth.assertThat(meminfo).isNotNull()
        Truth.assertThat(meminfo).startsWith("Unreachable memory\n")
        Truth.assertThat(meminfo).containsMatch(UNREACHABLE_MEMORY_RESULT_PATTERN.toPattern())
    }

    @Test
    @Throws(DeviceNotAvailableException::class)
    fun testDumpsysMeminfoUnreachableUndebuggableApp() {
        // Test that an app not marked android:debuggable can not have dumpsys meminfo --unreachable run on it.
        val meminfo = dumpsysMemunreachable(UNDEBUGGABLE_TEST_PACKAGE, TEST_ACTIVITY)
        Truth.assertThat(meminfo).isNotNull()
        Truth.assertThat(meminfo).startsWith("Unreachable memory\n")
        Truth.assertThat(meminfo)
            .doesNotContainMatch(UNREACHABLE_MEMORY_RESULT_PATTERN.toPattern())
    }

    companion object {
        private const val DEBUGGABLE_TEST_PACKAGE = "android.memunreachable.app.debuggable"
        private const val UNDEBUGGABLE_TEST_PACKAGE = "android.memunreachable.app.undebuggable"
        private const val TEST_ACTIVITY = "android.memunreachable.app.TestMemunreachableActivity"
        private val UNREACHABLE_MEMORY_PATTERN = Regex("\n\\s*(Unreachable memory\n.*)")
        private val UNREACHABLE_MEMORY_RESULT_PATTERN =
                Regex("\\d+ bytes in \\d+ unreachable allocation")
    }
}
