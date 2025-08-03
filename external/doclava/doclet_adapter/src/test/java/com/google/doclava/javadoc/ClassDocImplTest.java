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

package com.google.doclava.javadoc;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.sun.javadoc.FieldDoc;
import com.sun.javadoc.MethodDoc;
import com.sun.javadoc.ProgramElementDoc;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

public class ClassDocImplTest extends BaseTest {

    private ClassDocImpl abstractEmptyInterface;
    private ClassDocImpl abstractEmptyClass;
    private ClassDocImpl publicClass;
    private ClassDocImpl publicEnum;
    private ClassDocImpl publicAnnotation;
    private ClassDocImpl publicInterface;
    private ClassDocImpl simpleEnum;
    private ClassDocImpl serializable;
    private ClassDocImpl extendsSerializable;
    private ClassDocImpl extendsExternalizable;
    private ClassDocImpl implementsSerializable;
    private ClassDocImpl implementsExternalizable;

    private ClassDocImpl javaLangError;
    private ClassDocImpl javaLangException;
    private ClassDocImpl javaLangObject;
    private ClassDocImpl javaLangThrowable;
    private ClassDocImpl javaUtilMap;

    private ClassDocImpl publicClassWithNests;
    private ClassDocImpl publicClassWithNests$Nest1;
    private ClassDocImpl publicClassWithNests$Nest1$Nest2;
    private ClassDocImpl publicClassWithNests$Nest1$Nest2$Nest3;
    private ClassDocImpl innerClasses;

    private ClassDocImpl constructors;

    private ClassDocImpl packagePrivateClass;

    private ClassDocImpl fieldsAccessModifiers;
    private ClassDocImpl methodsAccessModifiers;

    @Before
    public void setUp() {
        super.setUp();
        abstractEmptyClass = ClassDocImpl.create(CLASS.publicAbstractClass, context);
        abstractEmptyInterface = ClassDocImpl.create(CLASS.publicAbstractInterface, context);

        publicClass = ClassDocImpl.create(CLASS.publicClass, context);
        publicClassWithNests = ClassDocImpl.create(CLASS.publicClassWithNests, context);
        publicEnum = ClassDocImpl.create(CLASS.publicEnum, context);
        simpleEnum = ClassDocImpl.create(CLASS.simpleEnum, context);
        // note that it is created using AnnotationTypeDocImpl ctor.
        publicAnnotation = AnnotationTypeDocImpl.create(CLASS.publicAnnotation, context);
        publicInterface = ClassDocImpl.create(CLASS.publicInterface, context);
        packagePrivateClass = ClassDocImpl.create(CLASS.packagePrivateClass, context);

        javaLangError = ClassDocImpl.create(INSTANCE.javaLangError, context);
        javaLangException = ClassDocImpl.create(INSTANCE.javaLangException, context);
        javaLangObject = ClassDocImpl.create(INSTANCE.javaLangObject, context);
        javaLangThrowable = ClassDocImpl.create(INSTANCE.javaLangThrowable, context);
        javaUtilMap = ClassDocImpl.create(CLASS.javaUtilMap, context);

        publicClassWithNests = ClassDocImpl.create(CLASS.publicClassWithNests, context);
        publicClassWithNests$Nest1 = ClassDocImpl.create(CLASS.publicClassWithNests$Nest1, context);
        publicClassWithNests$Nest1$Nest2 = ClassDocImpl.create(
                CLASS.publicClassWithNests$Nest1$Nest2, context);
        publicClassWithNests$Nest1$Nest2$Nest3 = ClassDocImpl.create(
                CLASS.publicClassWithNests$Nest1$Nest2$Nest3, context);
        innerClasses = ClassDocImpl.create(CLASS.innerClasses, context);

        constructors = ClassDocImpl.create(CLASS.constructors, context);

        implementsSerializable = ClassDocImpl.create(CLASS.implementsSerializable, context);
        implementsExternalizable = ClassDocImpl.create(CLASS.implementsExternalizable, context);
        serializable = ClassDocImpl.create(INTERFACE.serializable, context);
        extendsSerializable = ClassDocImpl.create(INTERFACE.extendsSerializable, context);
        extendsExternalizable = ClassDocImpl.create(INTERFACE.extendsExternalizable, context);

        fieldsAccessModifiers = ClassDocImpl.create(CLASS.fieldsAccessModifiers, context);
        methodsAccessModifiers = ClassDocImpl.create(CLASS.methodsAccessModifiers, context);
    }

