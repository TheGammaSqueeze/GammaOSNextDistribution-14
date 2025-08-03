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

import com.android.tools.lint.checks.infrastructure.TestFiles
import com.android.tools.metalava.cli.common.BaseCommandTest
import com.android.tools.metalava.cli.common.CommonOptions
import com.android.tools.metalava.cli.common.ISSUE_REPORTING_OPTIONS_HELP
import com.android.tools.metalava.cli.compatibility.COMPATIBILITY_CHECK_OPTIONS_HELP
import com.android.tools.metalava.cli.signature.SIGNATURE_FORMAT_OPTIONS_HELP
import com.android.tools.metalava.model.source.DEFAULT_JAVA_LANGUAGE_LEVEL
import com.android.tools.metalava.model.source.DEFAULT_KOTLIN_LANGUAGE_LEVEL
import com.android.tools.metalava.reporter.Issues
import java.io.File
import java.util.Locale
import kotlin.test.assertEquals
import org.junit.Assert
import org.junit.Test

class MainCommandTest :
    BaseCommandTest<MainCommand>({ executionEnvironment ->
        MainCommand(
            commonOptions = CommonOptions(),
            executionEnvironment = executionEnvironment,
        )
    }) {

    private val EXPECTED_HELP =
        """
Usage: metalava main [options] [flags]...

  The default sub-command that is run if no sub-command is specified.

Options:
  --api-class-resolution [api|api:classpath]
                                             Determines how class resolution is performed when loading API signature
                                             files. Any classes that cannot be found will be treated as empty.",

                                             api - will only look for classes in the API signature files.

                                             api:classpath (default) - will look for classes in the API signature files
                                             first and then in the classpath.
  --suppress-compatibility-meta-annotation <meta-annotation class>
                                             Suppress compatibility checks for any elements within the scope of an
                                             annotation which is itself annotated with the given meta-annotation.
  --manifest <file>                          A manifest file, used to check permissions to cross check APIs and retrieve
                                             min_sdk_version. (default: no manifest)
  --hide-sdk-extensions-newer-than INT       Ignore SDK extensions version INT and above. Used to exclude finalized but
                                             not yet released SDK extensions.
  --typedefs-in-signatures [none|ref|inline]
                                             Whether to include typedef annotations in signature files.

                                             none (default) - will not include typedef annotations in signature.

                                             ref - will include just a reference to the typedef class, which is not
                                             itself part of the API and is not included as a class

                                             inline - will include the constants themselves into each usage site
  -h, --help                                 Show this message and exit

$ISSUE_REPORTING_OPTIONS_HELP

$COMPATIBILITY_CHECK_OPTIONS_HELP

Signature File Output:

  Options controlling the signature file output. The format of the generated file is determined by the options in the
  `Signature Format Output` section.

  --api <file>                               Output file into which the API signature will be generated. If this is not
                                             specified then no API signature file will be created.
  --removed-api <file>                       Output file into which the API signatures for removed APIs will be
                                             generated. If this is not specified then no removed API signature file will
                                             be created.

$SIGNATURE_FORMAT_OPTIONS_HELP

$STUB_GENERATION_OPTIONS_HELP

Arguments:
  flags                                      See below.


API sources:
--source-files <files>
                                             A comma separated list of source files to be parsed. Can also be @ followed
                                             by a path to a text file containing paths to the full set of files to
                                             parse.
--source-path <paths>
                                             One or more directories (separated by `:`) containing source files (within
                                             a package hierarchy).
--common-source-path <paths>
                                             One or more directories (separated by `:`) containing common source files
                                             (within a package hierarchy) where platform-agnostic `expect` declarations
                                             as well as common business logic are defined.
--classpath <paths>
                                             One or more directories or jars (separated by `:`) containing classes that
                                             should be on the classpath when parsing the source files
--merge-qualifier-annotations <file>
                                             An external annotations file to merge and overlay the sources, or a
                                             directory of such files. Should be used for annotations intended for
                                             inclusion in the API to be written out, e.g. nullability. Formats supported
                                             are: IntelliJ's external annotations database format, .jar or .zip files
                                             containing those, Android signature files, and Java stub files.
--merge-inclusion-annotations <file>
                                             An external annotations file to merge and overlay the sources, or a
                                             directory of such files. Should be used for annotations which determine
                                             inclusion in the API to be written out, i.e. show and hide. The only format
                                             supported is Java stub files.
--validate-nullability-from-merged-stubs
                                             Triggers validation of nullability annotations for any class where
                                             --merge-qualifier-annotations includes a Java stub file.
--validate-nullability-from-list
                                             Triggers validation of nullability annotations for any class listed in the
                                             named file (one top-level class per line, # prefix for comment line).
--nullability-warnings-txt <file>
                                             Specifies where to write warnings encountered during validation of
                                             nullability annotations. (Does not trigger validation by itself.)
--nullability-errors-non-fatal
                                             Specifies that errors encountered during validation of nullability
                                             annotations should not be treated as errors. They will be written out to
                                             the file specified in --nullability-warnings-txt instead.
--input-api-jar <file>
                                             A .jar file to read APIs from directly
--hide-package <package>
                                             Remove the given packages from the API even if they have not been marked
                                             with @hide
--show-annotation <annotation class>
                                             Unhide any hidden elements that are also annotated with the given
                                             annotation
--show-single-annotation <annotation>
                                             Like --show-annotation, but does not apply to members; these must also be
                                             explicitly annotated
--show-for-stub-purposes-annotation <annotation class>
                                             Like --show-annotation, but elements annotated with it are assumed to be
                                             "implicitly" included in the API surface, and they'll be included in
                                             certain kinds of output such as stubs, but not in others, such as the
                                             signature file and API lint
--hide-annotation <annotation class>
                                             Treat any elements annotated with the given annotation as hidden
--show-unannotated
                                             Include un-annotated public APIs in the signature file as well
--java-source <level>
                                             Sets the source level for Java source files; default is ${DEFAULT_JAVA_LANGUAGE_LEVEL}.
--kotlin-source <level>
                                             Sets the source level for Kotlin source files; default is ${DEFAULT_KOTLIN_LANGUAGE_LEVEL}.
--sdk-home <dir>
                                             If set, locate the `android.jar` file from the given Android SDK
--compile-sdk-version <api>
                                             Use the given API level
--jdk-home <dir>
                                             If set, add the Java APIs from the given JDK to the classpath
--stub-packages <package-list>
                                             List of packages (separated by :) which will be used to filter out
                                             irrelevant code. If specified, only code in these packages will be included
                                             in signature files, stubs, etc. (This is not limited to just the stubs; the
                                             name is historical.) You can also use ".*" at the end to match subpackages,
                                             so `foo.*` will match both `foo` and `foo.bar`.
--subtract-api <api file>
                                             Subtracts the API in the given signature or jar file from the current API
                                             being emitted via --api, --stubs, --doc-stubs, etc. Note that the
                                             subtraction only applies to classes; it does not subtract members.
--ignore-classes-on-classpath
                                             Prevents references to classes on the classpath from being added to the
                                             generated stub files.


Extracting Signature Files:
--dex-api <file>
                                             Generate a DEX signature descriptor file listing the APIs
--proguard <file>
                                             Write a ProGuard keep file for the API
--sdk-values <dir>
                                             Write SDK values files to the given directory


Generating Stubs:
--doc-stubs <dir>
                                             Generate documentation stub source files for the API. Documentation stub
                                             files are similar to regular stub files, but there are some differences.
                                             For example, in the stub files, we'll use special annotations like
                                             @RecentlyNonNull instead of @NonNull to indicate that an element is
                                             recently marked as non null, whereas in the documentation stubs we'll just
                                             list this as @NonNull. Another difference is that @doconly elements are
                                             included in documentation stubs, but not regular stubs, etc.
--kotlin-stubs
                                             [CURRENTLY EXPERIMENTAL] If specified, stubs generated from Kotlin source
                                             code will be written in Kotlin rather than the Java programming language.
--pass-through-annotation <annotation classes>
                                             A comma separated list of fully qualified names of annotation classes that
                                             must be passed through unchanged.
--exclude-annotation <annotation classes>
                                             A comma separated list of fully qualified names of annotation classes that
                                             must be stripped from metalava's outputs.
--enhance-documentation
                                             Enhance documentation in various ways, for example auto-generating
                                             documentation based on source annotations present in the code. This is
                                             implied by --doc-stubs.
--exclude-documentation-from-stubs
                                             Exclude element documentation (javadoc and kdoc) from the generated stubs.
                                             (Copyright notices are not affected by this, they are always included.
                                             Documentation stubs (--doc-stubs) are not affected.)


Diffs and Checks:
--api-lint [api file]
                                             Check API for Android API best practices. If a signature file is provided,
                                             only the APIs that are new since the API will be checked.
--migrate-nullness <api file>
                                             Compare nullness information with the previous stable API and mark newly
                                             annotated APIs as under migration.
--warnings-as-errors
                                             Promote all warnings to errors
--lints-as-errors
                                             Promote all API lint warnings to errors
--report-even-if-suppressed <file>
                                             Write all issues into the given file, even if suppressed (via annotation or
                                             baseline) but not if hidden (by '--hide' or '--hide-category')
--baseline <file>
                                             Filter out any errors already reported in the given baseline file, or
                                             create if it does not already exist
--update-baseline [file]
                                             Rewrite the existing baseline file with the current set of warnings. If
                                             some warnings have been fixed, this will delete them from the baseline
                                             files. If a file is provided, the updated baseline is written to the given
                                             file; otherwise the original source baseline file is updated.
--baseline:api-lint <file> --update-baseline:api-lint [file]
                                             Same as --baseline and --update-baseline respectively, but used
                                             specifically for API lint issues performed by --api-lint.
--baseline:compatibility:released <file> --update-baseline:compatibility:released [file]
                                             Same as --baseline and --update-baseline respectively, but used
                                             specifically for API compatibility issues performed by
                                             --check-compatibility:api:released and
                                             --check-compatibility:removed:released.
--pass-baseline-updates
                                             Normally, encountering error will fail the build, even when updating
                                             baselines. This flag allows you to tell metalava to continue without
                                             errors, such that all the baselines in the source tree can be updated in
                                             one go.
--delete-empty-baselines
                                             Whether to delete baseline files if they are updated and there is nothing
                                             to include.
--error-message:api-lint <message>
                                             If set, metalava shows it when errors are detected in --api-lint.


JDiff:
--api-xml <file>
                                             Like --api, but emits the API in the JDiff XML format instead


Extracting Annotations:
--extract-annotations <zipfile>
                                             Extracts source annotations from the source files and writes them into the
                                             given zip file
--include-source-retention
                                             If true, include source-retention annotations in the stub files. Does not
                                             apply to signature files. Source retention annotations are extracted into
                                             the external annotations files instead.


Injecting API Levels:
--apply-api-levels <api-versions.xml>
                                             Reads an XML file containing API level descriptions and merges the
                                             information into the documentation


Extracting API Levels:
--generate-api-levels <xmlfile>
                                             Reads android.jar SDK files and generates an XML file recording the API
                                             level for each class, method and field
--remove-missing-class-references-in-api-levels
                                             Removes references to missing classes when generating the API levels XML
                                             file. This can happen when generating the XML file for the non-updatable
                                             portions of the module-lib sdk, as those non-updatable portions can
                                             reference classes that are part of an updatable apex.
--android-jar-pattern <pattern>
                                             Patterns to use to locate Android JAR files. The default is
                                             ${"$"}ANDROID_HOME/platforms/android-%/android.jar.
--first-version
                                             Sets the first API level to generate an API database from; usually 1
--current-version
                                             Sets the current API level of the current source code
--current-codename
                                             Sets the code name for the current source code
--current-jar
                                             Points to the current API jar, if any
--sdk-extensions-root
                                             Points to root of prebuilt extension SDK jars, if any. This directory is
                                             expected to contain snapshots of historical extension SDK versions in the
                                             form of stub jars. The paths should be on the format
                                             "<int>/public/<module-name>.jar", where <int> corresponds to the extension
                                             SDK version, and <module-name> to the name of the mainline module.
--sdk-extensions-info
                                             Points to map of extension SDK APIs to include, if any. The file is a plain
                                             text file and describes, per extension SDK, what APIs from that extension
                                             to include in the file created via --generate-api-levels. The format of
                                             each line is one of the following: "<module-name> <pattern> <ext-name>
                                             [<ext-name> [...]]", where <module-name> is the name of the mainline module
                                             this line refers to, <pattern> is a common Java name prefix of the APIs
                                             this line refers to, and <ext-name> is a list of extension SDK names in
                                             which these SDKs first appeared, or "<ext-name> <ext-id> <type>", where
                                             <ext-name> is the name of an SDK, <ext-id> its numerical ID and <type> is
                                             one of "platform" (the Android platform SDK), "platform-ext" (an extension
                                             to the Android platform SDK), "standalone" (a separate SDK). Fields are
                                             separated by whitespace. A mainline module may be listed multiple times.
                                             The special pattern "*" refers to all APIs in the given mainline module.
                                             Lines beginning with # are comments.


Generating API version history:
--generate-api-version-history <jsonfile>
                                             Reads API signature files and generates a JSON file recording the API
                                             version each class, method, and field was added in and (if applicable)
                                             deprecated in. Required to generate API version JSON.
--api-version-signature-files <files>
                                             An ordered list of text API signature files. The oldest API version should
                                             be first, the newest last. This should not include a signature file for the
                                             current API version, which will be parsed from the provided source files.
                                             Not required to generate API version JSON if the current version is the
                                             only version.
--api-version-names <strings>
                                             An ordered list of strings with the names to use for the API versions from
                                             --api-version-signature-files, and the name of the current API version.
                                             Required to generate API version JSON.


Environment Variables:
METALAVA_DUMP_ARGV
                                             Set to true to have metalava emit all the arguments it was invoked with.
                                             Helpful when debugging or reproducing under a debugger what the build
                                             system is doing.
METALAVA_PREPEND_ARGS
                                             One or more arguments (concatenated by space) to insert into the command
                                             line, before the documentation flags.
METALAVA_APPEND_ARGS
                                             One or more arguments (concatenated by space) to append to the end of the
                                             command line, after the generate documentation flags.
        """
            .trimIndent()

    @Test
    fun `Test help`() {
        commandTest {
            args += listOf("main", "--help")
            expectedStdout = EXPECTED_HELP
        }
    }

    @Test
    fun `Test invalid option`() {
        commandTest {
            args += listOf("main", "--blah-blah-blah")
            expectedStderr =
                """
Aborting: Error: no such option: "--blah-blah-blah"

$EXPECTED_HELP
                """
                    .trimIndent()
        }
    }

    @Test
    fun `Test deprecated lowercase matching in issue configuration options`() {
        // Temporarily set [options] as it is needed by the [ReporterOptions.reporter] when
        // reporting [Issues.DEPRECATED_OPTION].
        @Suppress("DEPRECATION")
        options = Options()

        commandTest {
            args +=
                listOf(
                    "main",
                    "--error",
                    Issues.DEPRECATED_OPTION.name,
                    "--hide",
                    Issues.ADDED_FINAL.name.lowercase(Locale.US),
                )

            expectedStderr =
                """
error: Case-insensitive issue matching is deprecated, use --hide AddedFinal instead of --hide addedfinal [DeprecatedOption]
                """
                    .trimIndent()

            verify { assertEquals(-1, exitCode, message = "exitCode") }
        }
    }

    @Test
    fun `Test for @file`() {
        val dir = temporaryFolder.newFolder()
        val files = (1..4).map { TestFiles.source("File$it.java", "File$it").createFile(dir) }
        val fileList =
            TestFiles.source(
                "files.lst",
                """
            ${files[0]}
            ${files[1]} ${files[2]}
            ${files[3]}
        """
                    .trimIndent()
            )

        val file = fileList.createFile(dir)

        commandTest {
            args += listOf("main", "@$file")

            verify {
                fun normalize(f: File): String = f.relativeTo(dir).path
                Assert.assertEquals(
                    files.map { normalize(it) },
                    command.optionGroup.sources.map { normalize(it) }
                )
            }
        }
    }
}
