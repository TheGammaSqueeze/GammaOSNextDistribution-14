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

package com.android.tools.metalava.testing

import com.android.tools.lint.checks.infrastructure.TestFile
import com.android.tools.lint.checks.infrastructure.TestFiles

object KnownSourceFiles {

    val notTypeUseNonNullSource: TestFile =
        TestFiles.java(
            """
                package not.type.use;
                public @interface NonNull {
                }
            """
        )

    val notTypeUseNullableSource: TestFile =
        TestFiles.java(
            """
                package not.type.use;
                public @interface Nullable {
                }
            """
        )

    val nonNullSource: TestFile =
        TestFiles.java(
            """
    package android.annotation;
    import java.lang.annotation.*;
    import static java.lang.annotation.ElementType.*;
    import static java.lang.annotation.RetentionPolicy.CLASS;
    /**
     * Denotes that a parameter, field or method return value can never be null.
     * @paramDoc This value must never be {@code null}.
     * @returnDoc This value will never be {@code null}.
     * @hide
     */
    @SuppressWarnings({"WeakerAccess", "JavaDoc"})
    @Retention(CLASS)
    @Target({METHOD, PARAMETER, FIELD, TYPE_USE})
    public @interface NonNull {
    }
    """
        )

    val nullableSource: TestFile =
        TestFiles.java(
            """
    package android.annotation;
    import java.lang.annotation.*;
    import static java.lang.annotation.ElementType.*;
    import static java.lang.annotation.RetentionPolicy.CLASS;
    /**
     * Denotes that a parameter, field or method return value can be null.
     * @paramDoc This value may be {@code null}.
     * @returnDoc This value may be {@code null}.
     * @hide
     */
    @SuppressWarnings({"WeakerAccess", "JavaDoc"})
    @Retention(CLASS)
    @Target({METHOD, PARAMETER, FIELD, TYPE_USE})
    public @interface Nullable {
    }
    """
        )

    val libcoreNonNullSource: TestFile =
        TestFiles.java(
            """
    package libcore.util;
    import static java.lang.annotation.ElementType.*;
    import static java.lang.annotation.RetentionPolicy.SOURCE;
    import java.lang.annotation.*;
    @Documented
    @Retention(SOURCE)
    @Target({TYPE_USE})
    public @interface NonNull {
    }
    """
        )

    val libcoreNullableSource: TestFile =
        TestFiles.java(
            """
    package libcore.util;
    import static java.lang.annotation.ElementType.*;
    import static java.lang.annotation.RetentionPolicy.SOURCE;
    import java.lang.annotation.*;
    @Documented
    @Retention(SOURCE)
    @Target({TYPE_USE})
    public @interface Nullable {
    }
    """
        )

    /**
     * The version of the Jetbrains nullness annotations used by metalava is not type-use, but the
     * latest version is.
     */
    val jetbrainsNullableTypeUseSource: TestFile =
        TestFiles.java(
            """
    package org.jetbrains.annotations;
    @java.lang.annotation.Target({ java.lang.annotation.ElementType.METHOD, java.lang.annotation.ElementType.FIELD, java.lang.annotation.ElementType.PARAMETER, java.lang.annotation.ElementType.LOCAL_VARIABLE, java.lang.annotation.ElementType.TYPE_USE })
    public @interface Nullable {}
            """
        )

    /** TYPE_USE version of [com.android.tools.metalava.intRangeAnnotationSource] */
    val intRangeTypeUseSource =
        java(
            """
        package androidx.annotation;
        import java.lang.annotation.*;
        import static java.lang.annotation.ElementType.*;
        import static java.lang.annotation.RetentionPolicy.SOURCE;
        @Retention(SOURCE)
        @Target({METHOD,PARAMETER,FIELD,LOCAL_VARIABLE,ANNOTATION_TYPE,TYPE_USE})
        public @interface IntRange {
            long from() default Long.MIN_VALUE;
            long to() default Long.MAX_VALUE;
        }
        """
        )

    val supportParameterName =
        java(
            """
                package androidx.annotation;
                import java.lang.annotation.*;
                import static java.lang.annotation.ElementType.*;
                import static java.lang.annotation.RetentionPolicy.SOURCE;
                @SuppressWarnings("WeakerAccess")
                @Retention(SOURCE)
                @Target({METHOD, PARAMETER, FIELD})
                public @interface ParameterName {
                    String value();
                }
            """
        )
}
