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

package com.android.tools.metalava

import com.android.tools.metalava.cli.common.BaseOptionGroupTest

val STUB_GENERATION_OPTIONS_HELP =
    """
Stub Generation:

  Options controlling the generation of stub files.

  --stubs <dir>                              Base directory to output the generated stub source files for the API, if
                                             specified.
  --include-annotations / --exclude-all-annotations
                                             Include/exclude annotations such as @Nullable in/from the stub files.
                                             (default: exclude)
  --force-convert-to-warning-nullability-annotations <package1:-package2:...>
                                             On every API declared in a class referenced by the given filter, makes
                                             nullability issues appear to callers as warnings rather than errors by
                                             replacing @Nullable/@NonNull in these APIs with
                                             @RecentlyNullable/@RecentlyNonNull.

                                             See `metalava help package-filters` for more information.
    """
        .trimIndent()

class StubGenerationOptionsTest :
    BaseOptionGroupTest<StubGenerationOptions>(
        STUB_GENERATION_OPTIONS_HELP,
    ) {
    override fun createOptions(): StubGenerationOptions = StubGenerationOptions()
}
