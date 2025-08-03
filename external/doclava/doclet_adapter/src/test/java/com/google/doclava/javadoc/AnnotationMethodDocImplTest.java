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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

public class AnnotationMethodDocImplTest extends BaseTest {

    private AnnotationMethodDocImpl withDefault;
    private AnnotationMethodDocImpl withoutDefault;

    @Override
    @Before
    public void setUp() {
        super.setUp();
        withoutDefault = AnnotationMethodDocImpl.create(ANNOTATION_METHOD.annotationMethod, context);
        withDefault =
                AnnotationMethodDocImpl.create(ANNOTATION_METHOD.annotationMethodWithDefault, context);
    }

    @Test
    public void defaultValue() {
        assertNull(withoutDefault.defaultValue());

        assertNotNull(withDefault.defaultValue());
    }

    @Test
    public void isAnnotationTypeElement() {
        assertTrue(withoutDefault.isAnnotationTypeElement());
        assertTrue(withDefault.isAnnotationTypeElement());
    }

    @Test
    public void isMethod() {
        assertFalse(withoutDefault.isMethod());
        assertFalse(withDefault.isMethod());
    }

    @Test
    public void isAbstract() {
        assertFalse(withoutDefault.isAbstract());
        assertFalse(withDefault.isAbstract());
    }
}