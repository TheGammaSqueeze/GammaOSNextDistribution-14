/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.android.SdkConstants
import com.android.SdkConstants.DOT_TXT
import com.android.SdkConstants.DOT_XML
import com.android.ide.common.process.DefaultProcessExecutor
import com.android.ide.common.process.LoggedProcessOutputHandler
import com.android.ide.common.process.ProcessException
import com.android.ide.common.process.ProcessInfoBuilder
import com.android.tools.lint.LintCliClient
import com.android.tools.lint.UastEnvironment
import com.android.tools.lint.checks.ApiLookup
import com.android.tools.lint.checks.infrastructure.ClassName
import com.android.tools.lint.checks.infrastructure.TestFile
import com.android.tools.lint.checks.infrastructure.TestFiles.java
import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import com.android.tools.lint.checks.infrastructure.stripComments
import com.android.tools.lint.client.api.LintClient
import com.android.tools.metalava.cli.common.ARG_HIDE
import com.android.tools.metalava.cli.common.ARG_NO_COLOR
import com.android.tools.metalava.cli.common.ARG_QUIET
import com.android.tools.metalava.cli.common.ARG_REPEAT_ERRORS_MAX
import com.android.tools.metalava.cli.common.ARG_VERBOSE
import com.android.tools.metalava.cli.compatibility.ARG_CHECK_COMPATIBILITY_API_RELEASED
import com.android.tools.metalava.cli.compatibility.ARG_CHECK_COMPATIBILITY_BASE_API
import com.android.tools.metalava.cli.compatibility.ARG_CHECK_COMPATIBILITY_REMOVED_RELEASED
import com.android.tools.metalava.cli.compatibility.ARG_ERROR_MESSAGE_CHECK_COMPATIBILITY_RELEASED
import com.android.tools.metalava.cli.signature.ARG_FORMAT
import com.android.tools.metalava.model.provider.Capability
import com.android.tools.metalava.model.psi.PsiModelOptions
import com.android.tools.metalava.model.source.SourceModelProvider
import com.android.tools.metalava.model.source.SourceSet
import com.android.tools.metalava.model.source.utils.DOT_KT
import com.android.tools.metalava.model.testing.CodebaseCreatorConfig
import com.android.tools.metalava.model.testing.CodebaseCreatorConfigAware
import com.android.tools.metalava.model.text.ApiClassResolution
import com.android.tools.metalava.model.text.ApiFile
import com.android.tools.metalava.model.text.FileFormat
import com.android.tools.metalava.model.text.assertSignatureFilesMatch
import com.android.tools.metalava.model.text.prepareSignatureFileForTest
import com.android.tools.metalava.reporter.Severity
import com.android.tools.metalava.testing.KnownSourceFiles
import com.android.tools.metalava.testing.TemporaryFolderOwner
import com.android.tools.metalava.testing.findKotlinStdlibPaths
import com.android.tools.metalava.testing.getAndroidJar
import com.android.tools.metalava.xml.parseDocument
import com.android.utils.SdkUtils
import com.android.utils.StdLogger
import com.google.common.io.Closeables
import com.intellij.openapi.util.Disposer
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.PrintStream
import java.io.PrintWriter
import java.io.StringWriter
import java.net.URL
import kotlin.text.Charsets.UTF_8
import org.intellij.lang.annotations.Language
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.rules.ErrorCollector
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

@RunWith(DriverTestRunner::class)
abstract class DriverTest : CodebaseCreatorConfigAware<SourceModelProvider>, TemporaryFolderOwner {
    @get:Rule override val temporaryFolder = TemporaryFolder()

    @get:Rule val errorCollector = ErrorCollector()

    /** The [CodebaseCreatorConfig] under which this test will be run. */
    final override lateinit var codebaseCreatorConfig: CodebaseCreatorConfig<SourceModelProvider>

    /**
     * The setting of [PsiModelOptions.useK2Uast]. Is computed lazily as it depends on
     * [codebaseCreatorConfig] which is set after object initialization.
     */
    protected val isK2 by
        lazy(LazyThreadSafetyMode.NONE) {
            codebaseCreatorConfig.modelOptions[PsiModelOptions.useK2Uast]
        }

    @Before
    fun setup() {
        System.setProperty(ENV_VAR_METALAVA_TESTS_RUNNING, SdkConstants.VALUE_TRUE)
        Disposer.setDebugMode(true)
    }

    // Makes a note to fail the test, but still allows the test to complete before failing
    protected fun addError(error: String) {
        errorCollector.addError(Throwable(error))
    }

    protected fun getApiFile(): File {
        return File(temporaryFolder.root.path, "public-api.txt")
    }

    private fun runDriver(
        // The SameParameterValue check reports that this is passed the same value because the first
        // value that is passed is always the same but this is a varargs parameter so other values
        // that are passed matter, and they are not the same.
        args: Array<String>,
        expectedFail: String,
        reporterEnvironment: ReporterEnvironment,
        testEnvironment: TestEnvironment,
    ): String {
        // Capture the actual input and output from System.out/err and compare it to the output
        // printed through the official writer; they should be the same, otherwise we have stray
        // print calls littered in the code!
        val previousOut = System.out
        val previousErr = System.err
        try {
            val output = TeeWriter(previousOut)
            System.setOut(PrintStream(output))
            val error = TeeWriter(previousErr)
            System.setErr(PrintStream(error))

            val sw = StringWriter()
            val writer = PrintWriter(sw)

            Disposer.setDebugMode(true)

            val executionEnvironment =
                ExecutionEnvironment(
                    stdout = writer,
                    stderr = writer,
                    reporterEnvironment = reporterEnvironment,
                    testEnvironment = testEnvironment,
                )
            val exitCode = run(executionEnvironment, args)
            if (exitCode == 0) {
                assertTrue(
                    "Test expected to fail but didn't. Expected failure: $expectedFail",
                    expectedFail.isEmpty()
                )
            } else {
                val actualFail = cleanupString(sw.toString(), null)
                if (
                    cleanupString(expectedFail, null).replace(".", "").trim() !=
                        actualFail.replace(".", "").trim()
                ) {
                    val reportedCompatError =
                        actualFail.startsWith(
                            "Aborting: Found compatibility problems checking the "
                        )
                    if (
                        expectedFail == "Aborting: Found compatibility problems" &&
                            reportedCompatError
                    ) {
                        // Special case for compat checks; we don't want to force each one of them
                        // to pass in the right string (which may vary based on whether writing out
                        // the signature was passed at the same time
                        // ignore
                    } else {
                        if (reportedCompatError) {
                            // if a compatibility error was unexpectedly reported, then mark that as
                            // an error but keep going, so we can see the actual compatibility error
                            if (expectedFail.trimIndent() != actualFail) {
                                addError(
                                    "ComparisonFailure: expected failure $expectedFail, actual $actualFail"
                                )
                            }
                        } else {
                            // no compatibility error; check for other errors now, and
                            // if one is found, fail right away
                            assertEquals(
                                "expectedFail does not match actual failures",
                                expectedFail.trimIndent(),
                                actualFail
                            )
                        }
                    }
                }
            }

            val stdout = output.toString(UTF_8.name())
            if (stdout.isNotEmpty()) {
                addError("Unexpected write to stdout:\n $stdout")
            }
            val stderr = error.toString(UTF_8.name())
            if (stderr.isNotEmpty()) {
                addError("Unexpected write to stderr:\n $stderr")
            }

            val printedOutput = sw.toString()
            if (printedOutput.isNotEmpty() && printedOutput.trim().isEmpty()) {
                fail("Printed newlines with nothing else")
            }

            UastEnvironment.checkApplicationEnvironmentDisposed()
            Disposer.assertIsEmpty(true)

            return printedOutput
        } finally {
            System.setOut(previousOut)
            System.setErr(previousErr)
        }
    }

    // This is here, so we can keep a record of what was printed, to make sure we don't have any
    // unexpected print calls in the source that are left behind after debugging and pollute the
    // production output
    class TeeWriter(private val otherStream: PrintStream) : ByteArrayOutputStream() {
        override fun write(b: ByteArray, off: Int, len: Int) {
            otherStream.write(b, off, len)
            super.write(b, off, len)
        }

        override fun write(b: ByteArray) {
            otherStream.write(b)
            super.write(b)
        }

        override fun write(b: Int) {
            otherStream.write(b)
            super.write(b)
        }
    }

