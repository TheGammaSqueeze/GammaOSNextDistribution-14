/*
 * Copyright (C) 2022 The Android Open Source Project
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

package androidx.test.uiautomator

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** A parser for logcat logs processing. */
object LogcatParser {
    private val LOGCAT_LOGS_PATTERN = "^\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}.\\d{3} ".toRegex()
    private const val LOGCAT_DATE_FORMAT = "yyyy-MM-dd HH:mm:ss.SSS"

    /**
     * Filters out the logcat logs which contains specific log and appears not before specific time.
     *
     * @param logcatLogs the concatenated logcat logs to filter
     * @param specificLog the log string expected to appear
     * @param startTime the time point to start finding the specific log
     * @return a list of logs that match the condition
     */
    fun findSpecificLogAfter(
        logcatLogs: String,
        specificLog: String,
        startTime: Date
    ): List<String> = logcatLogs.split("\n")
        .filter { it.contains(specificLog) && !parseLogTime(it)!!.before(startTime) }

    /**
     * Parses the logcat log string to extract the timestamp.
     *
     * @param logString the log string to parse
     * @return the timestamp of the log
     */
    private fun parseLogTime(logString: String): Date? =
        SimpleDateFormat(LOGCAT_DATE_FORMAT, Locale.US)
            .parse(LOGCAT_LOGS_PATTERN.find(logString)!!.value)
}
