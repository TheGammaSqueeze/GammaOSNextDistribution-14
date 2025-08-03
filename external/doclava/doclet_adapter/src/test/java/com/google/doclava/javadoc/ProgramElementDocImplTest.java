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

package com.google.doclava.javadoc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import com.google.doclava.javadoc.BaseTest.METHOD.OF_INTERFACE;
import com.sun.javadoc.AnnotationDesc;
import com.sun.javadoc.AnnotationDesc.ElementValuePair;
import org.junit.Before;
import org.junit.Test;

public class ProgramElementDocImplTest extends BaseTest {

    private ClassDocImpl topLevel;
    private ClassDocImpl nest1;
    private ClassDocImpl nest2;
    private MethodDocImpl method_ofInterface;
    private ConstructorDocImpl ctor;
    private ClassDocImpl annotatedClass;

    @Before
    public void setUp() {
        super.setUp();
        topLevel = ClassDocImpl.create(CLASS.publicClassWithNests, context);
        nest1 = ClassDocImpl.create(CLASS.publicClassWithNests$Nest1, context);
        nest2 = ClassDocImpl.create(CLASS.publicClassWithNests$Nest1$Nest2, context);
        method_ofInterface = MethodDocImpl.create(OF_INTERFACE.public_void_arg0, context);
        ctor = ConstructorDocImpl.create(CONSTRUCTOR.empty, context);
        annotatedClass = ClassDocImpl.create(CLASS.annotatedClass, context);
    }

    @Test
    public void containingClass() {
        assertNull(topLevel.containingClass());
        assertEquals("PublicClassWithNests", nest1.containingClass().name());
        assertEquals("PublicClassWithNests.Nest1", nest2.containingClass().name());
        assertEquals("OfInterface", method_ofInterface.containingClass().name());
        assertEquals("Constructors", ctor.containingClass().name());
    }

    @Test
    public void containingPackage() {
        assertEquals("classes", topLevel.containingPackage().name());
        assertEquals("classes", nest1.containingPackage().name());
        assertEquals("classes", nest2.containingPackage().name());
        assertEquals("methods", method_ofInterface.containingPackage().name());
        assertEquals("constructors", ctor.containingPackage().name());
    }

    @Test
    public void annotations() {
        assertAnnotations(topLevel.annotations());

        assertAnnotations(annotatedClass.annotations(),
                "com.example.classes.UniversalAnnotation",
                "com.example.classes.AllDefaultAnnotation");
    }

    private void assertAnnotations(AnnotationDesc[] annotations, String... qualifiedNames) {
        assertEquals(qualifiedNames.length, annotations.length);
        for (int i = 0; i < qualifiedNames.length; i++) {
            final String expectedQualifiedName = qualifiedNames[i];
            final String actualQualifiedName = annotations[i].annotationType().qualifiedName();
            assertEquals(expectedQualifiedName, actualQualifiedName);
        }
    }

    @Test
    public void elementValues() {
        var annotations = annotatedClass.annotations();
        assertEquals(2, annotations.length);

        // 1. @UniversalAnnotation (with no expectedParameters).
        var values = annotations[0].elementValues();
        assertEquals(0, values.length);

        // 2. @AllDefaultAnnotation(str = "abc", integer = 123).
        values = annotations[1].elementValues();
        assertEquals(2, values.length);
        assertElementValues(values[0], "str", "abc");
        assertElementValues(values[1], "integer", 123);
    }

    @SuppressWarnings("unchecked")
    private <T> void assertElementValues(ElementValuePair pair, String expectedDescName,
            T expectedValue) {
        var element = pair.element();
        if (element instanceof AnnotationMethodDocImpl amd) {
            assertEquals(expectedDescName, amd.name());
        } else {
            fail("Expected returned value type of ElementValues.element() to be "
                    + "AnnotationMethodDocImpl but got " + element.getClass().getSimpleName());
        }

        // suppressed unchecked cast
        T value = (T) pair.value().value();
        assertEquals(expectedValue, value);
    }
}