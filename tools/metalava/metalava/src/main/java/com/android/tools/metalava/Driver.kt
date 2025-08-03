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
@file:JvmName("Driver")

package com.android.tools.metalava

import com.android.SdkConstants.DOT_JAR
import com.android.SdkConstants.DOT_TXT
import com.android.tools.lint.detector.api.assertionsEnabled
import com.android.tools.metalava.apilevels.ApiGenerator
import com.android.tools.metalava.cli.common.ActionContext
import com.android.tools.metalava.cli.common.EarlyOptions
import com.android.tools.metalava.cli.common.MetalavaCliException
import com.android.tools.metalava.cli.common.MetalavaCommand
import com.android.tools.metalava.cli.common.SignatureFileLoader
import com.android.tools.metalava.cli.common.VersionCommand
import com.android.tools.metalava.cli.common.commonOptions
import com.android.tools.metalava.cli.compatibility.ARG_CHECK_COMPATIBILITY_BASE_API
import com.android.tools.metalava.cli.compatibility.CompatibilityCheckOptions.CheckRequest
import com.android.tools.metalava.cli.help.HelpCommand
import com.android.tools.metalava.cli.internal.MakeAnnotationsPackagePrivateCommand
import com.android.tools.metalava.cli.signature.MergeSignaturesCommand
import com.android.tools.metalava.cli.signature.SignatureToDexCommand
import com.android.tools.metalava.cli.signature.SignatureToJDiffCommand
import com.android.tools.metalava.cli.signature.UpdateSignatureHeaderCommand
import com.android.tools.metalava.compatibility.CompatibilityCheck
import com.android.tools.metalava.doc.DocAnalyzer
import com.android.tools.metalava.lint.ApiLint
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.ClassResolver
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.MergedCodebase
import com.android.tools.metalava.model.ModelOptions
import com.android.tools.metalava.model.psi.PsiModelOptions
import com.android.tools.metalava.model.source.EnvironmentManager
import com.android.tools.metalava.model.source.SourceParser
import com.android.tools.metalava.model.source.SourceSet
import com.android.tools.metalava.model.text.ApiClassResolution
import com.android.tools.metalava.model.visitors.ApiVisitor
import com.android.tools.metalava.reporter.Issues
import com.android.tools.metalava.reporter.Reporter
import com.android.tools.metalava.stub.StubWriter
import com.github.ajalt.clikt.core.subcommands
import com.google.common.base.Stopwatch
import java.io.File
import java.io.IOException
import java.io.PrintWriter
import java.io.StringWriter
import java.util.Arrays
import java.util.concurrent.TimeUnit.SECONDS
import kotlin.system.exitProcess

const val PROGRAM_NAME = "metalava"

fun main(args: Array<String>) {
    val executionEnvironment = ExecutionEnvironment()
    val exitCode = run(executionEnvironment = executionEnvironment, originalArgs = args)

    executionEnvironment.stdout.flush()
    executionEnvironment.stderr.flush()

    exitProcess(exitCode)
}

/**
 * The metadata driver is a command line interface to extracting various metadata from a source tree
 * (or existing signature files etc.). Run with --help to see more details.
 */
fun run(
    executionEnvironment: ExecutionEnvironment,
    originalArgs: Array<String>,
): Int {
    val stdout = executionEnvironment.stdout
    val stderr = executionEnvironment.stderr

    // Preprocess the arguments by adding any additional arguments specified in environment
    // variables.
    val modifiedArgs = preprocessArgv(originalArgs)

    // Process the early options. This does not consume any arguments, they will be parsed again
    // later. A little inefficient but produces cleaner code.
    val earlyOptions = EarlyOptions.parse(modifiedArgs)

    val progressTracker = ProgressTracker(earlyOptions.verbosity.verbose, stdout)

    progressTracker.progress("$PROGRAM_NAME started\n")

    // Dump the arguments, and maybe generate a rerun-script.
    maybeDumpArgv(stdout, originalArgs, modifiedArgs)

    // Actual work begins here.
    val command =
        createMetalavaCommand(
            executionEnvironment,
            progressTracker,
        )
    val exitCode = command.process(modifiedArgs)

    stdout.flush()
    stderr.flush()

    progressTracker.progress("$PROGRAM_NAME exiting with exit code $exitCode\n")

    return exitCode
}

