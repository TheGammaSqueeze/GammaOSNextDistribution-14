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
import static org.junit.Assert.assertSame;

import com.google.doclava.javadoc.BaseTest.METHOD.OF_CLASS;
import com.google.doclava.javadoc.BaseTest.METHOD.PARAMETER;
import com.sun.javadoc.AnnotatedType;
import com.sun.javadoc.AnnotationDesc;
import com.sun.javadoc.Parameter;
import com.sun.javadoc.Type;
import java.util.function.BiConsumer;
import org.junit.Before;
import org.junit.Test;

public class ParameterImplTest extends BaseTest {

    public static MethodDocImpl arg0;
    public static MethodDocImpl arg2_int_String;
    public static MethodDocImpl arg1_annotatedObject; // Annotated with @UniversalAnnotation

    record TestCase(MethodDocImpl method, ExpectedParameter... expectedParameters) {

    }

    private static class ExpectedParameter {

        public final Type type;
        public final String name;
        public final AnnotationDesc[] annotations;

        public ExpectedParameter(Type type, String name) {
            this.type = type;
            this.name = name;
            if (type instanceof AnnotatedType ad) {
                this.annotations = ad.annotations();
            } else {
                this.annotations = new AnnotationDesc[0];
            }
        }
    }

    private static TestCase[] TESTS = new TestCase[0];

    @Before
    public void setUp() {
        super.setUp();
        arg0 = MethodDocImpl.create(OF_CLASS.private_int_arg0, context);
        arg1_annotatedObject = MethodDocImpl.create(OF_CLASS.void_arg1_annotatedObject, context);
        arg2_int_String =
                MethodDocImpl.create(OF_CLASS.packagePrivate_String_arg2_int_String, context);

        AnnotatedTypeImpl annotatedObject = (AnnotatedTypeImpl) TypeImpl.create(
                PARAMETER.Object_annotatedWith_UniversalAnnotation.asType(), context);
        ClassDocImpl javaLangString = ClassDocImpl.create(INSTANCE.javaLangString, context);
        // AnnotationDescImpl universalAnnotation =
        //         new AnnotationDescImpl(annotatedObject.annotations()[0])

        TESTS = new TestCase[]{
                // "method()" (0 parameters)
                new TestCase(arg0),

                // "method(@UniversalAnnotation Object obj)" (1 parameter: annotated Object)
                new TestCase(
                        arg1_annotatedObject,
                        new ExpectedParameter(annotatedObject, "obj")
                ),

                // "method(int a, String b)" (2 parameters: int, String)
                new TestCase(
                        arg2_int_String,
                        new ExpectedParameter(PrimitiveTypeImpl.INT, "a"),
                        new ExpectedParameter(javaLangString, "b")
                )
        };
    }

    @Test
    public void type() {
        testParametersWith((actual, expected) -> assertSame(actual.type(), expected.type));
    }

    @Test
    public void name() {
        testParametersWith((actual, expected) -> assertEquals(actual.name(), expected.name));
    }

    @Test
    public void typeName() {
        testParametersWith((actual, expected) -> {
            assertEquals(actual.typeName(), ""/*expected.type.qualifiedTypeName()*/);
        });
    }

    @Test
    public void annotations() {
        testParametersWith((actual, expected) -> {
            final var actualAnnotations = actual.annotations();
            final var expectedAnnotations = expected.annotations;
            assertEquals(expectedAnnotations.length, actualAnnotations.length);

            for (int i = 0; i < expectedAnnotations.length; i++) {
                var ea = expectedAnnotations[i].annotationType();
                var aa = actualAnnotations[i].annotationType();

                assertEquals(ea.qualifiedName(), aa.qualifiedName());
                assertEquals(ea.qualifiedTypeName(), aa.qualifiedTypeName());
            }
        });
    }

    /**
     * Calls given {@code function} on each pair of <{@code actualParameter}, {@code
     * expectedParameter}> that are listed in {@link #TESTS}. The {@code function} accepts two
     * values: actual {@link Parameter} and {@link ExpectedParameter} fixture, and should compare
     * "actual" value against "expected", throwing an assertion when required.
     *
     * Also, asserts that number of actual and expected parameters is same.
     *
     * @param function compares actual {@link Parameter} returned from {@link ParameterImpl} with a
     * fixture.
     * @throws AssertionError when expectations for actual parameter are not met.
     */
    private void testParametersWith(BiConsumer<Parameter, ExpectedParameter> function)
            throws AssertionError {
        for (TestCase test : TESTS) {
            final Parameter[] actualParameters = test.method.parameters();
            final ExpectedParameter[] expectedParameters = test.expectedParameters;
            assertEquals(actualParameters.length, expectedParameters.length);

            for (int i = 0; i < expectedParameters.length; i++) {
                final var actual = actualParameters[i];
                final var expected = expectedParameters[i];

                function.accept(actual, expected);
            }
        }
    }
}