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

import com.android.tools.metalava.model.provider.Capability
import com.android.tools.metalava.model.source.SourceModelProvider
import com.android.tools.metalava.model.testing.BaseModelProviderRunner
import com.android.tools.metalava.model.testing.CodebaseCreatorConfig

/**
 * A [BaseModelProviderRunner] that will retrieve [SourceModelProvider]s from the
 * [SourceModelProvider] and run the tests against them.
 */
class DriverTestRunner(clazz: Class<*>) :
    BaseModelProviderRunner<SourceModelProvider, DriverTest>(
        clazz = clazz,
        codebaseCreatorConfigsGetter = { getSourceModelProviders() },
        baselineResourcePath = "source-model-provider-baseline.txt",
        minimumCapabilities = setOf(Capability.JAVA, Capability.DOCUMENTATION),
    ) {
    companion object {
        fun getSourceModelProviders(): List<CodebaseCreatorConfig<SourceModelProvider>> {
            return SourceModelProvider.implementations.flatMap { provider ->
                provider.modelOptionsList.map { modelOptions ->
                    CodebaseCreatorConfig(
                        creator = provider,
                        modelOptions = modelOptions,
                    )
                }
            }
        }
    }
}
