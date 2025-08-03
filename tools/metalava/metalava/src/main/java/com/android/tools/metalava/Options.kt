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
import com.android.SdkConstants.FN_FRAMEWORK_LIBRARY
import com.android.tools.lint.detector.api.isJdkFolder
import com.android.tools.metalava.cli.common.ARG_HIDE
import com.android.tools.metalava.cli.common.ARG_HIDE_CATEGORY
import com.android.tools.metalava.cli.common.CommonOptions
import com.android.tools.metalava.cli.common.IssueReportingOptions
import com.android.tools.metalava.cli.common.MetalavaCliException
import com.android.tools.metalava.cli.common.Terminal
import com.android.tools.metalava.cli.common.TerminalColor
import com.android.tools.metalava.cli.common.Verbosity
import com.android.tools.metalava.cli.common.enumOption
import com.android.tools.metalava.cli.common.fileForPathInner
import com.android.tools.metalava.cli.common.stringToExistingDir
import com.android.tools.metalava.cli.common.stringToExistingFile
import com.android.tools.metalava.cli.common.stringToNewDir
import com.android.tools.metalava.cli.common.stringToNewFile
import com.android.tools.metalava.cli.compatibility.ARG_CHECK_COMPATIBILITY_API_RELEASED
import com.android.tools.metalava.cli.compatibility.ARG_CHECK_COMPATIBILITY_REMOVED_RELEASED
import com.android.tools.metalava.cli.compatibility.CompatibilityCheckOptions
import com.android.tools.metalava.cli.compatibility.CompatibilityCheckOptions.CheckRequest
import com.android.tools.metalava.cli.signature.SignatureFormatOptions
import com.android.tools.metalava.lint.DefaultLintErrorMessage
import com.android.tools.metalava.manifest.Manifest
import com.android.tools.metalava.manifest.emptyManifest
import com.android.tools.metalava.model.AnnotationManager
import com.android.tools.metalava.model.TypedefMode
import com.android.tools.metalava.model.source.DEFAULT_JAVA_LANGUAGE_LEVEL
import com.android.tools.metalava.model.source.DEFAULT_KOTLIN_LANGUAGE_LEVEL
import com.android.tools.metalava.model.text.ApiClassResolution
import com.android.tools.metalava.model.visitors.ApiVisitor
import com.android.tools.metalava.reporter.Baseline
import com.android.tools.metalava.reporter.DEFAULT_BASELINE_NAME
import com.android.tools.metalava.reporter.Reporter
import com.android.tools.metalava.stub.StubWriterConfig
import com.android.utils.SdkUtils.wrap
import com.github.ajalt.clikt.core.NoSuchOption
import com.github.ajalt.clikt.parameters.groups.OptionGroup
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.deprecated
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.unique
import com.github.ajalt.clikt.parameters.types.choice
import com.github.ajalt.clikt.parameters.types.file
import com.github.ajalt.clikt.parameters.types.int
import java.io.File
import java.io.IOException
import java.io.PrintWriter
import java.io.StringWriter
import java.util.Locale
import java.util.Optional
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty
import org.jetbrains.jps.model.java.impl.JavaSdkUtil

/**
 * A [ReadWriteProperty] that is used as the delegate for [options].
 *
 * It provides read/write methods and also a [disallowAccess] method which when called will cause
 * any attempt to read the [options] property to fail. This allows code to ensure that any code
 * which it calls does not access the deprecated [options] property.
 */
object OptionsDelegate : ReadWriteProperty<Nothing?, Options> {

    /**
     * The value of this delegate.
     *
     * Is `null` if [setValue] has not been called since the last call to [disallowAccess]. In that
     * case any attempt to read the value of this delegate will fail.
     */
    private var possiblyNullOptions: Options? = Options()

    /**
     * The stack trace of the last caller to [disallowAccess] (if any) to make it easy to determine
     * why a read of [options] failed.
     */
    private var disallowerStackTrace: Throwable? = null

    /** Prevent all future reads of [options] until the [setValue] method is next called. */
    fun disallowAccess() {
        disallowerStackTrace = UnexpectedOptionsAccess("Global options property cleared")
        possiblyNullOptions = null
    }

    override fun setValue(thisRef: Nothing?, property: KProperty<*>, value: Options) {
        disallowerStackTrace = null
        possiblyNullOptions = value
    }

    override fun getValue(thisRef: Nothing?, property: KProperty<*>): Options {
        return possiblyNullOptions
            ?: throw UnexpectedOptionsAccess("options is not set", disallowerStackTrace!!)
    }
}

/** A private class to try and avoid it being caught and ignored. */
private class UnexpectedOptionsAccess(message: String, cause: Throwable? = null) :
    RuntimeException(message, cause)

/**
 * Global options for the metadata extraction tool
 *
 * This is an empty options which is created to avoid having a nullable options. It is replaced with
 * the actual options to use, either created from the command line arguments for the main process or
 * with arguments supplied by tests.
 */
@Deprecated(
    """
    Do not add any more usages of this and please remove any existing uses that you find. Global
    variables tightly couple all the code that uses them making them hard to test, modularize and
    reuse. Which is why there is an ongoing process to remove usages of global variables and
    eventually the global variable itself.
    """
)
var options by OptionsDelegate

private const val INDENT_WIDTH = 45

const val ARG_CLASS_PATH = "--classpath"
const val ARG_COMMON_SOURCE_PATH = "--common-source-path"
const val ARG_SOURCE_PATH = "--source-path"
const val ARG_SOURCE_FILES = "--source-files"
const val ARG_XML_API = "--api-xml"
const val ARG_API_CLASS_RESOLUTION = "--api-class-resolution"
const val ARG_DEX_API = "--dex-api"
const val ARG_SDK_VALUES = "--sdk-values"
const val ARG_MERGE_QUALIFIER_ANNOTATIONS = "--merge-qualifier-annotations"
const val ARG_MERGE_INCLUSION_ANNOTATIONS = "--merge-inclusion-annotations"
const val ARG_VALIDATE_NULLABILITY_FROM_MERGED_STUBS = "--validate-nullability-from-merged-stubs"
const val ARG_VALIDATE_NULLABILITY_FROM_LIST = "--validate-nullability-from-list"
const val ARG_NULLABILITY_WARNINGS_TXT = "--nullability-warnings-txt"
const val ARG_NULLABILITY_ERRORS_NON_FATAL = "--nullability-errors-non-fatal"
const val ARG_INPUT_API_JAR = "--input-api-jar"
const val ARG_DOC_STUBS = "--doc-stubs"
const val ARG_KOTLIN_STUBS = "--kotlin-stubs"
/** Used by Firebase, see b/116185431#comment15, not used by Android Platform or AndroidX */
const val ARG_PROGUARD = "--proguard"
const val ARG_EXTRACT_ANNOTATIONS = "--extract-annotations"
const val ARG_EXCLUDE_DOCUMENTATION_FROM_STUBS = "--exclude-documentation-from-stubs"
const val ARG_ENHANCE_DOCUMENTATION = "--enhance-documentation"
const val ARG_HIDE_PACKAGE = "--hide-package"
const val ARG_MANIFEST = "--manifest"
const val ARG_MIGRATE_NULLNESS = "--migrate-nullness"
const val ARG_WARNINGS_AS_ERRORS = "--warnings-as-errors"
const val ARG_LINTS_AS_ERRORS = "--lints-as-errors"
const val ARG_SHOW_ANNOTATION = "--show-annotation"
const val ARG_SHOW_SINGLE_ANNOTATION = "--show-single-annotation"
const val ARG_HIDE_ANNOTATION = "--hide-annotation"
const val ARG_REVERT_ANNOTATION = "--revert-annotation"
const val ARG_SUPPRESS_COMPATIBILITY_META_ANNOTATION = "--suppress-compatibility-meta-annotation"
const val ARG_SHOW_FOR_STUB_PURPOSES_ANNOTATION = "--show-for-stub-purposes-annotation"
const val ARG_SHOW_UNANNOTATED = "--show-unannotated"
const val ARG_APPLY_API_LEVELS = "--apply-api-levels"
const val ARG_GENERATE_API_LEVELS = "--generate-api-levels"
const val ARG_REMOVE_MISSING_CLASS_REFERENCES_IN_API_LEVELS =
    "--remove-missing-class-references-in-api-levels"
const val ARG_ANDROID_JAR_PATTERN = "--android-jar-pattern"
const val ARG_CURRENT_VERSION = "--current-version"
const val ARG_FIRST_VERSION = "--first-version"
const val ARG_CURRENT_CODENAME = "--current-codename"
const val ARG_CURRENT_JAR = "--current-jar"
const val ARG_GENERATE_API_VERSION_HISTORY = "--generate-api-version-history"
const val ARG_API_VERSION_SIGNATURE_FILES = "--api-version-signature-files"
const val ARG_API_VERSION_NAMES = "--api-version-names"
const val ARG_API_LINT = "--api-lint"
const val ARG_JAVA_SOURCE = "--java-source"
const val ARG_KOTLIN_SOURCE = "--kotlin-source"
const val ARG_SDK_HOME = "--sdk-home"
const val ARG_JDK_HOME = "--jdk-home"
const val ARG_COMPILE_SDK_VERSION = "--compile-sdk-version"
const val ARG_INCLUDE_SOURCE_RETENTION = "--include-source-retention"
const val ARG_PASS_THROUGH_ANNOTATION = "--pass-through-annotation"
const val ARG_EXCLUDE_ANNOTATION = "--exclude-annotation"
const val ARG_PASS_BASELINE_UPDATES = "--pass-baseline-updates"
const val ARG_BASELINE = "--baseline"
const val ARG_BASELINE_API_LINT = "--baseline:api-lint"
const val ARG_BASELINE_CHECK_COMPATIBILITY_RELEASED = "--baseline:compatibility:released"
const val ARG_REPORT_EVEN_IF_SUPPRESSED = "--report-even-if-suppressed"
const val ARG_UPDATE_BASELINE = "--update-baseline"
const val ARG_UPDATE_BASELINE_API_LINT = "--update-baseline:api-lint"
const val ARG_UPDATE_BASELINE_CHECK_COMPATIBILITY_RELEASED =
    "--update-baseline:compatibility:released"
