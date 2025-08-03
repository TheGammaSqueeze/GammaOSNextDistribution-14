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
import com.android.tools.metalava.androidxNonNullSource
import com.android.tools.metalava.androidxNullableSource
import com.android.tools.metalava.testing.java
import org.junit.Test

/** Tests for the [ApiLint.checkBuilder] method. */
class CheckBuilderTest : DriverTest() {

    @Test
    fun `Check builders`() {
        check(
            apiLint = "", // enabled
            expectedIssues =
                """
                src/android/pkg/Bad.java:13: warning: Builder must be final: android.pkg.Bad.BadBuilder [StaticFinalBuilder]
                src/android/pkg/Bad.java:13: warning: Builder must be static: android.pkg.Bad.BadBuilder [StaticFinalBuilder]
                src/android/pkg/Bad.java:14: warning: Builder constructor arguments must be mandatory (i.e. not @Nullable): parameter badParameter in android.pkg.Bad.BadBuilder(String badParameter) [OptionalBuilderConstructorArgument]
                src/android/pkg/Bad.java:38: warning: Builder methods names should use setFoo() / addFoo() / clearFoo() style: method android.pkg.Bad.BadBuilder.withBadSetterStyle(boolean) [BuilderSetStyle]
                src/android/pkg/Bad.java:41: warning: Builder setter must be @NonNull: method android.pkg.Bad.BadBuilder.setReturnsNullable(boolean) [SetterReturnsThis]
                src/android/pkg/Bad.java:43: warning: Getter should be on the built object, not the builder: method android.pkg.Bad.BadBuilder.getOnBuilder() [GetterOnBuilder]
                src/android/pkg/Bad.java:45: warning: Methods must return the builder object (return type android.pkg.Bad.BadBuilder instead of void): method android.pkg.Bad.BadBuilder.setNotReturningBuilder(boolean) [SetterReturnsThis]
                src/android/pkg/Bad.java:20: warning: android.pkg.Bad does not declare a `getWithoutMatchingGetters()` method matching method android.pkg.Bad.BadBuilder.addWithoutMatchingGetter(String) [MissingGetterMatchingBuilder]
                src/android/pkg/Bad.java:23: warning: android.pkg.Bad does not declare a `isWithoutMatchingGetter()` method matching method android.pkg.Bad.BadBuilder.setWithoutMatchingGetter(boolean) [MissingGetterMatchingBuilder]
                src/android/pkg/Bad.java:26: warning: android.pkg.Bad does not declare a `getPluralWithoutMatchingGetters()` method matching method android.pkg.Bad.BadBuilder.addPluralWithoutMatchingGetter(java.util.Collection<java.lang.String>) [MissingGetterMatchingBuilder]
                src/android/pkg/Bad.java:32: warning: android.pkg.Bad does not declare a getter method matching method android.pkg.Bad.BadBuilder.addPluralWithoutMatchingGetters(java.util.Collection<java.lang.String>) (expected one of: [getPluralWithoutMatchingGetters(), getPluralWithoutMatchingGetterses()]) [MissingGetterMatchingBuilder]
                src/android/pkg/Bad.java:45: warning: android.pkg.Bad does not declare a `isNotReturningBuilder()` method matching method android.pkg.Bad.BadBuilder.setNotReturningBuilder(boolean) [MissingGetterMatchingBuilder]
                src/android/pkg/Bad.java:57: warning: Methods must return the builder object (return type android.pkg.Bad.BadGenericBuilder<T> instead of T): method android.pkg.Bad.BadGenericBuilder.setBoolean(boolean) [SetterReturnsThis]
                src/android/pkg/Bad.java:51: warning: android.pkg.Bad.NoBuildMethodBuilder does not declare a `build()` method, but builder classes are expected to [MissingBuildMethod]
                src/android/pkg/TopLevelBuilder.java:3: warning: Builder should be defined as inner class: android.pkg.TopLevelBuilder [TopLevelBuilder]
                src/android/pkg/TopLevelBuilder.java:3: warning: android.pkg.TopLevelBuilder does not declare a `build()` method, but builder classes are expected to [MissingBuildMethod]
                src/test/pkg/BadClass.java:4: warning: Builder must be final: test.pkg.BadClass.Builder [StaticFinalBuilder]
                src/test/pkg/BadInterface.java:4: warning: Builder must be final: test.pkg.BadInterface.Builder [StaticFinalBuilder]
                """,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package android.pkg;

                    public final class TopLevelBuilder {
                    }
                    """
                    ),
                    java(
                        """
                    package android.pkg;

                    import androidx.annotation.NonNull;
                    import androidx.annotation.Nullable;

                    public class Ok {

                        public int getInt();
                        @NonNull
                        public List<String> getStrings();
                        @NonNull
                        public List<String> getProperties();
                        @NonNull
                        public List<String> getRays();
                        @NonNull
                        public List<String> getBuses();
                        @NonNull
                        public List<String> getTaxes();
                        @NonNull
                        public List<String> getMessages();
                        public boolean isBoolean();
                        public boolean hasBoolean2();
                        public boolean shouldBoolean3();

                        public static final class OkBuilder {
                            public OkBuilder(@NonNull String goodParameter, int goodParameter2) {}

                            @NonNull
                            public Ok build() { return null; }

                            @NonNull
                            public OkBuilder setInt(int value) { return this; }

                            @NonNull
                            public OkBuilder addString(@NonNull String value) { return this; }

                            @NonNull
                            public OkBuilder addProperty(@NonNull String value) { return this; }

                            @NonNull
                            public OkBuilder addRay(@NonNull String value) { return this; }

                            @NonNull
                            public OkBuilder addBus(@NonNull String value) { return this; }

                            @NonNull
                            public OkBuilder addTax(@NonNull String value) { return this; }

                            @NonNull
                            public OkBuilder addMessages(@NonNull Collection<String> value) {
                                return this;
                            }

                            @NonNull
                            public OkBuilder clearStrings() { return this; }

                            @NonNull
                            public OkBuilder setBoolean(boolean v) { return this; }

                            @NonNull
                            public OkBuilder setHasBoolean2(boolean v) { return this; }

                            @NonNull
                            public OkBuilder setShouldBoolean3(boolean v) { return this; }

                            @NonNull
                            public OkBuilder clear() { return this; }

                            @NonNull
                            public OkBuilder clearAll() { return this; }
                        }

                        public static final class GenericBuilder<B extends GenericBuilder> {
                            @NonNull
                            public B setBoolean(boolean value) { return this; }

                            @NonNull
                            public Ok build() { return null; }
                        }
                    }
                    """
                    ),
                    java(
                        """
                    package android.pkg;

                    public class SubOk extends Ok {

                        public static final class Builder {
                            public Builder() {}

                            @NonNull
                            public SubOk build() { return null; }

                            @NonNull
                            public Builder setInt(int value) { return this; }
                        }
                    }
                    """
                    ),
                    java(
                        """
                    package android.pkg;

                    import androidx.annotation.NonNull;
                    import androidx.annotation.Nullable;
                    import java.util.Collection;

                    public class Bad {

                        public boolean isBoolean();
                        public boolean getWithoutMatchingGetter();
                        public boolean isReturnsNullable();

                        public class BadBuilder {
                            public BadBuilder(@Nullable String badParameter) {}

                            @NonNull
                            public Bad build() { return null; }

                            @NonNull
                            public BadBuilder addWithoutMatchingGetter(@NonNull String value) { return this; }

                            @NonNull
                            public BadBuilder setWithoutMatchingGetter(boolean v) { return this; }

                            @NonNull
                            public BadBuilder addPluralWithoutMatchingGetter(
                                @NonNull Collection<String> value) {
                                return this;
                            }

                            @NonNull
                            public BadBuilder addPluralWithoutMatchingGetters(
                                @NonNull Collection<String> value) {
                                return this;
                            }

                            @NonNull
                            public BadBuilder withBadSetterStyle(boolean v) { return this; }

                            @Nullable
                            public BadBuilder setReturnsNullable(boolean v) { return this; }

                            public boolean getOnBuilder() { return true; }

                            public void setNotReturningBuilder(boolean v) { return this; }

                            @NonNull
                            public BadBuilder () { return this; }
                        }

                        public static final class NoBuildMethodBuilder {
                            public NoBuildMethodBuilder() {}
                        }

                        public static final class BadGenericBuilder<T extends Bad> {
                            @NonNull
                            public T setBoolean(boolean value) { return this; }

                            @NonNull
                            public Bad build() { return null; }
                        }
                    }
                    """
                    ),
                    java(
                        """
                    package test.pkg;

                    public class GoodInterface {
                        public interface Builder extends java.lang.Runnable {
                            @NonNull
                            GoodInterface build();
                        }
                    }
                        """
                            .trimIndent()
                    ),
                    java(
                        """
                    package test.pkg;

                    public class GoodClass {
                        public static abstract class Builder extends Base {
                            @NonNull
                            public abstract GoodClass build();
                        }
                        public class Base {}
                    }
                        """
                            .trimIndent()
                    ),
                    java(
                        """
                    package test.pkg;

                    public class BadInterface {
                        public interface Builder {
                            @NonNull
                            BadInterface build();
                        }
                    }
                        """
                            .trimIndent()
                    ),
                    java(
                        """
                    package test.pkg;

                    public class BadClass {
                        public static abstract class Builder {
                            @NonNull
                            public abstract BadClass build();
                        }
                    }
                        """
                            .trimIndent()
                    ),
                    androidxNonNullSource,
                    androidxNullableSource
                )
        )
    }
}
