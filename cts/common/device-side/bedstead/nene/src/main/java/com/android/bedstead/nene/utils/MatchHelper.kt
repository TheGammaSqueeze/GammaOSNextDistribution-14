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

package com.android.bedstead.nene.utils
import com.android.bedstead.nene.exceptions.AdbParseException

/**
 * Matches and returns first occurrence of pattern or, if not found, empty string
 */
fun String.matchFirst(pattern: Regex): String {
    val results = this.matchAll(pattern)
    if (results.isNotEmpty()) {
        return this.matchAll(pattern).first()
    } else {
        return ""
    }
}
/**
 * Matches and returns all occurrences of pattern or, if not found, empty string list
 */
fun String.matchAll(pattern: Regex): List<String> {
    if (this.contains(pattern)) {
        try {
            pattern.find(this)?.let {firstMatch ->
                val results = mutableListOf<String>()
                var match: MatchResult? = firstMatch
                while (match != null) {
                    results.add(match.groupValues[1])
                    match = match.next()
                }
                return results
            } ?: run {
                return listOf<String>()
            }
        } catch (e: IndexOutOfBoundsException) {
            throw AdbParseException("Error parsing content of \"$pattern\"", this, e)
        }
    } else {
        return listOf<String>()
    }
}
