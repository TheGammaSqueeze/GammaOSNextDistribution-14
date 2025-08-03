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

package com.android.tools.metalava.cli.signature

import com.android.tools.metalava.cli.common.BaseCommandTest
import com.android.tools.metalava.cli.common.CommandTestConfig
import com.android.tools.metalava.model.text.FileFormat
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import org.junit.Test

private val signatureToJdiffHelp =
    """
Usage: metalava signature-to-jdiff [options] <api-file> <xml-file>

  Convert an API signature file into a file in the JDiff XML format.

Options:
  --strip / --no-strip                       Determines whether duplicate inherited methods should be stripped from the
                                             output or not. (default: false)
  --format-for-legacy-files <format-specifier>
                                             Optional format to use when reading legacy, i.e. no longer supported,
                                             format versions. Forces the signature file to be parsed as if it was in
                                             this format.

                                             This is provided primarily to allow version 1.0 files, which had no header,
                                             to be parsed as if they were 2.0 files (by specifying
                                             `--format-for-legacy-files=2.0`) so that version 1.0 files can still be
                                             read even though metalava no longer supports version 1.0 files
                                             specifically. That is effectively what metalava did anyway before it
                                             removed support for version 1.0 files so should work reasonably well.

                                             Applies to both `--base-api` and `<api-file>`.
  --base-api <base-api-file>                 Optional base API file. If provided then the output will only include API
                                             items that are not in this file.
  -h, -?, --help                             Show this message and exit

Arguments:
  <api-file>                                 API signature file to convert to the JDiff XML format.
  <xml-file>                                 Output JDiff XML format file.
    """
        .trimIndent()

