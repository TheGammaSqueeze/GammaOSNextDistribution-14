/*
 * Copyright (C) 2013 The Android Open Source Project
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
package libcore.java.lang;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import dalvik.annotation.compat.VersionCodes;
import dalvik.system.InMemoryDexClassLoader;
import dalvik.system.PathClassLoader;
import dalvik.system.VMRuntime;

import libcore.io.Streams;
import libcore.test.annotation.NonCts;
import libcore.test.reasons.NonCtsReasons;

import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Stream;

@RunWith(JUnit4.class)
public class ClassTest {

    interface Foo {
        public void foo();
    }

    interface ParameterizedFoo<T> {
        public void foo(T param);
    }

    interface ParameterizedBar<T> extends ParameterizedFoo<T> {
        public void bar(T param);
    }

    interface ParameterizedBaz extends ParameterizedFoo<String> {

    }

    @Test
    public void getGenericSuperclass_nullReturnCases() {
        // Should always return null for interfaces.
        assertNull(Foo.class.getGenericSuperclass());
        assertNull(ParameterizedFoo.class.getGenericSuperclass());
        assertNull(ParameterizedBar.class.getGenericSuperclass());
        assertNull(ParameterizedBaz.class.getGenericSuperclass());

        assertNull(Object.class.getGenericSuperclass());
        assertNull(void.class.getGenericSuperclass());
        assertNull(int.class.getGenericSuperclass());
    }

    @Test
    public void getGenericSuperclass_returnsObjectForArrays() {
        assertSame(Object.class, (new Integer[0]).getClass().getGenericSuperclass());
    }

    @Test
    public void b28833829() throws Exception {
        File f = File.createTempFile("temp_b28833829", ".dex");
        try (InputStream is =
            getClass().getClassLoader().getResourceAsStream("TestBug28833829.dex");
            OutputStream os = new FileOutputStream(f)) {
            byte[] buffer = new byte[8192];
            int bytesRead = 0;
            while ((bytesRead = is.read(buffer)) >= 0) {
                os.write(buffer, 0, bytesRead);
            }
        }
        assertTrue(f.setReadOnly());

        PathClassLoader pcl = new PathClassLoader(f.getAbsolutePath(), null);
        Class<?> cl = pcl.loadClass(
            "libcore.java.lang.TestBadInnerClass_Outer$ClassTestBadInnerClass_InnerClass");

        // Note that getName() and getSimpleName() are inconsistent here because for
        // inner classes,  the latter is fetched directly from the InnerClass
        // annotation in the dex file. We do not perform any sort of consistency
        // checks with the class name or the enclosing class name. Unfortunately, applications
        // have come to rely on this behaviour.
        assertEquals("libcore.java.lang.TestBadInnerClass_Outer$ClassTestBadInnerClass_InnerClass",
            cl.getName());
        assertEquals("TestBadInnerClass_InnerXXXXX", cl.getSimpleName());
    }

    @Test
    public void testGetSimpleName() {
        assertEquals("ClassTest", this.getClass().getSimpleName());
        assertEquals("int[]", int[].class.getSimpleName());
        assertEquals("int[][]", int[][].class.getSimpleName());
        assertEquals("ClassTest[]", ClassTest[].class.getSimpleName());
        assertEquals("", new A() {}.getClass().getSimpleName()); // Anonymous class
        assertEquals("A", A.class.getSimpleName()); // member interface
        assertEquals("X", X.class.getSimpleName()); // member class
        class LocalClass implements A {}
        assertEquals("LocalClass", LocalClass.class.getSimpleName());
    }

    interface A {
        public static String name = "A";
    }
    interface B {
        public static String name = "B";
    }
    class X implements A { }
    class Y extends X implements B { }
    @Test
    public void getField() {
        try {
            assertEquals(A.class.getField("name"), X.class.getField("name"));
        } catch (NoSuchFieldException e) {
            fail("Got exception");
        }
        try {
            assertEquals(B.class.getField("name"), Y.class.getField("name"));
        } catch (NoSuchFieldException e) {
            fail("Got exception");
        }
    }

    interface C {
        void foo();
    }
    interface D extends C {
        void foo();
    }
    abstract class Z implements D { }

    @Test
    public void getMethod() {
      try {
          assertEquals(Z.class.getMethod("foo"), D.class.getMethod("foo"));
      } catch (NoSuchMethodException e) {
          fail("Got exception");
      }
    }

    @Test
    public void getPrimitiveType_null() throws Throwable {
        try {
            getPrimitiveType(null);
            fail();
        } catch (NullPointerException expected) {
            assertNull(expected.getMessage());
        }
    }

    @Test
    public void getPrimitiveType_invalid() throws Throwable {
        List<String> invalidNames = Arrays.asList("", "java.lang.Object", "invalid",
                "Boolean", "java.lang.Boolean", "java/lang/Boolean", "Ljava/lang/Boolean;");
        for (String name : invalidNames) {
            try {
                getPrimitiveType(name);
                fail("Invalid type should be rejected: " + name);
            } catch (ClassNotFoundException expected) {
                assertEquals(name, expected.getMessage());
            }
        }
    }

    @Test
    public void getPrimitiveType_valid() throws Throwable {
        checkPrimitiveType("boolean", boolean.class, Boolean.TYPE,
            boolean[].class.getComponentType());
        checkPrimitiveType("byte", byte.class, Byte.TYPE, byte[].class.getComponentType());
        checkPrimitiveType("char", char.class, Character.TYPE, char[].class.getComponentType());
        checkPrimitiveType("double", double.class, Double.TYPE, double[].class.getComponentType());
        checkPrimitiveType("float", float.class, Float.TYPE, float[].class.getComponentType());
        checkPrimitiveType("int", int.class, Integer.TYPE, int[].class.getComponentType());
        checkPrimitiveType("long", long.class, Long.TYPE, long[].class.getComponentType());
        checkPrimitiveType("short", short.class, Short.TYPE, short[].class.getComponentType());
        checkPrimitiveType("void", void.class, Void.TYPE);
    }

    private static void checkPrimitiveType(String name, Class expected, Class... expectedEqual)
            throws Throwable {
        Class clazz = getPrimitiveType(name);
        assertEquals(name, clazz.getName());
        assertTrue(clazz.isPrimitive());
        assertEquals(expected, clazz);
        for (Class c : expectedEqual) {
            assertEquals(expected, c);
        }
    }

    /** Calls {@link Class#getPrimitiveClass(String)} via reflection. */
    private static Class getPrimitiveType(String name) throws Throwable {
        try {
            Method method = Class.class.getDeclaredMethod("getPrimitiveClass", String.class);
            method.setAccessible(true);
            return (Class) method.invoke(null, name);
        } catch (InvocationTargetException e) {
            throw e.getTargetException();
        } catch (Throwable unexpected) {
            // no other kinds of throwables are expected to happen
            fail(unexpected.toString());
            return null;
        }
    }

    public static class TestGetVirtualMethod_Super {
        protected String protectedMethod() {
            return "protectedMethod";
        }

        public String publicMethod() {
            return "publicMethod";
        }

        /* package */ String packageMethod() {
            return "packageMethod";
        }
    }

    public static class TestGetVirtualMethod extends TestGetVirtualMethod_Super {
        public static void staticMethod(String foo) {
        }

        public String publicMethod2() {
            return "publicMethod2";
        }

        protected String protectedMethod2() {
            return "protectedMethod2";
        }

        private String privateMethod() {
            return "privateMethod";
        }

        /* package */ String packageMethod2() {
            return "packageMethod2";
        }
    }

    @Test
    public void getVirtualMethod() throws Exception {
        final Class<?>[] noArgs = new Class<?>[] { };

        TestGetVirtualMethod instance = new TestGetVirtualMethod();
        TestGetVirtualMethod_Super super_instance = new TestGetVirtualMethod_Super();

        // Package private methods from the queried class as well as super classes
        // must be returned.
        Method m = TestGetVirtualMethod.class.getInstanceMethod("packageMethod2", noArgs);
        assertNotNull(m);
        assertEquals("packageMethod2", m.invoke(instance));
        m = TestGetVirtualMethod.class.getInstanceMethod("packageMethod", noArgs);
        assertNotNull(m);
        assertEquals("packageMethod", m.invoke(instance));

        // Protected methods from both the queried class as well as super classes must
        // be returned.
        m = TestGetVirtualMethod.class.getInstanceMethod("protectedMethod2", noArgs);
        assertNotNull(m);
        assertEquals("protectedMethod2", m.invoke(instance));
        m = TestGetVirtualMethod.class.getInstanceMethod("protectedMethod", noArgs);
        assertNotNull(m);
        assertEquals("protectedMethod", m.invoke(instance));

        // Public methods from the queried classes and all its super classes must be
        // returned.
        m = TestGetVirtualMethod.class.getInstanceMethod("publicMethod2", noArgs);
        assertNotNull(m);
        assertEquals("publicMethod2", m.invoke(instance));
        m = TestGetVirtualMethod.class.getInstanceMethod("publicMethod", noArgs);
        assertNotNull(m);
        assertEquals("publicMethod", m.invoke(instance));

        m = TestGetVirtualMethod.class.getInstanceMethod("privateMethod", noArgs);
        assertNotNull(m);

        assertNull(TestGetVirtualMethod.class.getInstanceMethod("staticMethod", noArgs));
    }

    @Test
    public void toStringTest() throws Exception {
        final String outerClassName = getClass().getName();
        final String packageProtectedClassName = PackageProtectedClass.class.getName();

        assertToString("int", int.class);
        assertToString("class [I", int[].class);
        assertToString("class java.lang.Object", Object.class);
        assertToString("class [Ljava.lang.Object;", Object[].class);
        assertToString("class java.lang.Integer", Integer.class);
        assertToString("interface java.util.function.Function", Function.class);
        assertToString(
                "class " + outerClassName + "$PublicStaticInnerClass",
                PublicStaticInnerClass.class);
        assertToString(
                "class " + outerClassName + "$DefaultStaticInnerClass",
                DefaultStaticInnerClass.class);
        assertToString(
                "interface " + outerClassName + "$PublicInnerInterface",
                PublicInnerInterface.class);
        assertToString(
                "class " + packageProtectedClassName,
                PackageProtectedClass.class);
        assertToString(
                "class " + outerClassName + "$PrivateStaticInnerClass",
                PrivateStaticInnerClass.class);
        assertToString("interface java.lang.annotation.Retention", Retention.class);
        assertToString("class java.lang.annotation.RetentionPolicy", RetentionPolicy.class);
        assertToString("class java.util.TreeMap", TreeMap.class);
        assertToString(
                "interface " + outerClassName + "$WildcardInterface",
                WildcardInterface.class);
    }

    private static void assertToString(String expected, Class<?> clazz) {
        assertEquals(expected, clazz.toString());
    }

    @Test
    public void getTypeName() throws Exception {
        final String outerClassName = getClass().getName();
        final String packageProtectedClassName = PackageProtectedClass.class.getName();

        assertGetTypeName("int", int.class);
        assertGetTypeName("int[]", int[].class);
        assertGetTypeName("java.lang.Object", Object.class);
        assertGetTypeName("java.lang.Object[]", Object[].class);
        assertGetTypeName("java.lang.Integer", Integer.class);
        assertGetTypeName("java.util.function.Function", Function.class);
        assertGetTypeName(outerClassName + "$PublicStaticInnerClass", PublicStaticInnerClass.class);
        assertGetTypeName(
                outerClassName + "$DefaultStaticInnerClass",
                DefaultStaticInnerClass.class);
        assertGetTypeName(outerClassName + "$PublicInnerInterface", PublicInnerInterface.class);
        assertGetTypeName(packageProtectedClassName, PackageProtectedClass.class);
        assertGetTypeName(
                outerClassName + "$PrivateStaticInnerClass",
                PrivateStaticInnerClass.class);
        assertGetTypeName("java.lang.annotation.Retention", Retention.class);
        assertGetTypeName("java.lang.annotation.RetentionPolicy", RetentionPolicy.class);
        assertGetTypeName("java.util.TreeMap", TreeMap.class);
        assertGetTypeName(outerClassName + "$WildcardInterface", WildcardInterface.class);
    }

    private void assertGetTypeName(String expected, Class<?> clazz) {
        assertEquals(expected, clazz.getTypeName());
    }

    @NonCts(bug = 287231726, reason = NonCtsReasons.NON_BREAKING_BEHAVIOR_FIX)
    @Ignore
    public void test_toGenericString() {
        // This test method has been renamed to toGenericString().
        // Keep this empty method in order to generate the method name into skippedCtsTest.txt
        // and skip the test in aosp/android12-tests-dev.
    }

    @NonCts(bug = 287231726, reason = NonCtsReasons.NON_BREAKING_BEHAVIOR_FIX)
    @Test
    public void toGenericString() throws Exception {
        final String outerClassName = getClass().getName();
        final String packageProtectedClassName = PackageProtectedClass.class.getName();

        assertToGenericString("int", int.class);
        assertToGenericString("int[]", int[].class);
        assertToGenericString("public class java.lang.Object", Object.class);
        assertToGenericString("java.lang.Object[]", Object[].class);
        assertToGenericString("public final class java.lang.Integer", Integer.class);
        assertToGenericString(
                "public abstract interface java.util.function.Function<T,R>",
                Function.class);
        assertToGenericString("public static class " + outerClassName + "$PublicStaticInnerClass",
                PublicStaticInnerClass.class);
        assertToGenericString("static class " + outerClassName + "$DefaultStaticInnerClass",
                DefaultStaticInnerClass.class);
        assertToGenericString(
                "public abstract static interface " + outerClassName + "$PublicInnerInterface",
                PublicInnerInterface.class);
        assertToGenericString("class " + packageProtectedClassName, PackageProtectedClass.class);
        assertToGenericString(
                "private static class " + outerClassName + "$PrivateStaticInnerClass",
                PrivateStaticInnerClass.class);
        assertToGenericString(
                "public abstract @interface java.lang.annotation.Retention", Retention.class);
        assertToGenericString("public final enum java.lang.annotation.RetentionPolicy",
                RetentionPolicy.class);
        assertToGenericString("public class java.util.TreeMap<K,V>", TreeMap.class);
        String wildcardInterfaceName;
        if (VMRuntime.getSdkVersion() >= VersionCodes.VANILLA_ICE_CREAM) {
            wildcardInterfaceName = "$WildcardInterface<T extends java.lang.Number,"
                    + "U extends java.util.function.Function"
                    + "<? extends java.lang.Number, ? super java.lang.Number>>";
        } else {
            wildcardInterfaceName = "$WildcardInterface<T,U>";
        }
        assertToGenericString(
                "abstract static interface " + outerClassName + wildcardInterfaceName,
                WildcardInterface.class);
    }

    private static void assertToGenericString(String expected, Class<?> clazz) {
        assertEquals(expected, clazz.toGenericString());
    }

    private static class PrivateStaticInnerClass {}
    static class DefaultStaticInnerClass {}
    public static class PublicStaticInnerClass {}
    public interface PublicInnerInterface {}
    interface WildcardInterface<
            T extends Number,
            U extends Function<? extends Number, ? super Number>>
            extends Comparable<T> {}

    @Test
    public void nestMate() {
        try {
            ClassLoader classLoader = createClassLoaderForResource("core-tests-smali.dex");

            Class hostClass = classLoader.loadClass("libcore.java.lang.nestgroup.NestGroupHost");
            Class innerAClass = classLoader.loadClass("libcore.java.lang.nestgroup.NestGroupInnerA");
            Class bClass = classLoader.loadClass("libcore.java.lang.nestgroup.NestGroupB");
            Class innerFakeClass = classLoader.loadClass("libcore.java.lang.nestgroup.NestGroupInnerFake");
            Class selfClass = classLoader.loadClass("libcore.java.lang.nestgroup.NestGroupSelf");

            assertEquals(int.class, int.class.getNestHost());
            assertTrue(int.class.isNestmateOf(int.class));
            assertArrayEquals(new Class[] { int.class }, int.class.getNestMembers());

            assertEquals(Integer[].class, Integer[].class.getNestHost());
            assertTrue(Integer[].class.isNestmateOf(Integer[].class));
            assertArrayEquals(new Class[] { Integer[].class }, Integer[].class.getNestMembers());

            assertEquals(hostClass, hostClass.getNestHost());
            assertTrue(hostClass.isNestmateOf(hostClass));
            assertArrayEquals(new Class[] { hostClass, innerAClass }, hostClass.getNestMembers());

            assertEquals(hostClass, innerAClass.getNestHost());
            assertTrue(hostClass.isNestmateOf(innerAClass));
            assertArrayEquals(new Class[] { hostClass, innerAClass }, innerAClass.getNestMembers());

            assertEquals(innerFakeClass, innerFakeClass.getNestHost());
            assertTrue(innerFakeClass.isNestmateOf(innerFakeClass));
            assertArrayEquals(new Class[] { innerFakeClass }, innerFakeClass.getNestMembers());

            assertEquals(bClass, bClass.getNestHost());
            assertTrue(bClass.isNestmateOf(bClass));
            assertArrayEquals(new Class[] { bClass }, bClass.getNestMembers());

            assertEquals(selfClass, selfClass.getNestHost());
            assertTrue(selfClass.isNestmateOf(selfClass));
            assertArrayEquals(new Class[] { selfClass }, selfClass.getNestMembers());
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    private static ClassLoader createClassLoaderForResource(String resourcePath)
            throws Exception {
        byte[] data;
        try (InputStream is =
                ThreadTest.class.getClassLoader().getResourceAsStream(resourcePath)) {
            data = Streams.readFullyNoClose(is);
        }
        return new InMemoryDexClassLoader(ByteBuffer.wrap(data),
                ThreadTest.class.getClassLoader());
    }

    @Test
    public void sealedClass() {
        try {
            ClassLoader classLoader = createClassLoaderForResource("core-tests-smali.dex");

            Class sealedBaseClass = classLoader.loadClass("libcore.java.lang.sealedclasses.SealedBaseClass");
            Class finalDerivedClass = classLoader.loadClass("libcore.java.lang.sealedclasses.FinalDerivedClass");
            Class sealedDerivedClass = classLoader.loadClass("libcore.java.lang.sealedclasses.SealedDerivedClass");
            Class openDerivedClass = classLoader.loadClass("libcore.java.lang.sealedclasses.OpenDerivedClass");
            Class standaloneClass = classLoader.loadClass("libcore.java.lang.sealedclasses.StandaloneClass");
            Class sealedFinalClass = classLoader.loadClass("libcore.java.lang.sealedclasses.SealedFinalClass");

            assertTrue(sealedBaseClass.isSealed());
            assertArrayEquals(new Class[] { finalDerivedClass, sealedDerivedClass},
                    sealedBaseClass.getPermittedSubclasses());

            assertFalse(finalDerivedClass.isSealed());
            assertArrayEquals((Class[]) null, finalDerivedClass.getPermittedSubclasses());

            assertTrue(sealedDerivedClass.isSealed());
            assertArrayEquals(new Class[] { openDerivedClass}, sealedDerivedClass.getPermittedSubclasses());

            assertFalse(openDerivedClass.isSealed());
            assertArrayEquals((Class[]) null, openDerivedClass.getPermittedSubclasses());

            assertFalse(standaloneClass.isSealed());
            assertArrayEquals((Class[]) null, standaloneClass.getPermittedSubclasses());

            assertFalse(sealedFinalClass.isSealed());
            assertArrayEquals((Class[]) null, sealedFinalClass.getPermittedSubclasses());

        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    @Test
    public void isSealed() {
        assertTrue(SealedInterface.class.isSealed());
        assertFalse(SealedFinalClass.class.isSealed());
        assertTrue(SealedAbstractClass.class.isSealed());
        assertFalse(NonSealedDerivedClass.class.isSealed());
        assertFalse(DerivedClass.class.isSealed());
    }

    @Test
    public void getPermittedSubclasses() {
        assertNull(SealedFinalClass.class.getPermittedSubclasses());
        assertNull(NonSealedDerivedClass.class.getPermittedSubclasses());
        assertNull(DerivedClass.class.getPermittedSubclasses());

        var sealedInterfaceSubclasses = SealedInterface.class.getPermittedSubclasses();
        assertNotNull(sealedInterfaceSubclasses);
        assertEquals(2, sealedInterfaceSubclasses.length);
        assertTrue(Set.of(sealedInterfaceSubclasses).contains(SealedAbstractClass.class));
        assertTrue(Set.of(sealedInterfaceSubclasses).contains(SealedFinalClass.class));

        var sealedAbstractClass = SealedAbstractClass.class.getPermittedSubclasses();
        assertNotNull(sealedAbstractClass);
        assertEquals(1, sealedAbstractClass.length);
        assertEquals(NonSealedDerivedClass.class, sealedAbstractClass[0]);
    }

    public static sealed interface SealedInterface permits SealedAbstractClass, SealedFinalClass {
        int getNumber();
    }

    public static final class SealedFinalClass implements SealedInterface {
        @Override
        public int getNumber() {
            return 1;
        }
    }

    public static abstract sealed class SealedAbstractClass implements SealedInterface
                                                                permits NonSealedDerivedClass {
    }

    public static non-sealed class NonSealedDerivedClass extends SealedAbstractClass {
        @Override
        public int getNumber() {
            return 2;
        }
    }

    public static class DerivedClass extends NonSealedDerivedClass {
        @Override
        public int getNumber() {
            return 3;
        }
    }

    @Test
    public void recordClass() {
        try {
            ClassLoader classLoader = createClassLoaderForResource("core-tests-smali.dex");

            Class recordClassA = classLoader.loadClass(
                    "libcore.java.lang.recordclasses.RecordClassA");
            Class recordClassB = classLoader.loadClass(
                    "libcore.java.lang.recordclasses.RecordClassB");
            Class nonFinalRecordClass = classLoader.loadClass(
                    "libcore.java.lang.recordclasses.NonFinalRecordClass");
            Class emptyRecordClass = classLoader.loadClass(
                    "libcore.java.lang.recordclasses.EmptyRecordClass");
            Class validAbstractEmptyClass = classLoader.loadClass(
                    "libcore.java.lang.recordclasses.ValidAbstractEmptyRecord");
            Class validNonFinalEmptyClass = classLoader.loadClass(
                    "libcore.java.lang.recordclasses.ValidNonFinalEmptyRecord");
            Class validRecordWithExtraElement = classLoader.loadClass(
                    "libcore.java.lang.recordclasses.ValidRecordWithExtraElement");
            Class validEmptyRecordWithoutRecordAnnotation = classLoader.loadClass(
                    "libcore.java.lang.recordclasses.ValidEmptyRecordWithoutRecordAnnotation");

            assertTrue(recordClassA.isRecord());
            RecordComponent[] components = recordClassA.getRecordComponents();
            assertNotNull(components);
            assertEquals(2, components.length);
            assertEquals("x", components[0].getName());
            assertEquals(int.class, components[0].getType());
            assertEquals("y", components[1].getName());
            assertEquals(Integer.class, components[1].getType());

            assertTrue(recordClassB.isRecord());
            assertEquals(2, recordClassB.getRecordComponents().length);

            assertFalse(nonFinalRecordClass.isRecord());
            assertNull(nonFinalRecordClass.getRecordComponents());

            assertTrue(emptyRecordClass.isRecord());
            assertEquals(new RecordComponent[0], emptyRecordClass.getRecordComponents());
            assertFalse(validAbstractEmptyClass.isRecord());
            assertFalse(validNonFinalEmptyClass.isRecord());
            assertTrue(validRecordWithExtraElement.isRecord());
            assertFalse(validEmptyRecordWithoutRecordAnnotation.isRecord());

            assertClassFormatError(classLoader,
                    "libcore.java.lang.recordclasses.UnequalComponentArraysRecordClass");
            assertClassFormatError(classLoader,
                    "libcore.java.lang.recordclasses.InvalidEmptyRecord1");
            assertClassFormatError(classLoader,
                    "libcore.java.lang.recordclasses.InvalidEmptyRecord2");
            assertClassFormatError(classLoader,
                    "libcore.java.lang.recordclasses.InvalidEmptyRecord3");
            assertClassFormatError(classLoader,
                    "libcore.java.lang.recordclasses.InvalidEmptyRecord4");
            assertClassFormatError(classLoader,
                    "libcore.java.lang.recordclasses.InvalidEmptyRecord5");
            assertClassFormatError(classLoader,
                    "libcore.java.lang.recordclasses.InvalidEmptyRecord6");
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    private static void assertClassFormatError(ClassLoader cl, String className) {
        assertThrows(ClassFormatError.class, () -> cl.loadClass(className));
    }

    @Test
    public void testComponentType() {
        assertNull(int.class.componentType());
        assertNull(String.class.componentType());
        assertNull(Object.class.componentType());

        assertEquals(int.class, int[].class.componentType());
        assertEquals(int[].class, int[][].class.componentType());
        assertEquals(String.class, String[].class.componentType());
        assertEquals(Foo.class, Foo[].class.componentType());
    }

    @Test
    public void testArrayType() {
        assertEquals(int[].class, int.class.arrayType());
        assertEquals(int[][].class, int[].class.arrayType());
        assertEquals(String[].class, String.class.arrayType());
        assertEquals(Foo[].class, Foo.class.arrayType());
    }

    @Test
    public void testDescriptorString() {
        assertEquals("I", int.class.descriptorString());
        assertEquals("V", void.class.descriptorString());
        assertEquals("[I", int[].class.descriptorString());
        assertEquals("[[I", int[][].class.descriptorString());
        assertEquals("Ljava/lang/String;", String.class.descriptorString());
        assertEquals("[Ljava/lang/String;", String[].class.descriptorString());
    }
}
