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

@file:Suppress("ALL")

package com.android.tools.metalava

import com.android.tools.lint.checks.infrastructure.TestFile
import com.android.tools.metalava.model.text.FileFormat
import com.android.tools.metalava.testing.java
import org.junit.Test

class AddAdditionalOverridesTest : DriverTest() {

    private fun checkAddAdditionalOverrides(
        sourceFiles: Array<TestFile>,
        apiOriginal: String,
        apiWithAdditionalOverrides: String,
        format: FileFormat = FileFormat.V2,
        extraArguments: Array<String> = emptyArray(),
    ) {
        // Signature content without additional overrides check
        check(
            format = format,
            sourceFiles = sourceFiles,
            api = apiOriginal,
            extraArguments = extraArguments,
        )

        // Signature content with additional overrides check
        check(
            format = format.copy(specifiedAddAdditionalOverrides = true),
            sourceFiles = sourceFiles,
            api = apiWithAdditionalOverrides,
            extraArguments = extraArguments,
        )
    }

    @Test
    fun `Does emit Object method override to signature file to prevent compile error`() {

        // Currently, ChildClass.hashCode() is not emitted to signature files as it possess
        // identical signature to java.lang.Object.hashCode(). However, omitting this method will
        // lead to a compile error as ChildClass extends ParentClass, which overrides hashCode() as
        // an abstract method. In other words, if there are "multiple" super methods for Object
        // methods (e.g. ChildClass.hashCode()'s super methods are Object.hashCode() and
        // ParentClass.hashCode()), whether the super method of the non-Object class needs to be
        // overridden or not has to be used to determine if the method needs to be included in the
        // signature file or not.
        checkAddAdditionalOverrides(
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;
                    public abstract class ParentClass implements java.util.Comparator<Object> {
                        @Override
                        public abstract int hashCode();

                        @Override
                        public abstract int someMethod();
                    }
                    """
                    ),
                    java(
                        """
                    package test.pkg;
                    public class ChildClass extends ParentClass {
                        @Override
                        public int hashCode() {
                            return 0;
                        }

                        @Override
                        public int someMethod() {
                            return 0;
                        }
                    }
                    """
                    ),
                ),
            apiOriginal =
                """
            // Signature format: 2.0
            package test.pkg {
              public class ChildClass extends test.pkg.ParentClass {
                ctor public ChildClass();
                method public int someMethod();
              }
              public abstract class ParentClass implements java.util.Comparator<java.lang.Object> {
                ctor public ParentClass();
                method public abstract int hashCode();
                method public abstract int someMethod();
              }
            }
        """,
            apiWithAdditionalOverrides =
                """
            // Signature format: 2.0
            package test.pkg {
              public class ChildClass extends test.pkg.ParentClass {
                ctor public ChildClass();
                method public int hashCode();
                method public int someMethod();
              }
              public abstract class ParentClass implements java.util.Comparator<java.lang.Object> {
                ctor public ParentClass();
                method public abstract int hashCode();
                method public abstract int someMethod();
              }
            }
        """,
        )
    }

    @Test
    fun `Does emit override with identical signature to prevent compile error`() {

        // ChildClass.someMethod() possess identical signature to that of
        // ChildInterface.someMethod(), but needs to be implemented in ChildClass to resolve compile
        // error and thus needs to be included in the signature file.
        checkAddAdditionalOverrides(
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;
                    public class ChildClass extends ParentClass implements ChildInterface {
                        public void someMethod() {}
                    }
                    """
                    ),
                    java(
                        """
                    package test.pkg;
                    public abstract class ParentClass implements ParentInterface {
                        public abstract void someMethod();
                    }
                    """
                    ),
                    java(
                        """
                    package test.pkg;
                    public interface ParentInterface {
                        default void someMethod() {}
                    }
                    """
                    ),
                    java(
                        """
                    package test.pkg;
                    public interface ChildInterface extends ParentInterface {
                        void someMethod();
                    }
                    """
                    ),
                ),
            apiOriginal =
                """
            // Signature format: 2.0
            package test.pkg {
              public class ChildClass extends test.pkg.ParentClass implements test.pkg.ChildInterface {
                ctor public ChildClass();
              }
              public interface ChildInterface extends test.pkg.ParentInterface {
                method public void someMethod();
              }
              public abstract class ParentClass implements test.pkg.ParentInterface {
                ctor public ParentClass();
                method public abstract void someMethod();
              }
              public interface ParentInterface {
                method public default void someMethod();
              }
            }
        """,
            apiWithAdditionalOverrides =
                """
            // Signature format: 2.0
            package test.pkg {
              public class ChildClass extends test.pkg.ParentClass implements test.pkg.ChildInterface {
                ctor public ChildClass();
                method public void someMethod();
              }
              public interface ChildInterface extends test.pkg.ParentInterface {
                method public void someMethod();
              }
              public abstract class ParentClass implements test.pkg.ParentInterface {
                ctor public ParentClass();
                method public abstract void someMethod();
              }
              public interface ParentInterface {
                method public default void someMethod();
              }
            }
        """,
        )
    }

    @Test
    fun `Does add override-equivalent signatures`() {

        // When an interface inherits several methods with override-equivalent signatures
        // but it is not defined, it leads to a compile error thus needs to be included in the
        // signature file.
        checkAddAdditionalOverrides(
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;
                    public interface ChildInterface extends ParentInterface1, ParentInterface2 {
                        void someMethod();
                    }
                    """
                    ),
                    java(
                        """
                    package test.pkg;
                    public interface ParentInterface1 {
                        default void someMethod() {}
                    }
                    """
                    ),
                    java(
                        """
                    package test.pkg;
                    public interface ParentInterface2 {
                        void someMethod();
                    }
                    """
                    ),
                ),
            apiOriginal =
                """
                // Signature format: 2.0
                package test.pkg {
                  public interface ChildInterface extends test.pkg.ParentInterface1 test.pkg.ParentInterface2 {
                  }
                  public interface ParentInterface1 {
                    method public default void someMethod();
                  }
                  public interface ParentInterface2 {
                    method public void someMethod();
                  }
                }
            """,
            apiWithAdditionalOverrides =
                """
            // Signature format: 2.0
            package test.pkg {
              public interface ChildInterface extends test.pkg.ParentInterface1 test.pkg.ParentInterface2 {
                method public void someMethod();
              }
              public interface ParentInterface1 {
                method public default void someMethod();
              }
              public interface ParentInterface2 {
                method public void someMethod();
              }
            }
        """,
        )
    }

    @Test
    fun `Method with multiple interface parent methods in same hierarchy not elided`() {
        checkAddAdditionalOverrides(
            sourceFiles =
                arrayOf(
                    // Although ParentInterface provides the default super method, it is abstracted
                    // in AnotherParentInterface and thus ChildClass.Foo() is an essential method.
                    java(
                        """
                    package test.pkg;

                    public class ChildClass implements ParentInterface, AnotherParentInterface {
                        public void Foo() {}
                    }
                    """
                    ),
                    java(
                        """
                    package test.pkg;

                    public interface ParentInterface {
                        public default void Foo() {}
                    }
                    """
                    ),
                    java(
                        """
                    package test.pkg;

                    public interface AnotherParentInterface extends ParentInterface {
                        public void Foo();
                    }
                    """
                    ),
                ),
            apiOriginal =
                """
            // Signature format: 2.0
            package test.pkg {
              public interface AnotherParentInterface extends test.pkg.ParentInterface {
                method public void Foo();
              }
              public class ChildClass implements test.pkg.AnotherParentInterface test.pkg.ParentInterface {
                ctor public ChildClass();
              }
              public interface ParentInterface {
                method public default void Foo();
              }
            }
        """,
            apiWithAdditionalOverrides =
                """
            // Signature format: 2.0
            package test.pkg {
              public interface AnotherParentInterface extends test.pkg.ParentInterface {
                method public void Foo();
              }
              public class ChildClass implements test.pkg.AnotherParentInterface test.pkg.ParentInterface {
                ctor public ChildClass();
                method public void Foo();
              }
              public interface ParentInterface {
                method public default void Foo();
              }
            }
        """,
        )
    }

    @Test
    fun `Do not treat generic method as override when erased parameter type does not match`() {
        checkAddAdditionalOverrides(
            sourceFiles =
                arrayOf(
                    java(
                        """
                        package test.pkg;

                        public class ParentClass<T> {
                            public void hasGenericParameter(T t) {}
                        }
                        """
                    ),
                    java(
                        """
                        package test.pkg;

                        public class ChildClass<T extends Number> extends ParentClass<T> {
                            public void hasGenericParameter(T t) {}
                        }
                        """
                    ),
                ),
            apiOriginal =
                """
                // Signature format: 2.0
                package test.pkg {
                  public class ChildClass<T extends java.lang.Number> extends test.pkg.ParentClass<T> {
                    ctor public ChildClass();
                  }
                  public class ParentClass<T> {
                    ctor public ParentClass();
                    method public void hasGenericParameter(T);
                  }
                }
                """,
            apiWithAdditionalOverrides =
                """
                // Signature format: 2.0
                package test.pkg {
                  public class ChildClass<T extends java.lang.Number> extends test.pkg.ParentClass<T> {
                    ctor public ChildClass();
                    method public void hasGenericParameter(T);
                  }
                  public class ParentClass<T> {
                    ctor public ParentClass();
                    method public void hasGenericParameter(T);
                  }
                }
                """,
        )
    }

    @Test
    fun `Do not elide generic method override when erased parameter types match but unerased parameter types do not match`() {
        // This test is intended to ensure that textual differences in return and parameter types
        // will cause the methods to be kept even when they don't strictly need to be. That is to
        // preserve existing behavior to reduce churn in API signature files and also to detect
        // significant differences such as nullability annotations.
        checkAddAdditionalOverrides(
            sourceFiles =
                arrayOf(
                    java(
                        """
                        package test.pkg;

                        public class ParentClass<T> {
                            public void hasGenericParameter(T t) {}
                        }
                        """
                    ),
                    java(
                        """
                        package test.pkg;

                        public class ChildClass<E> extends ParentClass<E> {
                            public void hasGenericParameter(E e) {}
                        }
                        """
                    ),
                ),
            apiOriginal =
                """
                // Signature format: 2.0
                package test.pkg {
                  public class ChildClass<E> extends test.pkg.ParentClass<E> {
                    ctor public ChildClass();
                    method public void hasGenericParameter(E);
                  }
                  public class ParentClass<T> {
                    ctor public ParentClass();
                    method public void hasGenericParameter(T);
                  }
                }
                """,
            apiWithAdditionalOverrides =
                """
                // Signature format: 2.0
                package test.pkg {
                  public class ChildClass<E> extends test.pkg.ParentClass<E> {
                    ctor public ChildClass();
                    method public void hasGenericParameter(E);
                  }
                  public class ParentClass<T> {
                    ctor public ParentClass();
                    method public void hasGenericParameter(T);
                  }
                }
                """,
        )
    }

    @Test
    fun `Do not elide generic override when erased return types does not match`() {
        checkAddAdditionalOverrides(
            sourceFiles =
                arrayOf(
                    java(
                        """
                        package test.pkg;

                        public class ParentClass<T> {
                            public T hasGenericReturnType() {throw new IllegalStateException();}
                        }
                        """
                    ),
                    java(
                        """
                        package test.pkg;

                        public class ChildClass<T extends Number> extends ParentClass<T> {
                            public T hasGenericReturnType() {throw new IllegalStateException();}
                        }
                        """
                    ),
                ),
            apiOriginal =
                """
                // Signature format: 2.0
                package test.pkg {
                  public class ChildClass<T extends java.lang.Number> extends test.pkg.ParentClass<T> {
                    ctor public ChildClass();
                  }
                  public class ParentClass<T> {
                    ctor public ParentClass();
                    method public T hasGenericReturnType();
                  }
                }
                """,
            apiWithAdditionalOverrides =
                """
                // Signature format: 2.0
                package test.pkg {
                  public class ChildClass<T extends java.lang.Number> extends test.pkg.ParentClass<T> {
                    ctor public ChildClass();
                    method public T hasGenericReturnType();
                  }
                  public class ParentClass<T> {
                    ctor public ParentClass();
                    method public T hasGenericReturnType();
                  }
                }
                """,
        )
    }

    @Test
    fun `Do not elide generic override when erased return types match but unerased return types do not match`() {
        // This test is intended to ensure that textual differences in return and parameter types
        // will cause the methods to be kept even when they don't strictly need to be. That is to
        // preserve existing behavior to reduce churn in API signature files and also to detect
        // significant differences such as nullability annotations.
        checkAddAdditionalOverrides(
            sourceFiles =
                arrayOf(
                    java(
                        """
                        package test.pkg;

                        public class ParentClass<T> {
                            public T hasGenericReturnType() {throw new IllegalStateException();}
                        }
                        """
                    ),
                    java(
                        """
                        package test.pkg;

                        public class ChildClass<E> extends ParentClass<E> {
                            public E hasGenericReturnType() {throw new IllegalStateException();}
                        }
                        """
                    ),
                ),
            apiOriginal =
                """
                // Signature format: 2.0
                package test.pkg {
                  public class ChildClass<E> extends test.pkg.ParentClass<E> {
                    ctor public ChildClass();
                    method public E hasGenericReturnType();
                  }
                  public class ParentClass<T> {
                    ctor public ParentClass();
                    method public T hasGenericReturnType();
                  }
                }
                """,
            apiWithAdditionalOverrides =
                """
                // Signature format: 2.0
                package test.pkg {
                  public class ChildClass<E> extends test.pkg.ParentClass<E> {
                    ctor public ChildClass();
                    method public E hasGenericReturnType();
                  }
                  public class ParentClass<T> {
                    ctor public ParentClass();
                    method public T hasGenericReturnType();
                  }
                }
                """,
        )
    }

    @Test
    fun `Do not elide overriding method of a default method`() {
        checkAddAdditionalOverrides(
            sourceFiles =
                arrayOf(
                    java(
                        """
                        package test.pkg;

                        public interface ParentInterface {
                            public default void bar() {}
                        }
                        """
                    ),
                    java(
                        """
                        package test.pkg;

                        public class ChildClass implements ParentInterface {
                            public void bar() {}
                        }
                        """
                    ),
                ),
            apiOriginal =
                """
                // Signature format: 2.0
                package test.pkg {
                  public class ChildClass implements test.pkg.ParentInterface {
                    ctor public ChildClass();
                  }
                  public interface ParentInterface {
                    method public default void bar();
                  }
                }
                """,
            apiWithAdditionalOverrides =
                """
                // Signature format: 2.0
                package test.pkg {
                  public class ChildClass implements test.pkg.ParentInterface {
                    ctor public ChildClass();
                    method public void bar();
                  }
                  public interface ParentInterface {
                    method public default void bar();
                  }
                }
                """,
        )
    }

    @Test
    fun `Do not elide method inherited from inaccessible class and default method from interface`() {
        checkAddAdditionalOverrides(
            sourceFiles =
                arrayOf(
                    java(
                        """
                        package test.pkg;

                        public interface ParentInterface {
                            default void bar() {}
                        }
                        """
                    ),
                    java(
                        """
                        package test.pkg;

                        class InaccessibleClass implements ParentInterface {
                            public void bar() {}
                        }
                        """
                    ),
                    java(
                        """
                        package test.pkg;

                        public class ChildClass extends InaccessibleClass implements ParentInterface {
                        }
                        """
                    ),
                ),
            apiOriginal =
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public class ChildClass implements test.pkg.ParentInterface {
                        ctor public ChildClass();
                      }
                      public interface ParentInterface {
                        method public default void bar();
                      }
                    }
                """,
            apiWithAdditionalOverrides =
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public class ChildClass implements test.pkg.ParentInterface {
                        ctor public ChildClass();
                        method public void bar();
                      }
                      public interface ParentInterface {
                        method public default void bar();
                      }
                    }
                """,
        )
    }

    @Test
    fun `Elide methods inherited from one inaccessible class if they override method inherited from another inaccessible class`() {
        check(
            sourceFiles =
                arrayOf(
                    java(
                        """
                        package test.pkg;

                        class InaccessibleClass1 {
                            public void bar() {}
                        }
                        """
                    ),
                    java(
                        """
                        package test.pkg;

                        public class PublicClass1 extends InaccessibleClass1 {
                        }
                        """
                    ),
                    java(
                        """
                        package test.pkg;

                        class InaccessibleClass2 extends PublicClass1 {
                            public void bar() {}
                        }
                        """
                    ),
                    java(
                        """
                        package test.pkg;

                        public class PublicClass2 extends InaccessibleClass2 {
                        }
                        """
                    ),
                ),
            format = FileFormat.V2,
            api =
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public class PublicClass1 {
                        ctor public PublicClass1();
                        method public void bar();
                      }
                      public class PublicClass2 extends test.pkg.PublicClass1 {
                        ctor public PublicClass2();
                      }
                    }
                """,
        )
    }
}
