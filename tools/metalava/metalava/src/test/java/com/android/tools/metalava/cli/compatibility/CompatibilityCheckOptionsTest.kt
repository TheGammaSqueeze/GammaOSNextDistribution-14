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

package com.android.tools.metalava.cli.compatibility

import com.android.tools.metalava.ApiType
import com.android.tools.metalava.cli.common.BaseOptionGroupTest
import com.android.tools.metalava.testing.signature
import com.google.common.truth.Truth.assertThat
import org.junit.Test

val COMPATIBILITY_CHECK_OPTIONS_HELP =
    """
Compatibility Checks:

  Options controlling which, if any, compatibility checks are performed against a previously released API.

  --check-compatibility:base <file>          When performing a compat check, use the provided signature file as a base
                                             api, which is treated as part of the API being checked. This allows us to
                                             compute the full API surface from a partial API surface (e.g. the current
                                             @SystemApi txt file), which allows us to recognize when an API is moved
                                             from the partial API to the base API and avoid incorrectly flagging this
  --check-compatibility:api:released <file>  Check compatibility of the previously released API.

                                             When multiple files are provided any files that are a delta on another file
                                             must come after the other file, e.g. if `system` is a delta on `public`
                                             then `public` must come first, then `system`. Or, in other words, they must
                                             be provided in order from the narrowest API to the widest API.
  --check-compatibility:removed:released <file>
                                             Check compatibility of the previously released but since removed APIs.

                                             When multiple files are provided any files that are a delta on another file
                                             must come after the other file, e.g. if `system` is a delta on `public`
                                             then `public` must come first, then `system`. Or, in other words, they must
                                             be provided in order from the narrowest API to the widest API.
  --error-message:compatibility:released <message>
                                             If set, this is output when errors are detected in
                                             --check-compatibility:api:released or
                                             --check-compatibility:removed:released.
    """
        .trimIndent()

class CompatibilityCheckOptionsTest :
    BaseOptionGroupTest<CompatibilityCheckOptions>(
        COMPATIBILITY_CHECK_OPTIONS_HELP,
    ) {

    override fun createOptions(): CompatibilityCheckOptions = CompatibilityCheckOptions()

    @Test
    fun `check compatibility api released`() {
        val file =
            signature("released.txt", "// Signature format: 2.0\n").createFile(temporaryFolder.root)
        runTest(ARG_CHECK_COMPATIBILITY_API_RELEASED, file.path) {
            assertThat(options.compatibilityChecks)
                .isEqualTo(
                    listOf(
                        CompatibilityCheckOptions.CheckRequest(
                            files = listOf(file),
                            apiType = ApiType.PUBLIC_API,
                        ),
                    )
                )
        }
    }

    @Test
    fun `check compatibility api released multiple files`() {
        val file1 =
            signature("released1.txt", "// Signature format: 2.0\n")
                .createFile(temporaryFolder.root)
        val file2 =
            signature("released2.txt", "// Signature format: 2.0\n")
                .createFile(temporaryFolder.root)
        runTest(
            ARG_CHECK_COMPATIBILITY_API_RELEASED,
            file1.path,
            ARG_CHECK_COMPATIBILITY_API_RELEASED,
            file2.path,
        ) {
            assertThat(options.compatibilityChecks)
                .isEqualTo(
                    listOf(
                        CompatibilityCheckOptions.CheckRequest(
                            files = listOf(file1, file2),
                            apiType = ApiType.PUBLIC_API,
                        ),
                    )
                )
        }
    }

    @Test
    fun `check compatibility removed api released`() {
        val file =
            signature("removed.txt", "// Signature format: 2.0\n").createFile(temporaryFolder.root)
        runTest(ARG_CHECK_COMPATIBILITY_REMOVED_RELEASED, file.path) {
            assertThat(options.compatibilityChecks)
                .isEqualTo(
                    listOf(
                        CompatibilityCheckOptions.CheckRequest(
                            files = listOf(file),
                            apiType = ApiType.REMOVED,
                        ),
                    )
                )
        }
    }
}
