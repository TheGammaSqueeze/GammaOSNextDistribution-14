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

package com.android.tools.metalava.model.testsuite.constructoritem

import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.testsuite.BaseModelTest
import com.android.tools.metalava.testing.java
import com.android.tools.metalava.testing.kotlin
import org.junit.Test

/** Common tests for implementations of [MethodItem]. */
class CommonConstructorItemTest : BaseModelTest() {

    @Test
    fun `Test access type parameter of outer class`() {
        runCodebaseTest(
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public class Outer<O> {
                      }
                      public class Outer.Middle {
                      }
                      public abstract class Outer.Middle.Inner {
                        ctor public Inner(O);
                      }
                    }
                """
            ),
            java(
                """
                    package test.pkg;

                    public class Outer<O> {
                        private Outer() {}

                        public class Middle {
                            private Middle() {}
                            public class Inner {
                                public Inner(O o) {}
                            }
                        }
                    }
                """
            ),
            kotlin(
                """
                    package test.pkg

                    class Outer<O> private constructor() {
                        inner class Middle private constructor() {
                            abstract inner class Inner(o: O) {
                            }
                        }
                    }
                """
            ),
        ) {
            val oTypeParameter = codebase.assertClass("test.pkg.Outer").typeParameterList.single()
            val constructorType =
                codebase
                    .assertClass("test.pkg.Outer.Middle.Inner")
                    .constructors()
                    .first()
                    .parameters()
                    .last()
                    .type()

            constructorType.assertReferencesTypeParameter(oTypeParameter)
        }
    }
}
