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

import com.google.prefab.api.AndroidAbiMetadata
import com.google.prefab.api.ModuleMetadataV1
import com.google.prefab.api.PackageMetadataV1
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.redundent.kotlin.xml.xml
import java.io.File
import java.io.Serializable

data class PackageData(
    val name: String,
    val mavenVersion: String,
    val prefabVersion: CMakeCompatibleVersion,
    val minSdkVersion: Int,
    val licensePath: String,
    val modules: List<ModuleDescription>,
    val dependencies: Map<String, String>,
)

/**
 * A module exported by the package.
 *
 * As currently implemented by ndkports, one module is exactly one library.
 * Prefab supports header-only libraries, but ndkports does not support these
 * yet.
 *
 * Static libraries are not currently supported by ndkports.
 *
 * @property[name] The name of the module. Note that currently the name of the
 * installed library file must be exactly `lib$name.so`.
 * @property[includesPerAbi] Set to true if a different set of headers should be
 * exposed per-ABI. Not currently implemented.
 * @property[dependencies] A list of other modules required by this module, in
 * the format described by https://google.github.io/prefab/.
 */
data class ModuleDescription(
    val name: String,
    val static: Boolean,
    val headerOnly: Boolean,
    val includesPerAbi: Boolean,
    val dependencies: List<String>,
) : Serializable

class PrefabPackageBuilder(
    private val packageData: PackageData,
    private val packageDirectory: File,
    private val directory: File,
    private val sourceDirectory: File,
    private val ndk: Ndk,
) {
    private val prefabDirectory = packageDirectory.resolve("prefab")
    private val modulesDirectory = prefabDirectory.resolve("modules")

    // TODO: Get from gradle.
    private val packageName = "com.android.ndk.thirdparty.${packageData.name}"

    private fun preparePackageDirectory() {
        if (packageDirectory.exists()) {
            packageDirectory.deleteRecursively()
        }
        modulesDirectory.mkdirs()
    }

    private fun makePackageMetadata() {
        prefabDirectory.resolve("prefab.json").writeText(
            Json.encodeToString(
                PackageMetadataV1(
                    packageData.name,
                    schemaVersion = 1,
                    dependencies = packageData.dependencies.keys.toList(),
                    version = packageData.prefabVersion.toString()
                )
            )
        )
    }

    private fun makeModuleMetadata(module: ModuleDescription, moduleDirectory: File) {
        moduleDirectory.resolve("module.json").writeText(
            Json.encodeToString(
                ModuleMetadataV1(
                    exportLibraries = module.dependencies
                )
            )
        )
    }

    private fun installLibForAbi(module: ModuleDescription, abi: Abi, libsDir: File) {
        val extension = if (module.static) "a" else "so"
        val libName = "lib${module.name}.${extension}"
        val installDirectory = libsDir.resolve("android.${abi.abiName}").apply {
            mkdirs()
        }

        directory.resolve("$abi/lib/$libName")
            .copyTo(installDirectory.resolve(libName))

        installDirectory.resolve("abi.json").writeText(
            Json.encodeToString(
                AndroidAbiMetadata(
                    abi = abi.abiName,
                    api = abi.adjustMinSdkVersion(packageData.minSdkVersion),
                    ndk = ndk.version.major,
                    stl = "c++_shared"
                )
            )
        )
    }

    private fun installLicense() {
        val src = sourceDirectory.resolve(packageData.licensePath)
        val dest = packageDirectory.resolve("META-INF")
            .resolve(File(packageData.licensePath).name)
        src.copyTo(dest)
    }

    private fun createAndroidManifest() {
        packageDirectory.resolve("AndroidManifest.xml")
            .writeText(xml("manifest") {
                attributes(
                    "xmlns:android" to "http://schemas.android.com/apk/res/android",
                    "package" to packageName,
                    "android:versionCode" to 1,
                    "android:versionName" to "1.0"
                )

                "uses-sdk" {
                    attributes(
                        "android:minSdkVersion" to packageData.minSdkVersion,
                        "android:targetSdkVersion" to 29
                    )
                }
            }.toString())
    }

    fun build() {
        preparePackageDirectory()
        makePackageMetadata()
        for (module in packageData.modules) {
            val moduleDirectory = modulesDirectory.resolve(module.name).apply {
                mkdirs()
            }

            makeModuleMetadata(module, moduleDirectory)

            if (module.includesPerAbi) {
                TODO()
            } else {
                // TODO: Check that headers are actually identical across ABIs.
                directory.resolve("${Abi.Arm}/include")
                    .copyRecursively(moduleDirectory.resolve("include"))
            }

            if (!module.headerOnly) {
                val libsDir = moduleDirectory.resolve("libs").apply { mkdirs() }
                for (abi in Abi.values()) {
                    installLibForAbi(module, abi, libsDir)
                }
            }
        }

        installLicense()

        createAndroidManifest()
    }
}
