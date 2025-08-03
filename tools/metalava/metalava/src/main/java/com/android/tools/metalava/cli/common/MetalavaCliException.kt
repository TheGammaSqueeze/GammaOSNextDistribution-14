/*
 * Copyright (C) 2018 The Android Open Source Project
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

/** Exceptions thrown by metalava CLI code. */
class MetalavaCliException(
    /** The text that must be output to stderr, if it is blank then nothing is to be output. */
    val stderr: String = "",

    /** The text that must be output to stdout, if it is blank then nothing is to be output. */
    val stdout: String = "",

    /**
     * The exit code for the process to return, defaults to `-1` if [stderr] is not blank, otherwise
     * defaults to `0`.
     */
    val exitCode: Int = if (stderr.isBlank()) 0 else -1,

    /** Optional cause. */
    cause: Throwable? = null,
) : RuntimeException(stdout + stderr, cause)
