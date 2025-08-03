/*
 * Copyright (C) 2019 The Android Open Source Project
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

import java.io.LineNumberReader
import java.io.StringReader
import java.nio.file.Path
import kotlin.test.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

val DEFAULTABLE_PROPERTY_NAMES =
    listOf(
        "add-additional-overrides",
        "overloaded-method-order",
        "sort-whole-extends-list",
    )

val DEFAULTABLE_PROPERTIES = DEFAULTABLE_PROPERTY_NAMES.joinToString { "'$it'" }

class FileFormatTest {
    private fun checkParseHeader(
        apiText: String,
        formatForLegacyFiles: FileFormat? = null,
        expectedFormat: FileFormat? = null,
        expectedError: String? = null,
        expectedNextLine: String? = null
    ) {
        val reader = LineNumberReader(StringReader(apiText.trimIndent()))
        val parseHeader = {
            FileFormat.parseHeader(Path.of("api.txt"), reader, formatForLegacyFiles)
        }
        if (expectedError == null) {
            val format = parseHeader()
            assertEquals(expectedFormat, format)
            val nextLine = reader.readLine()
            assertEquals(expectedNextLine, nextLine, "next line mismatch")
        } else {
            assertNull("cannot specify both expectedFormat and expectedError", expectedFormat)
            val e = assertThrows(ApiParseException::class.java) { parseHeader() }
            assertEquals(expectedError, e.message)
        }
    }

    /**
     * Tests that the [header] and [specifier] can be parsed to produce the [format] and vice versa.
     */
    private fun headerAndSpecifierTest(
        header: String,
        specifier: String,
        format: FileFormat,
    ) {
        assertEquals(header.trimIndent(), format.header(), message = "header does not match format")
        assertEquals(specifier, format.specifier(), message = "specifier does not match format")

        val reader = LineNumberReader(StringReader(header.trimIndent()))
        assertEquals(
            format,
            FileFormat.parseHeader(Path.of("api.txt"), reader),
            message = "format parsed from header does not match"
        )
        val nextLine = reader.readLine()
        assertNull("next line mismatch", nextLine)

        assertEquals(
            format,
            FileFormat.parseSpecifier(
                specifier,
                migratingAllowed = true,
                extraVersions = emptySet()
            ),
            message = "format parsed from specifier does not match"
        )
    }

    @Test
    fun `Check format parsing, blank line between header and package`() {
        checkParseHeader(
            """
                // Signature format: 2.0

                package test.pkg {
            """,
            expectedFormat = FileFormat.V2,
            expectedNextLine = "",
        )
    }

    @Test
    fun `Check format parsing, comment after header and package`() {
        checkParseHeader(
            """
                // Signature format: 2.0
                // Some manually added comment
            """,
            expectedFormat = FileFormat.V2,
            expectedNextLine = "// Some manually added comment",
        )
    }

    @Test
    fun `Check format parsing (v1)`() {
        checkParseHeader(
            """
                package test.pkg {
                  public class MyTest {
                    ctor public MyTest();
                  }
                }
            """,
            expectedError =
                "api.txt:1: Signature format error - invalid prefix, found 'package test.pkg {', expected '// Signature format: '",
        )
    }

    @Test
    fun `Check format parsing (v1 + legacy format)`() {
        checkParseHeader(
            """
                package test.pkg {
                  public class MyTest {
                    ctor public MyTest();
                  }
                }
            """,
            formatForLegacyFiles = FileFormat.V2,
            expectedFormat = FileFormat.V2,
            expectedNextLine = "package test.pkg {",
        )
    }

    @Test
    fun `Check format parsing (unknown version)`() {
        checkParseHeader(
            """
                // Signature format: 3.14
                package test.pkg {
                  public class MyTest {
                    ctor public MyTest();
                  }
                }
                """,
            expectedError =
                "api.txt:1: Signature format error - invalid version, found '3.14', expected one of '2.0', '3.0', '4.0', '5.0'",
        )
    }

    @Test
    fun `Check format parsing (v2)`() {
        checkParseHeader(
            """
                // Signature format: 2.0
                package libcore.util {
                  @java.lang.annotation.Documented @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.SOURCE) public @interface NonNull {
                    method public abstract int from() default java.lang.Integer.MIN_VALUE;
                    method public abstract int to() default java.lang.Integer.MAX_VALUE;
                  }
                }
            """,
            expectedFormat = FileFormat.V2,
            expectedNextLine = "package libcore.util {",
        )
    }

    @Test
    fun `Check format parsing (v3)`() {
        checkParseHeader(
            """
                // Signature format: 3.0
                package androidx.collection {
                  public final class LruCacheKt {
                    ctor public LruCacheKt();
                  }
                }
            """,
            expectedFormat = FileFormat.V3,
            expectedNextLine = "package androidx.collection {",
        )
    }

    @Test
    fun `Check format parsing (v2 non-unix newlines)`() {
        checkParseHeader(
            "" +
                "// Signature format: 2.0\r\n" +
                "package libcore.util {\r\n" +
                "  @java.lang.annotation.Documented @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.SOURCE) public @interface NonNull {\r\n" +
                "    method public abstract int from() default java.lang.Integer.MIN_VALUE;\r\n" +
                "    method public abstract int to() default java.lang.Integer.MAX_VALUE;\r\n" +
                "  }\r\n" +
                "}\r\n",
            expectedFormat = FileFormat.V2,
            expectedNextLine = "package libcore.util {",
        )
    }

    @Test
    fun `Check format parsing, shortened prefix (v2 non-unix newlines)`() {
        checkParseHeader(
            "" +
                "// Signature for\r\n" +
                "package libcore.util {\r\n" +
                "  @java.lang.annotation.Documented @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.SOURCE) public @interface NonNull {\r\n" +
                "    method public abstract int from() default java.lang.Integer.MIN_VALUE;\r\n" +
                "    method public abstract int to() default java.lang.Integer.MAX_VALUE;\r\n" +
                "  }\r\n" +
                "}\r\n",
            expectedError =
                "api.txt:1: Signature format error - invalid prefix, found '// Signature for', expected '// Signature format: '",
        )
    }

    @Test
    fun `Check format parsing (invalid)`() {
        checkParseHeader(
            """
                blah blah
            """,
            expectedError =
                "api.txt:1: Signature format error - invalid prefix, found 'blah blah', expected '// Signature format: '",
        )
    }

    @Test
    fun `Check format parsing (blank)`() {
        checkParseHeader("")
    }

    @Test
    fun `Check format parsing (blank - multiple lines)`() {
        checkParseHeader(
            """



            """,
        )
    }

    @Test
    fun `Check format parsing (not blank, multiple lines of white space, then some text)`() {
        checkParseHeader(
            """


                blah blah
            """,
            expectedError =
                "api.txt:1: Signature format error - invalid prefix, found '', expected '// Signature format: '",
        )
    }

    @Test
    fun `Check format parsing (v3 + kotlin-style-nulls=no but no migrating)`() {
        checkParseHeader(
            """
                // Signature format: 3.0
                // - kotlin-style-nulls=no
            """,
            expectedError =
                "api.txt:2: Signature format error - must provide a 'migrating' property when customizing version 3.0",
        )
    }

    @Test
    fun `Check header and specifier (v3 + kotlin-style-nulls=no,migrating=test)`() {
        headerAndSpecifierTest(
            header =
                """
                // Signature format: 3.0
                // - kotlin-style-nulls=no
                // - migrating=test

            """,
            specifier = "3.0:kotlin-style-nulls=no,migrating=test",
            format = FileFormat.V3.copy(kotlinStyleNulls = false, migrating = "test"),
        )
    }

    @Test
    fun `Check header and specifier (v2 + kotlin-style-nulls=yes,migrating=test)`() {
        headerAndSpecifierTest(
            header =
                """
                // Signature format: 2.0
                // - kotlin-style-nulls=yes
                // - migrating=test

            """,
            specifier = "2.0:kotlin-style-nulls=yes,migrating=test",
            format = FileFormat.V2.copy(kotlinStyleNulls = true, migrating = "test"),
        )
    }

    @Test
    fun `Check header and specifier (v5)`() {
        headerAndSpecifierTest(
            header = """
                // Signature format: 5.0

            """,
            specifier = "5.0",
            format = FileFormat.V5,
        )
    }

    @Test
    fun `Check format parsing (v5) - no properties with package`() {
        checkParseHeader(
            """
                // Signature format: 5.0
                package fred {
            """,
            expectedFormat = FileFormat.V5,
            expectedNextLine = "package fred {",
        )
    }

    @Test
    fun `Check format parsing (v5) - invalid property`() {
        checkParseHeader(
            """
                // Signature format: 5.0
                // - foo=fred
                package fred {
            """,
            expectedError =
                "api.txt:2: Signature format error - unknown format property name `foo`, expected one of $FILE_FORMAT_PROPERTIES"
        )
    }

    @Test
    fun `Check format parsing (v5) - kotlin-style-nulls property`() {
        checkParseHeader(
            """
                // Signature format: 5.0
                // - kotlin-style-nulls=no
                package fred {
            """,
            expectedFormat = FileFormat.V5.copy(kotlinStyleNulls = false),
            expectedNextLine = "package fred {",
        )
    }

    @Test
    fun `Check header and specifier (v2)`() {
        headerAndSpecifierTest(
            header = """
                // Signature format: 2.0

            """,
            specifier = "2.0",
            format = FileFormat.V2,
        )
    }

    @Test
    fun `Check header and specifier (v2 + kotlin-style-nulls=yes + migrating=test)`() {
        headerAndSpecifierTest(
            header =
                """
                // Signature format: 2.0
                // - kotlin-style-nulls=yes
                // - migrating=test

            """,
            specifier = "2.0:kotlin-style-nulls=yes,migrating=test",
            format = FileFormat.V2.copy(kotlinStyleNulls = true, migrating = "test")
        )
    }

    @Test
    fun `Check header and specifier (v3 + kotlin-style-nulls=no)`() {
        headerAndSpecifierTest(
            header =
                """
                // Signature format: 3.0
                // - kotlin-style-nulls=no
                // - migrating=test

            """,
            specifier = "3.0:kotlin-style-nulls=no,migrating=test",
            format = FileFormat.V3.copy(kotlinStyleNulls = false, migrating = "test"),
        )
    }

    @Test
    fun `Check header (v2 + overloaded-method-order=source but no migrating)`() {
        assertEquals(
            // The full specifier is only output when migrating is specified.
            "// Signature format: 2.0\n",
            FileFormat.V2.copy(
                    specifiedOverloadedMethodOrder = FileFormat.OverloadedMethodOrder.SOURCE,
                )
                .header()
        )
    }

    @Test
    fun `Check no ',' in migrating`() {
        val e =
            assertThrows(IllegalStateException::class.java) {
                @Suppress("UnusedDataClassCopyResult") FileFormat.V2.copy(migrating = "a,b")
            }
        assertEquals(
            """invalid value for property 'migrating': 'a,b' contains at least one invalid character from the set {',', '\n'}""",
            e.message
        )
    }

    @Test
    fun `Check header and specifier (v5 + overloaded-method-order=source)`() {
        headerAndSpecifierTest(
            header =
                """
                // Signature format: 5.0
                // - overloaded-method-order=source

            """,
            specifier = "5.0:overloaded-method-order=source",
            format =
                FileFormat.V5.copy(
                    specifiedOverloadedMethodOrder = FileFormat.OverloadedMethodOrder.SOURCE,
                ),
        )
    }

    @Test
    fun `Check header and specifier (v5 + overloaded-method-order=source,migrating=test)`() {
        headerAndSpecifierTest(
            header =
                """
                // Signature format: 5.0
                // - migrating=test
                // - overloaded-method-order=source

            """,
            specifier = "5.0:migrating=test,overloaded-method-order=source",
            format =
                FileFormat.V5.copy(
                    specifiedOverloadedMethodOrder = FileFormat.OverloadedMethodOrder.SOURCE,
                    migrating = "test",
                ),
        )
    }

    @Test
    fun `Check header and specifier (v5 + language=java)`() {
        headerAndSpecifierTest(
            header =
                """
                // Signature format: 5.0
                // - language=java

            """,
            specifier = "5.0:language=java",
            format =
                FileFormat.V5.copy(
                    language = FileFormat.Language.JAVA,
                    conciseDefaultValues = false,
                    kotlinStyleNulls = false,
                ),
        )
    }

    @Test
    fun `Check header and specifier (v5 + kotlin-style-nulls=yes,language=java)`() {
        headerAndSpecifierTest(
            header =
                """
                // Signature format: 5.0
                // - language=java
                // - kotlin-style-nulls=yes

            """,
            specifier = "5.0:language=java,kotlin-style-nulls=yes",
            format =
                FileFormat.V5.copy(
                    language = FileFormat.Language.JAVA,
                    conciseDefaultValues = false,
                    kotlinStyleNulls = true,
                ),
        )
    }

    @Test
    fun `Check header and specifier (v5 + language=kotlin)`() {
        headerAndSpecifierTest(
            header =
                """
                // Signature format: 5.0
                // - language=kotlin

            """,
            specifier = "5.0:language=kotlin",
            format =
                FileFormat.V5.copy(
                    language = FileFormat.Language.KOTLIN,
                ),
        )
    }

    @Test
    fun `Check header and specifier (v5 + concise-default-values=no,language=kotlin)`() {
        headerAndSpecifierTest(
            header =
                """
                // Signature format: 5.0
                // - language=kotlin
                // - concise-default-values=no

            """,
            specifier = "5.0:language=kotlin,concise-default-values=no",
            format =
                FileFormat.V5.copy(
                    language = FileFormat.Language.KOTLIN,
                    conciseDefaultValues = false,
                ),
        )
    }

    @Test
    fun `Check header and specifier (v5 + kotlinNameTypeOrder=yes)`() {
        headerAndSpecifierTest(
            header =
                """
                // Signature format: 5.0
                // - kotlin-name-type-order=yes

            """,
            specifier = "5.0:kotlin-name-type-order=yes",
            format =
                FileFormat.V5.copy(
                    kotlinNameTypeOrder = true,
                ),
        )
    }

    @Test
    fun `Check header and specifier (v5 + kotlin-name-type-order=yes,kotlin-name-type-order=yes)`() {
        headerAndSpecifierTest(
            header =
                """
                // Signature format: 5.0
                // - include-type-use-annotations=yes
                // - kotlin-name-type-order=yes

            """,
            specifier = "5.0:include-type-use-annotations=yes,kotlin-name-type-order=yes",
            format =
                FileFormat.V5.copy(kotlinNameTypeOrder = true, includeTypeUseAnnotations = true),
        )
    }

    @Test
    fun `Check that include-type-use-annotations=yes cannot be set without kotlin-name-type-order=yes`() {
        val e =
            assertThrows(IllegalStateException::class.java) {
                checkParseHeader(
                    """
                    // Signature format: 5.0
                    // - kotlin-name-type-order=no
                    // - include-type-use-annotations=yes
                """
                        .trimIndent()
                )
            }
        assertEquals(
            "Type-use annotations can only be included in signatures when `kotlin-name-type-order=yes` is set",
            e.message
        )
    }

    @Test
    fun `Check name with valid and invalid values`() {
        fun checkValidName(name: String) {
            headerAndSpecifierTest(
                header =
                    """
                // Signature format: 5.0
                // - name=$name

            """,
                specifier = "5.0:name=$name",
                format =
                    FileFormat.V5.copy(
                        name = name,
                    ),
            )
        }

        fun checkInvalidName(name: String) {
            val e =
                assertThrows(IllegalStateException::class.java) {
                    @Suppress("UnusedDataClassCopyResult") FileFormat.V5.copy(name = name)
                }

            assertEquals(
                """invalid value for property 'name': '$name' must start with a lower case letter, contain any number of lower case letters, numbers and hyphens, and end with either a lowercase letter or number""",
                e.message
            )
        }

        checkValidName("a")
        checkValidName("a1")
        checkValidName("a--1")
        checkValidName("large-name")

        checkInvalidName("")
        checkInvalidName("1")
        checkInvalidName("-")
        checkInvalidName("a-")
        checkInvalidName("aBa")
    }

    @Test
    fun `Check surface with valid and invalid values`() {
        fun checkValidSurface(surface: String) {
            headerAndSpecifierTest(
                header =
                    """
                // Signature format: 5.0
                // - surface=$surface

            """,
                specifier = "5.0:surface=$surface",
                format =
                    FileFormat.V5.copy(
                        surface = surface,
                    ),
            )
        }

        fun checkInvalidSurface(surface: String) {
            val e =
                assertThrows(IllegalStateException::class.java) {
                    @Suppress("UnusedDataClassCopyResult") FileFormat.V5.copy(surface = surface)
                }

            assertEquals(
                """invalid value for property 'surface': '$surface' must start with a lower case letter, contain any number of lower case letters, numbers and hyphens, and end with either a lowercase letter or number""",
                e.message
            )
        }

        checkValidSurface("a")
        checkValidSurface("a1")
        checkValidSurface("a--1")
        checkValidSurface("large-surface")

        checkInvalidSurface("")
        checkInvalidSurface("1")
        checkInvalidSurface("-")
        checkInvalidSurface("a-")
        checkInvalidSurface("aBa")
    }

    @Test
    fun `Check defaultable properties`() {
        assertEquals(DEFAULTABLE_PROPERTY_NAMES, FileFormat.defaultableProperties())
    }

    @Test
    fun `Check parseDefaults overloaded-method-order=source`() {
        val defaults = FileFormat.parseDefaults("overloaded-method-order=source")
        assertEquals(
            FileFormat.OverloadedMethodOrder.SOURCE,
            defaults.specifiedOverloadedMethodOrder
        )
    }

    @Test
    fun `Check parseDefaults kotlin-style-nulls=yes`() {
        val e =
            assertThrows(ApiParseException::class.java) {
                FileFormat.parseDefaults("kotlin-style-nulls=yes")
            }
        assertEquals(
            "unknown format property name `kotlin-style-nulls`, expected one of $DEFAULTABLE_PROPERTIES",
            e.message
        )
    }

    @Test
    fun `Check parseDefaults foo=bar`() {
        val e = assertThrows(ApiParseException::class.java) { FileFormat.parseDefaults("foo=bar") }
        assertEquals(
            "unknown format property name `foo`, expected one of $DEFAULTABLE_PROPERTIES",
            e.message
        )
    }

    @Test
    fun `Check defaults are not written to header or specifier`() {
        val defaults = FileFormat.parseDefaults("add-additional-overrides=yes")
        val format = FileFormat.V5.copy(formatDefaults = defaults)
        // Defaults should not be written to the header or specifier.
        assertEquals(
            """
                // Signature format: 5.0

            """
                .trimIndent(),
            format.header()
        )
        assertEquals("5.0", format.specifier())
    }
}
