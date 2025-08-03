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

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

public class FieldDocImplTest extends BaseTest {

    private FieldDocImpl public_int;
    private FieldDocImpl public_transient_volatile_Object;
    private FieldDocImpl public_final_int;
    private FieldDocImpl public_final_String;

    @Before
    public void setUp() {
        super.setUp();
        public_int = FieldDocImpl.create(FIELD.public_int, context);
        public_transient_volatile_Object =
                FieldDocImpl.create(FIELD.public_transient_volatile_Object, context);
        public_final_int = FieldDocImpl.create(FIELD.public_final_int, context);
        public_final_String = FieldDocImpl.create(FIELD.public_final_String, context);
    }

    @Test
    public void isSynthetic() {
        assertFalse(public_int.isSynthetic());
        assertFalse(public_transient_volatile_Object.isSynthetic());
        assertFalse(public_final_int.isSynthetic());
        assertFalse(public_final_String.isSynthetic());
        // DocletEnvironment does not contain/handle any synthetic fields.
    }

    @Test
    public void name() {
        assertEquals("public_int", public_int.name());
        assertEquals("public_transient_volatile_Object", public_transient_volatile_Object.name());
        assertEquals("public_final_int", public_final_int.name());
        assertEquals("public_final_String", public_final_String.name());
    }

    @Test
    public void qualifiedName() {
        assertEquals("com.example.fields.Fields.public_int", public_int.qualifiedName());
        assertEquals("com.example.fields.Fields.public_transient_volatile_Object",
                public_transient_volatile_Object.qualifiedName());
    }

    @Ignore("Not yet implemented")
    @Test
    public void isIncluded() {
    }

    @Test
    public void type() {
        assertEquals(PrimitiveTypeImpl.INT, public_int.type());
        assertEquals(ClassDocImpl.create(INSTANCE.javaLangObject, context),
                public_transient_volatile_Object.type());
    }

    @Test
    public void isTransient() {
        assertFalse(public_int.isTransient());
        assertTrue(public_transient_volatile_Object.isTransient());
    }

    @Test
    public void isVolatile() {
        assertFalse(public_int.isVolatile());
        assertTrue(public_transient_volatile_Object.isVolatile());
    }

    @Ignore("Not yet implemented")
    @Test
    public void serialFieldTags() {
    }

    @Test
    public void constantValue() {
        assertNull(public_int.constantValue());
        assertNull(public_transient_volatile_Object.constantValue());

        assertEquals(public_final_int.constantValue(), Integer.valueOf(5));
        assertEquals(public_final_String.constantValue(), "abc");
    }

    @Ignore("Not yet implemented")
    @Test
    public void constantValueExpression() {
    }
}