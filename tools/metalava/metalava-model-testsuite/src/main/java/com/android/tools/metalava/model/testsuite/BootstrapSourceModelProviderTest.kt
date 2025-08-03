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

package com.android.tools.metalava.model.testsuite

import com.android.tools.metalava.model.AnnotationRetention
import com.android.tools.metalava.model.ClassTypeItem
import com.android.tools.metalava.model.DefaultAnnotationSingleAttributeValue
import com.android.tools.metalava.model.PrimitiveTypeItem
import com.android.tools.metalava.model.VariableTypeItem
import com.android.tools.metalava.testing.java
import com.google.common.truth.Truth.assertThat
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.junit.Test

/**
 * Provides a set of tests that are geared towards helping to bootstrap a new model.
 *
 * The basic idea is that each test (in numerical order) requires a small increment over the
 * previous test so that a developer would start by running the first test, making it pass,
 * submitting the changes and then moving on to the next test.
 */
class BootstrapSourceModelProviderTest : BaseModelTest() {

    @Test
    fun `010 - check source model provider exists`() {
        // Do nothing.
    }

    @Test
    fun `020 - check empty file`() {
        runSourceCodebaseTest(java("")) { assertNotNull(codebase) }
    }

    @Test
    fun `030 - check simplest class`() {
        runSourceCodebaseTest(
            java(
                """
                    package test.pkg;

                    class Test {
                    }
                """
            ),
        ) {
            val classItem = codebase.assertClass("test.pkg.Test")
            assertEquals("test.pkg.Test", classItem.qualifiedName())
        }
    }

    @Test
    fun `040 - check package exists`() {
        runSourceCodebaseTest(
            java(
                """
                    package test.pkg;

                    class Test {
                    }
                """
            ),
        ) {
            val packageItem = codebase.assertPackage("test.pkg")
            assertEquals("test.pkg", packageItem.qualifiedName())
            assertEquals(1, packageItem.topLevelClasses().count(), message = "")
        }
    }

    @Test
    fun `050 - check field exists`() {
        runSourceCodebaseTest(
            java(
                """
                    package test.pkg;

                    class Test {
                        int field;
                    }
                """
            ),
        ) {
            val testClass = codebase.assertClass("test.pkg.Test")
            val fieldItem = testClass.assertField("field")
            assertEquals("field", fieldItem.name())
            assertEquals(testClass, fieldItem.containingClass())
        }
    }

    @Test
    fun `060 - check method exists`() {
        runSourceCodebaseTest(
            java(
                """
                    package test.pkg;

                    class Test {
                        void method();
                    }
                """
            ),
        ) {
            val testClass = codebase.assertClass("test.pkg.Test")
            val methodItem = testClass.assertMethod("method", "")
            assertEquals("method", methodItem.name())
        }
    }

    @Test
    fun `070 - check constructor exists`() {
        runSourceCodebaseTest(
            java(
                """
                    package test.pkg;

                    class Test {
                        public Test() {}
                    }
                """
            ),
        ) {
            val testClass = codebase.assertClass("test.pkg.Test")
            val constructorItem = testClass.assertConstructor("")
            assertEquals("Test", constructorItem.name())
        }
    }

    @Test
    fun `080 - check inner class`() {
        runSourceCodebaseTest(
            java(
                """
                    package test.pkg;

                    class Test {
                      class InnerTestClass {}
                    }
                """
            ),
        ) {
            val classItem = codebase.assertClass("test.pkg.Test")
            val innerClassItem = codebase.assertClass("test.pkg.Test.InnerTestClass")
            assertEquals("test.pkg.Test.InnerTestClass", innerClassItem.qualifiedName())
            assertEquals("Test.InnerTestClass", innerClassItem.fullName())
            assertEquals("InnerTestClass", innerClassItem.simpleName())
            assertEquals(classItem, innerClassItem.containingClass())
            assertEquals(1, classItem.innerClasses().count(), message = "")
        }
    }

