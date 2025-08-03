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

import com.android.build.api.dsl.Lint
import com.android.tools.metalava.buildinfo.CreateAggregateLibraryBuildInfoFileTask
import com.android.tools.metalava.buildinfo.CreateAggregateLibraryBuildInfoFileTask.Companion.CREATE_AGGREGATE_BUILD_INFO_FILES_TASK
import com.android.tools.metalava.buildinfo.addTaskToAggregateBuildInfoFileTask
import com.android.tools.metalava.buildinfo.configureBuildInfoTask
import java.io.File
import java.io.StringReader
import java.util.Properties
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.component.AdhocComponentWithVariants
import org.gradle.api.internal.tasks.testing.filter.DefaultTestFilter
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.provider.Provider
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin
import org.gradle.api.publish.tasks.GenerateModuleMetadata
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.bundling.Zip
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.plugin.KotlinBasePluginWrapper
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

class MetalavaBuildPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.plugins.all { plugin ->
            when (plugin) {
                is JavaPlugin -> {
                    project.extensions.getByType<JavaPluginExtension>().apply {
                        sourceCompatibility = JavaVersion.VERSION_17
                        targetCompatibility = JavaVersion.VERSION_17
                    }
                }
                is KotlinBasePluginWrapper -> {
                    project.tasks.withType(KotlinCompile::class.java).configureEach { task ->
                        task.kotlinOptions.apply {
                            jvmTarget = "17"
                            apiVersion = "1.7"
                            languageVersion = "1.7"
                            allWarningsAsErrors = true
                        }
                    }
                }
                is MavenPublishPlugin -> {
                    configurePublishing(project)
                }
            }
        }

        configureLint(project)
        configureTestTasks(project)
        project.configureKtfmt()
        project.version = project.getMetalavaVersion()
        project.group = "com.android.tools.metalava"
    }

    fun configureLint(project: Project) {
        project.apply(mapOf("plugin" to "com.android.lint"))
        project.extensions.getByType<Lint>().apply {
            fatal.add("UastImplementation") // go/hide-uast-impl
            fatal.add("KotlincFE10") // b/239982263
            disable.add("UseTomlInstead") // not useful for this project
            disable.add("GradleDependency") // not useful for this project
            abortOnError = true
            baseline = File("lint-baseline.xml")
        }
    }

    fun configureTestTasks(project: Project) {
        val testTask = project.tasks.named("test", Test::class.java)

        val zipTask: TaskProvider<Zip> =
            project.tasks.register("zipTestResults", Zip::class.java) { zip ->
                zip.destinationDirectory.set(
                    File(getDistributionDirectory(project), "host-test-reports")
                )
                zip.archiveFileName.set(testTask.map { "${it.path}.zip" })
                zip.from(testTask.map { it.reports.junitXml.outputLocation.get() })
            }

        testTask.configure { task ->
            task as Test
            task.jvmArgs = listOf(
                "--add-opens=java.base/java.lang=ALL-UNNAMED",
                // Needed for CustomizableParameterizedRunner
                "--add-opens=java.base/java.lang.reflect=ALL-UNNAMED",
            )

            task.doFirst {
                // Before running the tests update the filter.
                task.filter { testFilter ->
                    testFilter as DefaultTestFilter

                    // The majority of Metalava tests are now parameterized, as they run against
                    // multiple providers. As parameterized tests they include a suffix of `[....]`
                    // after the method name that contains the arguments for those parameters. The
                    // problem with parameterized tests is that the test name does not match the
                    // method name so when running a specific test an IDE cannot just use the
                    // method name in the test filter, it has to use a wildcard to match all the
                    // instances of the test method. When IntelliJ runs a test that has
                    // `@RunWith(org.junit.runners.Parameterized::class)` it will add `[*]` to the
                    // end of the test filter to match all instances of that test method.
                    // Unfortunately, that only applies to tests that explicitly use
                    // `org.junit.runners.Parameterized` and the Metalava tests use their own
                    // custom runner that uses `Parameterized` under the covers. Without the `[*]`,
                    // any attempt to run a specific parameterized test method just results in an
                    // error that "no tests matched".
                    //
                    // This code avoids that by checking the patterns that have been provided on the
                    // command line and adding a wildcard. It cannot add `[*]` as that would cause
                    // a "no tests matched" error for non-parameterized tests and while most tests
                    // in Metalava are parameterized, some are not. Also, it is necessary to be able
                    // to run a specific instance of a test with a specific set of arguments.
                    //
                    // This code adds a `*` to the end of the pattern if it does not already end
                    // with a `*` or a `\]`. i.e.:
                    // * "pkg.ClassTest" will become "pkg.ClassTest*". That does run the risk of
                    //   matching other classes, e.g. "ClassTestOther" but they are unlikely to
                    //   exist and can be renamed if it becomes an issue.
                    // * "pkg.ClassTest.method" will become "pkg.ClassTest.method*". That does run
                    //   the risk of running other non-parameterized methods, e.g.
                    //   "pkg.ClassTest.methodWithSuffix" but again they can be renamed if it
                    //   becomes an issue.
                    // * "pkg.ClassTest.method[*]" will be unmodified and will match any
                    //   parameterized instance of the method.
                    // * "pkg.ClassTest.method[a,b]" will be unmodified and will match a specific
                    //   parameterized instance of the method.
                    val commandLineIncludePatterns = testFilter.commandLineIncludePatterns
                    if (commandLineIncludePatterns.isNotEmpty()) {
                        val transformedPatterns = commandLineIncludePatterns.map { pattern ->

                            if (!pattern.endsWith("]") && !pattern.endsWith("*")) {
                                "$pattern*"
                            } else {
                                pattern
                            }
                        }
                        testFilter.setCommandLineIncludePatterns(transformedPatterns)
                    }
                }
            }

            task.maxParallelForks =
                (Runtime.getRuntime().availableProcessors() / 2).takeIf { it > 0 } ?: 1
            task.testLogging.events =
                hashSetOf(
                    TestLogEvent.FAILED,
                    TestLogEvent.STANDARD_OUT,
                    TestLogEvent.STANDARD_ERROR
                )
            task.finalizedBy(zipTask)
            if (isBuildingOnServer()) task.ignoreFailures = true
        }
    }

    fun configurePublishing(project: Project) {
        val projectRepo = project.layout.buildDirectory.dir("repo")
        val archiveTaskProvider =
            configurePublishingArchive(
                project,
                publicationName,
                repositoryName,
                getBuildId(),
                getDistributionDirectory(project),
                projectRepo,
            )

        project.extensions.getByType<PublishingExtension>().apply {
            publications { publicationContainer ->
                publicationContainer.create<MavenPublication>(publicationName) {
                    val javaComponent = project.components["java"] as AdhocComponentWithVariants
                    // Disable publishing of test fixtures as we consider them internal
                    project.configurations.findByName("testFixturesApiElements")?.let {
                        javaComponent.withVariantsFromConfiguration(it) { it.skip() }
                    }
                    project.configurations.findByName("testFixturesRuntimeElements")?.let {
                        javaComponent.withVariantsFromConfiguration(it) { it.skip() }
                    }
                    from(javaComponent)
                    suppressPomMetadataWarningsFor("testFixturesApiElements")
                    suppressPomMetadataWarningsFor("testFixturesRuntimeElements")
                    pom { pom ->
                        pom.licenses { spec ->
                            spec.license { license ->
                                license.name.set("The Apache License, Version 2.0")
                                license.url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                            }
                        }
                        pom.developers { spec ->
                            spec.developer { developer ->
                                developer.name.set("The Android Open Source Project")
                            }
                        }
                        pom.scm { scm ->
                            scm.connection.set(
                                "scm:git:https://android.googlesource.com/platform/tools/metalava"
                            )
                            scm.url.set("https://android.googlesource.com/platform/tools/metalava/")
                        }
                    }

                    val buildInfoTask =
                        configureBuildInfoTask(
                            project,
                            this,
                            isBuildingOnServer(),
                            getDistributionDirectory(project),
                            archiveTaskProvider
                        )
                    project.addTaskToAggregateBuildInfoFileTask(buildInfoTask)
                }
            }
            repositories { handler ->
                handler.maven { repository ->
                    repository.url =
                        project.uri(
                            "file://${
                                getDistributionDirectory(project).canonicalPath
                            }/repo/m2repository"
                        )
                }
                handler.maven { repository ->
                    repository.name = repositoryName
                    repository.url = project.uri(projectRepo)
                }
            }
        }

        // Add a buildId into Gradle Metadata file so we can tell which build it is from.
        project.tasks.withType(GenerateModuleMetadata::class.java).configureEach { task ->
            val outDirProvider = project.providers.environmentVariable("DIST_DIR")
            task.inputs.property("buildOutputDirectory", outDirProvider).optional(true)
            task.doLast {
                val metadata = (it as GenerateModuleMetadata).outputFile.asFile.get()
                val text = metadata.readText()
                val buildId = outDirProvider.orNull?.let { File(it).name } ?: "0"
                metadata.writeText(
                    text.replace(
                        """"createdBy": {
    "gradle": {""",
                        """"createdBy": {
    "gradle": {
      "buildId:": "$buildId",""",
                    )
                )
            }
        }
    }
}

