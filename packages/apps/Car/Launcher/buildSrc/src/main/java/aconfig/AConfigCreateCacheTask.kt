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

package aconfig

import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.AbstractExecTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile

abstract class AConfigCreateCacheTask :
    AbstractExecTask<AConfigCreateCacheTask>(AConfigCreateCacheTask::class.java) {

    @get:InputFile
    abstract val aconfigPath: RegularFileProperty

    @get:Input
    abstract var packageName: String

    @get:InputFiles
    abstract val srcFiles: ConfigurableFileCollection

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    override fun exec() {
        commandLine(aconfigPath.get())
        args("create-cache", "--package", packageName)

        srcFiles.files.forEach { aconfigFile ->
            args("--declarations", aconfigFile)
        }
        args("--cache", "${outputFile.get()}")
        super.exec()
    }
}
