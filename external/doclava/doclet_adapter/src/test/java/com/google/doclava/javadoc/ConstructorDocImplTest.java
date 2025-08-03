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

import static org.junit.Assert.*;

import org.junit.Test;

public class ConstructorDocImplTest extends BaseTest {

    private ConstructorDocImpl empty;
    private ConstructorDocImpl arg1_int;
    private ConstructorDocImpl arg1_String;
    private ConstructorDocImpl arg2_int_String;

    @Override
    public void setUp() {
        super.setUp();
        empty = ConstructorDocImpl.create(CONSTRUCTOR.empty, context);
        arg1_int = ConstructorDocImpl.create(CONSTRUCTOR.arg1_int, context);
        arg1_String = ConstructorDocImpl.create(CONSTRUCTOR.arg1_String, context);
        arg2_int_String = ConstructorDocImpl.create(CONSTRUCTOR.arg2_int_String, context);
    }

    @Test
    public void isConstructor() {
        assertTrue(empty.isConstructor());
        assertTrue(arg1_int.isConstructor());
        assertTrue(arg1_String.isConstructor());
        assertTrue(arg2_int_String.isConstructor());
    }

    @Test
    public void name() {
        assertEquals("Constructors", empty.name());
        assertEquals("Constructors", arg1_int.name());
        assertEquals("Constructors", arg1_String.name());
        assertEquals("Constructors", arg2_int_String.name());
    }

    @Test
    public void qualifiedName() {
        assertEquals("com.example.constructors.Constructors", empty.qualifiedName());
        assertEquals("com.example.constructors.Constructors", arg1_int.qualifiedName());
        assertEquals("com.example.constructors.Constructors", arg1_String.qualifiedName());
        assertEquals("com.example.constructors.Constructors", arg2_int_String.qualifiedName());
    }
}