    private fun getJdkPath(): String? {
        val javaHome = System.getProperty("java.home")
        if (javaHome != null) {
            var javaHomeFile = File(javaHome)
            if (File(javaHomeFile, "bin${File.separator}javac").exists()) {
                return javaHome
            } else if (javaHomeFile.name == "jre") {
                javaHomeFile = javaHomeFile.parentFile
                if (File(javaHomeFile, "bin${File.separator}javac").exists()) {
                    return javaHomeFile.path
                }
            }
        }
        return System.getenv("JAVA_HOME")
    }

    private fun <T> buildOptionalArgs(option: T?, converter: (T) -> Array<String>): Array<String> {
        return if (option != null) {
            converter(option)
        } else {
            emptyArray()
        }
    }

    /** Test information related to a baseline file. */
    data class BaselineTestInfo(
        /**
         * The contents of the input baseline.
         *
         * If this is `null` then no baseline testing is performed.
         */
        val inputContents: String? = null,

        /** The contents of the expected updated baseline. */
        val expectedOutputContents: String? = null,

        /** Indicates whether testing of the baseline should suppress reporting of issues or not. */
        val silentUpdate: Boolean = true,
    ) {
        init {
            if (inputContents == null && expectedOutputContents != null) {
                error("`inputContents` must be non-null as `expectedOutputContents` is non-null")
            }
        }
    }

    /** Represents a check that can be performed on a baseline file. */
    @Suppress("ArrayInDataClass")
    private data class BaselineCheck(
        /** The option for the input baseline, used in test failure messages. */
        val baselineOption: String,

        /** The args to pass to metalava. */
        val args: Array<String>,

        /**
         * The input/output file.
         *
         * If this is `null` then no check is performed.
         */
        val file: File?,

        /** The expected contents of [file]. */
        val expectedFileContents: String,
    ) {
        /** Apply the baseline check. */
        fun apply() {
            file ?: return

            assertTrue(
                "${file.path} does not exist even though $baselineOption was used",
                file.exists()
            )

            val actualText = readFile(file)
            assertEquals(
                stripComments(
                    expectedFileContents.trimIndent(),
                    DOT_TXT,
                    stripLineComments = false
                ),
                actualText
            )
        }
    }

    private fun buildBaselineCheck(
        baselineOption: String,
        updateBaselineOption: String,
        filename: String,
        info: BaselineTestInfo,
    ): BaselineCheck {
        return info.inputContents?.let { inputContents ->
            val baselineFile = temporaryFolder.newFile(filename)
            baselineFile?.writeText(inputContents.trimIndent())
            val args = arrayOf(baselineOption, baselineFile.path)

            info.expectedOutputContents?.let { expectedOutputContents ->
                // If silent update is request then use the same baseline file for update as for the
                // input, otherwise create a separate update file.
                val updateFile =
                    if (info.silentUpdate) baselineFile
                    else temporaryFolder.newFile("update-$filename")

                // As expected output contents are provided add extra arguments to output the
                // baseline and then compare the baseline file against the expected output. Use the
                // update baseline option in any error messages.
                BaselineCheck(
                    updateBaselineOption,
                    args + arrayOf(updateBaselineOption, updateFile.path),
                    updateFile,
                    expectedOutputContents,
                )
            }
                ?:
                // As no expected output is provided then compare the baseline file against the
                // supplied input contents to make sure that they have not changed. Use the
                // basic baseline option in any error messages.
                BaselineCheck(
                    baselineOption,
                    args,
                    baselineFile,
                    inputContents,
                )
        }
            ?: BaselineCheck("", emptyArray(), null, "")
    }

