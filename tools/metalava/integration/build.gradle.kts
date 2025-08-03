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

// Integration test project that analyzes an external dependency: see libraryToRunAgainst.
// Set INTEGRATION=true to enable.

import com.android.build.api.attributes.BuildTypeAttr

plugins {
    id("com.android.library") // needed for bootClasspath and AAR transforms to work
}

repositories {
    google()
    mavenCentral()
}

// Create two configurations used in dependencies {} block.
val runner: Configuration by configurations.creating
val libraryToRunAgainst: Configuration by configurations.creating {
    isCanBeResolved = false
    isCanBeConsumed = false
}

dependencies {
    libraryToRunAgainst("androidx.compose.foundation:foundation:1.4.3")
    runner(project(":metalava"))
}

android { // minimal set up to make com.android.library plugin work
    compileSdkVersion = "android-33"
    namespace = "com.android.tools.metalava.integration"
}

/**
 * Configuration used to resolve source jars for projects in libraryToRunAgainst
 */
val sources: Configuration by configurations.creating {
    extendsFrom(libraryToRunAgainst)
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, project.objects.named(Usage.JAVA_RUNTIME))
        attribute(Category.CATEGORY_ATTRIBUTE, project.objects.named(Category.DOCUMENTATION))
        attribute(DocsType.DOCS_TYPE_ATTRIBUTE, project.objects.named(DocsType.SOURCES))
        attribute(
            LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE,
            project.objects.named(LibraryElements.JAR)
        )
    }
}

fun Configuration.setResolveClasspathForUsage(usage: String) {
    isCanBeConsumed = false
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, project.objects.named<Usage>(usage))
        attribute(Category.CATEGORY_ATTRIBUTE, project.objects.named<Category>(Category.LIBRARY))
        attribute(BuildTypeAttr.ATTRIBUTE, project.objects.named<BuildTypeAttr>("release"))
    }
    extendsFrom(sources)
}

/**
 * Configuration used to resolve compile classpath for projects in libraryToRunAgainst
 */
val sourcesCompileClasspath: Configuration by configurations.creating {
    setResolveClasspathForUsage(Usage.JAVA_API)
}
/**
 * Configuration used to resolve runtime classpath for projects in libraryToRunAgainst
 */
val sourcesRuntimeClasspath: Configuration by configurations.creating {
    setResolveClasspathForUsage(Usage.JAVA_RUNTIME)
}
/**
 * Full classpath of all the dependencies needed to analyze projects in libraryToRunAgainst
 * This includes android.jar, compile and runtime jars
 */
val sourceDependencyClasspath: FileCollection = files(android.bootClasspath) +
    sourcesCompileClasspath.incoming.artifactView {
        attributes {
            attribute(Attribute.of("artifactType", String::class.java), "android-classes")
        }
    }.files + sourcesRuntimeClasspath.incoming.artifactView {
        attributes {
            attribute(Attribute.of("artifactType", String::class.java), "android-classes")
        }
    }.files

@CacheableTask
abstract class MetalavaRunner : DefaultTask() {
    @get:Inject
    abstract val execOperations: ExecOperations
    @get:[InputFiles PathSensitive(PathSensitivity.NONE)]
    abstract var sources: File
    @get:[InputFile PathSensitive(PathSensitivity.NONE)]
    abstract val apiLintBaseline: RegularFileProperty
    @get:Classpath
    abstract val dependencyClasspath: ConfigurableFileCollection
    @get:Classpath
    abstract val metalavaClasspath: ConfigurableFileCollection
    @get:OutputFile
    abstract val signatureFile: RegularFileProperty

    @TaskAction
    fun doThings() {
        // An approximation of AndroidX usage of metalava for tracking public
        // api surface of a library and running API lint against it.
        execOperations.javaexec {
            mainClass.set("com.android.tools.metalava.Driver")
            classpath = metalavaClasspath
            args = listOf(
                "--source-path",
                sources.absolutePath,
                "--api",
                signatureFile.get().asFile.absolutePath,
                "--classpath",
                dependencyClasspath.files.joinToString(File.pathSeparator),
                "--hide-annotation",
                "androidx.annotation.RestrictTo",
                "--show-unannotated",
                "--api-lint",
                "--baseline",
                apiLintBaseline.get().asFile.absolutePath,
                "--hide",
                listOf(
                    "Enum",
                    "StartWithLower",
                    "MissingJvmstatic",
                    "ArrayReturn",
                    "UserHandleName",
                ).joinToString(),
            )
        }
    }
}

interface Injected {
    @get:Inject val archiveOperations: ArchiveOperations
}

/**
 * A task that will extract all of the source jars that are added to libraryToRunAgainst
 * configuration.
 */
val copyInputSources = tasks.register<Sync>("copyInputSources") {
    // Store archiveOperations into a local variable to prevent access to project object
    // during the task execution, as that breaks configuration caching.
    val archiveOperations = project.objects.newInstance<Injected>().archiveOperations
    from(
        sources.incoming.artifactView { }.files.elements.map { jars ->
            jars.map { jar ->
                archiveOperations.zipTree(jar)
            }
        }
    )
    into(layout.buildDirectory.dir("inputSources"))
}

tasks.register<MetalavaRunner>("run") {
    dependsOn(copyInputSources)
    sources = copyInputSources.get().destinationDir
    dependencyClasspath.from(sourceDependencyClasspath)
    metalavaClasspath.from(runner)
    signatureFile.set(layout.buildDirectory.file("current.txt"))
    apiLintBaseline.set(layout.projectDirectory.file("api_lint.ignore"))
    outputs.upToDateWhen { false }
}
