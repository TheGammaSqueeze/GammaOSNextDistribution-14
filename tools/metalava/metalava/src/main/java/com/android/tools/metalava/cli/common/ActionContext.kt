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

import com.android.tools.metalava.ProgressTracker
import com.android.tools.metalava.model.source.SourceParser
import com.android.tools.metalava.reporter.Reporter

/**
 * Contextual information needed by various actions performed by different commands.
 *
 * This avoids having to pass all the information in as parameters while still allowing the methods
 * to be used from multiple commands.
 */
data class ActionContext(
    val progressTracker: ProgressTracker,
    val reporter: Reporter,
    val reporterApiLint: Reporter,
    val sourceParser: SourceParser,
)
