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

package android.car.test.util;

import static com.google.common.truth.Truth.assertWithMessage;

import android.car.annotation.AddedInOrBefore;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;


// TODO(b/237565347): Refactor this class so that 'field' and 'method' code is not repeated.
/**
 * Helper for evaluating annotations in tests.
 */
public class AnnotationHelper {

    public static final HashSet<String> sJavaLangObjectNames;

    static {
        Method[] objectMethods = Object.class.getMethods();
        sJavaLangObjectNames = new HashSet<>(objectMethods.length);

        for (Method method : objectMethods) {
            sJavaLangObjectNames.add(
                    method.getReturnType().toString() + method.getName() + Arrays.toString(
                            method.getParameterTypes()));
        }

        // getMethods excludes protected functions.
        sJavaLangObjectNames.add("voidfinalize[]");
    }

    public static void checkForAnnotation(String[] classes, HashSet<String> addedInOrBeforeApis,
            Class<?>... annotationClasses)
            throws Exception {
        List<String> errorsNoAnnotation = new ArrayList<>();
        List<String> errorsExtraAnnotation = new ArrayList<>();
        List<String> errorsExemptAnnotation = new ArrayList<>();

        for (int i = 0; i < classes.length; i++) {
            String className = classes[i];
            Field[] fields = Class.forName(className).getDeclaredFields();
            for (int j = 0; j < fields.length; j++) {
                Field field = fields[j];

                // These are some internal fields
                if (field.isSynthetic()) continue;

                boolean isAnnotated = containsAddedInAnnotation(field, addedInOrBeforeApis,
                        annotationClasses);
                boolean shouldBeAnnotated = Modifier.isPublic(field.getModifiers())
                        || Modifier.isProtected(field.getModifiers());

                if (!shouldBeAnnotated && isAnnotated) {
                    errorsExtraAnnotation.add(className + " FIELD: " + field.getName());
                }

                if (shouldBeAnnotated && !isAnnotated) {
                    errorsNoAnnotation.add(className + " FIELD: " + field.getName());
                }
            }

            Method[] methods = Class.forName(className).getDeclaredMethods();
            for (int j = 0; j < methods.length; j++) {
                Method method = methods[j];

                // These are some internal methods
                if (method.isBridge() || method.isSynthetic()) continue;

                boolean isAnnotated = containsAddedInAnnotation(method, addedInOrBeforeApis,
                        annotationClasses);
                boolean shouldBeAnnotated = Modifier.isPublic(method.getModifiers())
                        || Modifier.isProtected(method.getModifiers());

                if (isExempt(method)) {
                    if (isAnnotated) {
                        errorsExemptAnnotation.add(className + " METHOD: " + method.getName());
                    }
                    continue;
                }

                if (!shouldBeAnnotated && isAnnotated) {
                    errorsExtraAnnotation.add(className + " METHOD: " + method.getName());
                }

                if (shouldBeAnnotated && !isAnnotated) {
                    errorsNoAnnotation.add(className + " METHOD: " + method.getName());
                }
            }
        }

        StringBuilder errorFlatten = new StringBuilder();

        if (!errorsExemptAnnotation.isEmpty()) {
            errorFlatten.append(
                    "Errors:\nApiRequirements or AddedInOrBefore annotation used for overridden "
                            + "JDK methods-\n");
            errorFlatten.append(String.join("\n", errorsExemptAnnotation));
        }

        if (!errorsNoAnnotation.isEmpty()) {
            List<Class<?>> annotations = Arrays.stream(annotationClasses).toList();
            if (annotations.isEmpty()) {
                errorFlatten.append("Errors:\nannotationClasses argument should not be empty\n");
            } else {
                if (annotations.contains(android.car.annotation.ApiRequirements.class)) {
                    errorFlatten.append("\nErrors:\nMissing ApiRequirements annotation for-\n");
                } else {
                    errorFlatten.append("\nErrors:\nMissing AddedIn annotation for-\n");
                }
            }
            errorFlatten.append(String.join("\n", errorsNoAnnotation));
        }

        if (!errorsExtraAnnotation.isEmpty()) {
            // TODO(b/240343308): remove @AddedIn once all usages have been replaced
            errorFlatten.append("\nErrors:\nApiRequirements annotation used for "
                    + "private or package scoped members or methods-\n");
            errorFlatten.append(String.join("\n", errorsExtraAnnotation));
        }

        assertWithMessage(errorFlatten.toString()).that(
                errorsExtraAnnotation.size() + errorsNoAnnotation.size()
                        + errorsExemptAnnotation.size()).isEqualTo(0);
    }