    @Suppress("DEPRECATION")
    protected fun check(
        /** Any jars to add to the class path */
        classpath: Array<TestFile>? = null,
        /** The API signature content (corresponds to --api) */
        @Language("TEXT") api: String? = null,
        /** The API signature content (corresponds to --api-xml) */
        @Language("XML") apiXml: String? = null,
        /** The DEX API (corresponds to --dex-api) */
        dexApi: String? = null,
        /** The removed API (corresponds to --removed-api) */
        removedApi: String? = null,
        /** The subtract api signature content (corresponds to --subtract-api) */
        @Language("TEXT") subtractApi: String? = null,
        /** Expected stubs (corresponds to --stubs) */
        stubFiles: Array<TestFile> = emptyArray(),
        /** Expected paths of stub files created */
        stubPaths: Array<String>? = null,
        /**
         * Whether the stubs should be written as documentation stubs instead of plain stubs.
         * Decides whether the stubs include @doconly elements, uses rewritten/migration
         * annotations, etc
         */
        docStubs: Boolean = false,
        /** Signature file format */
        format: FileFormat = FileFormat.LATEST,
        /** All expected issues to be generated when analyzing these sources */
        expectedIssues: String? = "",
        /** Expected [Severity.ERROR] issues to be generated when analyzing these sources */
        errorSeverityExpectedIssues: String? = null,
        checkCompilation: Boolean = false,
        /** Annotations to merge in (in .xml format) */
        @Language("XML") mergeXmlAnnotations: String? = null,
        /** Annotations to merge in (in .txt/.signature format) */
        @Language("TEXT") mergeSignatureAnnotations: String? = null,
        /** Qualifier annotations to merge in (in Java stub format) */
        @Language("JAVA") mergeJavaStubAnnotations: String? = null,
        /** Inclusion annotations to merge in (in Java stub format) */
        mergeInclusionAnnotations: Array<TestFile> = emptyArray(),
        /** Optional API signature files content to load **instead** of Java/Kotlin source files */
        @Language("TEXT") signatureSources: Array<String> = emptyArray(),
        apiClassResolution: ApiClassResolution = ApiClassResolution.API,
        /**
         * An optional API signature file content to load **instead** of Java/Kotlin source files.
         * This is added to [signatureSources]. This argument exists for backward compatibility.
         */
        @Language("TEXT") signatureSource: String? = null,
        /** An optional API jar file content to load **instead** of Java/Kotlin source files */
        apiJar: File? = null,
        /**
         * An optional API signature to check the last released API's compatibility with.
         *
         * This can either be the name of a file or the contents of the signature file. In the
         * latter case the contents are adjusted to make sure it is a valid signature file with a
         * valid header and written to a file.
         */
        @Language("TEXT") checkCompatibilityApiReleased: String? = null,
        /**
         * Allow specifying multiple instances of [checkCompatibilityApiReleased].
         *
         * In order from narrowest to widest API.
         */
        checkCompatibilityApiReleasedList: List<String> = emptyList(),
        /**
         * An optional API signature to check the last released removed API's compatibility with.
         *
         * See [checkCompatibilityApiReleased].
         */
        @Language("TEXT") checkCompatibilityRemovedApiReleased: String? = null,
        /**
         * Allow specifying multiple instances of [checkCompatibilityRemovedApiReleased].
         *
         * In order from narrowest to widest API.
         */
        checkCompatibilityRemovedApiReleasedList: List<String> = emptyList(),
        /** An optional API signature to use as the base API codebase during compat checks */
        @Language("TEXT") checkCompatibilityBaseApi: String? = null,
        @Language("TEXT") migrateNullsApi: String? = null,
        /** An optional Proguard keep file to generate */
        @Language("Proguard") proguard: String? = null,
        /** Show annotations (--show-annotation arguments) */
        showAnnotations: Array<String> = emptyArray(),
        /** "Show for stub purposes" API annotation ([ARG_SHOW_FOR_STUB_PURPOSES_ANNOTATION]) */
        showForStubPurposesAnnotations: Array<String> = emptyArray(),
        /** Hide annotations (--hide-annotation arguments) */
        hideAnnotations: Array<String> = emptyArray(),
        /** No compat check meta-annotations (--no-compat-check-meta-annotation arguments) */
        suppressCompatibilityMetaAnnotations: Array<String> = emptyArray(),
        /** If using [showAnnotations], whether to include unannotated */
        showUnannotated: Boolean = false,
        /** Additional arguments to supply */
        extraArguments: Array<String> = emptyArray(),
        /** Expected output (stdout and stderr combined). If null, don't check. */
        expectedOutput: String? = null,
        /** Expected fail message and state, if any */
        expectedFail: String? = null,
        /** Optional manifest to load and associate with the codebase */
        @Language("XML") manifest: String? = null,
        /**
         * Packages to pre-import (these will therefore NOT be included in emitted stubs, signature
         * files etc
         */
        importedPackages: List<String> = emptyList(),
        /** See [TestEnvironment.skipEmitPackages] */
        skipEmitPackages: List<String> = listOf("java.lang", "java.util", "java.io"),
        /** Whether we should include --showAnnotations=android.annotation.SystemApi */
        includeSystemApiAnnotations: Boolean = false,
        /** Whether we should warn about super classes that are stripped because they are hidden */
        includeStrippedSuperclassWarnings: Boolean = false,
        /** Apply level to XML */
        applyApiLevelsXml: String? = null,
        /** Corresponds to SDK constants file broadcast_actions.txt */
        sdkBroadcastActions: String? = null,
        /** Corresponds to SDK constants file activity_actions.txt */
        sdkActivityActions: String? = null,
        /** Corresponds to SDK constants file service_actions.txt */
        sdkServiceActions: String? = null,
        /** Corresponds to SDK constants file categories.txt */
        sdkCategories: String? = null,
        /** Corresponds to SDK constants file features.txt */
        sdkFeatures: String? = null,
        /** Corresponds to SDK constants file widgets.txt */
        sdkWidgets: String? = null,
        /**
         * Extract annotations and check that the given packages contain the given extracted XML
         * files
         */
        extractAnnotations: Map<String, String>? = null,
        /**
         * Creates the nullability annotations validator, and check that the report has the given
         * lines (does not define files to be validated)
         */
        validateNullability: Set<String>? = null,
        /** Enable nullability validation for the listed classes */
        validateNullabilityFromList: String? = null,
        /** Hook for performing additional initialization of the project directory */
        projectSetup: ((File) -> Unit)? = null,
        /** [ARG_BASELINE] and [ARG_UPDATE_BASELINE] */
        baselineTestInfo: BaselineTestInfo = BaselineTestInfo(),
        /** [ARG_BASELINE_API_LINT] and [ARG_UPDATE_BASELINE_API_LINT] */
        baselineApiLintTestInfo: BaselineTestInfo = BaselineTestInfo(),
        /**
         * [ARG_BASELINE_CHECK_COMPATIBILITY_RELEASED] and
         * [ARG_UPDATE_BASELINE_CHECK_COMPATIBILITY_RELEASED]
         */
        baselineCheckCompatibilityReleasedTestInfo: BaselineTestInfo = BaselineTestInfo(),

        /** [ARG_ERROR_MESSAGE_API_LINT] */
        errorMessageApiLint: String? = null,
        /** [ARG_ERROR_MESSAGE_CHECK_COMPATIBILITY_RELEASED] */
        errorMessageCheckCompatibilityReleased: String? = null,

        /**
         * If non-null, enable API lint. If non-blank, a codebase where only new APIs not in the
         * codebase are linted.
         */
        @Language("TEXT") apiLint: String? = null,
        /** The source files to pass to the analyzer */
        sourceFiles: Array<TestFile> = emptyArray(),
        /** The common source files to pass to the analyzer */
        commonSourceFiles: Array<TestFile> = emptyArray(),
        /** [ARG_REPEAT_ERRORS_MAX] */
        repeatErrorsMax: Int = 0
    ) {
        // Ensure different API clients don't interfere with each other
        try {
            val method = ApiLookup::class.java.getDeclaredMethod("dispose")
            method.isAccessible = true
            method.invoke(null)
        } catch (ignore: Throwable) {
            ignore.printStackTrace()
        }

        // Ensure that lint infrastructure (for UAST) knows it's dealing with a test
        LintCliClient(LintClient.CLIENT_UNIT_TESTS)

        // Verify that a test that provided kotlin code is only being run against a provider that
        // supports kotlin code.
        val anyKotlin =
            sourceFiles.any { it.targetPath.endsWith(DOT_KT) } ||
                commonSourceFiles.any { it.targetPath.endsWith(DOT_KT) }
        if (anyKotlin && Capability.KOTLIN !in codebaseCreatorConfig.creator.capabilities) {
            error(
                "Provider ${codebaseCreatorConfig.providerName} does not support Kotlin; please add `@RequiresCapabilities(Capability.KOTLIN)` to the test"
            )
        }

        val releasedApiCheck =
            CompatibilityCheckRequest.create(
                optionName = ARG_CHECK_COMPATIBILITY_API_RELEASED,
                fileOrSignatureContents = checkCompatibilityApiReleased,
                fileOrSignatureContentsList = checkCompatibilityApiReleasedList,
                newBasename = "released-api.txt",
            )
        val releasedRemovedApiCheck =
            CompatibilityCheckRequest.create(
                optionName = ARG_CHECK_COMPATIBILITY_REMOVED_RELEASED,
                fileOrSignatureContents = checkCompatibilityRemovedApiReleased,
                fileOrSignatureContentsList = checkCompatibilityRemovedApiReleasedList,
                newBasename = "removed-released-api.txt",
            )

        val actualExpectedFail =
            when {
                expectedFail != null -> expectedFail
                (releasedApiCheck.required() || releasedRemovedApiCheck.required()) &&
                    !expectedIssues.isNullOrBlank() -> {
                    "Aborting: Found compatibility problems"
                }
                else -> ""
            }

        // Unit test which checks that a signature file is as expected
        val androidJar = getAndroidJar()

        val project = createProject(sourceFiles + commonSourceFiles)

        val sourcePathDir = File(project, "src")
        if (!sourcePathDir.isDirectory) {
            sourcePathDir.mkdirs()
        }

        var sourcePath = sourcePathDir.path
        var commonSourcePath: String? = null

        // Make it easy to configure a source path with more than one source root: src and src2
        if (sourceFiles.any { it.targetPath.startsWith("src2") }) {
            sourcePath = sourcePath + File.pathSeparator + sourcePath + "2"
        }

        fun pathUnderProject(path: String): String = File(project, path).path

        if (commonSourceFiles.isNotEmpty()) {
            // Assume common/source are placed in different folders, e.g., commonMain, androidMain
            sourcePath =
                pathUnderProject(sourceFiles.first().targetPath.substringBefore("src") + "src")
            commonSourcePath =
                pathUnderProject(
                    commonSourceFiles.first().targetPath.substringBefore("src") + "src"
                )
        }

        val apiClassResolutionArgs =
            arrayOf(ARG_API_CLASS_RESOLUTION, apiClassResolution.optionValue)

        val sourceList =
            if (signatureSources.isNotEmpty() || signatureSource != null) {
                sourcePathDir.mkdirs()

                // if signatureSource is set, add it to signatureSources.
                val sources = signatureSources.toMutableList()
                signatureSource?.let { sources.add(it) }

                var num = 0
                val args = mutableListOf<String>()
                sources.forEach { file ->
                    val signatureFile =
                        File(project, "load-api${ if (++num == 1) "" else num.toString() }.txt")
                    signatureFile.writeSignatureText(file)
                    args.add(signatureFile.path)
                }
                if (!includeStrippedSuperclassWarnings) {
                    args.add(ARG_HIDE)
                    args.add("HiddenSuperclass") // Suppress warning #111
                }
                args.toTypedArray()
            } else if (apiJar != null) {
                sourcePathDir.mkdirs()
                assert(sourceFiles.isEmpty()) {
                    "Shouldn't combine sources with API jar file loads"
                }
                arrayOf(apiJar.path)
            } else {
                (sourceFiles + commonSourceFiles)
                    .asSequence()
                    .map { pathUnderProject(it.targetPath) }
                    .toList()
                    .toTypedArray()
            }

        val classpathArgs: Array<String> =
            if (classpath != null) {
                val classpathString =
                    classpath
                        .map { it.createFile(project) }
                        .map { it.path }
                        .joinToString(separator = File.pathSeparator) { it }

                arrayOf(ARG_CLASS_PATH, classpathString)
            } else {
                emptyArray()
            }

        val allReportedIssues = StringBuilder()
        val errorSeverityReportedIssues = StringBuilder()
        val reporterEnvironment =
            object : ReporterEnvironment {
                override val rootFolder = project

                override fun printReport(message: String, severity: Severity) {
                    val cleanedUpMessage = cleanupString(message, rootFolder).trim()
                    if (severity == Severity.ERROR) {
                        errorSeverityReportedIssues.append(cleanedUpMessage).append('\n')
                    }
                    allReportedIssues.append(cleanedUpMessage).append('\n')
                }
            }

        val mergeAnnotationsArgs =
            if (mergeXmlAnnotations != null) {
                val merged = File(project, "merged-annotations.xml")
                merged.writeText(mergeXmlAnnotations.trimIndent())
                arrayOf(ARG_MERGE_QUALIFIER_ANNOTATIONS, merged.path)
            } else {
                emptyArray()
            }

        val signatureAnnotationsArgs =
            if (mergeSignatureAnnotations != null) {
                val merged = File(project, "merged-annotations.txt")
                merged.writeText(mergeSignatureAnnotations.trimIndent())
                arrayOf(ARG_MERGE_QUALIFIER_ANNOTATIONS, merged.path)
            } else {
                emptyArray()
            }

        val javaStubAnnotationsArgs =
            if (mergeJavaStubAnnotations != null) {
                // We need to place the qualifier class into its proper package location
                // to make the parsing machinery happy
                val cls = ClassName(mergeJavaStubAnnotations)
                val pkg = cls.packageName
                val relative = pkg?.replace('.', File.separatorChar) ?: "."
                val merged = File(project, "qualifier/$relative/${cls.className}.java")
                merged.parentFile.mkdirs()
                merged.writeText(mergeJavaStubAnnotations.trimIndent())
                arrayOf(ARG_MERGE_QUALIFIER_ANNOTATIONS, merged.path)
            } else {
                emptyArray()
            }

        val inclusionAnnotationsArgs =
            if (mergeInclusionAnnotations.isNotEmpty()) {
                // Create each file in their own directory.
                mergeInclusionAnnotations
                    .flatMapIndexed { i, testFile ->
                        val suffix = if (i == 0) "" else i.toString()
                        val targetDir = File(project, "inclusion$suffix")
                        targetDir.mkdirs()
                        testFile.createFile(targetDir)
                        listOf(ARG_MERGE_INCLUSION_ANNOTATIONS, targetDir.path)
                    }
                    .toTypedArray()
            } else {
                emptyArray()
            }

        val apiLintArgs =
            if (apiLint != null) {
                if (apiLint.isBlank()) {
                    arrayOf(ARG_API_LINT)
                } else {
                    val file = File(project, "prev-api-lint.txt")
                    file.writeSignatureText(apiLint)
                    arrayOf(ARG_API_LINT, file.path)
                }
            } else {
                emptyArray()
            }

        val checkCompatibilityBaseApiFile =
            useExistingSignatureFileOrCreateNewFile(
                project,
                checkCompatibilityBaseApi,
                "compatibility-base-api.txt"
            )

        val migrateNullsApiFile =
            useExistingSignatureFileOrCreateNewFile(project, migrateNullsApi, "stable-api.txt")

        val manifestFileArgs =
            if (manifest != null) {
                val file = File(project, "manifest.xml")
                file.writeText(manifest.trimIndent())
                arrayOf(ARG_MANIFEST, file.path)
            } else {
                emptyArray()
            }

        val migrateNullsArguments =
            if (migrateNullsApiFile != null) {
                arrayOf(ARG_MIGRATE_NULLNESS, migrateNullsApiFile.path)
            } else {
                emptyArray()
            }

        val checkCompatibilityBaseApiArguments =
            if (checkCompatibilityBaseApiFile != null) {
                arrayOf(ARG_CHECK_COMPATIBILITY_BASE_API, checkCompatibilityBaseApiFile.path)
            } else {
                emptyArray()
            }

        val quiet =
            if (expectedOutput != null && !extraArguments.contains(ARG_VERBOSE)) {
                // If comparing output, avoid noisy output such as the banner etc
                arrayOf(ARG_QUIET)
            } else {
                emptyArray()
            }

        var proguardFile: File? = null
        val proguardKeepArguments =
            if (proguard != null) {
                proguardFile = File(project, "proguard.cfg")
                arrayOf(ARG_PROGUARD, proguardFile.path)
            } else {
                emptyArray()
            }

        val showAnnotationArguments =
            if (showAnnotations.isNotEmpty() || includeSystemApiAnnotations) {
                val args = mutableListOf<String>()
                for (annotation in showAnnotations) {
                    args.add(ARG_SHOW_ANNOTATION)
                    args.add(annotation)
                }
                if (includeSystemApiAnnotations && !args.contains("android.annotation.SystemApi")) {
                    args.add(ARG_SHOW_ANNOTATION)
                    args.add("android.annotation.SystemApi")
                }
                if (includeSystemApiAnnotations && !args.contains("android.annotation.TestApi")) {
                    args.add(ARG_SHOW_ANNOTATION)
                    args.add("android.annotation.TestApi")
                }
                args.toTypedArray()
            } else {
                emptyArray()
            }

        val hideAnnotationArguments =
            if (hideAnnotations.isNotEmpty()) {
                val args = mutableListOf<String>()
                for (annotation in hideAnnotations) {
                    args.add(ARG_HIDE_ANNOTATION)
                    args.add(annotation)
                }
                args.toTypedArray()
            } else {
                emptyArray()
            }

        val showForStubPurposesAnnotationArguments =
            if (showForStubPurposesAnnotations.isNotEmpty()) {
                val args = mutableListOf<String>()
                for (annotation in showForStubPurposesAnnotations) {
                    args.add(ARG_SHOW_FOR_STUB_PURPOSES_ANNOTATION)
                    args.add(annotation)
                }
                args.toTypedArray()
            } else {
                emptyArray()
            }

        val suppressCompatMetaAnnotationArguments =
            if (suppressCompatibilityMetaAnnotations.isNotEmpty()) {
                val args = mutableListOf<String>()
                for (annotation in suppressCompatibilityMetaAnnotations) {
                    args.add(ARG_SUPPRESS_COMPATIBILITY_META_ANNOTATION)
                    args.add(annotation)
                }
                args.toTypedArray()
            } else {
                emptyArray()
            }

        val showUnannotatedArgs =
            if (showUnannotated) {
                arrayOf(ARG_SHOW_UNANNOTATED)
            } else {
                emptyArray()
            }

        var removedApiFile: File? = null
        val removedArgs =
            if (removedApi != null) {
                removedApiFile = temporaryFolder.newFile("removed.txt")
                arrayOf(ARG_REMOVED_API, removedApiFile.path)
            } else {
                emptyArray()
            }

        // Always pass apiArgs and generate API text file in runDriver
        val apiFile: File = newFile("public-api.txt")
        val apiArgs = arrayOf(ARG_API, apiFile.path)

        var apiXmlFile: File? = null
        val apiXmlArgs =
            if (apiXml != null) {
                apiXmlFile = temporaryFolder.newFile("public-api-xml.txt")
                arrayOf(ARG_XML_API, apiXmlFile.path)
            } else {
                emptyArray()
            }

        var dexApiFile: File? = null
        val dexApiArgs =
            if (dexApi != null) {
                dexApiFile = temporaryFolder.newFile("public-dex.txt")
                arrayOf(ARG_DEX_API, dexApiFile.path)
            } else {
                emptyArray()
            }

        val subtractApiFile: File?
        val subtractApiArgs =
            if (subtractApi != null) {
                subtractApiFile = temporaryFolder.newFile("subtract-api.txt")
                subtractApiFile.writeSignatureText(subtractApi)
                arrayOf(ARG_SUBTRACT_API, subtractApiFile.path)
            } else {
                emptyArray()
            }

        var stubsDir: File? = null
        val stubsArgs =
            if (stubFiles.isNotEmpty() || stubPaths != null) {
                stubsDir = newFolder("stubs")
                if (docStubs) {
                    arrayOf(ARG_DOC_STUBS, stubsDir.path)
                } else {
                    arrayOf(ARG_STUBS, stubsDir.path)
                }
            } else {
                emptyArray()
            }

        val applyApiLevelsXmlFile: File?
        val applyApiLevelsXmlArgs =
            if (applyApiLevelsXml != null) {
                ApiLookup::class
                    .java
                    .getDeclaredMethod("dispose")
                    .apply { isAccessible = true }
                    .invoke(null)
                applyApiLevelsXmlFile = temporaryFolder.newFile("api-versions.xml")
                applyApiLevelsXmlFile?.writeText(applyApiLevelsXml.trimIndent())
                arrayOf(ARG_APPLY_API_LEVELS, applyApiLevelsXmlFile.path)
            } else {
                emptyArray()
            }

        val baselineCheck =
            buildBaselineCheck(
                ARG_BASELINE,
                ARG_UPDATE_BASELINE,
                "baseline.txt",
                baselineTestInfo,
            )
        val baselineApiLintCheck =
            buildBaselineCheck(
                ARG_BASELINE_API_LINT,
                ARG_UPDATE_BASELINE_API_LINT,
                "baseline-api-lint.txt",
                baselineApiLintTestInfo,
            )
        val baselineCheckCompatibilityReleasedCheck =
            buildBaselineCheck(
                ARG_BASELINE_CHECK_COMPATIBILITY_RELEASED,
                ARG_UPDATE_BASELINE_CHECK_COMPATIBILITY_RELEASED,
                "baseline-check-released.txt",
                baselineCheckCompatibilityReleasedTestInfo,
            )

        val importedPackageArgs = mutableListOf<String>()
        importedPackages.forEach {
            importedPackageArgs.add("--stub-import-packages")
            importedPackageArgs.add(it)
        }

        val kotlinPathArgs = findKotlinStdlibPathArgs(sourceList)

        val sdkFilesDir: File?
        val sdkFilesArgs: Array<String>
        if (
            sdkBroadcastActions != null ||
                sdkActivityActions != null ||
                sdkServiceActions != null ||
                sdkCategories != null ||
                sdkFeatures != null ||
                sdkWidgets != null
        ) {
            val dir = File(project, "sdk-files")
            sdkFilesArgs = arrayOf(ARG_SDK_VALUES, dir.path)
            sdkFilesDir = dir
        } else {
            sdkFilesArgs = emptyArray()
            sdkFilesDir = null
        }

        val extractedAnnotationsZip: File?
        val extractAnnotationsArgs =
            if (extractAnnotations != null) {
                extractedAnnotationsZip = temporaryFolder.newFile("extracted-annotations.zip")
                arrayOf(ARG_EXTRACT_ANNOTATIONS, extractedAnnotationsZip.path)
            } else {
                extractedAnnotationsZip = null
                emptyArray()
            }

        val validateNullabilityTxt: File?
        val validateNullabilityArgs =
            if (validateNullability != null) {
                validateNullabilityTxt = temporaryFolder.newFile("validate-nullability.txt")
                arrayOf(
                    ARG_NULLABILITY_WARNINGS_TXT,
                    validateNullabilityTxt.path,
                    ARG_NULLABILITY_ERRORS_NON_FATAL // for testing, report on errors instead of
                    // throwing
                )
            } else {
                validateNullabilityTxt = null
                emptyArray()
            }
        val validateNullabilityFromListFile: File?
        val validateNullabilityFromListArgs =
            if (validateNullabilityFromList != null) {
                validateNullabilityFromListFile =
                    temporaryFolder.newFile("validate-nullability-classes.txt")
                validateNullabilityFromListFile.writeText(validateNullabilityFromList)
                arrayOf(ARG_VALIDATE_NULLABILITY_FROM_LIST, validateNullabilityFromListFile.path)
            } else {
                emptyArray()
            }

        val errorMessageApiLintArgs =
            buildOptionalArgs(errorMessageApiLint) { arrayOf(ARG_ERROR_MESSAGE_API_LINT, it) }
        val errorMessageCheckCompatibilityReleasedArgs =
            buildOptionalArgs(errorMessageCheckCompatibilityReleased) {
                arrayOf(ARG_ERROR_MESSAGE_CHECK_COMPATIBILITY_RELEASED, it)
            }

        val repeatErrorsMaxArgs =
            if (repeatErrorsMax > 0) {
                arrayOf(ARG_REPEAT_ERRORS_MAX, repeatErrorsMax.toString())
            } else {
                emptyArray()
            }

        // Run optional additional setup steps on the project directory
        projectSetup?.invoke(project)

        // Make sure that the options is initialized. Just in case access was disallowed by another
        // test.
        options = Options()

        val args =
            arrayOf(
                ARG_NO_COLOR,

                // Tell metalava where to store temp folder: place them under the
                // test root folder such that we clean up the output strings referencing
                // paths to the temp folder
                "--temp-folder",
                newFolder("temp").path,

                // Annotation generation temporarily turned off by default while integrating with
                // SDK builds; tests need these
                ARG_INCLUDE_ANNOTATIONS,
                ARG_SOURCE_PATH,
                sourcePath,
                ARG_CLASS_PATH,
                androidJar.path,
                *classpathArgs,
                *kotlinPathArgs,
                *removedArgs,
                *apiArgs,
                *apiXmlArgs,
                *dexApiArgs,
                *subtractApiArgs,
                *stubsArgs,
                *quiet,
                *mergeAnnotationsArgs,
                *signatureAnnotationsArgs,
                *javaStubAnnotationsArgs,
                *inclusionAnnotationsArgs,
                *migrateNullsArguments,
                *releasedApiCheck.arguments(project),
                *checkCompatibilityBaseApiArguments,
                *releasedRemovedApiCheck.arguments(project),
                *proguardKeepArguments,
                *manifestFileArgs,
                *applyApiLevelsXmlArgs,
                *baselineCheck.args,
                *baselineApiLintCheck.args,
                *baselineCheckCompatibilityReleasedCheck.args,
                *showAnnotationArguments,
                *hideAnnotationArguments,
                *suppressCompatMetaAnnotationArguments,
                *showForStubPurposesAnnotationArguments,
                *showUnannotatedArgs,
                *sdkFilesArgs,
                *importedPackageArgs.toTypedArray(),
                *extractAnnotationsArgs,
                *validateNullabilityArgs,
                *validateNullabilityFromListArgs,
                format.outputFlags(),
                *apiClassResolutionArgs,
                *sourceList,
                *extraArguments,
                *errorMessageApiLintArgs,
                *errorMessageCheckCompatibilityReleasedArgs,
                *repeatErrorsMaxArgs,
                // Must always be last as this can consume a following argument, breaking the test.
                *apiLintArgs,
            ) +
                buildList {
                        if (commonSourcePath != null) {
                            add(ARG_COMMON_SOURCE_PATH)
                            add(commonSourcePath)
                        }
                    }
                    .toTypedArray()

        val testEnvironment =
            TestEnvironment(
                skipEmitPackages = skipEmitPackages,
                sourceModelProvider = codebaseCreatorConfig.creator,
                modelOptions = codebaseCreatorConfig.modelOptions,
            )

        val actualOutput =
            runDriver(
                args = args,
                expectedFail = actualExpectedFail,
                reporterEnvironment = reporterEnvironment,
                testEnvironment = testEnvironment,
            )

        if (expectedIssues != null || allReportedIssues.toString() != "") {
            assertEquals(
                "expectedIssues does not match actual issues reported",
                expectedIssues?.trimIndent()?.trim() ?: "",
                allReportedIssues.toString().trim(),
            )
        }
        if (errorSeverityExpectedIssues != null) {
            assertEquals(
                "errorSeverityExpectedIssues does not match actual issues reported",
                errorSeverityExpectedIssues.trimIndent().trim(),
                errorSeverityReportedIssues.toString().trim(),
            )
        }

        if (expectedOutput != null) {
            assertEquals(
                "expectedOutput does not match actual output",
                expectedOutput.trimIndent().trim(),
                actualOutput.trim()
            )
        }

        if (api != null) {
            assertTrue(
                "${apiFile.path} does not exist even though --api was used",
                apiFile.exists()
            )
            assertSignatureFilesMatch(api, apiFile.readText(), expectedFormat = format)
            // Make sure we can read back the files we write
            ApiFile.parseApi(apiFile, options.annotationManager)
        }

        if (apiXml != null && apiXmlFile != null) {
            assertTrue(
                "${apiXmlFile.path} does not exist even though $ARG_XML_API was used",
                apiXmlFile.exists()
            )
            val actualText = readFile(apiXmlFile)
            assertEquals(
                stripComments(apiXml, DOT_XML, stripLineComments = false).trimIndent(),
                actualText
            )
            // Make sure we can read back the files we write
            parseDocument(apiXmlFile.readText(), false)
        }

        baselineCheck.apply()
        baselineApiLintCheck.apply()
        baselineCheckCompatibilityReleasedCheck.apply()

        if (dexApi != null && dexApiFile != null) {
            assertTrue(
                "${dexApiFile.path} does not exist even though --dex-api was used",
                dexApiFile.exists()
            )
            val actualText = readFile(dexApiFile)
            assertEquals(
                stripComments(dexApi, DOT_TXT, stripLineComments = false).trimIndent(),
                actualText
            )
        }

        if (removedApi != null && removedApiFile != null) {
            assertTrue(
                "${removedApiFile.path} does not exist even though --removed-api was used",
                removedApiFile.exists()
            )
            assertSignatureFilesMatch(
                removedApi,
                removedApiFile.readText(),
                expectedFormat = format
            )
            // Make sure we can read back the files we write
            ApiFile.parseApi(removedApiFile, options.annotationManager)
        }

        if (proguard != null && proguardFile != null) {
            assertTrue(
                "${proguardFile.path} does not exist even though --proguard was used",
                proguardFile.exists()
            )
            val expectedProguard = readFile(proguardFile)
            assertEquals(
                stripComments(proguard, DOT_TXT, stripLineComments = false).trimIndent(),
                expectedProguard
            )
        }

        if (sdkBroadcastActions != null) {
            val actual = readFile(File(sdkFilesDir, "broadcast_actions.txt"))
            assertEquals(sdkBroadcastActions.trimIndent().trim(), actual.trim())
        }

        if (sdkActivityActions != null) {
            val actual = readFile(File(sdkFilesDir, "activity_actions.txt"))
            assertEquals(sdkActivityActions.trimIndent().trim(), actual.trim())
        }

        if (sdkServiceActions != null) {
            val actual = readFile(File(sdkFilesDir, "service_actions.txt"))
            assertEquals(sdkServiceActions.trimIndent().trim(), actual.trim())
        }

        if (sdkCategories != null) {
            val actual = readFile(File(sdkFilesDir, "categories.txt"))
            assertEquals(sdkCategories.trimIndent().trim(), actual.trim())
        }

        if (sdkFeatures != null) {
            val actual = readFile(File(sdkFilesDir, "features.txt"))
            assertEquals(sdkFeatures.trimIndent().trim(), actual.trim())
        }

        if (sdkWidgets != null) {
            val actual = readFile(File(sdkFilesDir, "widgets.txt"))
            assertEquals(sdkWidgets.trimIndent().trim(), actual.trim())
        }

        if (extractAnnotations != null && extractedAnnotationsZip != null) {
            assertTrue(
                "Using --extract-annotations but $extractedAnnotationsZip was not created",
                extractedAnnotationsZip.isFile
            )
            for ((pkg, xml) in extractAnnotations) {
                assertPackageXml(pkg, extractedAnnotationsZip, xml)
            }
        }

        if (validateNullabilityTxt != null) {
            assertTrue(
                "Using $ARG_NULLABILITY_WARNINGS_TXT but $validateNullabilityTxt was not created",
                validateNullabilityTxt.isFile
            )
            val actualReport = validateNullabilityTxt.readLines().map(String::trim).toSet()
            assertEquals(validateNullability, actualReport)
        }

        val stubsCreated =
            stubsDir
                ?.walkTopDown()
                ?.filter { it.isFile }
                ?.map { it.relativeTo(stubsDir).path }
                ?.sorted()
                ?.joinToString("\n")

        if (stubPaths != null) {
            assertEquals("stub paths", stubPaths.joinToString("\n"), stubsCreated)
        }

        if (stubFiles.isNotEmpty()) {
            for (expected in stubFiles) {
                val actual = File(stubsDir!!, expected.targetRelativePath)
                if (!actual.exists()) {
                    throw FileNotFoundException(
                        "Could not find a generated stub for ${expected.targetRelativePath}. " +
                            "Found these files: \n${stubsCreated!!.prependIndent("  ")}"
                    )
                }
                val actualContents = readFile(actual)
                val stubSource = if (sourceFiles.isEmpty()) "text" else "source"
                val message =
                    "Generated from-$stubSource stub contents does not match expected contents"
                assertEquals(message, expected.contents, actualContents)
            }
        }

        if (checkCompilation && stubsDir != null) {
            val generated =
                SourceSet.createFromSourcePath(options.reporter, listOf(stubsDir))
                    .sources
                    .asSequence()
                    .map { it.path }
                    .toList()
                    .toTypedArray()

            // Also need to include on the compile path annotation classes referenced in the stubs
            val extraAnnotationsDir = File("../stub-annotations/src/main/java")
            if (!extraAnnotationsDir.isDirectory) {
                fail(
                    "Couldn't find $extraAnnotationsDir: Is the pwd set to the root of the metalava source code?"
                )
                fail(
                    "Couldn't find $extraAnnotationsDir: Is the pwd set to the root of an Android source tree?"
                )
            }
            val extraAnnotations =
                SourceSet.createFromSourcePath(options.reporter, listOf(extraAnnotationsDir))
                    .sources
                    .asSequence()
                    .map { it.path }
                    .toList()
                    .toTypedArray()

            if (
                !runCommand(
                    "${getJdkPath()}/bin/javac",
                    arrayOf("-d", project.path, *generated, *extraAnnotations)
                )
            ) {
                fail("Couldn't compile stub file -- compilation problems")
                return
            }
        }
    }

