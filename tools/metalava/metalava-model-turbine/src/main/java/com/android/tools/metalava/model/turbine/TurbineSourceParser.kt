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
import com.android.tools.metalava.model.ClassResolver
import com.android.tools.metalava.model.source.SourceCodebase
import com.android.tools.metalava.model.source.SourceParser
import com.android.tools.metalava.model.source.SourceSet
import com.google.turbine.diag.SourceFile
import com.google.turbine.parse.Parser
import java.io.File

internal class TurbineSourceParser(private val annotationManager: AnnotationManager) :
    SourceParser {

    override fun getClassResolver(classPath: List<File>): ClassResolver {
        TODO("implement it")
    }

    /**
     * Returns a codebase initialized from the given Java source files, with the given description.
     */
    override fun parseSources(
        sourceSet: SourceSet,
        commonSourceSet: SourceSet,
        description: String,
        classPath: List<File>,
    ): TurbineBasedCodebase {
        val rootDir = sourceSet.sourcePath.firstOrNull() ?: File("").canonicalFile
        val codebase = TurbineBasedCodebase(rootDir, description, annotationManager)

        val sourceFiles = getSourceFiles(sourceSet.sources)
        val units = sourceFiles.map { Parser.parse(it) }
        codebase.initialize(units, classPath)

        return codebase
    }

    private fun getSourceFiles(sources: List<File>): List<SourceFile> {
        return sources.map { SourceFile(it.path, it.readText()) }
    }

    override fun loadFromJar(apiJar: File, preFiltered: Boolean): SourceCodebase {
        TODO("b/299044569 handle this")
    }
}
