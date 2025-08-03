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

package com.android.tools.metalava.model.text

import com.android.tools.lint.checks.infrastructure.TestFile
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.testsuite.BaseModelTest

/**
 * Base class for text test classes that parse signature files to create a [TextCodebase] that can
 * then be introspected.
 */
abstract class BaseTextCodebaseTest : BaseModelTest() {

    /** Run a single signature test with a set of signature files. */
    fun runSignatureTest(vararg sources: TestFile, test: CodebaseContext<Codebase>.() -> Unit) {
        runCodebaseTest(inputSet(*sources), test = test)
    }
}