    @Test
    public void isClass() {
        assertFalse(publicAnnotation.isClass());
        assertFalse(publicInterface.isClass());

        assertTrue(publicClass.isClass());
        assertTrue(publicEnum.isClass());
        assertTrue(javaLangObject.isClass());
        assertTrue(javaLangError.isClass());
        assertTrue(javaLangException.isClass());
    }

    @Test
    public void isOrdinaryClass() {
        // not an enumeration, interface, annotation, exception, or an error.
        assertFalse(publicAnnotation.isOrdinaryClass());
        assertFalse(publicEnum.isOrdinaryClass());
        assertFalse(publicInterface.isOrdinaryClass());
        assertFalse(javaLangError.isOrdinaryClass());
        assertFalse(javaLangException.isOrdinaryClass());

        assertTrue(publicClass.isOrdinaryClass());
        assertTrue(javaLangObject.isOrdinaryClass());
    }

    @Test
    public void isEnum() {
        assertTrue(publicEnum.isEnum());

        assertFalse(publicAnnotation.isEnum());
        assertFalse(publicClass.isEnum());
        assertFalse(publicInterface.isEnum());
        assertFalse(javaLangError.isEnum());
        assertFalse(javaLangException.isEnum());
        assertFalse(javaLangObject.isEnum());
    }

    @Test
    public void isInterface() {
        assertTrue(publicInterface.isInterface());

        assertFalse(publicAnnotation.isInterface());
        assertFalse(publicClass.isInterface());
        assertFalse(publicEnum.isInterface());
        assertFalse(javaLangError.isInterface());
        assertFalse(javaLangException.isInterface());
        assertFalse(javaLangObject.isInterface());
    }

    @Test
    public void isException() {
        assertTrue(javaLangException.isException());

        assertFalse(publicAnnotation.isException());
        assertFalse(publicClass.isException());
        assertFalse(publicEnum.isException());
        assertFalse(publicInterface.isException());
        assertFalse(javaLangError.isException());
        assertFalse(javaLangObject.isException());
    }

    @Test
    public void isError() {
        assertTrue(javaLangError.isError());

        assertFalse(publicAnnotation.isError());
        assertFalse(publicClass.isError());
        assertFalse(publicEnum.isError());
        assertFalse(publicInterface.isError());
        assertFalse(javaLangException.isError());
        assertFalse(javaLangObject.isError());
    }

    @Test
    public void name() {
        assertEquals("AbstractEmptyClass", abstractEmptyClass.name());
        assertEquals("AbstractEmptyInterface", abstractEmptyInterface.name());
        assertEquals("PublicInterface", publicInterface.name());
        assertEquals("PublicAnnotation", publicAnnotation.name());
        assertEquals("PublicClass", publicClass.name());
        assertEquals("PublicEnum", publicEnum.name());

        assertEquals("Error", javaLangError.name());
        assertEquals("Exception", javaLangException.name());
        assertEquals("Object", javaLangObject.name());

        assertEquals("PublicClassWithNests", publicClassWithNests.name());
        assertEquals("PublicClassWithNests.Nest1", publicClassWithNests$Nest1.name());
        assertEquals("PublicClassWithNests.Nest1.Nest2", publicClassWithNests$Nest1$Nest2.name());
    }

    @Test
    public void qualifiedName() {
        assertEquals("com.example.classes.AbstractEmptyClass", abstractEmptyClass.qualifiedName());
        assertEquals("com.example.classes.AbstractEmptyInterface",
                abstractEmptyInterface.qualifiedName());
        assertEquals("com.example.classes.PublicInterface", publicInterface.qualifiedName());
        assertEquals("com.example.classes.PublicAnnotation", publicAnnotation.qualifiedName());
        assertEquals("com.example.classes.PublicClass", publicClass.qualifiedName());
        assertEquals("com.example.classes.PublicEnum", publicEnum.qualifiedName());

        assertEquals("java.lang.Error", javaLangError.qualifiedName());
        assertEquals("java.lang.Exception", javaLangException.qualifiedName());
        assertEquals("java.lang.Object", javaLangObject.qualifiedName());

        assertEquals("com.example.classes.PublicClassWithNests",
                publicClassWithNests.qualifiedName());
        assertEquals("com.example.classes.PublicClassWithNests.Nest1",
                publicClassWithNests$Nest1.qualifiedName());
        assertEquals("com.example.classes.PublicClassWithNests.Nest1.Nest2",
                publicClassWithNests$Nest1$Nest2.qualifiedName());
    }