    @Test
    fun `090 - check class hierarchy`() {
        runSourceCodebaseTest(
            inputSet(
                java(
                    """
                        package test.pkg;

                        import test.parent.SuperInterface;

                        abstract class SuperClass implements SuperInterface {}

                        interface SuperChildInterface {}
                        interface ChildInterface extends SuperChildInterface,SuperInterface {}

                        class Test extends SuperClass implements ChildInterface {}
                    """
                ),
                java(
                    """
                        package test.parent;

                        public interface SuperInterface {}
                     """
                ),
            )
        ) {
            val classItem = codebase.assertClass("test.pkg.Test")
            val superClassItem = codebase.assertClass("test.pkg.SuperClass")
            val superInterfaceItem = codebase.assertClass("test.parent.SuperInterface")
            val childInterfaceItem = codebase.assertClass("test.pkg.ChildInterface")
            val superChildInterfaceItem = codebase.assertClass("test.pkg.SuperChildInterface")
            assertEquals(superClassItem, classItem.superClass())
            assertEquals(
                setOf(childInterfaceItem, superChildInterfaceItem, superInterfaceItem),
                classItem.allInterfaces().toSet()
            )
            assertEquals(
                setOf(childInterfaceItem, superChildInterfaceItem, superInterfaceItem),
                childInterfaceItem.allInterfaces().toSet()
            )
        }
    }

    @Test
    fun `100 - check class types`() {
        runSourceCodebaseTest(
            java(
                """
                  package test.pkg;

                  interface TestInterface {}
                  enum TestEnum {}
                  @interface TestAnnotation {}
                """
            ),
        ) {
            val interfaceItem = codebase.assertClass("test.pkg.TestInterface")
            val enumItem = codebase.assertClass("test.pkg.TestEnum")
            val annotationItem = codebase.assertClass("test.pkg.TestAnnotation")
            assertEquals(true, interfaceItem.isInterface())
            assertEquals(true, enumItem.isEnum())
            assertEquals(true, annotationItem.isAnnotationType())
        }
    }

    @Test
    fun `110 - advanced package test`() {
        runSourceCodebaseTest(
            inputSet(
                java(
                    """
                        package test.pkg;

                        class Test {
                            class Inner {}
                        }
                    """
                ),
                java("""
                        package test;
                     """),
            ),
        ) {
            val packageItem = codebase.assertPackage("test.pkg")
            val parentPackageItem = codebase.assertPackage("test")
            val rootPackageItem = codebase.assertPackage("")
            val classItem = codebase.assertClass("test.pkg.Test")
            val innerClassItem = codebase.assertClass("test.pkg.Test.Inner")
            assertEquals(1, packageItem.topLevelClasses().count())
            assertEquals(0, parentPackageItem.topLevelClasses().count())
            assertEquals(parentPackageItem, packageItem.containingPackage())
            assertEquals(rootPackageItem, parentPackageItem.containingPackage())
            assertEquals(null, rootPackageItem.containingPackage())
            assertEquals(packageItem, classItem.containingPackage())
            assertEquals(packageItem, innerClassItem.containingPackage())
        }
    }

    @Test
    fun `120 - check modifiers`() {
        runSourceCodebaseTest(
            java(
                """
                    package test.pkg;

                    public final class Test1 {
                        private int var1;
                        protected static final int var2;
                        int var3;
                    }
                """
            ),
        ) {
            val packageItem = codebase.assertPackage("test.pkg")
            val classItem1 = codebase.assertClass("test.pkg.Test1")
            val fieldItem1 = classItem1.assertField("var1")
            val fieldItem2 = classItem1.assertField("var2")
            val fieldItem3 = classItem1.assertField("var3")
            val packageMod = packageItem.mutableModifiers()
            val classMod1 = classItem1.mutableModifiers()
            val fieldMod1 = fieldItem1.mutableModifiers()
            val fieldMod2 = fieldItem2.mutableModifiers()
            val fieldMod3 = fieldItem3.mutableModifiers()
            assertEquals(true, packageMod.isPublic())
            assertEquals(true, classMod1.isPublic())
            assertEquals(false, classMod1.isSynchronized())
            assertEquals(true, fieldMod1.isPrivate())
            assertEquals(false, fieldMod1.isPackagePrivate())
            assertEquals(false, fieldMod2.isPrivate())
            assertEquals(true, fieldMod2.asAccessibleAs(fieldMod1))
            assertEquals(true, fieldMod3.isPackagePrivate())
            assertEquals(packageItem, packageMod.owner())
            assertEquals(classItem1, classMod1.owner())
            assertEquals(fieldItem1, fieldMod1.owner())
            assertEquals(fieldItem2, fieldMod2.owner())
            assertEquals(fieldItem3, fieldMod3.owner())
        }
    }

