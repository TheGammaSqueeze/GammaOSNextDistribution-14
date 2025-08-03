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

package com.android.tools.metalava

import java.io.ByteArrayOutputStream
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFile
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations

/** Create a configuration that includes all the dependencies required to run ktfmt. */
private fun Project.getKtfmtConfiguration(): Configuration {
    return configurations.findByName("ktfmt")
        ?: configurations.create("ktfmt") {
            val dependency = project.dependencies.create("com.facebook:ktfmt:0.44")
            it.dependencies.add(dependency)
        }
}

/** Creates two tasks for checking and formatting kotlin sources. */
fun Project.configureKtfmt() {
    tasks.register("ktCheck", KtfmtCheckTask::class.java) {
        it.description = "Check Kotlin code style."
        it.group = "Verification"
        it.ktfmtClasspath.from(getKtfmtConfiguration())
        it.cacheEvenIfNoOutputs()
    }
    tasks.register("ktFormat", KtfmtFormatTask::class.java) {
        it.description = "Fix Kotlin code style deviations."
        it.group = "formatting"
        it.ktfmtClasspath.from(getKtfmtConfiguration())
    }
}

abstract class KtfmtBaseTask : DefaultTask() {
    @get:Inject abstract val execOperations: ExecOperations

    @get:Classpath abstract val ktfmtClasspath: ConfigurableFileCollection

    @get:Inject abstract val objects: ObjectFactory

    private val shouldIncludeBuildSrc: Boolean = project.rootProject == project

    @[InputFiles PathSensitive(PathSensitivity.RELATIVE)]
    fun getInputFiles(): FileCollection {
        var files =
            objects.fileTree().setDir("src").apply { include("**/*.kt") } +
                objects.fileCollection().apply { from("build.gradle.kts") }
        if (shouldIncludeBuildSrc) {
            files += objects.fileTree().setDir("buildSrc/src").apply { include("**/*.kt") }
        }
        return files
    }

    fun getArgs(dryRun: Boolean): List<String> {
        return if (dryRun) {
            listOf("--kotlinlang-style", "--dry-run") +
                getInputFiles().files.map { it.absolutePath }
        } else {
            listOf("--kotlinlang-style") + getInputFiles().files.map { it.absolutePath }
        }
    }
}

/** A task that formats the Kotlin code. */
abstract class KtfmtFormatTask : KtfmtBaseTask() {
    // Output needs to be defined for this task as it rewrites these files
    @OutputFiles
    fun getOutputFiles(): FileCollection {
        return getInputFiles()
    }

    @TaskAction
    fun doChecking() {
        execOperations.javaexec {
            it.mainClass.set("com.facebook.ktfmt.cli.Main")
            it.classpath = ktfmtClasspath
            it.args = getArgs(dryRun = false)
        }
    }
}

/** A task that checks of the Kotlin code passes formatting checks. */
abstract class KtfmtCheckTask : KtfmtBaseTask() {
    @TaskAction
    fun doChecking() {
        val outputStream = ByteArrayOutputStream()
        execOperations.javaexec {
            it.standardOutput = outputStream
            it.mainClass.set("com.facebook.ktfmt.cli.Main")
            it.classpath = ktfmtClasspath
            it.args = getArgs(dryRun = true)
        }
        val output = outputStream.toString()
        if (output.isNotEmpty()) {
            throw Exception(
                """Failed check for the following files:
                |$output
                |
                |Run ./gradlew ktFormat to fix it."""
                    .trimMargin()
            )
        }
    }
}

// Tells Gradle to skip running this task, even if this task declares no output files
fun Task.cacheEvenIfNoOutputs() {
    this.outputs.file(this.getPlaceholderOutput())
}

// Returns a placeholder/unused output path that we can pass to Gradle to prevent Gradle from
// thinking that we forgot to declare outputs of this task, and instead to skip this task if its
// inputs are unchanged
private fun Task.getPlaceholderOutput(): Provider<RegularFile> =
    project.layout.buildDirectory.file("placeholderOutput/${name.replace(':', '-')}")