    /** Encapsulates information needed to request a compatibility check. */
    private class CompatibilityCheckRequest
    private constructor(
        private val optionName: String,
        private val fileOrSignatureContentsList: List<String>,
        private val newBasename: String,
    ) {
        companion object {
            fun create(
                optionName: String,
                fileOrSignatureContents: String?,
                fileOrSignatureContentsList: List<String>,
                newBasename: String,
            ): CompatibilityCheckRequest =
                CompatibilityCheckRequest(
                    optionName = optionName,
                    fileOrSignatureContentsList =
                        listOfNotNull(fileOrSignatureContents) + fileOrSignatureContentsList,
                    newBasename = newBasename,
                )
        }

        /** Indicates whether the compatibility check is required. */
        fun required(): Boolean = fileOrSignatureContentsList.isNotEmpty()

        /** The arguments to pass to Metalava. */
        fun arguments(project: File): Array<out String> {
            if (fileOrSignatureContentsList.isEmpty()) return emptyArray()

            val paths =
                fileOrSignatureContentsList.mapNotNull {
                    useExistingSignatureFileOrCreateNewFile(project, it, newBasename)?.path
                }

            // For each path in the list generate an option with the path as the value.
            return paths.flatMap { listOf(optionName, it) }.toTypedArray()
        }
    }

