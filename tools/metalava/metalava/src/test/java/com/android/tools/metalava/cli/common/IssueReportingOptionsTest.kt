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
import com.android.tools.metalava.ReporterEnvironment
import com.android.tools.metalava.reporter.IssueConfiguration
import com.android.tools.metalava.reporter.Issues
import com.android.tools.metalava.reporter.Severity
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

val ISSUE_REPORTING_OPTIONS_HELP =
    """
Issue Reporting:

  Options that control which issues are reported, the severity of the reports, how, when and where they are reported.

  See `metalava help issues` for more help including a table of the available issues and their category and default
  severity.

  --error <id>                               Report issues of the given id as errors
  --warning <id>                             Report issues of the given id as warnings
  --lint <id>                                Report issues of the given id as having lint-severity
  --hide <id>                                Hide/skip issues of the given id
  --error-category <name>                    Report all issues in the given category as errors
  --warning-category <name>                  Report all issues in the given category as warnings
  --lint-category <name>                     Report all issues in the given category as having lint-severity
  --hide-category <name>                     Hide/skip all issues in the given category
  --repeat-errors-max <n>                    When specified, repeat at most N errors before finishing. (default: 0)
    """
        .trimIndent()

/**
 * JUnit [TestRule] that will intercept calls to [DefaultReporter.reportPrinter], save them into a
 * couple of buffers and then allow the test to verify them. If there are any unverified errors then
 * the test will fail. The other issues will only be verified when requested.
 */
class ReportCollectorRule(
    private val cleaner: (String) -> String,
    private val rootFolderSupplier: () -> File,
) : TestRule {
    private val allReportedIssues = StringBuilder()
    private val errorSeverityReportedIssues = StringBuilder()

    internal var reporterEnvironment: ReporterEnvironment? = null

    override fun apply(base: Statement, description: Description): Statement {
        return object : Statement() {
            override fun evaluate() {
                try {
                    reporterEnvironment = InterceptingReporterEnvironment()
                    // Evaluate the test.
                    base.evaluate()
                } finally {
                    reporterEnvironment = null
                }

                assertEquals("", errorSeverityReportedIssues.toString())
            }
        }
    }

    fun verifyAll(expected: String) {
        assertEquals(expected.trim(), allReportedIssues.toString().trim())
        allReportedIssues.clear()
    }

    fun verifyErrors(expected: String) {
        assertEquals(expected.trim(), errorSeverityReportedIssues.toString().trim())
        errorSeverityReportedIssues.clear()
    }

    /** Intercepts calls to the [ReporterEnvironment] and collates the reports. */
    private inner class InterceptingReporterEnvironment : ReporterEnvironment {

        override val rootFolder: File
            get() = rootFolderSupplier()

        override fun printReport(message: String, severity: Severity) {
            val cleanedMessage = cleaner(message)
            if (severity == Severity.ERROR) {
                errorSeverityReportedIssues.append(cleanedMessage).append('\n')
            }
            allReportedIssues.append(cleanedMessage).append('\n')
        }
    }
}

