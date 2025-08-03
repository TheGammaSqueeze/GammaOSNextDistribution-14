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
import com.android.tools.metalava.testing.java
import org.junit.Test

class PolymorphicMethodsTest : DriverTest() {

    @Test
    fun `Test MethodHandle`() {
        check(
            format = FileFormat.V2,
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package java.lang.invoke;

                            public abstract class MethodHandle {
                                public final native Object invokeNative(Object... args);

                                // The following should not end up with a `native` modifier as it is
                                // not native.
                                public final Object invokeNotNative(Object... args);

                                // The following should not end up with a `native` modifier as it
                                // does not take a single variable arity parameter it takes two
                                // parameters.
                                public final native Object invokeNotSingleParameter(String name, Object[] args);

                                // The following should not end up with a `native` modifier as it
                                // does not take a single variable arity parameter it does take one
                                // parameter, but it's not a variable arity parameter.
                                public final native Object invokeNotVarArgs(Object[] args);

                                // The following should not end up with a `native` modifier as while
                                // it erases to `Object[]` that is not its declared type.
                                @SafeVarargs
                                public final native <T> Object invokeNotDeclaredObjectVarArgsTypes(T... args);
                            }
                        """
                            .trimIndent()
                    ),
                    java(
                        """
                            package java.lang.invoke;

                            public abstract class NotMethodHandle {
                                // The following should not end up with a `native` modifier as it's
                                // in the wrong class.
                                public final native Object invokeNative(Object... args);
                            }
                        """
                            .trimIndent()
                    ),
                ),
            api =
                """
                    // Signature format: 2.0
                    package java.lang.invoke {
                      public abstract class MethodHandle {
                        ctor public MethodHandle();
                        method public final native Object invokeNative(java.lang.Object...);
                        method @java.lang.SafeVarargs public final <T> Object invokeNotDeclaredObjectVarArgsTypes(T...);
                        method public final Object invokeNotNative(java.lang.Object...);
                        method public final Object invokeNotSingleParameter(String, Object[]);
                        method public final Object invokeNotVarArgs(Object[]);
                      }
                      public abstract class NotMethodHandle {
                        ctor public NotMethodHandle();
                        method public final Object invokeNative(java.lang.Object...);
                      }
                    }
                """
                    .trimIndent(),
        )
    }

    @Test
    fun `Test VarHandle`() {
        check(
            format = FileFormat.V2,
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package java.lang.invoke;

                            public abstract class VarHandle {
                                public final native Object get(Object... args);
                                public final native boolean compareAndSet(Object... args);
                                public final native void set(Object... args);
                            }
                        """
                            .trimIndent()
                    ),
                ),
            api =
                """
                    // Signature format: 2.0
                    package java.lang.invoke {
                      public abstract class VarHandle {
                        ctor public VarHandle();
                        method public final native boolean compareAndSet(java.lang.Object...);
                        method public final native Object get(java.lang.Object...);
                        method public final native void set(java.lang.Object...);
                      }
                    }
                """
                    .trimIndent(),
        )
    }
}