    /** Checks that the given zip annotations file contains the given XML package contents */
    private fun assertPackageXml(pkg: String, output: File, @Language("XML") expected: String) {
        assertNotNull(output)
        assertTrue(output.exists())
        val url =
            URL(
                "jar:" +
                    SdkUtils.fileToUrlString(output) +
                    "!/" +
                    pkg.replace('.', '/') +
                    "/annotations.xml"
            )
        val stream = url.openStream()
        try {
            val bytes = stream.readBytes()
            assertNotNull(bytes)
            val xml = String(bytes, UTF_8).replace("\r\n", "\n")
            assertEquals(expected.trimIndent().trim(), xml.trimIndent().trim())
        } finally {
            Closeables.closeQuietly(stream)
        }
    }

    private fun runCommand(executable: String, args: Array<String>): Boolean {
        try {
            val logger = StdLogger(StdLogger.Level.ERROR)
            val processExecutor = DefaultProcessExecutor(logger)
            val processInfo =
                ProcessInfoBuilder().setExecutable(executable).addArgs(args).createProcess()

            val processOutputHandler = LoggedProcessOutputHandler(logger)
            val result = processExecutor.execute(processInfo, processOutputHandler)

            result.rethrowFailure().assertNormalExitValue()
        } catch (e: ProcessException) {
            fail(
                "Failed to run $executable (${e.message}): not verifying this API on the old doclava engine"
            )
            return false
        }
        return true
    }

