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
import com.android.tools.metalava.model.text.FileFormat
import com.android.tools.metalava.model.text.assertSignatureFilesMatch
import com.android.tools.metalava.model.text.prepareSignatureFileForTest
import org.junit.Assert.fail
import org.junit.Test

class MergeSignaturesCommandTest :
    BaseCommandTest<MergeSignaturesCommand>({ MergeSignaturesCommand() }) {

    private fun checkMergeSignatures(
        vararg files: String,
        format: FileFormat = FileFormat.LATEST,
        expectedOutput: String? = null,
        expectedStderr: String = "",
    ) {
        commandTest {
            args += "merge-signatures"
            files.forEachIndexed { i, contents ->
                val input =
                    inputFile(
                        "api${i + 1}.txt",
                        prepareSignatureFileForTest(contents.trimIndent(), FileFormat.V2)
                    )
                args += input.path
            }

            val output = outputFile("out.txt")
            args += "--out"
            args += output.path

            args += "--format"
            args += format.specifier()

            if (expectedOutput == null) {
                verify {
                    if (output.exists()) {
                        fail(
                            "file $output was written unexpectedly and contains\n${output.readText()}"
                        )
                    }
                }
            } else {
                verify {
                    assertSignatureFilesMatch(
                        expectedOutput,
                        output.readText(Charsets.UTF_8),
                        expectedFormat = format
                    )
                }
            }

            this.expectedStderr = expectedStderr
        }
    }

    @Test
    fun `Test help`() {
        commandTest {
            args += listOf("merge-signatures", "--help")

            expectedStdout =
                """
Usage: metalava merge-signatures [options] <files>...

  Merge multiple signature files together into a single file.

  The files must all be from the same API surface. The input files may overlap at the package and class level but if two
  files do include the same class they must be identical. Note: It is the user's responsibility to ensure that these
  constraints are met as metalava does not have the information available to enforce it. Failure to do so will result in
  undefined behavior.

Options:
  --out <file>                               The output file into which the result will be written. The format of the
                                             file will be determined by the options in `Signature Format Output`.
                                             (required)
  -h, -?, --help                             Show this message and exit

$SIGNATURE_FORMAT_OPTIONS_HELP

Arguments:
  <files>                                    Multiple signature files that will be merged together.
                """
                    .trimIndent()
        }
    }

    @Test
    fun `Test merging API signature files`() {
        val source1 =
            """
                package Test.pkg {
                  public final class Class1 {
                    method public void method1();
                  }
                }
                package Test.pkg1 {
                  public final class Class1 {
                    method public void method1();
                  }
                }
            """
        val source2 =
            """
                package Test.pkg {
                  public final class Class2 {
                    method public void method1(String);
                  }
                }
                package Test.pkg2 {
                  public final class Class1 {
                    method public void method1(String, String);
                  }
                }
            """
        val expected =
            """
                package Test.pkg {
                  public final class Class1 {
                    method public void method1();
                  }
                  public final class Class2 {
                    method public void method1(String);
                  }
                }
                package Test.pkg1 {
                  public final class Class1 {
                    method public void method1();
                  }
                }
                package Test.pkg2 {
                  public final class Class1 {
                    method public void method1(String, String);
                  }
                }
            """
        checkMergeSignatures(
            source1,
            source2,
            format = FileFormat.V2,
            expectedOutput = expected,
        )
    }

    private val MERGE_TEST_SOURCE_1 =
        """
            package test.pkg {
              public final class BaseClass {
                method public void method1();
              }
            }
        """
    private val MERGE_TEST_SOURCE_2 =
        """
            package test.pkg {
              public final class SubClass extends test.pkg.BaseClass {
              }
            }
        """
    private val MERGE_TEST_EXPECTED =
        """
            package test.pkg {
              public final class BaseClass {
                method public void method1();
              }
              public final class SubClass extends test.pkg.BaseClass {
              }
            }
        """

    @Test
    fun `Test merging API signature files, one refer to another`() {
        checkMergeSignatures(
            MERGE_TEST_SOURCE_1,
            MERGE_TEST_SOURCE_2,
            expectedOutput = MERGE_TEST_EXPECTED,
        )
    }

    @Test
    fun `Test merging API signature files, one refer to another, in reverse order`() {
        // Exactly the same as the previous test, but read them in the reverse order
        checkMergeSignatures(
            MERGE_TEST_SOURCE_2,
            MERGE_TEST_SOURCE_1,
            expectedOutput = MERGE_TEST_EXPECTED,
        )
    }

    @Test
    fun `Test merging API signature files with reverse dependency`() {
        val source1 =
            """
            package test.pkg {
              public final class Class1 {
                method public void method1(test.pkg.Class2 arg);
              }
            }
                    """
        val source2 =
            """
            package test.pkg {
              public final class Class2 {
              }
            }
                    """
        val expected =
            """
            package test.pkg {
              public final class Class1 {
                method public void method1(test.pkg.Class2 arg);
              }
              public final class Class2 {
              }
            }
                    """
        checkMergeSignatures(
            source1,
            source2,
            format = FileFormat.V2,
            expectedOutput = expected,
        )
    }

    @Test
    fun `Test merging 3 API signature files`() {
        val source1 =
            """
                package test.pkg1 {
                  public final class BaseClass1 {
                    method public void method1();
                  }

                  public final class AnotherSubClass extends test.pkg2.AnotherBase {
                    method public void method1();
                  }
                }
            """
        val source2 =
            """
                package test.pkg2 {
                  public final class SubClass1 extends test.pkg1.BaseClass1 {
                  }
                }
            """
        val source3 =
            """
                package test.pkg2 {
                  public final class SubClass2 extends test.pkg2.SubClass1 {
                    method public void bar();
                  }

                  public final class AnotherBase {
                    method public void baz();
                  }
                }
            """
        val expected =
            """
                package test.pkg1 {
                  public final class AnotherSubClass extends test.pkg2.AnotherBase {
                    method public void method1();
                  }
                  public final class BaseClass1 {
                    method public void method1();
                  }
                }
                package test.pkg2 {
                  public final class AnotherBase {
                    method public void baz();
                  }
                  public final class SubClass1 extends test.pkg1.BaseClass1 {
                  }
                  public final class SubClass2 extends test.pkg2.SubClass1 {
                    method public void bar();
                  }
                }
            """

        checkMergeSignatures(source1, source2, source3, expectedOutput = expected)
    }

    @Test
    fun `Test can merge API signature files with duplicate class`() {
        val source1 =
            """
                package Test.pkg {
                  public final class Class1 {
                    method public void method1();
                  }
                }
            """
        val source2 =
            """
                package Test.pkg {
                  public final class Class1 {
                    method public void method1();
                  }
                }
                """
        val expected =
            """
                package Test.pkg {
                  public final class Class1 {
                    method public void method1();
                  }
                }
            """
        checkMergeSignatures(source1, source2, expectedOutput = expected)
    }

    @Test
    fun `Test cannot merge API signature files with incompatible class definitions`() {
        val source1 =
            """
                package Test.pkg {
                  public class Class1 {
                    method public void method2();
                  }
                }
            """
        val source2 =
            """
                package Test.pkg {
                  public final class Class1 {
                    method public void method1();
                  }
                }
            """

        checkMergeSignatures(
            source1,
            source2,
            expectedStderr =
                "Aborting: TESTROOT/api2.txt:3: Incompatible class Test.pkg.Class1 definitions",
        )
    }

    @Test
    fun `Test can merge API signature files with different file formats`() {
        val source1 =
            """
                // Signature format: 2.0
                package Test.pkg {
                }
            """

        val source2 =
            """
                // Signature format: 5.0
                // - kotlin-style-nulls=no
                package Test.pkg {
                }
            """

        checkMergeSignatures(
            source1,
            source2,
            format = FileFormat.V4,
            expectedOutput = """
            // Signature format: 4.0
            """,
        )
    }
}
