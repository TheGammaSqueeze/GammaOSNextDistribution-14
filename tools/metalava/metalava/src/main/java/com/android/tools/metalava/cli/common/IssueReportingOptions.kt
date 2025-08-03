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

import com.android.tools.metalava.DefaultReporter
import com.android.tools.metalava.DefaultReporterEnvironment
import com.android.tools.metalava.ReporterEnvironment
import com.android.tools.metalava.reporter.IssueConfiguration
import com.android.tools.metalava.reporter.Issues
import com.android.tools.metalava.reporter.Reporter
import com.android.tools.metalava.reporter.Severity
import com.github.ajalt.clikt.parameters.groups.OptionGroup
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.restrictTo
import java.io.File

const val ARG_ERROR = "--error"
const val ARG_WARNING = "--warning"
const val ARG_LINT = "--lint"
const val ARG_HIDE = "--hide"
const val ARG_ERROR_CATEGORY = "--error-category"
const val ARG_WARNING_CATEGORY = "--warning-category"
const val ARG_LINT_CATEGORY = "--lint-category"
const val ARG_HIDE_CATEGORY = "--hide-category"

/** The name of the group, can be used in help text to refer to the options in this group. */
const val REPORTING_OPTIONS_GROUP = "Issue Reporting"

class IssueReportingOptions(
    reporterEnvironment: ReporterEnvironment = DefaultReporterEnvironment()
) :
    OptionGroup(
        name = REPORTING_OPTIONS_GROUP,
        help =
            """
            Options that control which issues are reported, the severity of the reports, how, when
            and where they are reported.

            See `metalava help issues` for more help including a table of the available issues and
            their category and default severity.
        """
                .trimIndent()
    ) {

    /** The [IssueConfiguration] that is configured by these options. */
    val issueConfiguration = IssueConfiguration()

    /**
     * The [Reporter] that is used to report issues encountered while parsing these options.
     *
     * A slight complexity is that this [Reporter] and its [IssueConfiguration] are both modified
     * and used during the process of processing the options.
     */
    internal val bootstrapReporter: Reporter =
        DefaultReporter(
            reporterEnvironment,
            issueConfiguration,
        )

    init {
        // Create a Clikt option for handling the issue options and updating them as a side effect.
        // This needs to be a single option for handling all the issue options in one go because the
        // order of processing them matters and Clikt will collate all the values for all the
        // options together before processing them so something like this would never work if they
        // were treated as separate options.
        //     --hide Foo --error Bar --hide Bar --error Foo
        //
        // When processed immediately that is equivalent to:
        //     --hide Bar --error Foo
        //
        // However, when processed after grouping they would be equivalent to one of the following
        // depending on which was processed last:
        //     --hide Foo,Bar
        //     --error Foo,Bar
        //
        // Instead, this creates a single Clikt option to handle all the issue options but they are
        // still collated before processing so if they are interleaved with other options whose
        // parsing makes use of the `reporter` then there is the potential for a change in behavior.
        // However, currently it does not look as though that is the case except for reporting
        // issues with deprecated options which is tested.
        //
        // Having a single Clikt option with lots of different option names does make the help hard
        // to read as it produces a single line with all the option names on it. So, this uses a
        // mechanism that will cause `MetalavaHelpFormatter` to split the option into multiple
        // separate options purely for help purposes.
        val issueOption =
            compositeSideEffectOption(
                // Create one side effect option per label.
                ConfigLabel.values().map {
                    sideEffectOption(it.optionName, help = it.help) {
                        // if `--hide id1,id2` was supplied on the command line then this will split
                        // it into
                        // ["id1", "id2"]
                        val values = it.split(",")

                        // Get the label from the name of the option.
                        val label = ConfigLabel.fromOptionName(name)

                        // Update the configuration immediately
                        values.forEach {
                            label.setAspectForId(bootstrapReporter, issueConfiguration, it.trim())
                        }
                    }
                }
            )

        // Register the option so that Clikt will process it.
        registerOption(issueOption)
    }

    /** When non-0, metalava repeats all the errors at the end of the run, at most this many. */
    val repeatErrorsMax by
        option(
                ARG_REPEAT_ERRORS_MAX,
                metavar = "<n>",
                help = """When specified, repeat at most N errors before finishing."""
            )
            .int()
            .restrictTo(min = 0)
            .default(0)
}