@Suppress("DEPRECATION")
internal fun processFlags(
    executionEnvironment: ExecutionEnvironment,
    environmentManager: EnvironmentManager,
    progressTracker: ProgressTracker
) {
    val stopwatch = Stopwatch.createStarted()

    val reporter = options.reporter
    val reporterApiLint = options.reporterApiLint
    val annotationManager = options.annotationManager
    val modelOptions =
        // If the option was specified on the command line then use [ModelOptions] created from
        // that.
        options.useK2Uast?.let { useK2Uast ->
            ModelOptions.build("from command line") { this[PsiModelOptions.useK2Uast] = useK2Uast }
        }
        // Otherwise, use the [ModelOptions] specified in the [TestEnvironment] if any.
        ?: executionEnvironment.testEnvironment?.modelOptions?.apply {
                // Make sure that the [options.useK2Uast] matches the test environment.
                options.useK2Uast = this[PsiModelOptions.useK2Uast]
            }
            // Otherwise, use the default
            ?: ModelOptions.empty
    val sourceParser =
        environmentManager.createSourceParser(
            reporter = reporter,
            annotationManager = annotationManager,
            javaLanguageLevel = options.javaLanguageLevelAsString,
            kotlinLanguageLevel = options.kotlinLanguageLevelAsString,
            modelOptions = modelOptions,
            jdkHome = options.jdkHome,
        )

    val signatureFileCache = options.signatureFileCache

    val actionContext =
        ActionContext(
            progressTracker = progressTracker,
            reporter = reporter,
            reporterApiLint = reporterApiLint,
            sourceParser = sourceParser,
        )

    val classResolverProvider =
        ClassResolverProvider(
            sourceParser = sourceParser,
            apiClassResolution = options.apiClassResolution,
            classpath = options.classpath,
        )

    val sources = options.sources
    val codebase =
        if (sources.isNotEmpty() && sources[0].path.endsWith(DOT_TXT)) {
            // Make sure all the source files have .txt extensions.
            sources
                .firstOrNull { !it.path.endsWith(DOT_TXT) }
                ?.let {
                    throw MetalavaCliException(
                        "Inconsistent input file types: The first file is of $DOT_TXT, but detected different extension in ${it.path}"
                    )
                }
            val signatureFileLoader = SignatureFileLoader(annotationManager)
            val textCodebase =
                signatureFileLoader.loadFiles(sources, classResolverProvider.classResolver)

            // If this codebase was loaded in order to generate stubs then they will need some
            // additional items to be added that were purposely removed from the signature files.
            if (options.stubsDir != null) {
                addMissingItemsRequiredForGeneratingStubs(
                    sourceParser,
                    textCodebase,
                    reporterApiLint
                )
            }
            textCodebase
        } else if (options.apiJar != null) {
            actionContext.loadFromJarFile(options.apiJar!!)
        } else if (sources.size == 1 && sources[0].path.endsWith(DOT_JAR)) {
            actionContext.loadFromJarFile(sources[0])
        } else if (sources.isNotEmpty() || options.sourcePath.isNotEmpty()) {
            actionContext.loadFromSources(signatureFileCache)
        } else {
            return
        }

    progressTracker.progress(
        "$PROGRAM_NAME analyzed API in ${stopwatch.elapsed(SECONDS)} seconds\n"
    )

    options.subtractApi?.let {
        progressTracker.progress("Subtracting API: ")
        actionContext.subtractApi(signatureFileCache, codebase, it)
    }

    val androidApiLevelXml = options.generateApiLevelXml
    val apiLevelJars = options.apiLevelJars
    val apiGenerator = ApiGenerator(signatureFileCache)
    if (androidApiLevelXml != null && apiLevelJars != null) {
        assert(options.currentApiLevel != -1)

        progressTracker.progress(
            "Generating API levels XML descriptor file, ${androidApiLevelXml.name}: "
        )
        val sdkJarRoot = options.sdkJarRoot
        val sdkInfoFile = options.sdkInfoFile
        val sdkExtArgs: ApiGenerator.SdkExtensionsArguments? =
            if (sdkJarRoot != null && sdkInfoFile != null) {
                ApiGenerator.SdkExtensionsArguments(
                    sdkJarRoot,
                    sdkInfoFile,
                    options.latestReleasedSdkExtension
                )
            } else {
                null
            }
        apiGenerator.generateXml(
            apiLevelJars,
            options.firstApiLevel,
            options.currentApiLevel,
            options.isDeveloperPreviewBuild(),
            androidApiLevelXml,
            codebase,
            sdkExtArgs,
            options.removeMissingClassesInApiLevels
        )
    }

    if (options.docStubsDir != null || options.enhanceDocumentation) {
        if (!codebase.supportsDocumentation()) {
            error("Codebase does not support documentation, so it cannot be enhanced.")
        }
        progressTracker.progress("Enhancing docs: ")
        val docAnalyzer = DocAnalyzer(codebase, reporterApiLint)
        docAnalyzer.enhance()
        val applyApiLevelsXml = options.applyApiLevelsXml
        if (applyApiLevelsXml != null) {
            progressTracker.progress("Applying API levels")
            docAnalyzer.applyApiLevels(applyApiLevelsXml)
        }
    }

    val apiVersionsJson = options.generateApiVersionsJson
    val apiVersionNames = options.apiVersionNames
    if (apiVersionsJson != null && apiVersionNames != null) {
        progressTracker.progress(
            "Generating API version history JSON file, ${apiVersionsJson.name}: "
        )

        val apiType = ApiType.PUBLIC_API
        val apiEmit = apiType.getEmitFilter(options.apiPredicateConfig)
        val apiReference = apiType.getReferenceFilter(options.apiPredicateConfig)

        apiGenerator.generateJson(
            // The signature files can be null if the current version is the only version
            options.apiVersionSignatureFiles ?: emptyList(),
            codebase,
            apiVersionsJson,
            apiVersionNames,
            apiEmit,
            apiReference
        )
    }

    // Generate the documentation stubs *before* we migrate nullness information.
    options.docStubsDir?.let {
        createStubFiles(
            progressTracker,
            it,
            codebase,
            docStubs = true,
        )
    }

    // Based on the input flags, generates various output files such
    // as signature files and/or stubs files
    options.apiFile?.let { apiFile ->
        val apiType = ApiType.PUBLIC_API
        val apiEmit = apiType.getEmitFilter(options.apiPredicateConfig)
        val apiReference = apiType.getReferenceFilter(options.apiPredicateConfig)

        createReportFile(progressTracker, codebase, apiFile, "API") { printWriter ->
            SignatureWriter(
                printWriter,
                apiEmit,
                apiReference,
                codebase.preFiltered,
                fileFormat = options.signatureFileFormat,
                showUnannotated = options.showUnannotated,
                apiVisitorConfig = options.apiVisitorConfig
            )
        }
    }

    options.apiXmlFile?.let { apiFile ->
        val apiType = ApiType.PUBLIC_API
        val apiEmit = apiType.getEmitFilter(options.apiPredicateConfig)
        val apiReference = apiType.getReferenceFilter(options.apiPredicateConfig)

        createReportFile(progressTracker, codebase, apiFile, "XML API") { printWriter ->
            JDiffXmlWriter(
                printWriter,
                apiEmit,
                apiReference,
                codebase.preFiltered,
                showUnannotated = @Suppress("DEPRECATION") options.showUnannotated,
                config = options.apiVisitorConfig,
            )
        }
    }

    options.removedApiFile?.let { apiFile ->
        val unfiltered = codebase.original ?: codebase

        val apiType = ApiType.REMOVED
        val removedEmit = apiType.getEmitFilter(options.apiPredicateConfig)
        val removedReference = apiType.getReferenceFilter(options.apiPredicateConfig)

        createReportFile(
            progressTracker,
            unfiltered,
            apiFile,
            "removed API",
            options.deleteEmptyRemovedSignatures
        ) { printWriter ->
            SignatureWriter(
                printWriter,
                removedEmit,
                removedReference,
                codebase.original != null,
                options.includeSignatureFormatVersionRemoved,
                options.signatureFileFormat,
                options.showUnannotated,
                options.apiVisitorConfig,
            )
        }
    }

    val apiPredicateConfigIgnoreShown = options.apiPredicateConfig.copy(ignoreShown = true)
    val apiReferenceIgnoreShown = ApiPredicate(config = apiPredicateConfigIgnoreShown)
    options.dexApiFile?.let { apiFile ->
        val apiFilter = FilterPredicate(ApiPredicate())

        createReportFile(progressTracker, codebase, apiFile, "DEX API") { printWriter ->
            DexApiWriter(printWriter, apiFilter, apiReferenceIgnoreShown, options.apiVisitorConfig)
        }
    }

    options.proguard?.let { proguard ->
        val apiEmit = FilterPredicate(ApiPredicate())
        createReportFile(progressTracker, codebase, proguard, "Proguard file") { printWriter ->
            ProguardWriter(printWriter, apiEmit, apiReferenceIgnoreShown)
        }
    }

    options.sdkValueDir?.let { dir ->
        dir.mkdirs()
        SdkFileWriter(codebase, dir).generate()
    }

    for (check in options.compatibilityChecks) {
        actionContext.checkCompatibility(signatureFileCache, classResolverProvider, codebase, check)
    }

    val previousApiFile = options.migrateNullsFrom
    if (previousApiFile != null) {
        val previous =
            if (previousApiFile.path.endsWith(DOT_JAR)) {
                actionContext.loadFromJarFile(previousApiFile)
            } else {
                signatureFileCache.load(file = previousApiFile)
            }

        // If configured, checks for newly added nullness information compared
        // to the previous stable API and marks the newly annotated elements
        // as migrated (which will cause the Kotlin compiler to treat problems
        // as warnings instead of errors

        NullnessMigration.migrateNulls(codebase, previous)

        previous.dispose()
    }

    convertToWarningNullabilityAnnotations(
        codebase,
        options.forceConvertToWarningNullabilityAnnotations
    )

    // Now that we've migrated nullness information we can proceed to write non-doc stubs, if any.

    options.stubsDir?.let {
        createStubFiles(
            progressTracker,
            it,
            codebase,
            docStubs = false,
        )
    }

    options.externalAnnotations?.let { extractAnnotations(progressTracker, codebase, it) }

    val packageCount = codebase.size()
    progressTracker.progress(
        "$PROGRAM_NAME finished handling $packageCount packages in ${stopwatch.elapsed(SECONDS)} seconds\n"
    )
}

