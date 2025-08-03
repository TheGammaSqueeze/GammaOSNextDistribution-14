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

class BinaryCompatibilityInterfaceFieldsTest : DriverTest() {

    @Test
    fun `Change type of API field (Incompatible)`() {
        check(
            expectedIssues =
                """
                load-api.txt:4: error: Field test.pkg.Foo.bar has changed type from int to java.lang.String [ChangedType]
            """,
            signatureSource =
                """
                package test.pkg {
                  public interface Foo {
                    field public final String bar;
                  }
                }
            """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                public interface Foo {
                  field public final int bar;
                }
                }
            """
        )
    }

    @Test
    fun `Change value of API field (Incompatible)`() {
        check(
            expectedIssues =
                """
                load-api.txt:4: error: Field test.pkg.Foo.bar has changed value from 8 to 7 [ChangedValue]
            """,
            signatureSource =
                """
                package test.pkg {
                  public interface Foo {
                    field public static final int bar = 7;
                  }
                }
            """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                public interface Foo {
                  field public static final int bar = 8;
                }
                }
            """
        )
    }
}
