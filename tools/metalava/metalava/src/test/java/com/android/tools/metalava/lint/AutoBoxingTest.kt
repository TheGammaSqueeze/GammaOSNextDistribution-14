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

package com.android.tools.metalava.lint

import com.android.tools.metalava.DriverTest
import com.android.tools.metalava.androidxNullableSource
import com.android.tools.metalava.model.provider.Capability
import com.android.tools.metalava.model.testing.RequiresCapabilities
import com.android.tools.metalava.testing.java
import com.android.tools.metalava.testing.kotlin
import org.junit.Test

class AutoBoxingTest : DriverTest() {

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `Check boxed types`() {
        check(
            apiLint = "", // enabled
            expectedIssues =
                """
                src/test/pkg/KotlinClass.kt:4: error: Must avoid boxed primitives (`java.lang.Double`) [AutoBoxing]
                src/test/pkg/KotlinClass.kt:6: error: Must avoid boxed primitives (`java.lang.Boolean`) [AutoBoxing]
                src/test/pkg/MyClass.java:9: error: Must avoid boxed primitives (`java.lang.Long`) [AutoBoxing]
                src/test/pkg/MyClass.java:12: error: Must avoid boxed primitives (`java.lang.Short`) [AutoBoxing]
                src/test/pkg/MyClass.java:12: error: Must avoid boxed primitives (`java.lang.Double`) [AutoBoxing]
                src/test/pkg/MyClass.java:14: error: Must avoid boxed primitives (`java.lang.Boolean`) [AutoBoxing]
                src/test/pkg/MyClass.java:7: error: Must avoid boxed primitives (`java.lang.Integer`) [AutoBoxing]
                """,
            expectedFail = DefaultLintErrorMessage,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;

                    import androidx.annotation.Nullable;

                    public class MyClass {
                        @Nullable
                        public final Integer integer1;
                        public final int integer2;
                        public MyClass(@Nullable Long l) {
                        }
                        @Nullable
                        public Short getDouble(@Nullable Double l) { return null; }
                        @Nullable
                        public Boolean getBoolean() { return null; }
                    }
                    """
                    ),
                    kotlin(
                        """
                    package test.pkg
                    class KotlinClass {
                        fun getIntegerOk(): Double { TODO() }
                        fun getIntegerBad(): Double? { TODO() }
                        fun getBooleanOk(): Boolean { TODO() }
                        fun getBooleanBad(): Boolean? { TODO() }
                    }
                """
                    ),
                    androidxNullableSource
                )
        )
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `Check boxing of generic`() {
        check(
            apiLint = "", // enabled
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;

                    public class MyClass<T extends Number> {
                        public final T field;
                    }
                    """
                    ),
                    kotlin(
                        """
                    package test.pkg
                    interface KotlinClass<T: Number> {
                        val property: T
                    }
                """
                    ),
                )
        )
    }
}
