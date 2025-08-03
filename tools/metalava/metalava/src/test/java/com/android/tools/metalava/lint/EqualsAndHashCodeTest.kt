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

package com.android.tools.metalava.lint

import com.android.tools.metalava.DriverTest
import com.android.tools.metalava.androidxNullableSource
import com.android.tools.metalava.reporter.Issues
import com.android.tools.metalava.testing.java
import org.junit.Test

/** Tests for the [Issues.EQUALS_AND_HASH_CODE] issue. */
class EqualsAndHashCodeTest : DriverTest() {

    @Suppress("EqualsWhichDoesntCheckParameterClass")
    @Test
    fun `Test equals and hashCode`() {
        check(
            apiLint = "", // enabled
            expectedIssues =
                """
                src/android/pkg/MissingEquals.java:4: error: Must override both equals and hashCode; missing one in android.pkg.MissingEquals [EqualsAndHashCode]
                src/android/pkg/MissingHashCode.java:6: error: Must override both equals and hashCode; missing one in android.pkg.MissingHashCode [EqualsAndHashCode]
                """,
            expectedFail = DefaultLintErrorMessage,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package android.pkg;

                    import androidx.annotation.Nullable;

                    public class Ok {
                        public boolean equals(@Nullable Object other) { return true; }
                        public int hashCode() { return 0; }
                    }
                    """
                    ),
                    java(
                        """
                    package android.pkg;

                    public class MissingEquals {
                        public int hashCode() { return 0; }
                    }
                    """
                    ),
                    java(
                        """
                    package android.pkg;

                    import androidx.annotation.Nullable;

                    public class MissingHashCode {
                        public boolean equals(@Nullable Object other) { return true; }
                    }
                    """
                    ),
                    java(
                        """
                    package android.pkg;

                    import androidx.annotation.Nullable;

                    public class UnrelatedEquals {
                        public static boolean equals(@Nullable Object other) { return true; } // static
                        public boolean equals(int other) { return false; } // wrong parameter type
                        public boolean equals(@Nullable Object other, int bar) { return false; } // wrong signature
                    }
                    """
                    ),
                    androidxNullableSource
                )
        )
    }
}
