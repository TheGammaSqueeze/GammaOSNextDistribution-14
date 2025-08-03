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

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.groups.OptionGroup
import com.github.ajalt.clikt.parameters.groups.provideDelegate
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.switch

const val ARG_QUIET = "--quiet"
const val ARG_VERBOSE = "--verbose"

enum class Verbosity(val quiet: Boolean = false, val verbose: Boolean = false) {
    /** Whether to report warnings and other diagnostics along the way. */
    QUIET(quiet = true),

    /** Standard output level. */
    NORMAL,

    /** Whether to report extra diagnostics along the way. */
    VERBOSE(verbose = true)
}

/**
 * Container for options that need to be processed as early as possible, i.e. before any commands.
 *
 * Note this could generate results different from what would happen downstream, e.g. if
 * `"--verbose"` was used as a flag value, e.g. `--hide --verbose`. But that's not a realistic
 * problem.
 */
open class EarlyOptions : OptionGroup() {

    val verbosity: Verbosity by
        option(
                help =
                    """
            Set the verbosity of the output.$HARD_NEWLINE
                $ARG_QUIET - Only include vital output.$HARD_NEWLINE
                $ARG_VERBOSE - Include extra diagnostic output.$HARD_NEWLINE
            """
                        .trimIndent()
            )
            .switch(
                ARG_QUIET to Verbosity.QUIET,
                ARG_VERBOSE to Verbosity.VERBOSE,
            )
            .default(Verbosity.NORMAL, defaultForHelp = "Neither $ARG_QUIET or $ARG_VERBOSE")

    companion object {
        /**
         * Parse the arguments to extract the early options; ignores any unknown arguments or
         * options.
         */
        fun parse(args: Array<String>): EarlyOptions {
            val command = EarlyOptionCommand()
            command.parse(args)
            return command.earlyOptions
        }
    }

    /** A command used to parse [EarlyOptions]. */
    private class EarlyOptionCommand : CliktCommand(treatUnknownOptionsAsArgs = true) {
        init {
            context {
                // Ignore help options.
                helpOptionNames = emptySet()
            }
        }

        /**
         * Register an argument that will consume all the unknown options and arguments so that they
         * will not be treated as an error but any collected values will just be ignored.
         */
        @Suppress("unused") private val ignored by argument().multiple()

        val earlyOptions by EarlyOptions()

        override fun run() {}
    }
}