    /**
     * Check for the following:
     * 1) If a class from classpath is needed by some source class, the corresponding classItem is
     *    created
     * 2) While classpath may contain a lot of classes , only create classItems for the classes
     *    required by source classes directly or indirectly (e.g. superclass of superclass)
     */
    @Test
    fun `130 - check classes from classpath`() {
        runSourceCodebaseTest(
            java(
                """
                    package test.pkg;

                    import java.util.Date;

                    class Test extends Date {}
                """
            ),
        ) {
            // Make sure that classes (and containing package) from the source have been loaded into
            // the codebase and will be emitted.
            val pkgItem = codebase.assertPackage("test.pkg")
            assertEquals(true, pkgItem.emit)
            val classItem = codebase.assertClass("test.pkg.Test")
            assertEquals(true, classItem.emit)

            // Force the Test super class (java.util.Date) to be resolved.
            val classItemSuperClass = classItem.superClass()

            // Check that the class and package have been loaded but will not be emitted.
            val utilPkgItem = codebase.assertPackage("java.util")
            assertEquals(false, utilPkgItem.emit)
            val utilClassItem = codebase.assertClass("java.util.Date")
            assertEquals(false, utilClassItem.emit)

            // Check that the Test super class is expected.
            assertEquals(utilClassItem, classItemSuperClass)

            // Force the Date super class (java.lang.Object) to be resolved.
            val utilClassSuperClass = utilClassItem.superClass()

            // Check that the class and package have been loaded but will not be emitted.
            val langPkgItem = codebase.assertPackage("java.lang")
            assertEquals(false, langPkgItem.emit)
            val objectClassItem = codebase.assertClass("java.lang.Object")
            assertEquals(false, objectClassItem.emit)

            // Check that the Date super class is expected.
            assertEquals(objectClassItem, utilClassSuperClass)

            assertEquals(3, utilClassItem.allInterfaces().count())
        }
    }

    @Test
    fun `130 - test missing symbols`() {
        runSourceCodebaseTest(
            java(
                """
                    package test.pkg;

                    interface Interface {}
                    class Test extends UnresolvedSuper implements Interface, UnresolvedInterface {}
                """
            ),
        ) {
            val classItem = codebase.assertClass("test.pkg.Test")
            val superClassType = classItem.superClassType()

            assertEquals(null, classItem.superClass())
            assertEquals(1, classItem.allInterfaces().count())
            assertEquals(2, classItem.interfaceTypes().count())

            assertNotNull(superClassType)
            assertEquals(null, superClassType.asClass())
        }
    }

