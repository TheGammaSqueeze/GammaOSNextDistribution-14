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

package com.android.tools.metalava.model.testsuite.codebase

import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.testsuite.BaseModelTest
import com.android.tools.metalava.testing.java
import com.android.tools.metalava.testing.kotlin
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import org.junit.Test
import org.junit.runners.Parameterized

class ParameterizedFindClassTest : BaseModelTest() {

    @Parameterized.Parameter(0) lateinit var params: TestParams

    data class TestParams(
        val className: String,
        val expectedFound: Boolean,
        /**
         * This is only tested when `true` as even though the class might be unknown some models
         * that work with partial information, e.g. text, will fabricate an instance just in case it
         * was real.
         */
        val expectedResolved: Boolean = expectedFound,
    ) {
        override fun toString(): String {
            return className
        }
    }

    companion object {
        private val params =
            listOf(
                TestParams(
                    className = "test.pkg.Foo",
                    expectedFound = true,
                ),
                TestParams(
                    className = "test.pkg.Unknown",
                    expectedFound = false,
                ),
                TestParams(
                    // Test to make sure that a class whose name does not match the file name will
                    // be found.
                    className = "test.pkg.SecondInFile",
                    expectedFound = true,
                ),
                // The following classes will be explicitly loaded. Although these are used
                // implicitly the behavior differs between models so is hard to test. By specifying
                // them explicitly it makes the tests more consistent.
                TestParams(
                    className = "java.lang.Object",
                    expectedFound = true,
                ),
                TestParams(
                    className = "java.lang.Throwable",
                    expectedFound = true,
                ),
                // The following classes are implicitly used, directly, or indirectly and are tested
                // to check that the implicit use does not accidentally include them when they
                // should not. However, they should all be resolvable.
                TestParams(
                    className = "java.lang.annotation.Annotation",
                    expectedFound = false,
                    expectedResolved = true,
                ),
                TestParams(
                    className = "java.lang.Enum",
                    expectedFound = false,
                    expectedResolved = true,
                ),
                TestParams(
                    className = "java.lang.Comparable",
                    expectedFound = false,
                    expectedResolved = true,
                ),
                // The following should not be used implicitly by anything.
                TestParams(
                    className = "java.io.File",
                    expectedFound = false,
                    expectedResolved = true,
                ),
            )

        @JvmStatic @Parameterized.Parameters fun params() = params
    }

    private fun assertFound(className: String, expectedFound: Boolean, foundClass: ClassItem?) {
        if (expectedFound) {
            assertNotNull(foundClass, message = "$className should exist")
        } else {
            assertNull(foundClass, message = "$className should not exist")
        }
    }

    @Test
    fun `test findClass()`() {
        runCodebaseTest(
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public class Foo {
                        method public Object foo(Throwable) throws Throwable;
                      }
                      public class SecondInFile {
                      }
                    }
                """
            ),
            java(
                """
                    package test.pkg;
                    public class Foo {
                        private Foo() {}
                        public Object foo(Throwable t) throws Throwable {throw new Throwable();}
                    }
                    public class SecondInFile {
                    }
                """
            ),
            kotlin(
                """
                    package test.pkg
                    class Foo
                    private constructor() {
                        @Throws(Throwable::class)
                        fun foo(t: Throwable): Any {throw Throwable()}
                    }
                    class SecondInFile {
                    }
                """
            ),
        ) {
            val fooMethod = codebase.assertClass("test.pkg.Foo").methods().single()

            // Force loading of the Object classes by resolving the return type which is
            // java.lang.Object.
            fooMethod.returnType().asClass()

            // Force loading of the Throwable classes by resolving the parameter's type which is
            // java.lang.Object.
            fooMethod.parameters().single().type().asClass()

            val className = params.className
            val foundClass = codebase.findClass(className)
            assertFound(className, params.expectedFound, foundClass)

            val resolvedClass = codebase.resolveClass(className)
            if (foundClass == null) {
                // If the class was not found then resolving might have found it.
                if (params.expectedResolved) {
                    assertNotNull(resolvedClass, message = "expected to resolve $className")
                }

                // If the class was resolved then it must now be found.
                if (resolvedClass != null) {
                    val foundClassAfterResolving = codebase.findClass(className)
                    assertSame(
                        resolvedClass,
                        foundClassAfterResolving,
                        message = "could not find $className even though it was previously resolved"
                    )
                }
            } else {
                // If the class was found then it must be resolved to the same class.
                assertSame(
                    foundClass,
                    resolvedClass,
                    message = "could not resolve $className even though it was previously found"
                )
            }
        }
    }
}
