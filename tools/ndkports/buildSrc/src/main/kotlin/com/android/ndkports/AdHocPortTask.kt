/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.ndkports

import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import java.io.File

open class RunBuilder {
    val cmd = mutableListOf<String>()
    fun arg(arg: String) = cmd.add(arg)
    fun args(vararg args: String) = cmd.addAll(args)

    val env = mutableMapOf<String, String>()
    fun env(key: String, value: String) = env.set(key, value)
}

class AdHocBuilder(
    val sourceDirectory: File,
    val buildDirectory: File,
    val installDirectory: File,
    val toolchain: Toolchain,
    val sysroot: File,
    val ncpus: Int,
) {
    val runs = mutableListOf<RunBuilder>()
    fun run(block: RunBuilder.() -> Unit) {
        runs.add(RunBuilder().apply { block() })
    }
}

abstract class AdHocPortTask : PortTask() {
    @get:Input
    abstract val builder: Property<AdHocBuilder.() -> Unit>

    fun builder(block: AdHocBuilder.() -> Unit) = builder.set(block)

    override fun buildForAbi(
        toolchain: Toolchain,
        workingDirectory: File,
        buildDirectory: File,
        installDirectory: File
    ) {
        buildDirectory.mkdirs()

        val builderBlock = builder.get()
        val builder = AdHocBuilder(
            sourceDirectory.get().asFile,
            buildDirectory,
            installDirectory,
            toolchain,
            prefabGenerated.get().asFile,
            ncpus,
        )
        builder.builderBlock()

        for (run in builder.runs) {
            executeSubprocess(
                run.cmd, buildDirectory, additionalEnvironment = run.env
            )
        }
    }
}