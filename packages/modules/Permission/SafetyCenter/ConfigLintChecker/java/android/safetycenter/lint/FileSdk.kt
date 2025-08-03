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

package android.safetycenter.lint

import android.os.Build
import android.os.Build.VERSION_CODES.TIRAMISU
import com.google.common.annotations.VisibleForTesting
import java.io.File
import java.io.InputStream
import java.lang.reflect.Modifier.isFinal
import java.lang.reflect.Modifier.isPublic
import java.lang.reflect.Modifier.isStatic

/** A class that allows interacting with files that are versioned by sdk. */
object FileSdk {
    /**
     * Linter constant to limit the mocked SDK levels that will be checked. We are making an
     * important assumption here that if new parser logic is introduced that depends on a new SDK
     * level, we expect a new schema to exist and a new version code to have been added.
     */
    private val MAX_VERSION: Int = maxOf(getMaxVersionCodesConstant(), getMaxSchemaVersion())

    /** Test only override to further limit the mocked SDK that will be checked in a test */
    @VisibleForTesting @Volatile @JvmStatic var maxVersionOverride: Int? = null

    /**
     * Returns the max SDK level version that should be used while linting to check for backward
     * compatibility.
     */
    fun getMaxSdkVersion(): Int = maxVersionOverride ?: MAX_VERSION

    /** Returns the SDK level version that a file resource belongs to. */
    fun getSdkQualifier(file: File): Int {
        val directParentName = file.parentFile.name
        val lastQualifier = directParentName.substringAfterLast("-", "")
        if (lastQualifier.isEmpty() || lastQualifier[0] != 'v') {
            return TIRAMISU
        }
        return lastQualifier.substring(1).toIntOrNull() ?: TIRAMISU
    }

    /**
     * Returns whether the file belongs to a basic configuration. By basic, we mean either the
     * default configuration that has no qualifier, or a configuration that is defined only by an
     * SDK level version.
     */
    fun belongsToABasicConfiguration(file: File): Boolean {
        val directParentName = file.parentFile.name
        val qualifierCount = directParentName.count { it == '-' }
        val lastQualifier = directParentName.substringAfterLast("-", "")
        if (
            lastQualifier.isNotEmpty() &&
                lastQualifier[0] == 'v' &&
                lastQualifier.substring(1).toIntOrNull() != null
        ) {
            return qualifierCount == 1
        }
        return qualifierCount == 0
    }

    /** Returns the schema for the specific SDK level provided or null if it doesn't exist. */
    fun getSchemaAsStream(sdk: Int): InputStream? =
        FileSdk::class.java.getResourceAsStream("/safety_center_config${toQualifier(sdk)}.xsd")

    private fun toQualifier(sdk: Int): String = if (sdk == TIRAMISU) "" else "-v$sdk"

    private fun getMaxVersionCodesConstant(): Int =
        Build.VERSION_CODES::class
            .java
            .declaredFields
            .filter {
                isPublic(it.modifiers) &&
                    isFinal(it.modifiers) &&
                    isStatic(it.modifiers) &&
                    it.type == Integer.TYPE
            }
            .maxOf { it.get(null) as Int }

    private fun getMaxSchemaVersion(): Int =
        // 99 is an arbitrary high value to look for the schema with the highest SDK level.
        // Gaps are possible which is why we cannot just stop as soon as an SDK level has no schema.
        (TIRAMISU..99).filter { getSchemaAsStream(it) != null }.maxOrNull() ?: 0
}
