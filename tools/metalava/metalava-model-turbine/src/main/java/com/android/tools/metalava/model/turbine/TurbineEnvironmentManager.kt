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

import com.android.tools.metalava.model.AnnotationManager
import com.android.tools.metalava.model.ModelOptions
import com.android.tools.metalava.model.source.EnvironmentManager
import com.android.tools.metalava.model.source.SourceParser
import com.android.tools.metalava.reporter.Reporter
import java.io.File

/** Manages the objects created when processing sources. */
internal class TurbineEnvironmentManager() : EnvironmentManager {

    override fun createSourceParser(
        reporter: Reporter,
        annotationManager: AnnotationManager,
        javaLanguageLevel: String,
        kotlinLanguageLevel: String,
        modelOptions: ModelOptions,
        jdkHome: File?,
    ): SourceParser {
        return TurbineSourceParser(annotationManager)
    }

    // TODO (b/299217550 implement it)
    override fun close() {}
}
