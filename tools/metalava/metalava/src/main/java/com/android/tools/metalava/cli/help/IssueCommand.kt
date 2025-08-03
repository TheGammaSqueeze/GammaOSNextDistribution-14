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

import com.android.tools.metalava.cli.common.stdout
import com.android.tools.metalava.reporter.Issues.Issue
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.context
import java.util.Locale

class IssueCommand(val issue: Issue) :
    CliktCommand(
        name = issue.name,
        help =
            formatDataColumns(
                issue.category.name.lowercase(Locale.US),
                issue.defaultLevel.name.lowercase(Locale.US),
                useNonBreakingSpace = true,
            ),
    ) {
    init {
        context {
            // Help options make no sense on a help command.
            helpOptionNames = emptySet()
        }
    }

    override fun run() {
        stdout.println("Under construction. No additional help available at the moment.")
    }
}
