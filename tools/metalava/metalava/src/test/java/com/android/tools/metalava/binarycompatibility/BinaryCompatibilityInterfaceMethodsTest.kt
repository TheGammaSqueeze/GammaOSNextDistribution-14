/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.tools.metalava.binarycompatibility

import com.android.tools.metalava.DriverTest
import org.junit.Test

class BinaryCompatibilityInterfaceMethodsTest : DriverTest() {

    @Test
    fun `Change method name (Incompatible)`() {
        check(
            expectedIssues =
                """
                released-api.txt:4: error: Removed method test.pkg.Foo.bar(int) [RemovedMethod]
            """,
            signatureSource =
                """
                package test.pkg {
                  interface Foo {
                    method public void baz(int);
                  }
                }
            """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  interface Foo {
                    method public void bar(int);
                  }
                }
            """
        )
    }

    @Test
    fun `Add or delete formal parameter (Incompatible)`() {
        check(
            expectedIssues =
                """
                released-api.txt:4: error: Removed method test.pkg.Foo.bar(int) [RemovedMethod]
            """,
            signatureSource =
                """
                package test.pkg {
                  interface Foo {
                    method public void bar();
                  }
                }
            """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  interface Foo {
                    method public void bar(int);
                  }
                }
            """
        )
    }

    @Test
    fun `Change type of a formal parameter (Incompatible)`() {
        check(
            expectedIssues =
                """
                released-api.txt:4: error: Removed method test.pkg.Foo.bar(int) [RemovedMethod]
            """,
            signatureSource =
                """
                package test.pkg {
                  interface Foo {
                    method public void bar(Float);
                  }
                }
            """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  interface Foo {
                    method public void bar(int);
                  }
                }
            """
        )
    }

    @Test
    fun `Change result type (including void) (Incompatible)`() {
        check(
            expectedIssues =
                """
                load-api.txt:4: error: Method test.pkg.Foo.bar has changed return type from void to int [ChangedType]
            """,
            signatureSource =
                """
                package test.pkg {
                  interface Foo {
                    method public int bar(int);
                  }
                }
            """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  interface Foo {
                    method public void bar(int);
                  }
                }
            """
        )
    }

    @Test
    fun `Add checked exceptions thrown (Incompatible)`() {
        check(
            expectedIssues =
                """
                load-api.txt:4: error: Method test.pkg.Foo.bar added thrown exception java.lang.Throwable [ChangedThrows]
            """,
            signatureSource =
                """
                package test.pkg {
                  interface Foo {
                    method public void bar(int) throws java.lang.Throwable;
                  }
                }
            """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  interface Foo {
                    method public void bar(int);
                  }
                }
            """
        )
    }

    @Test
    fun `Delete checked exceptions thrown (Incompatible)`() {
        check(
            expectedIssues =
                """
                load-api.txt:4: error: Method test.pkg.Foo.bar no longer throws exception java.lang.Throwable [ChangedThrows]
            """,
            signatureSource =
                """
                package test.pkg {
                  interface Foo {
                    method public void bar(int);
                  }
                }
            """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  interface Foo {
                    method public void bar(int) throws java.lang.Throwable;
                  }
                }
            """
        )
    }

    @Test
    fun `Re-order list of exceptions thrown (Compatible)`() {
        check(
            signatureSource =
                """
                package test.pkg {
                  interface Foo {
                    method public void bar(int) throws java.lang.Exception, java.lang.Throwable;
                  }
                }
            """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  interface Foo {
                    method public void bar(int) throws java.lang.Throwable, java.lang.Exception;
                  }
                }
            """
        )
    }

    @Test
    fun `Change static to non-static (Incompatible)`() {
        check(
            expectedIssues =
                """
                load-api.txt:4: error: Method test.pkg.Foo.bar has changed 'static' qualifier [ChangedStatic]
            """,
            signatureSource =
                """
                package test.pkg {
                  interface Foo {
                    method public void bar(int);
                  }
                }
            """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  interface Foo {
                    method static public void bar(int);
                  }
                }
            """
        )
    }

    @Test
    fun `Change non-static to static (Incompatible)`() {
        check(
            expectedIssues =
                """
                load-api.txt:4: error: Method test.pkg.Foo.bar has changed 'static' qualifier [ChangedStatic]
            """,
            signatureSource =
                """
                package test.pkg {
                  interface Foo {
                    method static public void bar(int);
                  }
                }
            """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  interface Foo {
                    method public void bar(int);
                  }
                }
            """
        )
    }

    @Test
    fun `Change default to abstract (Incompatible)`() {
        check(
            expectedIssues =
                """
                load-api.txt:4: error: Method test.pkg.Foo.bar has changed 'default' qualifier [ChangedDefault]
            """,
            signatureSource =
                """
                package test.pkg {
                  interface Foo {
                    method abstract public void bar(int);
                  }
                }
            """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  interface Foo {
                    method default public void bar(int);
                  }
                }
            """
        )
    }

    @Test
    fun `Change abstract to default (Compatible)`() {
        check(
            signatureSource =
                """
                package test.pkg {
                  interface Foo {
                    method default public void bar(int);
                  }
                }
            """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  interface Foo {
                    method abstract public void bar(int);
                  }
                }
            """
        )
    }

    /*
    TODO: Fix b/217229076 and uncomment this block of tests

    @Test
    fun `Add type parameter, no existing type parameters (Compatible)`() {
        check(
            signatureSource = """
                package test.pkg {
                  class Foo {
                    method public <T> void bar(int);
                  }
                }
            """,
            checkCompatibilityApiReleased = """
                package test.pkg {
                  class Foo {
                    method public void bar(int);
                  }
                }
            """
        )
    }

    @Test
    fun `Add type parameter, existing type parameters (Incompatible)`() {
        check(
            signatureSource = """
                package test.pkg {
                  interface Foo {
                    method public <T, K> void bar();
                  }
                }
            """,
            checkCompatibilityApiReleased = """
                package test.pkg {
                  interface Foo {
                    method public <T> void bar();
                  }
                }
            """
        )
    }

    @Test
    fun `Delete type parameter (Incompatible)`() {
        check(
            signatureSource = """
                package test.pkg {
                  class Foo {
                    method public void bar(int);
                  }
                }
            """,
            checkCompatibilityApiReleased = """
                package test.pkg {
                  class Foo {
                    method public <T> void bar(int);
                  }
                }
            """
        )
    }

    @Test
    fun `Re-order type parameters (Incompatible)`() {
        check(
            signatureSource = """
                package test.pkg {
                  class Foo {
                    method public <T, K> void bar(int);
                  }
                }
            """,
            checkCompatibilityApiReleased = """
                package test.pkg {
                  class Foo {
                    method public <K, T> void bar(int);
                  }
                }
            """
        )
    }

    @Test
    fun `Rename type parameter (Compatible)`() {
         check(
            signatureSource = """
                package test.pkg {
                  class Foo {
                    method public <T> void bar(int);
                  }
                }
            """,
            checkCompatibilityApiReleased = """
                package test.pkg {
                  class Foo {
                    method public <K> void bar(int);
                  }
                }
            """
        )
    }

    @Test
    fun `Add, delete, or change type bounds of type parameter (Incompatible)`() {
        check(
            signatureSource = """
                package test.pkg {
                  class Foo {
                    method public <T extends Foo> void bar(int);
                  }
                }
            """,
            checkCompatibilityApiReleased = """
                package test.pkg {
                  class Foo {
                    method public <T> void bar(int);
                  }
                }
            """
        )
    }
     */

    @Test
    fun `Change last parameter from array type T(array) to variable arity T(elipse) (Compatible)`() {
        check(
            signatureSource =
                """
                package test.pkg {
                    interface Foo {
                        method public <T> void bar(T...);
                    }
                }
            """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                    interface Foo {
                        method public <T> void bar(T[]);
                    }
                }
            """
        )
    }

    @Test
    fun `Change last parameter from variable arity T(elipse) to array type T(array) (Incompatible)`() {
        check(
            expectedIssues =
                """
                load-api.txt:4: error: Changing from varargs to array is an incompatible change: parameter arg1 in test.pkg.Foo.bar(T[] arg1) [VarargRemoval]
            """,
            signatureSource =
                """
                package test.pkg {
                    interface Foo {
                        method public <T> void bar(T[]);
                    }
                }
            """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                    interface Foo {
                        method public <T> void bar(T...);
                    }
                }
            """
        )
    }

    @Test
    fun `Add default clause to annotation type element (Compatible)`() {
        check(
            signatureSource =
                """
                package test.pkg {
                  public @interface Foo {
                    method public void bar(int) default 0;
                  }
                }
            """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  public @interface Foo {
                    method public void bar(int);
                  }
                }
            """
        )
    }

    /**
     * Note: While this is technically binary compatible, it's bad API design to allow. Thus, we
     * continue to flag this as an error.
     */
    @Test
    fun `Change default clause on annotation type element (Incompatible)`() {
        check(
            expectedIssues =
                """
                load-api.txt:4: error: Method test.pkg.Foo.bar has changed value from 0 to 1 [ChangedValue]
            """,
            signatureSource =
                """
                package test.pkg {
                  public @interface Foo {
                    method public void bar(int) default 1;
                  }
                }
            """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  public @interface Foo {
                    method public void bar(int) default 0;
                  }
                }
            """
        )
    }

    @Test
    fun `Delete default clause from annotation type element (Incompatible)`() {
        check(
            expectedIssues =
                """
                load-api.txt:4: error: Method test.pkg.Foo.bar has changed value from 0 to nothing [ChangedValue]
            """,
            signatureSource =
                """
                package test.pkg {
                  public @interface Foo {
                    method public void bar(int);
                  }
                }
            """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  public @interface Foo {
                    method public void bar(int) default 0;
                  }
                }
            """
        )
    }
}