    @Test
    fun `140 - test annotations`() {
        runSourceCodebaseTest(
            inputSet(
                java(
                    """
                        package test.pkg;

                        import test.anno.FieldInfo;
                        import anno.FieldValue;
                        import test.SimpleClass;

                        class Test {
                            @test.Nullable
                            @FieldInfo(children = {"child1","child2"}, val = 5, cls = SimpleClass.class)
                            @FieldValue(testInt1+testInt2)
                            public static String myString;

                            public static final int testInt1 = 5;
                            public static final int testInt2 = 7;
                        }
                    """
                ),
                java(
                    """
                        package test.anno;

                        import java.lang.annotation.ElementType;
                        import java.lang.annotation.Retention;
                        import java.lang.annotation.RetentionPolicy;
                        import java.lang.annotation.Target;

                        @Target(ElementType.FIELD)
                        @Retention(RetentionPolicy.RUNTIME)
                        public @interface FieldInfo {
                          String name() default "FieldName";
                          String[] children();
                          int val();
                          Class<?> cls();
                        }
                     """
                ),
                java(
                    """
                        package anno;

                        public @interface FieldValue {
                          int value();
                        }
                    """
                ),
                java(
                    """
                        package test;

                        @Nullable
                        public class SimpleClass<T> {}
                    """
                ),
            ),
        ) {
            val classItem = codebase.assertClass("test.pkg.Test")
            val fieldItem = classItem.assertField("myString")

            val nullAnno = fieldItem.assertAnnotation("test.Nullable")

            val customAnno1 = fieldItem.assertAnnotation("test.anno.FieldInfo")
            val custAnno1Attr1 = customAnno1.findAttribute("children")
            val custAnno1Attr2 = customAnno1.findAttribute("val")
            val custAnno1Attr3 = customAnno1.findAttribute("cls")
            val annoClassItem1 = codebase.assertClass("test.anno.FieldInfo")
            val retAnno = annoClassItem1.assertAnnotation("java.lang.annotation.Retention")
            val tarAnno = annoClassItem1.assertAnnotation("java.lang.annotation.Target")
            val tarAnnoAtrr1 = tarAnno.findAttribute("value")

            val customAnno2 = fieldItem.assertAnnotation("anno.FieldValue")
            val annoClassItem2 = codebase.assertClass("anno.FieldValue")
            val custAnno2Attr1 = customAnno2.findAttribute("value")

            assertEquals(3, fieldItem.modifiers.annotations().count())

            assertEquals(true, nullAnno.isNullable())

            assertEquals(3, customAnno1.attributes.count())
            assertEquals(false, customAnno1.isRetention())
            assertNotNull(custAnno1Attr1)
            assertNotNull(custAnno1Attr2)
            assertNotNull(custAnno1Attr3)
            assertEquals(
                true,
                listOf("child1", "child2").toTypedArray() contentEquals
                    custAnno1Attr1.value.value() as Array<*>
            )
            assertEquals(5, custAnno1Attr2.value.value())
            assertEquals("test.SimpleClass", custAnno1Attr3.value.value())
            assertEquals(annoClassItem1, customAnno1.resolve())
            assertEquals(true, retAnno.isRetention())
            assertEquals(AnnotationRetention.RUNTIME, annoClassItem1.getRetention())

            assertEquals(annoClassItem2, customAnno2.resolve())
            assertNotNull(custAnno2Attr1)
            assertEquals(12, custAnno2Attr1.value.value())

            assertEquals("@test.Nullable", nullAnno.toSource())

            assertEquals(
                "@java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)",
                retAnno.toSource()
            )
            assertEquals(
                "@java.lang.annotation.Target(java.lang.annotation.ElementType.FIELD)",
                tarAnno.toSource()
            )
            assertEquals(true, tarAnnoAtrr1!!.value is DefaultAnnotationSingleAttributeValue)
        }
    }

    @Test
    fun `150 - advanced superMethods() test on methoditem`() {
        runSourceCodebaseTest(
            java(
                """
                    package test.pkg;

                    interface Interface1 {
                        public void method1();
                        public <T> void method2(T value);
                    }

                    interface Interface2 extends Interface1 {
                        public void method1();
                    }

                    interface Interface3 extends Interface1,Interface2 {}

                    abstract class Test1 implements Interface2 {
                        @Override
                        public void method1(){}

                        @Override
                        public <Integer> void method2(Integer value){}
                    }

                    class Test2 implements Interface3 {
                        @Override
                        public void method1(){}
                    }

                    class Test3 implements Interface2,Interface1 {
                        @Override
                        public void method1(){}
                    }
                """
            ),
        ) {
            val itfCls1 = codebase.assertClass("test.pkg.Interface1")
            val itf1Mtd1 = itfCls1.assertMethod("method1", "")
            val itf1Mtd2 = itfCls1.assertMethod("method2", "java.lang.Object")

            val itfCls2 = codebase.assertClass("test.pkg.Interface2")
            val itf2Mtd1 = itfCls2.assertMethod("method1", "")

            val classItem1 = codebase.assertClass("test.pkg.Test1")
            val cls1Mtd1 = classItem1.assertMethod("method1", "")
            val cls1Mtd2 = classItem1.assertMethod("method2", "java.lang.Object")

            val classItem2 = codebase.assertClass("test.pkg.Test2")
            val cls2Mtd1 = classItem2.assertMethod("method1", "")

            val classItem3 = codebase.assertClass("test.pkg.Test3")
            val cls3Mtd1 = classItem3.assertMethod("method1", "")

            assertEquals(listOf(itf2Mtd1), cls1Mtd1.superMethods())
            assertEquals(listOf(itf2Mtd1, itf1Mtd1), cls1Mtd1.allSuperMethods().toList())
            assertEquals(listOf(itf1Mtd2), cls1Mtd2.superMethods())
            assertEquals(listOf(itf1Mtd1, itf2Mtd1), cls2Mtd1.superMethods())
            assertEquals(listOf(itf2Mtd1, itf1Mtd1), cls3Mtd1.superMethods())
        }
    }