/**
 * When generating stubs from text signature files some additional items are needed.
 *
 * Those items are:
 * * Constructors - in the signature file a missing constructor means no publicly visible
 *   constructor but the stub classes still need a constructor.
 */
@Suppress("DEPRECATION")
private fun addMissingItemsRequiredForGeneratingStubs(
    sourceParser: SourceParser,
    codebase: Codebase,
    reporterApiLint: Reporter,
) {
    // Reuse the existing ApiAnalyzer support for adding constructors that is used in
    // [loadFromSources], to make sure that the constructors are correct when generating stubs
    // from source files.
    val analyzer = ApiAnalyzer(sourceParser, codebase, reporterApiLint, options.apiAnalyzerConfig)
    analyzer.addConstructors { _ -> true }
}

private fun ActionContext.subtractApi(
    signatureFileCache: SignatureFileCache,
    codebase: Codebase,
    subtractApiFile: File,
) {
    val path = subtractApiFile.path
    val oldCodebase =
        when {
            path.endsWith(DOT_TXT) -> signatureFileCache.load(subtractApiFile)
            path.endsWith(DOT_JAR) -> loadFromJarFile(subtractApiFile)
            else ->
                throw MetalavaCliException(
                    "Unsupported $ARG_SUBTRACT_API format, expected .txt or .jar: ${subtractApiFile.name}"
                )
        }

    @Suppress("DEPRECATION")
    CodebaseComparator(
            apiVisitorConfig = @Suppress("DEPRECATION") options.apiVisitorConfig,
        )
        .compare(
            object : ComparisonVisitor() {
                override fun compare(old: ClassItem, new: ClassItem) {
                    new.emit = false
                }
            },
            oldCodebase,
            codebase,
            ApiType.ALL.getReferenceFilter(options.apiPredicateConfig)
        )
}

