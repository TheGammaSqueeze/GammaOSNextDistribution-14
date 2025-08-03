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
import org.junit.Ignore
import org.junit.Test

/**
 * Tests for binary compatibility of modifications to interfaces derived from
 * https://wiki.eclipse.org/Evolving_Java-based_APIs_2#Evolving_API_Interfaces
 */
class BinaryCompatibilityInterfacesTest : DriverTest() {
    @Ignore("b/220960090")
    @Test
    fun `Add abstract method, if method need not be implemented by client (Compatible)`() {
        check(
            signatureSource =
                """
                package test.pkg {
                    public sealed interface Foo {
                        method public abstract void bar();
                    }
                }
            """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                    public sealed interface Foo {
                    }
                }
            """
        )
    }

    @Test
    fun `Add abstract method, if method must be implemented by client (Incompatible)`() {
        check(
            expectedIssues =
                """
                load-api.txt:4: error: Added method test.pkg.Foo.bar() [AddedAbstractMethod]
            """,
            signatureSource =
                """
                package test.pkg {
                    public interface Foo {
                        method public abstract void bar();
                    }
                }
            """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                    public interface Foo {
                    }
                }
            """
        )
    }

    @Ignore("b/220960090")
    @Test
    fun `Add default method, if interface not implementable by clients (Compatible)`() {
        check(
            signatureSource =
                """
                package test.pkg {
                    public sealed interface Foo {
                        method public default void bar();
                    }
                }
            """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                    public sealed interface Foo {
                    }
                }
            """
        )
    }

    @Ignore("b/222739015")
    @Test
    fun `Add default method, if interface implementable by clients (Incompatible)`() {
        check(
            expectedIssues = """
                (expected issue for interface Foo)
            """,
            signatureSource =
                """
                package test.pkg {
                    public interface Foo {
                        method public default void bar();
                    }
                }
            """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                    public interface Foo {
                    }
                }
            """
        )
    }

    @Test
    fun `Add static method (Compatible)`() {
        check(
            signatureSource =
                """
                package test.pkg {
                    public interface Foo {
                        method public static void bar();
                    }
                }
            """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                    public interface Foo {
                    }
                }
            """
        )
    }

    @Test
    fun `Delete API method (Incompatible)`() {
        check(
            expectedIssues =
                """
                released-api.txt:4: error: Removed method test.pkg.Foo.bar() [RemovedMethod]
                released-api.txt:6: error: Removed method test.pkg.Foo.bax() [RemovedMethod]
                released-api.txt:5: error: Removed method test.pkg.Foo.baz() [RemovedMethod]
            """,
            signatureSource =
                """
                package test.pkg {
                    public interface Foo {
                    }
                }
            """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                    public interface Foo {
                        method public abstract void bar();
                        method public default void baz();
                        method public static void bax();
                    }
                }
            """
        )
    }

    @Ignore("b/220960090")
    @Test
    fun `Move API method up type hierarchy, if method in supertype need not be implemented by client (Compatible)`() {
        check(
            signatureSource =
                """
                package test.pkg {
                    public sealed interface Upper {
                        method public abstract void bar();
                    }
                    public sealed interface Lower extends test.pkg.Upper {
                    }
                }
            """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                    public sealed interface Upper {
                    }
                    public sealed interface Lower extends test.pkg.Upper {
                        method public abstract void bar();
                    }
                }
            """
        )
    }

    @Test
    fun `Move API method up the type hierarchy, if method in supertype must be implemented by client (Incompatible)`() {
        check(
            expectedIssues =
                """
                load-api.txt:4: error: Added method test.pkg.Upper.bar() [AddedAbstractMethod]
            """,
            signatureSource =
                """
                package test.pkg {
                    public interface Upper {
                        method public abstract void bar();
                    }
                    public interface Lower extends test.pkg.Upper {
                    }
                }
            """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                    public interface Upper {
                    }
                    public interface Lower extends test.pkg.Upper {
                        method public abstract void bar();
                    }
                }
            """
        )
    }

    @Test
    fun `Move method down type hierarchy (Incompatible)`() {
        check(
            expectedIssues =
                """
                released-api.txt:4: error: Removed method test.pkg.Upper.bar() [RemovedMethod]
            """,
            signatureSource =
                """
                package test.pkg {
                    public interface Upper {
                    }
                    public interface Lower extends test.pkg.Upper {
                        method public abstract void bar();
                    }
                }
            """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                    public interface Upper {
                        method public abstract void bar();
                    }
                    public interface Lower extends test.pkg.Upper {
                    }
                }
            """
        )
    }

    @Test
    fun `Add API field (Compatible)`() {
        check(
            signatureSource =
                """
                package test.pkg {
                    public interface Foo {
                        field public static final int BAR;
                    }
                }
            """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                    public interface Foo {
                    }
                }
            """
        )
    }

    @Test
    fun `Delete API field (Incompatible)`() {
        check(
            expectedIssues =
                """
                released-api.txt:4: error: Removed field test.pkg.Foo.BAR [RemovedField]
            """,
            signatureSource =
                """
                package test.pkg {
                    public interface Foo {
                    }
                }
            """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                    public interface Foo {
                        field public static final int BAR;
                    }
                }
            """
        )
    }

    @Test
    fun `Expand superinterfaces set (Compatible)`() {
        check(
            signatureSource =
                """
                package test.pkg {
                    public interface One {
                    }
                    public interface Two {
                    }
                    public interface Foo extends test.pkg.One test.pkg.Two {
                    }
                }
            """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                    public interface One {
                    }
                    public interface Two {
                    }
                    public interface Foo extends test.pkg.One {
                    }
                }
            """
        )
    }

    @Test
    fun `Contract superinterface set (Incompatible)`() {
        check(
            expectedIssues =
                """
                load-api.txt:7: error: Class test.pkg.Foo no longer implements test.pkg.Two [RemovedInterface]
            """,
            signatureSource =
                """
                package test.pkg {
                    public interface One {
                    }
                    public interface Two {
                    }
                    public interface Foo extends test.pkg.One {
                    }
                }
            """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                    public interface One {
                    }
                    public interface Two {
                    }
                    public interface Foo extends test.pkg.One test.pkg.Two {
                    }
                }
            """
        )
    }

    @Test
    fun `Add API type member (Compatible)`() {
        check(
            signatureSource =
                """
                package test.pkg {
                    public interface Outer {
                    }
                    public interface Outer.Inner {
                    }
                }
            """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                    public interface Outer {
                    }
                }
            """
        )
    }

    @Test
    fun `Delete API type member (Incompatible)`() {
        check(
            expectedIssues =
                """
                released-api.txt:5: error: Removed class test.pkg.Outer.Inner [RemovedInterface]
            """,
            signatureSource =
                """
                package test.pkg {
                    public interface Outer {
                    }
                }
            """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                    public interface Outer {
                    }
                    public interface Outer.Inner {
                    }
                }
            """
        )
    }

    @Test
    fun `Add type parameter, if interface has no type parameters (Compatible)`() {
        check(
            signatureSource =
                """
                package test.pkg {
                    public interface Foo<T> {
                    }
                }
            """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                    public interface Foo {
                    }
                }
            """
        )
    }

    @Test
    fun `Add type parameter, if interface has type parameters (Incompatible)`() {
        check(
            expectedIssues =
                """
                load-api.txt:3: error: Class test.pkg.Foo changed number of type parameters from 1 to 2 [ChangedType]
            """,
            signatureSource =
                """
                package test.pkg {
                    public interface Foo<A, B> {
                    }
                }
            """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                    public interface Foo<A> {
                    }
                }
            """
        )
    }

    @Test
    fun `Delete type parameter (Incompatible)`() {
        check(
            expectedIssues =
                """
                load-api.txt:3: error: Class test.pkg.Foo changed number of type parameters from 1 to 0 [ChangedType]
            """,
            signatureSource =
                """
                package test.pkg {
                    public interface Foo {
                    }
                }
            """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                    public interface Foo<T> {
                    }
                }
            """
        )
    }

    @Ignore("b/217746739")
    @Test
    fun `Re-order type parameters (Incompatible)`() {
        check(
            expectedIssues = """
                (expected issue for interface Foo)
            """,
            signatureSource =
                """
                package test.pkg {
                    public interface Foo<B, A> {
                    }
                }
            """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                    public interface Foo<A, B> {
                    }
                }
            """
        )
    }

    @Ignore("b/217746739")
    @Test
    fun `Rename type parameter (Incompatible)`() {
        check(
            expectedIssues = """
                (expected issue for interface Foo)
            """,
            signatureSource =
                """
                package test.pkg {
                    public interface Foo<B> {
                    }
                }
            """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                    public interface Foo<A> {
                    }
                }
            """
        )
    }

    @Ignore("b/217747331")
    @Test
    fun `Add, delete, or change type bounds of type parameter (Incompatible)`() {
        check(
            expectedIssues =
                """
                (expected issue for interface Add)
                (expected issue for interface Change)
                (expected issue for interface Delete)
            """,
            signatureSource =
                """
                package test.pkg {
                    public interface Add<T extends java.util.List> {
                    }
                    public interface Change<T extends java.util.List> {
                    }
                    public interface Delete<T> {
                    }
                }
            """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                    public interface Add<T> {
                    }
                    public interface Change<T extends java.util.Map> {
                    }
                    public interface Delete<T extends java.util.List> {
                    }
                }
            """
        )
    }

    @Test
    fun `Add element to annotation type, if element has a default value (Compatible)`() {
        check(
            signatureSource =
                """
                package test.pkg {
                    public @interface Foo {
                        method public abstract int bar() default 0;
                    }
                }
            """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                    public @interface Foo {
                    }
                }
            """
        )
    }

    @Test
    fun `Add element to annotation type, if element has no default value (Incompatible)`() {
        check(
            expectedIssues =
                """
                load-api.txt:4: error: Added method test.pkg.Foo.bar() [AddedAbstractMethod]
            """,
            signatureSource =
                """
                package test.pkg {
                    public interface Foo {
                        method public abstract int bar();
                    }
                }
            """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                    public interface Foo {
                    }
                }
            """
        )
    }

    @Test
    fun `Delete element from annotation type (Incompatible)`() {
        check(
            expectedIssues =
                """
                released-api.txt:4: error: Removed method test.pkg.Foo.bar() [RemovedMethod]
                released-api.txt:5: error: Removed method test.pkg.Foo.baz() [RemovedMethod]
            """,
            signatureSource =
                """
                package test.pkg {
                    public interface Foo {
                    }
                }
            """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                    public interface Foo {
                        method public abstract int bar();
                        method public abstract int baz() default 0;
                    }
                }
            """
        )
    }
}
