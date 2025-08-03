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

package android.safetycenter.hostside.rules

import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test
import com.android.tradefed.util.CommandStatus
import java.io.IOException
import org.junit.Assume.assumeTrue
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/** toBooleanString() doesn't seem available on all Kotlin versions we need to support. */
private fun String.toBooleanStrictInt(): Boolean =
    when (this) {
        "true" -> true
        "false" -> false
        else ->
            throw IllegalArgumentException("The string doesn't represent a boolean value: $this")
    }

/** JUnit rule for host side tests that requires Safety Center to be supported and enabled. */
class RequireSafetyCenterRule(private val hostTestClass: BaseHostJUnit4Test) : TestRule {

    private val safetyCenterSupported: Boolean by lazy {
        shellCommandStdoutOrThrow("cmd safety_center supported").toBooleanStrictInt()
    }
    private val safetyCenterEnabled: Boolean by lazy {
        shellCommandStdoutOrThrow("cmd safety_center enabled").toBooleanStrictInt()
    }

    override fun apply(base: Statement, description: Description): Statement {
        return object : Statement() {
            override fun evaluate() {
                assumeTrue("Test device does not support Safety Center", safetyCenterSupported)
                assumeTrue("Safety Center is not enabled on test device", safetyCenterEnabled)
                base.evaluate()
            }
        }
    }

    /** Returns the package name of Safety Center on the test device. */
    fun getSafetyCenterPackageName(): String {
        return shellCommandStdoutOrThrow("cmd safety_center package-name")
    }

    private fun shellCommandStdoutOrThrow(command: String): String {
        val result = hostTestClass.device.executeShellV2Command(command)
        if (result.status != CommandStatus.SUCCESS) {
            throw IOException(
                """Host-side test failed to execute adb shell command on test device.
                        |Command '$command' exited with status code ${result.exitCode}.
                        |This probably means the test device does not have a compatible version of
                        |the Permission Mainline module. Please check the test configuration."""
                    .trimMargin("|")
            )
        }
        return result.stdout.trim()
    }
}
