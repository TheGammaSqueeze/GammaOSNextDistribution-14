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

package com.android.tools.metalava

import com.android.tools.metalava.model.ModelOptions
import com.android.tools.metalava.model.source.SourceModelProvider

/**
 * Contains information provided by the tests.
 *
 * This is used to avoid having to add command line options that are only intended for use by the
 * tests but which could be supplied on an actual command line.
 */
class TestEnvironment(
    /**
     * Packages to skip emitting signatures/stubs for even if public. Typically used for unit tests
     * referencing to classpath classes that aren't part of the definitions and shouldn't be part of
     * the test output; e.g. a test may reference java.lang.Enum but we don't want to start
     * reporting all the public APIs in the java.lang package just because it's indirectly
     * referenced via the "enum" superclass
     */
    val skipEmitPackages: List<String>,
    val sourceModelProvider: SourceModelProvider,
    val modelOptions: ModelOptions,
)
