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
import static org.junit.Assert.assertTrue;

import com.sun.javadoc.ClassDoc;
import com.sun.javadoc.Doc;
import java.util.Arrays;
import java.util.Comparator;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

public class PackageDocImplTest extends BaseTest {

    private PackageDocImpl comExamplePackages;

    @Before
    public void setUp() {
        super.setUp();
        comExamplePackages = PackageDocImpl.create(PACKAGE.comExamplePackages, context);
    }

    @Ignore("Not used")
    @Test
    public void allClasses() {
    }

    @Ignore("Not used")
    @Test
    public void allClasses_filter() {
    }

    @Test
    public void ordinaryClasses() {
        ClassDoc[] ordinaryClasses = comExamplePackages.ordinaryClasses();

        assertEquals(3, ordinaryClasses.length);
        assertArrayEquals(new String[]{
                        "ClassWithNested", "ClassWithNested.Nested", "ClassWithNested.Nested.Nested2"},
                Arrays.stream(ordinaryClasses)
                        .sorted(Comparator.comparing(Doc::name))
                        .map(Doc::name)
                        .toArray(String[]::new)
        );
    }

    @Test
    public void exceptions() {
        ClassDoc[] exceptions = comExamplePackages.exceptions();

        assertEquals(1, exceptions.length);
        assertTrue(exceptions[0].isException());
        assertEquals("Exception", exceptions[0].name());
    }

    @Test
    public void errors() {
        ClassDoc[] errors = comExamplePackages.errors();

        assertEquals(1, errors.length);
        assertTrue(errors[0].isError());
        assertEquals("Error", errors[0].name());
    }

    @Test
    public void enums() {
        ClassDoc[] enums = comExamplePackages.enums();

        assertEquals(1, enums.length);
        assertTrue(enums[0].isEnum());
        assertEquals("Enum", enums[0].name());
    }

    @Test
    public void interfaces() {
        ClassDoc[] interfaces = comExamplePackages.interfaces();

        assertEquals(1, interfaces.length);
        assertTrue(interfaces[0].isInterface());
        assertEquals("Interface", interfaces[0].name());
    }

    @Test
    public void annotationTypes() {
        ClassDoc[] annotationTypes = comExamplePackages.annotationTypes();

        assertEquals(1, annotationTypes.length);
        assertTrue(annotationTypes[0].isAnnotationType());
        assertEquals("Annotation", annotationTypes[0].name());
    }

    @Ignore("Not yet implemented")
    @Test
    public void annotations() {
    }

    @Ignore("Not yet implemented")
    @Test
    public void findClass() {
    }

    @Ignore("Not yet implemented")
    @Test
    public void isIncluded() {
    }

    @Test
    public void name() {
        assertEquals("com.example.packages", comExamplePackages.name());
    }

    @Test
    public void qualifiedName() {
        assertEquals("com.example.packages", comExamplePackages.qualifiedName());
    }
}