    /**
     * @see #constructors()
     */
    @Test
    public void isIncluded() {
        assertTrue(publicClass.isIncluded());

        assertFalse(packagePrivateClass.isIncluded());
    }

    @Test
    public void isAbstract() {
        assertTrue(abstractEmptyClass.isAbstract());
        assertTrue(abstractEmptyInterface.isAbstract());
        assertTrue(publicInterface.isAbstract());
        assertTrue(publicAnnotation.isAbstract());

        assertFalse(publicClass.isAbstract());
        assertFalse(publicEnum.isAbstract());
        assertFalse(javaLangError.isAbstract());
        assertFalse(javaLangException.isAbstract());
        assertFalse(javaLangObject.isAbstract());
    }

    @Test
    public void isSerializable() {
        // Classes
        assertTrue(implementsSerializable.isSerializable());
        assertTrue(implementsExternalizable.isSerializable());

        assertFalse(javaLangObject.isSerializable());

        // Interfaces
        assertTrue(extendsSerializable.isSerializable());
        assertTrue(extendsExternalizable.isSerializable());
    }

    @Test
    public void isExternalizable() {
        // Classes
        assertTrue(implementsExternalizable.isExternalizable());

        assertFalse(implementsSerializable.isExternalizable());
        assertFalse(javaLangObject.isExternalizable());

        // Interfaces
        assertTrue(extendsExternalizable.isExternalizable());

        assertFalse(extendsSerializable.isExternalizable());
    }

    @Ignore("Not yet implemented")
    @Test
    public void serializationMethods() {
    }

    @Ignore("Not yet implemented")
    @Test
    public void serializableFields() {
    }

    @Ignore("Not yet implemented")
    @Test
    public void definesSerializableFields() {
    }

    @Test
    public void superclass() {
        // a) interfaces (always null)
        assertNull(publicInterface.superclass());

        // b) classes
        // 1. Object (no superclass)
        assertNull(javaLangObject.superclass());
        // 2. Object <- PublicClass
        assertEquals(javaLangObject, publicClass.superclass());
        // 3. Object <- Throwable <- Error
        assertEquals(javaLangThrowable, javaLangError.superclass());
        assertNotEquals(javaLangObject, javaLangError.superclass());
        assertEquals(javaLangObject, javaLangError.superclass().superclass());
    }

    @Test
    public void superclassType() {
        // a) interfaces (always null)
        assertNull(publicInterface.superclassType());

        // b) classes
        // 1. Object (no superclass)
        assertNull(javaLangObject.superclassType());
        // 2. Object <- PublicClass
        assertEquals("java.lang.Object", publicClass.superclassType().qualifiedTypeName());
        // 3. Object <- Throwable <- Error
        assertEquals("java.lang.Throwable", javaLangError.superclassType().qualifiedTypeName());
        assertNotEquals("java.lang.Object", javaLangError.superclassType().qualifiedTypeName());
    }

    @Test
    public void subclassOf() {
        // 1. Class is subclass of itself
        assertTrue(javaLangObject.subclassOf(javaLangObject));
        assertTrue(javaLangError.subclassOf(javaLangError));
        assertTrue(publicClass.subclassOf(publicClass));

        // 2. Class is subclass of java.lang.Object
        assertTrue(javaLangError.subclassOf(javaLangObject));
        assertTrue(publicClass.subclassOf(javaLangObject));

        // 3. Interface is subclass of java.lang.Object *only*
        assertTrue(publicInterface.subclassOf(javaLangObject));
        assertFalse(publicInterface.subclassOf(publicInterface));
        assertFalse(extendsExternalizable.subclassOf(extendsSerializable));
    }

    @Ignore("Not yet implemented")
    @Test
    public void interfaces() {
    }

