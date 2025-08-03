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

import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class TokenizerTest(private val params: Params) {

    data class Params(
        val input: String,
        val parenIsSep: Boolean = true,
        val expectedToken: String? = null,
        val expectedError: String? = null,
    ) {
        init {
            if (expectedToken == null && expectedError == null) {
                throw IllegalArgumentException(
                    "Expected one of `expectedToken` and `expectedError`, found neither"
                )
            } else if (expectedToken != null && expectedError != null) {
                throw IllegalArgumentException(
                    "Expected one of `expectedToken` and `expectedError`, found both"
                )
            }
        }

        override fun toString(): String = input
    }

    companion object {
        private val params =
            listOf(
                Params(
                    input = """  "string"  """,
                    expectedToken = """"string"""",
                ),
                Params(
                    input = """  "string  """,
                    expectedError = """api.txt:1: Unexpected end of file for " starting at 1""",
                ),
                Params(
                    input = """  "string\""",
                    expectedError = """api.txt:1: Unexpected end of file for " starting at 1""",
                ),
                Params(
                    input = """ @pkg.Annotation("string") """,
                    parenIsSep = false,
                    expectedToken = """@pkg.Annotation("string")""",
                ),
                Params(
                    input = """ @pkg.Annotation("string """,
                    parenIsSep = false,
                    expectedError = """api.txt:1: Unexpected end of file for " starting at 1""",
                ),
            )

        @JvmStatic
        @Parameterized.Parameters(name = "#{index} -{0}")
        fun testParams(): List<Params> = params
    }

    @Test
    fun `check token`() {
        val tokenizer = Tokenizer(Path.of("api.txt"), params.input.toCharArray())

        fun requireToken(): String {
            return tokenizer.requireToken(parenIsSep = params.parenIsSep)
        }

        params.expectedError?.let { expectedError ->
            val exception = assertThrows(ApiParseException::class.java) { requireToken() }
            assertEquals(expectedError, exception.message)
        }

        params.expectedToken?.let { expectedToken ->
            val token = requireToken()
            assertEquals(expectedToken, token)
        }
    }
}
