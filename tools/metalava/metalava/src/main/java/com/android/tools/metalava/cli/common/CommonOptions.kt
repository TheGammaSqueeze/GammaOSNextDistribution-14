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

import com.github.ajalt.clikt.parameters.options.deprecated
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.switch

const val ARG_COLOR = "--color"
const val ARG_NO_COLOR = "--no-color"
const val ARG_NO_BANNER = "--no-banner"

const val ARG_REPEAT_ERRORS_MAX = "--repeat-errors-max"

/**
 * Options that are common to all metalava sub-commands.
 *
 * This extends [EarlyOptions] for a couple of reasons:
 * 1. [EarlyOptions] does not consume any arguments when parsing so, they need to be parsed again.
 * 2. The options in [EarlyOptions] need to be added to the help just as if they were part of this
 *    class.
 *
 * Extending [EarlyOptions] solves both of those issues.
 */
class CommonOptions : EarlyOptions() {

    /**
     * Whether output should use terminal capabilities.
     *
     * This is unsafe to use when generating the help as the help may be being generated in response
     * to a failure to parse these options in which case these options will not be set.
     *
     * This is only accessible for testing purposes, do not use this otherwise. Use [terminal]
     * instead.
     */
    internal val unsafeTerminal by
        option(
                "terminal",
                help =
                    """
                Determine whether to use terminal capabilities to colorize and otherwise style the
                output. (default: true if ${"$"}TERM starts with `xterm` or ${"$"}COLORTERM is set)
            """
                        .trimIndent(),
            )
            .switch(
                ARG_COLOR to stylingTerminal,
                ARG_NO_COLOR to plainTerminal,
            )

    /** A safe property for accessing the terminal. */
    val terminal by lazy {
        val configuredTerminal =
            try {
                unsafeTerminal
            } catch (e: IllegalStateException) {
                null
            }

        configuredTerminal
            ?: run {
                val colorDefaultValue: Boolean =
                    System.getenv("TERM")?.startsWith("xterm")
                        ?: (System.getenv("COLORTERM") != null)

                if (colorDefaultValue) stylingTerminal else plainTerminal
            }
    }

    @Suppress("unused")
    val noBanner by
        option(ARG_NO_BANNER, help = "A banner is never output so this has no effect")
            .flag(default = true)
            .deprecated(
                "WARNING: option `$ARG_NO_BANNER` is deprecated; it has no effect please remove",
                tagValue = "please remove"
            )
}
