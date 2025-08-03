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

import com.android.tools.lint.LintCliClient
import com.android.tools.lint.detector.api.Project
import org.jetbrains.kotlin.config.LanguageVersionSettings

private const val METALAVA_CLIENT_TAG = "METALAVA_CLI_CLIENT"

internal class MetalavaCliClient(
    private val kotlinLanguageVersionSettings: LanguageVersionSettings,
) : LintCliClient(METALAVA_CLIENT_TAG) {

    // TODO(jsjeon): back to simple client when Lint Project allows to set language version
    override fun getKotlinLanguageLevel(project: Project): LanguageVersionSettings {
        return kotlinLanguageVersionSettings
    }
}