    companion object {
        @JvmStatic
        protected fun readFile(file: File): String {
            var apiLines: List<String> = file.readLines()
            apiLines = apiLines.filter { it.isNotBlank() }
            return apiLines.joinToString(separator = "\n") { it }.trim()
        }

        /**
         * Get an optional signature API [File] from either a file path or its contents.
         *
         * @param project the directory in which to create a new file.
         * @param fileOrFileContents either a path to an existing file or the contents of the
         *   signature file. If the latter the contents will be trimmed, updated to add a
         *   [FileFormat.V2] header if needed and written to a new file created within [project].
         * @param newBasename the basename of a new file created.
         */
        private fun useExistingSignatureFileOrCreateNewFile(
            project: File,
            fileOrFileContents: String?,
            newBasename: String
        ) =
            fileOrFileContents?.let {
                val maybeFile = File(fileOrFileContents)
                if (maybeFile.isFile) {
                    maybeFile
                } else {
                    val file = File(project, newBasename)
                    file.writeSignatureText(fileOrFileContents)
                    file
                }
            }
    }
}

private fun FileFormat.outputFlags(): String {
    return "$ARG_FORMAT=${specifier()}"
}

private fun File.writeSignatureText(contents: String) {
    writeText(prepareSignatureFileForTest(contents, FileFormat.V2))
}

/** Returns the paths returned by [findKotlinStdlibPaths] as metalava args expected by Options. */
fun findKotlinStdlibPathArgs(sources: Array<String>): Array<String> {
    val kotlinPaths = findKotlinStdlibPaths(sources)

    return if (kotlinPaths.isEmpty()) emptyArray()
    else
        arrayOf(
            ARG_CLASS_PATH,
            kotlinPaths.joinToString(separator = File.pathSeparator) { it.path }
        )
}

