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

package com.android.tools.metalava.model.testsuite

/** Provide access to a model test suite's baseline file. */
object ModelTestSuiteBaseline {
    /**
     * The path of the file relative to the resources directory (in source) or [ClassLoader] when
     * loading as a resource at runtime.
     */
    const val RESOURCE_PATH = "model-test-suite-baseline.txt"
}
