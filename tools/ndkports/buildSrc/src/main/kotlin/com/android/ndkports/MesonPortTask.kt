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

import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import java.io.File
import javax.inject.Inject

@Suppress("UnstableApiUsage")
abstract class MesonPortTask @Inject constructor(objects: ObjectFactory) :
    PortTask() {
    enum class DefaultLibraryType(val argument: String) {
        Both("both"), Shared("shared"), Static("static")
    }

    @get:Input
    val defaultLibraryType: Property<DefaultLibraryType> =
        objects.property(DefaultLibraryType::class.java)
            .convention(DefaultLibraryType.Shared)

    override fun buildForAbi(
        toolchain: Toolchain,
        workingDirectory: File,
        buildDirectory: File,
        installDirectory: File
    ) {
        configure(toolchain, workingDirectory, buildDirectory, installDirectory)
        build(buildDirectory)
        install(buildDirectory)
    }

    private fun configure(
        toolchain: Toolchain,
        workingDirectory: File,
        buildDirectory: File,
        installDirectory: File
    ) {
        val cpuFamily = when (toolchain.abi) {
            Abi.Arm -> "arm"
            Abi.Arm64 -> "aarch64"
            Abi.X86 -> "x86"
            Abi.X86_64 -> "x86_64"
        }

        val cpu = when (toolchain.abi) {
            Abi.Arm -> "armv7a"
            Abi.Arm64 -> "armv8a"
            Abi.X86 -> "i686"
            Abi.X86_64 -> "x86_64"
        }

        val crossFile = workingDirectory.resolve("cross_file.txt").apply {
            writeText(
                """
            [binaries]
            ar = '${toolchain.ar}'
            c = '${toolchain.clang}'
            cpp = '${toolchain.clangxx}'
            strip = '${toolchain.strip}'

            [host_machine]
            system = 'android'
            cpu_family = '$cpuFamily'
            cpu = '$cpu'
            endian = 'little'
            """.trimIndent()
            )
        }

        executeSubprocess(
            listOf(
                "meson",
                "--cross-file",
                crossFile.absolutePath,
                "--buildtype",
                "release",
                "--prefix",
                installDirectory.absolutePath,
                "--default-library",
                defaultLibraryType.get().argument,
                sourceDirectory.get().asFile.absolutePath,
                buildDirectory.absolutePath
            ), workingDirectory
        )
    }

    private fun build(buildDirectory: File) =
        executeSubprocess(listOf("ninja", "-v"), buildDirectory)

    private fun install(buildDirectory: File) =
        executeSubprocess(listOf("ninja", "-v", "install"), buildDirectory)
}