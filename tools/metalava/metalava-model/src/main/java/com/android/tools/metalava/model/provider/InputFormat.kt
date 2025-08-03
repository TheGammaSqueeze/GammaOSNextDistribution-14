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

package com.android.tools.metalava.model.provider

import java.io.File

/** Possible input formats supported by the different model providers. */
enum class InputFormat(
    val extension: String,
) {
    /**
     * Signature text files.
     *
     * The files will end with `.txt`.
     */
    SIGNATURE(
        extension = "txt",
    ),

    /**
     * Java files.
     *
     * The files will end with `.java`.
     */
    JAVA(
        extension = "java",
    ),

    /**
     * Kotlin files.
     *
     * The files will end with `.kt`.
     */
    KOTLIN(
        extension = "kt",
    );

    fun combineWith(other: InputFormat): InputFormat {
        if (this == other) return this
        if (this == SIGNATURE || other == SIGNATURE) error("Cannot mix signature and source files")
        // When mixing Kotlin and Java then it should be treated as Kotlin as a Kotlin provider can
        // handle Java but the reverse is not true.
        return KOTLIN
    }

    companion object {
        fun fromFilename(path: String): InputFormat {
            val extension = File(path).extension
            return values().filter { it.extension == extension }.single()
        }
    }
}