/** Checks compatibility of the given codebase with the codebase described in the signature file. */
@Suppress("DEPRECATION")
private fun ActionContext.checkCompatibility(
    signatureFileCache: SignatureFileCache,
    classResolverProvider: ClassResolverProvider,
    newCodebase: Codebase,
    check: CheckRequest,
) {
    progressTracker.progress("Checking API compatibility ($check): ")

    val apiType = check.apiType
    val generatedApiFile =
        when (apiType) {
            ApiType.PUBLIC_API -> options.apiFile
            ApiType.REMOVED -> options.removedApiFile
            else -> error("unsupported $apiType")
        }

    // Fast path: if we've already generated a signature file, and it's identical to the previously
    // released API then we're good.
    //
    // Some things to watch out for:
    // * There is no guarantee that the signature file is actually a txt file, it could also be
    //   a `jar` file, so double check that first.
    // * Reading two files that may be a couple of MBs each isn't a particularly fast path so
    //   check the lengths first and then compare contents byte for byte so that it exits
    //   quickly if they're different and does not do all the UTF-8 conversions.
    generatedApiFile?.let { apiFile ->
        val signatureFile = check.files.last()
        val compatibilityCheckCanBeSkipped =
            signatureFile.extension == "txt" && compareFileContents(apiFile, signatureFile)
        // TODO(b/301282006): Remove global variable use when this can be tested properly
        fastPathCheckResult = compatibilityCheckCanBeSkipped
        if (compatibilityCheckCanBeSkipped) return
    }

    val oldCodebases =
        check.files.map { signatureFile ->
            if (signatureFile.path.endsWith(DOT_JAR)) {
                loadFromJarFile(signatureFile)
            } else {
                signatureFileCache.load(signatureFile, classResolverProvider.classResolver)
            }
        }

    var baseApi: Codebase? = null

    if (options.showUnannotated && apiType == ApiType.PUBLIC_API) {
        val baseApiFile = options.baseApiForCompatCheck
        if (baseApiFile != null) {
            baseApi = signatureFileCache.load(file = baseApiFile)
        }
    } else if (options.baseApiForCompatCheck != null) {
        // This option does not make sense with showAnnotation, as the "base" in that case
        // is the non-annotated APIs.
        throw MetalavaCliException(
            "$ARG_CHECK_COMPATIBILITY_BASE_API is not compatible with --showAnnotation."
        )
    }

    // The MergedCodebase assume that the codebases are in order from widest to narrowest which is
    // the opposite of how they are supplied so this reverses them.
    val mergedOldCodebases = MergedCodebase(oldCodebases.reversed())

    // If configured, compares the new API with the previous API and reports
    // any incompatibilities.
    CompatibilityCheck.checkCompatibility(
        newCodebase,
        mergedOldCodebases,
        apiType,
        baseApi,
        options.reporterCompatibilityReleased,
        options.issueConfiguration,
    )
}