    @Test
    fun `160 - check field type`() {
        runSourceCodebaseTest(
            java(
                """
                    package test.pkg;

                    public class Test {
                        public int field;
                    }
                """
            ),
        ) {
            val fieldTypeItem = codebase.assertClass("test.pkg.Test").assertField("field").type()
            assertThat(fieldTypeItem).isInstanceOf(PrimitiveTypeItem::class.java)
            assertEquals(PrimitiveTypeItem.Primitive.INT, (fieldTypeItem as PrimitiveTypeItem).kind)
        }
    }

    @Test
    fun `170 - check unannotated typeString`() {
        runSourceCodebaseTest(
            java(
                """
                    package test.pkg;

                    import java.util.List;

                    public class Test {
                        public int field;

                        public void method(String a, List<Outer<String>> [] ... b, List<?extends   String> c){}

                        public <T> Outer<Integer>.Inner<T, Test1<String>> foo() {
                            return (new Outer<Integer>()).new Inner<Boolean, Test1<String>>();
                        }
                    }

                    class Outer<P> {
                        class Inner<R,S> {}
                    }

                    class Test1<String> {}
                """
            ),
        ) {
            val classItem = codebase.assertClass("test.pkg.Test")
            val methodItem1 = classItem.methods()[0]
            val methodItem2 = classItem.methods()[1]

            val fieldTypeItem = classItem.assertField("field").type()
            val returnTypeItem1 = methodItem1.returnType()
            val parameterTypeItem1 = methodItem1.parameters()[0].type()
            val parameterTypeItem2 = methodItem1.parameters()[1].type()
            val parameterTypeItem3 = methodItem1.parameters()[2].type()
            val returnTypeItem2 = methodItem2.returnType()

            assertEquals("int", fieldTypeItem.toTypeString())
            assertEquals("void", returnTypeItem1.toTypeString())
            assertEquals("java.lang.String", parameterTypeItem1.toTypeString())
            assertEquals(
                "java.util.List<test.pkg.Outer<java.lang.String>>[]...",
                parameterTypeItem2.toTypeString()
            )
            assertEquals(
                "java.util.List<? extends java.lang.String>",
                parameterTypeItem3.toTypeString()
            )
            assertEquals(
                "test.pkg.Outer<java.lang.Integer>.Inner<T,test.pkg.Test1<java.lang.String>>",
                returnTypeItem2.toTypeString()
            )
        }
    }

