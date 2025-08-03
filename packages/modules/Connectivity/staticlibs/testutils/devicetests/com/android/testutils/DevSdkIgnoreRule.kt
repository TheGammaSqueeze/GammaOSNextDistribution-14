/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.testutils

import android.os.Build
import androidx.test.InstrumentationRegistry
import com.android.modules.utils.build.UnboundedSdkLevel
import java.util.regex.Pattern
import org.junit.Assume.assumeTrue
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

@Deprecated("Use Build.VERSION_CODES", ReplaceWith("Build.VERSION_CODES.S_V2"))
const val SC_V2 = Build.VERSION_CODES.S_V2
// TODO: Remove this when Build.VERSION_CODES.VANILLA_ICE_CREAM is available in all branches
// where this code builds
const val VANILLA_ICE_CREAM = 35 // Bui1ld.VERSION_CODES.VANILLA_ICE_CREAM

private val MAX_TARGET_SDK_ANNOTATION_RE = Pattern.compile("MaxTargetSdk([0-9]+)$")
private val targetSdk = InstrumentationRegistry.getContext().applicationInfo.targetSdkVersion

private fun isDevSdkInRange(minExclusive: String?, maxInclusive: String?): Boolean {
    return (minExclusive == null || !isAtMost(minExclusive)) &&
            (maxInclusive == null || isAtMost(maxInclusive))
}

private fun isAtMost(sdkVersionOrCodename: String): Boolean {
    // UnboundedSdkLevel does not support builds < Q, and may stop supporting Q as well since it
    // is intended for mainline modules that are now R+.
    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
        // Assume that any codename passed as argument from current code is a more recent build than
        // Q: this util did not exist before Q, and codenames are only used before the corresponding
        // build is finalized. This util could list 28 older codenames to check against (as per
        // ro.build.version.known_codenames in more recent builds), but this does not seem valuable.
        val intVersion = sdkVersionOrCodename.toIntOrNull() ?: return true
        return Build.VERSION.SDK_INT <= intVersion
    }
    return UnboundedSdkLevel.isAtMost(sdkVersionOrCodename)
}

/**
 * Returns true if the development SDK version of the device is in the provided annotation range.
 *
 * If the device is not using a release SDK, the development SDK differs from
 * [Build.VERSION.SDK_INT], and is indicated by the device codenames; see [UnboundedSdkLevel].
 */
fun isDevSdkInRange(
    ignoreUpTo: DevSdkIgnoreRule.IgnoreUpTo?,
    ignoreAfter: DevSdkIgnoreRule.IgnoreAfter?
): Boolean {
    val minExclusive =
            if (ignoreUpTo?.value == 0) ignoreUpTo.codename
            else ignoreUpTo?.value?.toString()
    val maxInclusive =
            if (ignoreAfter?.value == 0) ignoreAfter.codename
            else ignoreAfter?.value?.toString()
    return isDevSdkInRange(minExclusive, maxInclusive)
}

private fun getMaxTargetSdk(description: Description): Int? {
    return description.annotations.firstNotNullOfOrNull {
        MAX_TARGET_SDK_ANNOTATION_RE.matcher(it.annotationClass.simpleName).let { m ->
            if (m.find()) m.group(1).toIntOrNull() else null
        }
    }
}

/**
 * A test rule to ignore tests based on the development SDK level.
 *
 * If the device is not using a release SDK, the development SDK is considered to be higher than
 * [Build.VERSION.SDK_INT].
 *
 * @param ignoreClassUpTo Skip all tests in the class if the device dev SDK is <= this codename or
 *                        SDK level.
 * @param ignoreClassAfter Skip all tests in the class if the device dev SDK is > this codename or
 *                         SDK level.
 */
class DevSdkIgnoreRule @JvmOverloads constructor(
    private val ignoreClassUpTo: String? = null,
    private val ignoreClassAfter: String? = null
) : TestRule {
    /**
     * @param ignoreClassUpTo Skip all tests in the class if the device dev SDK is <= this value.
     * @param ignoreClassAfter Skip all tests in the class if the device dev SDK is > this value.
     */
    @JvmOverloads
    constructor(ignoreClassUpTo: Int?, ignoreClassAfter: Int? = null) : this(
            ignoreClassUpTo?.toString(), ignoreClassAfter?.toString())

    override fun apply(base: Statement, description: Description): Statement {
        return IgnoreBySdkStatement(base, description)
    }

    /**
     * Ignore the test for any development SDK that is strictly after [value].
     *
     * If the device is not using a release SDK, the development SDK is considered to be higher
     * than [Build.VERSION.SDK_INT].
     */
    annotation class IgnoreAfter(val value: Int = 0, val codename: String = "")

    /**
     * Ignore the test for any development SDK that lower than or equal to [value].
     *
     * If the device is not using a release SDK, the development SDK is considered to be higher
     * than [Build.VERSION.SDK_INT].
     */
    annotation class IgnoreUpTo(val value: Int = 0, val codename: String = "")

    private inner class IgnoreBySdkStatement(
        private val base: Statement,
        private val description: Description
    ) : Statement() {
        override fun evaluate() {
            val ignoreAfter = description.getAnnotation(IgnoreAfter::class.java)
            val ignoreUpTo = description.getAnnotation(IgnoreUpTo::class.java)

            val devSdkMessage = "Skipping test for build ${Build.VERSION.CODENAME} " +
                    "with SDK ${Build.VERSION.SDK_INT}"
            assumeTrue(devSdkMessage, isDevSdkInRange(ignoreClassUpTo, ignoreClassAfter))
            assumeTrue(devSdkMessage, isDevSdkInRange(ignoreUpTo, ignoreAfter))

            val maxTargetSdk = getMaxTargetSdk(description)
            if (maxTargetSdk != null) {
                assumeTrue("Skipping test, target SDK $targetSdk greater than $maxTargetSdk",
                        targetSdk <= maxTargetSdk)
            }
            base.evaluate()
        }
    }
}
