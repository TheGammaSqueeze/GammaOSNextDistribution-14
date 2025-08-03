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

package android.tools.io

import android.tools.Scenario

/** Possible status of a flicker ru */
enum class RunStatus(val prefix: String, val isFailure: Boolean) {
    UNDEFINED("UNDEFINED", false),
    RUN_EXECUTED("EXECUTED", false),
    ASSERTION_SUCCESS("PASS", false),
    RUN_FAILED("FAILED_RUN", true),
    PARSING_FAILURE("FAILED_PARSING", true),
    ASSERTION_FAILED("FAIL", true);

    fun generateArchiveNameFor(scenario: Scenario, counter: Int): String = buildString {
        append(prefix)
        append("__")
        append(scenario)
        if (counter > 0) {
            append("_")
            append(counter)
        }
        append(".zip")
    }

    companion object {
        fun fromFileName(fileName: String): RunStatus? {
            if (!fileName.contains("__")) {
                return UNDEFINED
            }

            return when (fileName.split("__").first()) {
                RUN_EXECUTED.prefix -> RUN_EXECUTED
                ASSERTION_SUCCESS.prefix -> ASSERTION_SUCCESS
                RUN_FAILED.prefix -> RUN_FAILED
                PARSING_FAILURE.prefix -> PARSING_FAILURE
                ASSERTION_FAILED.prefix -> ASSERTION_FAILED
                UNDEFINED.prefix -> UNDEFINED
                else -> null
            }
        }

        val ALL: List<RunStatus> = RunStatus.values().toList()
    }
}
