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
 * Tests for binary compatibility of modifications to classes derived from
 * https://wiki.eclipse.org/Evolving_Java-based_APIs_2#Evolving_API_Classes
 */
class BinaryCompatibilityClassesTest : DriverTest() {
    @Test
    fun `Add API method, if method need not be reimplemented by client (Compatible)`() {
        check(
            signatureSource =
                """
                package test.pkg {
                    public abstract class Foo {
                        ctor public Foo();
                        method public final void bar();
                    }
                }
            """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                    public abstract class Foo {
                        ctor public Foo();
                    }
                }
            """
        )
    }

    @Test
    fun `Add API method, if method must be reimplemented by client (Incompatible)`() {
        check(
            expectedIssues =
                """
                load-api.txt:5: error: Added method test.pkg.Foo.bar() [AddedAbstractMethod]
            """,
            signatureSource =
                """
                package test.pkg {
                    public abstract class Foo {
                        ctor public Foo();
                        method public abstract void bar();
                    }
                }
            """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                    public abstract class Foo {
                        ctor public Foo();
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
            """,
            signatureSource =
                """
                package test.pkg {
                    public class Foo {
                    }
                }
            """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                    public class Foo {
                        method public void bar();
                    }
                }
            """
        )
    }

    @Test
    fun `Move API method up type hierarchy, if method in supertype need not be reimplemented by client (Compatible)`() {
        check(
            signatureSource =
                """
                package test.pkg {
                    public class Upper {
                        method public void foo();
                    }
                    public class Lower extends test.pkg.Upper {
                    }
                }
            """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                    public class Upper {
                    }
                    public class Lower extends test.pkg.Upper {
                        method public void foo();
                    }
                }
            """
        )
    }

    @Test
    fun `Move API method up type hierarchy, if method in supertype must be reimplemented by client (Incompatible)`() {
        check(
            expectedIssues =
                """
                load-api.txt:5: error: Added method test.pkg.Upper.foo() [AddedAbstractMethod]
            """,
            signatureSource =
                """
                package test.pkg {
                    public abstract class Upper {
                        ctor public Upper();
                        method public abstract void foo();
                    }
                    public abstract class Lower extends test.pkg.Upper {
                        ctor public Lower();
                    }
                }
            """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                    public abstract class Upper {
                        ctor public Upper();
                    }
                    public abstract class Lower extends test.pkg.Upper {
                        ctor public Lower();
                        method public abstract void foo();
                    }
                }
            """
        )
    }

    @Test
    fun `Move API method down type hierarchy (Incompatible)`() {
        check(
            expectedIssues =
                """
                released-api.txt:4: error: Removed method test.pkg.Upper.foo() [RemovedMethod]
            """,
            signatureSource =
                """
                package test.pkg {
                    public class Upper {
                    }
                    public class Lower extends test.pkg.Upper {
                        method public void foo();
                    }
                }
            """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                    public class Upper {
                        method public void foo();
                    }
                    public class Lower extends test.pkg.Upper {
                    }
                }
            """
        )
    }

    @Test
    fun `Add API constructor, if there are other constructors (Compatible)`() {
        check(
            signatureSource =
                """
                package test.pkg {
                    public class Foo {
                        ctor public Foo(int);
                        ctor public Foo(int, int);
                    }
                }
            """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                    public class Foo {
                        ctor public Foo(int);
                    }
                }
            """
        )
    }

    @Test
    fun `Add API constructor, if this is the only constructor (Incompatible)`() {
        check(
            expectedIssues =
                """
                released-api.txt:4: error: Removed constructor test.pkg.Foo() [RemovedMethod]
            """,
            signatureSource =
                """
                package test.pkg {
                    public class Foo {
                        ctor public Foo(int);
                    }
                }
            """,
            // A default constructor would be tracked as a zero-arg constructor in the signature
            // file, as below. (Indistinguishable from a hand-coded zero-arg constructor)
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                    public class Foo {
                        ctor public Foo();
                    }
                }
            """
        )
    }

    @Test
    fun `Delete API constructor (Incompatible)`() {
        check(
            expectedIssues =
                """
                released-api.txt:4: error: Removed constructor test.pkg.Foo() [RemovedMethod]
            """,
            signatureSource =
                """
                package test.pkg {
                    public class Foo {
                    }
                }
            """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                    public class Foo {
                        ctor public Foo();
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
                    public final class Foo {
                        field public int bar;
                    }
                }
            """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                    public final class Foo {
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
                released-api.txt:4: error: Removed field test.pkg.Foo.bar [RemovedField]
            """,
            signatureSource =
                """
                package test.pkg {
                    public class Foo {
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
    fun `Expand superinterface set (Compatible)`() {
        check(
            signatureSource =
                """
                package test.pkg {
                    public interface One {
                    }
                    public interface Two {
                    }
                    public class Foo implements test.pkg.One, test.pkg.Two {
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
                    public class Foo implements test.pkg.One {
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
                    public class Foo implements test.pkg.One {
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
                    public class Foo implements test.pkg.One, test.pkg.Two {
                    }
                }
            """
        )
    }

    @Test
    fun `Expand superclass set (Compatible)`() {
        check(
            signatureSource =
                """
                package test.pkg {
                    public class Upper {
                    }
                    public class Middle extends test.pkg.Upper {
                    }
                    public class Lower extends test.pkg.Middle {
                    }
                }
            """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                    public class Upper {
                    }
                    public class Lower extends test.pkg.Upper {
                    }
                }
            """
        )
    }

    @Test
    fun `Contract superclass set (Incompatible)`() {
        check(
            expectedIssues =
                """
                load-api.txt:7: error: Class test.pkg.Foo superclass changed from test.pkg.Baz to test.pkg.Bar [ChangedSuperclass]
            """,
            signatureSource =
                """
                package test.pkg {
                    public class Bar {
                    }
                    public class Baz extends test.pkg.Bar {
                    }
                    public class Foo extends test.pkg.Bar {
                    }
                }
            """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                    public class Bar {
                    }
                    public class Baz extends test.pkg.Bar {
                    }
                    public class Foo extends test.pkg.Baz {
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
                    public class Outer {
                    }
                    public class Outer.Inner {
                    }
                }
            """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                    public class Outer {
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
                released-api.txt:5: error: Removed class test.pkg.Outer.Inner [RemovedClass]
            """,
            signatureSource =
                """
                package test.pkg {
                    public class Outer {
                    }
                }
            """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                    public class Outer {
                    }
                    public class Outer.Inner {
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
                    public class Foo {
                    }
                }
            """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                    public abstract class Foo {
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
                load-api.txt:3: error: Class test.pkg.Foo changed 'abstract' qualifier [ChangedAbstract]
            """,
            signatureSource =
                """
                package test.pkg {
                    public abstract class Foo {
                    }
                }
            """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                    public class Foo {
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
                load-api.txt:4: error: Constructor test.pkg.Foo has removed 'final' qualifier [RemovedFinalStrict]
            """,
            signatureSource =
                """
                package test.pkg {
                    public class Foo {
                        ctor public Foo();
                    }
                }
            """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                    public final class Foo {
                        ctor public Foo();
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
                load-api.txt:3: error: Class test.pkg.Foo added 'final' qualifier [AddedFinal]
                load-api.txt:4: error: Constructor test.pkg.Foo has added 'final' qualifier [AddedFinal]
            """,
            signatureSource =
                """
                package test.pkg {
                    public final class Foo {
                        ctor public Foo();
                    }
                }
            """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                    public class Foo {
                        ctor public Foo();
                    }
                }
            """
        )
    }

    @Test
    fun `Add type parameter, if class has no type parameters (Compatible)`() {
        check(
            signatureSource =
                """
                package test.pkg {
                    public class Foo<A> {
                    }
                }
            """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                    public class Foo {
                    }
                }
            """
        )
    }

    @Test
    fun `Add type parameter, if class has type parameters (Incompatible)`() {
        check(
            expectedIssues =
                """
                load-api.txt:3: error: Class test.pkg.Foo changed number of type parameters from 1 to 2 [ChangedType]
            """,
            signatureSource =
                """
                package test.pkg {
                    public class Foo<A, B> {
                    }
                }
            """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                    public class Foo<A> {
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
                load-api.txt:3: error: Class test.pkg.Bar changed number of type parameters from 1 to 0 [ChangedType]
                load-api.txt:5: error: Class test.pkg.Foo changed number of type parameters from 2 to 1 [ChangedType]
            """,
            signatureSource =
                """
                package test.pkg {
                    public class Bar {
                    }
                    public class Foo<A> {
                    }
                }
            """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                    public class Bar<A> {
                    }
                    public class Foo<A, B> {
                    }
                }
            """
        )
    }

    @Ignore("b/217746739")
    @Test
    fun `Reorder type parameters (Incompatible)`() {
        check(
            expectedIssues = """
                (expected issue for class Foo)
            """,
            signatureSource =
                """
                package test.pkg {
                    public class Foo<B, A> {
                    }
                }
            """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                    public class Foo<A, B> {
                    }
                }
            """
        )
    }

    @Test
    fun `Rename type parameter (Compatible)`() {
        check(
            signatureSource =
                """
                package test.pkg {
                    public class Foo<B> {
                    }
                }
            """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                    public class Foo<A> {
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
                (expected issue for class Add)
                (expected issue for class Change)
                (expected issue for class Delete)
            """,
            signatureSource =
                """
                package test.pkg {
                    public class Add<T extends java.util.List> {
                    }
                    public class Change<T extends java.util.List> {
                    }
                    public class Delete<T> {
                    }
                }
            """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                    public class Add<T> {
                    }
                    public class Change<T extends java.util.Map> {
                    }
                    public class Delete<T extends java.util.List> {
                    }
                }
            """
        )
    }

    @Test
    fun `Rename enum constant (Incompatible)`() {
        check(
            expectedIssues =
                """
                released-api.txt:4: error: Removed enum constant test.pkg.Foo.OLD [RemovedField]
            """,
            signatureSource =
                """
                package test.pkg {
                    public enum Foo {
                        enum_constant public static final test.pkg.Foo NEW;
                    }
                }
            """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                    public enum Foo {
                        enum_constant public static final test.pkg.Foo OLD;
                    }
                }
            """
        )
    }

    @Test
    fun `Add enum constant (Compatible)`() {
        check(
            signatureSource =
                """
                package test.pkg {
                    public enum Foo {
                        enum_constant public static final test.pkg.Foo ONE;
                        enum_constant public static final test.pkg.Foo TWO;
                    }
                }
            """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                    public enum Foo {
                        enum_constant public static final test.pkg.Foo ONE;
                    }
                }
            """
        )
    }

    @Test
    fun `Delete enum constant (Incompatible)`() {
        check(
            expectedIssues =
                """
                released-api.txt:5: error: Removed enum constant test.pkg.Foo.TWO [RemovedField]
            """,
            signatureSource =
                """
                package test.pkg {
                    public enum Foo {
                        enum_constant public static final test.pkg.Foo ONE;
                    }
                }
            """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                    public enum Foo {
                        enum_constant public static final test.pkg.Foo ONE;
                        enum_constant public static final test.pkg.Foo TWO;
                    }
                }
            """
        )
    }

    @Test
    fun `Re-order enum constants (Compatible)`() {
        check(
            signatureSource =
                """
                package test.pkg {
                    public enum Foo {
                        enum_constant public static final test.pkg.Foo TWO;
                        enum_constant public static final test.pkg.Foo ONE;
                    }
                }
            """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                    public enum Foo {
                        enum_constant public static final test.pkg.Foo ONE;
                        enum_constant public static final test.pkg.Foo TWO;
                    }
                }
            """
        )
    }
}