    @Test
    fun `180 - test classItem toType`() {
        runSourceCodebaseTest(
            java(
                """
                    package test.pkg;

                    public class Test {}
                    class Test1<S> {
                        class Test2<T extends Test> {}
                    }

                """
            ),
        ) {
            val testClass = codebase.assertClass("test.pkg.Test")
            val testClass1 = codebase.assertClass("test.pkg.Test1")
            val testClass2 = codebase.assertClass("test.pkg.Test1.Test2")
            val testClassType = testClass.type()
            val testClassType1 = testClass1.type()
            val testClassType2 = testClass2.type()

            assertThat(testClassType).isInstanceOf(ClassTypeItem::class.java)
            assertEquals("test.pkg.Test", testClassType.qualifiedName)
            assertEquals(0, testClassType.arguments.count())

            assertThat(testClassType1).isInstanceOf(ClassTypeItem::class.java)
            assertEquals("test.pkg.Test1", testClassType1.qualifiedName)
            assertEquals(1, testClassType1.arguments.count())
            val typeArgument1 = testClassType1.arguments.single()
            val typeParameter1 = testClass1.typeParameterList.single()
            typeArgument1.assertReferencesTypeParameter(typeParameter1)
            assertEquals("S", (typeArgument1 as VariableTypeItem).toString())
            assertEquals(0, typeParameter1.typeBounds().count())
            assertEquals("test.pkg.Test1<S>", testClassType1.toString())
            assertEquals(null, testClassType1.outerClassType)

            assertThat(testClassType2).isInstanceOf(ClassTypeItem::class.java)
            assertEquals("test.pkg.Test1.Test2", testClassType2.qualifiedName)
            assertEquals(1, testClassType2.arguments.count())
            val typeArgument2 = testClassType2.arguments.single()
            val typeParameter2 = testClass2.typeParameterList.single()
            typeArgument2.assertReferencesTypeParameter(typeParameter2)
            assertEquals("T", (typeArgument2 as VariableTypeItem).toString())
            assertEquals("test.pkg.Test", typeParameter2.typeBounds().single().toString())
            assertEquals("test.pkg.Test1<S>.Test2<T>", testClassType2.toString())
            assertEquals(testClassType1, testClassType2.outerClassType)
        }
    }

    @Test
    fun `190 - test constructors`() {
        runSourceCodebaseTest(
            java(
                """
                    package test.pkg;

                    public class Test {
                        public int field;
                        public Test() {}
                        public Test(int a) {
                            field = a;
                        }
                        class Test1 {}
                    }
                """
            ),
        ) {
            val testClass = codebase.assertClass("test.pkg.Test")
            assertEquals(false, testClass.hasImplicitDefaultConstructor())
            assertEquals(2, testClass.constructors().count())
            val constructorItem = testClass.constructors().first()
            assertEquals("Test", constructorItem.name())
            assertEquals(testClass.type(), constructorItem.returnType())
            assertEquals(false, testClass.hasImplicitDefaultConstructor())

            val testClass1 = codebase.assertClass("test.pkg.Test.Test1")
            val constructorItem1 = testClass1.constructors().single()
            assertEquals("Test1", constructorItem1.name())
            assertEquals("test.pkg.Test.Test1", constructorItem1.returnType().toString())
            assertEquals(testClass1.type(), constructorItem1.returnType())
            assertEquals(true, testClass1.hasImplicitDefaultConstructor())
        }
    }

    @Test
    fun `200 - test TypeParameterList name strings`() {
        runSourceCodebaseTest(
            java(
                """
                    package test.pkg;

                    import java.util.Map;
                    import java.io.Serializable;

                    class Test<@Nullable T,U extends Map<? super U, String>,V extends  Comparable & Serializable> {
                        public <Q, R extends Outer<? super U>.Inner<? extends Comparable >,S extends  Comparable & Serializable> void foo1(Q a, R b, S c) {}
                        public <A extends Object, B extends Object> void foo2() {}
                    }

                    class Outer<O> {
                        class Inner<P> {}
                    }
                    @interface Nullable {}
                """
            ),
        ) {
            val classItem = codebase.assertClass("test.pkg.Test")
            val annoItem = codebase.assertClass("test.pkg.Nullable")
            val method1Item = classItem.methods()[0]
            val method2Item = classItem.methods()[1]
            val classTypeParameterList = classItem.typeParameterList
            val method1TypeParameterList = method1Item.typeParameterList
            val method2TypeParameterList = method2Item.typeParameterList
            val annoTypeParameterList = annoItem.typeParameterList

            val classParameterNames = listOf("T", "U", "V")
            val method1ParameterNames = listOf("Q", "R", "S")
            val method2TypeParameterNames = listOf("A", "B")

            assertEquals(true, classItem.hasTypeVariables())

            assertEquals(classParameterNames, classTypeParameterList.map { it.name() })
            assertEquals(emptyList(), annoTypeParameterList.map { it.name() })
            assertEquals(method1ParameterNames, method1TypeParameterList.map { it.name() })
            assertEquals(method2TypeParameterNames, method2TypeParameterList.map { it.name() })

            assertEquals(
                "<T, U extends java.util.Map<? super U, java.lang.String>, V extends java.lang.Comparable & java.io.Serializable>",
                classTypeParameterList.toString()
            )
            assertEquals("", annoTypeParameterList.toString())
            assertEquals(
                "<Q, R extends test.pkg.Outer<? super U>.Inner<? extends java.lang.Comparable>, S extends java.lang.Comparable & java.io.Serializable>",
                method1TypeParameterList.toString()
            )
            assertEquals("<A, B>", method2TypeParameterList.toString())
        }
    }

