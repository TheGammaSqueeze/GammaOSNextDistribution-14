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

package com.android.tools.metalava.model.provider

/**
 * Provides access to codebase creator specific information needed to support filtering tests based
 * on the creator's capabilities and requirements.
 */
interface FilterableCodebaseCreator {
    /** The name of the provider. */
    val providerName: String

    /** The set of supported input formats. */
    val supportedInputFormats: Set<InputFormat>

    /** The set of supported [Capability]s. */
    val capabilities: Set<Capability>
}
