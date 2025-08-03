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

package android.car.test;

import static android.car.test.ApiHelper.resolve;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;

import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;

public final class ApiHelperTest {

    // TODO(b/242571576): add parameterized test class for invalid values (i.e., that return null)

    @Test
    public void testResolve_null() {
        assertThrows(NullPointerException.class, () -> resolve(null));
    }

    @Test
    public void testResolve_empty() {
        assertWithMessage("resolve()").that(resolve("")).isNull();
        assertWithMessage("resolve( )").that(resolve(" ")).isNull();
    }

    @Test
    public void testResolve_methodWithoutParameters() {
        assertMethod("android.car.test.ApiHelperTest#methodWithoutParameters", ApiHelperTest.class,
                "methodWithoutParameters");
    }

    @Test
    public void testResolve_methodWithOneParameter_fullyQualified() {
        assertInvalidApi("android.car.test.ApiHelperTest#"
                + "methodWithOneParameterAndroid(android.content.Context)");
    }

    @Test
    public void testResolve_methodWithOneParameter() {
        assertMethod("android.car.test.ApiHelperTest#methodWithOneParameterAndroid(Context)",
                ApiHelperTest.class, "methodWithOneParameterAndroid", Context.class);
    }

    @Test
    public void testResolve_methodWithOneParameter_notOverloaded_methodNameOnly() {
        assertMethod("android.car.test.ApiHelperTest#methodWithOneParameterAndroid",
                ApiHelperTest.class, "methodWithOneParameterAndroid", Context.class);
    }

    @Test
    public void testResolve_methodWithOneParameterFromJavaLang() {
        assertMethod("android.car.test.ApiHelperTest#methodWithOneParameterJavaLang(String)",
                ApiHelperTest.class, "methodWithOneParameterJavaLang", String.class);
    }

    @Test
    public void testResolve_methodWithOneParameterPrimitiveType() {
        assertMethod("android.car.test.ApiHelperTest#methodWithOneParameterPrimitive(int)",
                ApiHelperTest.class, "methodWithOneParameterPrimitive", int.class);
    }

    @Test
    public void testResolve_methodWithOverloadedParameters() {
        Method method1 = assertMethod(
                "android.car.test.ApiHelperTest#methodWithOneParameterOverloaded(Context)",
                ApiHelperTest.class, "methodWithOneParameterOverloaded", Context.class);

        Method method2 = assertMethod(
                "android.car.test.ApiHelperTest#methodWithOneParameterOverloaded(String)",
                ApiHelperTest.class, "methodWithOneParameterOverloaded", String.class);

        assertWithMessage("method1").that(method1).isNotEqualTo(method2);
        assertWithMessage("method1").that(method1).isNotSameInstanceAs(method2);

        assertInvalidApi("android.car.test.ApiHelperTest#methodWithOneParameterOverloaded");
    }

    @Test
    public void testResolve_methodWithMultipleParameters() {
        assertMethod(
                "android.car.test.ApiHelperTest#methodWithMultipleParameters(Context,String,int)",
                ApiHelperTest.class, "methodWithMultipleParameters",
                Context.class, String.class, int.class);
    }

    @Test
    public void testResolve_methodWithMultipleParametersWithSpaces() {
        assertMethod("android.car.test.ApiHelperTest#"
                + "methodWithMultipleParameters( Context, String,int )",
                ApiHelperTest.class, "methodWithMultipleParameters",
                Context.class, String.class, int.class);
    }

    @Test
    public void testResolve_singleField() {
        assertField("android.car.test.ApiHelperTest#SINGLE_FIELD", ApiHelperTest.class, long.class,
                "SINGLE_FIELD");
    }

    @Test
    public void testResolve_creator() {
        assertField("android.car.test.ApiHelperTest#CREATOR", ApiHelperTest.class,
                Parcelable.Creator.class, "CREATOR");
    }

    @Test
    public void testResolve_nestedField_valid() {
        assertField("android.car.test.ApiHelperTest.VERSION_CODES#KEY_LIME_PIE",
                ApiHelperTest.VERSION_CODES.class, int.class, "KEY_LIME_PIE");
    }

    @Test
    public void testResolve_nestedField_invalid() {
        assertInvalidApi("android.car.test.ApiHelperTest$VERSION_CODES");
        assertInvalidApi("android.car.test.ApiHelperTest$VERSION_CODES.KEY_LIME_PIE");
        assertInvalidApi("android.car.test.ApiHelperTest$VERSION_CODES#KEY_LIME_PIE");
        assertInvalidApi("android.car.test.ApiHelperTest#VERSION_CODES");
        assertInvalidApi("android.car.test.ApiHelperTest.VERSION_CODES.KEY_LIME_PIE");
    }

    ////////////////////////////////////
    // Start of members used on tests //
    ////////////////////////////////////

    public static final long SINGLE_FIELD = 4815162342L;

    public void methodWithoutParameters() {
    }

    public void methodWithOneParameterAndroid(Context context) {
    }

    public void methodWithOneParameterJavaLang(String string) {
    }

    public void methodWithOneParameterPrimitive(int integer) {
    }

    public void methodWithOneParameterOverloaded(Context context) {
    }

    public void methodWithOneParameterOverloaded(String string) {
    }

    public void methodWithMultipleParameters(Context context, String string, int integer) {
    }

    public static final Parcelable.Creator<Parcelable> CREATOR =
            new Parcelable.Creator<Parcelable>() {

        @Override
        public Parcelable createFromParcel(Parcel source) {
            return null;
        }

        @Override
        public Parcelable[] newArray(int size) {
            return new Parcelable[size];
        }
    };

    public static final class VERSION_CODES {
        public static final int KEY_LIME_PIE = 42;
    }


    //////////////////////////////////
    // End of members used on tests //
    //////////////////////////////////

    private static void assertInvalidApi(String api) {
        assertWithMessage("invalid API").that(resolve(api)).isNull();
    }

    private static Method assertMethod(String api, Class<?> expectedClass, String expectedName,
            Class<?>...expectedParameterTypes) {
        Method method = assertMember(api, Method.class, expectedClass, expectedName);
        assertWithMessage("parameter types of %s", method).that(method.getParameterTypes())
                .asList().containsExactlyElementsIn(expectedParameterTypes);
        return method;
    }

    private static Field assertField(String api, Class<?> expectedDeclaringClass,
            Class<?> expectedFieldClass, String expectedName) {
        Field field = assertMember(api, Field.class, expectedDeclaringClass, expectedName);
        assertWithMessage("type of %s", field).that(field.getType()).isEqualTo(expectedFieldClass);
        return field;
    }

    private static <M extends Member> M assertMember(String api, Class<M> expectedMemberType,
            Class<?> expectedDeclaringClass, String expectedName) {
        Member member = resolve(api);
        assertWithMessage("resolve(%s)", api).that(member).isNotNull();
        assertWithMessage("member type of %s", member).that(member)
                .isInstanceOf(expectedMemberType);
        assertWithMessage("declaring class of %s", member).that(member.getDeclaringClass())
                .isEqualTo(expectedDeclaringClass);
        assertWithMessage("name of %s", member).that(member.getName()).isEqualTo(expectedName);
        return expectedMemberType.cast(member);
    }
}