class IssueReportingOptionsTest :
    BaseOptionGroupTest<IssueReportingOptions>(
        ISSUE_REPORTING_OPTIONS_HELP,
    ) {

    @get:Rule
    val reportCollector = ReportCollectorRule(this::cleanupString, { temporaryFolder.root })

    override fun createOptions(): IssueReportingOptions =
        IssueReportingOptions(reporterEnvironment = reportCollector.reporterEnvironment!!)

    @Test
    fun `Test issue severity options`() {
        runTest(
            "--hide",
            "StartWithLower",
            "--lint",
            "EndsWithImpl",
            "--warning",
            "StartWithUpper",
            "--error",
            "ArrayReturn"
        ) {
            val issueConfiguration = options.issueConfiguration

            assertEquals(Severity.HIDDEN, issueConfiguration.getSeverity(Issues.START_WITH_LOWER))
            assertEquals(Severity.LINT, issueConfiguration.getSeverity(Issues.ENDS_WITH_IMPL))
            assertEquals(Severity.WARNING, issueConfiguration.getSeverity(Issues.START_WITH_UPPER))
            assertEquals(Severity.ERROR, issueConfiguration.getSeverity(Issues.ARRAY_RETURN))
        }
    }

    @Test
    fun `Test multiple issue severity options`() {
        // Purposely includes some whitespace as that is something callers of metalava do.
        runTest("--hide", "StartWithLower ,StartWithUpper, ArrayReturn") {
            val issueConfiguration = options.issueConfiguration
            assertEquals(Severity.HIDDEN, issueConfiguration.getSeverity(Issues.START_WITH_LOWER))
            assertEquals(Severity.HIDDEN, issueConfiguration.getSeverity(Issues.START_WITH_UPPER))
            assertEquals(Severity.HIDDEN, issueConfiguration.getSeverity(Issues.ARRAY_RETURN))
        }
    }

    @Test
    fun `Test issue severity options with inheriting issues`() {
        runTest("--error", "RemovedClass") {
            val issueConfiguration = options.issueConfiguration
            assertEquals(Severity.ERROR, issueConfiguration.getSeverity(Issues.REMOVED_CLASS))
            assertEquals(
                Severity.ERROR,
                issueConfiguration.getSeverity(Issues.REMOVED_DEPRECATED_CLASS)
            )
        }
    }

    @Test
    fun `Test issue severity options with case insensitive names`() {
        runTest("--hide", "arrayreturn") {
            reportCollector.verifyAll(
                "warning: Case-insensitive issue matching is deprecated, use --hide ArrayReturn instead of --hide arrayreturn [DeprecatedOption]"
            )

            val issueConfiguration = options.issueConfiguration
            assertEquals(Severity.HIDDEN, issueConfiguration.getSeverity(Issues.ARRAY_RETURN))
        }
    }

    @Test
    fun `Test issue severity options with non-existing issue`() {
        runTest("--hide", "ThisIssueDoesNotExist") {
            assertEquals("Unknown issue id: '--hide' 'ThisIssueDoesNotExist'", stderr)
        }
    }

    @Test
    fun `Test options process in order`() {
        // Interleave and change the order so that if all the hide options are processed before all
        // the error options (or vice versa) they would result in different behavior.
        runTest(
            "--hide",
            "UnavailableSymbol",
            "--error",
            "HiddenSuperclass",
            "--hide",
            "HiddenSuperclass",
            "--error",
            "UnavailableSymbol",
        ) {

            // Make sure the two issues both default to warning.
            val baseConfiguration = IssueConfiguration()
            assertEquals(Severity.WARNING, baseConfiguration.getSeverity(Issues.HIDDEN_SUPERCLASS))
            assertEquals(Severity.WARNING, baseConfiguration.getSeverity(Issues.UNAVAILABLE_SYMBOL))

            // Now make sure the issues fine.
            val issueConfiguration = options.issueConfiguration
            assertEquals(Severity.HIDDEN, issueConfiguration.getSeverity(Issues.HIDDEN_SUPERCLASS))
            assertEquals(Severity.ERROR, issueConfiguration.getSeverity(Issues.UNAVAILABLE_SYMBOL))
        }
    }

    @Test
    fun `Test issue severity options can affect issues related to processing the options`() {
        runTest("--error", "DeprecatedOption", "--hide", "arrayreturn") {
            reportCollector.verifyErrors(
                "error: Case-insensitive issue matching is deprecated, use --hide ArrayReturn instead of --hide arrayreturn [DeprecatedOption]\n"
            )

            val issueConfiguration = options.issueConfiguration
            assertEquals(Severity.HIDDEN, issueConfiguration.getSeverity(Issues.ARRAY_RETURN))
            assertEquals(Severity.ERROR, issueConfiguration.getSeverity(Issues.DEPRECATED_OPTION))
        }
    }
}