const val ARG_STUB_PACKAGES = "--stub-packages"
const val ARG_STUB_IMPORT_PACKAGES = "--stub-import-packages"
const val ARG_DELETE_EMPTY_BASELINES = "--delete-empty-baselines"
const val ARG_DELETE_EMPTY_REMOVED_SIGNATURES = "--delete-empty-removed-signatures"
const val ARG_SUBTRACT_API = "--subtract-api"
const val ARG_TYPEDEFS_IN_SIGNATURES = "--typedefs-in-signatures"
const val ARG_IGNORE_CLASSES_ON_CLASSPATH = "--ignore-classes-on-classpath"
const val ARG_ERROR_MESSAGE_API_LINT = "--error-message:api-lint"
const val ARG_SDK_JAR_ROOT = "--sdk-extensions-root"
const val ARG_SDK_INFO_FILE = "--sdk-extensions-info"
const val ARG_USE_K2_UAST = "--Xuse-k2-uast"
const val ARG_SOURCE_MODEL_PROVIDER = "--source-model-provider"

class Options(
    private val commonOptions: CommonOptions = CommonOptions(),
    private val issueReportingOptions: IssueReportingOptions = IssueReportingOptions(),
    private val compatibilityCheckOptions: CompatibilityCheckOptions = CompatibilityCheckOptions(),
    signatureFileOptions: SignatureFileOptions = SignatureFileOptions(),
    signatureFormatOptions: SignatureFormatOptions = SignatureFormatOptions(),
    stubGenerationOptions: StubGenerationOptions = StubGenerationOptions(),
) : OptionGroup() {
    /** Execution environment; initialized in [parse]. */
    private lateinit var executionEnvironment: ExecutionEnvironment

    /** Writer to direct output to. */
    val stdout: PrintWriter
        get() = executionEnvironment.stdout
    /** Writer to direct error messages to. */
    val stderr: PrintWriter
        get() = executionEnvironment.stderr

    /** Internal list backing [sources] */
    private val mutableSources: MutableList<File> = mutableListOf()
    /** Internal list backing [commonSourcePath] */
    private val mutableCommonSourcePath: MutableList<File> = mutableListOf()
    /** Internal list backing [sourcePath] */
    private val mutableSourcePath: MutableList<File> = mutableListOf()
    /** Internal list backing [classpath] */
    private val mutableClassPath: MutableList<File> = mutableListOf()
    /** Internal builder backing [allShowAnnotations] */
    private val allShowAnnotationsBuilder = AnnotationFilterBuilder()
    /** Internal builder backing [showAnnotations] */
    private val showAnnotationsBuilder = AnnotationFilterBuilder()
    /** Internal builder backing [showSingleAnnotations] */
    private val showSingleAnnotationsBuilder = AnnotationFilterBuilder()
    /** Internal builder backing [showForStubPurposesAnnotations] */
    private val showForStubPurposesAnnotationBuilder = AnnotationFilterBuilder()
    /** Internal builder backing [hideAnnotations] */
    private val hideAnnotationsBuilder = AnnotationFilterBuilder()
    /** Internal builder backing [revertAnnotations] */
    private val revertAnnotationsBuilder = AnnotationFilterBuilder()
    /** Internal list backing [stubImportPackages] */
    private val mutableStubImportPackages: MutableSet<String> = mutableSetOf()
    /** Internal list backing [mergeQualifierAnnotations] */
    private val mutableMergeQualifierAnnotations: MutableList<File> = mutableListOf()
    /** Internal list backing [mergeInclusionAnnotations] */
    private val mutableMergeInclusionAnnotations: MutableList<File> = mutableListOf()
    /** Internal list backing [hidePackages] */
    private val mutableHidePackages: MutableList<String> = mutableListOf()
    /** Internal list backing [passThroughAnnotations] */
    private val mutablePassThroughAnnotations: MutableSet<String> = mutableSetOf()
    /** Internal list backing [excludeAnnotations] */
    private val mutableExcludeAnnotations: MutableSet<String> = mutableSetOf()

    /** API to subtract from signature and stub generation. Corresponds to [ARG_SUBTRACT_API]. */
    var subtractApi: File? = null

    /**
     * Backing property for [nullabilityAnnotationsValidator]
     *
     * This uses [Optional] to wrap the value as [lazy] cannot handle nullable values as it uses
     * `null` as a special value.
     *
     * Creates [NullabilityAnnotationsValidator] lazily as it depends on a number of different
     * options which may be supplied in different orders.
     */
    private val optionalNullabilityAnnotationsValidator by lazy {
        Optional.ofNullable(
            if (validateNullabilityFromMergedStubs || validateNullabilityFromList != null) {
                NullabilityAnnotationsValidator(
                    reporter,
                    nullabilityErrorsFatal,
                    nullabilityWarningsTxt
                )
            } else null
        )
    }

    /** Validator for nullability annotations, if validation is enabled. */
    val nullabilityAnnotationsValidator: NullabilityAnnotationsValidator?
        get() = optionalNullabilityAnnotationsValidator.orElse(null)

    /** Whether nullability validation errors should be considered fatal. */
    private var nullabilityErrorsFatal = true

    /**
     * A file to write non-fatal nullability validation issues to. If null, all issues are treated
     * as fatal or else logged as warnings, depending on the value of [nullabilityErrorsFatal].
     */
    private var nullabilityWarningsTxt: File? = null

    /**
     * Whether to validate nullability for all the classes where we are merging annotations from
     * external java stub files. If true, [nullabilityAnnotationsValidator] must be set.
     */
    var validateNullabilityFromMergedStubs = false

    /**
     * A file containing a list of classes whose nullability annotations should be validated. If
     * set, [nullabilityAnnotationsValidator] must also be set.
     */
    var validateNullabilityFromList: File? = null

    /**
     * Whether to include element documentation (javadoc and KDoc) is in the generated stubs.
     * (Copyright notices are not affected by this, they are always included. Documentation stubs
     * (--doc-stubs) are not affected.)
     */
    var includeDocumentationInStubs = true

    /**
     * Enhance documentation in various ways, for example auto-generating documentation based on
     * source annotations present in the code. This is implied by --doc-stubs.
     */
    var enhanceDocumentation = false

    /** If true, treat all warnings as errors */
    var warningsAreErrors: Boolean = false

    /** If true, treat all API lint warnings as errors */
    var lintsAreErrors: Boolean = false

    /** Ths list of source roots in the common module */
    val commonSourcePath: List<File> = mutableCommonSourcePath

    /** The list of source roots */
    val sourcePath: List<File> = mutableSourcePath

    /** The list of dependency jars */
    val classpath: List<File> = mutableClassPath

    /** All source files to parse */
    var sources: List<File> = mutableSources

    val apiClassResolution by
        enumOption(
            help =
                """
                Determines how class resolution is performed when loading API signature files. Any
                classes that cannot be found will be treated as empty.",
            """
                    .trimIndent(),
            enumValueHelpGetter = { it.help },
            default = ApiClassResolution.API_CLASSPATH,
            key = { it.optionValue },
        )

    /**
     * Whether to include APIs with annotations (intended for documentation purposes). This includes
     * [showAnnotations], [showSingleAnnotations] and [showForStubPurposesAnnotations].
     */
    val allShowAnnotations by lazy(allShowAnnotationsBuilder::build)

    /**
     * A filter that will match annotations which will cause an annotated item (and its enclosed
     * items unless overridden by a closer annotation) to be included in the API surface.
     *
     * @see [allShowAnnotations]
     */
    val showAnnotations by lazy(showAnnotationsBuilder::build)

    /**
     * Like [showAnnotations], but does not work recursively.
     *
     * @see [allShowAnnotations]
     */
    private val showSingleAnnotations by lazy(showSingleAnnotationsBuilder::build)

    /**
     * Annotations that defines APIs that are implicitly included in the API surface. These APIs
     * will be included in certain kinds of output such as stubs, but others (e.g. API lint and the
     * API signature file) ignore them.
     *
     * @see [allShowAnnotations]
     */
    private val showForStubPurposesAnnotations by lazy(showForStubPurposesAnnotationBuilder::build)

    /**
     * Whether to include unannotated elements if {@link #showAnnotations} is set. Note: This only
     * applies to signature files, not stub files.
     */
    var showUnannotated = false

    /** Whether to validate the API for best practices */
    var checkApi = false

    /** If non-null, an API file to use to hide for controlling what parts of the API are new */
    var checkApiBaselineApiFile: File? = null

    /** Packages to include (if null, include all) */
    private var stubPackages: PackageFilter? = null

    /** Packages to import (if empty, include all) */
    private var stubImportPackages: Set<String> = mutableStubImportPackages

    /** Packages to exclude/hide */
    var hidePackages: List<String> = mutableHidePackages

    /** Packages that we should skip generating even if not hidden; typically only used by tests */
    val skipEmitPackages
        get() = executionEnvironment.testEnvironment?.skipEmitPackages ?: emptyList()

    /** Annotations to hide */
    val hideAnnotations by lazy(hideAnnotationsBuilder::build)

    /** Annotations to revert */
    val revertAnnotations by lazy(revertAnnotationsBuilder::build)

    val annotationManager: AnnotationManager by lazy {
        DefaultAnnotationManager(
            DefaultAnnotationManager.Config(
                passThroughAnnotations = passThroughAnnotations,
                allShowAnnotations = allShowAnnotations,
                showAnnotations = showAnnotations,
                showSingleAnnotations = showSingleAnnotations,
                showForStubPurposesAnnotations = showForStubPurposesAnnotations,
                hideAnnotations = hideAnnotations,
                revertAnnotations = revertAnnotations,
                suppressCompatibilityMetaAnnotations = suppressCompatibilityMetaAnnotations,
                excludeAnnotations = excludeAnnotations,
                typedefMode = typedefMode,
                apiPredicate = ApiPredicate(config = apiPredicateConfig),
                previouslyReleasedCodebasesProvider = {
                    compatibilityCheckOptions.previouslyReleasedCodebases(signatureFileCache)
                },
            )
        )
    }

    internal val signatureFileCache by lazy { SignatureFileCache(annotationManager) }

    /** Meta-annotations for which annotated APIs should not be checked for compatibility. */
    private val suppressCompatibilityMetaAnnotations by
        option(
                ARG_SUPPRESS_COMPATIBILITY_META_ANNOTATION,
                help =
                    """
                       Suppress compatibility checks for any elements within the scope of an
                       annotation which is itself annotated with the given meta-annotation.
                    """
                        .trimIndent(),
                metavar = "<meta-annotation class>",
            )
            .multiple()
            .unique()

    /**
     * Whether the generated API can contain classes that are not present in the source but are
     * present on the classpath. Defaults to true for backwards compatibility but is set to false if
     * any API signatures are imported as they must provide a complete set of all classes required
     * but not provided by the generated API.
     *
     * Once all APIs are either self-contained or imported all the required references this will be
     * removed and no classes will be allowed from the classpath JARs.
     */
    private var allowClassesFromClasspath = true

    /** The configuration options for the [ApiVisitor] class. */
    val apiVisitorConfig by lazy {
        ApiVisitor.Config(
            packageFilter = stubPackages,
            apiPredicateConfig = apiPredicateConfig,
        )
    }

    /** The configuration options for the [ApiAnalyzer] class. */
    val apiAnalyzerConfig by lazy {
        ApiAnalyzer.Config(
            manifest = manifest,
            hidePackages = hidePackages,
            skipEmitPackages = skipEmitPackages,
            mergeQualifierAnnotations = mergeQualifierAnnotations,
            mergeInclusionAnnotations = mergeInclusionAnnotations,
            stubImportPackages = stubImportPackages,
            allShowAnnotations = allShowAnnotations,
            apiPredicateConfig = apiPredicateConfig,
        )
    }

    val apiPredicateConfig by lazy {
        ApiPredicate.Config(
            ignoreShown = showUnannotated,
            allowClassesFromClasspath = allowClassesFromClasspath,
            addAdditionalOverrides = signatureFileFormat.addAdditionalOverrides,
        )
    }

    /** This is set directly by [preprocessArgv]. */
    private var verbosity: Verbosity = Verbosity.NORMAL

    /** Whether to report warnings and other diagnostics along the way */
    val quiet: Boolean
        get() = verbosity.quiet

    /**
     * Whether to report extra diagnostics along the way (note that verbose isn't the same as not
     * quiet)
     */
    val verbose: Boolean
        get() = verbosity.verbose

    internal val stubWriterConfig by lazy {
        StubWriterConfig(
            apiVisitorConfig = apiVisitorConfig,
            kotlinStubs = kotlinStubs,
            includeDocumentationInStubs = includeDocumentationInStubs,
        )
    }

    val stubsDir by stubGenerationOptions::stubsDir
    val forceConvertToWarningNullabilityAnnotations by
        stubGenerationOptions::forceConvertToWarningNullabilityAnnotations
    val generateAnnotations by stubGenerationOptions::includeAnnotations

    /**
     * If set, a directory to write documentation stub files to. Corresponds to the --stubs/-stubs
     * flag.
     */
    var docStubsDir: File? = null

    /** Whether code compiled from Kotlin should be emitted as .kt stubs instead of .java stubs */
    var kotlinStubs = false

    /** Proguard Keep list file to write */
    var proguard: File? = null

    val apiFile by signatureFileOptions::apiFile
    val removedApiFile by signatureFileOptions::removedApiFile
    val signatureFileFormat by signatureFormatOptions::fileFormat

    /** Like [apiFile], but with JDiff xml format. */
    var apiXmlFile: File? = null

    /** If set, a file to write the DEX signatures to. Corresponds to [ARG_DEX_API]. */
    var dexApiFile: File? = null

    /** Path to directory to write SDK values to */
    var sdkValueDir: File? = null

    /**
     * If set, a file to write extracted annotations to. Corresponds to the --extract-annotations
     * flag.
     */
    var externalAnnotations: File? = null

    /** An optional manifest [File]. */
    private val manifestFile by
        option(
                ARG_MANIFEST,
                help =
                    """
        A manifest file, used to check permissions to cross check APIs and retrieve min_sdk_version.
        (default: no manifest)
                    """
                        .trimIndent()
            )
            .file(mustExist = true, canBeDir = false, mustBeReadable = true)

    /**
     * A [Manifest] object to look up available permissions and min_sdk_version.
     *
     * Created lazily to make sure that the [reporter] has been initialized.
     */
    val manifest by lazy { manifestFile?.let { Manifest(it, reporter) } ?: emptyManifest }

    /** Whether output should be colorized */
    val terminal by commonOptions::terminal

    /** The set of annotation classes that should be passed through unchanged */
    private var passThroughAnnotations = mutablePassThroughAnnotations

    /** The set of annotation classes that should be removed from all outputs */
    private var excludeAnnotations = mutableExcludeAnnotations

    /** A signature file to migrate nullness data from */
    var migrateNullsFrom: File? = null

    /** The list of compatibility checks to run */
    val compatibilityChecks: List<CheckRequest> by compatibilityCheckOptions::compatibilityChecks

    /** The API to use a base for the otherwise checked API during compat checks. */
    val baseApiForCompatCheck by compatibilityCheckOptions::baseApiForCompatCheck

    /** Existing external annotation files to merge in */
    private var mergeQualifierAnnotations: List<File> = mutableMergeQualifierAnnotations
    private var mergeInclusionAnnotations: List<File> = mutableMergeInclusionAnnotations

    /**
     * An optional <b>jar</b> file to load classes from instead of from source. This is similar to
     * the [classpath] attribute except we're explicitly saying that this is the complete set of
     * classes and that we <b>should</b> generate signatures/stubs from them or use them to diff
     * APIs with (whereas [classpath] is only used to resolve types.)
     */
    var apiJar: File? = null

    /** mapping from API level to android.jar files, if computing API levels */
    var apiLevelJars: Array<File>? = null

    /** The api level of the codebase, or -1 if not known/specified */
    var currentApiLevel = -1

    /**
     * The first api level of the codebase; typically 1 but can be higher for example for the System
     * API.
     */
    var firstApiLevel = 1

    /**
     * The codename of the codebase: non-null string if this is a developer preview build, null if
     * this is a release build.
     */
    var currentCodeName: String? = null

    /** API level XML file to generate */
    var generateApiLevelXml: File? = null

    /** Whether references to missing classes should be removed from the api levels file. */
    var removeMissingClassesInApiLevels: Boolean = false

    /** Reads API XML file to apply into documentation */
    var applyApiLevelsXml: File? = null

    /** Directory of prebuilt extension SDK jars that contribute to the API */
    var sdkJarRoot: File? = null

    /**
     * Rules to filter out some extension SDK APIs from the API, and assign extensions to the APIs
     * that are kept
     */
    var sdkInfoFile: File? = null

    /**
     * The latest publicly released SDK extension version. When generating docs for d.android.com,
     * the SDK extensions that have been finalized but not yet publicly released should be excluded
     * from the docs.
     *
     * If null, the docs will include all SDK extensions.
     */
    val latestReleasedSdkExtension by
        option(
                "--hide-sdk-extensions-newer-than",
                help =
                    "Ignore SDK extensions version INT and above. Used to exclude finalized but not yet released SDK extensions."
            )
            .int()

    /** API version history JSON file to generate */
    var generateApiVersionsJson: File? = null

    /** Ordered list of signatures for each past API version, if generating an API version JSON */
    var apiVersionSignatureFiles: List<File>? = null

    /**
     * The names of the API versions in [apiVersionSignatureFiles], in the same order, and the name
     * of the current API version
     */
    var apiVersionNames: List<String>? = null

    /** Whether to include the signature file format version header in removed signature files */
    val includeSignatureFormatVersionRemoved: EmitFileHeader
        get() =
            if (deleteEmptyRemovedSignatures) {
                EmitFileHeader.IF_NONEMPTY_FILE
            } else {
                EmitFileHeader.ALWAYS
            }

    var allBaselines: List<Baseline> = emptyList()

    /**
     * If set, metalava will show this error message when "API lint" (i.e. [ARG_API_LINT]) fails.
     */
    private var errorMessageApiLint: String = DefaultLintErrorMessage

    /** [IssueConfiguration] used by all reporters. */
    val issueConfiguration by issueReportingOptions::issueConfiguration

    /** [Reporter] for general use. */
    lateinit var reporter: Reporter

    /**
     * [Reporter] for "api-lint".
     *
     * Initialized in [parse].
     */
    lateinit var reporterApiLint: Reporter

    /**
     * [Reporter] for "check-compatibility:*:released". (i.e. [ARG_CHECK_COMPATIBILITY_API_RELEASED]
     * and [ARG_CHECK_COMPATIBILITY_REMOVED_RELEASED]).
     *
     * Initialized in [parse].
     */
    lateinit var reporterCompatibilityReleased: Reporter

    internal var allReporters: List<DefaultReporter> = emptyList()

    /** If updating baselines, don't fail the build */
    var passBaselineUpdates = false

    /** If updating baselines and the baseline is empty, delete the file */
    private var deleteEmptyBaselines = false

    /** If generating a removed signature file, and it is empty, delete it */
    var deleteEmptyRemovedSignatures = false

    /** Whether the baseline should only contain errors */
    private var baselineErrorsOnly = false

    /** Writes a list of all errors, even if they were suppressed in baseline or via annotation. */
    private var reportEvenIfSuppressed: File? = null
    var reportEvenIfSuppressedWriter: PrintWriter? = null

    /** The language level to use for Java files, set with [ARG_JAVA_SOURCE] */
    var javaLanguageLevelAsString: String = DEFAULT_JAVA_LANGUAGE_LEVEL

    /** The language level to use for Kotlin files, set with [ARG_KOTLIN_SOURCE] */
    var kotlinLanguageLevelAsString: String = DEFAULT_KOTLIN_LANGUAGE_LEVEL

    /**
     * The JDK to use as a platform, if set with [ARG_JDK_HOME]. This is only set when metalava is
     * used for non-Android projects.
     */
    var jdkHome: File? = null

    /**
     * The JDK to use as a platform, if set with [ARG_SDK_HOME]. If this is set along with
     * [ARG_COMPILE_SDK_VERSION], metalava will automatically add the platform's android.jar file to
     * the classpath if it does not already find the android.jar file in the classpath.
     */
    private var sdkHome: File? = null

    /**
     * The compileSdkVersion, set by [ARG_COMPILE_SDK_VERSION]. For example, for R it would be "29".
     * For R preview, it would be "R".
     */
    private var compileSdkVersion: String? = null

    /**
     * How to handle typedef annotations in signature files; corresponds to
     * $ARG_TYPEDEFS_IN_SIGNATURES
     */
    private val typedefMode by
        enumOption(
            ARG_TYPEDEFS_IN_SIGNATURES,
            help = """Whether to include typedef annotations in signature files.""",
            enumValueHelpGetter = { it.help },
            default = TypedefMode.NONE,
            key = { it.optionValue },
        )

    /** Temporary folder to use instead of the JDK default, if any */
    private var tempFolder: File? = null

    var useK2Uast: Boolean? = null

    val sourceModelProvider by
        option(
                ARG_SOURCE_MODEL_PROVIDER,
                hidden = true,
            )
            .choice("psi", "turbine")
            .default("psi")
            .deprecated(
                """WARNING: The turbine model is under work and not usable for now. Eventually this option can be used to set the source model provider to either turbine or psi. The default is psi. """
                    .trimIndent()
            )

    val encoding by
        option("-encoding", hidden = true)
            .deprecated(
                "WARNING: option `-encoding` is deprecated; it has no effect please remove",
                tagValue = "please remove"
            )

    fun parse(
        executionEnvironment: ExecutionEnvironment,
        args: Array<String>,
    ) {
        this.executionEnvironment = executionEnvironment

        var androidJarPatterns: MutableList<String>? = null
        var currentJar: File? = null

        val baselineBuilder = Baseline.Builder().apply { description = "base" }
        val baselineApiLintBuilder = Baseline.Builder().apply { description = "api-lint" }
        val baselineCompatibilityReleasedBuilder =
            Baseline.Builder().apply { description = "compatibility:released" }

        fun getBaselineBuilderForArg(flag: String): Baseline.Builder =
            when (flag) {
                ARG_BASELINE,
                ARG_UPDATE_BASELINE -> baselineBuilder
                ARG_BASELINE_API_LINT,
                ARG_UPDATE_BASELINE_API_LINT -> baselineApiLintBuilder
                ARG_BASELINE_CHECK_COMPATIBILITY_RELEASED,
                ARG_UPDATE_BASELINE_CHECK_COMPATIBILITY_RELEASED ->
                    baselineCompatibilityReleasedBuilder
                else -> error("Internal error: Invalid flag: $flag")
            }

        fun getSourcePath(path: String, arg: String, sourcePathToStore: MutableList<File>) {
            if (path.isBlank()) {
                // Don't compute absolute path; we want to skip this file later on.
                // For current directory one should use ".", not "".
                sourcePathToStore.add(File(""))
            } else {
                if (path.endsWith(SdkConstants.DOT_JAVA)) {
                    throw MetalavaCliException(
                        "$arg should point to a source root directory, not a source file ($path)"
                    )
                }
                sourcePathToStore.addAll(stringToExistingDirsOrJars(path))
            }
        }

        var index = 0
        while (index < args.size) {
            when (val arg = args[index]) {
                // For now, we don't distinguish between bootclasspath and classpath
                ARG_CLASS_PATH -> {
                    val path = getValue(args, ++index)
                    mutableClassPath.addAll(stringToExistingDirsOrJars(path))
                }
                ARG_COMMON_SOURCE_PATH -> {
                    val path = getValue(args, ++index)
                    getSourcePath(path, arg, mutableCommonSourcePath)
                }
                ARG_SOURCE_PATH,
                "--sources",
                "--sourcepath" -> {
                    val path = getValue(args, ++index)
                    getSourcePath(path, arg, mutableSourcePath)
                }
                ARG_SOURCE_FILES -> {
                    val listString = getValue(args, ++index)
                    listString.split(",").forEach { path ->
                        mutableSources.addAll(stringToExistingFiles(path))
                    }
                }
                ARG_SUBTRACT_API -> {
                    if (subtractApi != null) {
                        throw MetalavaCliException(
                            stderr = "Only one $ARG_SUBTRACT_API can be supplied"
                        )
                    }
                    subtractApi = stringToExistingFile(getValue(args, ++index))
                }

                // TODO: Remove the legacy --merge-annotations flag once it's no longer used to
                // update P docs
                ARG_MERGE_QUALIFIER_ANNOTATIONS,
                "--merge-zips",
                "--merge-annotations" ->
                    mutableMergeQualifierAnnotations.addAll(
                        stringToExistingDirsOrFiles(getValue(args, ++index))
                    )
                ARG_MERGE_INCLUSION_ANNOTATIONS ->
                    mutableMergeInclusionAnnotations.addAll(
                        stringToExistingDirsOrFiles(getValue(args, ++index))
                    )
                ARG_VALIDATE_NULLABILITY_FROM_MERGED_STUBS -> {
                    validateNullabilityFromMergedStubs = true
                }
                ARG_VALIDATE_NULLABILITY_FROM_LIST -> {
                    validateNullabilityFromList = stringToExistingFile(getValue(args, ++index))
                }
                ARG_NULLABILITY_WARNINGS_TXT ->
                    nullabilityWarningsTxt = stringToNewFile(getValue(args, ++index))
                ARG_NULLABILITY_ERRORS_NON_FATAL -> nullabilityErrorsFatal = false
                ARG_SDK_VALUES -> sdkValueDir = stringToNewDir(getValue(args, ++index))
                ARG_XML_API -> apiXmlFile = stringToNewFile(getValue(args, ++index))
                ARG_DEX_API -> dexApiFile = stringToNewFile(getValue(args, ++index))
                ARG_SHOW_ANNOTATION -> {
                    val annotation = getValue(args, ++index)
                    showAnnotationsBuilder.add(annotation)
                    // These should also be counted as allShowAnnotations
                    allShowAnnotationsBuilder.add(annotation)
                }
                ARG_SHOW_SINGLE_ANNOTATION -> {
                    val annotation = getValue(args, ++index)
                    showSingleAnnotationsBuilder.add(annotation)
                    // These should also be counted as allShowAnnotations
                    allShowAnnotationsBuilder.add(annotation)
                }
                ARG_SHOW_FOR_STUB_PURPOSES_ANNOTATION -> {
                    val annotation = getValue(args, ++index)
                    showForStubPurposesAnnotationBuilder.add(annotation)
                    // These should also be counted as allShowAnnotations
                    allShowAnnotationsBuilder.add(annotation)
                }
                ARG_SHOW_UNANNOTATED -> showUnannotated = true
                ARG_HIDE_ANNOTATION -> hideAnnotationsBuilder.add(getValue(args, ++index))
                ARG_REVERT_ANNOTATION -> revertAnnotationsBuilder.add(getValue(args, ++index))
                ARG_DOC_STUBS -> docStubsDir = stringToNewDir(getValue(args, ++index))
                ARG_KOTLIN_STUBS -> kotlinStubs = true
                ARG_EXCLUDE_DOCUMENTATION_FROM_STUBS -> includeDocumentationInStubs = false
                ARG_ENHANCE_DOCUMENTATION -> enhanceDocumentation = true
                ARG_PASS_THROUGH_ANNOTATION -> {
                    val annotations = getValue(args, ++index)
                    annotations.split(",").forEach { path ->
                        mutablePassThroughAnnotations.add(path)
                    }
                }
                ARG_EXCLUDE_ANNOTATION -> {
                    val annotations = getValue(args, ++index)
                    annotations.split(",").forEach { path -> mutableExcludeAnnotations.add(path) }
                }
                ARG_PROGUARD -> proguard = stringToNewFile(getValue(args, ++index))
                ARG_HIDE_PACKAGE -> mutableHidePackages.add(getValue(args, ++index))
                ARG_STUB_PACKAGES,
                "-stubpackages" -> {
                    val packages = getValue(args, ++index)
                    val filter =
                        stubPackages
                            ?: run {
                                val newFilter = PackageFilter()
                                stubPackages = newFilter
                                newFilter
                            }
                    filter.addPackages(packages)
                }
                ARG_STUB_IMPORT_PACKAGES -> {
                    val packages = getValue(args, ++index)
                    for (pkg in packages.split(File.pathSeparatorChar)) {
                        mutableStubImportPackages.add(pkg)
                        mutableHidePackages.add(pkg)
                    }
                }
                ARG_IGNORE_CLASSES_ON_CLASSPATH -> {
                    allowClassesFromClasspath = false
                }
                ARG_BASELINE,
                ARG_BASELINE_API_LINT,
                ARG_BASELINE_CHECK_COMPATIBILITY_RELEASED -> {
                    val nextArg = getValue(args, ++index)
                    val builder = getBaselineBuilderForArg(arg)
                    builder.file = stringToExistingFile(nextArg)
                }
                ARG_REPORT_EVEN_IF_SUPPRESSED -> {
                    val relative = getValue(args, ++index)
                    if (reportEvenIfSuppressed != null) {
                        throw MetalavaCliException(
                            "Only one $ARG_REPORT_EVEN_IF_SUPPRESSED is allowed; found both $reportEvenIfSuppressed and $relative"
                        )
                    }
                    reportEvenIfSuppressed = stringToNewOrExistingFile(relative)
                    reportEvenIfSuppressedWriter = reportEvenIfSuppressed?.printWriter()
                }
                ARG_UPDATE_BASELINE,
                ARG_UPDATE_BASELINE_API_LINT,
                ARG_UPDATE_BASELINE_CHECK_COMPATIBILITY_RELEASED -> {
                    val builder = getBaselineBuilderForArg(arg)
                    if (index < args.size - 1) {
                        val nextArg = args[index + 1]
                        if (!nextArg.startsWith("-")) {
                            index++
                            builder.updateFile = stringToNewOrExistingFile(nextArg)
                        }
                    }
                }
                ARG_ERROR_MESSAGE_API_LINT -> errorMessageApiLint = getValue(args, ++index)
                ARG_PASS_BASELINE_UPDATES -> passBaselineUpdates = true
                ARG_DELETE_EMPTY_BASELINES -> deleteEmptyBaselines = true
                ARG_DELETE_EMPTY_REMOVED_SIGNATURES -> deleteEmptyRemovedSignatures = true
                ARG_INPUT_API_JAR -> apiJar = stringToExistingFile(getValue(args, ++index))
                ARG_EXTRACT_ANNOTATIONS ->
                    externalAnnotations = stringToNewFile(getValue(args, ++index))
                ARG_MIGRATE_NULLNESS -> {
                    migrateNullsFrom = stringToExistingFile(getValue(args, ++index))
                }
                ARG_WARNINGS_AS_ERRORS -> warningsAreErrors = true
                ARG_LINTS_AS_ERRORS -> lintsAreErrors = true
                ARG_API_LINT -> {
                    checkApi = true
                    if (index < args.size - 1) {
                        val nextArg = args[index + 1]
                        if (!nextArg.startsWith("-")) {
                            val file = stringToExistingFile(nextArg)
                            if (file.isFile) {
                                index++
                                checkApiBaselineApiFile = file
                            }
                        }
                    }
                }

                // Extracting API levels
                ARG_ANDROID_JAR_PATTERN -> {
                    val list =
                        androidJarPatterns
                            ?: run {
                                val list = arrayListOf<String>()
                                androidJarPatterns = list
                                list
                            }
                    list.add(getValue(args, ++index))
                }
                ARG_CURRENT_VERSION -> {
                    currentApiLevel = Integer.parseInt(getValue(args, ++index))
                    if (currentApiLevel <= 26) {
                        throw MetalavaCliException(
                            "Suspicious currentApi=$currentApiLevel, expected at least 27"
                        )
                    }
                }
                ARG_FIRST_VERSION -> {
                    firstApiLevel = Integer.parseInt(getValue(args, ++index))
                }
                ARG_CURRENT_CODENAME -> {
                    val codeName = getValue(args, ++index)
                    if (codeName != "REL") {
                        currentCodeName = codeName
                    }
                }
                ARG_CURRENT_JAR -> {
                    currentJar = stringToExistingFile(getValue(args, ++index))
                }
                ARG_GENERATE_API_LEVELS -> {
                    generateApiLevelXml = stringToNewFile(getValue(args, ++index))
                }
                ARG_APPLY_API_LEVELS -> {
                    applyApiLevelsXml =
                        if (args.contains(ARG_GENERATE_API_LEVELS)) {
                            // If generating the API file at the same time, it doesn't have
                            // to already exist
                            stringToNewFile(getValue(args, ++index))
                        } else {
                            stringToExistingFile(getValue(args, ++index))
                        }
                }
                ARG_REMOVE_MISSING_CLASS_REFERENCES_IN_API_LEVELS ->
                    removeMissingClassesInApiLevels = true
                ARG_GENERATE_API_VERSION_HISTORY -> {
                    generateApiVersionsJson = stringToNewFile(getValue(args, ++index))
                }
                ARG_API_VERSION_SIGNATURE_FILES -> {
                    apiVersionSignatureFiles = stringToExistingFiles(getValue(args, ++index))
                }
                ARG_API_VERSION_NAMES -> {
                    apiVersionNames = getValue(args, ++index).split(' ')
                }
                ARG_JAVA_SOURCE,
                "-source" -> {
                    val value = getValue(args, ++index)
                    javaLanguageLevelAsString = value
                }
                ARG_KOTLIN_SOURCE -> {
                    val value = getValue(args, ++index)
                    kotlinLanguageLevelAsString = value
                }
                ARG_JDK_HOME -> {
                    jdkHome = stringToExistingDir(getValue(args, ++index))
                }
                ARG_SDK_HOME -> {
                    sdkHome = stringToExistingDir(getValue(args, ++index))
                }
                ARG_COMPILE_SDK_VERSION -> {
                    compileSdkVersion = getValue(args, ++index)
                }
                ARG_USE_K2_UAST -> useK2Uast = true
                ARG_SDK_JAR_ROOT -> {
                    sdkJarRoot = stringToExistingDir(getValue(args, ++index))
                }
                ARG_SDK_INFO_FILE -> {
                    sdkInfoFile = stringToExistingFile(getValue(args, ++index))
                }
                "--temp-folder" -> {
                    tempFolder = stringToNewOrExistingDir(getValue(args, ++index))
                }

                // Option only meant for tests (not documented); doesn't work in all cases (to do
                // that we'd
                // need JNA to call libc)
                "--pwd" -> {
                    val pwd = stringToExistingDir(getValue(args, ++index)).absoluteFile
                    System.setProperty("user.dir", pwd.path)
                }
                else -> {
                    if (arg.startsWith("-")) {
                        // Some other argument: display usage info and exit
                        throw NoSuchOption(givenName = arg)
                    } else {
                        // All args that don't start with "-" are taken to be filenames
                        mutableSources.addAll(stringToExistingFiles(arg))
                    }
                }
            }

            ++index
        }

        if (generateApiLevelXml != null) {
            if (currentApiLevel == -1) {
                throw MetalavaCliException(
                    stderr = "$ARG_GENERATE_API_LEVELS requires $ARG_CURRENT_VERSION"
                )
            }

            // <String> is redundant here but while IDE (with newer type inference engine
            // understands that) the current 1.3.x compiler does not
            @Suppress("RemoveExplicitTypeArguments")
            val patterns = androidJarPatterns ?: run { mutableListOf<String>() }
            // Fallbacks
            patterns.add("prebuilts/tools/common/api-versions/android-%/android.jar")
            patterns.add("prebuilts/sdk/%/public/android.jar")
            apiLevelJars =
                findAndroidJars(
                    args,
                    patterns,
                    firstApiLevel,
                    currentApiLevel + if (isDeveloperPreviewBuild()) 1 else 0,
                    currentJar
                )
        }

        if ((sdkJarRoot == null) != (sdkInfoFile == null)) {
            throw MetalavaCliException(
                stderr = "$ARG_SDK_JAR_ROOT and $ARG_SDK_INFO_FILE must both be supplied"
            )
        }

        // apiVersionNames will include the current version but apiVersionSignatureFiles will not,
        // so there should be 1 more name than signature file (or both can be null)
        val numVersionNames = apiVersionNames?.size ?: 0
        val numVersionFiles = apiVersionSignatureFiles?.size ?: 0
        if (numVersionNames != 0 && numVersionNames != numVersionFiles + 1) {
            throw MetalavaCliException(
                "$ARG_API_VERSION_NAMES must have one more version than $ARG_API_VERSION_SIGNATURE_FILES to include the current version name"
            )
        }

        // If the caller has not explicitly requested that unannotated classes and
        // members should be shown in the output then only show them if no annotations were
        // provided.
        if (!showUnannotated && allShowAnnotations.isEmpty()) {
            showUnannotated = true
        }

        // Fix up [Baseline] files and [Reporter]s.

        val baselineHeaderComment =
            if (isBuildingAndroid())
                "// See tools/metalava/API-LINT.md for how to update this file.\n\n"
            else ""
        baselineBuilder.headerComment = baselineHeaderComment
        baselineApiLintBuilder.headerComment = baselineHeaderComment
        baselineCompatibilityReleasedBuilder.headerComment = baselineHeaderComment

        if (baselineBuilder.file == null) {
            // If default baseline is a file, use it.
            val defaultBaselineFile = getDefaultBaselineFile()
            if (defaultBaselineFile != null && defaultBaselineFile.isFile) {
                baselineBuilder.file = defaultBaselineFile
            }
        }

        val baselineConfig =
            Baseline.Config(
                issueConfiguration = issueConfiguration,
                baselineErrorsOnly = baselineErrorsOnly,
                deleteEmptyBaselines = deleteEmptyBaselines,
                sourcePath = sourcePath,
            )
        // A baseline to check against
        val baseline = baselineBuilder.build(baselineConfig)

        // A baseline to check against, specifically used for "API lint" (i.e. [ARG_API_LINT])
        val baselineApiLint = baselineApiLintBuilder.build(baselineConfig)

        // A baseline to check against, specifically used for "check-compatibility:*:released" (i.e.
        // [ARG_CHECK_COMPATIBILITY_API_RELEASED] and [ARG_CHECK_COMPATIBILITY_REMOVED_RELEASED])
        val baselineCompatibilityReleased =
            baselineCompatibilityReleasedBuilder.build(baselineConfig)

        // Initialize the reporters.
        reporter =
            DefaultReporter(
                environment = executionEnvironment.reporterEnvironment,
                issueConfiguration = issueConfiguration,
                baseline = baseline,
                packageFilter = stubPackages,
            )
        reporterApiLint =
            DefaultReporter(
                environment = executionEnvironment.reporterEnvironment,
                issueConfiguration = issueConfiguration,
                baseline = baselineApiLint ?: baseline,
                errorMessage = errorMessageApiLint,
                packageFilter = stubPackages,
            )
        reporterCompatibilityReleased =
            DefaultReporter(
                environment = executionEnvironment.reporterEnvironment,
                issueConfiguration = issueConfiguration,
                baseline = baselineCompatibilityReleased ?: baseline,
                errorMessage = compatibilityCheckOptions.errorMessage,
                packageFilter = stubPackages,
            )

        // Build "all baselines" and "all reporters"

        // Baselines are nullable, so selectively add to the list.
        allBaselines = listOfNotNull(baseline, baselineApiLint, baselineCompatibilityReleased)

        // Reporters are non-null.
        // Downcast to DefaultReporter to gain access to some implementation specific functionality.
        allReporters =
            listOf(
                    issueReportingOptions.bootstrapReporter,
                    reporter,
                    reporterApiLint,
                    reporterCompatibilityReleased,
                )
                .map { it as DefaultReporter }

        updateClassPath()
        checkFlagConsistency()
    }

    fun isDeveloperPreviewBuild(): Boolean = currentCodeName != null

    /** Update the classpath to insert android.jar or JDK classpath elements if necessary */
    private fun updateClassPath() {
        val sdkHome = sdkHome
        val jdkHome = jdkHome

        if (
            sdkHome != null &&
                compileSdkVersion != null &&
                classpath.none { it.name == FN_FRAMEWORK_LIBRARY }
        ) {
            val jar = File(sdkHome, "platforms/android-$compileSdkVersion")
            if (jar.isFile) {
                mutableClassPath.add(jar)
            } else {
                throw MetalavaCliException(
                    stderr =
                        "Could not find android.jar for API level " +
                            "$compileSdkVersion in SDK $sdkHome: $jar does not exist"
                )
            }
            if (jdkHome != null) {
                throw MetalavaCliException(
                    stderr = "Do not specify both $ARG_SDK_HOME and $ARG_JDK_HOME"
                )
            }
        } else if (jdkHome != null) {
            val isJre = !isJdkFolder(jdkHome)
            val roots = JavaSdkUtil.getJdkClassesRoots(jdkHome.toPath(), isJre).map { it.toFile() }
            mutableClassPath.addAll(roots)
        }
    }

    /**
     * Produce a default file name for the baseline. It's normally "baseline.txt", but can be
     * prefixed by show annotations; e.g. @TestApi -> test-baseline.txt, @SystemApi ->
     * system-baseline.txt, etc.
     *
     * Note because the default baseline file is not explicitly set in the command line, this file
     * would trigger a --strict-input-files violation. To avoid that, always explicitly pass a
     * baseline file.
     */
    private fun getDefaultBaselineFile(): File? {
        if (sourcePath.isNotEmpty() && sourcePath[0].path.isNotBlank()) {
            fun annotationToPrefix(qualifiedName: String): String {
                val name = qualifiedName.substring(qualifiedName.lastIndexOf('.') + 1)
                return name.lowercase(Locale.US).removeSuffix("api") + "-"
            }
            val sb = StringBuilder()
            allShowAnnotations.getIncludedAnnotationNames().forEach {
                sb.append(annotationToPrefix(it))
            }
            sb.append(DEFAULT_BASELINE_NAME)
            var base = sourcePath[0]
            // Convention: in AOSP, signature files are often in sourcepath/api: let's place
            // baseline
            // files there too
            val api = File(base, "api")
            if (api.isDirectory) {
                base = api
            }
            return File(base, sb.toString())
        } else {
            return null
        }
    }

    /**
     * Find an android stub jar that matches the given criteria.
     *
     * Note because the default baseline file is not explicitly set in the command line, this file
     * would trigger a --strict-input-files violation. To avoid that, use
     * --strict-input-files-exempt to exempt the jar directory.
     */
    private fun findAndroidJars(
        args: Array<String>,
        androidJarPatterns: List<String>,
        minApi: Int,
        currentApiLevel: Int,
        currentJar: File?
    ): Array<File> {
        val apiLevelFiles = mutableListOf<File>()
        // api level 0: placeholder, should not be processed.
        // (This is here because we want the array index to match
        // the API level)
        val element = File("not an api: the starting API index is $minApi")
        for (i in 0 until minApi) {
            apiLevelFiles.add(element)
        }

        // Get all the android.jar. They are in platforms-#
        var apiLevel = minApi - 1
        while (true) {
            apiLevel++
            try {
                var jar: File? = null
                if (apiLevel == currentApiLevel) {
                    jar = currentJar
                }
                if (jar == null) {
                    jar = getAndroidJarFile(apiLevel, androidJarPatterns)
                }
                if (jar == null || !jar.isFile) {
                    if (verbose) {
                        stdout.println("Last API level found: ${apiLevel - 1}")
                    }

                    if (apiLevel < 28) {
                        // Clearly something is wrong with the patterns; this should result in a
                        // build error
                        val argList = mutableListOf<String>()
                        args.forEachIndexed { index, arg ->
                            if (arg == ARG_ANDROID_JAR_PATTERN) {
                                argList.add(args[index + 1])
                            }
                        }
                        throw MetalavaCliException(
                            stderr =
                                "Could not find android.jar for API level $apiLevel; the " +
                                    "$ARG_ANDROID_JAR_PATTERN set might be invalid: ${argList.joinToString()}"
                        )
                    }

                    break
                }
                if (verbose) {
                    stdout.println("Found API $apiLevel at ${jar.path}")
                }
                apiLevelFiles.add(jar)
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }

        return apiLevelFiles.toTypedArray()
    }

    private fun getAndroidJarFile(apiLevel: Int, patterns: List<String>): File? {
        return patterns
            .map { fileForPathInner(it.replace("%", apiLevel.toString())) }
            .firstOrNull { it.isFile }
    }

    /** Makes sure that the flag combinations make sense */
    private fun checkFlagConsistency() {
        if (apiJar != null && sources.isNotEmpty()) {
            throw MetalavaCliException(
                stderr = "Specify either $ARG_SOURCE_FILES or $ARG_INPUT_API_JAR, not both"
            )
        }
    }

    private fun getValue(args: Array<String>, index: Int): String {
        if (index >= args.size) {
            throw MetalavaCliException("Missing argument for ${args[index - 1]}")
        }
        return args[index]
    }

    @Suppress("unused")
    private fun stringToExistingDirs(value: String): List<File> {
        val files = mutableListOf<File>()
        for (path in value.split(File.pathSeparatorChar)) {
            val file = fileForPathInner(path)
            if (!file.isDirectory) {
                throw MetalavaCliException("$file is not a directory")
            }
            files.add(file)
        }
        return files
    }

    private fun stringToExistingDirsOrJars(value: String): List<File> {
        val files = mutableListOf<File>()
        for (path in value.split(File.pathSeparatorChar)) {
            val file = fileForPathInner(path)
            if (!file.isDirectory && !(file.path.endsWith(SdkConstants.DOT_JAR) && file.isFile)) {
                throw MetalavaCliException("$file is not a jar or directory")
            }
            files.add(file)
        }
        return files
    }

    private fun stringToExistingDirsOrFiles(value: String): List<File> {
        val files = mutableListOf<File>()
        for (path in value.split(File.pathSeparatorChar)) {
            val file = fileForPathInner(path)
            if (!file.exists()) {
                throw MetalavaCliException("$file does not exist")
            }
            files.add(file)
        }
        return files
    }

    @Suppress("unused")
    private fun stringToExistingFileOrDir(value: String): File {
        val file = fileForPathInner(value)
        if (!file.exists()) {
            throw MetalavaCliException("$file is not a file or directory")
        }
        return file
    }

    private fun stringToExistingFiles(value: String): List<File> {
        return value
            .split(File.pathSeparatorChar)
            .map { fileForPathInner(it) }
            .map { file ->
                if (!file.isFile) {
                    throw MetalavaCliException("$file is not a file")
                }
                file
            }
    }

    private fun stringToNewOrExistingDir(value: String): File {
        val dir = fileForPathInner(value)
        if (!dir.isDirectory) {
            val ok = dir.mkdirs()
            if (!ok) {
                throw MetalavaCliException("Could not create $dir")
            }
        }
        return dir
    }

    private fun stringToNewOrExistingFile(value: String): File {
        val file = fileForPathInner(value)
        if (!file.exists()) {
            val parentFile = file.parentFile
            if (parentFile != null && !parentFile.isDirectory) {
                val ok = parentFile.mkdirs()
                if (!ok) {
                    throw MetalavaCliException("Could not create $parentFile")
                }
            }
        }
        return file
    }
}

object OptionsHelp {
    fun getUsage(terminal: Terminal, width: Int): String {
        val usage = StringWriter()
        val printWriter = PrintWriter(usage)
        usage(printWriter, terminal, width)
        return usage.toString()
    }

    private fun usage(out: PrintWriter, terminal: Terminal, width: Int) {
        val args =
            arrayOf(
                "",
                "API sources:",
                "$ARG_SOURCE_FILES <files>",
                "A comma separated list of source files to be parsed. Can also be " +
                    "@ followed by a path to a text file containing paths to the full set of files to parse.",
                "$ARG_SOURCE_PATH <paths>",
                "One or more directories (separated by `${File.pathSeparator}`) " +
                    "containing source files (within a package hierarchy).",
                "$ARG_COMMON_SOURCE_PATH <paths>",
                "One or more directories (separated by `${File.pathSeparator}`) " +
                    "containing common source files (within a package hierarchy) " +
                    "where platform-agnostic `expect` declarations as well as " +
                    "common business logic are defined.",
                "$ARG_CLASS_PATH <paths>",
                "One or more directories or jars (separated by " +
                    "`${File.pathSeparator}`) containing classes that should be on the classpath when parsing the " +
                    "source files",
                "$ARG_MERGE_QUALIFIER_ANNOTATIONS <file>",
                "An external annotations file to merge and overlay " +
                    "the sources, or a directory of such files. Should be used for annotations intended for " +
                    "inclusion in the API to be written out, e.g. nullability. Formats supported are: IntelliJ's " +
                    "external annotations database format, .jar or .zip files containing those, Android signature " +
                    "files, and Java stub files.",
                "$ARG_MERGE_INCLUSION_ANNOTATIONS <file>",
                "An external annotations file to merge and overlay " +
                    "the sources, or a directory of such files. Should be used for annotations which determine " +
                    "inclusion in the API to be written out, i.e. show and hide. The only format supported is " +
                    "Java stub files.",
                ARG_VALIDATE_NULLABILITY_FROM_MERGED_STUBS,
                "Triggers validation of nullability annotations " +
                    "for any class where $ARG_MERGE_QUALIFIER_ANNOTATIONS includes a Java stub file.",
                ARG_VALIDATE_NULLABILITY_FROM_LIST,
                "Triggers validation of nullability annotations " +
                    "for any class listed in the named file (one top-level class per line, # prefix for comment line).",
                "$ARG_NULLABILITY_WARNINGS_TXT <file>",
                "Specifies where to write warnings encountered during " +
                    "validation of nullability annotations. (Does not trigger validation by itself.)",
                ARG_NULLABILITY_ERRORS_NON_FATAL,
                "Specifies that errors encountered during validation of " +
                    "nullability annotations should not be treated as errors. They will be written out to the " +
                    "file specified in $ARG_NULLABILITY_WARNINGS_TXT instead.",
                "$ARG_INPUT_API_JAR <file>",
                "A .jar file to read APIs from directly",
                "$ARG_HIDE_PACKAGE <package>",
                "Remove the given packages from the API even if they have not been " +
                    "marked with @hide",
                "$ARG_SHOW_ANNOTATION <annotation class>",
                "Unhide any hidden elements that are also annotated " + "with the given annotation",
                "$ARG_SHOW_SINGLE_ANNOTATION <annotation>",
                "Like $ARG_SHOW_ANNOTATION, but does not apply " +
                    "to members; these must also be explicitly annotated",
                "$ARG_SHOW_FOR_STUB_PURPOSES_ANNOTATION <annotation class>",
                "Like $ARG_SHOW_ANNOTATION, but elements annotated " +
                    "with it are assumed to be \"implicitly\" included in the API surface, and they'll be included " +
                    "in certain kinds of output such as stubs, but not in others, such as the signature file and API lint",
                "$ARG_HIDE_ANNOTATION <annotation class>",
                "Treat any elements annotated with the given annotation " + "as hidden",
                ARG_SHOW_UNANNOTATED,
                "Include un-annotated public APIs in the signature file as well",
                "$ARG_JAVA_SOURCE <level>",
                "Sets the source level for Java source files; default is $DEFAULT_JAVA_LANGUAGE_LEVEL.",
                "$ARG_KOTLIN_SOURCE <level>",
                "Sets the source level for Kotlin source files; default is $DEFAULT_KOTLIN_LANGUAGE_LEVEL.",
                "$ARG_SDK_HOME <dir>",
                "If set, locate the `android.jar` file from the given Android SDK",
                "$ARG_COMPILE_SDK_VERSION <api>",
                "Use the given API level",
                "$ARG_JDK_HOME <dir>",
                "If set, add the Java APIs from the given JDK to the classpath",
                "$ARG_STUB_PACKAGES <package-list>",
                "List of packages (separated by ${File.pathSeparator}) which will " +
                    "be used to filter out irrelevant code. If specified, only code in these packages will be " +
                    "included in signature files, stubs, etc. (This is not limited to just the stubs; the name " +
                    "is historical.) You can also use \".*\" at the end to match subpackages, so `foo.*` will " +
                    "match both `foo` and `foo.bar`.",
                "$ARG_SUBTRACT_API <api file>",
                "Subtracts the API in the given signature or jar file from the " +
                    "current API being emitted via $ARG_API, $ARG_STUBS, $ARG_DOC_STUBS, etc. " +
                    "Note that the subtraction only applies to classes; it does not subtract members.",
                ARG_IGNORE_CLASSES_ON_CLASSPATH,
                "Prevents references to classes on the classpath from being added to " +
                    "the generated stub files.",
                "",
                "Extracting Signature Files:",
                // TODO: Document --show-annotation!
                "$ARG_DEX_API <file>",
                "Generate a DEX signature descriptor file listing the APIs",
                "$ARG_PROGUARD <file>",
                "Write a ProGuard keep file for the API",
                "$ARG_SDK_VALUES <dir>",
                "Write SDK values files to the given directory",
                "",
                "Generating Stubs:",
                "$ARG_DOC_STUBS <dir>",
                "Generate documentation stub source files for the API. Documentation stub " +
                    "files are similar to regular stub files, but there are some differences. For example, in " +
                    "the stub files, we'll use special annotations like @RecentlyNonNull instead of @NonNull to " +
                    "indicate that an element is recently marked as non null, whereas in the documentation stubs we'll " +
                    "just list this as @NonNull. Another difference is that @doconly elements are included in " +
                    "documentation stubs, but not regular stubs, etc.",
                ARG_KOTLIN_STUBS,
                "[CURRENTLY EXPERIMENTAL] If specified, stubs generated from Kotlin source code will " +
                    "be written in Kotlin rather than the Java programming language.",
                "$ARG_PASS_THROUGH_ANNOTATION <annotation classes>",
                "A comma separated list of fully qualified names of " +
                    "annotation classes that must be passed through unchanged.",
                "$ARG_EXCLUDE_ANNOTATION <annotation classes>",
                "A comma separated list of fully qualified names of " +
                    "annotation classes that must be stripped from metalava's outputs.",
                ARG_ENHANCE_DOCUMENTATION,
                "Enhance documentation in various ways, for example auto-generating documentation based on source " +
                    "annotations present in the code. This is implied by --doc-stubs.",
                ARG_EXCLUDE_DOCUMENTATION_FROM_STUBS,
                "Exclude element documentation (javadoc and kdoc) " +
                    "from the generated stubs. (Copyright notices are not affected by this, they are always included. " +
                    "Documentation stubs (--doc-stubs) are not affected.)",
                "",
                "Diffs and Checks:",
                "$ARG_API_LINT [api file]",
                "Check API for Android API best practices. If a signature file is " +
                    "provided, only the APIs that are new since the API will be checked.",
                "$ARG_MIGRATE_NULLNESS <api file>",
                "Compare nullness information with the previous stable API " +
                    "and mark newly annotated APIs as under migration.",
                ARG_WARNINGS_AS_ERRORS,
                "Promote all warnings to errors",
                ARG_LINTS_AS_ERRORS,
                "Promote all API lint warnings to errors",
                "$ARG_REPORT_EVEN_IF_SUPPRESSED <file>",
                "Write all issues into the given file, even if suppressed (via annotation or baseline) but not if hidden (by '$ARG_HIDE' or '$ARG_HIDE_CATEGORY')",
                "$ARG_BASELINE <file>",
                "Filter out any errors already reported in the given baseline file, or " +
                    "create if it does not already exist",
                "$ARG_UPDATE_BASELINE [file]",
                "Rewrite the existing baseline file with the current set of warnings. " +
                    "If some warnings have been fixed, this will delete them from the baseline files. If a file " +
                    "is provided, the updated baseline is written to the given file; otherwise the original source " +
                    "baseline file is updated.",
                "$ARG_BASELINE_API_LINT <file> $ARG_UPDATE_BASELINE_API_LINT [file]",
                "Same as $ARG_BASELINE and " +
                    "$ARG_UPDATE_BASELINE respectively, but used specifically for API lint issues performed by " +
                    "$ARG_API_LINT.",
                "$ARG_BASELINE_CHECK_COMPATIBILITY_RELEASED <file> $ARG_UPDATE_BASELINE_CHECK_COMPATIBILITY_RELEASED [file]",
                "Same as $ARG_BASELINE and " +
                    "$ARG_UPDATE_BASELINE respectively, but used specifically for API compatibility issues performed by " +
                    "$ARG_CHECK_COMPATIBILITY_API_RELEASED and $ARG_CHECK_COMPATIBILITY_REMOVED_RELEASED.",
                ARG_PASS_BASELINE_UPDATES,
                "Normally, encountering error will fail the build, even when updating " +
                    "baselines. This flag allows you to tell $PROGRAM_NAME to continue without errors, such that " +
                    "all the baselines in the source tree can be updated in one go.",
                ARG_DELETE_EMPTY_BASELINES,
                "Whether to delete baseline files if they are updated and there is nothing " +
                    "to include.",
                "$ARG_ERROR_MESSAGE_API_LINT <message>",
                "If set, $PROGRAM_NAME shows it when errors are detected in $ARG_API_LINT.",
                "",
                "JDiff:",
                "$ARG_XML_API <file>",
                "Like $ARG_API, but emits the API in the JDiff XML format instead",
                "",
                "Extracting Annotations:",
                "$ARG_EXTRACT_ANNOTATIONS <zipfile>",
                "Extracts source annotations from the source files and writes " +
                    "them into the given zip file",
                ARG_INCLUDE_SOURCE_RETENTION,
                "If true, include source-retention annotations in the stub files. Does " +
                    "not apply to signature files. Source retention annotations are extracted into the external " +
                    "annotations files instead.",
                "",
                "Injecting API Levels:",
                "$ARG_APPLY_API_LEVELS <api-versions.xml>",
                "Reads an XML file containing API level descriptions " +
                    "and merges the information into the documentation",
                "",
                "Extracting API Levels:",
                "$ARG_GENERATE_API_LEVELS <xmlfile>",
                "Reads android.jar SDK files and generates an XML file recording " +
                    "the API level for each class, method and field",
                ARG_REMOVE_MISSING_CLASS_REFERENCES_IN_API_LEVELS,
                "Removes references to missing classes when generating the API levels XML file. " +
                    "This can happen when generating the XML file for the non-updatable portions of " +
                    "the module-lib sdk, as those non-updatable portions can reference classes that are " +
                    "part of an updatable apex.",
                "$ARG_ANDROID_JAR_PATTERN <pattern>",
                "Patterns to use to locate Android JAR files. The default " +
                    "is \$ANDROID_HOME/platforms/android-%/android.jar.",
                ARG_FIRST_VERSION,
                "Sets the first API level to generate an API database from; usually 1",
                ARG_CURRENT_VERSION,
                "Sets the current API level of the current source code",
                ARG_CURRENT_CODENAME,
                "Sets the code name for the current source code",
                ARG_CURRENT_JAR,
                "Points to the current API jar, if any",
                ARG_SDK_JAR_ROOT,
                "Points to root of prebuilt extension SDK jars, if any. This directory is expected to " +
                    "contain snapshots of historical extension SDK versions in the form of stub jars. " +
                    "The paths should be on the format \"<int>/public/<module-name>.jar\", where <int> " +
                    "corresponds to the extension SDK version, and <module-name> to the name of the mainline module.",
                ARG_SDK_INFO_FILE,
                "Points to map of extension SDK APIs to include, if any. The file is a plain text file " +
                    "and describes, per extension SDK, what APIs from that extension to include in the " +
                    "file created via $ARG_GENERATE_API_LEVELS. The format of each line is one of the following: " +
                    "\"<module-name> <pattern> <ext-name> [<ext-name> [...]]\", where <module-name> is the " +
                    "name of the mainline module this line refers to, <pattern> is a common Java name prefix " +
                    "of the APIs this line refers to, and <ext-name> is a list of extension SDK names " +
                    "in which these SDKs first appeared, or \"<ext-name> <ext-id> <type>\", where " +
                    "<ext-name> is the name of an SDK, " +
                    "<ext-id> its numerical ID and <type> is one of " +
                    "\"platform\" (the Android platform SDK), " +
                    "\"platform-ext\" (an extension to the Android platform SDK), " +
                    "\"standalone\" (a separate SDK). " +
                    "Fields are separated by whitespace. " +
                    "A mainline module may be listed multiple times. " +
                    "The special pattern \"*\" refers to all APIs in the given mainline module. " +
                    "Lines beginning with # are comments.",
                "",
                "Generating API version history:",
                "$ARG_GENERATE_API_VERSION_HISTORY <jsonfile>",
                "Reads API signature files and generates a JSON file recording the API version each " +
                    "class, method, and field was added in and (if applicable) deprecated in. " +
                    "Required to generate API version JSON.",
                "$ARG_API_VERSION_SIGNATURE_FILES <files>",
                "An ordered list of text API signature files. The oldest API version should be " +
                    "first, the newest last. This should not include a signature file for the " +
                    "current API version, which will be parsed from the provided source files. Not " +
                    "required to generate API version JSON if the current version is the only version.",
                "$ARG_API_VERSION_NAMES <strings>",
                "An ordered list of strings with the names to use for the API versions from " +
                    "$ARG_API_VERSION_SIGNATURE_FILES, and the name of the current API version. " +
                    "Required to generate API version JSON.",
                "",
                "Environment Variables:",
                ENV_VAR_METALAVA_DUMP_ARGV,
                "Set to true to have metalava emit all the arguments it was invoked with. " +
                    "Helpful when debugging or reproducing under a debugger what the build system is doing.",
                ENV_VAR_METALAVA_PREPEND_ARGS,
                "One or more arguments (concatenated by space) to insert into the " +
                    "command line, before the documentation flags.",
                ENV_VAR_METALAVA_APPEND_ARGS,
                "One or more arguments (concatenated by space) to append to the " +
                    "end of the command line, after the generate documentation flags."
            )

        val indent = " ".repeat(INDENT_WIDTH)

        var i = 0
        while (i < args.size) {
            val arg = args[i]
            if (arg.isEmpty()) {
                val groupTitle = args[i + 1]
                out.println("\n")
                out.println(terminal.colorize(groupTitle, TerminalColor.YELLOW))
            } else {
                val description = "\n" + args[i + 1]
                val formattedArg = terminal.bold(arg)
                val invisibleChars = formattedArg.length - arg.length
                // +invisibleChars: the extra chars in the above are counted but don't
                // contribute to width so allow more space
                val formatString = "%1$-" + (INDENT_WIDTH + invisibleChars) + "s%2\$s"

                val output =
                    wrap(
                        String.format(formatString, formattedArg, description),
                        width + invisibleChars,
                        width,
                        indent
                    )

                // Remove trailing whitespace
                val lines = output.lines()
                lines.forEachIndexed { index, line ->
                    out.print(line.trimEnd())
                    if (index < lines.size - 1) {
                        out.println()
                    }
                }
            }
            i += 2
        }
    }
}
