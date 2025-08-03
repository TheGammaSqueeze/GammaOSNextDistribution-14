/*
 * Copyright (C) 2024 The Android Open Source Project
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

/**
 * An object for which issues can be reported.
 *
 * See [Reporter] for more details.
 */
interface Reportable {
    /** The file location for this object. */
    val fileLocation: FileLocation

    /** The location for this object that should be used for reporting issues about this object. */
    val issueLocation: IssueLocation

    /**
     * Get the set of suppressed issues for this.
     *
     * Each value could be just the name of an issue in which case all issues of that type are
     * suppressed. Or, it could be the name of the issue followed by ":" or ": " and the full
     * message in which case only the issue with that specific message is suppressed.
     */
    fun suppressedIssues(): Set<String>
}