val intRangeAnnotationSource: TestFile =
    java(
            """
        package android.annotation;
        import java.lang.annotation.*;
        import static java.lang.annotation.ElementType.*;
        import static java.lang.annotation.RetentionPolicy.SOURCE;
        @Retention(SOURCE)
        @Target({METHOD,PARAMETER,FIELD,LOCAL_VARIABLE,ANNOTATION_TYPE})
        public @interface IntRange {
            long from() default Long.MIN_VALUE;
            long to() default Long.MAX_VALUE;
        }
        """
        )
        .indented()

val intDefAnnotationSource: TestFile =
    java(
            """
    package android.annotation;
    import java.lang.annotation.Retention;
    import java.lang.annotation.RetentionPolicy;
    import java.lang.annotation.Target;
    import static java.lang.annotation.ElementType.*;
    import static java.lang.annotation.RetentionPolicy.SOURCE;
    @Retention(SOURCE)
    @Target({ANNOTATION_TYPE})
    public @interface IntDef {
        int[] value() default {};
        boolean flag() default false;
    }
    """
        )
        .indented()

val longDefAnnotationSource: TestFile =
    java(
            """
    package android.annotation;
    import java.lang.annotation.Retention;
    import java.lang.annotation.RetentionPolicy;
    import java.lang.annotation.Target;
    import static java.lang.annotation.ElementType.*;
    import static java.lang.annotation.RetentionPolicy.SOURCE;
    @Retention(SOURCE)
    @Target({ANNOTATION_TYPE})
    public @interface LongDef {
        long[] value() default {};
        boolean flag() default false;
    }
    """
        )
        .indented()

val nonNullSource = KnownSourceFiles.nonNullSource
val nullableSource = KnownSourceFiles.nullableSource
val libcoreNonNullSource = KnownSourceFiles.libcoreNonNullSource
val libcoreNullableSource = KnownSourceFiles.libcoreNullableSource

val libcoreNullFromTypeParamSource: TestFile =
    java(
            """
    package libcore.util;
    import static java.lang.annotation.ElementType.*;
    import static java.lang.annotation.RetentionPolicy.SOURCE;
    import java.lang.annotation.*;
    @Documented
    @Retention(SOURCE)
    @Target({TYPE_USE})
    public @interface NullFromTypeParam {
    }
    """
        )
        .indented()

val requiresPermissionSource: TestFile =
    java(
            """
    package android.annotation;
    import java.lang.annotation.*;
    import static java.lang.annotation.ElementType.*;
    import static java.lang.annotation.RetentionPolicy.SOURCE;
    @Retention(SOURCE)
    @Target({ANNOTATION_TYPE,METHOD,CONSTRUCTOR,FIELD,PARAMETER})
    public @interface RequiresPermission {
        String value() default "";
        String[] allOf() default {};
        String[] anyOf() default {};
        boolean conditional() default false;
        @Target({FIELD, METHOD, PARAMETER})
        @interface Read {
            RequiresPermission value() default @RequiresPermission;
        }
        @Target({FIELD, METHOD, PARAMETER})
        @interface Write {
            RequiresPermission value() default @RequiresPermission;
        }
    }
    """
        )
        .indented()

val requiresFeatureSource: TestFile =
    java(
            """
    package android.annotation;
    import java.lang.annotation.*;
    import static java.lang.annotation.ElementType.*;
    import static java.lang.annotation.RetentionPolicy.SOURCE;
    @Retention(SOURCE)
    @Target({TYPE,FIELD,METHOD,CONSTRUCTOR})
    public @interface RequiresFeature {
        String value();
    }
    """
        )
        .indented()

val requiresApiSource: TestFile =
    java(
            """
    package androidx.annotation;
    import java.lang.annotation.*;
    import static java.lang.annotation.ElementType.*;
    import static java.lang.annotation.RetentionPolicy.SOURCE;
    @Retention(SOURCE)
    @Target({TYPE,FIELD,METHOD,CONSTRUCTOR})
    public @interface RequiresApi {
        int value() default 1;
        int api() default 1;
    }
    """
        )
        .indented()

val sdkConstantSource: TestFile =
    java(
            """
    package android.annotation;
    import java.lang.annotation.*;
    @Target({ ElementType.FIELD })
    @Retention(RetentionPolicy.SOURCE)
    public @interface SdkConstant {
        enum SdkConstantType {
            ACTIVITY_INTENT_ACTION, BROADCAST_INTENT_ACTION, SERVICE_ACTION, INTENT_CATEGORY, FEATURE
        }
        SdkConstantType value();
    }
    """
        )
        .indented()

val broadcastBehaviorSource: TestFile =
    java(
            """
    package android.annotation;
    import java.lang.annotation.*;
    /** @hide */
    @Target({ ElementType.FIELD })
    @Retention(RetentionPolicy.SOURCE)
    public @interface BroadcastBehavior {
        boolean explicitOnly() default false;
        boolean registeredOnly() default false;
        boolean includeBackground() default false;
        boolean protectedBroadcast() default false;
    }
    """
        )
        .indented()

val androidxNonNullSource: TestFile =
    java(
            """
    package androidx.annotation;
    import java.lang.annotation.*;
    import static java.lang.annotation.ElementType.*;
    import static java.lang.annotation.RetentionPolicy.SOURCE;
    @SuppressWarnings("WeakerAccess")
    @Retention(SOURCE)
    @Target({METHOD, PARAMETER, FIELD, LOCAL_VARIABLE, TYPE_USE, ANNOTATION_TYPE, PACKAGE})
    public @interface NonNull {
    }
    """
        )
        .indented()

val androidxNullableSource: TestFile =
    java(
            """
    package androidx.annotation;
    import java.lang.annotation.*;
    import static java.lang.annotation.ElementType.*;
    import static java.lang.annotation.RetentionPolicy.SOURCE;
    @SuppressWarnings("WeakerAccess")
    @Retention(SOURCE)
    @Target({METHOD, PARAMETER, FIELD, LOCAL_VARIABLE, TYPE_USE, ANNOTATION_TYPE, PACKAGE})
    public @interface Nullable {
    }
    """
        )
        .indented()

val recentlyNonNullSource: TestFile =
    java(
            """
    package androidx.annotation;
    import java.lang.annotation.*;
    import static java.lang.annotation.ElementType.*;
    import static java.lang.annotation.RetentionPolicy.SOURCE;
    @SuppressWarnings("WeakerAccess")
    @Retention(SOURCE)
    @Target({METHOD, PARAMETER, FIELD, TYPE_USE})
    public @interface RecentlyNonNull {
    }
    """
        )
        .indented()

val recentlyNullableSource: TestFile =
    java(
            """
    package androidx.annotation;
    import java.lang.annotation.*;
    import static java.lang.annotation.ElementType.*;
    import static java.lang.annotation.RetentionPolicy.SOURCE;
    @SuppressWarnings("WeakerAccess")
    @Retention(SOURCE)
    @Target({METHOD, PARAMETER, FIELD, TYPE_USE})
    public @interface RecentlyNullable {
    }
    """
        )
        .indented()

val androidxIntRangeSource: TestFile =
    java(
            """
    package androidx.annotation;
    import java.lang.annotation.*;
    import static java.lang.annotation.ElementType.*;
    import static java.lang.annotation.RetentionPolicy.SOURCE;
    @Retention(CLASS)
    @Target({METHOD, PARAMETER, FIELD, LOCAL_VARIABLE, ANNOTATION_TYPE})
    public @interface IntRange {
        long from() default Long.MIN_VALUE;
        long to() default Long.MAX_VALUE;
    }
    """
        )
        .indented()

val supportParameterName = KnownSourceFiles.supportParameterName

val supportDefaultValue: TestFile =
    java(
            """
    package androidx.annotation;
    import java.lang.annotation.*;
    import static java.lang.annotation.ElementType.*;
    import static java.lang.annotation.RetentionPolicy.SOURCE;
    @SuppressWarnings("WeakerAccess")
    @Retention(SOURCE)
    @Target({METHOD, PARAMETER, FIELD})
    public @interface DefaultValue {
        String value();
    }
    """
        )
        .indented()

val uiThreadSource: TestFile =
    java(
            """
    package androidx.annotation;
    import java.lang.annotation.*;
    import static java.lang.annotation.ElementType.*;
    import static java.lang.annotation.RetentionPolicy.SOURCE;
    /**
     * Denotes that the annotated method or constructor should only be called on the
     * UI thread. If the annotated element is a class, then all methods in the class
     * should be called on the UI thread.
     * @memberDoc This method must be called on the thread that originally created
     *            this UI element. This is typically the main thread of your app.
     * @classDoc Methods in this class must be called on the thread that originally created
     *            this UI element, unless otherwise noted. This is typically the
     *            main thread of your app. * @hide
     */
    @SuppressWarnings({"WeakerAccess", "JavaDoc"})
    @Retention(SOURCE)
    @Target({METHOD,CONSTRUCTOR,TYPE,PARAMETER})
    public @interface UiThread {
    }
    """
        )
        .indented()