    @Test
    public void interfaceTypes() {
        // a) interfaces
        // 1. Serializable
        var types = serializable.interfaceTypes();
        assertArrayEmpty(types);

        // 2. Interface extends Serializable
        types = extendsSerializable.interfaceTypes();
        assertEquals(1, types.length);
        assertEquals("java.io.Serializable", types[0].qualifiedTypeName());

        // b) classes
        // 1. Object does not implement any interface
        types = javaLangObject.interfaceTypes();
        assertArrayEmpty(types);

        // 2. Class implements Serializable
        types = implementsSerializable.interfaceTypes();
        assertEquals(1, types.length);
        assertEquals("java.io.Serializable", types[0].qualifiedTypeName());
    }

    @Test
    public void typeParameters() {
        var params = javaLangObject.typeParameters();
        assertArrayEmpty(params);

        params = javaUtilMap.typeParameters();
        assertEquals(2, params.length);
        assertEquals("K", params[0].simpleTypeName());
        assertEquals("V", params[1].simpleTypeName());
    }

    @Ignore("Not yet implemented")
    @Test
    public void typeParamTags() {
    }

    /**
     * @implNote This (and also {@link #fields_filter()} and {@link #isIncluded()}) tests do not
     * cover cases when Doclet is supplied with a non-default
     * <b>selection control</b> flag (default is {@code -protected}), i.e. {@code -public}, {@code
     * -package} or {@code -private}. It also does not currently cover selection control options
     * from new Doclet API (e.g. {@code --show-members}.
     * @see jdk.javadoc.doclet definition of <b>selection control</b> in "Terminology" section
     * @see jdk.javadoc.doclet new options in "Options" section
     */
    @Test
    public void fields() {
        // 1. Class with four fields (public, protected, private and package-private).
        // Should include only public and protected.
        var classFields = fieldsAccessModifiers.fields();
        assertEquals(2, classFields.length);

        // 2. Public enum has no declared constructors, but has one implicit private.
        var enumFields = publicEnum.fields();
        assertArrayEmpty(enumFields);

        //TODO: Handle all selection control variants.
    }

    /**
     * @see #fields()
     */
    @Test
    public void fields_filter() {
        // 1. Class with four fields (public, protected, private and package-private).

        // filter(true): Should include only public and protected.
        var classFields = fieldsAccessModifiers.fields(true);
        assertEquals(2, classFields.length);

        // filter(false): Should include all four.
        classFields = fieldsAccessModifiers.fields(false);
        assertEquals(4, classFields.length);
    }

    @Test
    public void enumConstants() {
        assertArrayEmpty(publicClass.enumConstants());
        assertArrayEmpty(publicInterface.enumConstants());
        assertArrayEmpty(publicAnnotation.enumConstants());

        FieldDoc[] constants = simpleEnum.enumConstants();
        assertEquals(3, constants.length);
        assertEquals("A", constants[0].name());
        assertEquals("B", constants[1].name());
        assertEquals("C", constants[2].name());
    }

    private static <T> void assertArrayEmpty(T[] array) {
        assertEquals(0, array.length);
    }

    /**
     * @implNote This (and also {@link #fields_filter()} and {@link #isIncluded()}) tests do not
     * cover cases when Doclet is supplied with a non-default <b>selection control</b> flag (default
     * is {@code -protected}), i.e. {@code -public}, {@code -package} or {@code -private}. It also
     * does not currently cover selection control options from new Doclet API (e.g.
     * {@code --show-members}.
     * @see jdk.javadoc.doclet definition of <b>selection control</b> in "Terminology" section
     * @see jdk.javadoc.doclet new options in "Options" section
     */
    @Test
    public void methods() {
        assertArrayEmpty(publicClass.methods());

        // Class with 4 methods (public, protected, private and package-private).
        // Should include only public and protected.
        MethodDoc[] methods = methodsAccessModifiers.methods();
        assertEquals(2, methods.length);
    }

    /**
     * @see #methods()
     */
    @Test
    public void methods_filter() {
        // 1. Class with four methods (public, protected, private and package-private).

        // filter(true): Should include only public and protected.
        MethodDoc[] methods = methodsAccessModifiers.methods(true);
        assertEquals(2, methods.length);

        // filter(false): Should include all four.
        methods = methodsAccessModifiers.methods(false);
        assertEquals(4, methods.length);
    }

