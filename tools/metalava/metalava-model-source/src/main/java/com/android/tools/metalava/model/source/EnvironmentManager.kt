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

package com.android.tools.metalava.model.source

import com.android.tools.metalava.model.AnnotationManager
import com.android.tools.metalava.model.ModelOptions
import com.android.tools.metalava.reporter.Reporter
import java.io.Closeable
import java.io.File

/**
 * Manages environmental resources, e.g. temporary directories, file caches, etc. needed while
 * processing source files.
 *
 * This will clean up any resources on [close].
 */
interface EnvironmentManager : Closeable {

    /**
     * Create a [SourceParser] that can be used to create [SourceCodebase] related objects.
     *
     * @param reporter the [Reporter] to use for any issues found while processing the sources.
     * @param annotationManager the [AnnotationManager] that determines how annotations will affect
     *   any generated [SourceCodebase]s.
     * @param javaLanguageLevel the java language level as a string, e.g. 1.8, 17, etc.
     * @param kotlinLanguageLevel the kotlin language level as a string, e.g. 1.8, etc.
     * @param modelOptions a set of model specific options provided by the caller.
     * @param jdkHome the optional path to the jdk home directory.
     */
    fun createSourceParser(
        reporter: Reporter,
        annotationManager: AnnotationManager,
        javaLanguageLevel: String = DEFAULT_JAVA_LANGUAGE_LEVEL,
        kotlinLanguageLevel: String = DEFAULT_KOTLIN_LANGUAGE_LEVEL,
        modelOptions: ModelOptions = ModelOptions.empty,
        jdkHome: File? = null,
    ): SourceParser
}

const val DEFAULT_JAVA_LANGUAGE_LEVEL = "1.8"
const val DEFAULT_KOTLIN_LANGUAGE_LEVEL = "1.9"
