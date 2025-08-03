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

import com.android.build.api.variant.AndroidComponentsExtension
import java.io.File
import org.apache.tools.ant.taskdefs.condition.Os
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.configurationcache.extensions.capitalized
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.extra
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.register

abstract class AConfigPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        project.dependencies.add("implementation", project.project(":libs:aconfig-platform-compat"))
        project.extensions.create<AConfigExtension>("aconfig", project.objects)
        val androidComponents = project.extensions.getByType(AndroidComponentsExtension::class.java)
        val androidTop = project.extra["ANDROID_TOP"].toString()
        val platform = if (Os.isFamily(Os.FAMILY_MAC)) "darwin" else "linux"
        androidComponents.onVariants { variant ->
            val variantName = variant.name.capitalized()
            val aconfigExtension = project.extensions.getByType<AConfigExtension>()
            val aconfigBin = File("$androidTop/prebuilts/build-tools/$platform-x86/bin/aconfig")

            aconfigExtension.declarations.forEach {
                val pkgName = it.packageName.get()
                val addFlagCacheTaskProvider = project.tasks.register<AConfigCreateCacheTask>(
                    "generate${variantName}FlagCache_$pkgName"
                ) {
                    aconfigPath.set(aconfigBin)
                    packageName = pkgName
                    srcFiles.setFrom(it.srcFile)
                    outputFile.set(
                        project.layout.buildDirectory.file(
                            "intermediates/${variant.name}/aconfig/flag-cache-$pkgName.pb"
                        )
                    )
                }
                val addFlagLibTaskProvider = project.tasks.register<AConfigCreateJavaLibTask>(
                    "generate${variantName}FlagLib_$pkgName"
                ) {
                    aconfigPath.set(aconfigBin)
                    cacheFile.set(
                        addFlagCacheTaskProvider.flatMap(AConfigCreateCacheTask::outputFile)
                    )
                }
                variant.sources.java?.addGeneratedSourceDirectory(
                    addFlagLibTaskProvider,
                    AConfigCreateJavaLibTask::outputFolder
                )
            }
        }
    }
}
