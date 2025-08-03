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
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

public class AnnotationTypeDocImplTest extends BaseTest {

    private AnnotationTypeDocImpl empty;

    @Override
    @Before
    public void setUp() {
        super.setUp();
        empty = AnnotationTypeDocImpl.create(CLASS.publicAnnotation, context);
    }

    @Test(expected = IllegalArgumentException.class)
    public void create() {
        AnnotationTypeDocImpl.create(CLASS.publicClass, context);
    }

    @Ignore("Not yet implemented")
    @Test
    public void elements() {
    }

    @Test
    public void isAnnotationType() {
        assertTrue(empty.isAnnotationType());
    }

    @Test
    public void isInterface() {
        assertFalse(empty.isInterface());
    }
}