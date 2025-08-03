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

package com.android.tools.metalava.cli.help

import com.android.tools.metalava.cli.common.MetalavaHelpFormatter
import com.android.tools.metalava.cli.common.Terminal
import com.android.tools.metalava.cli.common.TerminalColor
import com.android.tools.metalava.cli.common.stdout
import com.android.tools.metalava.cli.common.terminal
import com.android.tools.metalava.reporter.Issues
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.output.HelpFormatter
import com.github.ajalt.clikt.output.Localization

private const val NON_BREAKING_SPACE = "\u00A0"

class IssuesCommand :
    CliktCommand(
        help = "Provides help related to issues and issue reporting",
        invokeWithoutSubcommand = true,
    ) {

    init {
        context {
            localization =
                object : Localization {
                    override fun commandsTitle(): String {
                        val dataColumnHeaders = formatDataColumns("Category", "Default Severity")
                        return """
Available Issues                             $dataColumnHeaders
---------------------------------------------+--------------------------+--------------------
                            """
                            .trimIndent()
                    }

                    override fun commandMetavar(): String {
                        return "<issue>?"
                    }

                    override fun noSuchSubcommand(
                        name: String,
                        possibilities: List<String>
                    ): String {
                        return "no such issue: \"$name\"" +
                            when (possibilities.size) {
                                0 -> ""
                                1 -> ". Did you mean \"${possibilities[0]}\"?"
                                else ->
                                    possibilities.joinToString(
                                        prefix = ". (Possible issues: ",
                                        postfix = ")"
                                    )
                            }
                    }
                }

            helpFormatter = IssueTableFormatter(this@IssuesCommand::terminal, localization)

            // Help options make no sense on a help command.
            helpOptionNames = emptySet()
        }
        subcommands(Issues.all.sortedBy { it.name }.map { IssueCommand(it) })
    }

    override fun run() {
        if (currentContext.invokedSubcommand == null) {
            stdout.println(getFormattedHelp())
        }
    }
}

private class IssueTableFormatter(terminalSupplier: () -> Terminal, localization: Localization) :
    MetalavaHelpFormatter(terminalSupplier = terminalSupplier, localization = localization) {

    override fun formatHelp(
        prolog: String,
        epilog: String,
        parameters: List<HelpFormatter.ParameterHelp>,
        programName: String
    ): String {
        // Replace any non-breaking spaces with normal spaces, so it prints and
        // tests correctly. Colorize the table and the different severities to make
        // it easier to read.
        val help = super.formatHelp(prolog, epilog, parameters, programName)

        return buildString {
            var inTable = false
            for (line in help.lines()) {
                if (!inTable) {
                    if (line.startsWith("----")) {
                        inTable = true
                    }
                    append(line)
                } else {
                    append(
                        line
                            .replace(NON_BREAKING_SPACE, " ")
                            .colorize(" | ", TerminalColor.YELLOW)
                            .colorize(" error", TerminalColor.RED)
                            .colorize(" warning", TerminalColor.MAGENTA)
                            .colorize(" lint", TerminalColor.BLUE)
                            .colorize(" info", TerminalColor.GREEN)
                    )
                }
                append("\n")
            }
        }
    }

    private fun String.colorize(substring: String, color: TerminalColor) =
        replace(substring, terminal.colorize(substring, color))
}

/**
 * Format the data columns.
 *
 * @param useNonBreakingSpace if true then this will replace normal spaces with non-breaking spaces
 *   to prevent them from being collapsed.
 */
internal fun formatDataColumns(
    category: String,
    severity: String,
    useNonBreakingSpace: Boolean = false
): String {
    return "|  %-22s  |   %s".format(category, severity).let {
        if (useNonBreakingSpace) it.replace(" ", NON_BREAKING_SPACE) else it
    }
}
