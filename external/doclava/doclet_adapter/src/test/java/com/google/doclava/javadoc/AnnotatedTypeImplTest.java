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

import com.sun.javadoc.Type;
import javax.lang.model.type.DeclaredType;
import org.junit.Ignore;
import org.junit.Test;

public class AnnotatedTypeImplTest extends BaseTest {

    private AnnotatedTypeImpl annotatedClass;

    @Override
    public void setUp() {
        super.setUp();

        annotatedClass = AnnotatedTypeImpl.create((DeclaredType) CLASS.annotatedClass.asType(),
                context);
    }

    @Ignore("Not used")
    @Test
    public void annotations() {
    }

    @Test
    public void underlyingType() {
        Type underlyingType = annotatedClass.underlyingType();
        assertEquals("AnnotatedClass", underlyingType.typeName());
    }
}