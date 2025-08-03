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

import com.github.ajalt.clikt.output.CliktHelpFormatter
import com.github.ajalt.clikt.output.HelpFormatter
import com.github.ajalt.clikt.output.HelpFormatter.ParameterHelp.Option
import com.github.ajalt.clikt.output.Localization
import java.util.TreeMap

private const val MAX_LINE_WIDTH = 120

/**
 * The following value was chosen to produce the same indentation for option descriptions as is
 * produced by [Options.usage].
 */
private const val MAX_COLUMN_WIDTH = 41

/**
 * There is no way to set a fixed column width for the first column containing the names of options,
 * arguments and sub-commands in [CliktHelpFormatter]. It only supports setting a maximum column
 * width. This provides padding that is added after the names of options, arguments and sub-commands
 * to make them exceed the maximum column width. That will cause [CliktHelpFormatter] to always set
 * the width of the column to the maximum, effectively making the maximum a fixed width.
 *
 * This will cause every option, argument or sub-command to have its description start on the
 * following line even if that is unnecessary. The [MetalavaHelpFormatter.removePadding] method will
 * correct that.
 */
private val namePadding = "X".repeat(MAX_COLUMN_WIDTH)

/**
 * The maximum width for a line containing the name that can have the first line of the description
 * on the same line.
 *
 * This is used by [MetalavaHelpFormatter.removePadding] when removing the [namePadding] from the
 * generated help to determine whether the name and the first line of the description can be on the
 * same line.
 *
 * This value was chosen to ensure that if Clikt would place the description on a separate line to
 * the name when the name is not padded then it will continue to do so after the padding has been
 * applied and removed.
 */
private const val MAX_WIDTH_FOR_DESCRIPTION_ON_SAME_LINE = MAX_COLUMN_WIDTH + 3

/** Metalava specific implementation of [CliktHelpFormatter]. */
internal open class MetalavaHelpFormatter(
    terminalSupplier: () -> Terminal,
    localization: Localization,
) :
    CliktHelpFormatter(
        localization = localization,
        showDefaultValues = true,
        showRequiredTag = true,
        maxWidth = MAX_LINE_WIDTH,
        maxColWidth = MAX_COLUMN_WIDTH,
    ) {

    /**
     * Property for accessing the [Terminal] instance that should be used to style (or not) help
     * text.
     */
    protected val terminal: Terminal by lazy { terminalSupplier() }

    /**
     * The name of the group to which options will be added if they do not belong to another group.
     * This has to match the name of the default options title minus the trailing `:`.
     */
    private val defaultOptionGroupName = localization.optionsTitle().removeSuffix(":")

    override fun formatHelp(
        prolog: String,
        epilog: String,
        parameters: List<HelpFormatter.ParameterHelp>,
        programName: String
    ): String {
        // Color the program name, there is no override to do that.
        val formattedProgramName = terminal.colorize(programName, TerminalColor.BLUE)

        val transformedParameters =
            parameters
                .asSequence()
                // Force all options to belong to a group. This is needed because Clikt will order
                // options without any group name (options like help and version) after option
                // groups but metalava help needs those to come first. It is not possible (or at
                // least not easy) to add group names to some of those options at creation time, so
                // it is done here.
                .map {
                    when (it) {
                        is Option ->
                            if (it.groupName == null) it.copy(groupName = defaultOptionGroupName)
                            else it
                        else -> it
                    }
                }
                // Map a composite option to multiple separate options for the purposes of help
                // formatting only.
                .flatMap { v ->
                    if (v is Option && v.isCompositeOption()) {
                        v.decompose()
                    } else {
                        sequenceOf(v)
                    }
                }
                // Force the options in the default group (like help) to come first.
                .sortedBy { (it as? Option)?.groupName != defaultOptionGroupName }
                .toList()

        // Use the default help format.
        val help = super.formatHelp(prolog, epilog, transformedParameters, formattedProgramName)

        return removePadding(help)
    }

    /**
     * Removes additional padding added to help to force a fixed column width.
     *
     * This also removes trailing white space.
     */
    private fun removePadding(help: String): String = buildString {
        val iterator = help.lines().iterator()
        while (iterator.hasNext()) {
            val line = iterator.next()

            // Try and remove any padding if any.
            val withoutPadding = line.replace(namePadding, "")

            // Check if any padding was found and removed.
            if (line != withoutPadding) {
                append(withoutPadding)

                // Get the length of the line without padding as it will appear in the terminal,
                // i.e. excluding any terminal styling characters.
                val length = withoutPadding.graphemeLength

                // If the name and first line of the description can fit on the same line then merge
                // them together.
                if (length < MAX_WIDTH_FOR_DESCRIPTION_ON_SAME_LINE) {
                    // Make sure that there is a next line. This will happen if no help is provided
                    // and this is the last option/argument.
                    if (iterator.hasNext()) {
                        val nextLine = iterator.next()
                        // Make sure that it is indented as much as the current line.
                        if (nextLine.length >= length) {
                            val reducedIndent = nextLine.substring(length)
                            append(reducedIndent)
                        }
                    }
                }
            } else {
                append(line.trimEnd())
            }
            if (iterator.hasNext()) {
                append("\n")
            }
        }
    }

    override fun renderArgumentName(name: String): String {
        return terminal.bold(super.renderArgumentName(name)) + namePadding
    }

    override fun renderHelpText(help: String, tags: Map<String, String>): String {
        // Copy the tags as they may be modified.
        val mutableTags = TreeMap(tags)

        // Scan the help text to see if it contains enum values and if it does then style them
        // accordingly.
        val styledHelp = styleEnumHelpTextIfNeeded(help, mutableTags, terminal)

        // Add any additional help text.
        val helpText = super.renderHelpText(styledHelp, mutableTags)

        // Remove any trailing NEL to prevent additional blank lines being added. This is done here
        // rather than before as super.renderHelpText(...) may append content to the end which would
        // need to be separated from the rest of the text by a blank line.
        return helpText.removeSuffix(HARD_NEWLINE)
    }

    override fun renderOptionName(name: String): String {
        return terminal.bold(super.renderOptionName(name)) + namePadding
    }

    override fun renderSectionTitle(title: String): String {
        return terminal.colorize(super.renderSectionTitle(title), TerminalColor.YELLOW)
    }

    override fun renderSubcommandName(name: String): String {
        return terminal.bold(super.renderSubcommandName(name)) + namePadding
    }
}
