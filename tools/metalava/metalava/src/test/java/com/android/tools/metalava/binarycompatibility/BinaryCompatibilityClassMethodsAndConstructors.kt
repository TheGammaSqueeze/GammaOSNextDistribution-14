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

class BinaryCompatibilityClassMethodsAndConstructors : DriverTest() {
    @Test
    fun `Change method name`() {
        check(
            expectedIssues =
                """
                released-api.txt:4: error: Removed method test.pkg.Foo.bar(Int) [RemovedMethod]
            """,
            signatureSource =
                """
                package test.pkg {
                  class Foo {
                    method public void baz(Int);
                  }
                }
            """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  class Foo {
                    method public void bar(Int);
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
                released-api.txt:4: error: Removed method test.pkg.Foo.bar(Int) [RemovedMethod]
            """,
            signatureSource =
                """
                package test.pkg {
                  class Foo {
                    method public void bar();
                  }
                }
            """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  class Foo {
                    method public void bar(Int);
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
                released-api.txt:4: error: Removed method test.pkg.Foo.bar(Int) [RemovedMethod]
            """,
            signatureSource =
                """
                package test.pkg {
                  class Foo {
                    method public void bar(Float);
                  }
                }
            """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  class Foo {
                    method public void bar(Int);
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
                load-api.txt:4: error: Method test.pkg.Foo.bar has changed return type from void to java.lang.Int [ChangedType]
            """,
            signatureSource =
                """
                package test.pkg {
                  class Foo {
                    method public Int bar(Int);
                  }
                }
            """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  class Foo {
                    method public void bar(Int);
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
                  class Foo {
                    method public void bar(Int) throws java.lang.Throwable;
                  }
                }
            """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  class Foo {
                    method public void bar(Int);
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
                  class Foo {
                    method public void bar(Int);
                  }
                }
            """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  class Foo {
                    method public void bar(Int) throws java.lang.Throwable;
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
                  class Foo {
                    method public void bar(Int) throws java.lang.Exception, java.lang.Throwable;
                  }
                }
            """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  class Foo {
                    method public void bar(Int) throws java.lang.Throwable, java.lang.Exception;
                  }
                }
            """
        )
    }
    /*
    Decrease access; that is, from protected access to default or private access,
    or from public access to protected, default, or private access
     */
    @Test
    fun `Decrease access(Incompatible)`() {
        check(
            expectedIssues =
                """
               load-api.txt:4: error: Method test.pkg.Foo.bar changed visibility from public to protected [ChangedScope]
            """,
            signatureSource =
                """
                package test.pkg {
                  class Foo {
                    method protected void bar(Int);
                  }
                }
            """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  class Foo {
                    method public void bar(Int);
                  }
                }
            """
        )
    }

    @Test
    fun `Increase access, that is, from protected access to public access (Compatible)`() {
        check(
            signatureSource =
                """
                package test.pkg {
                  class Foo {
                    method public void bar(Int);
                  }
                }
            """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  class Foo {
                    method protected void bar(Int);
                  }
                }
            """
        )
    }

    @Test
    fun `Change abstract to non-abstract (Compatible)`() {
        check(
            signatureSource =
                """
                package test.pkg {
                  abstract class Foo {
                    method public void bar(Int);
                  }
                }
            """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  abstract class Foo {
                    method abstract public void bar(Int);
                  }
                }
            """
        )
    }

