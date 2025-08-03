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

package com.android.tools.metalava.buildinfo

import com.android.tools.metalava.buildinfo.LibraryBuildInfoFile.Check
import com.android.tools.metalava.version
import com.google.gson.GsonBuilder
import java.io.File
import java.io.Serializable
import java.util.Objects
import java.util.concurrent.TimeUnit
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.ProjectComponentPublication
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.bundling.Zip

const val CREATE_BUILD_INFO_TASK = "createBuildInfo"

abstract class CreateLibraryBuildInfoTask : DefaultTask() {
    @get:Input abstract val artifactId: Property<String>
    @get:Input abstract val groupId: Property<String>
    @get:Input abstract val version: Property<String>
    @get:Input abstract val sha: Property<String>
    @get:Input abstract val projectZipPath: Property<String>
    @get:Input abstract val projectDirectoryRelativeToRootProject: Property<String>
    @get:Input abstract val dependencyList: ListProperty<LibraryBuildInfoFile.Dependency>

    @get:OutputFile abstract val outputFile: Property<File>

    @TaskAction
    fun createFile() {
        val info = LibraryBuildInfoFile()
        info.artifactId = artifactId.get()
        info.groupId = groupId.get()
        info.groupIdRequiresSameVersion = true
        info.version = version.get()
        info.path = projectDirectoryRelativeToRootProject.get()
        info.sha = sha.get()
        info.projectZipPath = projectZipPath.get()
        info.dependencies = dependencyList.get()
        info.checks = arrayListOf()
        val gson = GsonBuilder().setPrettyPrinting().create()
        val serializedInfo: String = gson.toJson(info)
        outputFile.get().writeText(serializedInfo)
    }
}

internal fun configureBuildInfoTask(
    project: Project,
    mavenPublication: MavenPublication,
    inCI: Boolean,
    distributionDirectory: File,
    archiveTaskProvider: TaskProvider<Zip>
): TaskProvider<CreateLibraryBuildInfoTask> {
    // Unfortunately, dependency information is only available through internal API
    // (See https://github.com/gradle/gradle/issues/21345).
    val dependencies =
        (mavenPublication as ProjectComponentPublication).component.map {
            it.usages.orEmpty().flatMap { it.dependencies }
        }

    return project.tasks.register(CREATE_BUILD_INFO_TASK, CreateLibraryBuildInfoTask::class.java) {
        it.artifactId.set(project.provider { project.name })
        it.groupId.set(project.provider { project.group as String })
        it.version.set(project.version())
        it.projectDirectoryRelativeToRootProject.set(
            project.provider {
                "/" + project.layout.projectDirectory.asFile.relativeTo(project.rootDir).toString()
            }
        )
        // Only set sha when in CI to keep local builds faster
        it.sha.set(project.provider { if (inCI) project.providers.exec {
            it.workingDir = project.projectDir
            it.commandLine("git", "rev-parse", "--verify", "HEAD")
        }.standardOutput.asText.get().trim() else "" })
        it.dependencyList.set(dependencies.map { it.asBuildInfoDependencies() })
        it.projectZipPath.set(archiveTaskProvider.flatMap { task -> task.archiveFileName })
        it.outputFile.set(
            project.provider {
                File(
                    distributionDirectory,
                    "build-info/${project.group}_${project.name}_build_info.txt"
                )
            }
        )
    }
}

fun List<Dependency>.asBuildInfoDependencies() =
    filter { it.group?.startsWith("com.android.tools.metalava") ?: false }
        .map {
            LibraryBuildInfoFile.Dependency().apply {
                this.artifactId = it.name.toString()
                this.groupId = it.group.toString()
                this.version = it.version.toString()
                this.isTipOfTree = it is ProjectDependency
            }
        }
        .toHashSet()
        .sortedWith(compareBy({ it.groupId }, { it.artifactId }, { it.version }))

/**
 * Object outlining the format of a library's build info file. This object will be serialized to
 * json. This file should match the corresponding class in Jetpad because this object will be
 * serialized to json and the result will be parsed by Jetpad. DO NOT TOUCH.
 *
 * @property groupId library maven group Id
 * @property artifactId library maven artifact Id
 * @property version library maven version
 * @property path local project directory path used for development, rooted at framework/support
 * @property sha the sha of the latest commit to modify the library (aka a commit that touches a
 *   file within [path])
 * @property groupIdRequiresSameVersion boolean that determines if all libraries with [groupId] have
 *   the same version
 * @property dependencies a list of dependencies on other androidx libraries
 * @property checks arraylist of [Check]s that is used by Jetpad
 */
class LibraryBuildInfoFile {
    var groupId: String? = null
    var artifactId: String? = null
    var version: String? = null
    var path: String? = null
    var sha: String? = null
    var projectZipPath: String? = null
    var groupIdRequiresSameVersion: Boolean? = null
    var dependencies: List<Dependency> = arrayListOf()
    var checks: ArrayList<Check> = arrayListOf()

    /** @property isTipOfTree boolean that specifies whether the dependency is tip-of-tree */
    class Dependency : Serializable {
        var groupId: String? = null
        var artifactId: String? = null
        var version: String? = null
        var isTipOfTree = false

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || javaClass != other.javaClass) return false
            other as Dependency
            return isTipOfTree == other.isTipOfTree &&
                groupId == other.groupId &&
                artifactId == other.artifactId &&
                version == other.version
        }

        override fun hashCode(): Int {
            return Objects.hash(groupId, artifactId, version, isTipOfTree)
        }

        companion object {
            private const val serialVersionUID = 346431634564L
        }
    }

    inner class Check {
        var name: String? = null
        var passing = false
    }
}
