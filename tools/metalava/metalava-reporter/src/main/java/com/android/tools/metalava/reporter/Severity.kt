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

enum class Severity(private val displayName: String) {
    INHERIT("inherit"),
    HIDDEN("hidden"),

    /**
     * Information level are for issues that are informational only; may or may not be a problem.
     */
    INFO("info"),

    /**
     * Lint level means that we encountered inconsistent or broken documentation. These should be
     * resolved, but don't impact API compatibility.
     */
    LINT("lint"),

    /**
     * Warning level means that we encountered some incompatible or inconsistent API change. These
     * must be resolved to preserve API compatibility.
     */
    WARNING("warning"),

    /**
     * Error level means that we encountered severe trouble and were unable to output the requested
     * documentation.
     */
    ERROR("error");

    override fun toString(): String = displayName
}
