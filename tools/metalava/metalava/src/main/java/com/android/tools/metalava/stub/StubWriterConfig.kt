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

package com.android.tools.metalava.stub

import com.android.tools.metalava.model.visitors.ApiVisitor

/**
 * Contains configuration for [StubWriter] that can, or at least could, come from command line
 * options.
 */
internal data class StubWriterConfig(
    val apiVisitorConfig: ApiVisitor.Config = ApiVisitor.Config(),

    /**
     * If true then generate kotlin stubs if the source is kotlin, otherwise generate java stubs.
     */
    val kotlinStubs: Boolean = false,

    /** If true then include documentation in the generated stubs. */
    val includeDocumentationInStubs: Boolean = false,
)
