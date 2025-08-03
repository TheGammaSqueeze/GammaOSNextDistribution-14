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
package com.android.adservices.shared.testing.common;

import static com.android.adservices.shared.testing.common.TestHelper.getAnnotation;
import static com.android.adservices.shared.testing.common.TestHelper.getTestName;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;

import com.google.auto.value.AutoAnnotation;
import com.google.common.truth.Expect;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.Description;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

public final class TestHelperTest {

    public @Rule final Expect expect = Expect.create();

    @Test
    public void testGetTestName_null() {
        assertThrows(NullPointerException.class, () -> getTestName(null));
    }

    @Test
    public void testGetTestName_testMethod() {
        Description test =
                Description.createTestDescription(AClassHasNoNothingAtAll.class, "butItHasATest");

        expect.withMessage("getTestName(%s)", test)
                .that(getTestName(test))
                .isEqualTo("AClassHasNoNothingAtAll#butItHasATest()");
    }

    @Test
    public void testGetTestName_testClass() {
        Description test = Description.createSuiteDescription(AClassHasNoNothingAtAll.class);

        expect.withMessage("getTestName(%s)", test)
                .that(getTestName(test))
                .isEqualTo("AClassHasNoNothingAtAll");
    }

    @Test
    public void testGetAnnotation_null() {
        Description test = Description.createSuiteDescription(AClassHasNoNothingAtAll.class);

        assertThrows(
                NullPointerException.class, () -> getAnnotation(test, /* annotationClass= */ null));
        assertThrows(
                NullPointerException.class,
                () -> getAnnotation(/* test= */ null, DaRealAnnotation.class));
    }

    @Test
    public void testGetAnnotation_notSetAnywhere() {
        Description test = Description.createSuiteDescription(AClassHasNoNothingAtAll.class);

        expect.withMessage("getAnnotation(%s)", test)
                .that(getAnnotation(test, DaRealAnnotation.class))
                .isNull();
    }

    @Test
    public void testGetAnnotation_fromMethod() {
        Description test =
                Description.createSuiteDescription(
                        AClassHasAnAnnotationAndAParent.class,
                        newDaFakeAnnotation("I annotate, therefore I am!"));

        DaRealAnnotation annotation = getAnnotation(test, DaRealAnnotation.class);

        assertWithMessage("getAnnotation(%s)", test).that(annotation).isNotNull();
        expect.withMessage("getAnnotation(%s).value()", test)
                .that(annotation.value())
                .isEqualTo("I annotate, therefore I am!");
    }

    @Test
    public void testGetAnnotation_fromClass() {
        Description test =
                Description.createSuiteDescription(AClassHasAnAnnotationAndAParent.class);

        DaRealAnnotation annotation = getAnnotation(test, DaRealAnnotation.class);

        assertWithMessage("getAnnotation(%s)", test).that(annotation).isNotNull();
        expect.withMessage("getAnnotation(%s).value()", test)
                .that(annotation.value())
                .isEqualTo("A class has an annotation and a parent!");
    }

    @Test
    public void testGetAnnotation_fromParentClass() {
        Description test =
                Description.createSuiteDescription(AClassHasNoAnnotationButItsParentDoes.class);

        DaRealAnnotation annotation = getAnnotation(test, DaRealAnnotation.class);

        assertWithMessage("getAnnotation(%s)", test).that(annotation).isNotNull();
        expect.withMessage("getAnnotation(%s).value()", test)
                .that(annotation.value())
                .isEqualTo("A class has an annotation!");
    }

    @Test
    public void testGetAnnotation_fromGrandParentClass() {
        Description test =
                Description.createSuiteDescription(
                        AClassHasNoAnnotationButItsGrandParentDoes.class);

        DaRealAnnotation annotation = getAnnotation(test, DaRealAnnotation.class);

        assertWithMessage("getAnnotation(%s)", test).that(annotation).isNotNull();
        expect.withMessage("getAnnotation(%s).value()", test)
                .that(annotation.value())
                .isEqualTo("A class has an annotation!");
    }

    private static class AClassHasNoNothingAtAll {}

    @DaRealAnnotation("A class has an annotation!")
    private static class AClassHasAnAnnotation {}

    @DaRealAnnotation("A class has an annotation and a parent!")
    private static class AClassHasAnAnnotationAndAParent extends AClassHasAnAnnotation {}

    private static class AClassHasNoAnnotationButItsParentDoes extends AClassHasAnAnnotation {}

    private static class AClassHasNoAnnotationButItsGrandParentDoes
            extends AClassHasNoAnnotationButItsParentDoes {}

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.METHOD, ElementType.TYPE})
    public @interface DaRealAnnotation {
        String value();
    }

    @AutoAnnotation
    private static DaRealAnnotation newDaFakeAnnotation(String value) {
        return new AutoAnnotation_TestHelperTest_newDaFakeAnnotation(value);
    }
}
