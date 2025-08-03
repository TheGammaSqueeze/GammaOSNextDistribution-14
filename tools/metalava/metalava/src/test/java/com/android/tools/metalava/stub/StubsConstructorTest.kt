/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.tools.metalava.stub

import com.android.tools.metalava.model.text.FileFormat
import com.android.tools.metalava.testing.java
import org.junit.Test

@SuppressWarnings("ALL")
class StubsConstructorTest : AbstractStubsTest() {

    @Test
    fun `Generate stubs for class that should not get default constructor (has other constructors)`() {
        // Class without explicit constructors (shouldn't insert default constructor)
        checkStubs(
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;
                    public class Foo {
                        public Foo(int i) {

                        }
                        public Foo(int i, int j) {
                        }
                    }
                    """
                    )
                ),
            source =
                """
                package test.pkg;
                @SuppressWarnings({"unchecked", "deprecation", "all"})
                public class Foo {
                public Foo(int i) { throw new RuntimeException("Stub!"); }
                public Foo(int i, int j) { throw new RuntimeException("Stub!"); }
                }
                """
        )
    }

    @Test
    fun `Generate stubs for class that already has a private constructor`() {
        // Class without private constructor; no default constructor should be inserted
        checkStubs(
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;
                    public class Foo {
                        private Foo() {
                        }
                    }
                    """
                    )
                ),
            source =
                """
                package test.pkg;
                @SuppressWarnings({"unchecked", "deprecation", "all"})
                public class Foo {
                Foo() { throw new RuntimeException("Stub!"); }
                }
                """
        )
    }

    @Test
    fun `Arguments to super constructors`() {
        // When overriding constructors we have to supply arguments
        checkStubs(
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;

                    @SuppressWarnings("WeakerAccess")
                    public class Constructors {
                        public class Parent {
                            public Parent(String arg1, int arg2, long arg3, boolean arg4, short arg5) {
                            }
                        }

                        public class Child extends Parent {
                            public Child(String arg1, int arg2, long arg3, boolean arg4, short arg5) {
                                super(arg1, arg2, arg3, arg4, arg5);
                            }

                            private Child(String arg1) {
                                super(arg1, 0, 0, false, 0);
                            }
                        }

                        public class Child2 extends Parent {
                            Child2(String arg1) {
                                super(arg1, 0, 0, false, 0);
                            }
                        }

                        public class Child3 extends Child2 {
                            private Child3(String arg1) {
                                super("something");
                            }
                        }

                        public class Child4 extends Parent {
                            Child4(String arg1, HiddenClass arg2) {
                                super(arg1, 0, 0, true, 0);
                            }
                        }
                        /** @hide */
                        public class HiddenClass {
                        }
                    }
                    """
                    )
                ),
            source =
                """
                    package test.pkg;
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class Constructors {
                    public Constructors() { throw new RuntimeException("Stub!"); }
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class Child extends test.pkg.Constructors.Parent {
                    public Child(java.lang.String arg1, int arg2, long arg3, boolean arg4, short arg5) { super(null, 0, 0, false, (short)0); throw new RuntimeException("Stub!"); }
                    }
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class Child2 extends test.pkg.Constructors.Parent {
                    Child2() { super(null, 0, 0, false, (short)0); throw new RuntimeException("Stub!"); }
                    }
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class Child3 extends test.pkg.Constructors.Child2 {
                    Child3() { throw new RuntimeException("Stub!"); }
                    }
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class Child4 extends test.pkg.Constructors.Parent {
                    Child4() { super(null, 0, 0, false, (short)0); throw new RuntimeException("Stub!"); }
                    }
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class Parent {
                    public Parent(java.lang.String arg1, int arg2, long arg3, boolean arg4, short arg5) { throw new RuntimeException("Stub!"); }
                    }
                    }
                    """,
            checkTextStubEquivalence = true
        )
    }

    @Test
    fun `Arguments to super constructors with showAnnotations`() {
        // When overriding constructors we have to supply arguments
        checkStubs(
            showAnnotations = arrayOf("android.annotation.SystemApi"),
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;

                    @SuppressWarnings("WeakerAccess")
                    public class Constructors {
                        public class Parent {
                            public Parent(String s, int i, long l, boolean b, short sh) {
                            }
                        }

                        public class Child extends Parent {
                            public Child(String s, int i, long l, boolean b, short sh) {
                                super(s, i, l, b, sh);
                            }

                            private Child(String s) {
                                super(s, 0, 0, false, 0);
                            }
                        }

                        public class Child2 extends Parent {
                            Child2(String s) {
                                super(s, 0, 0, false, 0);
                            }
                        }

                        public class Child3 extends Child2 {
                            private Child3(String s) {
                                super("something");
                            }
                        }

                        public class Child4 extends Parent {
                            Child4(String s, HiddenClass hidden) {
                                super(s, 0, 0, true, 0);
                            }
                        }
                        /** @hide */
                        public class HiddenClass {
                        }
                    }
                    """
                    )
                ),
            source =
                """
                    package test.pkg;
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class Constructors {
                    public Constructors() { throw new RuntimeException("Stub!"); }
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class Child extends test.pkg.Constructors.Parent {
                    public Child(java.lang.String s, int i, long l, boolean b, short sh) { super(null, 0, 0, false, (short)0); throw new RuntimeException("Stub!"); }
                    }
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class Child2 extends test.pkg.Constructors.Parent {
                    Child2() { super(null, 0, 0, false, (short)0); throw new RuntimeException("Stub!"); }
                    }
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class Child3 extends test.pkg.Constructors.Child2 {
                    Child3() { throw new RuntimeException("Stub!"); }
                    }
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class Child4 extends test.pkg.Constructors.Parent {
                    Child4() { super(null, 0, 0, false, (short)0); throw new RuntimeException("Stub!"); }
                    }
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class Parent {
                    public Parent(java.lang.String s, int i, long l, boolean b, short sh) { throw new RuntimeException("Stub!"); }
                    }
                    }
                    """
        )
    }

    // TODO: Add test to see what happens if I have Child4 in a different package which can't access
    // the package private constructor of child3?

    @Test
    fun `Test inaccessible constructors`() {
        // If the constructors of a class are not visible, and the class has subclasses,
        // those subclass stubs will need to reference these inaccessible constructors.
        // This generally only happens when the constructors are package private (and
        // therefore hidden) but the subclass using it is also in the same package.

        check(
            checkCompilation = true,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;
                    public class MyClass1 {
                        MyClass1(int myVar) { }
                    }
                    """
                    ),
                    java(
                        """
                    package test.pkg;
                    import java.io.IOException;
                    @SuppressWarnings("RedundantThrows")
                    public class MySubClass1 extends MyClass1 {
                        MySubClass1(int myVar) throws IOException { super(myVar); }
                    }
                    """
                    ),
                    java(
                        """
                    package test.pkg;
                    public class MyClass2 {
                        /** @hide */
                        public MyClass2(int myVar) { }
                    }
                    """
                    ),
                    java(
                        """
                    package test.pkg;
                    public class MySubClass2 extends MyClass2 {
                        public MySubClass2() { super(5); }
                    }
                    """
                    )
                ),
            expectedIssues = "",
            api =
                """
                    package test.pkg {
                      public class MyClass1 {
                      }
                      public class MyClass2 {
                      }
                      public class MySubClass1 extends test.pkg.MyClass1 {
                      }
                      public class MySubClass2 extends test.pkg.MyClass2 {
                        ctor public MySubClass2();
                      }
                    }
                    """,
            stubFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class MyClass1 {
                    MyClass1() { throw new RuntimeException("Stub!"); }
                    }
                    """
                    ),
                    java(
                        """
                    package test.pkg;
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class MySubClass1 extends test.pkg.MyClass1 {
                    MySubClass1() { throw new RuntimeException("Stub!"); }
                    }
                    """
                    ),
                    java(
                        """
                    package test.pkg;
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class MyClass2 {
                    MyClass2() { throw new RuntimeException("Stub!"); }
                    }
                    """
                    ),
                    java(
                        """
                    package test.pkg;
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class MySubClass2 extends test.pkg.MyClass2 {
                    public MySubClass2() { throw new RuntimeException("Stub!"); }
                    }
                    """
                    )
                ),
        )
    }

    // TODO: Add a protected constructor too to make sure my code to make non-public constructors
    // package private
    // don't accidentally demote protected constructors to package private!

    @Test
    fun `Picking Super Constructors`() {
        checkStubs(
            format = FileFormat.V2,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;

                    @SuppressWarnings({"RedundantThrows", "JavaDoc", "WeakerAccess"})
                    public class PickConstructors {
                        public abstract static class FileInputStream extends InputStream {

                            public FileInputStream(String name) throws FileNotFoundException {
                            }

                            public FileInputStream(File file) throws FileNotFoundException {
                            }

                            public FileInputStream(FileDescriptor fdObj) {
                                this(fdObj, false /* isFdOwner */);
                            }

                            /**
                             * @hide
                             */
                            public FileInputStream(FileDescriptor fdObj, boolean isFdOwner) {
                            }
                        }

                        public abstract static class AutoCloseInputStream extends FileInputStream {
                            public AutoCloseInputStream(ParcelFileDescriptor pfd) {
                                super(pfd.getFileDescriptor());
                            }
                        }

                        abstract static class HiddenParentStream extends FileInputStream {
                            public HiddenParentStream(FileDescriptor pfd) {
                                super(pfd);
                            }
                        }

                        public abstract static class AutoCloseInputStream2 extends HiddenParentStream {
                            public AutoCloseInputStream2(ParcelFileDescriptor pfd) {
                                super(pfd.getFileDescriptor());
                            }
                        }

                        public abstract class ParcelFileDescriptor implements Closeable {
                            public abstract FileDescriptor getFileDescriptor();
                        }

                        @SuppressWarnings("UnnecessaryInterfaceModifier")
                        public static interface Closeable extends AutoCloseable {
                        }

                        @SuppressWarnings("UnnecessaryInterfaceModifier")
                        public static interface AutoCloseable {
                        }

                        public static abstract class InputStream implements Closeable {
                        }

                        public static class File {
                        }

                        public static final class FileDescriptor {
                        }

                        public static class FileNotFoundException extends IOException {
                        }

                        public static class IOException extends Exception {
                        }
                    }
                    """
                    )
                ),
            warnings = "",
            api =
                """
                    package test.pkg {
                      public class PickConstructors {
                        ctor public PickConstructors();
                      }
                      public abstract static class PickConstructors.AutoCloseInputStream extends test.pkg.PickConstructors.FileInputStream {
                        ctor public PickConstructors.AutoCloseInputStream(test.pkg.PickConstructors.ParcelFileDescriptor);
                      }
                      public abstract static class PickConstructors.AutoCloseInputStream2 extends test.pkg.PickConstructors.FileInputStream {
                        ctor public PickConstructors.AutoCloseInputStream2(test.pkg.PickConstructors.ParcelFileDescriptor);
                      }
                      public static interface PickConstructors.AutoCloseable {
                      }
                      public static interface PickConstructors.Closeable extends test.pkg.PickConstructors.AutoCloseable {
                      }
                      public static class PickConstructors.File {
                        ctor public PickConstructors.File();
                      }
                      public static final class PickConstructors.FileDescriptor {
                        ctor public PickConstructors.FileDescriptor();
                      }
                      public abstract static class PickConstructors.FileInputStream extends test.pkg.PickConstructors.InputStream {
                        ctor public PickConstructors.FileInputStream(String) throws test.pkg.PickConstructors.FileNotFoundException;
                        ctor public PickConstructors.FileInputStream(test.pkg.PickConstructors.File) throws test.pkg.PickConstructors.FileNotFoundException;
                        ctor public PickConstructors.FileInputStream(test.pkg.PickConstructors.FileDescriptor);
                      }
                      public static class PickConstructors.FileNotFoundException extends test.pkg.PickConstructors.IOException {
                        ctor public PickConstructors.FileNotFoundException();
                      }
                      public static class PickConstructors.IOException extends java.lang.Exception {
                        ctor public PickConstructors.IOException();
                      }
                      public abstract static class PickConstructors.InputStream implements test.pkg.PickConstructors.Closeable {
                        ctor public PickConstructors.InputStream();
                      }
                      public abstract class PickConstructors.ParcelFileDescriptor implements test.pkg.PickConstructors.Closeable {
                        ctor public PickConstructors.ParcelFileDescriptor();
                        method public abstract test.pkg.PickConstructors.FileDescriptor getFileDescriptor();
                      }
                    }
                """,
            source =
                """
                    package test.pkg;
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class PickConstructors {
                    public PickConstructors() { throw new RuntimeException("Stub!"); }
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public abstract static class AutoCloseInputStream extends test.pkg.PickConstructors.FileInputStream {
                    public AutoCloseInputStream(test.pkg.PickConstructors.ParcelFileDescriptor pfd) { super((test.pkg.PickConstructors.FileDescriptor)null); throw new RuntimeException("Stub!"); }
                    }
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public abstract static class AutoCloseInputStream2 extends test.pkg.PickConstructors.FileInputStream {
                    public AutoCloseInputStream2(test.pkg.PickConstructors.ParcelFileDescriptor pfd) { super((test.pkg.PickConstructors.FileDescriptor)null); throw new RuntimeException("Stub!"); }
                    }
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public static interface AutoCloseable {
                    }
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public static interface Closeable extends test.pkg.PickConstructors.AutoCloseable {
                    }
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public static class File {
                    public File() { throw new RuntimeException("Stub!"); }
                    }
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public static final class FileDescriptor {
                    public FileDescriptor() { throw new RuntimeException("Stub!"); }
                    }
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public abstract static class FileInputStream extends test.pkg.PickConstructors.InputStream {
                    public FileInputStream(java.lang.String name) throws test.pkg.PickConstructors.FileNotFoundException { throw new RuntimeException("Stub!"); }
                    public FileInputStream(test.pkg.PickConstructors.File file) throws test.pkg.PickConstructors.FileNotFoundException { throw new RuntimeException("Stub!"); }
                    public FileInputStream(test.pkg.PickConstructors.FileDescriptor fdObj) { throw new RuntimeException("Stub!"); }
                    }
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public static class FileNotFoundException extends test.pkg.PickConstructors.IOException {
                    public FileNotFoundException() { throw new RuntimeException("Stub!"); }
                    }
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public static class IOException extends java.lang.Exception {
                    public IOException() { throw new RuntimeException("Stub!"); }
                    }
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public abstract static class InputStream implements test.pkg.PickConstructors.Closeable {
                    public InputStream() { throw new RuntimeException("Stub!"); }
                    }
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public abstract class ParcelFileDescriptor implements test.pkg.PickConstructors.Closeable {
                    public ParcelFileDescriptor() { throw new RuntimeException("Stub!"); }
                    public abstract test.pkg.PickConstructors.FileDescriptor getFileDescriptor();
                    }
                    }
                    """
        )
    }

    @Test
    fun `Picking Constructors`() {
        checkStubs(
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;

                    @SuppressWarnings({"WeakerAccess", "unused"})
                    public class Constructors2 {
                        public class TestSuite implements Test {

                            public TestSuite() {
                            }

                            public TestSuite(final Class<?> theClass) {
                            }

                            public TestSuite(Class<? extends TestCase> theClass, String name) {
                                this(theClass);
                            }

                            public TestSuite(String name) {
                            }
                            public TestSuite(Class<?>... classes) {
                            }

                            public TestSuite(Class<? extends TestCase>[] classes, String name) {
                                this(classes);
                            }
                        }

                        public class TestCase {
                        }

                        public interface Test {
                        }

                        public class Parent {
                            public Parent(int x) throws IOException {
                            }
                        }

                        class Intermediate extends Parent {
                            Intermediate(int x) throws IOException { super(x); }
                        }

                        public class Child extends Intermediate {
                            public Child() throws IOException { super(5); }
                            public Child(float x) throws IOException { this(); }
                        }

                        // ----------------------------------------------------

                        public abstract class DrawableWrapper {
                            public DrawableWrapper(Drawable dr) {
                            }

                            DrawableWrapper(Clipstate state, Object resources) {
                            }
                        }


                        public class ClipDrawable extends DrawableWrapper {
                            ClipDrawable() {
                                this(null);
                            }

                            public ClipDrawable(Drawable drawable, int gravity, int orientation) { this(null); }

                            private ClipDrawable(Clipstate clipstate) {
                                super(clipstate, null);
                            }
                        }

                        public class Drawable {
                        }

                        class Clipstate {
                        }
                    }
                    """
                    )
                ),
            warnings = "",
            source =
                """
                    package test.pkg;
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class Constructors2 {
                    public Constructors2() { throw new RuntimeException("Stub!"); }
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class Child extends test.pkg.Constructors2.Parent {
                    public Child() { super(0); throw new RuntimeException("Stub!"); }
                    public Child(float x) { super(0); throw new RuntimeException("Stub!"); }
                    }
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class ClipDrawable extends test.pkg.Constructors2.DrawableWrapper {
                    public ClipDrawable(test.pkg.Constructors2.Drawable drawable, int gravity, int orientation) { super(null); throw new RuntimeException("Stub!"); }
                    }
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class Drawable {
                    public Drawable() { throw new RuntimeException("Stub!"); }
                    }
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public abstract class DrawableWrapper {
                    public DrawableWrapper(test.pkg.Constructors2.Drawable dr) { throw new RuntimeException("Stub!"); }
                    }
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class Parent {
                    public Parent(int x) { throw new RuntimeException("Stub!"); }
                    }
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public static interface Test {
                    }
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class TestCase {
                    public TestCase() { throw new RuntimeException("Stub!"); }
                    }
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class TestSuite implements test.pkg.Constructors2.Test {
                    public TestSuite() { throw new RuntimeException("Stub!"); }
                    public TestSuite(java.lang.Class<?> theClass) { throw new RuntimeException("Stub!"); }
                    public TestSuite(java.lang.Class<? extends test.pkg.Constructors2.TestCase> theClass, java.lang.String name) { throw new RuntimeException("Stub!"); }
                    public TestSuite(java.lang.String name) { throw new RuntimeException("Stub!"); }
                    public TestSuite(java.lang.Class<?>... classes) { throw new RuntimeException("Stub!"); }
                    public TestSuite(java.lang.Class<? extends test.pkg.Constructors2.TestCase>[] classes, java.lang.String name) { throw new RuntimeException("Stub!"); }
                    }
                    }
                    """
        )
    }

    @Test
    fun `Another Constructor Test`() {
        // A specific scenario triggered in the API where the right super class detector was not
        // chosen
        checkStubs(
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;

                    @SuppressWarnings({"RedundantThrows", "JavaDoc", "WeakerAccess"})
                    public class PickConstructors2 {
                        public interface EventListener {
                        }

                        public interface PropertyChangeListener extends EventListener {
                        }

                        public static abstract class EventListenerProxy<T extends EventListener>
                                implements EventListener {
                            public EventListenerProxy(T listener) {
                            }
                        }

                        public static class PropertyChangeListenerProxy
                                extends EventListenerProxy<PropertyChangeListener>
                                implements PropertyChangeListener {
                            public PropertyChangeListenerProxy(String propertyName, PropertyChangeListener listener) {
                                super(listener);
                            }
                        }
                    }
                    """
                    )
                ),
            warnings = "",
            source =
                """
                    package test.pkg;
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class PickConstructors2 {
                    public PickConstructors2() { throw new RuntimeException("Stub!"); }
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public static interface EventListener {
                    }
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public abstract static class EventListenerProxy<T extends test.pkg.PickConstructors2.EventListener> implements test.pkg.PickConstructors2.EventListener {
                    public EventListenerProxy(T listener) { throw new RuntimeException("Stub!"); }
                    }
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public static interface PropertyChangeListener extends test.pkg.PickConstructors2.EventListener {
                    }
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public static class PropertyChangeListenerProxy extends test.pkg.PickConstructors2.EventListenerProxy<test.pkg.PickConstructors2.PropertyChangeListener> implements test.pkg.PickConstructors2.PropertyChangeListener {
                    public PropertyChangeListenerProxy(java.lang.String propertyName, test.pkg.PickConstructors2.PropertyChangeListener listener) { super(null); throw new RuntimeException("Stub!"); }
                    }
                    }
                    """
        )
    }

    @Test
    fun `Use type argument in constructor cast`() {
        check(
            format = FileFormat.V2,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;
                    /** @deprecated */
                    @Deprecated
                    public class BasicPoolEntryRef extends WeakRef<BasicPoolEntry> {
                        public BasicPoolEntryRef(BasicPoolEntry entry) {
                            super(entry);
                        }
                    }
                    """
                    ),
                    java(
                        """
                    package test.pkg;

                    public class WeakRef<T> {
                        public WeakRef(T foo) {
                        }
                        // need to have more than one constructor to trigger casts in stubs
                        public WeakRef(T foo, int size) {
                        }
                    }
                    """
                    ),
                    java(
                        """
                    package test.pkg;

                    public class BasicPoolEntry {
                    }
                    """
                    )
                ),
            api =
                """
                package test.pkg {
                  public class BasicPoolEntry {
                    ctor public BasicPoolEntry();
                  }
                  @Deprecated public class BasicPoolEntryRef extends test.pkg.WeakRef<test.pkg.BasicPoolEntry> {
                    ctor @Deprecated public BasicPoolEntryRef(test.pkg.BasicPoolEntry);
                  }
                  public class WeakRef<T> {
                    ctor public WeakRef(T);
                    ctor public WeakRef(T, int);
                  }
                }
                """,
            stubFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;
                    /** @deprecated */
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    @Deprecated
                    public class BasicPoolEntryRef extends test.pkg.WeakRef<test.pkg.BasicPoolEntry> {
                    @Deprecated
                    public BasicPoolEntryRef(test.pkg.BasicPoolEntry entry) { super((test.pkg.BasicPoolEntry)null); throw new RuntimeException("Stub!"); }
                    }
                    """
                    )
                )
        )
    }

    @Test
    fun `Use unspecified type argument in constructor cast`() {
        check(
            format = FileFormat.V2,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;
                    public class Foo extends Bar {
                        public Foo(Integer i) {
                            super(i);
                        }
                    }
                    """
                    ),
                    java(
                        """
                    package test.pkg;

                    public class Bar<T extends Number> {
                        public Bar(T foo) {
                        }
                        // need to have more than one constructor to trigger casts in stubs
                        public Bar(T foo, int size) {
                        }
                    }
                    """
                    ),
                ),
            api =
                """
                package test.pkg {
                  public class Bar<T extends java.lang.Number> {
                    ctor public Bar(T);
                    ctor public Bar(T, int);
                  }
                  public class Foo extends test.pkg.Bar {
                    ctor public Foo(Integer);
                  }
                }
                """,
            stubFiles =
                arrayOf(
                    java(
                        """
                        package test.pkg;
                        @SuppressWarnings({"unchecked", "deprecation", "all"})
                        public class Foo extends test.pkg.Bar {
                        public Foo(java.lang.Integer i) { super((java.lang.Number)null); throw new RuntimeException("Stub!"); }
                        }
                        """
                    )
                )
        )
    }

    @Test
    fun `Varargs constructor parameter requiring cast`() {
        check(
            format = FileFormat.V2,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;

                    public class Child extends Parent {
                        public Child(int... ints) {
                            super(ints);
                        }
                        public Child(String... strings) {
                            super(strings);
                        }
                    }
                    """
                    ),
                    java(
                        """
                    package test.pkg;

                    public class Parent {
                        public Parent(int... ints) {
                        }
                        public Parent(String... strings) {
                        }
                    }
                    """
                    ),
                ),
            api =
                """
                package test.pkg {
                  public class Child extends test.pkg.Parent {
                    ctor public Child(int...);
                    ctor public Child(java.lang.String...);
                  }
                  public class Parent {
                    ctor public Parent(int...);
                    ctor public Parent(java.lang.String...);
                  }
                }
                """,
            stubFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class Child extends test.pkg.Parent {
                    public Child(int... ints) { super((int[])null); throw new RuntimeException("Stub!"); }
                    public Child(java.lang.String... strings) { super((int[])null); throw new RuntimeException("Stub!"); }
                    }
                    """
                    )
                )
        )
    }
}