/** Compare two files to see if they are byte for byte identical. */
private fun compareFileContents(file1: File, file2: File): Boolean {
    // First check the lengths, if they are different they cannot be identical.
    if (file1.length() == file2.length()) {
        // Then load the contents in chunks to see if they differ.
        file1.inputStream().buffered().use { stream1 ->
            file2.inputStream().buffered().use { stream2 ->
                val buffer1 = ByteArray(DEFAULT_BUFFER_SIZE)
                val buffer2 = ByteArray(DEFAULT_BUFFER_SIZE)
                do {
                    val c1 = stream1.read(buffer1)
                    val c2 = stream2.read(buffer2)
                    if (c1 != c2) {
                        // This should never happen as the files are the same length.
                        break
                    }
                    if (c1 == -1) {
                        // They have both reached the end of file.
                        return true
                    }
                    // Check the buffer contents, if they differ exit the loop otherwise, continue
                    // on to read the next chunks.
                } while (Arrays.equals(buffer1, 0, c1, buffer2, 0, c2))
            }
        }
    }
    return false
}

/**
 * Used to store whether the fast path check in the previous method succeeded or not that can be
 * checked by tests.
 *
 * The test must initialize it to `null`. Then if the fast path check is run it will set it a
 * non-null to indicate whether the fast path was taken or not. The test can then differentiate
 * between the following states:
 * * `null` - the fast path check was not performed.
 * * `false` - the fast path check was performed and the fast path was not taken.
 * * `true` - the fast path check was performed and the fast path was taken.
 *
 * This is used because there is no nice way to test this code in isolation but the code needs to be
 * updated to deal with some test failures. This is a hack to avoid a catch-22 where this code needs
 * to be refactored to allow it to be tested but it needs to be tested before it can be safely
 * refactored.
 *
 * TODO(b/301282006): Remove this variable when the fast path this can be tested properly
 */