    @Test
    fun `Change non-abstract to abstract (Incompatible)`() {
        check(
            expectedIssues =
                """
               load-api.txt:4: error: Method test.pkg.Foo.bar has changed 'abstract' qualifier [ChangedAbstract]
            """,
            signatureSource =
                """
                package test.pkg {
                  abstract class Foo {
                    method abstract public void bar(Int);
                  }
                }
            """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  abstract class Foo {
                    method public void bar(Int);
                  }
                }
            """
        )
    }

    @Test
    fun `Change final to non-final (Compatible but Disallowed)`() {
        check(
            expectedIssues =
                """
               load-api.txt:4: error: Method test.pkg.Foo.bar has removed 'final' qualifier [RemovedFinalStrict]
            """,
            signatureSource =
                """
                package test.pkg {
                  class Foo {
                    method public void bar(Int);
                  }
                }
            """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  class Foo {
                    method final public void bar(Int);
                  }
                }
            """
        )
    }

    @Test
    fun `Change non-final to final (method not re-implementable) (Compatible)`() {
        check(
            signatureSource =
                """
                package test.pkg {
                  sealed class Foo {
                    method final public void bar(Int);
                  }
                }
            """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  sealed class Foo {
                    method public void bar(Int);
                  }
                }
            """
        )
    }

    @Test
    fun `Change non-final to final (method re-implementable) (Incompatible)`() {
        check(
            expectedIssues =
                """
               load-api.txt:5: error: Method test.pkg.Foo.bar has added 'final' qualifier [AddedFinal]
            """,
            signatureSource =
                """
                package test.pkg {
                  class Foo {
                    ctor public Foo();
                    method final public void bar(Int);
                  }
                }
            """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  class Foo {
                    ctor public Foo();
                    method public void bar(Int);
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
                  class Foo {
                    method public void bar(Int);
                  }
                }
            """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  class Foo {
                    method static public void bar(Int);
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
                  class Foo {
                    method static public void bar(Int);
                  }
                }
            """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  class Foo {
                    method public void bar(Int);
                  }
                }
            """
        )
    }

    @Test
    fun `Change native to non-native (Compatible)`() {
        check(
            signatureSource =
                """
                package test.pkg {
                  class Foo {
                    method native public void bar(Int);
                  }
                }
            """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  class Foo {
                    method public void bar(Int);
                  }
                }
            """
        )
    }

    @Test
    fun `Change non-native to native (Compatible)`() {
        check(
            signatureSource =
                """
                package test.pkg {
                  class Foo {
                    method public void bar(Int);
                  }
                }
            """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  class Foo {
                    method native public void bar(Int);
                  }
                }
            """
        )
    }

    @Test
    fun `Change synchronized to non-synchronized (Compatible)`() {
        check(
            signatureSource =
                """
                package test.pkg {
                  class Foo {
                    method public void bar(Int);
                  }
                }
            """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  class Foo {
                    method synchronized public void bar(Int);
                  }
                }
            """
        )
    }

    @Test
    fun `Change non-synchronized to synchronized (Compatible)`() {
        check(
            signatureSource =
                """
                package test.pkg {
                  class Foo {
                    method synchronized public void bar(Int);
                  }
                }
            """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  class Foo {
                    method public void bar(Int);
                  }
                }
            """
        )
    }

    /*
    TODO: Fix b/217229076 and uncomment this block of tests

    @Test
    fun `Add type parameter (existing type parameters) (Incompatible)`() {
        check(
            signatureSource = """
                package test.pkg {
                  class Foo {
                    method public <T, K> void bar();
                  }
                }
            """,
            checkCompatibilityApiReleased = """
                package test.pkg {
                  class Foo {
                    method public <T> void bar();
                  }
                }
            """
        )
    }
    @Test
    fun `Add type parameter (no existing type parameters) (Compatible)`() {
        check(
            signatureSource = """
                package test.pkg {
                  class Foo {
                    method public <T> void bar(Int);
                  }
                }
            """,
            checkCompatibilityApiReleased = """
                package test.pkg {
                  class Foo {
                    method public void bar(Int);
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
                    method public void bar(Int);
                  }
                }
            """,
            checkCompatibilityApiReleased = """
                package test.pkg {
                  class Foo {
                    method public <T> void bar(Int);
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
                    method public <T, K> void bar(Int);
                  }
                }
            """,
            checkCompatibilityApiReleased = """
                package test.pkg {
                  class Foo {
                    method public <K, T> void bar(Int);
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
                    method public <T> void bar(Int);
                  }
                }
            """,
            checkCompatibilityApiReleased = """
                package test.pkg {
                  class Foo {
                    method public <K> void bar(Int);
                  }
                }
            """
        )
    }
    @Test
    fun `Add, delete, or change type bounds of parameter (Incompatible)`() {
        check(
            signatureSource = """
                package test.pkg {
                  class Foo {
                    method public <T extends Foo> void bar(Int);
                  }
                }
            """,
            checkCompatibilityApiReleased = """
                package test.pkg {
                  class Foo {
                    method public <T> void bar(Int);
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
                    class Foo {
                        method public <T> void bar(T...);
                    }
                }
            """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                    class Foo {
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
                    class Foo {
                        method public <T> void bar(T[]);
                    }
                }
            """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                    class Foo {
                        method public <T> void bar(T...);
                    }
                }
            """
        )
    }
}
