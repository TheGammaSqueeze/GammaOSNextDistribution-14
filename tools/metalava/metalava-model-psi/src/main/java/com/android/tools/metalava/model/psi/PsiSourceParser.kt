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

package com.android.tools.metalava.model.psi

import com.android.SdkConstants
import com.android.tools.lint.UastEnvironment
import com.android.tools.lint.annotations.Extractor
import com.android.tools.lint.computeMetadata
import com.android.tools.lint.detector.api.Project
import com.android.tools.metalava.model.AnnotationManager
import com.android.tools.metalava.model.ClassResolver
import com.android.tools.metalava.model.noOpAnnotationManager
import com.android.tools.metalava.model.source.DEFAULT_JAVA_LANGUAGE_LEVEL
import com.android.tools.metalava.model.source.DEFAULT_KOTLIN_LANGUAGE_LEVEL
import com.android.tools.metalava.model.source.SourceCodebase
import com.android.tools.metalava.model.source.SourceParser
import com.android.tools.metalava.model.source.SourceSet
import com.android.tools.metalava.model.source.utils.OVERVIEW_HTML
import com.android.tools.metalava.model.source.utils.PACKAGE_HTML
import com.android.tools.metalava.model.source.utils.findPackage
import com.android.tools.metalava.reporter.Reporter
import com.intellij.pom.java.LanguageLevel
import java.io.File
import org.jetbrains.kotlin.config.ApiVersion
import org.jetbrains.kotlin.config.JVMConfigurationKeys
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.config.LanguageVersion
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.config.LanguageVersionSettingsImpl

internal val defaultJavaLanguageLevel = LanguageLevel.parse(DEFAULT_JAVA_LANGUAGE_LEVEL)!!

internal val defaultKotlinLanguageLevel = LanguageVersionSettingsImpl.DEFAULT

fun kotlinLanguageVersionSettings(value: String?): LanguageVersionSettings {
    val languageLevel =
        LanguageVersion.fromVersionString(value)
            ?: throw IllegalStateException(
                "$value is not a valid or supported Kotlin language level"
            )
    val apiVersion = ApiVersion.createByLanguageVersion(languageLevel)
    return LanguageVersionSettingsImpl(languageLevel, apiVersion)
}

/**
 * Parses a set of sources into a [PsiBasedCodebase].
 *
 * The codebases will use a project environment initialized according to the properties passed to
 * the constructor and the paths passed to [parseSources].
 */