    /**
     * @implNote This (and also {@link #constructors_filter_false()},
     * {@link #constructors_filter_true()} and {@link #isIncluded()}) tests do not cover cases when
     * Doclet is supplied with a non-default <b>selection control</b> flag (default is
     * {@code -protected}), i.e. {@code -public}, {@code -package} or {@code -private}. It also does
     * not currently cover selection control options from new Doclet API (e.g.
     * {@code --show-members}.
     * @see jdk.javadoc.doclet definition of <b>selection control</b> in "Terminology" section
     * @see jdk.javadoc.doclet new options in "Options" section
     */
    @Test
    public void constructors() {
        // 1. Class with four constructors (public, protected, private and package-private).
        // Should include only public and protected.
        var ctors = constructors.constructors();
        assertEquals(2, ctors.length);

        // 2. Public enum has no declared constructors, but has one implicit private.
        var enumConstructors = publicEnum.constructors();
        assertArrayEmpty(enumConstructors);

        //TODO: Handle all selection control variants.
    }

    /**
     * @see #constructors()
     */
    @Test
    public void constructors_filter_true() {
        // 1. Class with four constructors (public, protected, private and package-private).
        // Should include only public and protected.
        var ctors = constructors.constructors(true);
        assertEquals(2, ctors.length);

        // 2. Public enum has no declared constructors, but has one implicit private.
        var enumConstructors = publicEnum.constructors(true);
        assertArrayEmpty(enumConstructors);
    }

    /**
     * @see #constructors()
     */
    @Test
    public void constructors_filter_false() {
        // 1. Class with four constructors (public, protected, private and package-private).
        // Should include all four.
        var ctors = constructors.constructors(false);
        assertEquals(4, ctors.length);

        // 2. Public enum has no declared constructors, but has one implicit private.
        var enumConstructors = publicEnum.constructors(false);
        assertEquals(1, enumConstructors.length);
    }

    @Test
    public void innerClasses() {
        // Test is intentionally empty as it innerClasses() is effectively innerClasses(true) and
        // is covered in #innerClasses_filter_true().
    }

    /**
     * @implNote This (and also {@link #innerClasses_filter_false()}, and {@link #isIncluded()})
     * tests do not cover cases when Doclet is supplied with a non-default <b>selection control</b>
     * flag (default is {@code -protected}), i.e. {@code -public}, {@code -package} or
     * {@code -private}. It also does not currently cover selection control options from new Doclet
     * API (e.g. {@code --show-members}.
     * @see jdk.javadoc.doclet definition of <b>selection control</b> in "Terminology" section
     * @see jdk.javadoc.doclet new options in "Options" section
     */
    @Test
    public void innerClasses_filter_true() {
        // 1. No inner classes
        assertArrayEmpty(publicAnnotation.innerClasses(true));
        assertArrayEmpty(publicClass.innerClasses(true));
        assertArrayEmpty(publicEnum.innerClasses(true));
        assertArrayEmpty(publicInterface.innerClasses(true));

        // 2. innerClasses() should return only immediate inner classes.
        /*
        public class PublicClassWithNests {
            public class Nest1 {
                public class Nest2 {
                    public class Nest3 {
                    }
                }
            }
        }
         */
        assertArrayEquals(new String[]{"PublicClassWithNests.Nest1"},
                getInnerClassesNames(publicClassWithNests, true));
        assertArrayEquals(new String[]{"PublicClassWithNests.Nest1.Nest2"},
                getInnerClassesNames(publicClassWithNests$Nest1, true));
        assertArrayEquals(new String[]{"PublicClassWithNests.Nest1.Nest2.Nest3"},
                getInnerClassesNames(publicClassWithNests$Nest1$Nest2, true));
        assertArrayEmpty(getInnerClassesNames(publicClassWithNests$Nest1$Nest2$Nest3, true));

        // InnerClasses has 16 inner classes:
        // {public,private,protected,package-private}
        // x
        // {annotation,class,enum,interface}

        // 3. innerClasses() should return only classes and interfaces (no annotations/enums).
        assertTrue(Arrays.stream(innerClasses.innerClasses(true))
                .allMatch(cls -> cls.isClass() || cls.isInterface())
        );

        // 4. innerClasses() should return only public/protected/package-private (no private).
        assertTrue(Arrays.stream(innerClasses.innerClasses(true))
                .allMatch(cls -> cls.isPublic() || cls.isProtected() || cls.isPackagePrivate())
        );
    }

