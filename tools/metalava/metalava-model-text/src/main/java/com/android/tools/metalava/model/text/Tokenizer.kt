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

import com.android.tools.metalava.reporter.FileLocation
import java.nio.file.Path

/**
 * Extracts tokens from a sequence of characters.
 *
 * The tokens are not the usual sort of tokens created by a tokenizer, e.g. some tokens contain
 * white spaces and even whole strings. e.g. an annotation, including parameters if present, can be
 * returned as a single token, if requested (e.g. by calling [requireToken] with
 * `parenIsSep=false`).
 */
internal class Tokenizer(val path: Path, private val buffer: CharArray) {
    var position = 0
    var line = 1

    fun fileLocation(): FileLocation {
        return FileLocation.createLocation(path, line)
    }

    private fun eatWhitespace(): Boolean {
        var ate = false
        while (position < buffer.size && isSpace(buffer[position])) {
            if (buffer[position] == '\n') {
                line++
            }
            position++
            ate = true
        }
        return ate
    }

    private fun eatComment(): Boolean {
        if (position + 1 < buffer.size) {
            if (buffer[position] == '/' && buffer[position + 1] == '/') {
                position += 2
                while (position < buffer.size && !isNewline(buffer[position])) {
                    position++
                }
                return true
            }
        }
        return false
    }

    private fun eatWhitespaceAndComments() {
        while (eatWhitespace() || eatComment()) {
            // intentionally consume whitespace and comments
        }
    }

    fun requireToken(parenIsSep: Boolean = true, eatWhitespace: Boolean = true): String {
        val token = getToken(parenIsSep, eatWhitespace)
        return token ?: throw ApiParseException("Unexpected end of file", this)
    }

    fun offset(): Int {
        return position
    }

    fun getStringFromOffset(offset: Int): String {
        return String(buffer, offset, position - offset)
    }

    lateinit var current: String

    fun getToken(parenIsSep: Boolean = true, eatWhitespace: Boolean = true): String? {
        if (eatWhitespace) {
            eatWhitespaceAndComments()
        }
        if (position >= buffer.size) {
            return null
        }
        val line = line
        val c = buffer[position]
        val start = position
        position++
        if (c == '"') {
            val STATE_BEGIN = 0
            val STATE_ESCAPE = 1
            var state = STATE_BEGIN
            while (true) {
                if (position >= buffer.size) {
                    throw ApiParseException("Unexpected end of file for \" starting at $line", this)
                }
                val k = buffer[position]
                if (k == '\n' || k == '\r') {
                    throw ApiParseException(
                        "Unexpected newline for \" starting at $line in $path",
                        this
                    )
                }
                position++
                when (state) {
                    STATE_BEGIN ->
                        when (k) {
                            '\\' -> state = STATE_ESCAPE
                            '"' -> {
                                current = String(buffer, start, position - start)
                                return current
                            }
                        }
                    STATE_ESCAPE -> state = STATE_BEGIN
                }
            }
        } else if (isSeparator(c, parenIsSep)) {
            current = c.toString()
            return current
        } else {
            var genericDepth = 0
            do {
                while (position < buffer.size) {
                    val d = buffer[position]
                    if (isSpace(d) || isSeparator(d, parenIsSep)) {
                        break
                    } else if (d == '"') {
                        // String literal in token: skip the full thing
                        position++
                        while (position < buffer.size) {
                            if (buffer[position] == '"') {
                                position++
                                break
                            } else if (buffer[position] == '\\') {
                                position++
                            }
                            position++
                        }
                        continue
                    }
                    position++
                }
                if (position < buffer.size) {
                    if (buffer[position] == '<') {
                        genericDepth++
                        position++
                    } else if (genericDepth != 0) {
                        if (buffer[position] == '>') {
                            genericDepth--
                        }
                        position++
                    }
                }
            } while (
                position < buffer.size &&
                    (!isSpace(buffer[position]) && !isSeparator(buffer[position], parenIsSep) ||
                        genericDepth != 0)
            )
            if (position >= buffer.size) {
                throw ApiParseException("Unexpected end of file for \" starting at $line", this)
            }
            current = String(buffer, start, position - start)
            return current
        }
    }

    internal fun assertIdent(token: String) {
        if (!isIdent(token[0])) {
            throw ApiParseException("Expected identifier: $token", this)
        }
    }

    companion object {
        private fun isSpace(c: Char): Boolean {
            return c == ' ' || c == '\t' || c == '\n' || c == '\r'
        }

        private fun isNewline(c: Char): Boolean {
            return c == '\n' || c == '\r'
        }

        private fun isSeparator(c: Char, parenIsSep: Boolean): Boolean {
            if (parenIsSep) {
                if (c == '(' || c == ')') {
                    return true
                }
            }
            return c == '{' || c == '}' || c == ',' || c == ';' || c == '<' || c == '>'
        }

        private fun isIdent(c: Char): Boolean {
            return c != '"' && !isSeparator(c, true)
        }

        internal fun isIdent(token: String): Boolean {
            return isIdent(token[0])
        }
    }
}