internal var fastPathCheckResult: Boolean? = null

private fun convertToWarningNullabilityAnnotations(codebase: Codebase, filter: PackageFilter?) {
    if (filter != null) {
        // Our caller has asked for these APIs to not trigger nullness errors (only warnings) if
        // their callers make incorrect nullness assumptions (for example, calling a function on a
        // reference of nullable type). The way to communicate this to kotlinc is to mark these
        // APIs as RecentlyNullable/RecentlyNonNull
        codebase.accept(MarkPackagesAsRecent(filter))
    }
}

@Suppress("DEPRECATION")
private fun ActionContext.loadFromSources(
    signatureFileCache: SignatureFileCache,
): Codebase {
    progressTracker.progress("Processing sources: ")

    val sourceSet =
        if (options.sources.isEmpty()) {
            if (options.verbose) {
                options.stdout.println(
                    "No source files specified: recursively including all sources found in the source path (${options.sourcePath.joinToString()}})"
                )
            }
            SourceSet.createFromSourcePath(options.reporter, options.sourcePath)
        } else {
            SourceSet(options.sources, options.sourcePath)
        }

    val commonSourceSet =
        if (options.commonSourcePath.isNotEmpty())
            SourceSet.createFromSourcePath(options.reporter, options.commonSourcePath)
        else SourceSet.empty()

    progressTracker.progress("Reading Codebase: ")
    val codebase =
        sourceParser.parseSources(
            sourceSet,
            commonSourceSet,
            "Codebase loaded from source folders",
            classPath = options.classpath,
        )

    progressTracker.progress("Analyzing API: ")

    val analyzer = ApiAnalyzer(sourceParser, codebase, reporterApiLint, options.apiAnalyzerConfig)
    analyzer.mergeExternalInclusionAnnotations()

    analyzer.computeApi()

    val apiPredicateConfigIgnoreShown = options.apiPredicateConfig.copy(ignoreShown = true)
    val filterEmit = ApiPredicate(ignoreRemoved = false, config = apiPredicateConfigIgnoreShown)
    val apiEmitAndReference = ApiPredicate(config = apiPredicateConfigIgnoreShown)

    // Copy methods from soon-to-be-hidden parents into descendant classes, when necessary. Do
    // this before merging annotations or performing checks on the API to ensure that these methods
    // can have annotations added and are checked properly.
    progressTracker.progress("Insert missing stubs methods: ")
    analyzer.generateInheritedStubs(apiEmitAndReference, apiEmitAndReference)

    analyzer.mergeExternalQualifierAnnotations()
    options.nullabilityAnnotationsValidator?.validateAllFrom(
        codebase,
        options.validateNullabilityFromList
    )
    options.nullabilityAnnotationsValidator?.report()
    analyzer.handleStripping()

    // General API checks for Android APIs
    AndroidApiChecks(reporterApiLint).check(codebase)

    if (options.checkApi) {
        progressTracker.progress("API Lint: ")
        val localTimer = Stopwatch.createStarted()
        // See if we should provide a previous codebase to provide a delta from?
        val previousApiFile = options.checkApiBaselineApiFile
        val previous =
            when {
                previousApiFile == null -> null
                previousApiFile.path.endsWith(DOT_JAR) -> loadFromJarFile(previousApiFile)
                else -> signatureFileCache.load(file = previousApiFile)
            }
        val apiLintReporter = reporterApiLint as DefaultReporter
        ApiLint.check(
            codebase,
            previous,
            apiLintReporter,
            options.manifest,
            options.apiVisitorConfig,
        )
        progressTracker.progress(
            "$PROGRAM_NAME ran api-lint in ${localTimer.elapsed(SECONDS)} seconds with ${apiLintReporter.getBaselineDescription()}"
        )
    }

    // Compute default constructors (and add missing package private constructors
    // to make stubs compilable if necessary). Do this after all the checks as
    // these are not part of the API.
    if (options.stubsDir != null || options.docStubsDir != null) {
        progressTracker.progress("Insert missing constructors: ")
        analyzer.addConstructors(filterEmit)
    }

    progressTracker.progress("Performing misc API checks: ")
    analyzer.performChecks()

    return codebase
}