val workerThreadSource: TestFile =
    java(
            """
    package androidx.annotation;
    import java.lang.annotation.*;
    import static java.lang.annotation.ElementType.*;
    import static java.lang.annotation.RetentionPolicy.SOURCE;
    /**
     * @memberDoc This method may take several seconds to complete, so it should
     *            only be called from a worker thread.
     * @classDoc Methods in this class may take several seconds to complete, so it should
     *            only be called from a worker thread unless otherwise noted.
     * @hide
     */
    @SuppressWarnings({"WeakerAccess", "JavaDoc"})
    @Retention(SOURCE)
    @Target({METHOD,CONSTRUCTOR,TYPE,PARAMETER})
    public @interface WorkerThread {
    }
    """
        )
        .indented()

val suppressLintSource: TestFile =
    java(
            """
    package android.annotation;

    import static java.lang.annotation.ElementType.*;
    import java.lang.annotation.*;
    @Target({TYPE, FIELD, METHOD, PARAMETER, CONSTRUCTOR, LOCAL_VARIABLE})
    @Retention(RetentionPolicy.CLASS)
    public @interface SuppressLint {
        String[] value();
    }
    """
        )
        .indented()

val systemServiceSource: TestFile =
    java(
            """
    package android.annotation;
    import static java.lang.annotation.ElementType.TYPE;
    import static java.lang.annotation.RetentionPolicy.SOURCE;
    import java.lang.annotation.*;
    @Retention(SOURCE)
    @Target(TYPE)
    public @interface SystemService {
        String value();
    }
    """
        )
        .indented()

val systemApiSource: TestFile =
    java(
            """
    package android.annotation;
    import static java.lang.annotation.ElementType.*;
    import java.lang.annotation.*;
    @Target({TYPE, FIELD, METHOD, CONSTRUCTOR, ANNOTATION_TYPE, PACKAGE})
    @Retention(RetentionPolicy.SOURCE)
    public @interface SystemApi {
        enum Client {
            /**
             * Specifies that the intended clients of a SystemApi are privileged apps.
             * This is the default value for {@link #client}.
             */
            PRIVILEGED_APPS,

            /**
             * Specifies that the intended clients of a SystemApi are used by classes in
             * <pre>BOOTCLASSPATH</pre> in mainline modules. Mainline modules can also expose
             * this type of system APIs too when they're used only by the non-updatable
             * platform code.
             */
            MODULE_LIBRARIES,

            /**
             * Specifies that the system API is available only in the system server process.
             * Use this to expose APIs from code loaded by the system server process <em>but</em>
             * not in <pre>BOOTCLASSPATH</pre>.
             */
            SYSTEM_SERVER
        }

        /**
         * The intended client of this SystemAPI.
         */
        Client client() default android.annotation.SystemApi.Client.PRIVILEGED_APPS;
    }
    """
        )
        .indented()

val testApiSource: TestFile =
    java(
            """
    package android.annotation;
    import static java.lang.annotation.ElementType.*;
    import java.lang.annotation.*;
    @Target({TYPE, FIELD, METHOD, CONSTRUCTOR, ANNOTATION_TYPE, PACKAGE})
    @Retention(RetentionPolicy.SOURCE)
    public @interface TestApi {
    }
    """
        )
        .indented()

val widgetSource: TestFile =
    java(
            """
    package android.annotation;
    import java.lang.annotation.*;
    @Target({ ElementType.TYPE })
    @Retention(RetentionPolicy.SOURCE)
    public @interface Widget {
    }
    """
        )
        .indented()

val restrictToSource: TestFile =
    kotlin(
            """
    package androidx.annotation

    import androidx.annotation.RestrictTo.Scope
    import java.lang.annotation.ElementType.*

    @MustBeDocumented
    @Retention(AnnotationRetention.BINARY)
    @Target(
        AnnotationTarget.ANNOTATION_CLASS,
        AnnotationTarget.CLASS,
        AnnotationTarget.FUNCTION,
        AnnotationTarget.PROPERTY_GETTER,
        AnnotationTarget.PROPERTY_SETTER,
        AnnotationTarget.CONSTRUCTOR,
        AnnotationTarget.FIELD,
        AnnotationTarget.FILE
    )
    // Needed due to Kotlin's lack of PACKAGE annotation target
    // https://youtrack.jetbrains.com/issue/KT-45921
    @Suppress("DEPRECATED_JAVA_ANNOTATION")
    @java.lang.annotation.Target(ANNOTATION_TYPE, TYPE, METHOD, CONSTRUCTOR, FIELD, PACKAGE)
    annotation class RestrictTo(vararg val value: Scope) {
        enum class Scope {
            LIBRARY,
            LIBRARY_GROUP,
            LIBRARY_GROUP_PREFIX,
            @Deprecated("Use LIBRARY_GROUP_PREFIX instead.")
            GROUP_ID,
            TESTS,
            SUBCLASSES,
        }
    }
    """
        )
        .indented()

val visibleForTestingSource: TestFile =
    java(
            """
    package androidx.annotation;
    import static java.lang.annotation.RetentionPolicy.CLASS;
    import java.lang.annotation.Retention;
    @Retention(CLASS)
    @SuppressWarnings("WeakerAccess")
    public @interface VisibleForTesting {
        int otherwise() default PRIVATE;
        int PRIVATE = 2;
        int PACKAGE_PRIVATE = 3;
        int PROTECTED = 4;
        int NONE = 5;
    }
    """
        )
        .indented()

val columnSource: TestFile =
    java(
            """
    package android.provider;

    import static java.lang.annotation.ElementType.FIELD;
    import static java.lang.annotation.RetentionPolicy.RUNTIME;

    import android.content.ContentProvider;
    import android.content.ContentValues;
    import android.database.Cursor;

    import java.lang.annotation.Documented;
    import java.lang.annotation.Retention;
    import java.lang.annotation.Target;

    @Documented
    @Retention(RUNTIME)
    @Target({FIELD})
    public @interface Column {
        int value();
        boolean readOnly() default false;
    }
    """
        )
        .indented()

val flaggedApiSource: TestFile =
    java(
            """
    package android.annotation;

    import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
    import static java.lang.annotation.ElementType.CONSTRUCTOR;
    import static java.lang.annotation.ElementType.FIELD;
    import static java.lang.annotation.ElementType.METHOD;
    import static java.lang.annotation.ElementType.TYPE;

    import java.lang.annotation.Retention;
    import java.lang.annotation.RetentionPolicy;
    import java.lang.annotation.Target;

    /** @hide */
    @Target({TYPE, METHOD, CONSTRUCTOR, FIELD, ANNOTATION_TYPE})
    @Retention(RetentionPolicy.SOURCE)
    public @interface FlaggedApi {
        String value();
    }
    """
        )
        .indented()

val publishedApiSource: TestFile =
    kotlin(
            """
    /**
     * When applied to a class or a member with internal visibility allows to use it from public inline functions and
     * makes it effectively public.
     *
     * Public inline functions cannot use non-public API, since if they are inlined, those non-public API references
     * would violate access restrictions at a call site (https://kotlinlang.org/docs/reference/inline-functions.html#public-inline-restrictions).
     *
     * To overcome this restriction an `internal` declaration can be annotated with the `@PublishedApi` annotation:
     * - this allows to call that declaration from public inline functions;
     * - the declaration becomes effectively public, and this should be considered with respect to binary compatibility maintaining.
     */
    @Target(AnnotationTarget.CLASS, AnnotationTarget.CONSTRUCTOR, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY)
    @Retention(AnnotationRetention.BINARY)
    @MustBeDocumented
    @SinceKotlin("1.1")
    annotation class PublishedApi
    """
        )
        .indented()

val deprecatedForSdkSource: TestFile =
    java(
            """
    package android.annotation;
    import static java.lang.annotation.RetentionPolicy.SOURCE;
    import java.lang.annotation.Retention;
    /** @hide */
    @Retention(SOURCE)
    @SuppressWarnings("WeakerAccess")
    public @interface DeprecatedForSdk {
        String value();
        Class<?>[] allowIn() default {};
    }
    """
        )
        .indented()
