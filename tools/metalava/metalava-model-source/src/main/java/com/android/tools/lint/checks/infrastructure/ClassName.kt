/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.tools.lint.checks.infrastructure

import com.android.tools.metalava.model.source.utils.DOT_JAVA
import com.android.tools.metalava.model.source.utils.DOT_KT
import java.util.regex.Pattern

// Copy in metalava from lint to avoid compilation dependency directly on lint-tests

/**
 * A pair of package name and class name inferred from Java or Kotlin source code. The [source] is
 * the source code, and the [extension] is the file extension (including the leading dot) which
 * states whether this is a Kotlin source file, a Java source file, a Groovy source file, etc.
 */
class ClassName(source: String, extension: String = DOT_JAVA) {
    val packageName: String?
    val className: String?

    init {
        val withoutComments = stripComments(source, extension)
        packageName = getPackage(withoutComments)
        className = getClassName(withoutComments)
    }

    fun packageNameWithDefault() = packageName ?: ""
}

/** Strips line and block comments from the given Java or Kotlin source file. */
@Suppress("LocalVariableName")
fun stripComments(source: String, extension: String, stripLineComments: Boolean = true): String {
    val sb = StringBuilder(source.length)
    var state = 0
    val INIT = 0
    val INIT_SLASH = 1
    val LINE_COMMENT = 2
    val BLOCK_COMMENT = 3
    val BLOCK_COMMENT_ASTERISK = 4
    val BLOCK_COMMENT_SLASH = 5
    val IN_STRING = 6
    val IN_STRING_ESCAPE = 7
    val IN_CHAR = 8
    val AFTER_CHAR = 9
    var blockCommentDepth = 0
    for (c in source) {
        when (state) {
            INIT -> {
                when (c) {
                    '/' -> state = INIT_SLASH
                    '"' -> {
                        state = IN_STRING
                        sb.append(c)
                    }
                    '\'' -> {
                        state = IN_CHAR
                        sb.append(c)
                    }
                    else -> sb.append(c)
                }
            }
            INIT_SLASH -> {
                when {
                    c == '*' -> {
                        blockCommentDepth++
                        state = BLOCK_COMMENT
                    }
                    c == '/' && stripLineComments -> state = LINE_COMMENT
                    else -> {
                        state = INIT
                        sb.append('/') // because we skipped it in init
                        sb.append(c)
                    }
                }
            }
            LINE_COMMENT -> {
                when (c) {
                    '\n' -> state = INIT
                }
            }
            BLOCK_COMMENT -> {
                when (c) {
                    '*' -> state = BLOCK_COMMENT_ASTERISK
                    '/' -> state = BLOCK_COMMENT_SLASH
                }
            }
            BLOCK_COMMENT_ASTERISK -> {
                state =
                    when (c) {
                        '/' -> {
                            blockCommentDepth--
                            if (blockCommentDepth == 0) {
                                INIT
                            } else {
                                BLOCK_COMMENT
                            }
                        }
                        '*' -> BLOCK_COMMENT_ASTERISK
                        else -> BLOCK_COMMENT
                    }
            }
            BLOCK_COMMENT_SLASH -> {
                if (c == '*' && extension == DOT_KT) {
                    blockCommentDepth++
                }
                if (c != '/') {
                    state = BLOCK_COMMENT
                }
            }
            IN_STRING -> {
                when (c) {
                    '\\' -> state = IN_STRING_ESCAPE
                    '"' -> state = INIT
                }
                sb.append(c)
            }
            IN_STRING_ESCAPE -> {
                sb.append(c)
                state = IN_STRING
            }
            IN_CHAR -> {
                if (c != '\\') {
                    state = AFTER_CHAR
                }
                sb.append(c)
            }
            AFTER_CHAR -> {
                sb.append(c)
                if (c == '\\') {
                    state = INIT
                }
            }
        }
    }

    return sb.toString()
}

private val PACKAGE_PATTERN = Pattern.compile("""package\s+([\S&&[^;]]*)""")

private val CLASS_PATTERN =
    Pattern.compile(
        """(\bclass\b|\binterface\b|\benum class\b|\benum\b|\bobject\b)+?\s*([^\s:(]+)""",
        Pattern.MULTILINE
    )

fun getPackage(source: String): String? {
    val matcher = PACKAGE_PATTERN.matcher(source)
    return if (matcher.find()) {
        matcher.group(1).trim { it <= ' ' }
    } else {
        null
    }
}

fun getClassName(source: String): String? {
    val matcher = CLASS_PATTERN.matcher(source.replace('\n', ' '))
    var start = 0
    while (matcher.find(start)) {
        val cls = matcher.group(2)
        val groupStart = matcher.start(1)

        // Make sure this "class" reference isn't part of an annotation on the class
        // referencing a class literal -- Foo.class, or in Kotlin, Foo::class.java)
        if (groupStart == 0 || source[groupStart - 1] != '.' && source[groupStart - 1] != ':') {
            val trimmed = cls.trim { it <= ' ' }
            val typeParameter = trimmed.indexOf('<')
            return if (typeParameter != -1) {
                trimmed.substring(0, typeParameter)
            } else {
                trimmed
            }
        }
        start = matcher.end(2)
    }

    return null
}
