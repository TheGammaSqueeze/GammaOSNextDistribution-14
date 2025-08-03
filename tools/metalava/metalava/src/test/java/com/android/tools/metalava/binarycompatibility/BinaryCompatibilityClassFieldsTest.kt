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

class BinaryCompatibilityClassFieldsTest : DriverTest() {

    @Test
    fun `Change type of API field (Incompatible)`() {
        check(
            expectedIssues =
                """
                load-api.txt:4: error: Field test.pkg.Foo.bar has changed type from java.lang.String to int [ChangedType]
            """,
            signatureSource =
                """
                package test.pkg {
                  public class Foo {
                    field public int bar;
                  }
                }
            """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  public class Foo {
                    field public String bar;
                  }
                }
            """
        )
    }

    @Test
    fun `Change value of API field, compile-time constant (Incompatible)`() {
        check(
            expectedIssues =
                """
                load-api.txt:4: error: Field test.pkg.Foo.bar has changed value from 8 to 7 [ChangedValue]
            """,
            signatureSource =
                """
                package test.pkg {
                  public class Foo {
                    field public static final int bar = 7;
                  }
                }
            """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  public class Foo {
                    field public static final int bar = 8;
                  }
                }
            """
        )
    }

    @Test
    fun `Decrease access from protected to default or private, or public to protected, default, or private (Incompatible)`() {
        check(
            expectedIssues =
                """
                load-api.txt:4: error: Field test.pkg.Foo.bar changed visibility from protected to private [ChangedScope]
                load-api.txt:5: error: Field test.pkg.Foo.baz changed visibility from public to protected [ChangedScope]
            """,
            signatureSource =
                """
                package test.pkg {
                  public class Foo {
                    field private static final int bar = 8;
                    field protected static final int baz = 8;
                  }
                }
            """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  public class Foo {
                    field protected static final int bar = 8;
                    field public static final int baz = 8;
                  }
                }
            """
        )
    }

    @Test
    fun `Increase access, eg from protected to public (Compatible)`() {
        check(
            signatureSource =
                """
                package test.pkg {
                  public class Foo {
                    field protected static final int bar = 8;
                    field public static final int baz = 8;
                  }
                }
            """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  public class Foo {
                    field private static final int bar = 8;
                    field protected static final int baz = 8;
                  }
                }
            """
        )
    }

    @Test
    fun `Change final to non-final, non-static (Compatible)`() {
        check(
            signatureSource =
                """
                package test.pkg {
                  public class Foo {
                    field public int bar;
                  }
                }
            """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  public class Foo {
                    field public final int bar;
                  }
                }
            """
        )
    }

    @Test
    fun `Change final to non-final, static with compile-time constant value (Incompatible)`() {
        check(
            expectedIssues =
                """
                load-api.txt:4: error: Field test.pkg.Foo.bar has removed 'final' qualifier [RemovedFinal]
            """,
            signatureSource =
                """
                package test.pkg {
                  public class Foo {
                    field public static int bar = 0;
                  }
                }
            """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  public class Foo {
                    field public static final int bar = 0;
                  }
                }
            """
        )
    }

    @Test
    fun `Change non-final to final (Incompatible)`() {
        check(
            expectedIssues =
                """
                load-api.txt:4: error: Field test.pkg.Foo.bar has added 'final' qualifier [AddedFinal]
            """,
            signatureSource =
                """
                package test.pkg {
                  public class Foo {
                    field public final int bar;
                  }
                }
            """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  public class Foo {
                    field public int bar;
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
                load-api.txt:4: error: Field test.pkg.Foo.bar has changed 'static' qualifier [ChangedStatic]
            """,
            signatureSource =
                """
                package test.pkg {
                  public class Foo {
                    field public int bar = 0;
                  }
                }
            """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  public class Foo {
                    field public static int bar = 0;
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
                load-api.txt:4: error: Field test.pkg.Foo.bar has changed 'static' qualifier [ChangedStatic]
            """,
            signatureSource =
                """
                package test.pkg {
                  public class Foo {
                    field public static int bar = 0;
                  }
                }
            """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  public class Foo {
                    field public int bar = 0;
                  }
                }
            """
        )
    }

    @Test
    fun `Change transient to non-transient (Compatible)`() {
        check(
            signatureSource =
                """
                package test.pkg {
                  public class Foo {
                    field public int bar = 0;
                  }
                }
            """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  public class Foo {
                    field public transient int bar = 0;
                  }
                }
            """
        )
    }

    @Test
    fun `Change non-transient to transient (Compatible)`() {
        check(
            signatureSource =
                """
                package test.pkg {
                  public class Foo {
                    field public transient int bar = 0;
                  }
                }
            """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  public class Foo {
                    field public int bar = 0;
                  }
                }
            """
        )
    }
}