    public static void checkForAnnotation(String[] classes, Class<?>... annotationClasses)
            throws Exception {
        checkForAnnotation(classes, null, annotationClasses);
    }

    @SuppressWarnings("unchecked")
    private static boolean containsAddedInAnnotation(Field field,
            HashSet<String> addedInOrBeforeApis, Class<?>... annotationClasses) {
        for (int i = 0; i < annotationClasses.length; i++) {
            if (field.getAnnotation((Class<Annotation>) annotationClasses[i]) != null) {
                validatedAddInOrBeforeAnnotation(field, addedInOrBeforeApis);
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private static boolean containsAddedInAnnotation(Method method,
            HashSet<String> addedInOrBeforeApis, Class<?>... annotationClasses) {
        for (int i = 0; i < annotationClasses.length; i++) {
            if (method.getAnnotation((Class<Annotation>) annotationClasses[i]) != null) {
                validatedAddInOrBeforeAnnotation(method, addedInOrBeforeApis);
                return true;
            }
        }
        return false;
    }

    private static void validatedAddInOrBeforeAnnotation(Field field,
            HashSet<String> addedInOrBeforeApis) {
        AddedInOrBefore annotation = field.getAnnotation(AddedInOrBefore.class);
        String fullFieldName =
                (field.getDeclaringClass().getName() + "." + field.getName()).replace('$', '.');
        if (annotation != null) {
            assertWithMessage(
                    "%s, field: %s should not use AddedInOrBefore annotation. The annotation was "
                            + "reserved only for APIs added in or before majorVersion:33, "
                            + "minorVersion:0",
                    field.getDeclaringClass(), field.getName())
                    .that(annotation.majorVersion()).isEqualTo(33);
            assertWithMessage(
                    "%s, field: %s should not use AddedInOrBefore annotation. The annotation was "
                            + "reserved only for APIs added in or before majorVersion:33, "
                            + "minorVersion:0",
                    field.getDeclaringClass(), field.getName())
                    .that(annotation.minorVersion()).isEqualTo(0);
            if (addedInOrBeforeApis != null) {
                assertWithMessage(
                        "%s, field: %s was newly added and should not use the AddedInOrBefore "
                                + "annotation.",
                        field.getDeclaringClass(), field.getName())
                        .that(addedInOrBeforeApis.contains(fullFieldName)).isTrue();
            }
        }
    }

    private static void validatedAddInOrBeforeAnnotation(Method method,
            HashSet<String> addedInOrBeforeApis) {
        AddedInOrBefore annotation = method.getAnnotation(AddedInOrBefore.class);
        String fullMethodName =
                (method.getDeclaringClass().getName() + "." + method.getName()).replace('$', '.');
        if (annotation != null) {
            assertWithMessage(
                    "%s, method: %s should not use AddedInOrBefore annotation. The annotation was "
                            + "reserved only for APIs added in or before majorVersion:33, "
                            + "minorVersion:0",
                    method.getDeclaringClass(), method.getName())
                    .that(annotation.majorVersion()).isEqualTo(33);
            assertWithMessage(
                    "%s, method: %s should not use AddedInOrBefore annotation. The annotation was "
                            + "reserved only for APIs added in or before majorVersion:33, "
                            + "minorVersion:0",
                    method.getDeclaringClass(), method.getName())
                    .that(annotation.minorVersion()).isEqualTo(0);
            if (addedInOrBeforeApis != null) {
                assertWithMessage(
                        "%s, method: %s was newly added and should not use the AddedInOrBefore "
                                + "annotation.",
                        method.getDeclaringClass(), method.getName())
                        .that(addedInOrBeforeApis.contains(fullMethodName)).isTrue();
            }
        }
    }

    // Overridden JDK methods do not need @ApiRequirements annotations. Since @Override
    // annotations are discarded by the compiler, one needs to manually add any classes where
    // methods are overridden and @ApiRequirements are not needed.
    // Currently, overridden methods from java.lang.Object should be skipped.
    private static boolean isExempt(Method method) {
        String methodSignature =
                method.getReturnType().toString() + method.getName() + Arrays.toString(
                        method.getParameterTypes());
        return sJavaLangObjectNames.contains(methodSignature);
    }
}
