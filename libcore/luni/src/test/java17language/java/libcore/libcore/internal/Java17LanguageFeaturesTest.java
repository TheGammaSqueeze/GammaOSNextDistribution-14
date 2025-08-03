/*
 * Copyright (C) 2021 The Android Open Source Project
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

package libcore.libcore.internal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import libcore.internal.Java17LanguageFeatures;

import org.junit.Test;
import org.junit.Ignore;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class Java17LanguageFeaturesTest {

    @Test
    public void testMultilineString() {
        assertEquals("This is a\nmultiline\nstring.",
                Java17LanguageFeatures.getTextBlock());
    }

    @Test
    public void testPatternMatchingInstanceof() {
        Java17LanguageFeatures.Shape s = new Java17LanguageFeatures.Triangle(6, 10);
        assertEquals(30, Java17LanguageFeatures.calculateApproximateArea(s));
        s = new Java17LanguageFeatures.Rectangle(4, 5);
        assertEquals(20, Java17LanguageFeatures.calculateApproximateArea(s));
        s = new Java17LanguageFeatures.Circle(5);
        assertEquals(75, Java17LanguageFeatures.calculateApproximateArea(s));
    }

    @Test
    public void testRecord() {
        Java17LanguageFeatures.Point p1 = Java17LanguageFeatures.buildPoint(1, 2);
        Java17LanguageFeatures.Point p2 = Java17LanguageFeatures.buildPoint(1, 2);
        Java17LanguageFeatures.Point p3 = Java17LanguageFeatures.buildPoint(1, 3);

        assertEquals(1, p1.x());
        assertEquals(2, p1.y());
        assertEquals(1, p2.x());
        assertEquals(2, p2.y());
        assertEquals(1, p3.x());
        assertEquals(3, p3.y());

        assertEquals("Point[x=1, y=2]", p1.toString());
        assertEquals("Point[x=1, y=2]", p2.toString());
        assertEquals("Point[x=1, y=3]", p3.toString());

        assertTrue(p1.equals(p2));
        assertEquals(p1.hashCode(), p2.hashCode());

        assertFalse(p1.equals(p3));
        assertNotEquals(p1.hashCode(), p3.hashCode());

        assertFalse(p2.equals(p3));
        assertNotEquals(p2.hashCode(), p3.hashCode());
    }

    @Test
    public void testSealedClass() {
        Java17LanguageFeatures.BaseSealedClass obj = new Java17LanguageFeatures.BaseSealedClass();
        assertEquals(0, Java17LanguageFeatures.getSealedClassId(obj));
        obj = new Java17LanguageFeatures.FinalDerivedClass();
        assertEquals(1, Java17LanguageFeatures.getSealedClassId(obj));
        obj = new Java17LanguageFeatures.NonSealedDerivedClass();
        assertEquals(2, Java17LanguageFeatures.getSealedClassId(obj));
        obj = new DerivedClass();
        assertEquals(3, Java17LanguageFeatures.getSealedClassId(obj));
    }

    private static class DerivedClass extends Java17LanguageFeatures.NonSealedDerivedClass {
        @Override
        public int getId() {
            return 3;
        }
    }
}