internal class PsiSourceParser(
    private val psiEnvironmentManager: PsiEnvironmentManager,
    private val reporter: Reporter,
    private val annotationManager: AnnotationManager = noOpAnnotationManager,
    private val javaLanguageLevel: LanguageLevel = defaultJavaLanguageLevel,
    private val kotlinLanguageLevel: LanguageVersionSettings = defaultKotlinLanguageLevel,
    private val useK2Uast: Boolean = false,
    private val jdkHome: File? = null,
) : SourceParser {

    override fun getClassResolver(classPath: List<File>): ClassResolver {
        val uastEnvironment = loadUastFromJars(classPath)
        return PsiBasedClassResolver(uastEnvironment, annotationManager, reporter)
    }

    /**
     * Returns a codebase initialized from the given Java or Kotlin source files, with the given
     * description.
     *
     * All supplied [File] objects will be mapped to [File.getAbsoluteFile].
     */
    override fun parseSources(
        sourceSet: SourceSet,
        commonSourceSet: SourceSet,
        description: String,
        classPath: List<File>,
    ): PsiBasedCodebase {
        return parseAbsoluteSources(
            sourceSet.absoluteCopy().extractRoots(reporter),
            commonSourceSet.absoluteCopy().extractRoots(reporter),
            description,
            classPath.map { it.absoluteFile }
        )
    }

    /** Returns a codebase initialized from the given set of absolute files. */
    private fun parseAbsoluteSources(
        sourceSet: SourceSet,
        commonSourceSet: SourceSet,
        description: String,
        classpath: List<File>,
    ): PsiBasedCodebase {
        val config = UastEnvironment.Configuration.create(useFirUast = useK2Uast)
        config.javaLanguageLevel = javaLanguageLevel

        val rootDir = sourceSet.sourcePath.firstOrNull() ?: File("").canonicalFile

        if (commonSourceSet.sources.isNotEmpty()) {
            configureUastEnvironmentForKMP(
                config,
                sourceSet.sources,
                commonSourceSet.sources,
                classpath,
                rootDir
            )
        } else {
            configureUastEnvironment(config, sourceSet.sourcePath, classpath, rootDir)
        }
        // K1 UAST: loading of JDK (via compiler config, i.e., only for FE1.0), when using JDK9+
        jdkHome?.let {
            if (isJdkModular(it)) {
                config.kotlinCompilerConfig.put(JVMConfigurationKeys.JDK_HOME, it)
                config.kotlinCompilerConfig.put(JVMConfigurationKeys.NO_JDK, false)
            }
        }

        val environment = psiEnvironmentManager.createEnvironment(config)

        val kotlinFiles = sourceSet.sources.filter { it.path.endsWith(SdkConstants.DOT_KT) }
        environment.analyzeFiles(kotlinFiles)

        val units = Extractor.createUnitsForFiles(environment.ideaProject, sourceSet.sources)
        val packageDocs = gatherPackageJavadoc(sourceSet)

        val codebase = PsiBasedCodebase(rootDir, description, annotationManager, reporter)
        codebase.initializeFromSources(environment, units, packageDocs)
        return codebase
    }

    private fun isJdkModular(homePath: File): Boolean {
        return File(homePath, "jmods").isDirectory
    }

    override fun loadFromJar(apiJar: File, preFiltered: Boolean): SourceCodebase {
        val environment = loadUastFromJars(listOf(apiJar))
        val codebase =
            PsiBasedCodebase(apiJar, "Codebase loaded from $apiJar", annotationManager, reporter)
        codebase.initializeFromJar(environment, apiJar, preFiltered)
        return codebase
    }

    /** Initializes a UAST environment using the [apiJars] as classpath roots. */
    private fun loadUastFromJars(apiJars: List<File>): UastEnvironment {
        val config = UastEnvironment.Configuration.create(useFirUast = useK2Uast)
        // Use the empty dir otherwise this will end up scanning the current working directory.
        configureUastEnvironment(config, listOf(psiEnvironmentManager.emptyDir), apiJars)

        val environment = psiEnvironmentManager.createEnvironment(config)
        environment.analyzeFiles(emptyList()) // Initializes PSI machinery.
        return environment
    }

    private fun configureUastEnvironment(
        config: UastEnvironment.Configuration,
        sourceRoots: List<File>,
        classpath: List<File>,
        rootDir: File = sourceRoots.firstOrNull() ?: File("").canonicalFile
    ) {
        // TODO(jsjeon): should set language version _per_ module (Lint Project)
        val lintClient = MetalavaCliClient(kotlinLanguageLevel)
        // From ...lint.detector.api.Project, `dir` is, e.g., /tmp/foo/dev/src/project1,
        // and `referenceDir` is /tmp/foo/. However, in many use cases, they are just same.
        // `referenceDir` is used to adjust `lib` dir accordingly if needed,
        // but we set `classpath` anyway below.
        val lintProject =
            Project.create(lintClient, /* dir = */ rootDir, /* referenceDir = */ rootDir)
        lintProject.javaSourceFolders.addAll(sourceRoots)
        lintProject.javaLibraries.addAll(classpath)
        config.addModules(
            listOf(
                UastEnvironment.Module(
                    lintProject,
                    // K2 UAST: building KtSdkModule for JDK
                    jdkHome,
                    includeTests = false,
                    includeTestFixtureSources = false,
                    isUnitTest = false
                )
            ),
        )
    }

    private fun configureUastEnvironmentForKMP(
        config: UastEnvironment.Configuration,
        sourceFiles: List<File>,
        commonSourceFiles: List<File>,
        classpath: List<File>,
        rootDir: File,
    ) {
        // TODO(b/322111050): consider providing a nice DSL at Lint level
        val projectXml = File.createTempFile("project", ".xml", rootDir)

        fun describeSources(sources: List<File>) = buildString {
            for (source in sources) {
                if (!source.isFile) continue
                appendLine("    <src file=\"${source.absolutePath}\" />")
            }
        }

        fun describeClasspath() = buildString {
            for (dep in classpath) {
                // TODO: what other kinds of dependencies?
                if (dep.extension !in SUPPORTED_CLASSPATH_EXT) continue
                appendLine("    <classpath ${dep.extension}=\"${dep.absolutePath}\" />")
            }
        }

        // We're about to build the description of Lint's project model.
        // Alas, no proper documentation is available. Please refer to examples at upstream Lint:
        // https://cs.android.com/android-studio/platform/tools/base/+/mirror-goog-studio-main:lint/libs/lint-tests/src/test/java/com/android/tools/lint/ProjectInitializerTest.kt
        //
        // An ideal project structure would look like:
        //
        // <project>
        //   <root dir="frameworks/support/compose/ui/ui"/>
        //   <module name="commonMain" android="false">
        //     <src file="src/commonMain/.../file1.kt" /> <!-- and so on -->
        //     <klib file="lib/if/any.klib" />
        //     <classpath jar="/path/to/kotlin/coroutinesCore.jar" />
        //     ...
        //   </module>
        //   <module name="jvmMain" android="false">
        //     <dep module="commonMain" kind="dependsOn" />
        //     <src file="src/jvmMain/.../file1.kt" /> <!-- and so on -->
        //     ...
        //   </module>
        //   <module name="androidMain" android="true">
        //     <dep module="jvmMain" kind="dependsOn" />
        //     <src file="src/androidMain/.../file1.kt" /> <!-- and so on -->
        //     ...
        //   </module>
        //   ...
        // </project>
        //
        // That is, there are common modules where `expect` declarations and common business logic
        // reside, along with binary dependencies of several formats, including klib and jar.
        // Then, platform-specific modules "depend" on common modules, and have their own source set
        // and binary dependencies.
        //
        // For now, with --common-source-path, common source files are isolated, but the project
        // structure is not fully conveyed. Therefore, we will reuse the same binary dependencies
        // for all modules (which only(?) cause performance degradation on binary resolution).
        val description = buildString {
            appendLine("""<?xml version="1.0" encoding="utf-8"?>""")
            appendLine("<project>")
            appendLine("  <root dir=\"${rootDir.absolutePath}\" />")
            appendLine("  <module name=\"commonMain\" android=\"false\" >")
            append(describeSources(commonSourceFiles))
            append(describeClasspath())
            appendLine("  </module>")
            appendLine("  <module name=\"app\" >")
            appendLine("    <dep module=\"commonMain\" kind=\"dependsOn\" />")
            // NB: While K2 requires separate common / platform-specific source roots, K1 still
            // needs to receive all source roots at once. Thus, existing usages (e.g., androidx)
            // often pass all source files, according to compiler configuration.
            // To make a correct module structure, we need to filter out common source files here.
            // TODO: once fully switching to K2 and androidx usage is adjusted, we won't need this.
            append(describeSources(sourceFiles - commonSourceFiles))
            append(describeClasspath())
            appendLine("  </module>")
            appendLine("</project>")
        }
        projectXml.writeText(description)

        // TODO: use Lint's [withKMPEnabled] util when available
        val languageLevel = LanguageVersion.fromVersionString(DEFAULT_KOTLIN_LANGUAGE_LEVEL)!!
        val apiVersion = ApiVersion.createByLanguageVersion(languageLevel)
        val kotlinLanguageLevel =
            LanguageVersionSettingsImpl(
                languageLevel,
                apiVersion,
                emptyMap(),
                mapOf(LanguageFeature.MultiPlatformProjects to LanguageFeature.State.ENABLED),
            )
        val lintClient = MetalavaCliClient(kotlinLanguageLevel)
        // This will parse the description of Lint's project model and populate the module structure
        // inside the given Lint client. We will use it to set up the project structure that
        // [UastEnvironment] requires, which in turn uses that to set up Kotlin compiler frontend.
        // The overall flow looks like:
        //   project.xml -> Lint Project model -> UastEnvironment Module -> Kotlin compiler FE / AA
        // There are a couple of limitations that force use fall into this long steps:
        //  * Lint Project creation is not exposed at all. Only project.xml parsing is available.
        //  * UastEnvironment Module simply reuses existing Lint Project model.
        computeMetadata(lintClient, projectXml)
        config.addModules(
            lintClient.knownProjects.map { module ->
                UastEnvironment.Module(
                    module,
                    // K2 UAST: building KtSdkModule for JDK
                    jdkHome,
                    includeTests = false,
                    includeTestFixtureSources = false,
                    isUnitTest = false
                )
            }
        )
    }

    companion object {
        private const val AAR = "aar"
        private const val JAR = "jar"
        private const val KLIB = "klib"
        private val SUPPORTED_CLASSPATH_EXT = listOf(AAR, JAR, KLIB)
    }
}

