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

package com.android.tools.metalava.model.turbine

import com.android.tools.metalava.model.provider.Capability
import com.android.tools.metalava.model.provider.InputFormat

// @AutoService(SourceModelProvider::class)
internal class TurbineSourceModelProvider :
    com.android.tools.metalava.model.source.SourceModelProvider {

    override val providerName: String = "turbine"

    override val supportedInputFormats = setOf(InputFormat.JAVA)

    override val capabilities: Set<Capability> =
        setOf(
            Capability.JAVA,
            Capability.DOCUMENTATION,
        )

    override fun createEnvironmentManager(
        disableStderrDumping: Boolean,
        forTesting: Boolean,
    ): com.android.tools.metalava.model.source.EnvironmentManager = TurbineEnvironmentManager()

    override fun toString() = providerName
}
