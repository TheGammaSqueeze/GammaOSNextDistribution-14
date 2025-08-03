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

package com.android.tools.metalava.reporter

import java.io.File
import java.io.PrintWriter

interface Reporter {

    /**
     * Report an issue with a specific file.
     *
     * Delegates to calling `report(id, null, message, Location.forFile(file)`.
     *
     * @param id the id of the issue.
     * @param file the optional source file for which the issue is reported.
     * @param message the message to report.
     * @return true if the issue was reported false it is a known issue in a baseline file.
     */
    fun report(id: Issues.Issue, file: File?, message: String): Boolean {
        val location = IssueLocation.forFile(file)
        return report(id, null, message, location)
    }

    /**
     * Report an issue.
     *
     * The issue is handled as follows:
     * 1. If the item is suppressed (see [isSuppressed]) then it will only be reported to a file
     *    into which suppressed issues are reported and this will return `false`.
     * 2. If possible the issue will be checked in a relevant baseline file to see if it is a known
     *    issue and if so it will simply be ignored.
     * 3. Otherwise, it will be reported at the appropriate severity to the command output and if
     *    possible it will be recorded in a new baseline file that the developer can copy to silence
     *    the issue in the future.
     *
     * If no [location] or [reportable] is provided then no location is reported in the error
     * message, and the baseline file is neither checked nor updated.
     *
     * If a [location] is provided but no [reportable] then it is used both to report the message
     * and as the baseline key to check and update the baseline file.
     *
     * If an [reportable] is provided but no [location] then it is used both to report the message
     * and as the baseline key to check and update the baseline file.
     *
     * If both an [reportable] and [location] are provided then the [reportable] is used as the
     * baseline key to check and update the baseline file and the [location] is used to report the
     * message. The reason for that is the [location] is assumed to be a more accurate indication of
     * where the problem lies but the [reportable] is assumed to provide a more stable key to use in
     * the baseline as it will not change simply by adding and removing lines in the containing
     * file.
     *
     * @param id the id of the issue.
     * @param reportable the optional object for which the issue is reported.
     * @param message the message to report.
     * @param location the optional location to specify.
     * @return true if the issue was reported false it is a known issue in a baseline file.
     */
    fun report(
        id: Issues.Issue,
        reportable: Reportable?,
        message: String,
        location: IssueLocation = IssueLocation.unknownLocationAndBaselineKey
    ): Boolean

    /**
     * Check to see whether the issue is suppressed.
     * 1. If the [Severity] of the [Issues.Issue] is [Severity.HIDDEN] then this returns `true`.
     * 2. If the [reportable] is `null` then this returns `false`.
     * 3. If the item has a suppression annotation that lists the name of the issue then this
     *    returns `true`.
     * 4. Otherwise, this returns `false`.
     */
    fun isSuppressed(
        id: Issues.Issue,
        reportable: Reportable? = null,
        message: String? = null
    ): Boolean
}

/**
 * Basic implementation of a [Reporter] that performs no filtering and simply outputs the message to
 * the supplied [PrintWriter].
 */
class BasicReporter(private val stderr: PrintWriter) : Reporter {
    override fun report(
        id: Issues.Issue,
        reportable: Reportable?,
        message: String,
        location: IssueLocation
    ): Boolean {
        stderr.println(
            buildString {
                val usableLocation = reportable?.issueLocation ?: location
                append(usableLocation.path)
                if (usableLocation.line > 0) {
                    append(":")
                    append(usableLocation.line)
                }
                append(": ")
                append(id.defaultLevel.name.lowercase())
                append(": ")
                append(message)
                append(" [")
                append(id.name)
                append("]")
            }
        )
        return true
    }

    override fun isSuppressed(
        id: Issues.Issue,
        reportable: Reportable?,
        message: String?
    ): Boolean = false
}
