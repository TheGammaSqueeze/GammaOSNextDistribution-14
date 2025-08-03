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

package com.android.tools.metalava.cli.common

import com.android.tools.metalava.ExecutionEnvironment
import com.android.tools.metalava.ProgressTracker
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.NoSuchOption
import com.github.ajalt.clikt.core.PrintHelpMessage
import com.github.ajalt.clikt.core.PrintMessage
import com.github.ajalt.clikt.core.UsageError
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.groups.provideDelegate
import com.github.ajalt.clikt.parameters.options.eagerOption
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import java.io.PrintWriter

const val ARG_VERSION = "--version"

/**
 * Main metalava command.
 *
 * If no subcommand is specified in the arguments that are passed to [parse] then this will invoke
 * the subcommand called [defaultCommandName] passing in all the arguments not already consumed by
 * Clikt options.
 */
internal open class MetalavaCommand(
    internal val executionEnvironment: ExecutionEnvironment,

    /**
     * The optional name of the default subcommand to run if no subcommand is provided in the
     * command line arguments.
     */
    private val defaultCommandName: String? = null,
    internal val progressTracker: ProgressTracker,
) :
    CliktCommand(
        // Gather all the options and arguments into a list so that they can be handled by some
        // non-Clikt option processor which it is assumed that the default command, if specified,
        // has.
        treatUnknownOptionsAsArgs = defaultCommandName != null,
        // Call run on this command even if no sub-command is provided.
        invokeWithoutSubcommand = true,
        help =
            """
            Extracts metadata from source code to generate artifacts such as the signature files,
            the SDK stub files, external annotations etc.
        """
                .trimIndent()
    ) {

    private val stdout = executionEnvironment.stdout
    private val stderr = executionEnvironment.stderr

    init {
        context {
            console = MetalavaConsole(executionEnvironment)

            localization = MetalavaLocalization()

            /**
             * Disable built in help.
             *
             * See [showHelp] for an explanation.
             */
            helpOptionNames = emptySet()

            // Override the help formatter to use Metalava's special formatter.
            helpFormatter =
                MetalavaHelpFormatter(
                    { common.terminal },
                    localization,
                )
        }

        // Print the version number if requested.
        eagerOption(
            help = "Show the version and exit",
            names = setOf(ARG_VERSION),
            // Abort the processing of options immediately to display the version and exit.
            action = { throw PrintVersionException() }
        )

        // Add the --print-stack-trace option.
        eagerOption(
            "--print-stack-trace",
            help =
                """
                    Print the stack trace of any exceptions that will cause metalava to exit.
                    (default: no stack trace)
                """
                    .trimIndent(),
            action = { printStackTrace = true }
        )
    }

    /** Group of common options. */
    val common by CommonOptions()

    /**
     * True if a stack trace should be output for any exception that is thrown and causes metalava
     * to exit.
     *
     * Uses a real property that is set by an eager option action rather than a normal Clikt option
     * so that it will be readable even if metalava aborts before it has been processed. Otherwise,
     * exceptions that were thrown before the option was processed would cause this field to be
     * accessed to see whether their stack trace should be printed. That access would fail and
     * obscure the original error.
     */
    private var printStackTrace: Boolean = false

    /**
     * A custom, non-eager help option that allows [CommonOptions] like [CommonOptions.terminal] to
     * be used when generating the help output.
     *
     * The built-in help option is eager and throws a [PrintHelpMessage] exception which aborts the
     * processing of other options preventing their use when generating the help output.
     *
     * Currently, this does not support `-?` for help as Clikt considers that to be an invalid flag.
     * However, `-?` is still supported for backwards compatibility using a workaround in
     * [showHelpAndExitIfRequested].
     */
    private val showHelp by option("-h", "--help", help = "Show this message and exit").flag()

    /** Property into which all the arguments (and unknown options) are gathered. */
    private val flags by
        argument(
                name = "flags",
                help = "See below.",
            )
            .multiple()

    /** A list of actions to perform after the command has been executed. */
    private val postCommandActions = mutableListOf<() -> Unit>()

    /** Process the command, handling [MetalavaCliException]s. */
    fun process(args: Array<String>): Int {
        var exitCode = 0
        try {
            processThrowCliException(args)
        } catch (e: PrintVersionException) {
            // Print the version and exit.
            stdout.println("\n$commandName version: ${Version.VERSION}")
        } catch (e: MetalavaCliException) {
            stdout.flush()
            stderr.flush()

            if (printStackTrace) {
                e.printStackTrace(stderr)
            } else {
                val prefix =
                    if (e.exitCode != 0) {
                        "Aborting: "
                    } else {
                        ""
                    }

                if (e.stderr.isNotBlank()) {
                    stderr.println("\n${prefix}${e.stderr}")
                }
                if (e.stdout.isNotBlank()) {
                    stdout.println("\n${prefix}${e.stdout}")
                }
            }

            exitCode = e.exitCode
        }

        // Perform any subcommand specific actions, e.g. flushing files they have opened, etc.
        performPostCommandActions()

        return exitCode
    }

    /**
     * Register a command to run after the command has been executed and after any thrown
     * [MetalavaCliException]s have been caught.
     */
    fun registerPostCommandAction(action: () -> Unit) {
        postCommandActions.add(action)
    }

    /** Perform actions registered by [registerPostCommandAction]. */
    fun performPostCommandActions() {
        postCommandActions.forEach { it() }
    }

    /** Process the command, throwing [MetalavaCliException]s. */
    fun processThrowCliException(args: Array<String>) {
        try {
            parse(args)
        } catch (e: PrintHelpMessage) {
            throw MetalavaCliException(
                stdout = e.command.getFormattedHelp(),
                exitCode = if (e.error) 1 else 0
            )
        } catch (e: PrintMessage) {
            throw MetalavaCliException(
                stdout = e.message ?: "",
                exitCode = if (e.error) 1 else 0,
                cause = e,
            )
        } catch (e: NoSuchOption) {
            val message = createNoSuchOptionErrorMessage(e)
            throw MetalavaCliException(
                stderr = message,
                exitCode = e.statusCode,
                cause = e,
            )
        } catch (e: UsageError) {
            val message = e.helpMessage()
            throw MetalavaCliException(
                stderr = message,
                exitCode = e.statusCode,
                cause = e,
            )
        }
    }

    /**
     * Create an error message that incorporates the specific usage error as well as providing
     * documentation for all the available options.
     */
    private fun createNoSuchOptionErrorMessage(e: UsageError): String {
        return buildString {
            val errorContext = e.context ?: currentContext
            e.message?.let { append(errorContext.localization.usageError(it)).append("\n\n") }
            append(errorContext.command.getFormattedHelp())
        }
    }

    /**
     * Perform this command's actions.
     *
     * This is called after the command line parameters are parsed. If one of the sub-commands is
     * invoked then this is called before the sub-commands parameters are parsed.
     */
    override fun run() {
        // Make this available to all sub-commands.
        currentContext.obj = this

        val subcommand = currentContext.invokedSubcommand
        if (subcommand == null) {
            showHelpAndExitIfRequested()

            if (defaultCommandName != null) {
                // Get any remaining arguments/options that were not handled by Clikt.
                val remainingArgs = flags.toTypedArray()

                // Get the default command.
                val defaultCommand =
                    registeredSubcommands().singleOrNull { it.commandName == defaultCommandName }
                        ?: throw MetalavaCliException(
                            "Invalid default command name '$defaultCommandName', expected one of '${registeredSubcommandNames().joinToString("', '")}'"
                        )

                // No sub-command was provided so use the default subcommand.
                defaultCommand.parse(remainingArgs, currentContext)
            }
        }
    }

    /**
     * Show help and exit if requested.
     *
     * Help is requested if [showHelp] is true or [flags] contains `-?` or `-?`.
     */
    private fun showHelpAndExitIfRequested() {
        val remainingArgs = flags.toTypedArray()
        // Output help and exit if requested.
        if (showHelp || remainingArgs.contains("-?")) {
            throw PrintHelpMessage(this)
        }
    }

    /**
     * Exception to use for the --version option to use for aborting the processing of options
     * immediately and allow the exception handling code to treat it specially.
     */
    private class PrintVersionException : RuntimeException()
}

/**
 * Get the containing [MetalavaCommand].
 *
 * It will always be set.
 */
private val CliktCommand.metalavaCommand
    get() = if (this is MetalavaCommand) this else currentContext.findObject()!!

/** The [ExecutionEnvironment] within which the command is being run. */
val CliktCommand.executionEnvironment: ExecutionEnvironment
    get() = metalavaCommand.executionEnvironment

/** The [PrintWriter] to use for error output from the command. */
val CliktCommand.stderr: PrintWriter
    get() = executionEnvironment.stderr

/** The [PrintWriter] to use for non-error output from the command. */
val CliktCommand.stdout: PrintWriter
    get() = executionEnvironment.stdout

val CliktCommand.commonOptions
    // Retrieve the CommonOptions that is made available by the containing MetalavaCommand.
    get() = metalavaCommand.common

val CliktCommand.terminal
    // Retrieve the terminal from the CommonOptions.
    get() = commonOptions.terminal

val CliktCommand.progressTracker
    // Retrieve the ProgressTracker that is made available by the containing MetalavaCommand.
    get() = metalavaCommand.progressTracker

fun CliktCommand.registerPostCommandAction(action: () -> Unit) {
    metalavaCommand.registerPostCommandAction(action)
}
