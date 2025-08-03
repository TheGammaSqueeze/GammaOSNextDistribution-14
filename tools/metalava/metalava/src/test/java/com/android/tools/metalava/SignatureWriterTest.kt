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

package com.android.tools.metalava

import com.android.tools.metalava.model.text.FileFormat
import com.android.tools.metalava.model.visitors.ApiVisitor
import java.io.PrintWriter
import java.io.StringWriter
import kotlin.test.assertEquals
import org.junit.Test

class SignatureWriterTest {

    private fun runTest(
        fileFormat: FileFormat = FileFormat.V2,
        body: (SignatureWriter) -> Unit,
    ): String {
        val output =
            StringWriter().use {
                PrintWriter(it).use {
                    val writer =
                        SignatureWriter(
                            writer = it,
                            filterEmit = { true },
                            filterReference = { true },
                            preFiltered = false,
                            emitHeader = EmitFileHeader.IF_NONEMPTY_FILE,
                            fileFormat = fileFormat,
                            showUnannotated = false,
                            apiVisitorConfig = ApiVisitor.Config(),
                        )
                    body(writer)
                }
                it.toString()
            }
        return output
    }

    @Test
    fun `Write header only when not-empty (not-empty)`() {
        val output = runTest {
            it.write("text1\n")
            it.write("text2\n")
        }

        assertEquals(
            """
            // Signature format: 2.0
            text1
            text2

        """
                .trimIndent(),
            output
        )
    }

    @Test
    fun `Write header only when not-empty (empty)`() {
        val output = runTest {}
        assertEquals("", output)
    }
}
