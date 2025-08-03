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

package vogar.target.testng;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

public final class TestNg {
    private TestNg() {}

    public static boolean isTestNgTest(Class<?> klass) {
        // Classes annotated with @Test
        for (Annotation a : klass.getAnnotations()) {
            Class<?> annotationClass = a.annotationType();

            if (org.testng.annotations.Test.class.isAssignableFrom(annotationClass)) {
                return true;
            }
        }

        // Methods annotated with @Test
        for (Method m : klass.getMethods()) {
            for (Annotation a : m.getAnnotations()) {
                Class<?> annotationClass = a.annotationType();

                if (org.testng.annotations.Test.class.isAssignableFrom(annotationClass)) {
                    return true;
                }
            }
        }

        return false;
    }
}