private fun gatherPackageJavadoc(sourceSet: SourceSet): PackageDocs {
    val packageComments = HashMap<String, String>(100)
    val overviewHtml = HashMap<String, String>(10)
    val hiddenPackages = HashSet<String>(100)
    val sortedSourceRoots = sourceSet.sourcePath.sortedBy { -it.name.length }
    for (file in sourceSet.sources) {
        var javadoc = false
        val map =
            when (file.name) {
                PACKAGE_HTML -> {
                    javadoc = true
                    packageComments
                }
                OVERVIEW_HTML -> {
                    overviewHtml
                }
                else -> continue
            }
        var contents = file.readText(Charsets.UTF_8)
        if (javadoc) {
            contents = packageHtmlToJavadoc(contents)
        }

        // Figure out the package: if there is a java file in the same directory, get the package
        // name from the java file. Otherwise, guess from the directory path + source roots.
        // NOTE: This causes metalava to read files other than the ones explicitly passed to it.
        var pkg =
            file.parentFile
                ?.listFiles()
                ?.filter { it.name.endsWith(SdkConstants.DOT_JAVA) }
                ?.asSequence()
                ?.mapNotNull { findPackage(it) }
                ?.firstOrNull()
        if (pkg == null) {
            // Strip the longest prefix source root.
            val prefix = sortedSourceRoots.firstOrNull { file.startsWith(it) }?.path ?: ""
            pkg = file.parentFile.path.substring(prefix.length).trim('/').replace("/", ".")
        }
        map[pkg] = contents
        if (contents.contains("@hide")) {
            hiddenPackages.add(pkg)
        }
    }

    return PackageDocs(packageComments, overviewHtml, hiddenPackages)
}
