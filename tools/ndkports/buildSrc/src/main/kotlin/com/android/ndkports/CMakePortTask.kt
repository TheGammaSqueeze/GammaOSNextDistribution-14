/*
 * Copyright (C) 2021 The Android Open Source Project
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

class CMakeBuilder(val toolchain: Toolchain, val sysroot: File) :
    RunBuilder()

abstract class CMakePortTask : PortTask() {
    @get:Input
    abstract val cmake: Property<CMakeBuilder.() -> Unit>

    fun cmake(block: CMakeBuilder.() -> Unit) = cmake.set(block)

    override fun buildForAbi(
        toolchain: Toolchain,
        workingDirectory: File,
        buildDirectory: File,
        installDirectory: File
    ) {
        configure(toolchain, buildDirectory, installDirectory)
        build(buildDirectory)
        install(buildDirectory)
    }

    private fun configure(
        toolchain: Toolchain, buildDirectory: File, installDirectory: File
    ) {
        val cmakeBlock = cmake.get()
        val builder = CMakeBuilder(
            toolchain,
            prefabGenerated.get().asFile.resolve(toolchain.abi.triple)
        )
        builder.cmakeBlock()

        val toolchainFile =
            toolchain.ndk.path.resolve("build/cmake/android.toolchain.cmake")

        buildDirectory.mkdirs()
        executeSubprocess(
            listOf(
                "cmake",
                "-DCMAKE_TOOLCHAIN_FILE=${toolchainFile.absolutePath}",
                "-DCMAKE_BUILD_TYPE=RelWithDebInfo",
                "-DCMAKE_INSTALL_PREFIX=${installDirectory.absolutePath}",
                "-DANDROID_ABI=${toolchain.abi.abiName}",
                "-DANDROID_API_LEVEL=${toolchain.api}",
                "-GNinja",
                sourceDirectory.get().asFile.absolutePath,
            ) + builder.cmd, buildDirectory, builder.env
        )
    }

    private fun build(buildDirectory: File) =
        executeSubprocess(listOf("ninja", "-v"), buildDirectory)

    private fun install(buildDirectory: File) =
        executeSubprocess(listOf("ninja", "-v", "install"), buildDirectory)
}