class SignatureToJDiffCommandTest :
    BaseCommandTest<SignatureToJDiffCommand>({ SignatureToJDiffCommand() }) {

    @Test
    fun `Test help`() {
        commandTest {
            args += listOf("signature-to-jdiff", "--help")

            expectedStdout = signatureToJdiffHelp
        }
    }

    @Test
    fun `Test invalid option`() {

        commandTest {
            args += listOf("signature-to-jdiff", "--trip")

            args += inputFile("input.txt", "").path
            args += outputFile("output.xml").path

            expectedStderr =
                """
Aborting: Error: no such option: "--trip". (Possible options: --strip, --no-strip)

$signatureToJdiffHelp
            """
                    .trimIndent()
        }
    }

    @Test
    fun `Test conversion flag class with constructor`() {
        jdiffConversionTest {
            strip = true

            api =
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public class MyTest1 {
                        ctor public MyTest1();
                      }
                    }
                """

            expectedXml =
                """
                    <api name="api" xmlns:metalava="http://www.android.com/metalava/">
                    <package name="test.pkg"
                    >
                    <class name="MyTest1"
                     extends="java.lang.Object"
                     abstract="false"
                     static="false"
                     final="false"
                     deprecated="not deprecated"
                     visibility="public"
                    >
                    <constructor name="MyTest1"
                     type="test.pkg.MyTest1"
                     static="false"
                     final="false"
                     deprecated="not deprecated"
                     visibility="public"
                    >
                    </constructor>
                    </class>
                    </package>
                    </api>
                """
        }
    }

    @Test
    fun `Test conversion flag empty class`() {
        jdiffConversionTest {
            strip = true

            api =
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public class MyTest2 {
                      }
                    }
                """

            expectedXml =
                """
                    <api name="api" xmlns:metalava="http://www.android.com/metalava/">
                    <package name="test.pkg"
                    >
                    <class name="MyTest2"
                     extends="java.lang.Object"
                     abstract="false"
                     static="false"
                     final="false"
                     deprecated="not deprecated"
                     visibility="public"
                    >
                    </class>
                    </package>
                    </api>
                """
        }
    }

    @Test
    fun `Test convert new with compat mode and api strip`() {
        jdiffConversionTest {
            strip = true

            api =
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public class MyTest1 {
                        ctor public MyTest1();
                        method public deprecated int clamp(int);
                        method public java.lang.Double convert(java.lang.Float);
                        field public static final java.lang.String ANY_CURSOR_ITEM_TYPE = "vnd.android.cursor.item/*";
                        field public deprecated java.lang.Number myNumber;
                      }
                      public class MyTest2 {
                        ctor public MyTest2();
                        method public java.lang.Double convert(java.lang.Float);
                      }
                    }
                    package test.pkg.new {
                      public interface MyInterface {
                      }
                      public abstract class MyTest3 implements java.util.List {
                      }
                      public abstract class MyTest4 implements test.pkg.new.MyInterface {
                      }
                    }
                """

            baseApi =
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public class MyTest1 {
                        ctor public MyTest1();
                        method public deprecated int clamp(int);
                        field public deprecated java.lang.Number myNumber;
                      }
                    }
                """

            expectedXml =
                """
                    <api name="api" xmlns:metalava="http://www.android.com/metalava/">
                    <package name="test.pkg"
                    >
                    <class name="MyTest1"
                     extends="java.lang.Object"
                     abstract="false"
                     static="false"
                     final="false"
                     deprecated="not deprecated"
                     visibility="public"
                    >
                    <method name="convert"
                     return="java.lang.Double"
                     abstract="false"
                     native="false"
                     synchronized="false"
                     static="false"
                     final="false"
                     deprecated="not deprecated"
                     visibility="public"
                    >
                    <parameter name="null" type="java.lang.Float">
                    </parameter>
                    </method>
                    <field name="ANY_CURSOR_ITEM_TYPE"
                     type="java.lang.String"
                     transient="false"
                     volatile="false"
                     value="&quot;vnd.android.cursor.item/*&quot;"
                     static="true"
                     final="true"
                     deprecated="not deprecated"
                     visibility="public"
                    >
                    </field>
                    </class>
                    <class name="MyTest2"
                     extends="java.lang.Object"
                     abstract="false"
                     static="false"
                     final="false"
                     deprecated="not deprecated"
                     visibility="public"
                    >
                    <constructor name="MyTest2"
                     type="test.pkg.MyTest2"
                     static="false"
                     final="false"
                     deprecated="not deprecated"
                     visibility="public"
                    >
                    </constructor>
                    <method name="convert"
                     return="java.lang.Double"
                     abstract="false"
                     native="false"
                     synchronized="false"
                     static="false"
                     final="false"
                     deprecated="not deprecated"
                     visibility="public"
                    >
                    <parameter name="null" type="java.lang.Float">
                    </parameter>
                    </method>
                    </class>
                    </package>
                    <package name="test.pkg.new"
                    >
                    <interface name="MyInterface"
                     abstract="true"
                     static="false"
                     final="false"
                     deprecated="not deprecated"
                     visibility="public"
                    >
                    </interface>
                    <class name="MyTest3"
                     extends="java.lang.Object"
                     abstract="true"
                     static="false"
                     final="false"
                     deprecated="not deprecated"
                     visibility="public"
                    >
                    </class>
                    <class name="MyTest4"
                     extends="java.lang.Object"
                     abstract="true"
                     static="false"
                     final="false"
                     deprecated="not deprecated"
                     visibility="public"
                    >
                    <implements name="test.pkg.new.MyInterface">
                    </implements>
                    </class>
                    </package>
                    </api>
                """
        }
    }

    @Test
    fun `Test convert new without compat mode and no strip`() {
        jdiffConversionTest {
            api =
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public class MyTest1 {
                        ctor public MyTest1();
                        method public deprecated int clamp(int);
                        method public java.lang.Double convert(java.lang.Float);
                        field public static final java.lang.String ANY_CURSOR_ITEM_TYPE = "vnd.android.cursor.item/*";
                        field public deprecated java.lang.Number myNumber;
                      }
                      public class MyTest2 {
                        ctor public MyTest2();
                        method public java.lang.Double convert(java.lang.Float);
                      }
                    }
                    package test.pkg.new {
                      public interface MyInterface {
                      }
                      public abstract class MyTest3 implements java.util.List {
                      }
                      public abstract class MyTest4 implements test.pkg.new.MyInterface {
                      }
                    }
                """

            baseApi =
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public class MyTest1 {
                        ctor public MyTest1();
                        method public deprecated int clamp(int);
                        field public deprecated java.lang.Number myNumber;
                      }
                    }
                """

            expectedXml =
                """
                    <api name="api" xmlns:metalava="http://www.android.com/metalava/">
                    <package name="test.pkg"
                    >
                    <class name="MyTest1"
                     extends="java.lang.Object"
                     abstract="false"
                     static="false"
                     final="false"
                     deprecated="not deprecated"
                     visibility="public"
                    >
                    <method name="convert"
                     return="java.lang.Double"
                     abstract="false"
                     native="false"
                     synchronized="false"
                     static="false"
                     final="false"
                     deprecated="not deprecated"
                     visibility="public"
                    >
                    <parameter name="null" type="java.lang.Float">
                    </parameter>
                    </method>
                    <field name="ANY_CURSOR_ITEM_TYPE"
                     type="java.lang.String"
                     transient="false"
                     volatile="false"
                     value="&quot;vnd.android.cursor.item/*&quot;"
                     static="true"
                     final="true"
                     deprecated="not deprecated"
                     visibility="public"
                    >
                    </field>
                    </class>
                    <class name="MyTest2"
                     extends="java.lang.Object"
                     abstract="false"
                     static="false"
                     final="false"
                     deprecated="not deprecated"
                     visibility="public"
                    >
                    <constructor name="MyTest2"
                     type="test.pkg.MyTest2"
                     static="false"
                     final="false"
                     deprecated="not deprecated"
                     visibility="public"
                    >
                    </constructor>
                    <method name="convert"
                     return="java.lang.Double"
                     abstract="false"
                     native="false"
                     synchronized="false"
                     static="false"
                     final="false"
                     deprecated="not deprecated"
                     visibility="public"
                    >
                    <parameter name="null" type="java.lang.Float">
                    </parameter>
                    </method>
                    </class>
                    </package>
                    <package name="test.pkg.new"
                    >
                    <interface name="MyInterface"
                     abstract="true"
                     static="false"
                     final="false"
                     deprecated="not deprecated"
                     visibility="public"
                    >
                    </interface>
                    <class name="MyTest3"
                     extends="java.lang.Object"
                     abstract="true"
                     static="false"
                     final="false"
                     deprecated="not deprecated"
                     visibility="public"
                    >
                    <implements name="java.util.List">
                    </implements>
                    </class>
                    <class name="MyTest4"
                     extends="java.lang.Object"
                     abstract="true"
                     static="false"
                     final="false"
                     deprecated="not deprecated"
                     visibility="public"
                    >
                    <implements name="test.pkg.new.MyInterface">
                    </implements>
                    </class>
                    </package>
                    </api>
                """
        }
    }

    @Test
    fun `Test convert nothing new`() {
        jdiffConversionTest {
            api =
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public class MyTest1 {
                        ctor public MyTest1();
                        method public deprecated int clamp(int);
                        method public java.lang.Double convert(java.lang.Float);
                        field public static final java.lang.String ANY_CURSOR_ITEM_TYPE = "vnd.android.cursor.item/*";
                        field public deprecated java.lang.Number myNumber;
                      }
                      public class MyTest2 {
                        ctor public MyTest2();
                        method public java.lang.Double convert(java.lang.Float);
                      }
                    }
                    package test.pkg.new {
                      public class MyTest3 {
                      }
                    }
                """

            baseApi =
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public class MyTest1 {
                        ctor public MyTest1();
                        method public deprecated int clamp(int);
                        method public java.lang.Double convert(java.lang.Float);
                        field public static final java.lang.String ANY_CURSOR_ITEM_TYPE = "vnd.android.cursor.item/*";
                        field public deprecated java.lang.Number myNumber;
                      }
                      public class MyTest2 {
                        ctor public MyTest2();
                        method public java.lang.Double convert(java.lang.Float);
                      }
                    }
                    package test.pkg.new {
                      public class MyTest3 {
                      }
                    }
                """

            expectedXml =
                """
                    <api name="api" xmlns:metalava="http://www.android.com/metalava/">
                    </api>
                """
        }
    }

    @Test
    fun `Test convert legacy file`() {
        jdiffConversionTest {
            api =
                """
                    package test.pkg {
                      public class Test {
                      }
                    }
                """

            expectedStderr =
                "Aborting: Unable to parse signature file: TESTROOT/jdiff-conversion/api.txt:1: Signature format error - invalid prefix, found 'package test.pkg {', expected '// Signature format: '"
        }
    }

    @Test
    fun `Test convert legacy base file`() {
        jdiffConversionTest {
            api =
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public class Test {
                        ctor public Test();
                      }
                    }
                """

            baseApi =
                """
                    package test.pkg {
                      public class Test {
                      }
                    }
                """

            expectedStderr =
                "Aborting: Unable to parse signature file: TESTROOT/jdiff-conversion/base-api.txt:1: Signature format error - invalid prefix, found 'package test.pkg {', expected '// Signature format: '"
        }
    }

    @Test
    fun `Test convert legacy files with --format-for-legacy-files`() {
        jdiffConversionTest {
            formatForLegacyFiles = FileFormat.V2

            api =
                """
                    package test.pkg {
                      public class Test extends Number {
                        ctor public Test();
                      }
                    }
                """

            baseApi =
                """
                    package test.pkg {
                      public class Test {
                      }
                    }
                """

            expectedXml =
                """
                    <api name="api" xmlns:metalava="http://www.android.com/metalava/">
                    <package name="test.pkg"
                    >
                    <class name="Test"
                     extends="java.lang.Number"
                     abstract="false"
                     static="false"
                     final="false"
                     deprecated="not deprecated"
                     visibility="public"
                    >
                    <constructor name="Test"
                     type="test.pkg.Test"
                     static="false"
                     final="false"
                     deprecated="not deprecated"
                     visibility="public"
                    >
                    </constructor>
                    </class>
                    </package>
                    </api>
                """
                    .trimIndent()
        }
    }
}

fun BaseCommandTest<SignatureToJDiffCommand>.jdiffConversionTest(body: JDiffTestConfig.() -> Unit) {
    commandTest {
        val config = JDiffTestConfig(this)
        config.body()
        config.arrange()
    }
}

class JDiffTestConfig(val commandTestConfig: CommandTestConfig<SignatureToJDiffCommand>) {
    var strip = false
    var formatForLegacyFiles: FileFormat? = null
    var api = ""
    var baseApi: String? = null
    var expectedXml: String? = null

    /**
     * The expected output, defaults to an empty string.
     *
     * This will be checked after running the test.
     */
    var expectedStderr: String = ""

    fun arrange() {
        with(commandTestConfig) {
            args += "signature-to-jdiff"

            if (strip) {
                args += "--strip"
            }

            formatForLegacyFiles?.let { format ->
                args += "--format-for-legacy-files"
                args += format.specifier()
            }

            // Create a unique folder to allow multiple configs to be run in the same test.
            val folder = commandTestConfig.folder("jdiff-conversion")

            val apiFile = inputFile("api.txt", api.trimIndent(), parentDir = folder)
            args += apiFile.path

            baseApi?.let {
                val baseApiFile = inputFile("base-api.txt", it.trimIndent(), parentDir = folder)
                args += "--base-api"
                args += baseApiFile.path
            }

            val xmlFile = outputFile("api.xml", parentDir = folder)
            args += xmlFile.path

            verify {
                val expectedXml = this@JDiffTestConfig.expectedXml
                if (expectedXml == null) {
                    assertFalse(
                        xmlFile.exists(),
                        message = "did not expect $xmlFile to be created but it was"
                    )
                } else {
                    assertEquals(expectedXml.trimIndent(), xmlFile.readText().trim())
                }
            }

            expectedStderr = this@JDiffTestConfig.expectedStderr
        }
    }
}
