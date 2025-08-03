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

import android.annotation.Nullable;
import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

// TODO(b/242571576): move this class to com.android.compatibility.common.util

/**
 * Helper class used primarily to validate values used on
 * {code @com.android.compatibility.common.util.ApiTest}.
 */
public final class ApiHelper {

    private static final String TAG = ApiHelper.class.getSimpleName();
    private static final boolean DBG = Log.isLoggable(TAG, Log.DEBUG);

    /**
     * Resolves an API to the proper member (method or field).
     */
    @Nullable
    public static Member resolve(String api) {
        Objects.requireNonNull(api);

        Member member = null;
        ClassNotFoundException classNotFoundException = null;
        try {
            // Try method first, as it's the most common case...
            member = getMethod(api);
            if (member != null) {
                return member;
            }
        } catch (ClassNotFoundException e) {
            classNotFoundException = e;
        }

        // ...then field
        if (member == null) {
            if (api.contains("$")) {
                // See note below
                return null;
            }
            member = getField(api);
        }
        // ...then special cases
        if (member == null && api.contains("#")) {
            // TODO(b/242571576): From Java's point of view, a field from an inner class like:
            //  android.car.CarVersion$VERSION_CODES#TIRAMISU_0
            // is valid, but the python API parser is expecting
            //  android.car.CarVersion.VERSION_CODES#TIRAMISU_0
            int index = api.lastIndexOf('.');
            // TODO(b/242571576): it would fail if API was like Class.INNER_1.INNER_2.Field
            String fixed = api.substring(0, index) + "$" + api.substring(index + 1, api.length());
            member = getField(fixed);
        }

        if (member == null) {
            if (classNotFoundException != null) {
                Log.w(TAG, "Could not resolve API " + api + ": " + classNotFoundException);
            } else {
                Log.w(TAG, "Could not resolve API " + api + "; check log tag " + TAG
                        + " for more details");
            }
        }
        return member;
    }

    @Nullable
    private static Method getMethod(String fullyQualifiedMethodName) throws ClassNotFoundException {
        // TODO(b/242571576): improve it to:
        // - use regex
        // - handle methods from CREATOR
        // - support fields from inner classes like car.PlatformVersion$VERSION_CODES#TIRAMISU_0

        int classSeparator = fullyQualifiedMethodName.indexOf('#');
        if (classSeparator == -1) {
            return null;
        }
        String className = fullyQualifiedMethodName.substring(0, classSeparator);
        String methodSignature = fullyQualifiedMethodName.substring(classSeparator + 1,
                fullyQualifiedMethodName.length());
        if (DBG) {
            Log.d(TAG, "getMethod(" + fullyQualifiedMethodName + "): class=" + className
                    + ", signature=" + methodSignature);
        }

        Class<?> clazz = Class.forName(className);
        String methodName = methodSignature;
        if (methodSignature.contains("(") && methodSignature.endsWith(")")) {
            int openingIndex = methodSignature.indexOf('(');
            methodName = methodSignature.substring(0, openingIndex);
            String types = methodSignature.substring(openingIndex + 1,
                    methodSignature.length() - 1);
            String[] paramTypesNames = types.split(",");
            if (DBG) {
                Log.d(TAG, "Method name after stripping (): " + methodName + ". Types: "
                        + Arrays.toString(paramTypesNames));
            }
            return getMethodWithParameters(clazz, methodName, paramTypesNames);
        }
        return getMethodWithoutParameters(clazz, methodName);
    }

    @Nullable
    private static Method getMethodWithParameters(Class<?> clazz, String methodName,
            String[] paramTypesNames) {
        if (DBG) {
            Log.d(TAG, "getMethod(" + clazz  + ", " + methodName + ", "
                    + Arrays.toString(paramTypesNames) + ")");
        }
        // Need to interact trough all methods, otherwise it would be harder to handle java.lang
        // param types. For example:
        // - classes like String would need to be prefixed by "java.lang."
        // - primitive types would need to be handled case by case
        // Besides, the ApiTest syntax doesn't check for FQCN (for example, it should be just
        // "Handler" instead of "android.os.Handler");
        for (String paramTypeName : paramTypesNames) {
            if (paramTypeName.contains(".")) {
                return null;
            }
        }

        Method[] allMethods = clazz.getDeclaredMethods();
        method:
        for (Method method : allMethods) {
            if (DBG) {
                Log.v(TAG, "Trying method :"  + method);
            }
            if (!method.getName().equals(methodName)) {
                continue;
            }
            Class<?>[] paramTypes = method.getParameterTypes();
            if (paramTypes.length != paramTypesNames.length) {
                continue;
            }
            for (int i = 0; i < paramTypes.length; i++) {
                String expected = paramTypesNames[i].trim();
                String actual = paramTypes[i].getCanonicalName();
                if (DBG) {
                    Log.d(TAG, "Checking param #" + i + ": expected=" + expected + ", actual="
                            + actual);
                }
                if (!actual.endsWith(expected)) {
                    continue method;
                }
            }
            if (DBG) {
                Log.d(TAG, "Found method :"  + method);
            }
            return method;
        }
        return null;
    }

    @Nullable
    private static Method getMethodWithoutParameters(Class<?> clazz, String methodName) {
        if (DBG) {
            Log.d(TAG, "Getting method without params: " + methodName);
        }
        List<Method> methods = new ArrayList<>();
        for (Method method : clazz.getDeclaredMethods()) {
            if (methodName.equals(method.getName())) {
                if (DBG) {
                    Log.d(TAG, "Found " + methodName + ": " + method);
                }
                methods.add(method);
            }
        }
        if (methods.size() == 1) {
            return methods.get(0);
        }
        if (DBG) {
            if (methods.isEmpty()) {
                Log.d(TAG, "No method named " + methodName + " on " + clazz);
            } else {
                Log.d(TAG, "Found " + methods.size() + " methods on " + clazz + ": " + methods);
            }
        }
        return null;
    }

    @Nullable
    private static Field getField(String fullyQualifiedFieldName) {
        int classSeparator = fullyQualifiedFieldName.indexOf('#');
        if (classSeparator == -1) {
            return null;
        }
        String className = fullyQualifiedFieldName.substring(0, classSeparator);
        String fieldName = fullyQualifiedFieldName.substring(classSeparator + 1,
                fullyQualifiedFieldName.length());
        if (DBG) {
            Log.d(TAG, "getField(" + fullyQualifiedFieldName + "): class=" + className
                    + ", field=" + fieldName);
        }
        Class<?> clazz = null;
        try {
            clazz = Class.forName(className);
            if (clazz != null) {
                return clazz.getDeclaredField(fieldName);
            }
        } catch (Exception e) {
            Log.d(TAG, "getField(" + fullyQualifiedFieldName + ") failed: " + e);
            if (DBG && clazz != null) {
                Log.d(TAG, "Fields of " + clazz + ": "
                        + Arrays.toString(clazz.getDeclaredFields()));
            }
        }
        return null;
    }

    private ApiHelper() {
        throw new UnsupportedOperationException("provides only static methods");
    }
}
