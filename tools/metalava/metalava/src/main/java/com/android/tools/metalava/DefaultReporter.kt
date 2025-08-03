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

import com.android.tools.metalava.cli.common.Terminal
import com.android.tools.metalava.cli.common.TerminalColor
import com.android.tools.metalava.cli.common.plainTerminal
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.PackageItem
import com.android.tools.metalava.reporter.Baseline
import com.android.tools.metalava.reporter.IssueConfiguration
import com.android.tools.metalava.reporter.IssueLocation
import com.android.tools.metalava.reporter.Issues
import com.android.tools.metalava.reporter.Reportable
import com.android.tools.metalava.reporter.Reporter
import com.android.tools.metalava.reporter.Severity
import com.android.tools.metalava.reporter.Severity.ERROR
import com.android.tools.metalava.reporter.Severity.HIDDEN
import com.android.tools.metalava.reporter.Severity.INFO
import com.android.tools.metalava.reporter.Severity.INHERIT
import com.android.tools.metalava.reporter.Severity.LINT
import com.android.tools.metalava.reporter.Severity.WARNING
import java.io.File
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.nio.file.Path

@Suppress("DEPRECATION")
internal class DefaultReporter(
    private val environment: ReporterEnvironment,
    private val issueConfiguration: IssueConfiguration,

    /** [Baseline] file associated with this [Reporter]. */
    private val baseline: Baseline? = null,

    /**
     * An error message associated with this [Reporter], which should be shown to the user when
     * metalava finishes with errors.
     */
    private val errorMessage: String? = null,

    /** Filter to hide issues reported in packages which are not part of the API. */
    private val packageFilter: PackageFilter? = null,
) : Reporter {
    private var errors = mutableListOf<String>()
    private var warningCount = 0

    /** The number of errors. */
    val errorCount
        get() = errors.size

    /** Returns whether any errors have been detected. */
    fun hasErrors(): Boolean = errors.size > 0

    override fun report(
        id: Issues.Issue,
        reportable: Reportable?,
        message: String,
        location: IssueLocation
    ): Boolean {
        val severity = issueConfiguration.getSeverity(id)
        if (severity == HIDDEN) {
            return false
        }

        fun dispatch(
            which:
                (
                    severity: Severity, location: String?, message: String, id: Issues.Issue
                ) -> Boolean
        ): Boolean {
            // When selecting a location to use for reporting the issue the location is used in
            // preference to the item because the location is more specific. e.g. if the item is a
            // method then the location may be a line within the body of the method.
            val reportLocation =
                when {
                    location.path != null -> location.forReport()
                    reportable != null -> reportable.issueLocation.forReport()
                    else -> null
                }

            return which(severity, reportLocation, message, id)
        }

        // Optionally write to the --report-even-if-suppressed file.
        dispatch(this::reportEvenIfSuppressed)

        if (isSuppressed(id, reportable, message)) {
            return false
        }

        // If we are only emitting some packages (--stub-packages), don't report
        // issues from other packages
        val item = reportable as? Item
        if (item != null) {
            if (packageFilter != null) {
                val pkg = (item as? PackageItem) ?: item.containingPackage()
                if (pkg != null && !packageFilter.matches(pkg)) {
                    return false
                }
            }
        }

        if (baseline != null) {
            // When selecting a location to use for in checking the baseline the item is used in
            // preference to the location because the item is more stable. e.g. the location may be
            // for a specific line within a method which would change over time while the method
            // signature would stay the same.
            val baselineLocation =
                when {
                    reportable != null -> reportable.issueLocation
                    location.path != null -> location
                    else -> null
                }

            if (baselineLocation != null && baseline.mark(baselineLocation, message, id))
                return false
        }

        return dispatch(this::doReport)
    }

    override fun isSuppressed(
        id: Issues.Issue,
        reportable: Reportable?,
        message: String?
    ): Boolean {
        val severity = issueConfiguration.getSeverity(id)
        if (severity == HIDDEN) {
            return true
        }

        reportable ?: return false

        // Suppress the issue if requested for the item.
        return reportable.suppressedIssues().any { suppressMatches(it, id.name, message) }
    }

    private fun suppressMatches(value: String, id: String?, message: String?): Boolean {
        id ?: return false

        if (value == id) {
            return true
        }

        if (
            message != null &&
                value.startsWith(id) &&
                value.endsWith(message) &&
                (value == "$id:$message" || value == "$id: $message")
        ) {
            return true
        }

        return false
    }

    /**
     * Relativize the [absolutePath] against the [ReporterEnvironment.rootFolder] if specified.
     *
     * Tests will set [rootFolder] to the temporary directory so that this can remove that from any
     * paths that are reported to avoid the test having to be aware of the temporary directory.
     */
    private fun relativizeLocationPath(absolutePath: Path): String {
        // b/255575766: Note that [relativize] requires two paths to compare to have same types:
        // either both of them are absolute paths or both of them are not absolute paths.
        val path = environment.rootFolder.toPath().relativize(absolutePath) ?: absolutePath
        return path.toString()
    }

    /**
     * Convert the [IssueLocation] to an optional string representation suitable for use in a
     * report.
     *
     * See [relativizeLocationPath].
     */
    private fun IssueLocation.forReport(): String? {
        val pathString = path?.let { relativizeLocationPath(it) } ?: return null
        return if (line > 0) "$pathString:$line" else pathString
    }

    /** Alias to allow method reference to `dispatch` in [report] */
    private fun doReport(
        severity: Severity,
        location: String?,
        message: String,
        id: Issues.Issue?,
    ): Boolean {
        if (severity == HIDDEN) {
            return false
        }

        val effectiveSeverity =
            if (severity == LINT && options.lintsAreErrors) ERROR
            else if (severity == WARNING && options.warningsAreErrors) {
                ERROR
            } else {
                severity
            }

        val terminal: Terminal = options.terminal
        val formattedMessage = format(effectiveSeverity, location, message, id, terminal)
        if (effectiveSeverity == ERROR) {
            errors.add(formattedMessage)
        } else if (severity == WARNING) {
            warningCount++
        }

        environment.printReport(formattedMessage, effectiveSeverity)
        return true
    }

    private fun format(
        severity: Severity,
        location: String?,
        message: String,
        id: Issues.Issue?,
        terminal: Terminal,
    ): String {
        val sb = StringBuilder(100)

        sb.append(terminal.attributes(bold = true))
        location?.let { sb.append(it).append(": ") }
        when (severity) {
            LINT -> sb.append(terminal.attributes(foreground = TerminalColor.CYAN)).append("lint: ")
            INFO -> sb.append(terminal.attributes(foreground = TerminalColor.CYAN)).append("info: ")
            WARNING ->
                sb.append(terminal.attributes(foreground = TerminalColor.YELLOW))
                    .append("warning: ")
            ERROR ->
                sb.append(terminal.attributes(foreground = TerminalColor.RED)).append("error: ")
            INHERIT,
            HIDDEN -> {}
        }
        sb.append(terminal.reset())
        sb.append(message)
        id?.let { sb.append(" [").append(it.name).append("]") }
        return sb.toString()
    }

    private fun reportEvenIfSuppressed(
        severity: Severity,
        location: String?,
        message: String,
        id: Issues.Issue
    ): Boolean {
        options.reportEvenIfSuppressedWriter?.println(
            format(severity, location, message, id, terminal = plainTerminal)
        )
        return true
    }

    /** Print all the recorded errors to the given writer. Returns the number of errors printer. */
    fun printErrors(writer: PrintWriter, maxErrors: Int): Int {
        var i = 0
        errors.forEach loop@{
            if (i >= maxErrors) {
                return@loop
            }
            i++
            writer.println(it)
        }
        return i
    }

    /** Write the error message set to this [Reporter], if any errors have been detected. */
    fun writeErrorMessage(writer: PrintWriter) {
        if (hasErrors()) {
            errorMessage?.let { writer.write(it) }
        }
    }

    fun getBaselineDescription(): String {
        val file = baseline?.file
        return if (file != null) {
            "baseline ${file.path}"
        } else {
            "no baseline"
        }
    }
}

/**
 * Provides access to information about the environment within which the [Reporter] will be being
 * used.
 */
interface ReporterEnvironment {

    /** Root folder, against which location paths will be relativized to simplify the output. */
    val rootFolder: File

    /** Print the report. */
    fun printReport(message: String, severity: Severity)
}

class DefaultReporterEnvironment(
    val stdout: PrintWriter = PrintWriter(OutputStreamWriter(System.out)),
    val stderr: PrintWriter = PrintWriter(OutputStreamWriter(System.err)),
) : ReporterEnvironment {

    override val rootFolder = File("").absoluteFile

    override fun printReport(message: String, severity: Severity) {
        val output = if (severity == ERROR) stderr else stdout
        output.println(message.trim())
        output.flush()
    }
}
