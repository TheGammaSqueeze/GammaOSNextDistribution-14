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

package com.android.tools.metalava.model.psi

import com.android.tools.metalava.model.ModelOptions
import com.android.tools.metalava.model.provider.Capability
import com.android.tools.metalava.model.provider.InputFormat
import com.android.tools.metalava.model.source.EnvironmentManager
import com.android.tools.metalava.model.source.SourceModelProvider

// @AutoService(SourceModelProvider::class)
internal class PsiSourceModelProvider : SourceModelProvider {

    override val providerName: String = "psi"

    override val supportedInputFormats = setOf(InputFormat.JAVA, InputFormat.KOTLIN)

    override val capabilities: Set<Capability> =
        setOf(
            Capability.JAVA,
            Capability.KOTLIN,
            Capability.METHOD_BODY,
            Capability.DOCUMENTATION,
        )

    override val modelOptionsList: List<ModelOptions> =
        listOf(
            ModelOptions.build("k1") { this[PsiModelOptions.useK2Uast] = false },
            ModelOptions.build("k2") { this[PsiModelOptions.useK2Uast] = true },
        )

    override fun createEnvironmentManager(
        disableStderrDumping: Boolean,
        forTesting: Boolean,
    ): EnvironmentManager = PsiEnvironmentManager(disableStderrDumping, forTesting)

    override fun toString() = providerName
}