    @Test
    fun `210 Test Method exception list`() {
        runSourceCodebaseTest(
            java(
                """
                    package test.pkg;

                    import java.io.IOException;

                    public class Test {
                        public Test() {}
                        public void foo() throws TestException, IOException {}
                    }

                    public class TestException extends Exception {
                        public TestException(String str) {
                            super(str);
                        }
                    }
                """
            ),
        ) {
            val testClass = codebase.assertClass("test.pkg.Test")
            val methodItem = testClass.assertMethod("foo", "")
            val testExceptionClass = codebase.assertClass("test.pkg.TestException")

            // This must only be available after resolving throwable types.
            assertNull(codebase.findClass("java.io.IOException"))

            // Resolve the types to classes.
            val throwableClasses = methodItem.throwsTypes().map { it.erasedClass }

            // This must be available after resolving throwable types.
            val ioExceptionClass = codebase.assertClass("java.io.IOException")

            assertEquals(listOf(testExceptionClass, ioExceptionClass), throwableClasses)
        }
    }

    @Test
    fun `210 test reference between innerclass and outerclass`() {
        runSourceCodebaseTest(
            java(
                """
                    package test.pkg;

                    public class Outer {
                        class Inner1 extends Inner {
                            class InnerInner extends Outer {}
                        }
                        class Inner extends Outer {}
                    }
                """
            ),
        ) {
            val outerClass = codebase.assertClass("test.pkg.Outer")
            val innerClass = codebase.assertClass("test.pkg.Outer.Inner")
            val innerClass1 = codebase.assertClass("test.pkg.Outer.Inner1")
            val innerInnerClass = codebase.assertClass("test.pkg.Outer.Inner1.InnerInner")

            assertEquals(outerClass, innerClass.containingClass())
            assertEquals(outerClass, innerClass1.containingClass())
            assertEquals(innerClass1, innerInnerClass.containingClass())
        }
    }

    @Test
    fun `220 test ClassItem createDefaultConstructor`() {
        runSourceCodebaseTest(
            java(
                """
                    package test.pkg;

                    public final class Test<T extends String> {}
                """
            ),
        ) {
            val classItem = codebase.assertClass("test.pkg.Test")
            val ctorItem = classItem.createDefaultConstructor()

            assertEquals("Test", ctorItem.name())
            assertEquals(classItem, ctorItem.containingClass())
            assertEquals(classItem.type(), ctorItem.returnType())
            assertEquals(
                ctorItem.modifiers.getVisibilityLevel(),
                classItem.modifiers.getVisibilityLevel()
            )
            assertEquals(emptyList(), ctorItem.throwsTypes())
            assertEquals(emptyList(), ctorItem.parameters())
        }
    }

    @Test
    fun `230 test public name and default value of parameters`() {
        runSourceCodebaseTest(
            java(
                """
                    package test.pkg;

                    import java.lang.annotation.ElementType;
                    import java.lang.annotation.Target;

                    public class Test {
                        public void foo(@ParameterName("TestParam") @DefaultValue(5) int parameter) {
                        }
                    }

                    @Target(ElementType.PARAMETER)
                    @interface DefaultValue {
                        int value();
                    }

                    @Target(ElementType.PARAMETER)
                    @interface ParameterName {
                        String value();
                    }
                """
            ),
        ) {
            val methodItem = codebase.assertClass("test.pkg.Test").methods().single()
            val paramItem = methodItem.parameters().single()

            assertEquals("parameter", paramItem.name())
            assertEquals(methodItem, paramItem.containingMethod())
            assertEquals("TestParam", paramItem.publicName())
            assertEquals(true, paramItem.hasDefaultValue())
            assertEquals(true, paramItem.isDefaultValueKnown())
            assertEquals("5", paramItem.defaultValue())
        }
    }