/**
 * Avoids creating a [ClassResolver] unnecessarily as it is expensive to create but once created
 * allows it to be reused for the same reason.
 */
private class ClassResolverProvider(
    private val sourceParser: SourceParser,
    private val apiClassResolution: ApiClassResolution,
    private val classpath: List<File>
) {
    val classResolver: ClassResolver? by lazy {
        if (apiClassResolution == ApiClassResolution.API_CLASSPATH && classpath.isNotEmpty()) {
            sourceParser.getClassResolver(classpath)
        } else {
            null
        }
    }
}

@Suppress("DEPRECATION")
fun ActionContext.loadFromJarFile(
    apiJar: File,
    preFiltered: Boolean = false,
    apiAnalyzerConfig: ApiAnalyzer.Config = options.apiAnalyzerConfig,
    codebaseValidator: (Codebase) -> Unit = { codebase ->
        options.nullabilityAnnotationsValidator?.validateAllFrom(
            codebase,
            options.validateNullabilityFromList
        )
        options.nullabilityAnnotationsValidator?.report()
    },
    apiPredicateConfig: ApiPredicate.Config = options.apiPredicateConfig,
): Codebase {
    progressTracker.progress("Processing jar file: ")

    val codebase = sourceParser.loadFromJar(apiJar, preFiltered)
    val apiEmit =
        ApiPredicate(
            config = apiPredicateConfig.copy(ignoreShown = true),
        )
    val apiReference = apiEmit
    val analyzer = ApiAnalyzer(sourceParser, codebase, reporterApiLint, apiAnalyzerConfig)
    analyzer.mergeExternalInclusionAnnotations()
    analyzer.computeApi()
    analyzer.mergeExternalQualifierAnnotations()
    codebaseValidator(codebase)
    analyzer.generateInheritedStubs(apiEmit, apiReference)
    return codebase
}

internal fun disableStderrDumping(): Boolean {
    return !assertionsEnabled() &&
        System.getenv(ENV_VAR_METALAVA_DUMP_ARGV) == null &&
        !isUnderTest()
}

