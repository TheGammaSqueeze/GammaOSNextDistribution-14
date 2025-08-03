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

package com.android.tools.metalava.model.text

import org.junit.Assert.assertEquals

/** Verify that two signature files match. */
fun assertSignatureFilesMatch(
    expected: String,
    actual: String,
    expectedFormat: FileFormat = FileFormat.LATEST,
    message: String? = null
) {
    val expectedPrepared = prepareSignatureFileForTest(expected, expectedFormat)
    val actualStripped = actual.stripBlankLines()
    assertEquals(message, expectedPrepared, actualStripped)
}

fun String.stripBlankLines() = lines().filter { it.isNotBlank() }.joinToString("\n")

/** Strip comments, trim indent, and add a signature format version header if one is missing */
fun prepareSignatureFileForTest(expectedApi: String, format: FileFormat): String {
    val header = format.header()

    return expectedApi
        .trimIndent()
        .let { if (!it.startsWith(FileFormat.SIGNATURE_FORMAT_PREFIX)) header + it else it }
        .trim()
}
