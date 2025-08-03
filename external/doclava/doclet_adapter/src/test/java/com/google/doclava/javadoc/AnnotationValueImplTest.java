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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.doclava.javadoc.BaseTest.ANNOTATION_METHOD.WITH_DEFAULT;
import com.sun.javadoc.Type;
import javax.lang.model.element.AnnotationValue;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

public class AnnotationValueImplTest extends BaseTest {

    private static final AnnotationValue withDefault = ANNOTATION_METHOD.annotationMethodWithDefault.getDefaultValue();

    @Before
    public void setUp() {
        super.setUp();

    }

    @Test
    public void create() {
        assertNull(AnnotationValueImpl.create(null, context));

        var av = AnnotationValueImpl.create(withDefault, context);
        assertNotNull(av);
    }

    @Test
    public void value_ofNull() {
        assertNull(AnnotationValueImpl.create(null, context));
    }

    @Test
    public void value_ofBool() {
        AnnotationValueImpl b = AnnotationValueImpl.create(
                WITH_DEFAULT.returningBool.getDefaultValue(), context);
        assertNotNull(b);
        Object value = b.value();
        assertNotNull(value);

        assertTrue(value instanceof Boolean);
        assertEquals(true, value);
    }

    @Test
    public void value_ofByte() {
        AnnotationValueImpl b = AnnotationValueImpl.create(
                WITH_DEFAULT.returningByte.getDefaultValue(), context);
        assertNotNull(b);
        Object value = b.value();

        assertTrue(value instanceof Byte);
        assertEquals((byte) 1, value);
    }

    @Test
    public void value_ofChar() {
        AnnotationValueImpl b = AnnotationValueImpl.create(
                WITH_DEFAULT.returningChar.getDefaultValue(), context);
        assertNotNull(b);
        Object value = b.value();

        assertTrue(value instanceof Character);
        assertEquals('a', value);
    }

    @Test
    public void value_ofDouble() {
        AnnotationValueImpl b = AnnotationValueImpl.create(
                WITH_DEFAULT.returningDouble.getDefaultValue(), context);
        assertNotNull(b);
        Object value = b.value();

        assertTrue(value instanceof Double);
        assertEquals(3.1d, (double) value, 0.0000001d);
    }

    @Test
    public void value_ofFloat() {
        AnnotationValueImpl b = AnnotationValueImpl.create(
                WITH_DEFAULT.returningFloat.getDefaultValue(), context);
        assertNotNull(b);
        Object value = b.value();

        assertTrue(value instanceof Float);
        assertEquals(4.1f, (float) value, 0.0000001f);
    }

    @Test
    public void value_ofInteger() {
        AnnotationValueImpl b = AnnotationValueImpl.create(
                ANNOTATION_METHOD.WITH_DEFAULT.returningInteger.getDefaultValue(), context);
        assertNotNull(b);
        Object value = b.value();

        assertTrue(value instanceof Integer);
        assertEquals(5, value);
    }

    @Test
    public void value_ofLong() {
        AnnotationValueImpl b = AnnotationValueImpl.create(
                WITH_DEFAULT.returningLond.getDefaultValue(), context);
        assertNotNull(b);
        Object value = b.value();

        assertTrue(value instanceof Long);
        assertEquals(6L, value);
    }

    @Test
    public void value_ofShort() {
        AnnotationValueImpl b = AnnotationValueImpl.create(
                WITH_DEFAULT.returningShort.getDefaultValue(), context);
        assertNotNull(b);
        Object value = b.value();

        assertTrue(value instanceof Short);
        assertEquals((short) 7, value);
    }

    @Test
    public void value_ofString() {
        AnnotationValueImpl b = AnnotationValueImpl.create(
                WITH_DEFAULT.returningString.getDefaultValue(), context);
        assertNotNull(b);
        Object value = b.value();

        assertTrue(value instanceof String);
        assertEquals("qwe", value);
    }

    @Test
    public void value_ofClass() {
        AnnotationValueImpl b = AnnotationValueImpl.create(
                WITH_DEFAULT.returningClass.getDefaultValue(), context);
        assertNotNull(b);
        Object value = b.value();

        assertTrue(value instanceof Type);
        assertTrue(value instanceof ClassDocImpl);
        assertEquals("com.example.classes.PublicClass", ((ClassDocImpl) value).qualifiedName());
    }

    @Ignore("Not yet implemented")
    @Test
    public void value_ofEnum() {
        AnnotationValueImpl b = AnnotationValueImpl.create(
                WITH_DEFAULT.returningEnum.getDefaultValue(), context);
        assertNotNull(b);
        Object value = b.value();

        assertTrue(value instanceof FieldDocImpl);
    }

    @Ignore("Not yet implemented")
    @Test
    public void value_ofAnnotation() {
        AnnotationValueImpl b = AnnotationValueImpl.create(
                WITH_DEFAULT.returningAnnotation.getDefaultValue(), context);
        assertNotNull(b);
        Object value = b.value();
    }

    @Test
    public void value_ofArrayOfString() {
        AnnotationValueImpl b = AnnotationValueImpl.create(
                WITH_DEFAULT.returningArrayOfString.getDefaultValue(), context);
        assertNotNull(b);
        Object value = b.value();

        assertTrue(value instanceof AnnotationValueImpl[]);
        var arr = (AnnotationValueImpl[]) value;
        assertEquals(3, arr.length);

        var expected = new String[]{"abc", "def", "ghi"};
        for (int i = 0; i < arr.length; i++) {
            var unwrapped = arr[i].value();
            assertTrue(unwrapped instanceof String);
            assertEquals(expected[i], unwrapped);
        }
    }
}