    @Test
    public void innerClasses_filter_false() {
        // 1. No inner classes
        assertArrayEmpty(publicAnnotation.innerClasses(false));
        assertArrayEmpty(publicClass.innerClasses(false));
        assertArrayEmpty(publicEnum.innerClasses(false));
        assertArrayEmpty(publicInterface.innerClasses(false));

        // 2. innerClasses() should return only immediate inner classes.
        /*
        public class PublicClassWithNests {
            public class Nest1 {
                public class Nest2 {
                    public class Nest3 {
                    }
                }
            }
        }
         */
        assertArrayEquals(new String[]{"PublicClassWithNests.Nest1"},
                getInnerClassesNames(publicClassWithNests, false));
        assertArrayEquals(new String[]{"PublicClassWithNests.Nest1.Nest2"},
                getInnerClassesNames(publicClassWithNests$Nest1, false));
        assertArrayEquals(new String[]{"PublicClassWithNests.Nest1.Nest2.Nest3"},
                getInnerClassesNames(publicClassWithNests$Nest1$Nest2, false));
        assertArrayEmpty(getInnerClassesNames(publicClassWithNests$Nest1$Nest2$Nest3, false));

        // InnerClasses has 16 inner classes:
        // {public,private,protected,package-private}
        // x
        // {annotation,class,enum,interface}

        // 3. innerClasses() should return only classes and interfaces (no annotations/enums).
        assertTrue(Arrays.stream(innerClasses.innerClasses(false))
                .allMatch(cls -> cls.isClass() || cls.isInterface())
        );

        // 4. innerClasses() should return all (public/protected/package-private/private).
        assertTrue(Arrays.stream(innerClasses.innerClasses(false))
                .allMatch(cls -> cls.isPublic() || cls.isProtected() || cls.isPackagePrivate()
                        || cls.isPrivate())
        );
    }

    private static String[] getInnerClassesNames(ClassDocImpl cls, boolean filter) {
        return Arrays.stream(cls.innerClasses(filter))
                .map(ProgramElementDoc::name)
                .toArray(String[]::new);
    }

    @Ignore("Not yet implemented")
    @Test
    public void findClass() {
    }

    @Ignore("Not yet implemented")
    @Test
    public void importedClasses() {
    }

    @Ignore("Not yet implemented")
    @Test
    public void importedPackages() {
    }

    @Test
    public void typeName() {
        assertEquals("AbstractEmptyClass", abstractEmptyClass.typeName());
        assertEquals("AbstractEmptyInterface", abstractEmptyInterface.typeName());
        assertEquals("PublicInterface", publicInterface.typeName());
        assertEquals("PublicAnnotation", publicAnnotation.typeName());
        assertEquals("PublicClass", publicClass.typeName());
        assertEquals("PublicEnum", publicEnum.typeName());

        assertEquals("Error", javaLangError.typeName());
        assertEquals("Exception", javaLangException.typeName());
        assertEquals("Object", javaLangObject.typeName());

        assertEquals("PublicClassWithNests", publicClassWithNests.typeName());
        assertEquals("PublicClassWithNests.Nest1", publicClassWithNests$Nest1.typeName());
        assertEquals("PublicClassWithNests.Nest1.Nest2",
                publicClassWithNests$Nest1$Nest2.typeName());
    }

    @Test
    public void qualifiedTypeName() {
        assertEquals("com.example.classes.AbstractEmptyClass",
                abstractEmptyClass.qualifiedTypeName());
        assertEquals("com.example.classes.AbstractEmptyInterface",
                abstractEmptyInterface.qualifiedName());
        assertEquals("com.example.classes.PublicInterface", publicInterface.qualifiedName());
        assertEquals("com.example.classes.PublicAnnotation", publicAnnotation.qualifiedName());
        assertEquals("com.example.classes.PublicClass", publicClass.qualifiedName());
        assertEquals("com.example.classes.PublicEnum", publicEnum.qualifiedName());

        assertEquals("java.lang.Error", javaLangError.qualifiedName());
        assertEquals("java.lang.Exception", javaLangException.qualifiedName());
        assertEquals("java.lang.Object", javaLangObject.qualifiedName());

        assertEquals("com.example.classes.PublicClassWithNests",
                publicClassWithNests.qualifiedName());
        assertEquals("com.example.classes.PublicClassWithNests.Nest1",
                publicClassWithNests$Nest1.qualifiedName());
        assertEquals("com.example.classes.PublicClassWithNests.Nest1.Nest2",
                publicClassWithNests$Nest1$Nest2.qualifiedName());

        assertEquals("java.util.Map", javaUtilMap.qualifiedTypeName());
    }

