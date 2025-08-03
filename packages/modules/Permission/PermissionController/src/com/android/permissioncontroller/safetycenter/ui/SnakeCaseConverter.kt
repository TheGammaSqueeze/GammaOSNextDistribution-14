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

package com.android.permissioncontroller.safetycenter.ui

/** Class used to convert a [String] to the `snake_case` format */
object SnakeCaseConverter {

    /** Converts a [String] from `camelCase` to `snake_case` */
    fun fromCamelCase(input: String): String {
        if (isFullyCapitalized(input)) {
            return input.lowercase()
        }

        val camelRegex = "(?<=[a-zA-Z])[A-Z]".toRegex()
        return camelRegex.replace(input) { "_${it.value}" }.lowercase()
    }

    private fun isFullyCapitalized(input: String): Boolean {
        val uppercaseInput = input.uppercase()
        if (input == uppercaseInput) {
            return true
        }
        return false
    }
}
