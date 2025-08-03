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

class AutoconfBuilder(val toolchain: Toolchain, val sysroot: File) :
    RunBuilder()

abstract class AutoconfPortTask : PortTask() {
    @get:Input
    abstract val autoconf: Property<AutoconfBuilder.() -> Unit>

    fun autoconf(block: AutoconfBuilder.() -> Unit) = autoconf.set(block)

    override fun buildForAbi(
        toolchain: Toolchain,
        workingDirectory: File,
        buildDirectory: File,
        installDirectory: File
    ) {
        buildDirectory.mkdirs()

        val autoconfBlock = autoconf.get()
        val builder = AutoconfBuilder(
            toolchain,
            prefabGenerated.get().asFile.resolve(toolchain.abi.triple)
        )
        builder.autoconfBlock()

        executeSubprocess(listOf(
            "${sourceDirectory.get().asFile.absolutePath}/configure",
            "--host=${toolchain.binutilsTriple}",
            "--prefix=${installDirectory.absolutePath}"
        ) + builder.cmd,
            buildDirectory,
            additionalEnvironment = mutableMapOf(
                "AR" to toolchain.ar.absolutePath,
                "CC" to toolchain.clang.absolutePath,
                "CXX" to toolchain.clangxx.absolutePath,
                "RANLIB" to toolchain.ranlib.absolutePath,
                "STRIP" to toolchain.strip.absolutePath,
                "PATH" to "${toolchain.binDir}:${System.getenv("PATH")}"
            ).apply { putAll(builder.env) })

        executeSubprocess(listOf("make", "-j$ncpus"), buildDirectory)

        executeSubprocess(
            listOf("make", "-j$ncpus", "install"), buildDirectory
        )
    }
}