    @Test
    public void simpleTypeName() {
        assertEquals("AbstractEmptyClass", abstractEmptyClass.simpleTypeName());
        assertEquals("AbstractEmptyInterface", abstractEmptyInterface.simpleTypeName());
        assertEquals("PublicInterface", publicInterface.simpleTypeName());
        assertEquals("PublicAnnotation", publicAnnotation.simpleTypeName());
        assertEquals("PublicClass", publicClass.simpleTypeName());
        assertEquals("PublicEnum", publicEnum.simpleTypeName());

        assertEquals("Error", javaLangError.simpleTypeName());
        assertEquals("Exception", javaLangException.simpleTypeName());
        assertEquals("Object", javaLangObject.simpleTypeName());

        assertEquals("PublicClassWithNests", publicClassWithNests.simpleTypeName());
        assertEquals("Nest1", publicClassWithNests$Nest1.simpleTypeName());
        assertEquals("Nest2", publicClassWithNests$Nest1$Nest2.simpleTypeName());
    }

    @Test
    public void dimension() {
        assertEquals("", publicClass.dimension());
    }

    @Test
    public void isPrimitive() {
        assertFalse(publicClass.isPrimitive());
        assertFalse(publicInterface.isPrimitive());
        assertFalse(publicEnum.isPrimitive());
    }

    @Test
    public void asClassDoc() {
        assertEquals(publicClass, publicClass.asClassDoc());
        assertEquals(publicInterface, publicInterface.asClassDoc());
        assertEquals(publicEnum, publicEnum.asClassDoc());
    }

    @Test
    public void asParameterizedType() {
        assertNull(publicClass.asParameterizedType());
        assertNull(publicInterface.asParameterizedType());
        assertNull(publicEnum.asParameterizedType());
    }

    @Test
    public void asTypeVariable() {
        assertNull(publicClass.asTypeVariable());
        assertNull(publicInterface.asTypeVariable());
        assertNull(publicEnum.asTypeVariable());
    }

    @Test
    public void asWildcardType() {
        assertNull(publicClass.asWildcardType());
        assertNull(publicInterface.asWildcardType());
        assertNull(publicEnum.asWildcardType());
    }

    @Test
    public void asAnnotatedType() {
        assertNull(publicClass.asAnnotatedType());
        assertNull(publicInterface.asAnnotatedType());
        assertNull(publicEnum.asAnnotatedType());
    }

    @Test
    public void asAnnotationTypeDoc() {
        assertNull(publicClass.asAnnotationTypeDoc());
        assertNull(publicInterface.asAnnotationTypeDoc());
        assertNull(publicEnum.asAnnotationTypeDoc());
    }

    @Ignore("Not yet implemented")
    @Test
    public void getElementType() {
    }

    @Test
    public void modifiers() {
        assertEquals("public abstract", abstractEmptyClass.modifiers());
        assertEquals("public", publicClass.modifiers());
        assertEquals("public final", publicEnum.modifiers());

        // interfaces and annotations should not have 'abstract' modifier
        assertEquals("public interface", publicInterface.modifiers());
        assertEquals("public interface", publicAnnotation.modifiers());
    }

    @Test
    public void modifierSpecifier() {
        assertEquals(Modifier.PUBLIC | Modifier.ABSTRACT,
                abstractEmptyClass.modifierSpecifier());
        assertEquals(Modifier.PUBLIC, publicClass.modifierSpecifier());
        assertEquals(Modifier.PUBLIC | Modifier.FINAL, publicEnum.modifierSpecifier());

        // interfaces and annotations should not have 'abstract' modifier
        assertEquals(Modifier.PUBLIC | Modifier.INTERFACE, publicInterface.modifierSpecifier());
        assertEquals(Modifier.PUBLIC | Modifier.INTERFACE, publicAnnotation.modifierSpecifier());
    }
}