    @Test
    fun `240 test documentations`() {
        runSourceCodebaseTest(
            java(
                """
                    /*
                        A Header Comment
                    */
                    /**
                        A JavaDoc Header Comment
                    */
                    package test.pkg;

                    /** Class documentation */
                    public class Test {
                        /** Field Doc */
                        public int field;
                        /**
                         * Method documentation
                         * Multiple
                         * Lines
                         */
                         public void foo() {}
                         // Non javadoc comment
                         public static int field1 = 5;

                         class Inner {}
                    }
                    class Test1 {}
                """
            ),
        ) {
            val pkgItem = codebase.assertPackage("test.pkg")
            val classItem = codebase.assertClass("test.pkg.Test")
            val classItem1 = codebase.assertClass("test.pkg.Test1")
            val innerClassItem = codebase.assertClass("test.pkg.Test.Inner")
            val methodItem = classItem.methods().single()
            val fieldItem = classItem.assertField("field")
            val fieldItem1 = classItem.assertField("field1")
            val sourceFile = classItem.sourceFile()!!
            val headerComment =
                """
                    /*
                        A Header Comment
                    */
                    /**
                        A JavaDoc Header Comment
                    */

                """
                    .trimIndent()
            val methodComment =
                """
                    /**
                         * Method documentation
                         * Multiple
                         * Lines
                         */
                """
                    .trimIndent()
            assertEquals(null, innerClassItem.getSourceFile())
            assertEquals(headerComment, sourceFile.getHeaderComments())
            assertEquals(methodComment, methodItem.documentation)
            assertEquals("/** Class documentation */", classItem.documentation)
            assertEquals("/** Field Doc */", fieldItem.documentation)
            assertEquals("", fieldItem1.documentation)
            assertEquals("", pkgItem.documentation)
            assertEquals(classItem.sourceFile(), classItem1.sourceFile())
        }
    }

    @Test
    fun `250 - test enum class and field`() {
        runSourceCodebaseTest(
            java(
                """
                    package test.pkg;

                    public enum Test {
                        ENUM1,
                        ENUM2,;
                        public static final int IntField = 5;

                        public Test valueOf(int serial) {
                            if(serial == 1) {return ENUM1;}
                            else return ENUM2;
                        }

                        public int valueOf(Test a, String b) {return 7;}
                    }

                    enum Test1 {}

                    class Test2 {
                        static final Test field = Test.ENUM2;
                    }
                """
            ),
        ) {
            val classItem = codebase.assertClass("test.pkg.Test")
            val fieldItem1 = classItem.assertField("ENUM1")
            val fieldItem2 = classItem.assertField("IntField")
            val classItem1 = codebase.assertClass("test.pkg.Test1")
            val nonEnumClassField = codebase.assertClass("test.pkg.Test2").assertField("field")

            assertEquals(true, classItem.isEnum())
            assertEquals(2, classItem.methods().count())
            assertEquals(true, fieldItem1.isEnumConstant())
            assertEquals(false, fieldItem2.isEnumConstant())
            assertEquals(true, classItem1.isEnum())
            assertEquals(0, classItem1.methods().count())
            assertEquals(false, nonEnumClassField.isEnumConstant())
        }
    }

    @Test
    fun `260 - test doconly members`() {
        runSourceCodebaseTest(
            java(
                """
                    package test.pkg;

                    public class Test {
                        /** @doconly */
                        public class Inner {
                            public int InnerField;
                        }

                        /** @doconly Some docs here */
                        public int Field;
                    }
                """
            ),
        ) {
            val classItem = codebase.assertClass("test.pkg.Test")
            val innerClassItem = codebase.assertClass("test.pkg.Test.Inner")
            val fieldItem = classItem.assertField("Field")
            val innerFieldItem = innerClassItem.assertField("InnerField")

            assertEquals(false, classItem.docOnly)
            assertEquals(true, innerClassItem.docOnly)
            assertEquals(false, innerFieldItem.docOnly)
            assertEquals(true, fieldItem.docOnly)
        }
    }
}
