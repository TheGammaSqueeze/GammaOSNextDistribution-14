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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.doclava.javadoc.BaseTest.METHOD.OF_CLASS;
import com.google.doclava.javadoc.BaseTest.METHOD.OF_INTERFACE;
import com.google.doclava.javadoc.BaseTest.METHOD.OVERRIDES;
import com.sun.javadoc.MethodDoc;
import org.junit.Ignore;
import org.junit.Test;

public class MethodDocImplTest extends BaseTest {

    private static class CLASS_METHOD {

        public static MethodDocImpl public_void_arg0;
        public static MethodDocImpl private_int_arg0;
        public static MethodDocImpl packagePrivate_String_arg2_int_String;
        public static MethodDocImpl public_abstract_void_arg0;
        public static MethodDocImpl override_public_String_toString0;
    }

    private static class INTERFACE_METHOD {

        public static MethodDocImpl public_void_arg0;
        public static MethodDocImpl public_default_String_arg0;
    }

    private static class OVERRIDE {

        public static MethodDocImpl A;
        public static MethodDocImpl B;
        public static MethodDocImpl C;
        public static MethodDocImpl D;
    }

    private ClassDocImpl javaLangString;

    @Override
    public void setUp() {
        super.setUp();
        CLASS_METHOD.public_void_arg0 = MethodDocImpl.create(OF_CLASS.public_void_arg0, context);
        CLASS_METHOD.private_int_arg0 = MethodDocImpl.create(OF_CLASS.private_int_arg0, context);
        CLASS_METHOD.packagePrivate_String_arg2_int_String = MethodDocImpl.create(
                OF_CLASS.packagePrivate_String_arg2_int_String, context);
        CLASS_METHOD.public_abstract_void_arg0 = MethodDocImpl.create(
                OF_CLASS.public_abstract_void_arg0, context);
        CLASS_METHOD.override_public_String_toString0 = MethodDocImpl.create(
                OF_CLASS.override_public_String_toString0, context);

        INTERFACE_METHOD.public_void_arg0 = MethodDocImpl.create(OF_INTERFACE.public_void_arg0,
                context);
        INTERFACE_METHOD.public_default_String_arg0 =
                MethodDocImpl.create(OF_INTERFACE.public_default_String_arg0, context);

        OVERRIDE.A = MethodDocImpl.create(OVERRIDES.A_name, context);
        OVERRIDE.B = MethodDocImpl.create(OVERRIDES.B_name, context);
        OVERRIDE.C = MethodDocImpl.create(OVERRIDES.C_name, context);
        OVERRIDE.D = MethodDocImpl.create(OVERRIDES.D_name, context);

        javaLangString = ClassDocImpl.create(INSTANCE.javaLangString, context);
    }

    @Test
    public void name() {
        assertEquals("public_void_arg0", CLASS_METHOD.public_void_arg0.name());
        assertEquals("public_default_String_arg0",
                INTERFACE_METHOD.public_default_String_arg0.name());
    }

    @Test
    public void qualifiedName() {
        assertEquals("com.example.methods.OfClass.public_void_arg0",
                CLASS_METHOD.public_void_arg0.qualifiedName());
        assertEquals("com.example.methods.OfInterface.public_default_String_arg0",
                INTERFACE_METHOD.public_default_String_arg0.qualifiedName());
    }

    @Test
    public void isMethod() {
        assertTrue(CLASS_METHOD.public_void_arg0.isMethod());
        assertTrue(INTERFACE_METHOD.public_void_arg0.isMethod());
    }

    @Test
    public void isAbstract() {
        assertFalse(CLASS_METHOD.public_void_arg0.isAbstract());
        // default interface method is not abstract
        assertFalse(INTERFACE_METHOD.public_default_String_arg0.isAbstract());

        assertTrue(CLASS_METHOD.public_abstract_void_arg0.isAbstract());
        // interface methods are abstract
        assertTrue(INTERFACE_METHOD.public_void_arg0.isAbstract());
    }

    @Test
    public void isDefault() {
        assertFalse(CLASS_METHOD.public_void_arg0.isDefault());
        assertFalse(CLASS_METHOD.public_abstract_void_arg0.isDefault());

        assertTrue(INTERFACE_METHOD.public_default_String_arg0.isDefault());
    }

    @Test
    public void returnType() {
        assertEquals(PrimitiveTypeImpl.VOID, CLASS_METHOD.public_void_arg0.returnType());
        assertEquals(PrimitiveTypeImpl.INT, CLASS_METHOD.private_int_arg0.returnType());
        assertEquals(javaLangString, CLASS_METHOD.override_public_String_toString0.returnType());

        assertEquals(javaLangString, INTERFACE_METHOD.public_default_String_arg0.returnType());
        assertEquals(PrimitiveTypeImpl.VOID, INTERFACE_METHOD.public_void_arg0.returnType());
    }

    @Ignore("Not yet implemented")
    @Test
    public void overriddenClass() {
    }

    @Ignore("Not yet implemented")
    @Test
    public void overriddenType() {
    }

    @Test
    public void overriddenMethod() {
        assertNull(CLASS_METHOD.public_void_arg0.overriddenMethod());

        MethodDoc overrides_String_toString =
                CLASS_METHOD.override_public_String_toString0.overriddenMethod();
        assertEquals("java.lang.Object.toString", overrides_String_toString.qualifiedName());

        // Fixtures from com.example.methods.override
        // A <- B <- C
        // ^
        // |
        // D

        var a = OVERRIDE.A;
        var b = OVERRIDE.B;
        var c = OVERRIDE.C;
        var d = OVERRIDE.D;

        // Positive cases (A <- B; B <- C; A <- D)
        assertEquals(a, b.overriddenMethod());
        assertEquals(b, c.overriddenMethod());
        assertEquals(a, d.overriddenMethod());

        // Negative cases
        // 1. Top-level class is not extending (and overriding) anything.
        assertNull(a.overriddenMethod());

        // 2. Method never overrides itself.
        assertNotEquals(a, a.overriddenMethod());
        assertNotEquals(b, b.overriddenMethod());
        assertNotEquals(c, c.overriddenMethod());
        assertNotEquals(d, d.overriddenMethod());

        // 3. Indirect overrides.
        // *A* <- B <- *C* – A overrides C but *not* directly, there's B in between.
        assertNotEquals(a, c.overriddenMethod());

        // 4. Non-related methods (different classes).
        assertNotEquals(a, CLASS_METHOD.override_public_String_toString0.overriddenMethod());

        // 5. "Parent" does not override "child", i.e. method upper in hierarchy does not
        //    override anything below.
        assertNotEquals(b, a.overriddenMethod());
        assertNotEquals(c, a.overriddenMethod());
        assertNotEquals(d, a.overriddenMethod());

        assertNotEquals(c, b.overriddenMethod());

        // 6. Exhaust the rest of negative test cases
        assertNotEquals(b, d.overriddenMethod());
        assertNotEquals(c, d.overriddenMethod());
        assertNotEquals(d, b.overriddenMethod());
        assertNotEquals(d, c.overriddenMethod());
    }

    @Ignore("Not yet implemented")
    @Test
    public void overrides() {
    }
}