internal fun Project.version(): Provider<String> {
    @Suppress("UNCHECKED_CAST") // version is a VersionProviderWrapper set in MetalavaBuildPlugin
    return (version as VersionProviderWrapper).versionProvider
}

// https://github.com/gradle/gradle/issues/25971
private class VersionProviderWrapper(val versionProvider: Provider<String>) {
    override fun toString(): String {
        return versionProvider.get()
    }
}

private fun Project.getMetalavaVersion(): VersionProviderWrapper {
    val contents =
        providers.fileContents(
            rootProject.layout.projectDirectory.file("version.properties")
        )
    return VersionProviderWrapper(
        contents.asText.map {
            val versionProps = Properties()
            versionProps.load(StringReader(it))
            versionProps["metalavaVersion"]!! as String
        }
    )
}

/**
 * The build server will copy the contents of the distribution directory and make it available for
 * download.
 */
internal fun getDistributionDirectory(project: Project): File {
    return if (System.getenv("DIST_DIR") != null) {
        File(System.getenv("DIST_DIR"))
    } else {
        File(project.rootProject.projectDir, "../../out/dist")
    }
}

private fun isBuildingOnServer(): Boolean {
    return System.getenv("OUT_DIR") != null && System.getenv("DIST_DIR") != null
}

/**
 * @return build id string for current build
 *
 * The build server does not pass the build id so we infer it from the last folder of the
 * distribution directory name.
 */
private fun getBuildId(): String {
    return if (System.getenv("DIST_DIR") != null) File(System.getenv("DIST_DIR")).name else "0"
}

private const val publicationName = "Metalava"
private const val repositoryName = "Dist"
