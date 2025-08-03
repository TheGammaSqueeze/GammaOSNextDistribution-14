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

import static com.google.doclava.javadoc.PrimitiveTypeImpl.BOOLEAN;
import static com.google.doclava.javadoc.PrimitiveTypeImpl.BYTE;
import static com.google.doclava.javadoc.PrimitiveTypeImpl.CHAR;
import static com.google.doclava.javadoc.PrimitiveTypeImpl.DOUBLE;
import static com.google.doclava.javadoc.PrimitiveTypeImpl.FLOAT;
import static com.google.doclava.javadoc.PrimitiveTypeImpl.INT;
import static com.google.doclava.javadoc.PrimitiveTypeImpl.LONG;
import static com.google.doclava.javadoc.PrimitiveTypeImpl.SHORT;
import static com.google.doclava.javadoc.PrimitiveTypeImpl.VOID;
import static com.google.doclava.javadoc.PrimitiveTypeImpl.values;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

public class PrimitiveTypeImplTest extends BaseTest {

    private List<PrimitiveTypeImpl> allPrimitiveTypes;

    @Before
    public void setUp() {
        super.setUp();
        allPrimitiveTypes = Arrays.stream(values()).toList();
    }

    @Test
    public void enumValues() {
        assertEquals(9, PrimitiveTypeImpl.values().length);
    }

    @Test
    public void typeName() {
        assertEquals("boolean", BOOLEAN.typeName());
        assertEquals("byte", BYTE.typeName());
        assertEquals("char", CHAR.typeName());
        assertEquals("double", DOUBLE.typeName());
        assertEquals("float", FLOAT.typeName());
        assertEquals("int", INT.typeName());
        assertEquals("long", LONG.typeName());
        assertEquals("short", SHORT.typeName());
        assertEquals("void", VOID.typeName());
    }

    @Test
    public void qualifiedTypeName() {
        assertEquals("boolean", BOOLEAN.qualifiedTypeName());
        assertEquals("byte", BYTE.qualifiedTypeName());
        assertEquals("char", CHAR.qualifiedTypeName());
        assertEquals("double", DOUBLE.qualifiedTypeName());
        assertEquals("float", FLOAT.qualifiedTypeName());
        assertEquals("int", INT.qualifiedTypeName());
        assertEquals("long", LONG.qualifiedTypeName());
        assertEquals("short", SHORT.qualifiedTypeName());
        assertEquals("void", VOID.qualifiedTypeName());
    }

    @Test
    public void simpleTypeName() {
        assertEquals("boolean", BOOLEAN.simpleTypeName());
        assertEquals("byte", BYTE.simpleTypeName());
        assertEquals("char", CHAR.simpleTypeName());
        assertEquals("double", DOUBLE.simpleTypeName());
        assertEquals("float", FLOAT.simpleTypeName());
        assertEquals("int", INT.simpleTypeName());
        assertEquals("long", LONG.simpleTypeName());
        assertEquals("short", SHORT.simpleTypeName());
        assertEquals("void", VOID.simpleTypeName());
    }

    @Test
    public void dimension() {
        allPrimitiveTypes.forEach(pt -> assertEquals("", pt.dimension()));
    }

    @Test
    public void isPrimitive() {
        allPrimitiveTypes.forEach(pt -> assertTrue(pt.isPrimitive()));
    }

    @Test
    public void asClassDoc() {
        allPrimitiveTypes.forEach(pt -> assertNull(pt.asClassDoc()));
    }

    @Test
    public void asParameterizedType() {
        allPrimitiveTypes.forEach(pt -> assertNull(pt.asParameterizedType()));
    }

    @Test
    public void asTypeVariable() {
        allPrimitiveTypes.forEach(pt -> assertNull(pt.asTypeVariable()));
    }

    @Test
    public void asWildcardType() {
        allPrimitiveTypes.forEach(pt -> assertNull(pt.asWildcardType()));
    }

    @Test
    public void asAnnotatedType() {
        allPrimitiveTypes.forEach(pt -> assertNull(pt.asAnnotatedType()));
    }

    @Test
    public void asAnnotationTypeDoc() {
        allPrimitiveTypes.forEach(pt -> assertNull(pt.asAnnotationTypeDoc()));
    }

    @Test
    public void getElementType() {
        allPrimitiveTypes.forEach(pt -> assertNull(pt.getElementType()));
    }
}