@Suppress("DEPRECATION")
private fun extractAnnotations(progressTracker: ProgressTracker, codebase: Codebase, file: File) {
    val localTimer = Stopwatch.createStarted()

    options.externalAnnotations?.let { outputFile ->
        ExtractAnnotations(codebase, options.reporter, outputFile).extractAnnotations()
        if (options.verbose) {
            progressTracker.progress(
                "$PROGRAM_NAME extracted annotations into $file in ${localTimer.elapsed(SECONDS)} seconds\n"
            )
        }
    }
}

@Suppress("DEPRECATION")
private fun createStubFiles(
    progressTracker: ProgressTracker,
    stubDir: File,
    codebase: Codebase,
    docStubs: Boolean,
) {
    if (docStubs) {
        progressTracker.progress("Generating documentation stub files: ")
    } else {
        progressTracker.progress("Generating stub files: ")
    }

    val localTimer = Stopwatch.createStarted()

    val stubWriterConfig =
        options.stubWriterConfig.let {
            if (docStubs) {
                // Doc stubs always include documentation.
                it.copy(includeDocumentationInStubs = true)
            } else {
                it
            }
        }

    val stubWriter =
        StubWriter(
            stubsDir = stubDir,
            generateAnnotations = options.generateAnnotations,
            preFiltered = codebase.preFiltered,
            docStubs = docStubs,
            reporter = options.reporter,
            config = stubWriterConfig,
        )
    codebase.accept(stubWriter)

    if (docStubs) {
        // Overview docs? These are generally in the empty package.
        codebase.findPackage("")?.let { empty ->
            val overview = empty.overviewDocumentation
            if (!overview.isNullOrBlank()) {
                stubWriter.writeDocOverview(empty, overview)
            }
        }
    }

    progressTracker.progress(
        "$PROGRAM_NAME wrote ${if (docStubs) "documentation" else ""} stubs directory $stubDir in ${
        localTimer.elapsed(SECONDS)} seconds\n"
    )
}

@Suppress("DEPRECATION")
fun createReportFile(
    progressTracker: ProgressTracker,
    codebase: Codebase,
    apiFile: File,
    description: String?,
    deleteEmptyFiles: Boolean = false,
    createVisitor: (PrintWriter) -> ApiVisitor
) {
    if (description != null) {
        progressTracker.progress("Writing $description file: ")
    }
    val localTimer = Stopwatch.createStarted()
    try {
        val stringWriter = StringWriter()
        val writer = PrintWriter(stringWriter)
        writer.use { printWriter ->
            val apiWriter = createVisitor(printWriter)
            codebase.accept(apiWriter)
        }
        val text = stringWriter.toString()
        if (text.isNotEmpty() || !deleteEmptyFiles) {
            apiFile.writeText(text)
        }
    } catch (e: IOException) {
        options.reporter.report(Issues.IO_ERROR, apiFile, "Cannot open file for write.")
    }
    if (description != null) {
        progressTracker.progress(
            "$PROGRAM_NAME wrote $description file $apiFile in ${localTimer.elapsed(SECONDS)} seconds\n"
        )
    }
}

/** Whether metalava is running unit tests */
fun isUnderTest() = java.lang.Boolean.getBoolean(ENV_VAR_METALAVA_TESTS_RUNNING)

/** Whether metalava is being invoked as part of an Android platform build */
fun isBuildingAndroid() = System.getenv("ANDROID_BUILD_TOP") != null && !isUnderTest()

private fun createMetalavaCommand(
    executionEnvironment: ExecutionEnvironment,
    progressTracker: ProgressTracker
): MetalavaCommand {
    val command =
        MetalavaCommand(
            executionEnvironment = executionEnvironment,
            progressTracker = progressTracker,
            defaultCommandName = "main",
        )
    command.subcommands(
        MainCommand(command.commonOptions, executionEnvironment),
        AndroidJarsToSignaturesCommand(),
        HelpCommand(),
        MakeAnnotationsPackagePrivateCommand(),
        MergeSignaturesCommand(),
        SignatureToDexCommand(),
        SignatureToJDiffCommand(),
        UpdateSignatureHeaderCommand(),
        VersionCommand(),
    )
    return command
}
