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

import com.android.tools.metalava.cli.common.CommonOptions
import com.android.tools.metalava.cli.common.IssueReportingOptions
import com.android.tools.metalava.cli.common.LegacyHelpFormatter
import com.android.tools.metalava.cli.common.MetalavaCliException
import com.android.tools.metalava.cli.common.MetalavaLocalization
import com.android.tools.metalava.cli.common.executionEnvironment
import com.android.tools.metalava.cli.common.progressTracker
import com.android.tools.metalava.cli.common.registerPostCommandAction
import com.android.tools.metalava.cli.common.stderr
import com.android.tools.metalava.cli.common.stdout
import com.android.tools.metalava.cli.common.terminal
import com.android.tools.metalava.cli.compatibility.CompatibilityCheckOptions
import com.android.tools.metalava.cli.signature.SignatureFormatOptions
import com.android.tools.metalava.model.source.SourceModelProvider
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.groups.provideDelegate
import java.io.PrintWriter

/**
 * A command that is passed to [MetalavaCommand.defaultCommand] when the main metalava functionality
 * needs to be run when no subcommand is provided.
 */
class MainCommand(
    commonOptions: CommonOptions,
    executionEnvironment: ExecutionEnvironment,
) :
    CliktCommand(
        help = "The default sub-command that is run if no sub-command is specified.",
        treatUnknownOptionsAsArgs = true,
    ) {

    init {
        // Although, the `helpFormatter` is inherited from the parent context unless overridden the
        // same is not true for the `localization` so make sure to initialize it for this command.
        context {
            localization = MetalavaLocalization()

            // Explicitly specify help options as the parent command disables it.
            helpOptionNames = setOf("-h", "--help")

            // Override the help formatter to add in documentation for the legacy flags.
            helpFormatter =
                LegacyHelpFormatter(
                    { terminal },
                    localization,
                    OptionsHelp::getUsage,
                )
        }
    }

    /** Property into which all the arguments (and unknown options) are gathered. */
    private val flags by
        argument(
                name = "flags",
                help = "See below.",
            )
            .multiple()

    /** Issue reporter configuration. */
    private val issueReportingOptions by
        IssueReportingOptions(executionEnvironment.reporterEnvironment)

    /** Compatibility check options. */
    private val compatibilityCheckOptions by CompatibilityCheckOptions()

    /** Signature file options. */
    private val signatureFileOptions by SignatureFileOptions()

    /** Signature format options. */
    private val signatureFormatOptions by SignatureFormatOptions()

    /** Stub generation options. */
    private val stubGenerationOptions by StubGenerationOptions()

    /**
     * Add [Options] (an [OptionGroup]) so that any Clikt defined properties will be processed by
     * Clikt.
     */
    internal val optionGroup by
        Options(
            commonOptions = commonOptions,
            compatibilityCheckOptions = compatibilityCheckOptions,
            issueReportingOptions = issueReportingOptions,
            signatureFileOptions = signatureFileOptions,
            signatureFormatOptions = signatureFormatOptions,
            stubGenerationOptions = stubGenerationOptions,
        )

    override fun run() {
        // Make sure to flush out the baseline files, close files and write any final messages.
        registerPostCommandAction {
            // Update and close all baseline files.
            optionGroup.allBaselines.forEach { baseline ->
                if (optionGroup.verbose) {
                    baseline.dumpStats(optionGroup.stdout)
                }
                if (baseline.close()) {
                    if (!optionGroup.quiet) {
                        stdout.println(
                            "$PROGRAM_NAME wrote updated baseline to ${baseline.updateFile}"
                        )
                    }
                }
            }

            optionGroup.reportEvenIfSuppressedWriter?.close()

            // Show failure messages, if any.
            optionGroup.allReporters.forEach { it.writeErrorMessage(stderr) }
        }

        // Get any remaining arguments/options that were not handled by Clikt.
        val remainingArgs = flags.toTypedArray()

        // Parse any remaining arguments
        optionGroup.parse(executionEnvironment, remainingArgs)

        // Update the global options.
        @Suppress("DEPRECATION")
        options = optionGroup

        val sourceModelProvider =
            // Use the [SourceModelProvider] specified by the [TestEnvironment], if any.
            executionEnvironment.testEnvironment?.sourceModelProvider
            // Otherwise, use the one specified on the command line, or the default.
            ?: SourceModelProvider.getImplementation(optionGroup.sourceModelProvider)
        sourceModelProvider.createEnvironmentManager(disableStderrDumping()).use {
            processFlags(executionEnvironment, it, progressTracker)
        }

        if (optionGroup.allReporters.any { it.hasErrors() } && !optionGroup.passBaselineUpdates) {
            // Repeat the errors at the end to make it easy to find the actual problems.
            if (issueReportingOptions.repeatErrorsMax > 0) {
                repeatErrors(
                    stderr,
                    optionGroup.allReporters,
                    issueReportingOptions.repeatErrorsMax
                )
            }

            // Make sure that the process exits with an error code.
            throw MetalavaCliException(exitCode = -1)
        }
    }
}

private fun repeatErrors(writer: PrintWriter, reporters: List<DefaultReporter>, max: Int) {
    writer.println("Error: $PROGRAM_NAME detected the following problems:")
    val totalErrors = reporters.sumOf { it.errorCount }
    var remainingCap = max
    var totalShown = 0
    reporters.forEach {
        val numShown = it.printErrors(writer, remainingCap)
        remainingCap -= numShown
        totalShown += numShown
    }
    if (totalShown < totalErrors) {
        writer.println(
            "${totalErrors - totalShown} more error(s) omitted. Search the log for 'error:' to find all of them."
        )
    }
}