/** The different configurable aspects of [IssueConfiguration]. */
private enum class ConfigurableAspect {
    /** A single issue needs configuring. */
    ISSUE {
        override fun setAspectSeverityForId(
            reporter: Reporter,
            configuration: IssueConfiguration,
            optionName: String,
            severity: Severity,
            id: String
        ) {
            val issue =
                Issues.findIssueById(id)
                    ?: Issues.findIssueByIdIgnoringCase(id)?.also {
                        reporter.report(
                            Issues.DEPRECATED_OPTION,
                            null as File?,
                            "Case-insensitive issue matching is deprecated, use " +
                                "$optionName ${it.name} instead of $optionName $id"
                        )
                    }
                        ?: throw MetalavaCliException("Unknown issue id: '$optionName' '$id'")

            configuration.setSeverity(issue, severity)
        }
    },
    /** A whole category of issues needs configuring. */
    CATEGORY {
        override fun setAspectSeverityForId(
            reporter: Reporter,
            configuration: IssueConfiguration,
            optionName: String,
            severity: Severity,
            id: String
        ) {
            val issues =
                Issues.findCategoryById(id)?.let { Issues.findIssuesByCategory(it) }
                    ?: throw MetalavaCliException("Unknown category: $optionName $id")

            issues.forEach { configuration.setSeverity(it, severity) }
        }
    };

    /** Configure the [IssueConfiguration] appropriately. */
    abstract fun setAspectSeverityForId(
        reporter: Reporter,
        configuration: IssueConfiguration,
        optionName: String,
        severity: Severity,
        id: String
    )
}

/** The different labels that can be used on the command line. */
private enum class ConfigLabel(
    val optionName: String,
    /** The [Severity] which this label corresponds to. */
    val severity: Severity,
    val aspect: ConfigurableAspect,
    val help: String
) {
    ERROR(
        ARG_ERROR,
        Severity.ERROR,
        ConfigurableAspect.ISSUE,
        "Report issues of the given id as errors",
    ),
    WARNING(
        ARG_WARNING,
        Severity.WARNING,
        ConfigurableAspect.ISSUE,
        "Report issues of the given id as warnings",
    ),
    LINT(
        ARG_LINT,
        Severity.LINT,
        ConfigurableAspect.ISSUE,
        "Report issues of the given id as having lint-severity",
    ),
    HIDE(
        ARG_HIDE,
        Severity.HIDDEN,
        ConfigurableAspect.ISSUE,
        "Hide/skip issues of the given id",
    ),
    ERROR_CATEGORY(
        ARG_ERROR_CATEGORY,
        Severity.ERROR,
        ConfigurableAspect.CATEGORY,
        "Report all issues in the given category as errors",
    ),
    WARNING_CATEGORY(
        ARG_WARNING_CATEGORY,
        Severity.WARNING,
        ConfigurableAspect.CATEGORY,
        "Report all issues in the given category as warnings",
    ),
    LINT_CATEGORY(
        ARG_LINT_CATEGORY,
        Severity.LINT,
        ConfigurableAspect.CATEGORY,
        "Report all issues in the given category as having lint-severity",
    ),
    HIDE_CATEGORY(
        ARG_HIDE_CATEGORY,
        Severity.HIDDEN,
        ConfigurableAspect.CATEGORY,
        "Hide/skip all issues in the given category",
    );

    /** Configure the aspect identified by [id] into the [configuration]. */
    fun setAspectForId(reporter: Reporter, configuration: IssueConfiguration, id: String) {
        aspect.setAspectSeverityForId(reporter, configuration, optionName, severity, id)
    }

    companion object {
        private val optionNameToLabel = ConfigLabel.values().associateBy { it.optionName }

        /**
         * Get the label for the option name. This is only called with an option name that has been
         * obtained from [ConfigLabel.optionName] so it is known that it must match.
         */
        fun fromOptionName(option: String): ConfigLabel = optionNameToLabel[option]!!
    }
}
