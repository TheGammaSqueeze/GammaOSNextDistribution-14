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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.sun.javadoc.AnnotationTypeDoc;
import com.sun.javadoc.ClassDoc;
import com.sun.javadoc.ParameterizedType;
import com.sun.javadoc.TypeVariable;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

public class ArrayTypeImplTest extends BaseTest {

    // All these are declared in com.example.fields.Arrays class.
    private ArrayTypeImpl int_1;           // primitive type     : int[]
    private ArrayTypeImpl string_2;        // class              : String[][]
    private ArrayTypeImpl typeVar_3;       // type variable      : T[][][]
    private ArrayTypeImpl listOfStrings_4; // parameterized type : List<String>[][][][]
    private ArrayTypeImpl annotation_5;    // annotation         : @Override[][][][][]

    @Before
    public void setUp() {
        super.setUp();

        int_1 = ArrayTypeImpl.create(ARRAY.int_1, context);
        string_2 = ArrayTypeImpl.create(ARRAY.string_2, context);
        typeVar_3 = ArrayTypeImpl.create(ARRAY.T_3, context);
        listOfStrings_4 = ArrayTypeImpl.create(ARRAY.ListOfStrings_4, context);
        annotation_5 = ArrayTypeImpl.create(ARRAY.override_5, context);
    }

    @Ignore("Not yet implemented")
    @Test
    public void getElementType() {
    }

    @Ignore("Not yet implemented")
    @Test
    public void typeName() {
    }

    @Test
    public void qualifiedTypeName() {
        assertEquals("int", int_1.qualifiedTypeName());
        assertEquals("java.lang.String", string_2.qualifiedTypeName());
        assertEquals("T", typeVar_3.qualifiedTypeName());
        assertEquals("java.util.List<java.lang.String>", listOfStrings_4.qualifiedTypeName());
        assertEquals("java.lang.Override", annotation_5.qualifiedTypeName());
    }

    @Test
    public void simpleTypeName() {
        assertEquals("int", int_1.simpleTypeName());
        assertEquals("String", string_2.simpleTypeName());
        assertEquals("T", typeVar_3.simpleTypeName());
        assertEquals("List", listOfStrings_4.simpleTypeName());
        assertEquals("Override", annotation_5.simpleTypeName());
    }

    @Test
    public void dimension() {
        assertEquals("[]", int_1.dimension());
        assertEquals("[][]", string_2.dimension());
        assertEquals("[][][]", typeVar_3.dimension());
        assertEquals("[][][][]", listOfStrings_4.dimension());
        assertEquals("[][][][][]", annotation_5.dimension());
    }

    @Test
    public void asClassDoc() {
        assertNull(int_1.asClassDoc());
        assertNull(typeVar_3.asClassDoc());
        assertNull(listOfStrings_4.asClassDoc());

        ClassDoc string = string_2.asClassDoc();
        assertTrue(string.isClass());
        assertEquals("java.lang.String", string.qualifiedTypeName());

        ClassDoc annotation = annotation_5.asClassDoc();
        assertTrue(annotation.isAnnotationType());
        assertEquals("java.lang.Override", annotation.qualifiedTypeName());
    }

    @Test
    public void asTypeVariable() {
        assertNull(int_1.asTypeVariable());
        assertNull(string_2.asTypeVariable());
        assertNull(listOfStrings_4.asTypeVariable());
        assertNull(annotation_5.asTypeVariable());

        TypeVariable tv = typeVar_3.asTypeVariable();
        assertEquals("T", tv.simpleTypeName());
    }

    @Test
    public void asParameterizedType() {
        assertNull(int_1.asParameterizedType());
        assertNull(string_2.asParameterizedType());
        assertNull(typeVar_3.asParameterizedType());
        assertNull(annotation_5.asParameterizedType());

        ParameterizedType pt = listOfStrings_4.asParameterizedType();
        assertEquals("java.util.List<java.lang.String>", pt.qualifiedTypeName());
    }

    @Test
    public void asAnnotationTypeDoc() {
        assertNull(int_1.asAnnotationTypeDoc());
        assertNull(string_2.asAnnotationTypeDoc());
        assertNull(typeVar_3.asAnnotationTypeDoc());
        assertNull(listOfStrings_4.asAnnotationTypeDoc());

        AnnotationTypeDoc atd = annotation_5.asAnnotationTypeDoc();
        assertTrue(atd.isAnnotationType());
        assertEquals("java.lang.Override", atd.qualifiedTypeName());
    }

    @Test
    public void isPrimitive() {
        assertFalse(string_2.isPrimitive());
        assertFalse(typeVar_3.isPrimitive());
        assertFalse(listOfStrings_4.isPrimitive());
        assertFalse(annotation_5.isPrimitive());

        assertTrue(int_1.isPrimitive());
    }
}